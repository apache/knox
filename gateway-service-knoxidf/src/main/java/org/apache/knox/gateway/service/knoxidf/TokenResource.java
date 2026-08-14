/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.service.knoxidf;

import com.nimbusds.jose.KeyLengthException;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.service.knoxidf.userparams.UserParamsProvider;
import org.apache.knox.gateway.service.knoxidf.userparams.UserParamsProviderFactory;
import org.apache.knox.gateway.service.knoxtoken.PasscodeTokenResourceBase;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.federation.FederatedIdentity;
import org.apache.knox.gateway.services.knoxidf.federation.FederatedIdentityService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.JWTokenAttributesBuilder;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenMetadataType;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;
import org.apache.knox.gateway.util.ServletRequestUtils;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.apache.knox.gateway.security.CommonTokenConstants.CLIENT_SECRET;
import static org.apache.knox.gateway.security.CommonTokenConstants.GRANT_TYPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.AUTH_CODE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.BASE_RESOURCE_PATH;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CLIENT_ID;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE_CHALLENGE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE_CHALLENGE_METHOD;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE_VERIFIER;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.FEDERATED_IDENTITY_ID;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.OFFLINE_ACCESS_SCOPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.PKCE_METHOD_S256;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REDIRECT_URI;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REFRESH_TOKEN;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REFRESH_TOKEN_TTL;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REFRESH_TOKEN_TTL_DEFAULT;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.SCOPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFUtils.error;

@Path(TokenResource.RESOURCE_PATH)
@Produces(MediaType.APPLICATION_JSON)
public class TokenResource extends PasscodeTokenResourceBase {
    static final String RESOURCE_PATH = BASE_RESOURCE_PATH + "/token";

    // Per-request stash for the auth-code TokenMetadata read during validation. The code is
    // atomically consumed (deleted) BEFORE token issuance to close the replay window, so the
    // issuance steps (buildUserContext/addArbitraryTokenMetadata/buildResponseMap) can no longer
    // re-read it from the store; they read this request attribute instead. This resource is a
    // singleton, but the @Context request is a per-request proxy, so the attribute is request-scoped.
    private static final String AUTH_CODE_METADATA_ATTR = "knoxidf.authCode.metadata";

    // Per-request stash for the federated identity id of the current grant. On the authorization_code
    // grant it is read from the auth-code metadata; on the refresh_token grant it is restored from the
    // presented refresh token's metadata (see handleRefreshToken). This lets id_token generation keep
    // emitting federated profile claims, and lets the rotated refresh token carry the id forward, so
    // federated claims survive an arbitrary number of refresh rotations.
    private static final String FEDERATED_IDENTITY_ID_ATTR = "knoxidf.federatedIdentityId";

    private UserParamsProvider userParamsProvider;

    @Context
    HttpServletRequest request; // package-private for test injection; @Context injection is reflective

    @Context
    private ServletContext servletContext;

    private FederatedIdentityService federatedIdentityService;
    private long refreshTokenTTL;
    TokenMAC tokenMAC;

    @Override
    public String getPrefix() {
        return "knoxidf.";
    }

    @PostConstruct
    @Override
    public void init() throws ServletException, AliasServiceException, ServiceLifecycleException, KeyLengthException {
        super.init();
        this.servletContext = wrapContextForDefaultParams(this.servletContext);
        this.userParamsProvider = UserParamsProviderFactory.getUserParamsProvider(servletContext);
        final GatewayServices services = (GatewayServices) servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        federatedIdentityService = services.getService(ServiceType.KNOXIDF_FEDERATED_IDENTITY_SERVICE);
        // Build the same passcode MAC the JWTFederationFilter uses so the token endpoint can
        // independently authenticate a client_secret (see validateAuthCode). The HMAC key alias is
        // guaranteed to exist by this point (PasscodeTokenResourceBase#setupTokenStateService
        // generates it if absent).
        final GatewayConfig gatewayConfig = (GatewayConfig) servletContext.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
        final AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);
        this.tokenMAC = new TokenMAC(gatewayConfig.getKnoxTokenHashAlgorithm(), aliasService.getPasswordFromAliasForGateway(TokenMAC.KNOX_TOKEN_HASH_KEY_ALIAS_NAME));
        setRefreshTokenTTL();
    }

    private void setRefreshTokenTTL() {
        final String configuredRefreshTokenTTL = servletContext.getInitParameter(REFRESH_TOKEN_TTL);
        if (StringUtils.isNotBlank(configuredRefreshTokenTTL)) {
            this.refreshTokenTTL = Long.parseLong(configuredRefreshTokenTTL);
        } else {
            refreshTokenTTL = REFRESH_TOKEN_TTL_DEFAULT;
        }
    }

    @Override
    @POST
    public Response doPost() {
        final String grantType = getRequestParam(GRANT_TYPE);
        if (REFRESH_TOKEN.equals(grantType)) {
            return handleRefreshToken();
        } else if (AUTH_CODE.equals(grantType)) {
            return handleAuthorizationCodeFlow();
        }
        return super.doPost(); // with this, we don't need an additional KNOXTOKEN service in any KnoxIDF topology
    }

    private boolean isAuthCodeFlow() {
        return isAuthCodeFlow(getRequestParam(GRANT_TYPE));
    }

    private boolean isAuthCodeFlow(String grantType) {
        return AUTH_CODE.equals(grantType);
    }

    private boolean isRefreshTokenFlow() {
        return REFRESH_TOKEN.equals(getRequestParam(GRANT_TYPE));
    }

    @Override
    protected UserContext buildUserContext(HttpServletRequest request) {
        if (isAuthCodeFlow()) {
            try {
                final TokenMetadata tokenMetadata = getAuthCodeMetadata();
                final String scope = tokenMetadata.getMetadata(SCOPE);
                final Map<String, Object> userParams = userParamsProvider.getParamsFor(tokenMetadata.getUserName(), scope);
                userParams.put(SCOPE, scope);
                return new UserContext(tokenMetadata.getUserName(), null, userParams);
            } catch (UnknownTokenException e) {
                //this should not happen as we have just validated the auth code
                throw new RuntimeException(e);
            }
        }
        return super.buildUserContext(request);
    }

    @Override
    protected void addArbitraryTokenMetadata(TokenMetadata tokenMetadata) {
        super.addArbitraryTokenMetadata(tokenMetadata);
        if (isAuthCodeFlow()) {
            try {
                final String code = getRequestParam(CODE);
                if (StringUtils.isNotBlank(code)) {
                    final TokenMetadata authCodeTokenMetadata = getAuthCodeMetadata();

                    //if the auth code token was a result of a federated OIDC call, we need to save the associated
                    //federated identity ID in the JWT too (so that it can be looked up while fetching user info)
                    final String federatedIdentityId = authCodeTokenMetadata.getMetadata(FEDERATED_IDENTITY_ID);
                    if (StringUtils.isNotBlank(federatedIdentityId)) {
                        tokenMetadata.add(FEDERATED_IDENTITY_ID, federatedIdentityId);
                    }
                }
            } catch (UnknownTokenException e) {
                //this should not happen as we have just validated the auth code
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected ResponseMap buildResponseMap(JWT token, long expires) throws TokenServiceException {
        final ResponseMap responseMap = super.buildResponseMap(token, expires);

        // id_token + refresh-token rotation apply to the user-centric grants (authorization_code and
        // refresh_token). client_credentials and other grants routed to super.doPost() must not get an
        // id_token (no end user) and never carry offline_access, so they are excluded here.
        if (isAuthCodeFlow() || isRefreshTokenFlow()) {
            final String code = getRequestParam(CODE);
            TokenMetadata authCodeTokenMetadata = null;
            if (StringUtils.isNotBlank(code)) {
                try {
                    authCodeTokenMetadata = getAuthCodeMetadata();
                } catch (UnknownTokenException e) {
                    //NOP
                }
            }

            responseMap.map.put("id_token", generateIdToken(token, authCodeTokenMetadata));

            final String refreshToken = generateRefreshToken(token);
            if (StringUtils.isNotBlank(refreshToken)) {
                responseMap.map.put(REFRESH_TOKEN, refreshToken);
            }
        }

        return responseMap;
    }

    // Package-private for testability (the single-use rotation guard is exercised by
    // TokenResourceRefreshTokenRotationTest); not part of the public resource API.
    Response handleRefreshToken() {
        // Audit the outcome of every refresh_token grant exactly once. The resource is the masked
        // client_id; the masked refresh-token id and a reason are recorded in the message. The raw
        // refresh token and client_secret are never logged.
        final String clientId = getRequestParam(CLIENT_ID);
        String maskedRefreshTokenId = KnoxIDFAudit.UNKNOWN;
        String outcome = ActionOutcome.FAILURE;
        String detail = "reason=unknown";
        try {
            final String refreshTokenParam = getRequestParam(REFRESH_TOKEN);
            final String refreshTokenId = TokenUtils.getTokenId(refreshTokenParam);
            maskedRefreshTokenId = KnoxIDFAudit.mask(refreshTokenId);
            final TokenMetadata refreshTokenMetadata = tokenStateService.getTokenMetadata(refreshTokenId);
            validateRefreshTokenGrant(refreshTokenParam, refreshTokenId, refreshTokenMetadata);

            // Rotation is single-use: atomically consume (revoke) the presented refresh token BEFORE
            // issuing its replacement. consumeToken is an atomic claim -- exactly one of N concurrent
            // redemptions wins -- so two concurrent refreshes cannot both mint a new token pair from
            // the same refresh token. (DefaultTokenStateService otherwise has a check-then-act race in
            // revokeToken; the JDBC path is already atomic via a PK DELETE.) A lost claim means another
            // request already redeemed/rotated this token, so reject it as invalid_grant. This mirrors
            // the consume-before-issue guard on the authorization_code grant (see handleAuthorizationCodeFlow).
            if (!tokenStateService.consumeToken(refreshTokenId)) {
                detail = "reason=refresh_token_replayed";
                return error("invalid_grant", "Refresh token has already been redeemed");
            }

            // Valid, freshly-consumed refresh token -> issue new access token and new refresh token (rotation)
            final String userName = refreshTokenMetadata.getUserName();
            final String scope = refreshTokenMetadata.getMetadata(SCOPE);
            final Map<String, Object> userParams = userParamsProvider.getParamsFor(userName, scope);
            userParams.put(SCOPE, scope);

            // Restore the federated identity id (if any) so id_token generation keeps the federated
            // profile claims and the rotated refresh token carries the id forward for the next refresh.
            final String federatedIdentityId = refreshTokenMetadata.getMetadata(FEDERATED_IDENTITY_ID);
            if (StringUtils.isNotBlank(federatedIdentityId)) {
                request.setAttribute(FEDERATED_IDENTITY_ID_ATTR, federatedIdentityId);
            }

            // Build new tokens
            final UserContext userContext = new UserContext(userName, null, userParams);
            final TokenResponseContext resp = getTokenResponse(userContext);
            outcome = ActionOutcome.SUCCESS;
            detail = "reason=rotated";
            return resp.build();
        } catch (ParseException e) {
            detail = "reason=malformed_refresh_token";
            return error("invalid_grant", "Malformed refresh_token");
        } catch (UnknownTokenException e) {
            detail = "reason=unknown_refresh_token";
            return error("invalid_grant", "Unknown refresh_token");
        } catch (RefreshTokenValidationError e) {
            detail = "reason=validation_failed";
            return error("invalid_grant", e.getMessage());
        } finally {
            KnoxIDFAudit.audit(Action.AUTHENTICATION, KnoxIDFAudit.mask(clientId), ResourceType.PRINCIPAL,
                    outcome, "event=token_grant grant_type=refresh_token refresh_token_id="
                            + maskedRefreshTokenId + " " + detail);
        }
    }

    // Package-private for testability (client-authentication on the refresh grant is exercised by
    // TokenResourceRefreshTokenClientAuthTest); not part of the public resource API.
    void validateRefreshTokenGrant(String refreshTokenParam, String refreshTokenId, TokenMetadata refreshTokenMetadata) throws UnknownTokenException, RefreshTokenValidationError {
        final String clientId = getRequestParam(CLIENT_ID);

        if (StringUtils.isBlank(refreshTokenParam)) {
            throw new RefreshTokenValidationError("Invalid request: Missing refresh_token");
        }

        if (StringUtils.isBlank(clientId)) {
            throw new RefreshTokenValidationError("Invalid request: Missing client_id");
        }

        if (refreshTokenMetadata == null || !TokenMetadataType.REFRESH_TOKEN.name().equals(refreshTokenMetadata.getType())) {
            throw new RefreshTokenValidationError("Invalid grant: invalid refresh_token");
        }

        // A refresh token that has been administratively disabled (revoked) must not mint new tokens,
        // even if it has not yet expired.
        if (!refreshTokenMetadata.isEnabled()) {
            throw new RefreshTokenValidationError("Invalid grant: refresh_token disabled");
        }

        if (tokenStateService.getTokenExpiration(refreshTokenId) <= System.currentTimeMillis()) {
            throw new RefreshTokenValidationError("Invalid grant: Refresh token expired");
        }

        final String associatedClientId = refreshTokenMetadata.getMetadata(CLIENT_ID);
        if (!clientId.equals(associatedClientId)) {
            throw new RefreshTokenValidationError("Invalid grant: client_id mismatch");
        }

        // Client authentication (RFC 6749 §6, §10.4). Like the authorization_code grant
        // (see validateAuthCode), the refresh_token grant must independently prove client identity:
        // the JWTFederationFilter Bearer path forwards a request to this endpoint without checking
        // client_secret, so matching client_id alone would let anyone holding a stolen refresh token
        // redeem and rotate it. KnoxIDF issues every registered client a client_secret, so a
        // constant-time client_secret check against the stored passcode is required here.
        if (!isValidClientSecret(clientId, getRequestParam(CLIENT_SECRET))) {
            throw new RefreshTokenValidationError("Invalid grant: client authentication failed");
        }
    }

    // Package-private for testability (single-use replay guard is exercised by
    // TokenResourceAuthCodeReplayTest); not part of the public resource API.
    Response handleAuthorizationCodeFlow() {
        final String code = getRequestParam(CODE);
        final String redirectUri = getRequestParam(REDIRECT_URI);
        // Audit the outcome of every authorization_code grant exactly once. The resource is the masked
        // client_id; the masked auth-code id and a reason are recorded in the message. The raw code,
        // code_verifier and client_secret are never logged.
        final String clientId = getRequestParam(CLIENT_ID);
        String outcome = ActionOutcome.FAILURE;
        String detail = "reason=unknown";
        try {
            final TokenMetadata authCodeMetadata;
            try {
                authCodeMetadata = validateAuthCode(code, redirectUri);
            } catch (AuthTokenValidationError e) {
                detail = "reason=validation_failed";
                return error("invalid_grant", e.getMessage());
            }

            // Enforce single-use: atomically consume the code BEFORE issuing any token. Of N concurrent
            // redemptions of the same code, exactly one wins the consume and proceeds; the losers get
            // invalid_grant. This closes the replay window that existed when the code was only revoked
            // in a finally block AFTER issuance. A code that fails validation above is deliberately NOT
            // consumed here, so replaying with bad params cannot burn a victim's still-valid code.
            if (!tokenStateService.consumeToken(code)) {
                detail = "reason=code_replayed";
                return error("invalid_grant", "Authorization code has already been redeemed");
            }

            // The code is now gone from the store; hand the already-validated metadata to the issuance
            // path via a request attribute (see getAuthCodeMetadata) so it need not re-read the code.
            request.setAttribute(AUTH_CODE_METADATA_ATTR, authCodeMetadata);
            final Response response = getAuthenticationToken();
            outcome = ActionOutcome.SUCCESS;
            detail = "reason=tokens_issued";
            return response;
        } finally {
            KnoxIDFAudit.audit(Action.AUTHENTICATION, KnoxIDFAudit.mask(clientId), ResourceType.PRINCIPAL,
                    outcome, "event=token_grant grant_type=authorization_code code=" + KnoxIDFAudit.mask(code)
                            + " " + detail);
        }
    }

    /**
     * Returns the auth-code {@link TokenMetadata} captured at validation time and stashed in a
     * request attribute by {@link #handleAuthorizationCodeFlow()}. Because the code is consumed
     * (deleted) before token issuance, the issuance steps can no longer re-read it from the store;
     * this serves the cached copy, falling back to a store read only if the attribute is absent.
     */
    private TokenMetadata getAuthCodeMetadata() throws UnknownTokenException {
        final Object cached = request.getAttribute(AUTH_CODE_METADATA_ATTR);
        if (cached instanceof TokenMetadata) {
            return (TokenMetadata) cached;
        }
        return tokenStateService.getTokenMetadata(getRequestParam(CODE));
    }

    // Resolves the federated identity id for the current grant, or null for a local (non-federated)
    // user. On the authorization_code grant it comes from the auth-code metadata; on the refresh_token
    // grant it is restored from the presented refresh token's metadata via a request attribute
    // (see handleRefreshToken). This keeps federated profile claims flowing through every refresh.
    private String resolveFederatedIdentityId() {
        if (isAuthCodeFlow()) {
            try {
                return getAuthCodeMetadata().getMetadata(FEDERATED_IDENTITY_ID);
            } catch (UnknownTokenException e) {
                //this should not happen as we have just validated the auth code
                throw new RuntimeException(e);
            }
        }
        final Object cached = request.getAttribute(FEDERATED_IDENTITY_ID_ATTR);
        return cached == null ? null : cached.toString();
    }

    private TokenMetadata validateAuthCode(String code, String redirectUri) throws AuthTokenValidationError {
        try {
            if (code == null || code.isEmpty()) {
                throw new AuthTokenValidationError("Invalid request: missing code");
            }

            if (redirectUri == null || redirectUri.isEmpty()) {
                throw new AuthTokenValidationError("Invalid request: missing redirect_uri");
            }

            final TokenMetadata authCodeTokenMetadata = tokenStateService.getTokenMetadata(code);
            final String associateRedirectUri = authCodeTokenMetadata.getMetadata(REDIRECT_URI);
            if (!authCodeTokenMetadata.isAuthCode()) {
                throw new AuthTokenValidationError("Invalid auth_code: not an auth code token");
            } else if (tokenStateService.getTokenExpiration(code) <= System.currentTimeMillis()) {
                throw new AuthTokenValidationError("Invalid auth_code: expired");
            } else if (!associateRedirectUri.equals(redirectUri)) {
                throw new AuthTokenValidationError("Invalid redirect_uri: " + redirectUri);
            }

            final String associatedClientId = authCodeTokenMetadata.getMetadata(CLIENT_ID);
            final String clientId = getRequestParam(CLIENT_ID);
            if (!associatedClientId.equals(clientId)) {
                throw new AuthTokenValidationError("Invalid client_id: " + clientId);
            }

            // Client authentication (defense in depth). A stolen auth code must not be redeemable by
            // a party that merely holds some valid Knox JWT: the JWTProvider (JWTFederationFilter)
            // Bearer path forwards such a request to this endpoint without ever checking
            // client_secret. So the token endpoint independently binds the redemption to the
            // legitimate client here. The caller must prove client identity via EITHER:
            //   - PKCE: a code_verifier matching the challenge stored at authorize time (S256), or
            //   - the client's client_secret (constant-time compared against the stored passcode).
            // A code is rejected when neither is satisfiable.
            final String codeChallenge = authCodeTokenMetadata.getMetadata(CODE_CHALLENGE);
            if (StringUtils.isNotBlank(codeChallenge)) {
                final String codeChallengeMethod = authCodeTokenMetadata.getMetadata(CODE_CHALLENGE_METHOD);
                final String codeVerifier = getRequestParam(CODE_VERIFIER);
                if (StringUtils.isBlank(codeVerifier)) {
                    throw new AuthTokenValidationError("Missing code_verifier");
                }
                if (!validatePKCE(codeVerifier, codeChallenge, codeChallengeMethod)) {
                    throw new AuthTokenValidationError("Invalid code_verifier");
                }
            } else if (!isValidClientSecret(clientId, getRequestParam(CLIENT_SECRET))) {
                throw new AuthTokenValidationError("Invalid client authentication");
            }
            return authCodeTokenMetadata;
        } catch (UnknownTokenException e) {
            throw new AuthTokenValidationError("Unknown auth_code");
        }
    }

    /**
     * Authenticates a confidential client on the token endpoint by validating the presented
     * {@code client_secret} against the stored passcode of the client identified by {@code clientId}.
     * <p>
     * The wire format of {@code client_secret} matches what registration returns and what
     * {@link org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter} expects:
     * {@code Base64(Base64(tokenId)::Base64(rawPasscode))}. The embedded {@code tokenId} must equal
     * {@code clientId}, and {@code HMAC(tokenId, issueTime, userName, rawPasscode)} must equal the
     * stored passcode hash. The comparison is constant-time.
     *
     * @return {@code true} only if the secret is well-formed, bound to {@code clientId}, and matches.
     */
    boolean isValidClientSecret(final String clientId, final String clientSecret) {
        if (StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
            return false;
        }
        try {
            final String[] tokenIdAndPasscode = decodeBase64(clientSecret).split("::");
            if (tokenIdAndPasscode.length != 2) {
                return false;
            }
            final String tokenId = decodeBase64(tokenIdAndPasscode[0]);
            final String rawPasscode = decodeBase64(tokenIdAndPasscode[1]);
            // The client_secret must belong to exactly the client redeeming the code.
            if (!tokenId.equals(clientId)) {
                return false;
            }
            final TokenMetadata clientMetadata = tokenStateService.getTokenMetadata(tokenId);
            final String storedPasscode = clientMetadata == null ? null : clientMetadata.getPasscode();
            if (StringUtils.isBlank(storedPasscode)) {
                return false;
            }
            final long issueTime = tokenStateService.getTokenIssueTime(tokenId);
            final String userName = clientMetadata.getUserName();
            final byte[] computed = tokenMAC.hash(tokenId, issueTime, userName, rawPasscode).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(computed, storedPasscode.getBytes(StandardCharsets.UTF_8));
        } catch (UnknownTokenException | RuntimeException e) {
            return false;
        }
    }

    private String decodeBase64(final String value) {
        return new String(Base64.getDecoder().decode(value.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    private boolean validatePKCE(String codeVerifier, String codeChallenge, String method) {
        // Only S256 is supported. 'plain' provides no protection and is rejected (the authorize
        // endpoint already refuses to store a non-S256 challenge; this is defense in depth).
        if (PKCE_METHOD_S256.equals(method)) {
            try {
                return generateS256Challenge(codeVerifier).equals(codeChallenge);
            } catch (NoSuchAlgorithmException e) {
                return false;
            }
        }
        return false;
    }

    private String generateS256Challenge(String codeVerifier) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private String generateIdToken(JWT accessToken, TokenMetadata authCodeTokenMetadata) throws TokenServiceException {
        // The federated identity id is resolved for the whole grant (auth-code metadata on the
        // authorization_code grant, restored refresh-token metadata on the refresh_token grant), so a
        // federated user keeps their profile claims in the id_token across refresh-token rotations.
        final String federatedIdentityId = resolveFederatedIdentityId();

        if (StringUtils.isNotBlank(federatedIdentityId)) {
            // client_id and nonce live on the auth-code metadata for the authorization_code grant; on
            // the refresh_token grant there is no auth-code metadata, so client_id comes from the
            // request and there is no nonce to echo (nonce binds the original authorization request).
            final String clientId = authCodeTokenMetadata != null
                    ? authCodeTokenMetadata.getMetadata(CLIENT_ID) : getRequestParam(CLIENT_ID);
            final String nonce = authCodeTokenMetadata != null ? authCodeTokenMetadata.getMetadata("nonce") : null;
            return generateFederatedIdToken(accessToken, federatedIdentityId, clientId, nonce);
        } else {
            return generateLocalIdToken(accessToken, authCodeTokenMetadata);
        }
    }

    private String generateFederatedIdToken(JWT accessToken, String fedIdentityId, String clientId, String nonce) throws TokenServiceException {
        final FederatedIdentity federatedIdentity = federatedIdentityService
                .findById(fedIdentityId)
                .orElseThrow(() -> new TokenServiceException("Federated identity not found"));

        final JWTokenAttributesBuilder builder = new JWTokenAttributesBuilder();
        builder.setAlgorithm(accessToken.getSignatureAlgorithm().getName())
                .setUserName(federatedIdentity.getUserId())
                .setIssueTime(System.currentTimeMillis())
                .setExpires(Long.parseLong(accessToken.getExpires()))
                .setIssuer(accessToken.getIssuer())
                .setAudiences(clientId);

        final Map<String, Object> claims = new HashMap<>(federatedIdentity.getAttributes());
        claims.keySet().retainAll(AuthorizeResource.ALLOWED_CLAIMS);
        if (StringUtils.isNotBlank(nonce)) {
            claims.put("nonce", nonce);
        }

        // Optional: indicate source for auditing/logging
        claims.put("federated_idp", federatedIdentity.getProvider());
        claims.put("federated_sub", federatedIdentity.getExternalSubject());
        claims.put("federated_iss", federatedIdentity.getExternalIssuer());

        builder.setCustomAttributes(claims);

        return issueToken(builder).toString();
    }

    private String generateLocalIdToken(JWT accessToken, TokenMetadata authCodeTokenMetadata) throws TokenServiceException {
        final JWTokenAttributesBuilder idTokenAttributesBuilder = new JWTokenAttributesBuilder();
        idTokenAttributesBuilder
                .setAlgorithm(accessToken.getSignatureAlgorithm().getName())
                .setUserName(accessToken.getSubject())
                .setIssueTime(System.currentTimeMillis())
                .setExpires(Long.parseLong(accessToken.getExpires()))
                .setIssuer(accessToken.getIssuer());

        if (authCodeTokenMetadata != null) {
            final String associatedClientId = authCodeTokenMetadata.getMetadata("client_id");
            idTokenAttributesBuilder.setAudiences(associatedClientId);
            final String nonce = authCodeTokenMetadata.getMetadata("nonce");
            if (StringUtils.isNotBlank(nonce)) {
                idTokenAttributesBuilder.setCustomAttributes(Map.of("nonce", nonce));
            }
        } else {
            // If there is no auth code (e.g. refresh token grant), we use the client_id from the request
            idTokenAttributesBuilder.setAudiences(getRequestParam(CLIENT_ID));
        }

        return issueToken(idTokenAttributesBuilder).toString();
    }

    private String generateRefreshToken(JWT accessToken) throws TokenServiceException {
        final String scope = (String) accessToken.getJWTClaimsSet().getClaim(SCOPE);
        if (StringUtils.isNotBlank(scope) && scope.contains(OFFLINE_ACCESS_SCOPE)) {
            return issueRefreshToken(accessToken, scope);
        } else {
            return null;
        }
    }

    private String issueRefreshToken(JWT accessToken, String scope) throws TokenServiceException {
        final JWTokenAttributesBuilder refreshTokenAttributesBuilder = new JWTokenAttributesBuilder();

        final long issueTime = System.currentTimeMillis();
        final long expires = issueTime + refreshTokenTTL;
        final String clientId = getRequestParam(CLIENT_ID);

        refreshTokenAttributesBuilder.setIssuer(accessToken.getIssuer())
                .setUserName(accessToken.getSubject())
                .setAlgorithm(accessToken.getSignatureAlgorithm().getName())
                .setAudiences(clientId)
                .setIssueTime(issueTime)
                .setExpires(expires)
                .setManaged(tokenStateService != null)
                .setType(TokenMetadataType.REFRESH_TOKEN.name());

        final JWT refreshToken = issueToken(refreshTokenAttributesBuilder);

        if (tokenStateService != null) {
            final String tokenId = TokenUtils.getTokenId(refreshToken);
            tokenStateService.addToken(tokenId, issueTime, expires, tokenStateService.getDefaultMaxLifetimeDuration());
            final TokenMetadata metadata = new TokenMetadata(refreshToken.getSubject());
            metadata.setType(TokenMetadataType.REFRESH_TOKEN);
            metadata.add("client_id", clientId);
            metadata.add("scope", scope);
            // Carry the federated identity id onto the refresh token so that, after rotation, the
            // refresh_token grant can still emit federated profile claims in the id_token (the rotated
            // token has no auth code to read the id back from). Blank/absent for local users.
            final String federatedIdentityId = resolveFederatedIdentityId();
            if (StringUtils.isNotBlank(federatedIdentityId)) {
                metadata.add(FEDERATED_IDENTITY_ID, federatedIdentityId);
            }
            tokenStateService.addMetadata(tokenId, metadata);
        }

        return refreshToken.toString();
    }

    private JWT issueToken(final JWTokenAttributesBuilder builder) throws TokenServiceException {
        final JWTokenAuthority ts = getGatewayServices().getService(ServiceType.TOKEN_SERVICE);
        return ts.issueToken(builder.build());
    }

    private String getRequestParam(String paramName) {
        String requestParamValue = request.getParameter(paramName);
        if (requestParamValue == null) {
            requestParamValue = ServletRequestUtils.unwrapHttpServletRequest(request).getParameter(paramName);
        }
        return requestParamValue;
    }

    private static class AuthTokenValidationError extends Exception {
        AuthTokenValidationError(String message) {
            super(message);
        }
    }

    // Package-private so TokenResourceRefreshTokenClientAuthTest can assert the specific failure type.
    static class RefreshTokenValidationError extends Exception {
        RefreshTokenValidationError(String message) {
            super(message);
        }
    }
}

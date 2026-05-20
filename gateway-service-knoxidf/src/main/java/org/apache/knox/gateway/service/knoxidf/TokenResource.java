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
import org.apache.knox.gateway.service.knoxidf.userparams.UserParamsProvider;
import org.apache.knox.gateway.service.knoxidf.userparams.UserParamsProviderFactory;
import org.apache.knox.gateway.service.knoxtoken.PasscodeTokenResourceBase;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.federation.FederatedIdentity;
import org.apache.knox.gateway.services.knoxidf.federation.FederatedIdentityService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.JWTokenAttributesBuilder;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenMetadataType;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
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

import static org.apache.knox.gateway.security.CommonTokenConstants.GRANT_TYPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.AUTH_CODE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.BASE_RESORCE_PATH;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CLIENT_ID;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE_CHALLENGE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE_CHALLENGE_METHOD;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE_VERIFIER;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.FEDERATED_IDENTITY_ID;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.OFFLINE_ACCESS_SCOPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.PKCE_METHOD_PLAIN;
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
    static final String RESOURCE_PATH = BASE_RESORCE_PATH + "/token";
    private UserParamsProvider userParamsProvider;

    @Context
    private HttpServletRequest request;

    @Context
    private ServletContext servletContext;

    private FederatedIdentityService federatedIdentityService;
    private long refreshTokenTTL;

    @Override
    public String getPrefix() {
        return "knoxidf";
    }

    @PostConstruct
    @Override
    public void init() throws ServletException, AliasServiceException, ServiceLifecycleException, KeyLengthException {
        super.init();
        this.servletContext = wrapContextForDefaultParams(this.servletContext);
        this.userParamsProvider = UserParamsProviderFactory.getUserParamsProvider(servletContext);
        final GatewayServices services = (GatewayServices) servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        federatedIdentityService = services.getService(ServiceType.KNOXIDF_FEDERATED_IDENTITY_SERVICE);
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
        return error("invalid_request", "invalid grant type: " + grantType);
    }

    @Override
    protected UserContext buildUserContext(HttpServletRequest request) {
        try {
            final String code = getRequestParam(CODE);
            final TokenMetadata tokenMetadata = tokenStateService.getTokenMetadata(code);
            final String scope = tokenMetadata.getMetadata(SCOPE);
            final Map<String, Object> userParams = userParamsProvider.getParamsFor(tokenMetadata.getUserName(), scope);
            userParams.put(SCOPE, scope);
            return new UserContext(tokenMetadata.getUserName(), null, userParams);
        } catch (UnknownTokenException e) {
            //this should not happen as we have just validated the auth code
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void addArbitraryTokenMetadata(TokenMetadata tokenMetadata) {
        try {
            super.addArbitraryTokenMetadata(tokenMetadata);
            final String code = getRequestParam(CODE);
            if (StringUtils.isNotBlank(code)) {
                final TokenMetadata authCodeTokenMetadata = tokenStateService.getTokenMetadata(code);

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

    @Override
    protected ResponseMap buildResponseMap(JWT token, long expires) throws TokenServiceException {
        final ResponseMap responseMap = super.buildResponseMap(token, expires);

        final String code = getRequestParam(CODE);
        TokenMetadata authCodeTokenMetadata = null;
        if (StringUtils.isNotBlank(code)) {
            try {
                authCodeTokenMetadata = tokenStateService.getTokenMetadata(code);
            } catch (UnknownTokenException e) {
                //NOP
            }
        }

        responseMap.map.put("id_token", generateIdToken(token, authCodeTokenMetadata));

        final String refreshToken = generateRefreshToken(token);
        if (StringUtils.isNotBlank(refreshToken)) {
            responseMap.map.put(REFRESH_TOKEN, refreshToken);
        }

        return responseMap;
    }

    private Response handleRefreshToken() {
        try {
            final String refreshTokenParam = getRequestParam(REFRESH_TOKEN);
            final String refreshTokenId = TokenUtils.getTokenId(refreshTokenParam);
            final TokenMetadata refreshTokenMetadata = tokenStateService.getTokenMetadata(refreshTokenId);
            validateRefreshTokenGrant(refreshTokenParam, refreshTokenId, refreshTokenMetadata);
            // Valid refresh token -> issue new access token and new refresh token (rotation)
            final String userName = refreshTokenMetadata.getUserName();
            final String scope = refreshTokenMetadata.getMetadata(SCOPE);
            final Map<String, Object> userParams = userParamsProvider.getParamsFor(userName, scope);
            userParams.put(SCOPE, scope);

            // Revoke old refresh token (rotation)
            tokenStateService.revokeToken(refreshTokenId);

            // Build new tokens
            final UserContext userContext = new UserContext(userName, null, userParams);
            final TokenResponseContext resp = getTokenResponse(userContext);
            return resp.build();
        } catch (ParseException e) {
            return error("invalid_grant", "Malformed refresh_token");
        } catch (UnknownTokenException e) {
            return error("invalid_grant", "Unknown refresh_token");
        } catch (RefreshTokenValidationError e) {
            return error("Refresh token validation error", e.getMessage());
        }

    }

    private void validateRefreshTokenGrant(String refreshTokenParam, String refreshTokenId, TokenMetadata refreshTokenMetadata) throws UnknownTokenException, RefreshTokenValidationError {
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

        if (tokenStateService.getTokenExpiration(refreshTokenId) <= System.currentTimeMillis()) {
            throw new RefreshTokenValidationError("Invalid grant: Refresh token expired");
        }

        final String associatedClientId = refreshTokenMetadata.getMetadata(CLIENT_ID);
        if (!clientId.equals(associatedClientId)) {
            throw new RefreshTokenValidationError("Invalid grant: client_id mismatch");
        }
    }

    private Response handleAuthorizationCodeFlow() {
        final String code = getRequestParam(CODE);
        final String redirectUri = getRequestParam(REDIRECT_URI);

        try {
            validateAuthCode(code, redirectUri);
            return getAuthenticationToken();
        } catch (AuthTokenValidationError e) {
            return error("Auth code validation error", e.getMessage());
        } finally {
            try {
                tokenStateService.revokeToken(code);
            } catch (UnknownTokenException e) {
                //NOP: this should have been handled by the above UnknownTokenException already
            }
        }
    }

    private void validateAuthCode(String code, String redirectUri) throws AuthTokenValidationError {
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
            } else {
                final String associatedClientId = authCodeTokenMetadata.getMetadata(CLIENT_ID);
                final String clientId = getRequestParam(CLIENT_ID);
                if (!associatedClientId.equals(clientId)) {
                    throw new AuthTokenValidationError("Invalid client_id: " + clientId);
                }
            }

            // PKCE validation
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
            }
        } catch (UnknownTokenException e) {
            throw new AuthTokenValidationError("Unknown auth_code");
        }
    }

    private boolean validatePKCE(String codeVerifier, String codeChallenge, String method) {
        if (PKCE_METHOD_PLAIN.equals(method)) {
            return codeVerifier.equals(codeChallenge);
        } else if (PKCE_METHOD_S256.equals(method)) {
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
        final boolean hasFederatedIdToken = authCodeTokenMetadata != null && StringUtils.isNotBlank(authCodeTokenMetadata.getMetadata(FEDERATED_IDENTITY_ID));

        if (hasFederatedIdToken) {
            return generateFederatedIdToken(accessToken, authCodeTokenMetadata);
        } else {
            return generateLocalIdToken(accessToken, authCodeTokenMetadata);
        }
    }

    private String generateFederatedIdToken(JWT accessToken, TokenMetadata tokenMetadata) throws TokenServiceException {
        final String fedIdentityId = tokenMetadata.getMetadata(FEDERATED_IDENTITY_ID);
        final FederatedIdentity federatedIdentity = federatedIdentityService
                .findById(fedIdentityId)
                .orElseThrow(() -> new TokenServiceException("Federated identity not found"));

        final JWTokenAttributesBuilder builder = new JWTokenAttributesBuilder();
        builder.setAlgorithm(accessToken.getSignatureAlgorithm().getName())
                .setUserName(federatedIdentity.getUserId())
                .setIssueTime(System.currentTimeMillis())
                .setExpires(Long.parseLong(accessToken.getExpires()))
                .setIssuer(accessToken.getIssuer())
                .setAudiences(tokenMetadata.getMetadata(CLIENT_ID));

        final Map<String, Object> claims = new HashMap<>(federatedIdentity.getAttributes());
        claims.keySet().retainAll(AuthorizeResource.ALLOWED_CLAIMS);
        String nonce = tokenMetadata.getMetadata("nonce");
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

    private static class RefreshTokenValidationError extends Exception {
        RefreshTokenValidationError(String message) {
            super(message);
        }
    }
}

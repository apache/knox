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

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.NameBasedGenerator;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.service.knoxtoken.PasscodeTokenResourceBase;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.http.ssl.SSLContexts;
import org.apache.knox.gateway.services.knoxidf.federation.FederatedIdentity;
import org.apache.knox.gateway.services.knoxidf.federation.FederatedIdentityService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenMetadataType;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.JsonUtils;
import org.apache.knox.gateway.util.knoxidf.AuthorizeRequestMetadata;
import org.apache.knox.gateway.util.knoxidf.AuthorizeRequestMetadataStore;
import org.apache.knox.gateway.util.knoxidf.FederatedNonceStore;
import org.apache.knox.gateway.util.knoxidf.FederatedOpConfiguration;
import org.apache.knox.gateway.util.knoxidf.FederatedOpConfigurationStore;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.net.ssl.SSLContext;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.apache.knox.gateway.security.CommonTokenConstants.CLIENT_SECRET;
import static org.apache.knox.gateway.security.CommonTokenConstants.GRANT_TYPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.ALLOWED_SCOPES;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.BASE_RESOURCE_PATH;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CLIENT_ID;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE_CHALLENGE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE_CHALLENGE_METHOD;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.DEFAULT_SCOPES;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.FEDERATED_IDENTITY_ID;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.NONCE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.OFFLINE_ACCESS_SCOPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.PKCE_METHOD_S256;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REDIRECT_URI;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REDIRECT_URIS;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.RESPONSE_TYPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.SCOPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.STATE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFUtils.error;


@Path(AuthorizeResource.RESOURCE_PATH)
public class AuthorizeResource extends PasscodeTokenResourceBase {
    static final String RESOURCE_PATH = BASE_RESOURCE_PATH + "/authorize";
    private static final UUID KNOX_NAMESPACE = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");
    private static final NameBasedGenerator UUID_V5 = Generators.nameBasedGenerator(KNOX_NAMESPACE);
    public static final Set<String> ALLOWED_CLAIMS = Set.of("preferred_username", "email", "email_verified",
            "given_name", "family_name", "name", "locale");

    private static final String UTF_8 = StandardCharsets.UTF_8.name();
    private AuthorizeRequestMetadataStore authorizeRequestMetadataStore;
    private final FederatedOpConfigurationStore federatedOpConfigurationStore = FederatedOpConfigurationStore.getInstance(120000L);
    private final FederatedNonceStore federatedNonceStore = FederatedNonceStore.getInstance(120000L);

    @Context
    private HttpServletRequest request;

    @Context
    private ServletContext servletContext;

    private FederatedIdentityService federatedIdentityService;
    private boolean autoConsentEnabled;

    @Override
    public String getPrefix() {
        return "knoxidf.";
    }

    @PostConstruct
    @Override
    public void init() throws ServletException, AliasServiceException, ServiceLifecycleException, KeyLengthException {
        super.init();
        this.authorizeRequestMetadataStore = AuthorizeRequestMetadataStore.getInstance(tokenTTL);
        final GatewayServices services = (GatewayServices) servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        federatedIdentityService = services.getService(ServiceType.KNOXIDF_FEDERATED_IDENTITY_SERVICE);
        // Skipping user consent is a server-side deployment decision, never a client-supplied
        // request parameter: a client must not be able to bypass the consent screen by sending
        // auto_consent=true.
        this.autoConsentEnabled = "true".equalsIgnoreCase(servletContext.getInitParameter("knoxidf.auto.consent.enabled"));
    }

    @Override
    @GET
    public Response doGet() {
        return authorize();
    }

    @Override
    @POST
    public Response doPost() {
        return authorize();
    }

    private Response authorize() {
        return authorize(request.getParameter(RESPONSE_TYPE), request.getParameter(CLIENT_ID), request.getParameter(REDIRECT_URI),
                request.getParameter(SCOPE), request.getParameter(STATE), request.getParameter(NONCE),
                request.getParameter(CODE_CHALLENGE), request.getParameter(CODE_CHALLENGE_METHOD));
    }

    private Response authorize(String responseType,
                               String clientId,
                               String redirectUri,
                               String scope,
                               String state,
                               String nonce,
                               String codeChallenge,
                               String codeChallengeMethod) {
        final String subject = SubjectUtils.getCurrentEffectivePrincipalName();
        // DEFAULT_SCOPES is an ImmutableSet; copy it into a mutable set so downstream mutation is safe.
        final Set<String> requestedScopes = StringUtils.isBlank(scope) ? new HashSet<>(DEFAULT_SCOPES) : new HashSet<>(Arrays.asList(scope.split("\\s+")));
        final AuthorizeRequestMetadata authorizeRequestMetadata = new AuthorizeRequestMetadata(clientId, subject, responseType, redirectUri, requestedScopes, state, nonce, codeChallenge, codeChallengeMethod);
        final Response verificationErrorResponse = verifyParams(authorizeRequestMetadata);
        if (verificationErrorResponse != null) {
            return verificationErrorResponse;
        }

        if (!hasConsent(authorizeRequestMetadata)) {
            if (autoConsentEnabled) {
                markConsentAccepted(authorizeRequestMetadata);
            } else {
                final String consentAuthState = UUID.randomUUID().toString();
                authorizeRequestMetadataStore.put(consentAuthState, authorizeRequestMetadata);
                final String baseUri = servletContext.getContextPath() + "/authConsent";
                // Every value placed into the consent redirect's query string must be percent-encoded;
                // a client_id containing '&', '=' or '#' would otherwise split or corrupt the URL.
                final String clientIdParam = URLEncoder.encode(clientId, StandardCharsets.UTF_8);
                final String scopeParam = URLEncoder.encode(authorizeRequestMetadata.getJoinedRequestedScopes(), StandardCharsets.UTF_8);
                final String redirect = String.format(Locale.US, "%s?client_id=%s&state=%s&scope=%s", baseUri, clientIdParam, consentAuthState, scopeParam);
                return Response.seeOther(java.net.URI.create(redirect)).build();
            }
        }
        return getAuthCodeFromKnox(authorizeRequestMetadata, null);
    }

    private boolean hasConsent(final AuthorizeRequestMetadata authorizeRequestMetadata) {
        try {
            final TokenMetadata tokenMetadata = tokenStateService.getTokenMetadata(authorizeRequestMetadata.getClientId());
            final String consentKey = consentMetadataKey(authorizeRequestMetadata.getSubject());
            final String storedScopes = tokenMetadata.getMetadataMap().get(consentKey);
            if (storedScopes == null || storedScopes.isEmpty()) {
                return false;
            }
            final Set<String> storedScopeSet = new HashSet<>(Arrays.asList(storedScopes.split("\\s+")));
            return storedScopeSet.containsAll(authorizeRequestMetadata.getRequestedScopes());
        } catch (UnknownTokenException e) {
            //this should not happen as we validated the client_id already
            return false;
        }
    }

    private void markConsentAccepted(AuthorizeRequestMetadata authorizeRequestMetadata) {
        final TokenMetadata consentAcceptedMetadata = new TokenMetadata();
        consentAcceptedMetadata.add(consentMetadataKey(authorizeRequestMetadata.getSubject()), authorizeRequestMetadata.getJoinedRequestedScopes());
        tokenStateService.addMetadata(authorizeRequestMetadata.getClientId(), consentAcceptedMetadata);
    }

    /**
     * Derives the metadata key under which a subject's granted consent scopes are stored. Consent is
     * persisted in {@code KNOX_TOKEN_METADATA.md_name}, which is {@code VARCHAR(32)}; the previous
     * {@code "consentAccepted_" + subject} key overflowed that for realistic subjects (federated
     * UUID subjects, long usernames), silently truncating or failing the write on strict dialects.
     * This derives a fixed-width key {@code "consent_" + first-20-hex-chars(SHA-256(subject))} = 28
     * chars, comfortably within the column. ~80 bits of hash is collision-safe for any realistic
     * user population, and the derivation is uniform for plain usernames and UUID subjects alike.
     * {@link #hasConsent} and {@link #markConsentAccepted} both route through here so read and write
     * always agree on the key.
     */
    static String consentMetadataKey(final String subject) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest((subject == null ? "" : subject).getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder("consent_");
            for (int i = 0; i < 10; i++) { // 10 bytes -> 20 hex chars
                hex.append(String.format(Locale.US, "%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a required algorithm on every JRE; its absence is unrecoverable.
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    private Response getAuthCodeFromKnox(final AuthorizeRequestMetadata authorizeRequestMetadata, final Pair<String, String> federatedTokens) {
        final Response tokenResponse = getAuthenticationToken();
        if (tokenResponse.getStatus() == Response.Status.OK.getStatusCode()) {
            final Map<String, String> tokenResponseMap = JsonUtils.getMapFromJsonString(tokenResponse.getEntity().toString());
            final String tokenId = tokenResponseMap.get(TOKEN_ID);
            decorateAuthCodeToken(tokenId, authorizeRequestMetadata, federatedTokens);
            return redirectToAuthSuccess(authorizeRequestMetadata, tokenId);
        }
        return tokenResponse;
    }

    private Response redirectToAuthSuccess(final AuthorizeRequestMetadata authorizeRequestMetadata, final String code) {
        final String redirectLocation;
        try {
            redirectLocation = authorizeRequestMetadata.getRedirectUri()
                    + "?code=" + URLEncoder.encode(code, UTF_8)
                    + "&state=" + URLEncoder.encode(authorizeRequestMetadata.getState(), UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); //This should never happen with UTF-8
        }
        return Response.seeOther(URI.create(redirectLocation)).build();
    }

    @GET
    @Path("/callback")
    public Response authCallback() throws Exception {
        //This is the callback for the federated OP
        final String federatedAuthCode = request.getParameter(CODE);
        final String state = request.getParameter(STATE);
        if (StringUtils.isBlank(state) || StringUtils.isBlank(federatedAuthCode)) {
            return error("invalid_request", "Missing state or code");
        }
        final AuthorizeRequestMetadata authorizeRequestMetadata = authorizeRequestMetadataStore.get(state);
        if (authorizeRequestMetadata == null) {
            return error("invalid_request", "Unknown or expired state");
        }
        final Set<FederatedOpConfiguration> opConfigs = federatedOpConfigurationStore.get(state);
        final FederatedOpConfiguration federatedOpConfiguration = opConfigs == null ? null : opConfigs.stream().findFirst().orElse(null);
        if (federatedOpConfiguration == null) {
            return error("invalid_request", "No federated OP configuration available for the request");
        }
        // The federated callback state is single-use: invalidate it in both stores now that it has
        // been validated and captured, so a replayed callback with the same state is rejected. The
        // nonce Knox sent to the OP was stashed under the same key (the login-session id == state);
        // retrieve and invalidate it too so it cannot be reused.
        authorizeRequestMetadataStore.remove(state);
        federatedOpConfigurationStore.remove(state);
        final String expectedNonce = federatedNonceStore.get(state);
        federatedNonceStore.remove(state);
        final Pair<String, String> federatedTokens = exchangeFederatedAuthCodeToTokens(federatedAuthCode, federatedOpConfiguration);
        if (StringUtils.isBlank(federatedTokens.getLeft())) {
            return error("invalid_request", "Federated OP did not return an id_token");
        }
        final JWT federatedIdToken = new JWTToken(federatedTokens.getLeft());
        // Verify the OP's id_token (signature/issuer/audience/expiry) before trusting any claim in it.
        final Response validationError = validateFederatedIdToken(federatedIdToken, federatedOpConfiguration);
        if (validationError != null) {
            return validationError;
        }
        // Bind the (now signature-verified) id_token to this authorization request (OIDC Core 3.1.2.1):
        // its nonce claim must equal the nonce Knox generated and sent to the OP for this login session.
        // This is checked only after the token's authenticity is established, so a forged token cannot
        // supply its own matching nonce. A missing expected nonce (e.g. expired/replayed state) or a
        // mismatch fails the flow.
        final Response nonceError = verifyFederatedNonce(expectedNonce, federatedIdToken);
        if (nonceError != null) {
            return nonceError;
        }
        final FederatedIdentity federatedIdentity = resolveFederatedIdentity(federatedIdToken, federatedOpConfiguration.getName());
        return getAuthCodeFromKnox(authorizeRequestMetadata, Pair.of(federatedIdentity.getId(), federatedTokens.getRight()));
    }

    @GET
    @Path("/consentAccepted")
    public Response consentAccepted() throws Exception {
        final String state = request.getParameter(STATE);
        final AuthorizeRequestMetadata authorizeRequestMetadata = authorizeRequestMetadataStore.get(state);
        if (authorizeRequestMetadata == null) {
            return error("invalid_request", "Invalid state");
        }
        // Single-use consent state: invalidate it so the accepted-consent redirect cannot be replayed.
        authorizeRequestMetadataStore.remove(state);
        markConsentAccepted(authorizeRequestMetadata);
        return authorize(authorizeRequestMetadata.getResponseType(),
                authorizeRequestMetadata.getClientId(),
                authorizeRequestMetadata.getRedirectUri(),
                authorizeRequestMetadata.getJoinedRequestedScopes(),
                authorizeRequestMetadata.getState(),
                authorizeRequestMetadata.getNonce(),
                authorizeRequestMetadata.getCodeChallenge(),
                authorizeRequestMetadata.getCodeChallengeMethod());
    }

    @GET
    @Path("/consentDenied")
    public Response consentDenied() throws Exception {
        return Response.status(Response.Status.FORBIDDEN).entity("Consent denied!").build();
    }

    private void decorateAuthCodeToken(final String tokenId, final AuthorizeRequestMetadata authorizeRequestMetadata, final Pair<String, String> federatedTokens) {
        final Map<String, String> authCodeTokenMap = new HashMap<>();
        authCodeTokenMap.put(TokenMetadata.TYPE, TokenMetadataType.AUTH_CODE.name());
        authCodeTokenMap.put(CLIENT_ID, authorizeRequestMetadata.getClientId());
        authCodeTokenMap.put(REDIRECT_URI, authorizeRequestMetadata.getRedirectUri());
        authCodeTokenMap.put(TokenMetadata.USER_NAME, authorizeRequestMetadata.getSubject());
        authCodeTokenMap.put(SCOPE, authorizeRequestMetadata.getJoinedRequestedScopes());
        if (authorizeRequestMetadata.getRequestedScopes().contains(OFFLINE_ACCESS_SCOPE)) {
            authCodeTokenMap.put(OFFLINE_ACCESS_SCOPE, "true");
        }
        if (StringUtils.isNotBlank(authorizeRequestMetadata.getNonce())) {
            authCodeTokenMap.put(NONCE, authorizeRequestMetadata.getNonce());
        }
        if (StringUtils.isNotBlank(authorizeRequestMetadata.getCodeChallenge())) {
            authCodeTokenMap.put(CODE_CHALLENGE, authorizeRequestMetadata.getCodeChallenge());
            // Method is validated to be S256 in verifyParams; store it as-is (no 'plain' default).
            authCodeTokenMap.put(CODE_CHALLENGE_METHOD, authorizeRequestMetadata.getCodeChallengeMethod());
        }
        if (federatedTokens != null) {
            // Persist only the pointer to the (separately stored) federated identity. The OP's
            // access token (federatedTokens.getRight()) is deliberately NOT persisted: nothing reads
            // it back, and storing an OP bearer secret in plaintext token metadata is a secret-at-rest
            // exposure. If a future feature needs it, store it encrypted, not in the clear.
            authCodeTokenMap.put(FEDERATED_IDENTITY_ID, federatedTokens.getLeft());
        }
        tokenStateService.addMetadata(tokenId, new TokenMetadata(authCodeTokenMap));
    }

    private Response verifyParams(final AuthorizeRequestMetadata authorizeRequestMetadata) {
        final Response basicVerificationResponse = authorizeRequestMetadata.verify();
        if (basicVerificationResponse == null) {
            final TokenMetadata tokenMetadata;
            // Verify client ID
            try {
                //This is ok for a POC, but we should cache that later
                tokenMetadata = tokenStateService.getTokenMetadata(authorizeRequestMetadata.getClientId());
            } catch (UnknownTokenException e) {
                return error("invalid_request", "Unknown client_id");
            }

            // Verify redirect URI
            final String storedRedirectUris = tokenMetadata.getMetadata(REDIRECT_URIS);
            if (StringUtils.isBlank(storedRedirectUris)) {
                return error("invalid_request", "Missing stored redirect_uris, cannot authorize the request");
            }
            final Set<String> registeredRedirectUris = new HashSet<>(Arrays.asList(storedRedirectUris.split(",")));
            if (!matchesRedirectUri(authorizeRequestMetadata.getRedirectUri(), registeredRedirectUris)) {
                return error("invalid_request", "Invalid redirect_uri");
            }

            // Verify scope(s)
            final String storedAllowedScopes = tokenMetadata.getMetadata(ALLOWED_SCOPES);
            if (StringUtils.isBlank(storedAllowedScopes)) {
                return error("invalid_scope", "Missing stored allowed_scopes, cannot authorize the request");
            }
            final Set<String> registeredScopes = new HashSet<>(Arrays.asList(storedAllowedScopes.trim().split("\\s+")));
            if (authorizeRequestMetadata.getRequestedScopes().stream().anyMatch(scope -> !registeredScopes.contains(scope))) {
                return error("invalid_scope", "One or more requested scopes are not allowed");
            }

            // PKCE: only the S256 challenge method is supported. 'plain' (and an unspecified method,
            // which OAuth would default to 'plain') offers no protection and is rejected.
            if (StringUtils.isNotBlank(authorizeRequestMetadata.getCodeChallenge())
                    && !PKCE_METHOD_S256.equals(authorizeRequestMetadata.getCodeChallengeMethod())) {
                return error("invalid_request", "Unsupported code_challenge_method; only S256 is supported");
            }

            return null;
        }
        return basicVerificationResponse;
    }

    // Package-private for testability (wildcard path-traversal matching is exercised by
    // AuthorizeResourceRedirectUriMatchTest); not part of the public resource API.
    boolean matchesRedirectUri(String requestedUri, Set<String> registeredUris) {
        final URI requested = parseUri(requestedUri);
        if (requested == null) {
            return false;
        }
        for (String registered : registeredUris) {
            if (registered.endsWith("*")) {
                // Wildcard is a path-prefix match, but the origin (scheme/host/port) must match
                // exactly. Comparing parsed components prevents a bare startsWith from letting
                // "https://good.example*" match "https://good.example.evil.com".
                final URI base = parseUri(registered.substring(0, registered.length() - 1));
                if (base != null && sameOrigin(base, requested)) {
                    // Normalize the requested path before the prefix compare so a traversal segment
                    // cannot escape the registered prefix: a raw startsWith would let
                    // ".../callback/../admin" match ".../callback/*" and deliver the code to /admin.
                    // normalize() collapses "/callback/../admin" to "/admin", which no longer matches.
                    final String basePath = base.normalize().getPath() == null ? "" : base.normalize().getPath();
                    final String reqPath = requested.normalize().getPath() == null ? "" : requested.normalize().getPath();
                    if (reqPath.startsWith(basePath)) {
                        return true;
                    }
                }
            } else if (registered.equals(requestedUri)) {
                return true;
            }
        }
        return false;
    }

    private static URI parseUri(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static boolean sameOrigin(URI a, URI b) {
        return a.getScheme() != null && a.getScheme().equalsIgnoreCase(b.getScheme())
                && a.getHost() != null && a.getHost().equalsIgnoreCase(b.getHost())
                && a.getPort() == b.getPort();
    }

    private Pair<String, String> exchangeFederatedAuthCodeToTokens(String federatedAuthCode, FederatedOpConfiguration opConfig) {
        String federatedIdToken = null;
        String federatedAccessToken = null;
        final Response federatedTokenExchangeResponse = fetchFederatedTokens(federatedAuthCode, opConfig);
        if (federatedTokenExchangeResponse.getStatus() == Response.Status.OK.getStatusCode()) {
            final Map<String, String> federatedTokenExchangeResponseBodyMap = JsonUtils.getMapFromJsonString((String) federatedTokenExchangeResponse.getEntity());
            federatedIdToken = federatedTokenExchangeResponseBodyMap.get("id_token");
            federatedAccessToken = federatedTokenExchangeResponseBodyMap.get("access_token");
            return Pair.of(federatedIdToken, federatedAccessToken);
        } else {
            throw new RuntimeException("Error fetching Federated Tokens from Federated Auth Code: " + federatedTokenExchangeResponse.getEntity());
        }
    }

    /**
     * Resolves the federated OP's client secret for the back-channel token request. An
     * {@code AliasService} credential alias ({@code federated.op.<name>.clientSecret.alias}) is the
     * preferred, secure source and takes precedence: when it resolves to a value, that value is
     * used and the plaintext {@code clientSecret} topology param is never consulted. The plaintext
     * param remains supported as a fallback only when no alias is configured, so existing
     * deployments keep working. If an alias is configured but cannot be resolved we fail closed
     * (return {@code null}) rather than silently leaking through to the plaintext param, so a
     * misconfigured alias surfaces as an auth failure instead of masking the intended secure source.
     */
    private String resolveClientSecret(final FederatedOpConfiguration opConfig) {
        final String alias = opConfig.getClientSecretAlias();
        if (StringUtils.isBlank(alias)) {
            return opConfig.getClientSecret();
        }
        try {
            final AliasService aliasService = getGatewayServices().getService(ServiceType.ALIAS_SERVICE);
            String clusterName = (String) servletContext.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE);
            if (StringUtils.isBlank(clusterName)) {
                clusterName = AliasService.NO_CLUSTER_NAME;
            }
            final char[] secret = aliasService.getPasswordFromAliasForCluster(clusterName, alias, false);
            return secret == null ? null : new String(secret);
        } catch (AliasServiceException e) {
            return null;
        }
    }

    private Response fetchFederatedTokens(final String code, FederatedOpConfiguration opConfig) {
        final List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair(CODE, code));
        params.add(new BasicNameValuePair(REDIRECT_URI, opConfig.getAuthorizeCallback()));
        params.add(new BasicNameValuePair(GRANT_TYPE, "authorization_code"));
        params.add(new BasicNameValuePair(CLIENT_ID, opConfig.getClientId()));
        params.add(new BasicNameValuePair(CLIENT_SECRET, resolveClientSecret(opConfig)));

        try (CloseableHttpClient httpClient = createFederatedHttpClient()) {
            HttpPost post = new HttpPost(opConfig.getTokenEndpoint());
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");
            post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int status = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                return Response.status(status).entity(body).build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    /**
     * Builds the HTTP client used for the back-channel token request to the federated OP. The
     * OP's TLS certificate must be validated against the Gateway's configured truststore
     * ({@code gateway.truststore.*}) rather than the process-wide default, so this mirrors the
     * outbound-dispatch clients. When no Gateway truststore is configured we fall back to the
     * default client (JVM default trust material), never to an unvalidated client.
     */
    private CloseableHttpClient createFederatedHttpClient() throws Exception {
        final KeystoreService keystoreService = getGatewayServices().getService(ServiceType.KEYSTORE_SERVICE);
        final KeyStore trustStore = keystoreService.getTruststoreForHttpClient();
        if (trustStore != null) {
            final SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(trustStore, null).build();
            return HttpClients.custom().setSSLContext(sslContext).build();
        }
        return HttpClients.createDefault();
    }

    /**
     * Verifies the federated OP's id_token before any claim in it is trusted:
     * <ul>
     *   <li>signature against the OP's JWKS and {@code exp}/{@code nbf} (via {@link JWTokenAuthority});</li>
     *   <li>{@code iss} equals the configured OP issuer;</li>
     *   <li>{@code aud} contains our client_id registered at the OP.</li>
     * </ul>
     * Fails closed: if the OP is not configured with a JWKS endpoint, expected issuer and client_id,
     * the token cannot be verified and the federated login is refused.
     *
     * @return an error {@link Response} if verification fails, or {@code null} if the token is valid.
     */
    private Response validateFederatedIdToken(final JWT idToken, final FederatedOpConfiguration opConfig) {
        final String jwksEndpoint = opConfig.getJwksEndpoint();
        final String expectedIssuer = opConfig.getIssuer();
        final String expectedAudience = opConfig.getClientId();
        if (StringUtils.isBlank(jwksEndpoint) || StringUtils.isBlank(expectedIssuer) || StringUtils.isBlank(expectedAudience)) {
            return error("invalid_request", "Federated OP is missing jwks.endpoint/issuer/clientId configuration; cannot verify id_token");
        }

        try {
            final JWTokenAuthority authority = getGatewayServices().getService(ServiceType.TOKEN_SERVICE);
            // A non-null JWS type verifier is required: federated OP id_tokens carry a "typ" header
            // (Keycloak and most OPs set typ=JWT), and the shared token authority rejects any typ'd
            // token outright when no verifier is supplied. Accept "JWT" and a missing typ (typ is
            // optional per RFC 7519) so we interoperate with the range of conformant OPs.
            final JOSEObjectTypeVerifier<SecurityContext> typeVerifier =
                    new DefaultJOSEObjectTypeVerifier<>(new HashSet<>(Arrays.asList(JOSEObjectType.JWT, null)));
            // Verifies the signature against the OP's JWKS and checks exp/nbf.
            if (!authority.verifyToken(idToken, Collections.singleton(new URI(jwksEndpoint)), opConfig.getSignatureAlgorithm(), typeVerifier)) {
                return error("invalid_request", "Federated id_token signature or expiry verification failed");
            }
        } catch (URISyntaxException e) {
            return error("invalid_request", "Invalid jwks.endpoint configured for federated OP");
        } catch (TokenServiceException e) {
            return error("invalid_request", "Federated id_token verification error");
        }

        if (!expectedIssuer.equals(idToken.getIssuer())) {
            return error("invalid_request", "Federated id_token issuer mismatch");
        }

        final String[] audiences = idToken.getAudienceClaims();
        if (audiences == null || !Arrays.asList(audiences).contains(expectedAudience)) {
            return error("invalid_request", "Federated id_token audience mismatch");
        }

        return requireFederatedSubject(idToken);
    }

    /**
     * Verifies the OIDC {@code nonce} binding for a federated login (OIDC Core 3.1.2.1). The
     * {@code expectedNonce} is the value Knox generated for this login session and sent to the OP;
     * it must equal the {@code nonce} claim of the (already signature-verified) id_token. Callers
     * must invoke this only after {@link #validateFederatedIdToken} succeeds so a forged token cannot
     * assert its own nonce.
     *
     * @return an error {@link Response} on absence/mismatch, or {@code null} when the nonce matches.
     */
    Response verifyFederatedNonce(final String expectedNonce, final JWT idToken) {
        if (StringUtils.isBlank(expectedNonce)) {
            return error("invalid_request", "Missing or expired federated login nonce");
        }
        if (!expectedNonce.equals(idToken.getClaim(NONCE))) {
            return error("invalid_request", "Federated id_token nonce mismatch");
        }
        return null;
    }

    /**
     * Enforces that a verified federated id_token carries the {@code sub} claim, which OIDC Core 2
     * marks REQUIRED. Knox derives both the Knox subject and the federated-identity primary key from
     * it, and the identity tables declare {@code external_subject NOT NULL}. A broken or hostile OP
     * that omits {@code sub} would otherwise drive a NOT NULL insert failure -> HTTP 500 on every
     * callback through that OP; reject it as a client/OP error instead. Call only after
     * {@link #validateFederatedIdToken} has established the token's authenticity.
     *
     * @return an error {@link Response} when {@code sub} is absent/blank, or {@code null} otherwise.
     */
    Response requireFederatedSubject(final JWT idToken) {
        if (StringUtils.isBlank(idToken.getSubject())) {
            return error("invalid_request", "Federated id_token is missing the required sub claim");
        }
        return null;
    }

    private FederatedIdentity resolveFederatedIdentity(final JWT jwt, String opName) {
        final String issuer = jwt.getIssuer();
        final String subject = jwt.getSubject();
        return federatedIdentityService.findByProviderAndSubject(opName.toUpperCase(Locale.US), issuer, subject).orElseGet(() -> persistFederatedIdentity(jwt, opName));
    }

    private FederatedIdentity persistFederatedIdentity(final JWT jwt, String opName) {
        final Map<String, String> attributes = jwt.getJWTClaimsSet().getClaims().entrySet().stream()
                .filter(e -> ALLOWED_CLAIMS.contains(e.getKey()))
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.valueOf(e.getValue()),
                        (a, b) -> a,           // defensive: ignore duplicates
                        HashMap::new
                ));
        final FederatedIdentity federatedIdentity = new FederatedIdentity(
                deriveKnoxSubject(jwt.getSubject(), jwt.getIssuer()),  // internal user id (generated)
                opName.toUpperCase(Locale.US),                         // provider
                jwt.getSubject(),                                      // external subject
                jwt.getIssuer(),                                       // external issuer
                Instant.now(),                                         // createdAt
                attributes
        );

        federatedIdentityService.addFederatedIdentity(federatedIdentity);

        return federatedIdentity;
    }

    private String deriveKnoxSubject(String subject, String issuer) {
        final String name = issuer + "|" + subject;
        final UUID uuid = UUID_V5.generate(name.getBytes(StandardCharsets.UTF_8));
        return uuid.toString();
    }
}

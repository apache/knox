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
import com.nimbusds.jose.KeyLengthException;
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
import org.apache.knox.gateway.security.CommonTokenConstants;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.service.knoxtoken.PasscodeTokenResourceBase;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.federation.FederatedIdentity;
import org.apache.knox.gateway.services.knoxidf.federation.FederatedIdentityService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenMetadataType;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.JsonUtils;
import org.apache.knox.gateway.util.knoxidf.AuthorizeRequestMetadata;
import org.apache.knox.gateway.util.knoxidf.AuthorizeRequestMetadataStore;
import org.apache.knox.gateway.util.knoxidf.FederatedOpConfiguration;
import org.apache.knox.gateway.util.knoxidf.FederatedOpConfigurationStore;
import org.apache.knox.gateway.util.knoxidf.KnoxIDFUtils;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.BASE_RESORCE_PATH;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CLIENT_ID;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE_CHALLENGE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE_CHALLENGE_METHOD;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.DEFAULT_SCOPES;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.FEDERATED_IDENTITY_ID;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.NONCE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.OFFLINE_ACCESS_SCOPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REDIRECT_URI;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REDIRECT_URIS;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.RESPONSE_TYPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.SCOPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.STATE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFUtils.error;


@Path(AuthorizeResource.RESOURCE_PATH)
public class AuthorizeResource extends PasscodeTokenResourceBase {
    static final String RESOURCE_PATH = BASE_RESORCE_PATH + "/authorize";
    private static final UUID KNOX_NAMESPACE = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");
    private static final NameBasedGenerator UUID_V5 = Generators.nameBasedGenerator(KNOX_NAMESPACE);
    public static final Set<String> ALLOWED_CLAIMS = Set.of("preferred_username", "email", "email_verified",
            "given_name", "family_name", "name", "locale");

    private static final String UTF_8 = StandardCharsets.UTF_8.name();
    private AuthorizeRequestMetadataStore authorizeRequestMetadataStore;
    private final FederatedOpConfigurationStore federatedOpConfigurationStore = FederatedOpConfigurationStore.getInstance(120000L);

    @Context
    private HttpServletRequest request;

    @Context
    private ServletContext servletContext;

    private FederatedIdentityService federatedIdentityService;

    @PostConstruct
    @Override
    public void init() throws ServletException, AliasServiceException, ServiceLifecycleException, KeyLengthException {
        super.init();
        this.authorizeRequestMetadataStore = AuthorizeRequestMetadataStore.getInstance(tokenTTL);
        final GatewayServices services = (GatewayServices) servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        federatedIdentityService = services.getService(ServiceType.KNOXIDF_FEDERATED_IDENTITY_SERVICE);
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
        final Set<String> requestedScopes = StringUtils.isBlank(scope) ? DEFAULT_SCOPES : new HashSet<>(Arrays.asList(scope.split("\\s+")));
        final AuthorizeRequestMetadata authorizeRequestMetadata = new AuthorizeRequestMetadata(clientId, subject, responseType, redirectUri, requestedScopes, state, nonce, codeChallenge, codeChallengeMethod);
        final Response verificationErrorResponse = verifyParams(authorizeRequestMetadata);
        if (verificationErrorResponse != null) {
            return verificationErrorResponse;
        }

        if (!hasConsent(authorizeRequestMetadata)) {
            if ("true".equalsIgnoreCase(request.getParameter("auto_consent"))) {
                markConsentAccepted(authorizeRequestMetadata);
            } else {
                final String consentAuthState = UUID.randomUUID().toString();
                authorizeRequestMetadataStore.put(consentAuthState, authorizeRequestMetadata);
                final String baseUri = servletContext.getContextPath() + "/authConsent";
                final String scopeParam = URLEncoder.encode(authorizeRequestMetadata.getJoinedRequestedScopes(), StandardCharsets.UTF_8);
                final String redirect = String.format(Locale.US, "%s?client_id=%s&state=%s&scope=%s", baseUri, clientId, consentAuthState, scopeParam);
                return Response.seeOther(java.net.URI.create(redirect)).build();
            }
        }
        return getAuthCodeFromKnox(authorizeRequestMetadata, null);
    }

    private boolean hasConsent(final AuthorizeRequestMetadata authorizeRequestMetadata) {
        try {
            final TokenMetadata tokenMetadata = tokenStateService.getTokenMetadata(authorizeRequestMetadata.getClientId());
            final String consentKey = "consentAccepted_" + authorizeRequestMetadata.getSubject();
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
        consentAcceptedMetadata.add("consentAccepted_" + authorizeRequestMetadata.getSubject(), authorizeRequestMetadata.getJoinedRequestedScopes());
        tokenStateService.addMetadata(authorizeRequestMetadata.getClientId(), consentAcceptedMetadata);
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
        final AuthorizeRequestMetadata authorizeRequestMetadata = authorizeRequestMetadataStore.get(state);
        //at this point, there has to be exactly 1 federated OP config
        final FederatedOpConfiguration federatedOpConfiguration = federatedOpConfigurationStore.get(state).stream().findFirst().get();
        final Pair<String, String> federatedTokens = exchangeFederatedAuthCodeToTokens(federatedAuthCode, federatedOpConfiguration);
        final FederatedIdentity federatedIdentity = resolveFederatedIdentity(federatedTokens.getLeft(), federatedOpConfiguration.getName());
        return getAuthCodeFromKnox(authorizeRequestMetadata, Pair.of(federatedIdentity.getId(), federatedTokens.getRight()));
    }

    @GET
    @Path("/consentAccepted")
    public Response consentAccepted() throws Exception {
        final String state = request.getParameter(STATE);
        final AuthorizeRequestMetadata authorizeRequestMetadata = authorizeRequestMetadataStore.get(state);
        if (authorizeRequestMetadata == null) {
            return error("Consent cannot be accepted", "Invalid state");
        }
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
            authCodeTokenMap.put(CODE_CHALLENGE_METHOD, StringUtils.defaultIfBlank(authorizeRequestMetadata.getCodeChallengeMethod(), "plain"));
        }
        if (federatedTokens != null) {
            authCodeTokenMap.put(FEDERATED_IDENTITY_ID, federatedTokens.getLeft());
            authCodeTokenMap.putAll(KnoxIDFUtils.splitFederatedToken(federatedTokens.getRight(), false));
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

            return null;
        }
        return basicVerificationResponse;
    }

    private boolean matchesRedirectUri(String requestedUri, Set<String> registeredUris) {
        for (String registered : registeredUris) {
            if (registered.endsWith("*")) {
                String prefix = registered.substring(0, registered.length() - 1);
                if (requestedUri.startsWith(prefix)) {
                    return true;
                }
            } else if (registered.equals(requestedUri)) {
                return true;
            }
        }
        return false;
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

    private Response fetchFederatedTokens(final String code, FederatedOpConfiguration opConfig) {
        final List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair(CODE, code));
        params.add(new BasicNameValuePair(REDIRECT_URI, opConfig.getAuthorizeCallback()));
        params.add(new BasicNameValuePair(GRANT_TYPE, "authorization_code"));
        params.add(new BasicNameValuePair(CLIENT_ID, opConfig.getClientId()));
        params.add(new BasicNameValuePair(CLIENT_SECRET, opConfig.getClientSecret()));

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
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

    private FederatedIdentity resolveFederatedIdentity(String federatedIdToken, String opName) throws ParseException {
        final JWT jwt = new JWTToken(federatedIdToken);
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

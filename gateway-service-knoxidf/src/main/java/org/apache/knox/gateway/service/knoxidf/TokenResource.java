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
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;

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
import java.util.HashMap;
import java.util.Map;

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.BASE_RESORCE_PATH;
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

    @Override
    public String getPrefix() {
        return "knoxidf";
    }

    @PostConstruct
    @Override
    public void init() throws ServletException, AliasServiceException, ServiceLifecycleException, KeyLengthException {
        super.init();
        this.servletContext = wrapContextForDefaultParams(this.servletContext);
        this.userParamsProvider = new LdapUserParamsProvider(servletContext.getInitParameter("user.params.provider.ldap.url"));
        final GatewayServices services = (GatewayServices) servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        federatedIdentityService = services.getService(ServiceType.KNOXIDF_FEDERATED_IDENTITY_SERVICE);
    }

    @Override
    @POST
    public Response doPost() {
        final String code = request.getParameter("code");
        final String redirectUri = request.getParameter("redirect_uri");

        final Response paramVerificationErrorResponse = verifyParams(code, redirectUri);
        if (paramVerificationErrorResponse == null) {
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
        return paramVerificationErrorResponse;
    }

    @Override
    protected UserContext buildUserContext(HttpServletRequest request) {
        try {
            final String code = request.getParameter("code");
            final TokenMetadata tokenMetadata = tokenStateService.getTokenMetadata(code);
            final String scope = tokenMetadata.getMetadata("scope");
            final Map<String, Object> userParams = userParamsProvider.getParamsFor(tokenMetadata.getUserName(), scope);
            userParams.put("scope", scope);
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
            final String code = request.getParameter("code");
            final TokenMetadata authCodeTokenMetadata = tokenStateService.getTokenMetadata(code);

            //if the auth code token was a result of a federated OIDC call, we need to save the associated
            //federated identity ID in thw JWT too (so that it can be looked up while fetching user info)
            final String federatedIdentityId = authCodeTokenMetadata.getMetadata("federated_identity_id");
            if (StringUtils.isNotBlank(federatedIdentityId)) {
                tokenMetadata.add("federated_identity_id", federatedIdentityId);
            }
        } catch (UnknownTokenException e) {
            //this should not happen as we have just validated the auth code
            throw new RuntimeException(e);
        }
    }

    @Override
    protected ResponseMap buildResponseMap(JWT token, long expires) throws TokenServiceException {
        final ResponseMap responseMap = super.buildResponseMap(token, expires);
        responseMap.map.put("id_token", generateIdToken(token));
        return responseMap;
    }

    private String generateIdToken(JWT accessToken) throws TokenServiceException {
        try {
            final String code = request.getParameter("code");
            final TokenMetadata tokenMetadata = tokenStateService.getTokenMetadata(code);
            final boolean hasFederatedIdToken = StringUtils.isNotBlank(tokenMetadata.getMetadata("federated_identity_id"));

            if (hasFederatedIdToken) {
                return generateFederatedIdToken(accessToken, tokenMetadata);
            } else {
                return generateLocalIdToken(accessToken, tokenMetadata);
            }
        } catch (UnknownTokenException e) {
            return null; //should not happen
        }
    }

    private String generateLocalIdToken(JWT accessToken, TokenMetadata tokenMetadata) throws TokenServiceException {
        final JWTokenAttributesBuilder idTokenAttributesBuilder = new JWTokenAttributesBuilder();
        idTokenAttributesBuilder
                .setAlgorithm(accessToken.getSignatureAlgorithm().getName())
                .setUserName(accessToken.getSubject())
                .setIssueTime(System.currentTimeMillis())
                .setExpires(Long.parseLong(accessToken.getExpires()))
                .setIssuer(accessToken.getIssuer());
        final String associatedClientId = tokenMetadata.getMetadata("client_id");
        idTokenAttributesBuilder.setAudiences(associatedClientId);
        final String nonce = tokenMetadata.getMetadata("nonce");
        if (StringUtils.isNotBlank(nonce)) {
            idTokenAttributesBuilder.setCustomAttributes(Map.of("nonce", nonce));
        }

        final JWTokenAuthority ts = getGatewayServices().getService(ServiceType.TOKEN_SERVICE);
        final JWT idToken = ts.issueToken(idTokenAttributesBuilder.build());
        return idToken.toString();
    }

    private String generateFederatedIdToken(JWT accessToken, TokenMetadata tokenMetadata) throws TokenServiceException {
        final String fedIdentityId = tokenMetadata.getMetadata("federated_identity_id");
        final FederatedIdentity federatedIdentity = federatedIdentityService
                .findById(fedIdentityId)
                .orElseThrow(() -> new TokenServiceException("Federated identity not found"));

        final JWTokenAttributesBuilder builder = new JWTokenAttributesBuilder();
        builder.setAlgorithm(accessToken.getSignatureAlgorithm().getName())
                .setUserName(federatedIdentity.getUserId())
                .setIssueTime(System.currentTimeMillis())
                .setExpires(Long.parseLong(accessToken.getExpires()))
                .setIssuer(accessToken.getIssuer())
                .setAudiences(tokenMetadata.getMetadata("client_id"));

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

        JWTokenAuthority ts = getGatewayServices().getService(ServiceType.TOKEN_SERVICE);
        return ts.issueToken(builder.build()).toString();
    }

    private Response verifyParams(String code, String redirectUri) {
        if (code == null || code.isEmpty()) {
            return error("invalid_request", "Missing code");
        }

        if (redirectUri == null || redirectUri.isEmpty()) {
            return error("invalid_request", "Missing redirect_uri");
        }

        return null;
    }

    private void validateAuthCode(String code, String redirectUri) throws AuthTokenValidationError {
        try {
            final TokenMetadata tokenMetadata = tokenStateService.getTokenMetadata(code);
            final String associatedClientId = tokenMetadata.getMetadata("client_id");
            final String associateRedirectUri = tokenMetadata.getMetadata("redirect_uri");
            if (!tokenMetadata.isAuthCode()) {
                throw new AuthTokenValidationError("Invalid auth_code: not an auth code token"); //this one or the previous one might be redundant
            } else if (tokenStateService.getTokenExpiration(code) <= System.currentTimeMillis()) {
                throw new AuthTokenValidationError("Invalid auth_code: expired");
            } else if (!associateRedirectUri.equals(redirectUri)) {
                throw new AuthTokenValidationError("Invalid redirect_uri: " + redirectUri);
            } else {
                final String clientId = request.getParameter("client_id");
                if (!associatedClientId.equals(clientId)) {
                    throw new AuthTokenValidationError("Invalid client_id: " + clientId);
                }
            }
        } catch (UnknownTokenException e) {
            throw new AuthTokenValidationError("Unknown auth_code");
        }
    }

    private static class AuthTokenValidationError extends Exception {
        AuthTokenValidationError(String message) {
            super(message);
        }
    }
}

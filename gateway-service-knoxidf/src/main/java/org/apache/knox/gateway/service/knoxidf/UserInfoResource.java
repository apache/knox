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


import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.service.knoxidf.userparams.UserParamsProvider;
import org.apache.knox.gateway.service.knoxidf.userparams.UserParamsProviderFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.federation.FederatedIdentity;
import org.apache.knox.gateway.services.knoxidf.federation.FederatedIdentityService;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.util.JsonUtils;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.BASE_RESOURCE_PATH;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.SCOPE_ATTRIBUTE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.TOKEN_ID_ATTRIBUTE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFUtils.error;


@Path(UserInfoResource.RESOURCE_PATH)
@Produces(MediaType.APPLICATION_JSON)
public class UserInfoResource {

    static final String RESOURCE_PATH = BASE_RESOURCE_PATH + "/userinfo";
    private UserParamsProvider userParamsProvider;

    @Context
    private ServletContext servletContext;

    @Context
    HttpServletRequest request;

    private FederatedIdentityService federatedIdentityService;

    @PostConstruct
    public void init() {
        this.userParamsProvider = UserParamsProviderFactory.getUserParamsProvider(servletContext);
        final GatewayServices services = (GatewayServices) servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        federatedIdentityService = services.getService(ServiceType.KNOXIDF_FEDERATED_IDENTITY_SERVICE);
    }

    public Response doGet() {
        return getUserInfo();
    }

    public Response doPost() {
        throw new UnsupportedOperationException();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserInfo() {
        final String tokenId = request.getAttribute(TOKEN_ID_ATTRIBUTE) == null ? null : request.getAttribute(TOKEN_ID_ATTRIBUTE).toString();
        // Audit the outcome of every /userinfo access exactly once. The resource is the masked
        // bearer-token id (never the raw token); the reason distinguishes the failure modes.
        String outcome = ActionOutcome.FAILURE;
        String detail = "reason=unknown";
        try {
            if (tokenId == null) {
                detail = "reason=missing_token_id";
                return error("invalid_request", "Cannot find tokenId");
            }

            final String scope = request.getAttribute(SCOPE_ATTRIBUTE) == null ? "" : request.getAttribute(SCOPE_ATTRIBUTE).toString();
            final TokenMetadata tokenMetadata;
            try {
                tokenMetadata = getReadonlyTokenStateService().getTokenMetadata(tokenId);
            } catch (UnknownTokenException e) {
                // Expired, revoked, or otherwise unknown bearer token. Per RFC 6750 the protected
                // resource must answer 401 with a WWW-Authenticate: Bearer error="invalid_token"
                // challenge rather than leaking a 500 for what is a client authentication failure.
                detail = "reason=invalid_token";
                return invalidToken("The access token is expired, revoked, or unknown");
            }
            final Map<String, Object> userInfo = new HashMap<>();

            // Check if this token has a federated identity
            final String federatedIdentityId = tokenMetadata.getMetadata("federated_identity_id");

            if (StringUtils.isNotBlank(federatedIdentityId)) {
                // Federated user
                final FederatedIdentity federatedIdentity = federatedIdentityService
                        .findById(federatedIdentityId)
                        .orElse(null);
                if (federatedIdentity == null) {
                    // The token references a federated identity that no longer exists; the bearer token
                    // can no longer be honored, so answer with the RFC 6750 invalid_token challenge.
                    detail = "reason=unknown_federated_identity";
                    return invalidToken("The access token references an unknown federated identity");
                }

                // Include only allowed claims
                Map<String, Object> claims = federatedIdentity.getAttributes().entrySet().stream()
                        .filter(e -> AuthorizeResource.ALLOWED_CLAIMS.contains(e.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                // Mandatory claims for OIDC
                claims.put("sub", federatedIdentity.getUserId()); // internal Knox subject
                claims.put("idp", federatedIdentity.getProvider());

                // Optional: federated info for auditing
                claims.put("federated_sub", federatedIdentity.getExternalSubject());
                claims.put("federated_iss", federatedIdentity.getExternalIssuer());

                // Note: nonce is deliberately NOT returned here. Per OIDC it belongs in the id_token
                // only; echoing it from the UserInfo endpoint is a spec violation and serves no purpose.

                userInfo.putAll(claims);
            } else {
                // Local Knox user
                userInfo.putAll(userParamsProvider.getParamsFor(tokenMetadata.getUserName(), scope));
            }

            outcome = ActionOutcome.SUCCESS;
            detail = "reason=served";
            return Response.ok(JsonUtils.renderAsJsonString(userInfo, true)).build();
        } finally {
            KnoxIDFAudit.audit(Action.ACCESS, KnoxIDFAudit.mask(tokenId), ResourceType.URI, outcome,
                    "event=userinfo " + detail);
        }
    }

    TokenStateService getReadonlyTokenStateService() {
        GatewayServices services = (GatewayServices) servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        return services.getService(ServiceType.TOKEN_STATE_SERVICE);
    }

    /**
     * Builds the RFC 6750 §3 response for a bad bearer token: HTTP 401 with a
     * {@code WWW-Authenticate: Bearer error="invalid_token"} challenge and a matching JSON body.
     */
    static Response invalidToken(final String description) {
        final Response base = error("invalid_token", description, Response.Status.UNAUTHORIZED);
        final String challenge = "Bearer error=\"invalid_token\", error_description=\"" + description + "\"";
        return Response.fromResponse(base).header("WWW-Authenticate", challenge).build();
    }

}


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
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.federation.FederatedIdentity;
import org.apache.knox.gateway.services.knoxidf.federation.FederatedIdentityService;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.util.JsonUtils;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.BASE_RESORCE_PATH;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.SCOPE_ATTRIBUTE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.TOKEN_ID_ATTRIBUTE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFUtils.error;


@Path(UserInfoResource.RESOURCE_PATH)
@Produces(MediaType.APPLICATION_JSON)
public class UserInfoResource {

    static final String RESOURCE_PATH = BASE_RESORCE_PATH + "/userinfo";
    private UserParamsProvider userParamsProvider;

    @Context
    private ServletContext servletContext;

    @Context
    private HttpServletRequest request;

    private FederatedIdentityService federatedIdentityService;

    @PostConstruct
    public void init() {
        this.userParamsProvider = new LdapUserParamsProvider(servletContext.getInitParameter("user.params.provider.ldap.url"));
        final GatewayServices services = (GatewayServices) servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        federatedIdentityService = services.getService(ServiceType.KNOXIDF_FEDERATED_IDENTITY_SERVICE);
    }

    public Response doGet() {
        try {
            return getUserInfo();
        } catch (UnknownTokenException | TokenServiceException e) {
            throw new RuntimeException(e);
        }
    }

    public Response doPost() {
        throw new UnsupportedOperationException();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserInfo() throws UnknownTokenException, TokenServiceException {
        final String tokenId = request.getAttribute(TOKEN_ID_ATTRIBUTE) == null ? null : request.getAttribute(TOKEN_ID_ATTRIBUTE).toString();
        if (tokenId == null) {
            return error("invalid_request", "Cannot find tokenId");
        }

        final String scope = request.getAttribute(SCOPE_ATTRIBUTE) == null ? "" : request.getAttribute(SCOPE_ATTRIBUTE).toString();
        final TokenMetadata tokenMetadata = getReadonlyTokenStateService().getTokenMetadata(tokenId);
        final Map<String, Object> userInfo = new HashMap<>();

        // Check if this token has a federated identity
        final String federatedIdentityId = tokenMetadata.getMetadata("federated_identity_id");

        if (StringUtils.isNotBlank(federatedIdentityId)) {
            // Federated user
            final FederatedIdentity federatedIdentity = federatedIdentityService
                    .findById(federatedIdentityId)
                    .orElseThrow(() -> new TokenServiceException("Federated identity not found"));

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

            // Add nonce if available
            String nonce = tokenMetadata.getMetadata("nonce");
            if (StringUtils.isNotBlank(nonce)) {
                claims.put("nonce", nonce);
            }

            userInfo.putAll(claims);
        } else {
            // Local Knox user
            userInfo.putAll(userParamsProvider.getParamsFor(tokenMetadata.getUserName(), scope));
        }

        return Response.ok(JsonUtils.renderAsJsonString(userInfo, true)).build();
    }

    private TokenStateService getReadonlyTokenStateService() {
        GatewayServices services = (GatewayServices) servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        return services.getService(ServiceType.TOKEN_STATE_SERVICE);
    }

}


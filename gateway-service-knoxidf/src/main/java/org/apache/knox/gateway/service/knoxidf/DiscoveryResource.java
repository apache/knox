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

import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.util.JsonUtils;
import org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.Map;

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.BASE_RESOURCE_PATH;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.TOKEN_EXCHANGE_TOPOLOGY_NAME;

@Path(BASE_RESOURCE_PATH + "/.well-known/openid-configuration")
@Produces(MediaType.APPLICATION_JSON)
public class DiscoveryResource {
    private String currentTopologyName;
    private String tokenExchangeTopologyName;

    @Context
    private ServletContext servletContext;

    @PostConstruct
    public void init() {
        tokenExchangeTopologyName = servletContext.getInitParameter(TOKEN_EXCHANGE_TOPOLOGY_NAME);
        currentTopologyName = (String) servletContext.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE);
    }

    @GET
    public Response getConfig(@Context UriInfo uriInfo) {
        final String baseUrl = uriInfo.getBaseUri().toString();
        final Map<String, Object> config = new HashMap<>();
        config.put("issuer", baseUrl + "knoxidf");
        config.put("authorization_endpoint", baseUrl + AuthorizeResource.RESOURCE_PATH);
        String tokenEndpoint = baseUrl + TokenResource.RESOURCE_PATH;
        String userInfoEndpoint = baseUrl + UserInfoResource.RESOURCE_PATH;
        if (tokenExchangeTopologyName != null) {
            // Literal substitution: the topology name is data, not a regex. replaceAll would treat
            // any regex metacharacter in the topology name as a pattern.
            tokenEndpoint = tokenEndpoint.replace(currentTopologyName, tokenExchangeTopologyName);
            userInfoEndpoint = userInfoEndpoint.replace(currentTopologyName, tokenExchangeTopologyName);
        }
        config.put("token_endpoint", tokenEndpoint);
        config.put("userinfo_endpoint", userInfoEndpoint);
        // Dynamic client registration is served on the current topology (no token-exchange
        // substitution); advertise it so clients can discover it per OIDC Dynamic Client Registration.
        config.put("registration_endpoint", baseUrl + RegistrationResource.RESOURCE_PATH + "/register");
        config.put("jwks_uri", baseUrl + JwksResource.RESOURCE_PATH);
        config.put("response_types_supported", new String[]{KnoxIDFConstants.CODE});
        // REQUIRED by OpenID Connect Discovery 1.0. Knox derives 'sub' as a deterministic UUIDv5 over
        // a fixed namespace and the user identity -- the same for every client -- so the subject
        // identifier type is "public" (not "pairwise").
        config.put("subject_types_supported", new String[]{"public"});
        // The token endpoint reads client credentials only from request parameters (no HTTP Basic):
        // confidential clients send client_secret in the body (client_secret_post); public clients
        // authenticate with PKCE and no secret ("none"). client_secret_basic is intentionally absent
        // because it is not honored.
        config.put("token_endpoint_auth_methods_supported", new String[]{"client_secret_post", "none"});
        // Explicitly false: Knox does not resolve an HTTPS-URL client_id to a fetched Client ID
        // Metadata Document (OAuth CIMD draft, referenced by MCP). This is the spec default when the
        // field is absent, but stating it tells MCP clients to use dynamic client registration
        // (registration_endpoint) rather than a URL client_id. Flip to true only if CIMD is implemented.
        config.put("client_id_metadata_document_supported", Boolean.FALSE);
        config.put("grant_types_supported", new String[]{KnoxIDFConstants.AUTH_CODE, KnoxIDFConstants.REFRESH_TOKEN, KnoxIDFConstants.CLIENT_CREDENTIALS, KnoxIDFConstants.TOKEN_EXCHANGE_GRANT_TYPE});
        config.put("scopes_supported", KnoxIDFConstants.DEFAULT_SCOPES);
        config.put("id_token_signing_alg_values_supported", new String[]{"RS256"});
        // Advertise only S256: AuthorizeResource rejects any other code_challenge_method (including
        // "plain"), so discovery must not claim "plain" support it does not honor.
        config.put("code_challenge_methods_supported", new String[]{KnoxIDFConstants.PKCE_METHOD_S256});
        return Response.ok(JsonUtils.renderAsJsonString(config)).build();
    }

}

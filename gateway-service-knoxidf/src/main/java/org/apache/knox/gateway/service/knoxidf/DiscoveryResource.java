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

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
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
        // The OAuth-common metadata (issuer, endpoints, grant types, ...) is shared with the RFC 8414
        // OAuth 2.0 Authorization Server Metadata document so the two cannot drift; this method then
        // layers on the OpenID Connect-only claims below.
        final Map<String, Object> config =
            AuthorizationServerMetadata.buildOAuthMetadata(uriInfo, currentTopologyName, tokenExchangeTopologyName);

        final String baseUrl = uriInfo.getBaseUri().toString();
        String userInfoEndpoint = baseUrl + UserInfoResource.RESOURCE_PATH;
        if (tokenExchangeTopologyName != null) {
            // Literal substitution: the topology name is data, not a regex. replaceAll would treat
            // any regex metacharacter in the topology name as a pattern.
            userInfoEndpoint = userInfoEndpoint.replace(currentTopologyName, tokenExchangeTopologyName);
        }
        config.put("userinfo_endpoint", userInfoEndpoint);
        // REQUIRED by OpenID Connect Discovery 1.0. Knox derives 'sub' as a deterministic UUIDv5 over
        // a fixed namespace and the user identity -- the same for every client -- so the subject
        // identifier type is "public" (not "pairwise").
        config.put("subject_types_supported", new String[]{"public"});
        // Explicitly false: Knox does not resolve an HTTPS-URL client_id to a fetched Client ID
        // Metadata Document (OAuth CIMD draft, referenced by MCP). This is the spec default when the
        // field is absent, but stating it tells MCP clients to use dynamic client registration
        // (registration_endpoint) rather than a URL client_id. Flip to true only if CIMD is implemented.
        config.put("client_id_metadata_document_supported", Boolean.FALSE);
        config.put("id_token_signing_alg_values_supported", new String[]{"RS256"});
        return Response.ok(JsonUtils.renderAsJsonString(config)).build();
    }

}

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

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.BASE_RESOURCE_PATH;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.TOKEN_EXCHANGE_TOPOLOGY_NAME;

/**
 * RFC 8414 OAuth 2.0 Authorization Server Metadata, served under the KnoxIDF API path
 * ({@code knoxidf/api/v1/.well-known/oauth-authorization-server}) alongside the OIDC discovery
 * document. This is the OAuth-only counterpart of {@link DiscoveryResource}: it advertises the same
 * authorization server but omits the OpenID Connect-only claims. See
 * {@link OAuthServerMetadataRootResource} for the byte-identical document at the topology root.
 */
@Path(BASE_RESOURCE_PATH + "/.well-known/oauth-authorization-server")
@Produces(MediaType.APPLICATION_JSON)
public class OAuthServerMetadataResource {
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
    public Response getMetadata(@Context UriInfo uriInfo) {
        return Response.ok(JsonUtils.renderAsJsonString(
            AuthorizationServerMetadata.buildOAuthMetadata(uriInfo, currentTopologyName, tokenExchangeTopologyName))).build();
    }
}

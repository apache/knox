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

import org.apache.knox.gateway.util.JsonUtils;
import org.apache.knox.gateway.util.knoxidf.FederatedOpConfiguration;

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

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.BASE_RESORCE_PATH;

@Path(BASE_RESORCE_PATH + "/.well-known/openid-configuration")
@Produces(MediaType.APPLICATION_JSON)
public class DiscoveryResource {
    private FederatedOpConfiguration federatedOpConfiguration;

    @Context
    private ServletContext servletContext;

    @PostConstruct
    public void init() {
        this.federatedOpConfiguration = new FederatedOpConfiguration(servletContext);
    }

    @GET
    public Response getConfig(@Context UriInfo uriInfo) {
        final String baseUrl = uriInfo.getBaseUri().toString();
        final Map<String, Object> config = new HashMap<>();
        config.put("issuer", baseUrl.replaceAll("awc-sso", "awc-token") + "knoxidf");
        config.put("authorization_endpoint", baseUrl .replaceAll("noauth", "sso")+ AuthorizeResource.RESOURCE_PATH);
        config.put("token_endpoint", baseUrl
                .replaceAll("awc-sso", "awc-token")
                .replaceAll("noauth", "token") + TokenResource.RESOURCE_PATH);
        config.put("userinfo_endpoint", baseUrl
                .replaceAll("awc-sso", "awc-token")
                .replaceAll("noauth", "token") + UserInfoResource.RESOURCE_PATH);
        config.put("jwks_uri", baseUrl + JwksResource.RESOURCE_PATH);
        config.put("response_types_supported", new String[]{"code"});
        config.put("grant_types_supported", new String[]{"authorization_code"});
        config.put("id_token_signing_alg_values_supported", new String[]{"RS256"});
        return Response.ok(JsonUtils.renderAsJsonString(config)).build();
    }

}

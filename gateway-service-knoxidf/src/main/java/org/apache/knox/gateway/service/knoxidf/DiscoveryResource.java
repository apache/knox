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

import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.util.JsonUtils;
import org.apache.knox.gateway.util.knoxidf.FederatedOpConfiguration;
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

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.BASE_RESORCE_PATH;

@Path(BASE_RESORCE_PATH + "/.well-known/openid-configuration")
@Produces(MediaType.APPLICATION_JSON)
public class DiscoveryResource {

    @Context
    private ServletContext servletContext;

    @GET
    public Response getConfig(@Context UriInfo uriInfo) {
        final String baseUrl = uriInfo.getBaseUri().toString();
        final Map<String, Object> config = new HashMap<>();
        config.put("issuer", baseUrl + "knoxidf");
        config.put("authorization_endpoint", baseUrl + AuthorizeResource.RESOURCE_PATH);
        config.put("token_endpoint", baseUrl + TokenResource.RESOURCE_PATH);
        config.put("userinfo_endpoint", baseUrl + UserInfoResource.RESOURCE_PATH);
        config.put("jwks_uri", baseUrl + JwksResource.RESOURCE_PATH);
        config.put("response_types_supported", new String[]{KnoxIDFConstants.CODE});
        config.put("grant_types_supported", new String[]{KnoxIDFConstants.AUTH_CODE});
        config.put("id_token_signing_alg_values_supported", new String[]{"RS256"});
        return Response.ok(JsonUtils.renderAsJsonString(config)).build();
    }

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file to
 * you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.service.knoxtoken;

import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenMetadataType;
import org.apache.knox.gateway.util.JsonUtils;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.HashMap;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

@Path(APIKeyResource.RESOURCE_PATH)
@Singleton
public class APIKeyResource extends PasscodeTokenResourceBase {
    private static final String TYPE = "type";
    public static final String RESOURCE_PATH = "apikey/api/v1/auth/key";
    public static final String API_KEY = "api_key";
    public static final String KEY_ID = "key_id";

    public static final String PREFIX = "apikey.";

    @Override
    @GET
    @Produces({ APPLICATION_JSON, APPLICATION_XML })
    public Response doGet() {
        return super.doGet();
    }

    @Override
    @POST
    @Produces({ APPLICATION_JSON, APPLICATION_XML })
    public Response doPost() {
        return super.doPost();
    }

    @Override
    protected void addArbitraryTokenMetadata(TokenMetadata tokenMetadata) {
        tokenMetadata.add(TYPE, TokenMetadataType.API_KEY.name());
        super.addArbitraryTokenMetadata(tokenMetadata);
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public Response getAuthenticationToken() {
        UserContext context = buildUserContext(request);
        Response response = checkForInvalidRequestResponse(context);
        if (response != null) {
            return response;
        }

        TokenResponseContext resp = getTokenResponse (context);
        if (resp.responseMap != null) {
            String passcode = (String) resp.responseMap.map.get(PASSCODE);
            String tokenId = resp.responseMap.tokenId;

            final HashMap<String, Object> map = new HashMap<>();
            map.put(KEY_ID, tokenId);
            map.put(API_KEY, passcode);
            addExpiryIfNotNever(map);
            String jsonResponse = JsonUtils.renderAsJsonString(map);
            return resp.responseBuilder.entity(jsonResponse).build();
        }

        if (resp.responseStr != null) {
            return resp.responseBuilder.entity(resp.responseStr).build();
        } else {
            return resp.responseBuilder.build();
        }
    }
}

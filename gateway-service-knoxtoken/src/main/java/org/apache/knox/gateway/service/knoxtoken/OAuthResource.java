/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
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

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.JsonUtils;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.HashMap;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

@Singleton
@Path(OAuthResource.RESOURCE_PATH)
public class OAuthResource extends TokenResource {
    private static TokenServiceMessages log = MessagesFactory.get(TokenServiceMessages.class);
    static final String RESOURCE_PATH = "/{serviceName:.*}/v1/{oauthSegment:(oauth|token)}{path:(/tokens)?}";
    public static final String ISSUED_TOKEN_TYPE = "issued_token_type";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String ISSUED_TOKEN_TYPE_ACCESS_TOKEN_VALUE = "urn:ietf:params:oauth:token-type:access_token";

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
    public Response getAuthenticationToken() {

        Response response = enforceClientCertIfRequired();
        if (response != null) { return response; }

        response = onlyAllowGroupsToBeAddedWhenEnabled();
        if (response != null) { return response; }

        UserContext context = buildUserContext(request);

        response = enforceTokenLimitsAsRequired(context.userName);
        if (response != null) { return response; }

        TokenResponseContext resp = getTokenResponse(context);
        // if the responseMap isn't null then the knoxtoken request was successful
        // if not then there may have been an error and the underlying response
        // builder will communicate those details
        if (resp.responseMap != null) {
            // let's get the subset of the KnoxToken Response needed for OAuth
            String accessToken = resp.responseMap.accessToken;
            String passcode = resp.responseMap.passcode;
            long expires = (long) resp.responseMap.map.get(EXPIRES_IN);
            String tokenType = (String) resp.responseMap.map.get(TOKEN_TYPE);

            // build and return the expected OAuth response
            final HashMap<String, Object> map = new HashMap<>();
            map.put(ACCESS_TOKEN, accessToken);
            map.put(TOKEN_TYPE, tokenType);
            map.put(EXPIRES_IN, expires);
            map.put(ISSUED_TOKEN_TYPE, ISSUED_TOKEN_TYPE_ACCESS_TOKEN_VALUE);
            // let's use the passcode as the refresh token
            map.put(REFRESH_TOKEN, passcode);
            String jsonResponse = JsonUtils.renderAsJsonString(map);
            return resp.responseBuilder.entity(jsonResponse).build();
        }
        // there was an error if we got here - let's surface it appropriately
        // TODO: LJM we may need to translate certain errors into OAuth error messages
        if (resp.responseStr != null) {
            return resp.responseBuilder.entity(resp.responseStr).build();
        }
        else {
            return resp.responseBuilder.build();
        }
    }

    @Override
    protected long getExpiry() {
        long secs = tokenTTL/1000;

        String lifetimeStr = request.getParameter(LIFESPAN);
        if (lifetimeStr == null || lifetimeStr.isEmpty()) {
            if (tokenTTL == -1) {
                return -1;
            }
        }
        else {
            try {
                long lifetime = Duration.parse(lifetimeStr).toMillis()/1000;
                if (tokenTTL == -1) {
                    // if TTL is set to -1 the topology owner grants unlimited lifetime therefore no additional check is needed on lifespan
                    secs = lifetime;
                } else if (lifetime <= tokenTTL/1000) {
                    //this is expected due to security reasons: the configured TTL acts as an upper limit regardless of the supplied lifespan
                    secs = lifetime;
                }
            }
            catch (DateTimeParseException e) {
                log.invalidLifetimeValue(lifetimeStr);
            }
        }
        return secs;
    }
}

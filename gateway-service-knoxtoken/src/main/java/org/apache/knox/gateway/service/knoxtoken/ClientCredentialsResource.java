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

import com.nimbusds.jose.KeyLengthException;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.TokenAlreadyExistsException;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenMetadataType;
import org.apache.knox.gateway.util.JsonUtils;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;

@Path(ClientCredentialsResource.RESOURCE_PATH)
@Singleton
public class ClientCredentialsResource extends PasscodeTokenResourceBase {
    public static final String RESOURCE_PATH = "clientid/api/v1/oauth/credentials";
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    private static final String PREFIX = "clientid.";
    private static final String THIRD_PARTY_APP = "thirdPartyApp";
    private static final String ALLOW_USER_SUPPLIED_CLIENT_ID = "allowUserSuppliedClientId";
    private static final String CLIENT_ID_PARAM = "clientId";

    // A user-supplied clientId becomes the token_id primary key: alphanumerics plus
    // dot/underscore/hyphen, 1..128 chars (the column width).
    private static final Pattern CLIENT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    private boolean thirdPartyApp;
    private boolean allowUserSuppliedClientId;

    @Override
    @PostConstruct
    public void init() throws AliasServiceException, ServiceLifecycleException, KeyLengthException, ServletException {
        super.init();
        final String configuredThirdPartyApp = context.getInitParameter(THIRD_PARTY_APP);
        thirdPartyApp = configuredThirdPartyApp == null ? true : Boolean.parseBoolean(configuredThirdPartyApp);
        allowUserSuppliedClientId = Boolean.parseBoolean(context.getInitParameter(ALLOW_USER_SUPPLIED_CLIENT_ID));
    }

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
        tokenMetadata.add(TokenMetadata.TYPE, TokenMetadataType.CLIENT_ID.name());
        tokenMetadata.add(TokenMetadata.THIRD_PARTY_APP, String.valueOf(thirdPartyApp));
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

        // A present-but-malformed clientId is a client error (400); a blank/absent one falls
        // through to the normal UUID-generating behavior.
        if (allowUserSuppliedClientId) {
            final String suppliedClientId = request.getParameter(CLIENT_ID_PARAM);
            if (StringUtils.isNotBlank(suppliedClientId) && !isValidClientId(suppliedClientId)) {
                return errorResponse(Response.Status.BAD_REQUEST, "invalid_request",
                        "The supplied " + CLIENT_ID_PARAM + " is invalid; it must match " + CLIENT_ID_PATTERN.pattern());
            }
        }

        final TokenResponseContext resp;
        try {
            resp = getTokenResponse(context);
        } catch (TokenAlreadyExistsException e) {
            // The token_id primary key rejected a duplicate clientId; surface it as 409, not 500.
            return errorResponse(Response.Status.CONFLICT, "invalid_client",
                    "The supplied " + CLIENT_ID_PARAM + " already exists");
        }
        if (resp.responseMap != null) {
            String passcode = (String) resp.responseMap.map.get(PASSCODE);
            String tokenId = resp.responseMap.tokenId;

            final HashMap<String, Object> map = new HashMap<>();
            map.put(CLIENT_ID, tokenId);
            map.put(CLIENT_SECRET, passcode);
            addExpiryIfNotNever(map);
            decorateResponseMap(map);
            String jsonResponse = JsonUtils.renderAsJsonString(map);
            return resp.responseBuilder.entity(jsonResponse).build();
        }

        if (resp.responseStr != null) {
            return resp.responseBuilder.entity(resp.responseStr).build();
        } else {
            return resp.responseBuilder.build();
        }
    }

    protected void decorateResponseMap(Map<String, Object> responseMap) {
        //NOP
    }

    /**
     * The caller-supplied {@code clientId} when the feature is enabled and the value is valid,
     * otherwise null (generate a UUID). A present value is already validated in
     * {@link #getAuthenticationToken()}; the recheck here is defensive.
     */
    @Override
    protected String getRequestedTokenId() {
        if (!allowUserSuppliedClientId) {
            return null;
        }
        final String suppliedClientId = request.getParameter(CLIENT_ID_PARAM);
        return (StringUtils.isNotBlank(suppliedClientId) && isValidClientId(suppliedClientId)) ? suppliedClientId : null;
    }

    private static boolean isValidClientId(String clientId) {
        return CLIENT_ID_PATTERN.matcher(clientId).matches();
    }

    private static Response errorResponse(Response.Status status, String error, String description) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("error_description", description);
        return Response.status(status).entity(JsonUtils.renderAsJsonString(body)).build();
    }
}

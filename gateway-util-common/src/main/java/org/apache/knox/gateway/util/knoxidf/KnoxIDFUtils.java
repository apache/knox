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
package org.apache.knox.gateway.util.knoxidf;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.knox.gateway.util.JsonUtils;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


public class KnoxIDFUtils {

    private static final int CHUNK_SIZE = 255;

    public static Map<String, String> splitFederatedToken(String token, boolean idToken) {
        final String prefix = idToken ? KnoxIDFConstants.FEDERATED_ID_TOKEN_PREFIX : KnoxIDFConstants.FEDERATED_ACCESS_TOKEN_PREFIX;
        final Map<String, String> parts = new LinkedHashMap<>();
        int i = 0, part = 1;
        while (i < token.length()) {
            int end = Math.min(i + CHUNK_SIZE, token.length());
            parts.put(prefix + part++, token.substring(i, end));
            i = end;
        }
        return parts;
    }

    public static String joinFederatedToken(Map<String, String> tokenMetadataMap, boolean idToken) {
        final String prefix = idToken ? KnoxIDFConstants.FEDERATED_ID_TOKEN_PREFIX : KnoxIDFConstants.FEDERATED_ACCESS_TOKEN_PREFIX;
        return tokenMetadataMap.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparingInt(k -> Integer.parseInt(k.replace(prefix, "")))
                ))
                .map(Map.Entry::getValue)
                .collect(Collectors.joining());
    }

    public static Response error(String error, String description) {
        final Map<String, String> errorMap = new HashMap<>();
        errorMap.put("error", error);
        errorMap.put("error_description", description);
        return Response.status(Response.Status.UNAUTHORIZED).entity(JsonUtils.renderAsJsonString(errorMap)).build();
    }

    public static String getRequestParamSafe(final HttpServletRequest request, final String key) {
        String value = request.getParameter(key);
        if (value == null) {
            return "";
        } else {
            return StringEscapeUtils.escapeHtml4(value);
        }
    }

    public static Set<FederatedOpConfiguration> fetchEnabledFederatedOpConfigs(final HttpServletRequest request) {
        final ServletContext servletContext = request.getServletContext();
        return servletContext == null ? Collections.emptySet() : new HashSet<>(FederatedOpConfigurationFactory.createFederatedOpConfiguration(servletContext).values());
    }

    public static AuthorizeRequestMetadata buildAuthRequestMetadata(final HttpServletRequest request) {
        final String clientId = request.getParameter(KnoxIDFConstants.CLIENT_ID);
        final String responseType = request.getParameter(KnoxIDFConstants.RESPONSE_TYPE);
        final String redirectUri = request.getParameter(KnoxIDFConstants.REDIRECT_URI);
        final String scope = request.getParameter(KnoxIDFConstants.SCOPE);
        final Set<String> requestedScopes = StringUtils.isBlank(scope) ? KnoxIDFConstants.DEFAULT_SCOPES : new HashSet<>(Arrays.asList(scope.split("\\s+")));
        final String state = request.getParameter(KnoxIDFConstants.STATE);
        final String nonce = request.getParameter(KnoxIDFConstants.NONCE);
        return new AuthorizeRequestMetadata(clientId, null, responseType, redirectUri, requestedScopes, state, nonce);
    }

    public static String buildFederatedOpAuthRedirect(final FederatedOpConfiguration federatedOpConfiguration, final String federatedState) {
        return federatedOpConfiguration.getAuthorizeEndpoint()
                + "?" + KnoxIDFConstants.CLIENT_ID + "=" + federatedOpConfiguration.getClientId()
                + "&" + KnoxIDFConstants.REDIRECT_URI + "=" + federatedOpConfiguration.getAuthorizeCallback()
                + "&" + KnoxIDFConstants.CODE_RESPONSE_TYPE
                + "&" + KnoxIDFConstants.OPENID_SCOPE
                + "&" + KnoxIDFConstants.STATE + "=" + federatedState;
    }

}

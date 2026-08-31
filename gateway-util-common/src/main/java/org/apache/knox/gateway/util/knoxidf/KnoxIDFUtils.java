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
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;


public class KnoxIDFUtils {

    /**
     * Builds an OAuth 2.0 error response, deriving the HTTP status from the error code per
     * RFC 6749 §5.2 (rather than the previous always-401). Most protocol errors are client errors
     * (400); {@code invalid_client} is an authentication failure (401), {@code access_denied} is a
     * policy denial (403), and {@code server_error} is 500. Use the three-arg overload to override
     * the status explicitly when a call site needs a status the code alone does not imply.
     */
    public static Response error(String error, String description) {
        return error(error, description, statusForError(error));
    }

    public static Response error(String error, String description, Response.Status status) {
        final Map<String, String> errorMap = new HashMap<>();
        errorMap.put("error", error);
        errorMap.put("error_description", description);
        return Response.status(status).entity(JsonUtils.renderAsJsonString(errorMap)).build();
    }

    /**
     * Writes an OAuth 2.0 error response (RFC 6749 §5.2 / RFC 8693 §2.2.2) directly to a servlet
     * response as {@code {"error": ..., "error_description": ...}} with a JSON content type. Used by
     * the filter layer, which works with {@link HttpServletResponse} rather than a JAX-RS
     * {@link Response}. Uses {@link HttpServletResponse#setStatus(int)} (not {@code sendError}) so the
     * JSON body is returned verbatim rather than replaced by the servlet container's HTML error page.
     * The caller supplies the HTTP status; the {@code error_description} is omitted when {@code null}.
     */
    public static void writeErrorResponse(HttpServletResponse response, int status,
                                          String error, String description) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        if (description != null) {
            body.put("error_description", description);
        }
        response.getWriter().write(JsonUtils.renderAsJsonString(body));
    }

    private static Response.Status statusForError(String error) {
        if (error == null) {
            return Response.Status.BAD_REQUEST;
        }
        switch (error) {
            case "invalid_client":
                return Response.Status.UNAUTHORIZED;            // 401
            case "access_denied":
                return Response.Status.FORBIDDEN;               // 403
            case "server_error":
                return Response.Status.INTERNAL_SERVER_ERROR;   // 500
            case "temporarily_unavailable":
                return Response.Status.SERVICE_UNAVAILABLE;     // 503
            default:
                // invalid_request, invalid_grant, invalid_scope, unsupported_grant_type,
                // unsupported_response_type, unauthorized_client are all 400s.
                return Response.Status.BAD_REQUEST;             // 400
        }
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
        // Copy DEFAULT_SCOPES into a mutable set: the constant is now an ImmutableSet, and callers
        // downstream may add/remove scopes on the returned set.
        final Set<String> requestedScopes = StringUtils.isBlank(scope) ? new HashSet<>(KnoxIDFConstants.DEFAULT_SCOPES) : new HashSet<>(Arrays.asList(scope.split("\\s+")));
        final String state = request.getParameter(KnoxIDFConstants.STATE);
        final String nonce = request.getParameter(KnoxIDFConstants.NONCE);
        final String codeChallenge = request.getParameter(KnoxIDFConstants.CODE_CHALLENGE);
        final String codeChallengeMethod = request.getParameter(KnoxIDFConstants.CODE_CHALLENGE_METHOD);
        return new AuthorizeRequestMetadata(clientId, null, responseType, redirectUri, requestedScopes, state, nonce, codeChallenge, codeChallengeMethod);
    }

    public static String buildFederatedOpAuthRedirect(final FederatedOpConfiguration federatedOpConfiguration, final String federatedState, final String nonce) {
        // URL-encode every value placed into the query string. client_id and the callback URI
        // (which itself contains ':' '/' '?' etc.), the state and the nonce must be percent-encoded
        // or the OP receives a malformed/parameter-split URL. CODE_RESPONSE_TYPE and OPENID_SCOPE are
        // fixed "key=value" literals with no reserved characters, so they are appended as-is.
        // The nonce binds the returned id_token to this authorization request (OIDC Core 3.1.2.1);
        // it is verified against the id_token's nonce claim when the OP callback is processed.
        return federatedOpConfiguration.getAuthorizeEndpoint()
                + "?" + KnoxIDFConstants.CLIENT_ID + "=" + urlEncode(federatedOpConfiguration.getClientId())
                + "&" + KnoxIDFConstants.REDIRECT_URI + "=" + urlEncode(federatedOpConfiguration.getAuthorizeCallback())
                + "&" + KnoxIDFConstants.CODE_RESPONSE_TYPE
                + "&" + KnoxIDFConstants.OPENID_SCOPE
                + "&" + KnoxIDFConstants.STATE + "=" + urlEncode(federatedState)
                + "&" + KnoxIDFConstants.NONCE + "=" + urlEncode(nonce);
    }

    private static String urlEncode(final String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}

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

import javax.ws.rs.core.Response;
import java.util.Set;

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFUtils.error;

public final class AuthorizeRequestMetadata {
    private final String clientId;
    private final String subject;
    private final String responseType;
    private final String redirectUri;
    private final Set<String> requestedScopes;
    private final String state;
    private final String nonce;
    private final String codeChallenge;
    private final String codeChallengeMethod;

    public AuthorizeRequestMetadata(String clientId, String subject, String responseType, String redirectUri, Set<String> requestedScopes, String state, String nonce) {
        this(clientId, subject, responseType, redirectUri, requestedScopes, state, nonce, null, null);
    }

    public AuthorizeRequestMetadata(String clientId, String subject, String responseType, String redirectUri, Set<String> requestedScopes, String state, String nonce, String codeChallenge, String codeChallengeMethod) {
        this.clientId = clientId;
        this.subject = subject;
        this.responseType = responseType;
        this.redirectUri = redirectUri;
        this.requestedScopes = requestedScopes;
        this.state = state;
        this.nonce = nonce;
        this.codeChallenge = codeChallenge;
        this.codeChallengeMethod = codeChallengeMethod;
    }

    public Response verify() {
        if (responseType == null || responseType.isEmpty()) {
            return error("invalid_request", "Missing response_type");
        } else {
            if (!KnoxIDFConstants.ALLOWED_RESPONSE_TYPES.contains(responseType)) {
                return error("unsupported_response_type", "Unsupported response_type");
            }

            boolean requiresNonce = responseType.contains("id_token");
            if (requiresNonce && (nonce == null || nonce.isEmpty())) {
                return error("invalid_request", "Missing required parameter: nonce");
            }
        }

        if (clientId == null || clientId.isEmpty()) {
            return error("invalid_request", "Missing client_id");
        }

        // Verify redirect URI
        if (redirectUri == null || redirectUri.isEmpty()) {
            return error("invalid_request", "Missing redirect_uri");
        }

        // Verify scope(s)
        if (requestedScopes == null || requestedScopes.isEmpty()) {
            return error("invalid_scope", "Missing scopes");
        } else if (!requestedScopes.contains("openid")) {
            return error("invalid_scope", "Missing required scope: openid");
        }

        return null;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSubject() {
        return subject;
    }

    public String getResponseType() {
        return responseType;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getState() {
        return state;
    }

    public String getNonce() {
        return nonce;
    }

    public String getCodeChallenge() {
        return codeChallenge;
    }

    public String getCodeChallengeMethod() {
        return codeChallengeMethod;
    }

    public Set<String> getRequestedScopes() {
        return requestedScopes;
    }

    public String getJoinedRequestedScopes() {
        return String.join(" ", requestedScopes);
    }

}

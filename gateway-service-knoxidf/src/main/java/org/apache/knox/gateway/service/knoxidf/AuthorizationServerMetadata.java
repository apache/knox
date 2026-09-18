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

import org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants;

import jakarta.ws.rs.core.UriInfo;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the authorization-server metadata shared between the OpenID Connect discovery document
 * (RFC 8414's OIDC superset, OpenID Connect Discovery 1.0) and the OAuth 2.0 Authorization Server
 * Metadata document (RFC 8414). Both {@link DiscoveryResource} and the RFC 8414 resources
 * ({@link OAuthServerMetadataResource}, {@link OAuthServerMetadataRootResource}) call this so the two
 * documents cannot drift: the OAuth document is exactly this common subset, and the OIDC document is
 * this subset plus the OIDC-only claims that {@code DiscoveryResource} layers on top.
 *
 * <p>The metadata advertises only what KnoxIDF actually honors -- e.g. {@code client_secret_post} and
 * {@code none} for token-endpoint auth (never {@code client_secret_basic}, which the token endpoint
 * does not read), and {@code S256} as the only PKCE method (AuthorizeResource rejects {@code plain}).
 */
final class AuthorizationServerMetadata {

    private AuthorizationServerMetadata() {
    }

    /**
     * Builds the OAuth-common metadata subset. OIDC-only fields ({@code userinfo_endpoint},
     * {@code id_token_signing_alg_values_supported}, {@code subject_types_supported}, ...) are
     * intentionally excluded so the RFC 8414 document stays OAuth-only.
     *
     * @param uriInfo                   request URI info; its base URI anchors every advertised endpoint
     * @param currentTopologyName       the topology serving this request (may be {@code null})
     * @param tokenExchangeTopologyName when non-null, the token endpoint is advertised on this topology
     *                                  instead of the current one (see {@link DiscoveryResource})
     */
    static Map<String, Object> buildOAuthMetadata(UriInfo uriInfo,
                                                  String currentTopologyName,
                                                  String tokenExchangeTopologyName) {
        final String baseUrl = uriInfo.getBaseUri().toString();
        final Map<String, Object> metadata = new LinkedHashMap<>();

        // A single issuer identifies the authorization server, regardless of which well-known location
        // (OIDC or OAuth, mirror path or topology root) served this document. RFC 8414 3.3 asks a client
        // to reject metadata whose issuer differs from the identifier it discovered; Knox reuses the one
        // value the OIDC discovery document already advertises so all documents agree.
        metadata.put("issuer", baseUrl + "knoxidf");
        metadata.put("authorization_endpoint", baseUrl + AuthorizeResource.RESOURCE_PATH);

        String tokenEndpoint = baseUrl + TokenResource.RESOURCE_PATH;
        if (tokenExchangeTopologyName != null) {
            // Literal substitution: the topology name is data, not a regex. replaceAll would treat
            // any regex metacharacter in the topology name as a pattern.
            tokenEndpoint = tokenEndpoint.replace(currentTopologyName, tokenExchangeTopologyName);
        }
        metadata.put("token_endpoint", tokenEndpoint);

        // Dynamic client registration is served on the current topology (no token-exchange
        // substitution); advertise it so clients can discover it per OAuth Dynamic Client Registration.
        metadata.put("registration_endpoint", baseUrl + RegistrationResource.RESOURCE_PATH + "/register");
        metadata.put("jwks_uri", baseUrl + JwksResource.RESOURCE_PATH);
        metadata.put("response_types_supported", new String[]{KnoxIDFConstants.CODE});
        // The token endpoint reads client credentials only from request parameters (no HTTP Basic):
        // confidential clients send client_secret in the body (client_secret_post); public clients
        // authenticate with PKCE and no secret ("none"). client_secret_basic is intentionally absent
        // because it is not honored.
        metadata.put("token_endpoint_auth_methods_supported", new String[]{"client_secret_post", "none"});
        metadata.put("grant_types_supported", new String[]{KnoxIDFConstants.AUTH_CODE, KnoxIDFConstants.REFRESH_TOKEN, KnoxIDFConstants.CLIENT_CREDENTIALS, KnoxIDFConstants.TOKEN_EXCHANGE_GRANT_TYPE});
        metadata.put("scopes_supported", KnoxIDFConstants.DEFAULT_SCOPES);
        // Advertise only S256: AuthorizeResource rejects any other code_challenge_method (including
        // "plain"), so discovery must not claim "plain" support it does not honor.
        metadata.put("code_challenge_methods_supported", new String[]{KnoxIDFConstants.PKCE_METHOD_S256});
        return metadata;
    }
}

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

import com.google.common.collect.ImmutableSet;

import java.util.Set;

public interface KnoxIDFConstants {
    String BASE_RESOURCE_PATH = "knoxidf/api/v1";
    String AUTH_CODE = "authorization_code";
    String CLIENT_ID = "client_id";
    String REDIRECT_URI = "redirect_uri";
    String REDIRECT_URIS = "redirect_uris";
    String RESPONSE_TYPE = "response_type";
    // Immutable: an interface field is implicitly public static final, but a mutable HashSet would
    // still let any caller add()/remove() on the shared instance. ImmutableSet forbids that.
    Set<String> ALLOWED_RESPONSE_TYPES = ImmutableSet.of("code", "id_token", "code id_token");
    String SCOPE = "scope";
    String ALLOWED_SCOPES = "allowed_scopes";
    String OFFLINE_ACCESS_SCOPE = "offline_access";
    // Immutable shared constant; callers that need a mutable working set copy it (new HashSet<>(...)).
    Set<String> DEFAULT_SCOPES = ImmutableSet.of("openid", "profile", "email", OFFLINE_ACCESS_SCOPE);
    // The OIDC-standard scope set (OIDC Core 5.4 + offline_access). Used as the default bound on what
    // scopes a client may register when the operator has not configured an explicit whitelist. Matches
    // the baseline registerable set of well-known OPs (Okta/Auth0/Keycloak), so no standards-compliant
    // client is rejected, while non-standard scopes (e.g. 'admin') are refused unless explicitly allowed.
    Set<String> OIDC_STANDARD_SCOPES = ImmutableSet.of("openid", "profile", "email", "address", "phone", OFFLINE_ACCESS_SCOPE);
    String OPENID_SCOPE = SCOPE + "=openid";
    String STATE = "state";
    String CODE = "code";
    String REFRESH_TOKEN = "refresh_token";
    String CLIENT_CREDENTIALS = "client_credentials";
    // RFC 8693 §2.2.1: the token endpoint response must advertise the type of the issued token.
    String ISSUED_TOKEN_TYPE = "issued_token_type";
    //KnoxIDF always mints a JWT so issued_token_type is the JWT URN rather than the generic access_token URN.
    String ISSUED_TOKEN_TYPE_JWT_VALUE = "urn:ietf:params:oauth:token-type:jwt";
    // This is intentionally duplicated from JWTFederationFilter.TOKEN_EXCHANGE rather than shared:
    // it is a fixed standard identifier that will not change, and duplicating it avoids a module
    // dependency on the JWT federation provider.
    String TOKEN_EXCHANGE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    String REFRESH_TOKEN_TTL= "refresh.token.ttl";
    long REFRESH_TOKEN_TTL_DEFAULT = 86400000L; // 1 day
    String CODE_RESPONSE_TYPE = RESPONSE_TYPE + "=" + CODE;
    String NONCE = "nonce";

    String CODE_CHALLENGE = "code_challenge";
    String CODE_CHALLENGE_METHOD = "code_challenge_method";
    String CODE_VERIFIER = "code_verifier";
    String PKCE_METHOD_S256 = "S256";
    String PKCE_METHOD_PLAIN = "plain";

    String TOKEN_ID_ATTRIBUTE = "X-Token-Id";
    String TOKEN_ISS_ATTRIBUTE = "X-Token-Iss";
    String SCOPE_ATTRIBUTE = "X-Token-Scope";

    String FEDERATED_IDENTITY_ID = "federated_identity_id";
    String FEDERATED_OP_CONFIG_PREFIX = "federated.op.";
    String FEDERATED_OP_CONFIG_NAMES = FEDERATED_OP_CONFIG_PREFIX + "names";

    String TOKEN_EXCHANGE_TOPOLOGY_NAME = "token.exchange.topology.name";

    // When false (the default), the dynamic client-registration endpoint refuses anonymous callers
    // even if the topology wires it as 'anon'. Deployments that intend open, unauthenticated
    // registration must explicitly set this to true (see the sample knoxidf topologies).
    String CLIENT_REGISTRATION_ANONYMOUS_ALLOWED = "knoxidf.client.registration.anonymous.allowed";

    // Comma-separated hostnames that, in addition to the hard-coded loopback set (localhost/127.0.0.1/::1),
    // are permitted to use a plain-HTTP redirect_uri during dynamic client registration. Intended for
    // dev setups where the callback host is not literally loopback but is equally trusted (e.g.
    // 'host.docker.internal'). SECURITY: plain HTTP redirects to these hosts traverse a (virtual)
    // network, so only add hosts you fully control. Empty/undefined => today's behavior (loopback only).
    String CLIENT_REGISTRATION_CUSTOM_LOOPBACK_HOSTS = "knoxidf.custom.loopback.hosts";

    // Comma-separated server-side whitelist of scopes a client is permitted to register in its
    // allowed_scopes. A client cannot self-assign a scope outside this set, so it cannot mint tokens
    // carrying a privileged scope name that a downstream service might trust. Undefined/blank =>
    // defaults to the OIDC-standard scope set (see OIDC_STANDARD_SCOPES). 'openid' is always required
    // in a client's allowed_scopes regardless of this list.
    String CLIENT_REGISTRATION_ALLOWED_SCOPES = "knoxidf.registration.allowed.scopes";

    // TrustedOidcIssuerService gateway-level params (read from GatewayConfig / gateway-site.xml)
    String TRUSTED_OIDC_ISSUER_DISCOVERY_CACHE_TTL_SECS =
        "gateway.trustedoidcissuer.discovery.cache.ttl.secs";
    String TRUSTED_OIDC_ISSUER_DISCOVERY_CONNECT_TIMEOUT_MS =
        "gateway.trustedoidcissuer.discovery.connect.timeout.ms";
    String TRUSTED_OIDC_ISSUER_DISCOVERY_READ_TIMEOUT_MS =
        "gateway.trustedoidcissuer.discovery.read.timeout.ms";

    // Default values for gateway-level TrustedOidcIssuerService params
    int TRUSTED_OIDC_ISSUER_DEFAULT_DISCOVERY_CACHE_TTL_SECS = 600;
    int TRUSTED_OIDC_ISSUER_DEFAULT_DISCOVERY_CONNECT_TIMEOUT_MS = 3000;
    int TRUSTED_OIDC_ISSUER_DEFAULT_DISCOVERY_READ_TIMEOUT_MS = 10000;
}

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
package org.apache.knox.gateway.services.knoxidf.trustedoidcissuer;

import org.apache.knox.gateway.services.Service;

import java.util.List;
import java.util.Optional;

/**
 * Gateway service managing the registry of OIDC issuers trusted for JWT
 * verification in Knox. For issuers registered for dynamic JWKS discovery,
 * resolves JWKS URIs via OpenID Connect Discovery 1.0
 * (https://openid.net/specs/openid-connect-discovery-1_0.html) rather than
 * requiring statically configured JWKS endpoints.
 */
public interface TrustedOidcIssuerService extends Service {

  /**
   * Returns {@code true} if the given issuer URL is currently registered as
   * trusted. This is the primary SSRF gate: callers must verify trust before
   * requesting any external resource associated with an issuer.
   */
  boolean isTrusted(String issuerUrl);

  /**
   * Returns {@code true} if the given issuer URL is trusted and configured for
   * OIDC discovery-based JWKS resolution. Returns {@code false} if the issuer
   * is not trusted, or is trusted but configured for static JWKS only.
   * <p>
   * This method combines the trust check with the discovery-mode check.
   * Callers may use it as a single guard without separately calling
   * {@link #isTrusted(String)}.
   */
  boolean isDynamicJwks(String issuerUrl);

  /**
   * Resolves the JWKS URI for the given issuer URL using OIDC discovery.
   * Callers should verify that the issuer is trusted and configured for OIDC
   * discovery via {@link #isDynamicJwks(String)} before calling this method,
   * as that check covers both conditions.
   * <p>
   * Returns {@link Optional#empty()} in all failure cases — including issuer not
   * trusted, dynamic JWKS not configured, discovery document unreachable or
   * malformed, or any internal error. Failure details are logged internally for
   * troubleshooting. Callers should treat an empty result uniformly as
   * "no trusted JWKS URI available" without branching on the failure cause.
   */
  Optional<String> resolveJwksUri(String issuerUrl);

  /**
   * Forces re-resolution of the JWKS URI for the given issuer URL, discarding
   * any previously resolved value. Use this when a resolved JWKS URI is suspected
   * to be stale (for example, if an issuer has changed its JWKS endpoint).
   * Has no effect if the issuer is not registered or does not use OIDC discovery.
   */
  void refreshJwksUri(String issuerUrl);

  /**
   * Registers a new trusted OIDC issuer.
   *
   * @throws IllegalStateException if the maximum registered issuer limit is
   *     reached
   * @throws RuntimeException if registration fails due to a storage error such
   *     as a duplicate issuer URL or a database failure
   */
  void register(TrustedOidcIssuer issuer);

  /**
   * Removes the given issuer URL from the trusted registry and invalidates any
   * previously resolved JWKS URI for that issuer. Returns silently if the issuer
   * is not currently registered.
   *
   * @throws RuntimeException if removal fails due to a storage error
   */
  void deregister(String issuerUrl);

  /**
   * Returns all currently registered trusted issuers.
   */
  List<TrustedOidcIssuer> list();
}

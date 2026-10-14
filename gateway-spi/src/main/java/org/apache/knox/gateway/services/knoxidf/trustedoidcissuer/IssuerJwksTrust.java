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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

/**
 * The TLS trust a single trusted OIDC issuer's discovery and JWKS fetches must use when the issuer
 * is registered with a {@code jwksCa}. The trust holds exactly the issuer's CA bundle, never the
 * gateway-wide truststore, so it does not widen trust for any other TLS client.
 * <p>
 * The handle is stable for the issuer while the underlying CA material may change (re-registration,
 * or a rotated Kubernetes Secret); callers should ask for the current context or socket factory on
 * every connection rather than holding on to them.
 */
public interface IssuerJwksTrust {

  /**
   * @return the current SSL context trusting only the issuer's CA bundle
   * @throws IllegalStateException if the CA bundle is configured but cannot be loaded; callers must
   *     fail the connection rather than fall back to the default truststore
   */
  SSLContext getSslContext();

  /**
   * @return the socket factory of {@link #getSslContext()}; the same instance is returned for as long
   *     as the CA material is unchanged
   * @throws IllegalStateException if the CA bundle is configured but cannot be loaded
   */
  SSLSocketFactory getSslSocketFactory();

  /**
   * Called after a connection failed TLS trust validation. Re-reads the CA bundle if it comes from an
   * external source (a Kubernetes Secret) and that was not already done recently.
   *
   * @return {@code true} if the CA material changed, so retrying the connection is worthwhile
   */
  boolean reloadAfterTlsFailure();
}

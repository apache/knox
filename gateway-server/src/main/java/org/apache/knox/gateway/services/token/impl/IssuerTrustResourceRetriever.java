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
package org.apache.knox.gateway.services.token.impl;

import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jose.util.ResourceRetriever;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.IssuerJwksTrust;
import org.apache.knox.gateway.util.TruststoreSSLContextUtils;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.URL;

/**
 * Retrieves a JWKS over TLS connections that trust only a trusted OIDC issuer's CA bundle.
 * <p>
 * The socket factory is taken from the {@link IssuerJwksTrust} on every retrieval, so a re-read
 * CA bundle applies without rebuilding the JWK source. On a TLS trust failure the bundle is
 * re-read, if it comes from a Kubernetes Secret, and the retrieval is retried once. This happens
 * here rather than around token verification because the retrying and outage-tolerant JWK source
 * layers may not surface the TLS cause.
 */
class IssuerTrustResourceRetriever implements ResourceRetriever {

  private static final TokenAuthorityServiceMessages LOG = MessagesFactory.get(TokenAuthorityServiceMessages.class);

  private final IssuerJwksTrust jwksTrust;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;
  private final int sizeLimitBytes;

  IssuerTrustResourceRetriever(IssuerJwksTrust jwksTrust, int connectTimeoutMs, int readTimeoutMs, int sizeLimitBytes) {
    this.jwksTrust = jwksTrust;
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
    this.sizeLimitBytes = sizeLimitBytes;
  }

  @Override
  public Resource retrieveResource(URL url) throws IOException {
    try {
      return retrieve(url);
    } catch (IOException e) {
      if (TruststoreSSLContextUtils.isTlsTrustFailure(e) && jwksTrust.reloadAfterTlsFailure()) {
        LOG.retryingJwksWithReloadedIssuerCa(url.toString());
        return retrieve(url);
      }
      throw e;
    }
  }

  private Resource retrieve(URL url) throws IOException {
    final SSLSocketFactory socketFactory;
    try {
      socketFactory = jwksTrust.getSslSocketFactory();
    } catch (IllegalStateException e) {
      throw new IOException(e.getMessage(), e);
    }
    return new DefaultResourceRetriever(connectTimeoutMs, readTimeoutMs, sizeLimitBytes, true,
        HostSettingSSLSocketFactory.forUrl(url, socketFactory))
        .retrieveResource(url);
  }
}

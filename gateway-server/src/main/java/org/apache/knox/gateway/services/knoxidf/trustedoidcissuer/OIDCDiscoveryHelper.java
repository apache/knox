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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.knox.gateway.util.MetadataHttpClients;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.TruststoreSSLContextUtils;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Fetches and caches JWKS URIs resolved from OIDC provider discovery documents
 * (/.well-known/openid-configuration). Backed by a Caffeine time-based cache.
 * <p>
 * SSRF gate: {@link #discoverJwksUri(String)} returns {@link Optional#empty()} immediately
 * for any issuer not registered for dynamic JWKS. No HTTP call is ever made for
 * untrusted or static-JWKS issuers.
 * <p>
 * The {@link CloseableHttpClient} is injected at construction time so that tests can
 * supply a mock and verify the full fetch-and-parse code path without overriding methods.
 * Production callers use {@link #buildHttpClient(int, int)} to obtain a properly
 * configured long-lived client.
 * <p>
 * Issuers registered with a {@code jwksCa} are fetched with a short-lived client that trusts
 * only the issuer's CA bundle (see {@link TrustedOidcIssuerService#getJwksTrust(String)}). On a
 * TLS trust failure the bundle is re-read, if it comes from a Kubernetes Secret, and the fetch is
 * retried once. Discovery results are cached, so these clients are built rarely.
 */
class OIDCDiscoveryHelper {

  private static final TrustedOidcIssuerServiceMessages LOG =
      MessagesFactory.get(TrustedOidcIssuerServiceMessages.class);

  private static final String USER_AGENT = "Apache-Knox-OIDCDiscovery/1.0";

  private final TrustedOidcIssuerService trustedIssuers;
  // OIDC discovery document cache: issuerUrl → jwks_uri resolved from discovery endpoint.
  // Entries expire after cacheTtlSeconds and are re-fetched lazily on the next access.
  private final Cache<String, String> discoveryDocumentCache;
  private final CloseableHttpClient httpClient;
  // Builds a client trusting only the given SSL context, for issuers registered with a jwksCa.
  private final Function<SSLContext, CloseableHttpClient> pinnedHttpClientFactory;

  /**
   * Creates an {@code OIDCDiscoveryHelper} with the supplied HTTP client. Use
   * {@link #buildHttpClient(int, int)} to obtain the production-configured client.
   */
  OIDCDiscoveryHelper(TrustedOidcIssuerService trustedIssuers, long cacheTtlSeconds,
      CloseableHttpClient httpClient) {
    this(trustedIssuers, cacheTtlSeconds, httpClient,
        sslContext -> buildHttpClient(GatewayConfig.TRUSTED_OIDC_ISSUER_DISCOVERY_CONNECT_TIMEOUT_MS_DEFAULT,
            GatewayConfig.TRUSTED_OIDC_ISSUER_DISCOVERY_READ_TIMEOUT_MS_DEFAULT, sslContext));
  }

  /**
   * Creates an {@code OIDCDiscoveryHelper} with the supplied HTTP client, and a factory for the
   * clients used with issuers registered with a {@code jwksCa}. Use
   * {@link #buildHttpClient(int, int, SSLContext)} to build the production-configured ones.
   */
  OIDCDiscoveryHelper(TrustedOidcIssuerService trustedIssuers, long cacheTtlSeconds,
      CloseableHttpClient httpClient, Function<SSLContext, CloseableHttpClient> pinnedHttpClientFactory) {
    this.trustedIssuers = trustedIssuers;
    this.discoveryDocumentCache = Caffeine.newBuilder()
        .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
        .build();
    this.httpClient = httpClient;
    this.pinnedHttpClientFactory = pinnedHttpClientFactory;
  }

  /** Builds the production-configured client for OIDC discovery fetches. */
  static CloseableHttpClient buildHttpClient(int connectTimeoutMs, int readTimeoutMs) {
    return MetadataHttpClients.pooled(connectTimeoutMs, readTimeoutMs, MetadataHttpClients.DEFAULT_RETRIES);
  }

  /** Builds a short-lived client for one discovery fetch that trusts only the given context. */
  static CloseableHttpClient buildHttpClient(int connectTimeoutMs, int readTimeoutMs, SSLContext sslContext) {
    return MetadataHttpClients.trusting(connectTimeoutMs, readTimeoutMs, sslContext, MetadataHttpClients.DEFAULT_RETRIES);
  }

  /**
   * Returns the JWKS URI for the given issuer URL, resolving it via OIDC discovery if
   * not already cached. Returns {@link Optional#empty()} immediately without any HTTP
   * call if the issuer is not registered for dynamic JWKS — this is the primary SSRF gate.
   * <p>
   * {@code Cache.get(key, mappingFunction)} is atomic per key: concurrent cache misses for the
   * same issuer block on a single {@link #fetchJwksUri} call and share its result. If
   * {@link #fetchJwksUri} returns null (on any error), Caffeine does not cache null, so the
   * next call retries transparently.
   */
  Optional<String> discoverJwksUri(String issuerUrl) {
    if (!trustedIssuers.isDynamicJwks(issuerUrl)) {
      return Optional.empty();
    }
    return Optional.ofNullable(discoveryDocumentCache.get(issuerUrl, this::fetchJwksUri));
  }

  /**
   * Evicts the cached JWKS URI for the given issuer so the next call to
   * {@link #discoverJwksUri(String)} re-fetches from the discovery endpoint.
   */
  void invalidate(String issuerUrl) {
    discoveryDocumentCache.invalidate(issuerUrl);
  }

  /**
   * Closes the long-lived HTTP client.
   */
  void close() throws IOException {
    discoveryDocumentCache.invalidateAll();
    httpClient.close();
  }

  /**
   * Fetches the JWKS URI by retrieving and parsing the OIDC discovery document for the
   * given issuer. The discovery URL is constructed by stripping any trailing slash from
   * the issuer URL and appending {@code /.well-known/openid-configuration}.
   * Returns null on any error so Caffeine does not cache the failure and the next call retries.
   */
  String fetchJwksUri(String issuerUrl) {
    final String discoveryUrl = issuerUrl.replaceAll("/$", "") + "/.well-known/openid-configuration";
    final String body = httpGet(issuerUrl, discoveryUrl);
    if (body == null) {
      return null;
    }
    try {
      final URI jwksUri = OIDCProviderMetadata.parse(body).getJWKSetURI();
      if (jwksUri == null) {
        // Defensive: OIDC spec requires jwks_uri; Nimbus 11.x throws ParseException if absent,
        // but a non-compliant or future-lenient implementation could return null here.
        LOG.errorParsingDiscoveryDocument(issuerUrl,
            "discovery document contains no jwks_uri", null);
        return null;
      }
      return jwksUri.toString();
    } catch (Exception e) {
      LOG.errorParsingDiscoveryDocument(issuerUrl, e.getMessage(), e);
      return null;
    }
  }

  /**
   * Executes a GET request against the given URL and returns the response body as a string.
   * Uses a client trusting only the issuer's CA bundle if the issuer has one, retrying once with
   * the re-read bundle after a TLS trust failure.
   * Logs any failure and returns null so the caller knows not to cache the result.
   */
  private String httpGet(String issuerUrl, String url) {
    try {
      final Optional<IssuerJwksTrust> jwksTrust = trustedIssuers.getJwksTrust(issuerUrl);
      if (jwksTrust.isEmpty()) {
        return execute(httpClient, url);
      }
      try {
        return executePinned(jwksTrust.get(), url);
      } catch (IOException e) {
        if (TruststoreSSLContextUtils.isTlsTrustFailure(e) && jwksTrust.get().reloadAfterTlsFailure()) {
          LOG.retryingWithReloadedIssuerCa(issuerUrl, url);
          return executePinned(jwksTrust.get(), url);
        }
        throw e;
      }
    } catch (Exception e) {
      LOG.errorFetchingDiscoveryDocument(issuerUrl, url, e.getMessage(), e);
      return null;
    }
  }

  /**
   * Executes the GET with a client built for this fetch alone and closed right after it.
   * <p>
   * The pinned clients are deliberately not pooled or kept per issuer. Discovery documents are
   * cached for the configured TTL, so a pinned fetch happens about once per TTL per issuer (plus
   * at most one retry after a TLS trust failure), and a long-lived client would rarely get to
   * reuse a connection. A per-issuer client would also have to be rebuilt whenever the issuer's
   * CA bundle changes and closed when the issuer is deregistered. The cost of this approach, a
   * new connection and TLS handshake per fetch, is negligible at that rate. The issuer's JWKS is
   * fetched separately by the token authority, which caches the JWK source per issuer trust.
   */
  private String executePinned(IssuerJwksTrust jwksTrust, String url) throws IOException {
    try (CloseableHttpClient pinnedHttpClient = pinnedHttpClientFactory.apply(jwksTrust.getSslContext())) {
      return execute(pinnedHttpClient, url);
    }
  }

  private static String execute(CloseableHttpClient client, String url) throws IOException {
    final HttpGet request = new HttpGet(url);
    request.setHeader("User-Agent", USER_AGENT);
    try (CloseableHttpResponse response = client.execute(request)) {
      final int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode != 200) {
        throw new IOException("HTTP " + statusCode);
      }
      return EntityUtils.toString(response.getEntity());
    }
  }
}

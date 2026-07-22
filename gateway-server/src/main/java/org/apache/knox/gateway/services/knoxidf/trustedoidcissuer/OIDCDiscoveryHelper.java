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
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
 */
class OIDCDiscoveryHelper {

  private static final TrustedOidcIssuerServiceMessages LOG =
      MessagesFactory.get(TrustedOidcIssuerServiceMessages.class);

  private static final String USER_AGENT = "Apache-Knox-OIDCDiscovery/1.0";
  private static final int HTTP_RETRY_COUNT = 2;
  // Idle connections in the pool are closed after this duration so the next cache-miss
  // fetch always goes through a fresh connection rather than a potentially stale one.
  private static final long IDLE_EVICTION_SECONDS = 60L;

  private final TrustedOidcIssuerService trustedIssuers;
  // OIDC discovery document cache: issuerUrl → jwks_uri resolved from discovery endpoint.
  // Entries expire after cacheTtlSeconds and are re-fetched lazily on the next access.
  private final Cache<String, String> discoveryDocumentCache;
  private final CloseableHttpClient httpClient;

  /**
   * Creates an {@code OIDCDiscoveryHelper} with the supplied HTTP client. Use
   * {@link #buildHttpClient(int, int)} to obtain the production-configured client.
   */
  OIDCDiscoveryHelper(TrustedOidcIssuerService trustedIssuers, long cacheTtlSeconds,
      CloseableHttpClient httpClient) {
    this.trustedIssuers = trustedIssuers;
    this.discoveryDocumentCache = Caffeine.newBuilder()
        .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
        .build();
    this.httpClient = httpClient;
  }

  /**
   * Builds a production-configured {@link CloseableHttpClient} for OIDC discovery fetches.
   * <p>
   * {@code requestSentRetryEnabled=true}: Discovery endpoints are GET-only (idempotent by RFC 7231
   * §4.2.2), so retrying after the request was sent is safe and covers the most common
   * failure mode — connection reset mid-response.
   * <p>
   * {@code evictIdleConnections} + {@code evictExpiredConnections}: the client is held for the
   * gateway process lifetime. Without eviction, pooled connections become stale when the remote
   * server or a network middlebox closes them silently, causing the next fetch to fail with a
   * {@code NoHttpResponseException} before the retry handler can save it.
   */
  static CloseableHttpClient buildHttpClient(int connectTimeoutMs, int readTimeoutMs) {
    final RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(connectTimeoutMs)
        .setSocketTimeout(readTimeoutMs)
        .build();
    return HttpClients.custom()
        .setDefaultRequestConfig(requestConfig)
        .setRetryHandler(new DefaultHttpRequestRetryHandler(HTTP_RETRY_COUNT, true))
        .evictIdleConnections(IDLE_EVICTION_SECONDS, TimeUnit.SECONDS)
        .evictExpiredConnections()
        .build();
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
   * Logs any failure and returns null so the caller knows not to cache the result.
   */
  private String httpGet(String issuerUrl, String url) {
    final HttpGet request = new HttpGet(url);
    request.setHeader("User-Agent", USER_AGENT);
    try (CloseableHttpResponse response = httpClient.execute(request)) {
      final int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode != 200) {
        LOG.errorFetchingDiscoveryDocument(issuerUrl, url, "HTTP " + statusCode,
            new java.io.IOException("Non-200 status: " + statusCode));
        return null;
      }
      return EntityUtils.toString(response.getEntity());
    } catch (Exception e) {
      LOG.errorFetchingDiscoveryDocument(issuerUrl, url, e.getMessage(), e);
      return null;
    }
  }
}

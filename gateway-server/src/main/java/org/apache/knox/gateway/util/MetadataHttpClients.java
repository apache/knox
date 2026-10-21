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
package org.apache.knox.gateway.util;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.util.concurrent.TimeUnit;

/**
 * Builds the HTTP clients used to fetch identity-provider metadata - OIDC discovery documents and
 * JWK sets. Both are small, idempotent JSON GETs over TLS, so they want the same configuration.
 * <p>
 * The number of in-client retries is a parameter rather than a constant: a caller with retry
 * layers above it wants {@link #NO_RETRIES}, while one that is the only layer wants
 * {@link #DEFAULT_RETRIES}.
 * <p>
 * Apache HttpClient is used rather than {@link java.net.HttpURLConnection} because it
 * layers TLS over an already-connected socket and hands it the target host, so a
 * JSSE provider that takes the endpoint identification hostname from the socket, BouncyCastle's
 * FIPS provider among them has a hostname to check. {@code HttpURLConnection} creates its socket
 * without one.
 */
public final class MetadataHttpClients {

  /** No retries inside the client, for callers that already retry around it. */
  public static final int NO_RETRIES = 0;

  /** Retries for callers that are the only retry layer. */
  public static final int DEFAULT_RETRIES = 2;

  /*
   * The pool defaults - 2 per route - are far too small to share with a lease wait, so both are
   * set explicitly and the wait is bounded below.
   */
  private static final int CONNECTION_LEASE_TIMEOUT_MS = 2000;

  private static final int MAX_CONNECTIONS_PER_ROUTE = 10;
  private static final int MAX_CONNECTIONS_TOTAL = 40;

  /*
   * Idle connections in the pool are closed after this long, so the next fetch goes through a
   * fresh connection rather than one the remote end or a middlebox has silently closed.
   */
  private static final long IDLE_EVICTION_SECONDS = 60L;

  private MetadataHttpClients() {
  }

  /**
   * A client intended to be held for the gateway's lifetime, with idle and expired connections
   * evicted from its pool. The caller is responsible for closing it.
   */
  public static CloseableHttpClient pooled(int connectTimeoutMs, int readTimeoutMs, int retries) {
    return builder(connectTimeoutMs, readTimeoutMs, retries)
        .evictIdleConnections(IDLE_EVICTION_SECONDS, TimeUnit.SECONDS)
        .evictExpiredConnections()
        .build();
  }

  /**
   * A short-lived client that trusts only the given SSL context, for one fetch. Hostname
   * verification stays on, and it holds no eviction thread because the caller closes it promptly.
   */
  public static CloseableHttpClient trusting(int connectTimeoutMs, int readTimeoutMs, SSLContext sslContext,
                                            int retries) {
    return builder(connectTimeoutMs, readTimeoutMs, retries)
        .setSSLContext(sslContext)
        .build();
  }

  /**
   * As {@link #trusting(int, int, SSLContext, int)}, for callers that already hold the socket factory
   * rather than the context it came from.
   */
  public static CloseableHttpClient trusting(int connectTimeoutMs, int readTimeoutMs, SSLSocketFactory socketFactory,
                                            int retries) {
    return builder(connectTimeoutMs, readTimeoutMs, retries)
        .setSSLSocketFactory(new SSLConnectionSocketFactory(socketFactory,
            SSLConnectionSocketFactory.getDefaultHostnameVerifier()))
        .build();
  }

  /*
   * requestSentRetryEnabled=true: these endpoints are GET-only and so idempotent (RFC 7231
   * 4.2.2), which makes retrying after the request was sent safe and covers the common failure -
   * a connection reset mid-response.
   */
  private static HttpClientBuilder builder(int connectTimeoutMs, int readTimeoutMs, int retries) {
    final RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(connectTimeoutMs)
        .setSocketTimeout(readTimeoutMs)
        /*
         * A separate, non-zero value: without a lease timeout the wait for a pooled connection is
         * unbounded however short the other two are, and reusing the connect timeout would inherit
         * a configured zero and mean "wait for ever" again.
         */
        .setConnectionRequestTimeout(CONNECTION_LEASE_TIMEOUT_MS)
        .build();
    return HttpClients.custom()
        .setDefaultRequestConfig(requestConfig)
        /*
         * Honour https.protocols / https.cipherSuites, which the HttpURLConnection path these
         * clients replace applied automatically. An explicitly supplied SSL context still wins.
         */
        .useSystemProperties()
        /*
         * No redirects. These are signing-key and metadata endpoints, which have no reason to
         * redirect, and HttpClient would otherwise follow one across schemes - including https to
         * http, which HttpURLConnection refuses. A downgraded hop would fetch signing keys in
         * cleartext and, on a pinned-CA fetch, bypass the pinned bundle entirely.
         */
        .disableRedirectHandling()
        .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
        .setMaxConnTotal(MAX_CONNECTIONS_TOTAL)
        .setRetryHandler(new DefaultHttpRequestRetryHandler(retries, true));
  }
}

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

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class OIDCDiscoveryHelperTest {

  private static final String ISSUER = "https://issuer.example.com";
  private static final String ISSUER_WITH_SLASH = "https://issuer.example.com/";
  private static final String JWKS_URI = "https://issuer.example.com/jwks";
  private static final long CACHE_TTL = 600L;

  // Minimal valid OIDC discovery document (all required fields per OpenID Connect Discovery 1.0)
  private static final String VALID_DISCOVERY_JSON = "{"
      + "\"issuer\":\"" + ISSUER + "\","
      + "\"authorization_endpoint\":\"https://issuer.example.com/authorize\","
      + "\"jwks_uri\":\"" + JWKS_URI + "\","
      + "\"response_types_supported\":[\"code\"],"
      + "\"subject_types_supported\":[\"public\"],"
      + "\"id_token_signing_alg_values_supported\":[\"RS256\"]"
      + "}";

  // Discovery doc where jwks_uri is absent; Nimbus throws ParseException for this.
  private static final String DISCOVERY_JSON_NO_JWKS_URI = "{"
      + "\"issuer\":\"" + ISSUER + "\","
      + "\"authorization_endpoint\":\"https://issuer.example.com/authorize\","
      + "\"response_types_supported\":[\"code\"],"
      + "\"subject_types_supported\":[\"public\"],"
      + "\"id_token_signing_alg_values_supported\":[\"RS256\"]"
      + "}";

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  /** Returns a mock TrustedOidcIssuerService with fixed isTrusted / isDynamicJwks behavior. */
  private static TrustedOidcIssuerService trustedDynamic() {
    return stubService(true, true);
  }

  private static TrustedOidcIssuerService trustedStatic() {
    return stubService(true, false);
  }

  private static TrustedOidcIssuerService untrusted() {
    return stubService(false, false);
  }

  private static TrustedOidcIssuerService stubService(boolean trusted, boolean dynamicJwks) {
    return stubService(trusted, dynamicJwks, Optional::empty);
  }

  private static TrustedOidcIssuerService stubService(boolean trusted, boolean dynamicJwks,
      Supplier<Optional<IssuerJwksTrust>> jwksTrust) {
    return new TrustedOidcIssuerService() {
      @Override public boolean isTrusted(String url) { return trusted; }
      @Override public boolean isDynamicJwks(String url) { return dynamicJwks; }
      @Override public Optional<String> resolveJwksUri(String url) { return Optional.empty(); }
      @Override public void refreshJwksUri(String url) { }
      @Override public Optional<IssuerJwksTrust> getJwksTrust(String url) { return jwksTrust.get(); }
      @Override public void register(TrustedOidcIssuer issuer) { }
      @Override public void deregister(String url) { }
      @Override public List<TrustedOidcIssuer> list() { return Collections.emptyList(); }
      @Override public void init(GatewayConfig config, Map<String, String> opts) { }
      @Override public void start() { }
      @Override public void stop() { }
    };
  }

  /**
   * Returns a mock CloseableHttpResponse that yields the given status code and body.
   * Uses a real StringEntity so EntityUtils.toString() works without deep mocking.
   */
  private static CloseableHttpResponse mockResponse(int statusCode, String body) throws Exception {
    final StatusLine statusLine = EasyMock.createNiceMock(StatusLine.class);
    EasyMock.expect(statusLine.getStatusCode()).andReturn(statusCode).anyTimes();
    EasyMock.replay(statusLine);

    final CloseableHttpResponse response = EasyMock.createNiceMock(CloseableHttpResponse.class);
    EasyMock.expect(response.getStatusLine()).andReturn(statusLine).anyTimes();
    if (body != null) {
      EasyMock.expect(response.getEntity()).andReturn(new StringEntity(body, "UTF-8")).anyTimes();
    }
    EasyMock.replay(response);
    return response;
  }

  /** Returns an OIDCDiscoveryHelper backed by the given mock HttpClient. */
  private static OIDCDiscoveryHelper helper(TrustedOidcIssuerService trustedIssuers,
      CloseableHttpClient client) {
    return new OIDCDiscoveryHelper(trustedIssuers, CACHE_TTL, client);
  }

  // ------------------------------------------------------------------
  // SSRF gate
  // ------------------------------------------------------------------

  /**
   * SSRF prevention: discoverJwksUri must return empty immediately for an issuer that is
   * not registered for dynamic JWKS and must never call HttpClient.execute.
   */
  @Test
  public void testNoHttpCallForUntrustedIssuer() throws Exception {
    // Strict mock: any unexpected call to execute() fails the test immediately.
    final CloseableHttpClient client = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.replay(client);

    final Optional<String> result = helper(untrusted(), client).discoverJwksUri(ISSUER);

    assertFalse("Untrusted issuer must return empty", result.isPresent());
    EasyMock.verify(client); // verifies execute() was never called
  }

  @Test
  public void testStaticJwksIssuerMakesNoHttpCall() throws Exception {
    final CloseableHttpClient client = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.replay(client);

    final Optional<String> result = helper(trustedStatic(), client).discoverJwksUri(ISSUER);

    assertFalse("Static-JWKS issuer must return empty", result.isPresent());
    EasyMock.verify(client);
  }

  // ------------------------------------------------------------------
  // Happy path
  // ------------------------------------------------------------------

  @Test
  public void testDiscoveryReturnsJwksUri() throws Exception {
    final CloseableHttpClient client = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.expect(client.execute(EasyMock.isA(HttpUriRequest.class)))
        .andReturn(mockResponse(200, VALID_DISCOVERY_JSON));
    EasyMock.replay(client);

    final Optional<String> result = helper(trustedDynamic(), client).discoverJwksUri(ISSUER);

    assertTrue(result.isPresent());
    assertEquals(JWKS_URI, result.get());
    EasyMock.verify(client);
  }

  // ------------------------------------------------------------------
  // URL normalization
  // ------------------------------------------------------------------

  @Test
  public void testDiscoveryUrlTrailingSlashStripped() throws Exception {
    final Capture<HttpUriRequest> captured = EasyMock.newCapture();
    final CloseableHttpClient client = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.expect(client.execute(EasyMock.capture(captured)))
        .andReturn(mockResponse(200, VALID_DISCOVERY_JSON));
    EasyMock.replay(client);

    helper(trustedDynamic(), client).discoverJwksUri(ISSUER_WITH_SLASH);

    assertEquals("https://issuer.example.com/.well-known/openid-configuration",
        captured.getValue().getURI().toString());
  }

  @Test
  public void testDiscoveryUrlNoDoubleSlashWithoutTrailingSlash() throws Exception {
    final Capture<HttpUriRequest> captured = EasyMock.newCapture();
    final CloseableHttpClient client = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.expect(client.execute(EasyMock.capture(captured)))
        .andReturn(mockResponse(200, VALID_DISCOVERY_JSON));
    EasyMock.replay(client);

    helper(trustedDynamic(), client).discoverJwksUri(ISSUER);

    assertEquals("https://issuer.example.com/.well-known/openid-configuration",
        captured.getValue().getURI().toString());
  }

  // ------------------------------------------------------------------
  // Cache behaviour
  // ------------------------------------------------------------------

  @Test
  public void testDiscoveryDocumentCacheHit() throws Exception {
    // Strict mock expects exactly one execute() call; a second call would throw.
    final CloseableHttpClient client = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.expect(client.execute(EasyMock.isA(HttpUriRequest.class)))
        .andReturn(mockResponse(200, VALID_DISCOVERY_JSON))
        .once();
    EasyMock.replay(client);

    final OIDCDiscoveryHelper h = helper(trustedDynamic(), client);
    h.discoverJwksUri(ISSUER); // fetch
    h.discoverJwksUri(ISSUER); // cache hit — must NOT call execute again

    EasyMock.verify(client);
  }

  @Test
  public void testInvalidateEvictsFromCache() throws Exception {
    final CloseableHttpClient client = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.expect(client.execute(EasyMock.isA(HttpUriRequest.class)))
        .andReturn(mockResponse(200, VALID_DISCOVERY_JSON))
        .times(2); // must be called twice after eviction
    EasyMock.replay(client);

    final OIDCDiscoveryHelper h = helper(trustedDynamic(), client);
    h.discoverJwksUri(ISSUER); // fetch #1
    h.invalidate(ISSUER);      // evict
    h.discoverJwksUri(ISSUER); // fetch #2

    EasyMock.verify(client);
  }

  /**
   * When fetchJwksUri returns null (any failure), Caffeine must NOT cache the null.
   * The next call must trigger a fresh HTTP request.
   */
  @Test
  public void testNullNotCachedAfterFailure() throws Exception {
    final CloseableHttpClient client = EasyMock.createMock(CloseableHttpClient.class);
    // First call: connection error → fetchJwksUri returns null
    EasyMock.expect(client.execute(EasyMock.isA(HttpUriRequest.class)))
        .andThrow(new IOException("connection refused"));
    // Second call: succeeds
    EasyMock.expect(client.execute(EasyMock.isA(HttpUriRequest.class)))
        .andReturn(mockResponse(200, VALID_DISCOVERY_JSON));
    EasyMock.replay(client);

    final OIDCDiscoveryHelper h = helper(trustedDynamic(), client);
    assertFalse(h.discoverJwksUri(ISSUER).isPresent()); // failure → empty
    assertTrue(h.discoverJwksUri(ISSUER).isPresent());  // retry → success

    EasyMock.verify(client);
  }

  // ------------------------------------------------------------------
  // HTTP error paths
  // ------------------------------------------------------------------

  @Test
  public void testHttpGetNon200ReturnsEmpty() throws Exception {
    final CloseableHttpClient client = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.expect(client.execute(EasyMock.isA(HttpUriRequest.class)))
        .andReturn(mockResponse(404, null));
    EasyMock.replay(client);

    assertFalse(helper(trustedDynamic(), client).discoverJwksUri(ISSUER).isPresent());
    EasyMock.verify(client);
  }

  @Test
  public void testHttpGetConnectionExceptionReturnsEmpty() throws Exception {
    final CloseableHttpClient client = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.expect(client.execute(EasyMock.isA(HttpUriRequest.class)))
        .andThrow(new IOException("connection refused"));
    EasyMock.replay(client);

    assertFalse(helper(trustedDynamic(), client).discoverJwksUri(ISSUER).isPresent());
    EasyMock.verify(client);
  }

  // ------------------------------------------------------------------
  // Discovery document parse errors
  // ------------------------------------------------------------------

  @Test
  public void testMalformedDiscoveryDocumentReturnsEmpty() throws Exception {
    final CloseableHttpClient client = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.expect(client.execute(EasyMock.isA(HttpUriRequest.class)))
        .andReturn(mockResponse(200, "not valid json at all"));
    EasyMock.replay(client);

    assertFalse(helper(trustedDynamic(), client).discoverJwksUri(ISSUER).isPresent());
    EasyMock.verify(client);
  }

  /**
   * Nimbus 11.x treats jwks_uri as required and throws ParseException when it is absent.
   * Verifies the catch block in fetchJwksUri handles this and returns Optional.empty().
   */
  @Test
  public void testMissingJwksUriReturnsEmpty() throws Exception {
    final CloseableHttpClient client = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.expect(client.execute(EasyMock.isA(HttpUriRequest.class)))
        .andReturn(mockResponse(200, DISCOVERY_JSON_NO_JWKS_URI));
    EasyMock.replay(client);

    assertFalse(helper(trustedDynamic(), client).discoverJwksUri(ISSUER).isPresent());
    EasyMock.verify(client);
  }

  // ------------------------------------------------------------------
  // Issuer CA bundle (jwksCa)
  // ------------------------------------------------------------------

  /** An issuer trust whose TLS-failure reload reports the given result. */
  private static final class StubJwksTrust implements IssuerJwksTrust {
    private final SSLContext sslContext;
    private final boolean reloadResult;
    private final AtomicInteger reloads = new AtomicInteger();

    StubJwksTrust(boolean reloadResult) throws Exception {
      this.sslContext = SSLContext.getInstance("TLS");
      this.reloadResult = reloadResult;
    }

    @Override public SSLContext getSslContext() { return sslContext; }
    @Override public SSLSocketFactory getSslSocketFactory() { throw new UnsupportedOperationException(); }
    @Override public boolean reloadAfterTlsFailure() {
      reloads.incrementAndGet();
      return reloadResult;
    }
  }

  /** Returns a factory handing out the given pinned clients in turn, recording the SSL contexts. */
  private static Function<SSLContext, CloseableHttpClient> pinnedFactory(List<SSLContext> seen,
      CloseableHttpClient... clients) {
    final AtomicInteger next = new AtomicInteger();
    return sslContext -> {
      seen.add(sslContext);
      return clients[next.getAndIncrement()];
    };
  }

  private static CloseableHttpClient pinnedClient(CloseableHttpResponse response, Exception failure) throws Exception {
    final CloseableHttpClient client = EasyMock.createMock(CloseableHttpClient.class);
    if (failure != null) {
      EasyMock.expect(client.execute(EasyMock.isA(HttpUriRequest.class))).andThrow(failure);
    } else {
      EasyMock.expect(client.execute(EasyMock.isA(HttpUriRequest.class))).andReturn(response);
    }
    client.close();
    EasyMock.expectLastCall();
    EasyMock.replay(client);
    return client;
  }

  @Test
  public void testIssuerWithCaUsesPinnedClient() throws Exception {
    final CloseableHttpClient shared = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.replay(shared);
    final StubJwksTrust trust = new StubJwksTrust(false);
    final CloseableHttpClient pinned = pinnedClient(mockResponse(200, VALID_DISCOVERY_JSON), null);
    final List<SSLContext> seen = new ArrayList<>();

    final Optional<String> result = new OIDCDiscoveryHelper(stubService(true, true, () -> Optional.of(trust)),
        CACHE_TTL, shared, pinnedFactory(seen, pinned)).discoverJwksUri(ISSUER);

    assertEquals(Optional.of(JWKS_URI), result);
    assertEquals(1, seen.size());
    assertSame("pinned client must be built from the issuer's SSL context", trust.getSslContext(), seen.get(0));
    EasyMock.verify(shared, pinned); // shared client unused; pinned client closed
  }

  @Test
  public void testIssuerWithCaRetriesOnceAfterTlsFailureWithReloadedCa() throws Exception {
    final CloseableHttpClient shared = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.replay(shared);
    final StubJwksTrust trust = new StubJwksTrust(true);
    final CloseableHttpClient first = pinnedClient(null, new SSLHandshakeException("PKIX path building failed"));
    final CloseableHttpClient second = pinnedClient(mockResponse(200, VALID_DISCOVERY_JSON), null);
    final List<SSLContext> seen = new ArrayList<>();

    final Optional<String> result = new OIDCDiscoveryHelper(stubService(true, true, () -> Optional.of(trust)),
        CACHE_TTL, shared, pinnedFactory(seen, first, second)).discoverJwksUri(ISSUER);

    assertEquals(Optional.of(JWKS_URI), result);
    assertEquals(1, trust.reloads.get());
    assertEquals(2, seen.size());
    EasyMock.verify(shared, first, second);
  }

  @Test
  public void testIssuerWithCaNoRetryWhenCaUnchanged() throws Exception {
    final CloseableHttpClient shared = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.replay(shared);
    final StubJwksTrust trust = new StubJwksTrust(false);
    final CloseableHttpClient pinned = pinnedClient(null, new SSLHandshakeException("PKIX path building failed"));
    final List<SSLContext> seen = new ArrayList<>();

    final Optional<String> result = new OIDCDiscoveryHelper(stubService(true, true, () -> Optional.of(trust)),
        CACHE_TTL, shared, pinnedFactory(seen, pinned)).discoverJwksUri(ISSUER);

    assertFalse(result.isPresent());
    assertEquals(1, trust.reloads.get());
    assertEquals("no retry when the CA did not change", 1, seen.size());
    EasyMock.verify(shared, pinned);
  }

  @Test
  public void testIssuerWithCaNonTlsFailureDoesNotReloadCa() throws Exception {
    final CloseableHttpClient shared = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.replay(shared);
    final StubJwksTrust trust = new StubJwksTrust(true);
    final CloseableHttpClient timedOut = pinnedClient(null, new SocketTimeoutException("Read timed out"));
    final CloseableHttpClient notFound = pinnedClient(mockResponse(404, null), null);
    final List<SSLContext> seen = new ArrayList<>();

    final OIDCDiscoveryHelper h = new OIDCDiscoveryHelper(stubService(true, true, () -> Optional.of(trust)),
        CACHE_TTL, shared, pinnedFactory(seen, timedOut, notFound));
    assertFalse(h.discoverJwksUri(ISSUER).isPresent());
    assertFalse(h.discoverJwksUri(ISSUER).isPresent());

    assertEquals("timeouts and HTTP errors must not re-read the CA", 0, trust.reloads.get());
    EasyMock.verify(shared, timedOut, notFound);
  }

  @Test
  public void testIssuerWithUnavailableCaFailsClosed() throws Exception {
    // Neither the shared client nor a pinned client may be used: no fallback to the default truststore.
    final CloseableHttpClient shared = EasyMock.createMock(CloseableHttpClient.class);
    EasyMock.replay(shared);
    final List<SSLContext> seen = new ArrayList<>();

    final Optional<String> result = new OIDCDiscoveryHelper(stubService(true, true, () -> {
      throw new IllegalStateException("Secret knox/missing not found");
    }), CACHE_TTL, shared, pinnedFactory(seen)).discoverJwksUri(ISSUER);

    assertFalse(result.isPresent());
    assertTrue(seen.isEmpty());
    EasyMock.verify(shared);
  }
}

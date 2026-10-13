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

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.SSLSocketFactory;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IssuerTrustProviderTest {

  private static final String ISSUER = "https://cluster-1.example.com";
  private static final String SECRET_REF = "secret:cluster-1/apiserver-ca#ca.crt";
  private static final Duration MAX_AGE = Duration.ofHours(1);

  private final Map<String, TrustedOidcIssuer> registry = new HashMap<>();
  private final StubSecretReader secretReader = new StubSecretReader();
  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-13T10:00:00Z"));
  private IssuerTrustProvider provider;
  private String caA;
  private String caB;

  @Before
  public void setUp() throws Exception {
    caA = TestCaBundles.toPem(TestCaBundles.selfSigned("CN=ca-a"));
    caB = TestCaBundles.toPem(TestCaBundles.selfSigned("CN=ca-b"));
    provider = new IssuerTrustProvider(registry::get, MAX_AGE, secretReader, clock);
  }

  @Test
  public void testIssuerWithoutJwksCaHasNoTrust() {
    registry.put(ISSUER, issuer(null));
    assertFalse(provider.get(ISSUER).isPresent());
    assertFalse("unregistered issuer", provider.get("https://unknown.example.com").isPresent());
    assertFalse(provider.get(null).isPresent());
    assertTrue(secretReader.reads.isEmpty());
  }

  @Test
  public void testInlinePem() {
    registry.put(ISSUER, issuer(caA));

    final IssuerJwksTrust trust = provider.get(ISSUER).orElseThrow();
    assertNotNull(trust.getSslContext());
    final SSLSocketFactory factory = trust.getSslSocketFactory();
    assertNotNull(factory);

    clock.advance(MAX_AGE.multipliedBy(2));
    assertSame("inline PEM is never re-read", trust, provider.get(ISSUER).orElseThrow());
    assertSame(factory, trust.getSslSocketFactory());
    assertFalse("inline PEM cannot be reloaded", trust.reloadAfterTlsFailure());
    assertTrue(secretReader.reads.isEmpty());
  }

  @Test
  public void testInlinePemTwoCertificates() throws Exception {
    registry.put(ISSUER, issuer(caA + caB));
    assertNotNull(provider.get(ISSUER).orElseThrow().getSslSocketFactory());
  }

  @Test
  public void testSecretReadOnceOnFirstUse() {
    registry.put(ISSUER, issuer(SECRET_REF));
    secretReader.pem = caA;

    final IssuerJwksTrust trust = provider.get(ISSUER).orElseThrow();
    final SSLSocketFactory factory = trust.getSslSocketFactory();
    clock.advance(MAX_AGE.minusSeconds(1));
    assertSame(trust, provider.get(ISSUER).orElseThrow());
    assertSame(factory, trust.getSslSocketFactory());

    assertEquals(1, secretReader.reads.size());
    final JwksCa.SecretReference read = secretReader.reads.get(0);
    assertEquals("cluster-1", read.namespace());
    assertEquals("apiserver-ca", read.name());
    assertEquals("ca.crt", read.key());
  }

  @Test
  public void testSecretReferenceDefaultNamespace() {
    registry.put(ISSUER, issuer("secret:apiserver-ca#ca.crt"));
    secretReader.pem = caA;

    provider.get(ISSUER).orElseThrow();
    assertEquals(JwksCa.DEFAULT_SECRET_NAMESPACE, secretReader.reads.get(0).namespace());
  }

  @Test
  public void testSecretReReadAfterMaxAgeUnchanged() {
    registry.put(ISSUER, issuer(SECRET_REF));
    secretReader.pem = caA;
    final IssuerJwksTrust trust = provider.get(ISSUER).orElseThrow();
    final SSLSocketFactory factory = trust.getSslSocketFactory();

    clock.advance(MAX_AGE);
    assertSame("unchanged bundle keeps the socket factory", factory, trust.getSslSocketFactory());
    assertEquals(2, secretReader.reads.size());

    // the re-read restarted the max age
    clock.advance(MAX_AGE.minusSeconds(1));
    trust.getSslSocketFactory();
    assertEquals(2, secretReader.reads.size());
  }

  @Test
  public void testSecretReReadAfterMaxAgeChanged() {
    registry.put(ISSUER, issuer(SECRET_REF));
    secretReader.pem = caA;
    final IssuerJwksTrust trust = provider.get(ISSUER).orElseThrow();
    final SSLSocketFactory factory = trust.getSslSocketFactory();

    secretReader.pem = caB;
    clock.advance(MAX_AGE);
    assertNotSame("a changed bundle gives a new socket factory", factory, trust.getSslSocketFactory());
    assertEquals(2, secretReader.reads.size());
  }

  @Test
  public void testFailedMaxAgeReReadKeepsPreviousBundle() {
    registry.put(ISSUER, issuer(SECRET_REF));
    secretReader.pem = caA;
    final IssuerJwksTrust trust = provider.get(ISSUER).orElseThrow();
    final SSLSocketFactory factory = trust.getSslSocketFactory();

    secretReader.failure = new IllegalStateException("apiserver unreachable");
    clock.advance(MAX_AGE);
    assertSame(factory, trust.getSslSocketFactory());
    assertEquals(2, secretReader.reads.size());

    // the failed read is not retried within the reload interval
    trust.getSslSocketFactory();
    assertEquals(2, secretReader.reads.size());

    secretReader.failure = null;
    secretReader.pem = caB;
    clock.advance(IssuerTrustProvider.MIN_RELOAD_INTERVAL);
    assertNotSame(factory, trust.getSslSocketFactory());
    assertEquals(3, secretReader.reads.size());
  }

  @Test
  public void testMissingSecretFailsClosedAndIsRateLimited() {
    registry.put(ISSUER, issuer(SECRET_REF));
    secretReader.failure = new IllegalStateException("Secret cluster-1/apiserver-ca not found");

    IllegalStateException e = assertThrows(IllegalStateException.class, () -> provider.get(ISSUER));
    assertTrue(e.getMessage(), e.getMessage().contains("not found"));
    assertTrue(e.getMessage(), e.getMessage().contains(ISSUER));
    e = assertThrows(IllegalStateException.class, () -> provider.get(ISSUER));
    assertTrue("the last failure is reported", e.getMessage().contains("not found"));
    assertEquals("no re-read within the reload interval", 1, secretReader.reads.size());

    // once the Secret is created, it is picked up after the reload interval
    secretReader.failure = null;
    secretReader.pem = caA;
    clock.advance(IssuerTrustProvider.MIN_RELOAD_INTERVAL);
    assertNotNull(provider.get(ISSUER).orElseThrow().getSslSocketFactory());
    assertEquals(2, secretReader.reads.size());
  }

  @Test
  public void testSecretWithoutCertificateFailsClosed() {
    registry.put(ISSUER, issuer(SECRET_REF));
    secretReader.pem = "not a certificate";
    assertThrows(IllegalStateException.class, () -> provider.get(ISSUER));
  }

  @Test
  public void testMalformedStoredSecretReferenceFailsClosed() {
    registry.put(ISSUER, issuer("secret:Not_Valid"));
    final IllegalStateException e = assertThrows(IllegalStateException.class, () -> provider.get(ISSUER));
    assertTrue(e.getMessage(), e.getMessage().contains("malformed"));
    assertTrue(secretReader.reads.isEmpty());
  }

  @Test
  public void testReloadAfterTlsFailureWithRotatedCa() {
    registry.put(ISSUER, issuer(SECRET_REF));
    secretReader.pem = caA;
    final IssuerJwksTrust trust = provider.get(ISSUER).orElseThrow();
    final SSLSocketFactory factory = trust.getSslSocketFactory();

    // the first read happened at the current instant: a reload must wait for the interval
    assertFalse(trust.reloadAfterTlsFailure());
    assertEquals(1, secretReader.reads.size());

    secretReader.pem = caB;
    clock.advance(IssuerTrustProvider.MIN_RELOAD_INTERVAL);
    assertTrue(trust.reloadAfterTlsFailure());
    final SSLSocketFactory rotated = trust.getSslSocketFactory();
    assertNotSame(factory, rotated);
    assertEquals(2, secretReader.reads.size());

    // rate limited
    assertFalse(trust.reloadAfterTlsFailure());
    assertEquals(2, secretReader.reads.size());

    // unchanged bundle: re-read, but nothing to retry with
    clock.advance(IssuerTrustProvider.MIN_RELOAD_INTERVAL);
    assertFalse(trust.reloadAfterTlsFailure());
    assertSame(rotated, trust.getSslSocketFactory());
    assertEquals(3, secretReader.reads.size());

    // a failed re-read keeps the bundle
    secretReader.failure = new IllegalStateException("apiserver unreachable");
    clock.advance(IssuerTrustProvider.MIN_RELOAD_INTERVAL);
    assertFalse(trust.reloadAfterTlsFailure());
    assertSame(rotated, trust.getSslSocketFactory());
  }

  @Test
  public void testInvalidateForcesImmediateReRead() {
    registry.put(ISSUER, issuer(SECRET_REF));
    secretReader.pem = caA;
    final IssuerJwksTrust trust = provider.get(ISSUER).orElseThrow();
    final SSLSocketFactory factory = trust.getSslSocketFactory();

    secretReader.pem = caB;
    provider.invalidate(ISSUER);
    final IssuerJwksTrust fresh = provider.get(ISSUER).orElseThrow();
    assertNotSame(trust, fresh);
    assertNotSame(factory, fresh.getSslSocketFactory());
    assertEquals("re-read without waiting for the reload interval", 2, secretReader.reads.size());

    provider.invalidate(null); // ignored
  }

  @Test
  public void testChangedRegistrationGetsNewTrust() {
    registry.put(ISSUER, issuer(caA));
    final IssuerJwksTrust trust = provider.get(ISSUER).orElseThrow();

    registry.put(ISSUER, issuer(caB));
    final IssuerJwksTrust changed = provider.get(ISSUER).orElseThrow();
    assertNotSame(trust, changed);
    assertNotSame(trust.getSslSocketFactory(), changed.getSslSocketFactory());

    registry.put(ISSUER, issuer(null));
    assertFalse(provider.get(ISSUER).isPresent());
  }

  @Test
  public void testCloseClosesSecretReader() throws Exception {
    provider.close();
    assertTrue(secretReader.closed.get());
  }

  @Test
  public void testKubernetesSecretReaderData() throws Exception {
    final KubernetesClient client = EasyMock.createMock(KubernetesClient.class);
    final Object[] chain = expectSecretLookup(client, "cluster-1", "apiserver-ca", new SecretBuilder()
        .addToData("ca.crt", Base64.getEncoder().encodeToString(caA.getBytes(StandardCharsets.UTF_8))).build());
    client.close();
    EasyMock.expectLastCall().once();
    EasyMock.replay(chain);
    EasyMock.replay(client);

    try (IssuerTrustProvider.KubernetesSecretReader reader = new IssuerTrustProvider.KubernetesSecretReader(() -> client)) {
      assertEquals(caA, reader.read(JwksCa.parseSecretReference(SECRET_REF)));
    }
    EasyMock.verify(client);
    EasyMock.verify(chain);
  }

  @Test
  public void testKubernetesSecretReaderStringData() throws Exception {
    final KubernetesClient client = EasyMock.createNiceMock(KubernetesClient.class);
    final Object[] chain = expectSecretLookup(client, "knox", "apiserver-ca",
        new SecretBuilder().addToStringData("ca.crt", caA).build());
    EasyMock.replay(chain);
    EasyMock.replay(client);

    try (IssuerTrustProvider.KubernetesSecretReader reader = new IssuerTrustProvider.KubernetesSecretReader(() -> client)) {
      assertEquals(caA, reader.read(JwksCa.parseSecretReference("secret:apiserver-ca#ca.crt")));
    }
  }

  @Test
  public void testKubernetesSecretReaderMissingSecretOrKey() throws Exception {
    final KubernetesClient client = EasyMock.createNiceMock(KubernetesClient.class);
    final Object[] missingSecret = expectSecretLookup(client, "cluster-1", "apiserver-ca", null);
    EasyMock.replay(missingSecret);
    EasyMock.replay(client);
    try (IssuerTrustProvider.KubernetesSecretReader reader = new IssuerTrustProvider.KubernetesSecretReader(() -> client)) {
      final IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> reader.read(JwksCa.parseSecretReference(SECRET_REF)));
      assertTrue(e.getMessage(), e.getMessage().contains("cluster-1/apiserver-ca not found"));
    }

    final KubernetesClient client2 = EasyMock.createNiceMock(KubernetesClient.class);
    final Object[] missingKey = expectSecretLookup(client2, "cluster-1", "apiserver-ca",
        new SecretBuilder().addToData("other", "eA==").build());
    EasyMock.replay(missingKey);
    EasyMock.replay(client2);
    try (IssuerTrustProvider.KubernetesSecretReader reader = new IssuerTrustProvider.KubernetesSecretReader(() -> client2)) {
      final IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> reader.read(JwksCa.parseSecretReference(SECRET_REF)));
      assertTrue(e.getMessage(), e.getMessage().contains("has no key ca.crt"));
    }
  }

  @SuppressWarnings("unchecked")
  private static Object[] expectSecretLookup(KubernetesClient client, String namespace, String name, Secret secret) {
    final MixedOperation<Secret, SecretList, Resource<Secret>> mixed = EasyMock.createMock(MixedOperation.class);
    final NonNamespaceOperation<Secret, SecretList, Resource<Secret>> namespaced =
        EasyMock.createMock(NonNamespaceOperation.class);
    final Resource<Secret> resource = EasyMock.createMock(Resource.class);
    EasyMock.expect(client.secrets()).andReturn(mixed);
    EasyMock.expect(mixed.inNamespace(namespace)).andReturn(namespaced);
    EasyMock.expect(namespaced.withName(name)).andReturn(resource);
    EasyMock.expect(resource.get()).andReturn(secret);
    return new Object[] {mixed, namespaced, resource};
  }

  private static TrustedOidcIssuer issuer(String jwksCa) {
    return new TrustedOidcIssuer(ISSUER, true, "cluster-1", Instant.EPOCH, "admin", jwksCa);
  }

  private static final class StubSecretReader implements IssuerTrustProvider.SecretReader, AutoCloseable {
    private final List<JwksCa.SecretReference> reads = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private String pem;
    private RuntimeException failure;

    @Override
    public String read(JwksCa.SecretReference reference) {
      reads.add(reference);
      if (failure != null) {
        throw failure;
      }
      return pem;
    }

    @Override
    public void close() {
      closed.set(true);
    }
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    private void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}

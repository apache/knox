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

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.services.token.impl.DefaultTokenAuthorityService;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end check of per-issuer CA bundles ({@code jwksCa}): real HTTPS JWKS endpoints with their
 * own self-signed CAs, the issuer trust provider, and JWKS-based token verification.
 */
public class IssuerJwksCaAcceptanceTest {

  private static final String ISSUER_A = "https://cluster-1.example.com";
  private static final String ISSUER_B = "https://cluster-2.example.com";
  private static final String SECRET_REF_A = "secret:cluster-1/apiserver-ca#ca.crt";
  private static final JOSEObjectTypeVerifier<SecurityContext> TYPE_VERIFIER =
      new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT, null);

  private final Map<String, TrustedOidcIssuer> registry = new HashMap<>();
  private final Map<String, String> secrets = new HashMap<>();
  private final List<String> secretReads = new ArrayList<>();
  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-13T10:00:00Z"));
  private IssuerTrustProvider provider;
  private DefaultTokenAuthorityService authority;
  private TestJwksHttpsServer serverA;
  private TestJwksHttpsServer serverB;

  @Before
  public void setUp() throws Exception {
    serverA = TestJwksHttpsServer.start();
    serverB = TestJwksHttpsServer.start();
    provider = new IssuerTrustProvider(registry::get, Duration.ofHours(1), reference -> {
      secretReads.add(reference.toString());
      final String pem = secrets.get(reference.toString());
      if (pem == null) {
        throw new IllegalStateException("Secret " + reference + " not found");
      }
      return pem;
    }, clock);

    final GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getJwksCacheTimeToLive()).andReturn(TimeUnit.MINUTES.toMillis(20)).anyTimes();
    EasyMock.expect(config.getJwksCacheRefreshTimeout()).andReturn(TimeUnit.SECONDS.toMillis(15)).anyTimes();
    EasyMock.expect(config.getJwksOutageCacheTTL()).andReturn(TimeUnit.HOURS.toMillis(2)).anyTimes();
    EasyMock.expect(config.getIssuersWithIgnoredTypeHeader()).andReturn(Collections.emptySet()).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    final KeystoreService keystoreService = EasyMock.createNiceMock(KeystoreService.class);
    EasyMock.replay(config, aliasService, keystoreService);
    authority = new DefaultTokenAuthorityService();
    authority.setAliasService(aliasService);
    authority.setKeystoreService(keystoreService);
    authority.init(config, Collections.emptyMap());
  }

  @After
  public void tearDown() throws Exception {
    authority.stop();
    provider.close();
    serverA.close();
    serverB.close();
  }

  /* Acceptance (a): issuers with different self-signed CAs all validate, inline or Secret-referenced */
  @Test
  public void testIssuersWithDifferentSelfSignedCasValidate() throws Exception {
    register(ISSUER_A, serverA.getCaPem());
    secrets.put("cluster-2/apiserver-ca#ca.crt", serverB.getCaPem());
    register(ISSUER_B, "secret:cluster-2/apiserver-ca#ca.crt");

    assertTrue(verify(serverA, ISSUER_A, trust(ISSUER_A)));
    assertTrue(verify(serverB, ISSUER_B, trust(ISSUER_B)));
  }

  /* Acceptance (b): a CA replaced in the referenced Secret applies on the next fetch, no restart */
  @Test
  public void testRotatedSecretCaAppliesWithoutRestart() throws Exception {
    secrets.put("cluster-1/apiserver-ca#ca.crt", serverA.getCaPem());
    register(ISSUER_A, SECRET_REF_A);
    final IssuerJwksTrust trust = trust(ISSUER_A);
    assertTrue(verify(serverA, ISSUER_A, trust));

    // the apiserver gets a certificate from a new CA; the Secret is updated in place
    serverA.rotateCertificate();
    serverA.rotateSigningKey("kid-2");
    secrets.put("cluster-1/apiserver-ca#ca.crt", serverA.getCaPem());
    clock.advance(IssuerTrustProvider.MIN_RELOAD_INTERVAL);

    // the unknown kid makes the JWKS be fetched again; that fails TLS, re-reads the Secret and retries
    assertTrue(verify(serverA, ISSUER_A, trust));
    assertSame("the registration is unchanged", trust, trust(ISSUER_A));
    assertEquals(List.of("cluster-1/apiserver-ca#ca.crt", "cluster-1/apiserver-ca#ca.crt"), secretReads);
  }

  /* Acceptance (b): an inline CA replaced by re-registration (delete + create) applies at once */
  @Test
  public void testReRegisteredInlineCaAppliesWithoutRestart() throws Exception {
    register(ISSUER_A, serverA.getCaPem());
    final IssuerJwksTrust previous = trust(ISSUER_A);
    assertTrue(verify(serverA, ISSUER_A, previous));

    serverA.rotateCertificate();
    serverA.rotateSigningKey("kid-2");
    register(ISSUER_A, serverA.getCaPem());
    provider.invalidate(ISSUER_A);

    final IssuerJwksTrust rotated = trust(ISSUER_A);
    assertNotSame(previous, rotated);
    assertTrue(verify(serverA, ISSUER_A, rotated));
    assertFalse("the replaced CA is no longer accepted", verify(serverA, ISSUER_A, previous));
  }

  /* Acceptance (c): an issuer CA is trusted for that issuer only, never by other TLS clients */
  @Test
  public void testIssuerCaIsNotTrustedByOtherClients() throws Exception {
    final SSLSocketFactory defaultSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
    register(ISSUER_A, serverA.getCaPem());
    final IssuerJwksTrust trustA = trust(ISSUER_A);
    assertTrue(verify(serverA, ISSUER_A, trustA));

    assertFalse("issuer A's CA does not admit another issuer's endpoint", verify(serverB, ISSUER_A, trustA));
    assertFalse("the default truststore path does not trust issuer A's CA", verify(serverA, ISSUER_A, null));

    final HttpsURLConnection connection =
        (HttpsURLConnection) URI.create(serverA.getJwksUrl()).toURL().openConnection();
    try {
      assertThrows(SSLException.class, connection::getResponseCode);
    } finally {
      connection.disconnect();
    }
    assertSame(defaultSocketFactory, HttpsURLConnection.getDefaultSSLSocketFactory());
  }

  /* Acceptance (d): an apiserver signing key rotation needs no configuration change */
  @Test
  public void testSigningKeyRotationNeedsNoConfigChange() throws Exception {
    secrets.put("cluster-1/apiserver-ca#ca.crt", serverA.getCaPem());
    register(ISSUER_A, SECRET_REF_A);
    final IssuerJwksTrust trust = trust(ISSUER_A);
    assertTrue(verify(serverA, ISSUER_A, trust));

    serverA.rotateSigningKey("kid-2");
    assertTrue(verify(serverA, ISSUER_A, trust));
    assertEquals("the JWKS is fetched again for the new kid", 2, serverA.getRequestCount());
    assertEquals("the CA is not re-read", 1, secretReads.size());
  }

  private void register(String issuerUrl, String jwksCa) {
    registry.put(issuerUrl, new TrustedOidcIssuer(issuerUrl, true, "cluster", Instant.EPOCH, "admin", jwksCa));
  }

  private IssuerJwksTrust trust(String issuerUrl) {
    return provider.get(issuerUrl).orElseThrow();
  }

  private boolean verify(TestJwksHttpsServer server, String issuer, IssuerJwksTrust trust) throws Exception {
    return authority.verifyToken(new JWTToken(server.signToken(issuer)), Set.of(URI.create(server.getJwksUrl())),
        "RS256", TYPE_VERIFIER, trust);
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

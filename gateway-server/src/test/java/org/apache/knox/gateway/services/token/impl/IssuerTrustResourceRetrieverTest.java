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

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.Resource;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.IssuerJwksTrust;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.TestCaBundles;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.TestJwksHttpsServer;
import org.apache.knox.gateway.util.TruststoreSSLContextUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IssuerTrustResourceRetrieverTest {

  private TestJwksHttpsServer server;
  private URL jwksUrl;

  @Before
  public void setUp() throws Exception {
    server = TestJwksHttpsServer.start();
    jwksUrl = URI.create(server.getJwksUrl()).toURL();
  }

  @After
  public void tearDown() throws Exception {
    server.close();
  }

  @Test
  public void testRetrievesOverIssuerCa() throws Exception {
    final StubTrust trust = new StubTrust(server.getCertificate());
    final Resource resource = retriever(trust).retrieveResource(jwksUrl);
    assertEquals(1, JWKSet.parse(resource.getContent()).getKeys().size());
    assertEquals(0, trust.reloads);
  }

  @Test
  public void testTlsTrustFailureReloadsCaAndRetriesOnce() throws Exception {
    final StubTrust trust = new StubTrust(TestCaBundles.selfSigned("CN=localhost"));
    trust.reloaded = server.getCertificate();

    final Resource resource = retriever(trust).retrieveResource(jwksUrl);
    assertEquals(1, JWKSet.parse(resource.getContent()).getKeys().size());
    assertEquals(1, trust.reloads);
    assertEquals(1, server.getRequestCount());
  }

  @Test
  public void testTlsTrustFailureWithUnchangedCaIsNotRetried() throws Exception {
    final StubTrust trust = new StubTrust(TestCaBundles.selfSigned("CN=localhost"));

    final IOException e = assertThrows(IOException.class, () -> retriever(trust).retrieveResource(jwksUrl));
    assertTrue(e.toString(), TruststoreSSLContextUtils.isTlsTrustFailure(e));
    assertEquals(1, trust.reloads);
    assertEquals("no retry without a changed CA", 1, trust.socketFactoryRequests);
  }

  @Test
  public void testRetriedTlsTrustFailureIsNotReloadedAgain() throws Exception {
    final StubTrust trust = new StubTrust(TestCaBundles.selfSigned("CN=localhost"));
    trust.reloaded = TestCaBundles.selfSigned("CN=localhost");

    assertThrows(SSLException.class, () -> retriever(trust).retrieveResource(jwksUrl));
    assertEquals(1, trust.reloads);
    assertEquals(2, trust.socketFactoryRequests);
  }

  @Test
  public void testHttpErrorDoesNotReloadCa() throws Exception {
    final StubTrust trust = new StubTrust(server.getCertificate());
    server.setStatus(404);

    final IOException e = assertThrows(IOException.class, () -> retriever(trust).retrieveResource(jwksUrl));
    assertFalse(e.toString(), TruststoreSSLContextUtils.isTlsTrustFailure(e));
    assertEquals(0, trust.reloads);
  }

  @Test
  public void testConnectionFailureDoesNotReloadCa() throws Exception {
    final StubTrust trust = new StubTrust(server.getCertificate());
    server.close();

    assertThrows(IOException.class, () -> retriever(trust).retrieveResource(jwksUrl));
    assertEquals(0, trust.reloads);
  }

  @Test
  public void testUnavailableCaFailsTheRetrieval() {
    final StubTrust trust = new StubTrust(null);

    final IOException e = assertThrows(IOException.class, () -> retriever(trust).retrieveResource(jwksUrl));
    assertTrue(e.getCause() instanceof IllegalStateException);
    assertEquals(0, trust.reloads);
    assertEquals(0, server.getRequestCount());
  }

  private static IssuerTrustResourceRetriever retriever(IssuerJwksTrust trust) {
    return new IssuerTrustResourceRetriever(trust, 5000, 5000, 50 * 1024);
  }

  /* Trusts one CA certificate; a reload switches to the configured replacement, if any */
  private static final class StubTrust implements IssuerJwksTrust {
    private SSLContext sslContext;
    private X509Certificate reloaded;
    private int reloads;
    private int socketFactoryRequests;

    private StubTrust(X509Certificate ca) {
      this.sslContext = ca == null ? null : sslContext(ca);
    }

    @Override
    public SSLContext getSslContext() {
      if (sslContext == null) {
        throw new IllegalStateException("CA bundle is unavailable");
      }
      return sslContext;
    }

    @Override
    public SSLSocketFactory getSslSocketFactory() {
      socketFactoryRequests++;
      return getSslContext().getSocketFactory();
    }

    @Override
    public boolean reloadAfterTlsFailure() {
      reloads++;
      if (reloaded == null) {
        return false;
      }
      sslContext = sslContext(reloaded);
      reloaded = null;
      return true;
    }

    private static SSLContext sslContext(X509Certificate ca) {
      try {
        final KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
        truststore.load(null, null);
        truststore.setCertificateEntry("ca", ca);
        return TruststoreSSLContextUtils.getTruststoreSSLContext(truststore);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }
}

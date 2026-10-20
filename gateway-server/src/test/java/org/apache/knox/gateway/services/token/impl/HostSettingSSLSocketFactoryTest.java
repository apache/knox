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
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.Resource;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.TestJwksHttpsServer;
import org.apache.knox.gateway.util.TruststoreSSLContextUtils;
import org.junit.Test;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.security.KeyStore;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HostSettingSSLSocketFactoryTest {

  private static final String IP_LITERAL = "10.83.6.18";

  /*
   * The case this class exists for: BouncyCastle's JSSE takes the endpoint identification
   * hostname from the socket, and HttpsURLConnection creates the socket without one. Setting
   * it lets BC match the IP against the certificate's iPAddress SAN instead of failing with
   * "No hostname specified for HTTPS endpoint ID check".
   */
  @Test
  public void testSetsHostOnSocketExposingSetHost() throws Exception {
    final RecordingSocket socket = new RecordingSocket();
    final SSLSocketFactory factory = new HostSettingSSLSocketFactory(new StubSSLSocketFactory(socket), IP_LITERAL);

    assertSame(socket, factory.createSocket());

    assertEquals(IP_LITERAL, socket.host);
  }

  @Test
  public void testSetsHostOnEveryCreateSocketOverload() throws Exception {
    final RecordingSocket unconnected = new RecordingSocket();
    final RecordingSocket byName = new RecordingSocket();
    final RecordingSocket byNameAndLocal = new RecordingSocket();
    final RecordingSocket byAddress = new RecordingSocket();
    final RecordingSocket byAddressAndLocal = new RecordingSocket();
    final RecordingSocket layered = new RecordingSocket();
    final StubSSLSocketFactory delegate = new StubSSLSocketFactory(
        unconnected, byName, byNameAndLocal, byAddress, byAddressAndLocal, layered);
    final SSLSocketFactory factory = new HostSettingSSLSocketFactory(delegate, IP_LITERAL);

    final InetAddress loopback = InetAddress.getLoopbackAddress();
    factory.createSocket();
    factory.createSocket(IP_LITERAL, 6443);
    factory.createSocket(IP_LITERAL, 6443, loopback, 0);
    factory.createSocket(loopback, 6443);
    factory.createSocket(loopback, 6443, loopback, 0);
    factory.createSocket(new Socket(), IP_LITERAL, 6443, true);

    assertEquals(IP_LITERAL, unconnected.host);
    assertEquals(IP_LITERAL, byName.host);
    assertEquals(IP_LITERAL, byNameAndLocal.host);
    assertEquals(IP_LITERAL, byAddress.host);
    assertEquals(IP_LITERAL, byAddressAndLocal.host);
    assertEquals(IP_LITERAL, layered.host);
  }

  /*
   * Outside a FIPS deployment the JSSE sockets are not BouncyCastle's and have no setHost, and
   * the stock JSSE already identifies IP endpoints correctly. The wrapper must stay out of the way.
   */
  @Test
  public void testLeavesSocketWithoutSetHostUnchanged() throws Exception {
    try (Socket plain = new Socket()) {
      final SSLSocketFactory factory = new HostSettingSSLSocketFactory(new StubSSLSocketFactory(plain), IP_LITERAL);

      assertSame(plain, factory.createSocket());
    }
  }

  @Test
  public void testSetHostReportsWhetherItWasApplied() throws Exception {
    final RecordingSocket supported = new RecordingSocket();
    assertTrue(HostSettingSSLSocketFactory.setHost(supported, IP_LITERAL));
    assertEquals(IP_LITERAL, supported.host);

    try (Socket plain = new Socket()) {
      assertFalse(HostSettingSSLSocketFactory.setHost(plain, IP_LITERAL));
    }
  }

  /*
   * HttpsURLConnection calls createSocket() with no host for every URL, not only IP-literal ones,
   * so the host is taken from the URL and applied unconditionally.
   */
  @Test
  public void testForUrlStampsIpLiteralHostFromTheUrl() throws Exception {
    final RecordingSocket socket = new RecordingSocket();
    final SSLSocketFactory factory = HostSettingSSLSocketFactory.forUrl(
        URI.create("https://10.83.6.18:6443/openid/v1/jwks").toURL(), new StubSSLSocketFactory(socket));

    factory.createSocket();

    assertEquals(IP_LITERAL, socket.host);
  }

  @Test
  public void testForUrlStampsDnsHostFromTheUrl() throws Exception {
    final RecordingSocket socket = new RecordingSocket();
    final SSLSocketFactory factory = HostSettingSSLSocketFactory.forUrl(
        URI.create("https://kubernetes.default.svc.cluster.local/openid/v1/jwks").toURL(),
        new StubSSLSocketFactory(socket));

    factory.createSocket();

    assertEquals("kubernetes.default.svc.cluster.local", socket.host);
  }

  /*
   * A socket that exposes setHost but rejects the call must not break the JWKS request: the host
   * is left unset, which is the behaviour from before it was applied, and the socket is returned.
   */
  @Test
  public void testSetHostReportsFailureWhenTheCallItselfThrows() throws Exception {
    try (ThrowingSocket socket = new ThrowingSocket()) {
      assertFalse(HostSettingSSLSocketFactory.setHost(socket, IP_LITERAL));
      assertTrue(socket.called);
    }
  }

  @Test
  public void testCreateSocketStillSucceedsWhenSetHostThrows() throws Exception {
    try (ThrowingSocket socket = new ThrowingSocket()) {
      final SSLSocketFactory factory =
          new HostSettingSSLSocketFactory(new StubSSLSocketFactory(socket), IP_LITERAL);

      assertSame(socket, factory.createSocket());
    }
  }

  /*
   * The decorator sits in the real JWKS retrieval path, so it must stay transparent: a wrapped
   * factory has to complete a genuine TLS handshake and fetch the key set.
   */
  @Test
  public void testRetrievesJwksThroughWrappedFactory() throws Exception {
    try (TestJwksHttpsServer server = TestJwksHttpsServer.start()) {
      final KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
      truststore.load(null, null);
      truststore.setCertificateEntry("ca", server.getCertificate());
      final SSLSocketFactory base =
          TruststoreSSLContextUtils.getTruststoreSSLContext(truststore).getSocketFactory();
      final URL jwksUrl = URI.create(server.getJwksUrl()).toURL();

      final SSLSocketFactory wrapped = HostSettingSSLSocketFactory.forUrl(jwksUrl, base);
      assertNotSame(base, wrapped);

      final Resource resource =
          new DefaultResourceRetriever(2000, 2000, 51200, true, wrapped).retrieveResource(jwksUrl);

      assertEquals(1, JWKSet.parse(resource.getContent()).getKeys().size());
    }
  }

  @Test
  public void testDelegatesCipherSuites() {
    final StubSSLSocketFactory delegate = new StubSSLSocketFactory(new RecordingSocket());
    final SSLSocketFactory factory = new HostSettingSSLSocketFactory(delegate, IP_LITERAL);

    assertArrayEquals(delegate.getDefaultCipherSuites(), factory.getDefaultCipherSuites());
    assertArrayEquals(delegate.getSupportedCipherSuites(), factory.getSupportedCipherSuites());
  }

  /* A Socket shaped like org.bouncycastle.jsse.BCSSLSocket, which declares setHost(String). */
  private static final class RecordingSocket extends Socket {
    private String host;

    public void setHost(String host) {
      this.host = host;
    }
  }

  /* A socket that advertises setHost but refuses the call. */
  private static final class ThrowingSocket extends Socket {
    private boolean called;

    public void setHost(String host) {
      called = true;
      throw new IllegalStateException("setHost refused for " + host);
    }
  }

  private static final class StubSSLSocketFactory extends SSLSocketFactory {
    private final Socket[] sockets;
    private int next;

    StubSSLSocketFactory(Socket... sockets) {
      this.sockets = sockets;
    }

    private Socket nextSocket() {
      return sockets[next++ % sockets.length];
    }

    @Override
    public String[] getDefaultCipherSuites() {
      return new String[] {"TLS_AES_128_GCM_SHA256"};
    }

    @Override
    public String[] getSupportedCipherSuites() {
      return new String[] {"TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384"};
    }

    @Override
    public Socket createSocket() throws IOException {
      return nextSocket();
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
      return nextSocket();
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
      return nextSocket();
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
      return nextSocket();
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
      return nextSocket();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
      return nextSocket();
    }
  }
}

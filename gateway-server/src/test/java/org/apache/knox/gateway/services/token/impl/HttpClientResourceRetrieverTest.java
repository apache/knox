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
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.TestJwksHttpsServer;
import org.apache.knox.gateway.util.OidcHttpClients;
import org.apache.knox.gateway.util.TruststoreSSLContextUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HttpClientResourceRetrieverTest {

  private static final int SIZE_LIMIT = 51200;
  private static final int TIMEOUT_MS = 2000;

  private TestJwksHttpsServer server;
  private URL jwksUrl;
  private SSLSocketFactory trustingFactory;

  @Before
  public void setUp() throws Exception {
    server = TestJwksHttpsServer.start();
    jwksUrl = URI.create(server.getJwksUrl()).toURL();
    final KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
    truststore.load(null, null);
    truststore.setCertificateEntry("ca", server.getCertificate());
    trustingFactory = TruststoreSSLContextUtils.getTruststoreSSLContext(truststore).getSocketFactory();
  }

  @After
  public void tearDown() throws Exception {
    if (server != null) {
      server.close();
    }
  }

  @Test
  public void testRetrievesTheKeySet() throws Exception {
    try (CloseableHttpClient client = clientTrusting(trustingFactory)) {
      final Resource resource = new HttpClientResourceRetriever(client, SIZE_LIMIT).retrieveResource(jwksUrl);

      assertEquals(1, JWKSet.parse(resource.getContent()).getKeys().size());
    }
  }

  /*
   * The reason this retriever exists. BouncyCastle's JSSE takes the hostname for HTTPS endpoint
   * identification from the socket; HttpsURLConnection, which Nimbus uses by default, creates the
   * socket with no host and so the check fails before the certificate is examined. Apache
   * HttpClient layers TLS over a connected socket and hands it the target host, which is what
   * makes endpoint identification work for every provider without any special casing.
   */
  @Test
  public void testSuppliesTheTargetHostToTheSocket() throws Exception {
    final HostRecordingSocketFactory recording = new HostRecordingSocketFactory(trustingFactory);

    try (CloseableHttpClient client = clientTrusting(recording)) {
      new HttpClientResourceRetriever(client, SIZE_LIMIT).retrieveResource(jwksUrl);
    }

    /*
     * Only the layered call is assertable: HttpClient creates the underlying plain socket through
     * its own connection factory, never through this one, so a "nothing hostless was created"
     * assertion here would hold no matter what the production code did.
     */
    assertEquals("the target host must reach the socket, or BouncyCastle cannot identify the endpoint",
        List.of("localhost"), recording.layeredHosts);
  }

  /*
   * The host is not fixed at construction: it comes from the URL of each request, so a JWKS
   * endpoint named by IP literal gets that IP and one named by DNS gets that name. Requests the
   * same server by a second spelling to show the recorded host tracks the URL rather than being
   * whatever the first request happened to use.
   */
  @Test
  public void testTheHostComesFromTheRequestedUrl() throws Exception {
    final HostRecordingSocketFactory recording = new HostRecordingSocketFactory(trustingFactory);
    /*
     * The address "localhost" resolves to on this host, which is the one Jetty bound - assuming
     * 127.0.0.1 would fail at connect wherever localhost is ::1.
     */
    final String loopbackIp = InetAddress.getByName("localhost").getHostAddress();
    final URL byLoopbackIp =
        URI.create("https://" + hostForUrl(loopbackIp) + ":" + jwksUrl.getPort() + jwksUrl.getPath()).toURL();

    try (CloseableHttpClient client = clientTrusting(recording)) {
      final HttpClientResourceRetriever retriever = new HttpClientResourceRetriever(client, SIZE_LIMIT);
      retriever.retrieveResource(jwksUrl);
      try {
        /*
         * The certificate is issued for localhost, so this spelling may fail verification - which
         * is correct, and beside the point: the host reaches the socket either way.
         */
        retriever.retrieveResource(byLoopbackIp);
      } catch (IOException expectedForAnUnmatchedName) { // NOPMD - asserted on the record below
        /* ignored */
      }
    }

    /*
     * URI.getHost() keeps the brackets on an IPv6 literal and HttpClient passes that form to the
     * socket, so both sides are normalised rather than assuming the bare form.
     */
    assertEquals("the host must follow each request's URL",
        List.of("localhost", hostForUrl(loopbackIp)), recording.layeredHosts);
  }

  /*
   * Redirects must not be followed. HttpClient would otherwise follow one across schemes, and a
   * 302 to http:// would fetch signing keys in cleartext - which the HttpURLConnection path this
   * replaced refused outright. A redirect has to surface as a failed retrieval instead.
   */
  @Test
  public void testDoesNotFollowRedirects() throws Exception {
    server.setStatus(302);

    try (CloseableHttpClient client = clientTrusting(trustingFactory)) {
      final HttpClientResourceRetriever retriever = new HttpClientResourceRetriever(client, SIZE_LIMIT);

      final IOException thrown = assertThrows(IOException.class, () -> retriever.retrieveResource(jwksUrl));
      /*
       * This retriever's own wording, not HttpClient's: were redirects enabled, HttpClient would
       * fail inside its redirect strategy with "Received redirect response ... but no location
       * header" - which also mentions 302, so matching the bare number would prove nothing.
       */
      assertTrue("the redirect must be reported by the retriever, not followed: " + thrown.getMessage(),
          thrown.getMessage().contains("HTTP 302"));
    }
  }

  @Test
  public void testRejectsANonOkResponse() throws Exception {
    server.setStatus(503);

    try (CloseableHttpClient client = clientTrusting(trustingFactory)) {
      final HttpClientResourceRetriever retriever = new HttpClientResourceRetriever(client, SIZE_LIMIT);

      final IOException thrown = assertThrows(IOException.class, () -> retriever.retrieveResource(jwksUrl));
      assertTrue("the status belongs in the message: " + thrown.getMessage(),
          thrown.getMessage().contains("503"));
    }
  }

  @Test
  public void testRejectsAResponseOverTheSizeLimit() throws Exception {
    try (CloseableHttpClient client = clientTrusting(trustingFactory)) {
      final HttpClientResourceRetriever retriever = new HttpClientResourceRetriever(client, 16);

      final IOException thrown = assertThrows(IOException.class, () -> retriever.retrieveResource(jwksUrl));
      assertTrue("the limit belongs in the message: " + thrown.getMessage(),
          thrown.getMessage().contains("16"));
    }
  }

  /*
   * The production builder, not a hand-rolled client: these tests have to exercise the real
   * configuration - redirect policy above all - or they assert nothing about what ships.
   */
  private static CloseableHttpClient clientTrusting(SSLSocketFactory socketFactory) {
    return OidcHttpClients.trusting(TIMEOUT_MS, TIMEOUT_MS, socketFactory,
        OidcHttpClients.NO_RETRIES);
  }


  /* An IPv6 literal is bracketed both in a URL and in what URI.getHost() hands back. */
  private static String hostForUrl(String ip) {
    return ip.indexOf(':') >= 0 ? "[" + ip + "]" : ip;
  }

  /* Records the target host handed to the layered socket, which is the call HttpClient makes. */
  private static final class HostRecordingSocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;
    private final List<String> layeredHosts = new ArrayList<>();

    HostRecordingSocketFactory(SSLSocketFactory delegate) {
      this.delegate = delegate;
    }

    @Override
    public String[] getDefaultCipherSuites() {
      return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
      return delegate.getSupportedCipherSuites();
    }


    @Override
    public Socket createSocket(String host, int port) throws IOException {
      layeredHosts.add(host);
      return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
      layeredHosts.add(host);
      return delegate.createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
      return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
      return delegate.createSocket(address, port, localAddress, localPort);
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
      layeredHosts.add(host);
      return delegate.createSocket(s, host, port, autoClose);
    }
  }
}

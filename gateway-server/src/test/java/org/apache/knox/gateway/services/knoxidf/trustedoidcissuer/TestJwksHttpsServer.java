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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A local HTTPS server standing in for an issuer's JWKS endpoint, with a self-signed
 * {@code CN=localhost} certificate acting as the issuer's CA. Both the TLS certificate and the JWKS
 * signing key can be rotated while the server keeps running.
 */
public final class TestJwksHttpsServer implements AutoCloseable {

  private static final String KEYSTORE_PASSWORD = "changeit";

  private final Server server;
  private final ServerConnector connector;
  private final SslContextFactory.Server sslContextFactory;
  private final AtomicInteger requests = new AtomicInteger();
  private KeyPair currentKeyPair;
  private final AtomicReference<X509Certificate> certificate = new AtomicReference<>();
  private final AtomicReference<SigningKey> signingKey = new AtomicReference<>();
  private final AtomicInteger status = new AtomicInteger(200);

  private TestJwksHttpsServer() throws Exception {
    certificate.set(newCertificate());
    signingKey.set(SigningKey.generate("kid-1"));
    sslContextFactory = new SslContextFactory.Server();
    sslContextFactory.setKeyStore(keyStore(certificate.get(), currentKeyPair));
    sslContextFactory.setKeyStorePassword(KEYSTORE_PASSWORD);
    sslContextFactory.setKeyManagerPassword(KEYSTORE_PASSWORD);
    server = new Server();
    connector = new ServerConnector(server, sslContextFactory);
    connector.setHost("localhost");
    connector.setPort(0);
    server.addConnector(connector);
    server.setHandler(new JwksHandler());
  }

  public static TestJwksHttpsServer start() throws Exception {
    final TestJwksHttpsServer jwksServer = new TestJwksHttpsServer();
    jwksServer.server.start();
    return jwksServer;
  }

  public String getJwksUrl() {
    return "https://localhost:" + connector.getLocalPort() + "/jwks";
  }

  /** @return the server's current self-signed certificate, i.e. the issuer's CA */
  public X509Certificate getCertificate() {
    return certificate.get();
  }

  /** @return the PEM of the server's current self-signed certificate, i.e. the issuer's CA bundle */
  public String getCaPem() throws GeneralSecurityException {
    return TestCaBundles.toPem(certificate.get());
  }

  public int getRequestCount() {
    return requests.get();
  }

  /** Makes the JWKS endpoint answer with the given HTTP status and an empty key set. */
  public void setStatus(int status) {
    this.status.set(status);
  }

  /** Re-issues the server certificate under a new self-signed CA, without restarting the server. */
  public void rotateCertificate() throws Exception {
    final X509Certificate rotated = newCertificate();
    final KeyStore keyStore = keyStore(rotated, currentKeyPair);
    sslContextFactory.reload(factory -> factory.setKeyStore(keyStore));
    certificate.set(rotated);
  }

  /** Replaces the JWKS signing key by one with a new key ID, as an apiserver signing key rotation does. */
  public void rotateSigningKey(String kid) throws GeneralSecurityException {
    signingKey.set(SigningKey.generate(kid));
  }

  /** @return a token signed with the current JWKS signing key */
  public String signToken(String issuer) throws Exception {
    final SigningKey key = signingKey.get();
    final SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.kid()).type(JOSEObjectType.JWT).build(),
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject("system:serviceaccount:default:workload")
            .expirationTime(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)))
            .build());
    jwt.sign(new RSASSASigner((RSAPrivateKey) key.keyPair().getPrivate()));
    return jwt.serialize();
  }

  @Override
  public void close() throws Exception {
    server.stop();
  }

  private X509Certificate newCertificate() throws GeneralSecurityException {
    currentKeyPair = TestCaBundles.generateKeyPair();
    return TestCaBundles.selfSigned("CN=localhost", currentKeyPair);
  }

  private static KeyStore keyStore(X509Certificate certificate, KeyPair keyPair) throws Exception {
    final KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null, null);
    keyStore.setKeyEntry("server", keyPair.getPrivate(), KEYSTORE_PASSWORD.toCharArray(),
        new Certificate[] {certificate});
    return keyStore;
  }

  private record SigningKey(String kid, KeyPair keyPair, String jwks) {
    static SigningKey generate(String kid) throws GeneralSecurityException {
      final KeyPair keyPair = TestCaBundles.generateKeyPair();
      final String jwks = new JWKSet(new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
          .keyID(kid)
          .keyUse(KeyUse.SIGNATURE)
          .algorithm(JWSAlgorithm.RS256)
          .build()).toString();
      return new SigningKey(kid, keyPair, jwks);
    }
  }

  private final class JwksHandler extends Handler.Abstract {
    @Override
    public boolean handle(Request request, Response response, Callback callback) {
      requests.incrementAndGet();
      final int responseStatus = status.get();
      response.setStatus(responseStatus);
      response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/json");
      // no keep-alive, so every fetch makes a fresh TLS handshake against the current certificate
      response.getHeaders().put(HttpHeader.CONNECTION, HttpHeaderValue.CLOSE.asString());
      Content.Sink.write(response, true, responseStatus == 200 ? signingKey.get().jwks() : "{\"keys\":[]}", callback);
      return true;
    }
  }
}

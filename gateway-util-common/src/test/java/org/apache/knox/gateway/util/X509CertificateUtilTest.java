/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.util;

import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class X509CertificateUtilTest {

  @Test
  public void testFetchPublicCertsFromServer() throws Exception {
    // 1. Generate a self-signed certificate
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();
    X509Certificate cert = X509CertificateUtil.generateCertificate("CN=localhost", keyPair, 30, "SHA256withRSA");

    // 2. Set up a KeyStore with the certificate
    KeyStore keyStore = KeyStore.getInstance("JKS");
    keyStore.load(null, null);
    keyStore.setKeyEntry("alias", keyPair.getPrivate(), "password".toCharArray(), new java.security.cert.Certificate[]{cert});

    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(keyStore, "password".toCharArray());

    SSLContext serverSslContext = SSLContext.getInstance("TLS");
    serverSslContext.init(kmf.getKeyManagers(), null, new SecureRandom());

    SSLServerSocketFactory ssf = serverSslContext.getServerSocketFactory();
    try (SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(0)) {
      int port = serverSocket.getLocalPort();

      ExecutorService executor = Executors.newSingleThreadExecutor();
      Future<Void> serverFuture = executor.submit(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          try (SSLSocket clientSocket = (SSLSocket) serverSocket.accept()) {
            clientSocket.startHandshake();
          } catch (IOException e) {
            // Expected if client closes connection early
          }
          return null;
        }
      });

      // 3. Fetch the certificate from the server
      try {
        String serverUrl = "https://localhost:" + port;
        X509Certificate[] certs = X509CertificateUtil.fetchPublicCertsFromServer(serverUrl, null, true, null);

        // 4. Verify
        Assert.assertNotNull(certs);
        Assert.assertTrue(certs.length > 0);
        Assert.assertEquals(cert.getSubjectX500Principal(), certs[0].getSubjectX500Principal());
        Assert.assertEquals(cert.getPublicKey(), certs[0].getPublicKey());
      } finally {
        serverFuture.get(5, TimeUnit.SECONDS);
        executor.shutdown();
      }
    }
  }
}

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

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Guards BouncyCastle upgrades: checks the certificates Knox builds with BouncyCastle in detail, and that
 * BCFKS keystores written by earlier BouncyCastle versions still load with the current one.
 */
public class BouncyCastleCompatibilityTest {

  // BouncyCastle versions that wrote a fixture under src/test/resources/bouncycastle (see GenerateBouncyCastleFixtures.java)
  private static final List<String> FIXTURE_VERSIONS = List.of("1.85");
  private static final String FIXTURE_PASSWORD = "knox-bc-fixture";

  // The DN template DefaultKeystoreService uses for the gateway identity
  private static final String GATEWAY_DN = "CN=knox.example.com,OU=Test,O=Hadoop,L=Test,ST=Test,C=US";
  // BouncyCastle encodes the RDNs in the order they appear in GATEWAY_DN; RFC 2253 renders the ASN.1 sequence
  // in reverse. Pinning the rendering catches an upgrade that changes the encoded DN of generated certificates.
  private static final String GATEWAY_DN_RFC2253 = "C=US,ST=Test,L=Test,O=Hadoop,OU=Test,CN=knox.example.com";
  private static final int VALIDITY_DAYS = 365;

  private static boolean providerAdded;

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @BeforeClass
  public static void addProvider() {
    providerAdded = Security.addProvider(new BouncyCastleProvider()) != -1;
  }

  @AfterClass
  public static void removeProvider() {
    if (providerAdded) {
      Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
    }
  }

  @Test
  public void testGeneratedRsaCertificate() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    assertGeneratedCertificate(kpg.generateKeyPair(), "SHA256withRSA");
  }

  @Test
  public void testGeneratedEcCertificate() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
    kpg.initialize(new ECGenParameterSpec("secp256r1"));
    assertGeneratedCertificate(kpg.generateKeyPair(), "SHA256withECDSA");
  }

  @Test
  public void testGeneratedCertificatePemIsReadableByJdk() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    X509Certificate cert = X509CertificateUtil.generateCertificate(GATEWAY_DN, kpg.generateKeyPair(), VALIDITY_DAYS, "SHA256withRSA");
    assertNotNull("Certificate generation failed, see the log", cert);

    File pem = temporaryFolder.newFile("cert.pem");
    X509CertificateUtil.writeCertificateToFile(cert, pem);

    X509Certificate parsed;
    try (InputStream in = Files.newInputStream(pem.toPath())) {
      // The JDK's own X.509 parser, not BouncyCastle's
      parsed = (X509Certificate) CertificateFactory.getInstance("X.509", "SUN").generateCertificate(in);
    }
    assertArrayEquals(cert.getEncoded(), parsed.getEncoded());
    parsed.verify(cert.getPublicKey());
  }

  @Test
  public void testBcfksFixturesFromEarlierBouncyCastleVersionsStillLoad() throws Exception {
    for (String version : FIXTURE_VERSIONS) {
      X509Certificate expected;
      try (InputStream in = fixture("bc-" + version + ".pem")) {
        expected = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
      }

      KeyStore keyStore = KeyStore.getInstance("BCFKS", BouncyCastleProvider.PROVIDER_NAME);
      try (InputStream in = fixture("bc-" + version + ".bcfks")) {
        keyStore.load(in, FIXTURE_PASSWORD.toCharArray());
      }

      String context = "BCFKS written by BouncyCastle " + version;
      assertTrue(context, keyStore.isKeyEntry("gateway-identity"));
      assertTrue(context, keyStore.isCertificateEntry("trusted-cert"));
      assertEquals(context, expected, keyStore.getCertificate("trusted-cert"));
      assertEquals(context, expected, keyStore.getCertificateChain("gateway-identity")[0]);

      // The private key must still be usable and must match the certificate
      PrivateKey key = (PrivateKey) keyStore.getKey("gateway-identity", FIXTURE_PASSWORD.toCharArray());
      assertNotNull(context, key);
      byte[] data = "knox".getBytes(StandardCharsets.UTF_8);
      Signature signer = Signature.getInstance("SHA256withRSA");
      signer.initSign(key);
      signer.update(data);
      byte[] signature = signer.sign();
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(expected.getPublicKey());
      verifier.update(data);
      assertTrue(context, verifier.verify(signature));
    }
  }

  private static void assertGeneratedCertificate(KeyPair pair, String algorithm) throws Exception {
    X509Certificate cert = X509CertificateUtil.generateCertificate(GATEWAY_DN, pair, VALIDITY_DAYS, algorithm);
    assertNotNull("Certificate generation failed, see the log", cert);

    cert.checkValidity();
    cert.verify(pair.getPublic());
    assertEquals(pair.getPublic(), cert.getPublicKey());
    assertEquals(algorithm, cert.getSigAlgName());
    assertEquals(3, cert.getVersion());
    assertEquals(GATEWAY_DN_RFC2253, cert.getSubjectX500Principal().getName());
    assertEquals(GATEWAY_DN_RFC2253, cert.getIssuerX500Principal().getName());
    assertTrue(X509CertificateUtil.isSelfSignedCertificate(cert));
    assertEquals(TimeUnit.DAYS.toMillis(VALIDITY_DAYS), cert.getNotAfter().getTime() - cert.getNotBefore().getTime());

    Collection<List<?>> sans = cert.getSubjectAlternativeNames();
    assertNotNull(sans);
    assertEquals(1, sans.size());
    List<?> san = sans.iterator().next();
    assertEquals(2, san.get(0)); // dNSName
    assertEquals("knox.example.com", san.get(1));
  }

  private static InputStream fixture(String name) {
    InputStream in = BouncyCastleCompatibilityTest.class.getResourceAsStream("/bouncycastle/" + name);
    assertNotNull("Missing fixture " + name, in);
    return in;
  }
}

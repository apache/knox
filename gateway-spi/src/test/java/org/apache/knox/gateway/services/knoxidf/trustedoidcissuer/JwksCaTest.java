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

import org.apache.knox.gateway.util.X509CertificateUtil;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JwksCaTest {

  @Test
  public void testIsSecretReference() {
    assertTrue(JwksCa.isSecretReference("secret:knox/ca#ca.crt"));
    assertTrue(JwksCa.isSecretReference("secret:bogus"));
    assertFalse(JwksCa.isSecretReference("-----BEGIN CERTIFICATE-----"));
    assertFalse(JwksCa.isSecretReference(null));
  }

  @Test
  public void testParseSecretReferenceWithNamespace() {
    final JwksCa.SecretReference ref = JwksCa.parseSecretReference("secret:awc-1/apiserver-ca.v2#ca.crt");
    assertEquals("awc-1", ref.namespace());
    assertEquals("apiserver-ca.v2", ref.name());
    assertEquals("ca.crt", ref.key());
    assertEquals("awc-1/apiserver-ca.v2#ca.crt", ref.toString());
  }

  @Test
  public void testParseSecretReferenceDefaultsNamespace() {
    final JwksCa.SecretReference ref = JwksCa.parseSecretReference("secret:apiserver-ca#ca_bundle-1.pem");
    assertEquals(JwksCa.DEFAULT_SECRET_NAMESPACE, ref.namespace());
    assertEquals("knox", ref.namespace());
    assertEquals("apiserver-ca", ref.name());
    assertEquals("ca_bundle-1.pem", ref.key());
  }

  @Test
  public void testParseSecretReferenceRejectsMalformed() {
    for (String value : new String[] {
        null,
        "",
        "secret:",
        "secret:name",                 // no key
        "secret:name#",                // empty key
        "secret:#ca.crt",              // no name
        "secret:/name#ca.crt",         // empty namespace
        "secret:ns/#ca.crt",           // empty name
        "secret:Ns/name#ca.crt",       // upper-case namespace
        "secret:ns/Name#ca.crt",       // upper-case name
        "secret:ns.x/name#ca.crt",     // dot in namespace (label, not subdomain)
        "secret:-ns/name#ca.crt",      // leading dash
        "secret:ns/name-#ca.crt",      // trailing dash
        "secret:a/b/c#ca.crt",         // too many segments
        "secret:ns/name#ca/crt",       // slash in key
        "secret:ns/name#ca.crt#x",     // two keys
        "secret: ns/name#ca.crt",      // whitespace
        "ns/name#ca.crt"               // no prefix
    }) {
      final IllegalArgumentException e =
          assertThrows(String.valueOf(value), IllegalArgumentException.class, () -> JwksCa.parseSecretReference(value));
      assertTrue(e.getMessage(), e.getMessage().contains("secret:[<namespace>/]<name>#<key>"));
    }
  }

  @Test
  public void testParsePemBundleSingleCertificate() throws Exception {
    final X509Certificate cert = generateCertificate("CN=ca-a");
    final List<X509Certificate> parsed = JwksCa.parsePemBundle(toPem(cert));
    assertEquals(List.of(cert), parsed);
  }

  @Test
  public void testParsePemBundleMultipleCertificates() throws Exception {
    final X509Certificate a = generateCertificate("CN=ca-a");
    final X509Certificate b = generateCertificate("CN=ca-b");
    // surrounding whitespace, as pasted from a file or a YAML block scalar
    final List<X509Certificate> parsed = JwksCa.parsePemBundle("\n  " + toPem(a) + "\n" + toPem(b) + "\n\n");
    assertEquals(List.of(a, b), parsed);
  }

  @Test
  public void testParsePemBundleRejectsInvalid() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> JwksCa.parsePemBundle(null));
    assertThrows(IllegalArgumentException.class, () -> JwksCa.parsePemBundle("  \n"));
    assertThrows(IllegalArgumentException.class, () -> JwksCa.parsePemBundle("not a certificate"));
    assertThrows(IllegalArgumentException.class, () -> JwksCa.parsePemBundle(
        "-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----"));
    final String truncated = toPem(generateCertificate("CN=ca-a"));
    assertThrows(IllegalArgumentException.class,
        () -> JwksCa.parsePemBundle(truncated.substring(0, truncated.length() / 2)));
  }

  static X509Certificate generateCertificate(String dn) throws Exception {
    final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return X509CertificateUtil.generateCertificate(dn, generator.generateKeyPair(), 1, "SHA256withRSA");
  }

  static String toPem(X509Certificate cert) throws Exception {
    final Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));
    return "-----BEGIN CERTIFICATE-----\n" + encoder.encodeToString(cert.getEncoded()) + "\n-----END CERTIFICATE-----\n";
  }
}

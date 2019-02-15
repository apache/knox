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
package org.apache.knox.gateway.services.security.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class X509CertificateUtilTest {
  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  static X509Certificate certificate;

  @BeforeClass
  public static void createCertificate() throws NoSuchAlgorithmException {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();
    String dn = "cn=test,ou=hadoop";

    certificate = X509CertificateUtil.generateCertificate(dn, keyPair, 365, "SHA1withRSA");
  }

  @Test
  public void testGenerateCertificate() throws Exception {
    String expectedDn = "CN=test, OU=hadoop";

    assertEquals(expectedDn, certificate.getIssuerDN().getName());
    assertEquals(expectedDn, certificate.getSubjectDN().getName());
    assertEquals("SHA1withRSA", certificate.getSigAlgName());

    certificate.checkValidity();
  }

  @Test(expected = CertificateNotYetValidException.class)
  public void testGenerateCertificateValidityPeriodBefore() throws Exception {
    Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.ROOT);
    calendar.add(Calendar.DAY_OF_YEAR, -1);
    certificate.checkValidity(calendar.getTime());
  }

  @Test(expected = CertificateExpiredException.class)
  public void testGenerateCertificateValidityPeriodAfter() throws Exception {
    Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.ROOT);
    calendar.add(Calendar.DAY_OF_YEAR, 365);
    certificate.checkValidity(calendar.getTime());
  }

  @Test
  public void testWriteCertificateToFile() throws Exception {
    File file = testFolder.newFile();
    assertTrue(file.delete());

    assertFalse(file.exists());
    X509CertificateUtil.writeCertificateToFile(certificate, file);
    assertTrue(file.exists());

    BufferedReader fileReader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);

    String line = fileReader.readLine();
    String lastLine = null;

    assertEquals("-----BEGIN CERTIFICATE-----", line);
    while (line != null) {
      lastLine = line;
      line = fileReader.readLine();
    }

    assertEquals("-----END CERTIFICATE-----", lastLine);
  }

  @Test
  public void testWriteCertificateToJks() throws Exception {
    testWriteCertificateToXXX("jks",
        (alias, file) -> X509CertificateUtil.writeCertificateToJks(certificate, alias, file));
  }

  @Test
  public void testWriteCertificateToJceks() throws Exception {
    testWriteCertificateToXXX("jceks",
        (alias, file) -> X509CertificateUtil.writeCertificateToJceks(certificate, alias, file));
  }

  @Test
  public void testWriteCertificateToPkcs12() throws Exception {
    testWriteCertificateToXXX("pkcs12",
        (alias, file) -> X509CertificateUtil.writeCertificateToPkcs12(certificate, alias, file));
  }

  @Test
  public void testIsSelfSignedCertificate() throws Exception {
    assertTrue(X509CertificateUtil.isSelfSignedCertificate(certificate));
    // TODO: Programmatically create a non-selfsigned cert and test
  }

  private void testWriteCertificateToXXX(String storeType, Operation operation)
      throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
    File file = testFolder.newFile();
    assertTrue(file.delete());

    assertFalse(file.exists());

    operation.execute("my_alias", file);
    assertTrue(file.exists());

    InputStream input = Files.newInputStream(file.toPath());
    assertNotNull(input);
    KeyStore keyStore = KeyStore.getInstance(storeType);
    keyStore.load(input, "changeit".toCharArray());

    assertEquals(1, keyStore.size());

    Certificate inCertificate = keyStore.getCertificate("my_alias");
    assertEquals(certificate, inCertificate);
  }

  private interface Operation {
    void execute(String alias, File file) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException;
  }
}
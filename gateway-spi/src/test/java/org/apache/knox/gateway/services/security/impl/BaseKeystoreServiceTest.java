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

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.MasterService;
import org.easymock.EasyMockSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.Locale;

public class BaseKeystoreServiceTest extends EasyMockSupport {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Test(expected = KeystoreServiceException.class)
  public void testCreateKeystoreWithBadType() throws IOException, KeystoreServiceException {
    BaseKeystoreService baseKeystoreService = createMockBuilder(BaseKeystoreService.class).createMock();
    baseKeystoreService.createKeystore(testFolder.newFile(), "INVALID_TYPE", "password".toCharArray());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateKeystoreWithNullPassword() throws IOException, KeystoreServiceException {
    BaseKeystoreService baseKeystoreService = createMockBuilder(BaseKeystoreService.class).createMock();
    baseKeystoreService.createKeystore(testFolder.newFile(), "JKS", null);
  }

  @Test
  public void testCreateGetAndCheckKeystore() throws IOException, KeystoreServiceException, KeyStoreException {
    BaseKeystoreService baseKeystoreService = createMockBuilder(BaseKeystoreService.class).createMock();

    // Test the popular keystore types...
    for (String keystoreType : new String[]{"jks", "jceks", "pkcs12"}) {
      testCreateGetAndCheckKeystore(baseKeystoreService, keystoreType);
    }
  }

  @Test
  public void testCreateGetAndRemoveCredential() throws Exception {
    BaseKeystoreService baseKeystoreService = createMockBuilder(BaseKeystoreService.class).createMock();

    // This appears to only work for JCEKS keystores.
    testCreateGetAndRemoveCredential(baseKeystoreService, "jceks");
  }

  @Test
  public void testWriteCertificateToFile() throws IOException, NoSuchAlgorithmException, CertificateEncodingException {
    BaseKeystoreService baseKeystoreService = createMockBuilder(BaseKeystoreService.class).createMock();

    File file = testFolder.newFile();
    Certificate outCertificate = createCertificate();
    baseKeystoreService.writeCertificateToFile(outCertificate, file);

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
  public void writeKeystoreToFile() throws IOException, KeystoreServiceException, CertificateException, NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
    BaseKeystoreService baseKeystoreService = createMockBuilder(BaseKeystoreService.class).createMock();

    File keystoreFile = testFolder.newFile("keystore.jceks");
    assertTrue(keystoreFile.delete());

    assertFalse(keystoreFile.exists());

    String keystoreType = "jceks";
    char[] keystorePassword = "keystore_spassword".toCharArray();

    baseKeystoreService.createKeystore(keystoreFile, keystoreType, keystorePassword);
    KeyStore keystore = baseKeystoreService.getKeystore(keystoreFile, keystoreType, keystorePassword);
    assertEquals(0, keystore.size());
    baseKeystoreService.writeKeystoreToFile(keystore, keystorePassword, keystoreFile);

    assertTrue(keystoreFile.exists());

    keystore = baseKeystoreService.getKeystore(keystoreFile, keystoreType, keystorePassword);
    assertEquals(0, keystore.size());

    baseKeystoreService.addCredential("alias", "password", keystore, "password".toCharArray());

    KeyStore keystore2 = baseKeystoreService.getKeystore(keystoreFile, keystoreType, keystorePassword);
    assertEquals(0, keystore2.size());

    assertEquals(1, keystore.size());
    baseKeystoreService.writeKeystoreToFile(keystore, keystorePassword, keystoreFile);
    keystore2 = baseKeystoreService.getKeystore(keystoreFile, keystoreType, keystorePassword);
    assertEquals(1, keystore2.size());

    assertNotSame(keystore, keystore2);

    assertEquals(keystore.getKey("alias", "password".toCharArray()), keystore2.getKey("alias", "password".toCharArray()));
  }

  @Test
  public void testGetMasterSecret() {
    BaseKeystoreService baseKeystoreService = createMockBuilder(BaseKeystoreService.class).createMock();

    MasterService masterService = createStrictMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn("secret".toCharArray()).once();

    replayAll();
    baseKeystoreService.setMasterService(masterService);
    assertEquals("secret", String.valueOf(baseKeystoreService.getMasterSecret()));

    verifyAll();
  }

  private void testCreateGetAndCheckKeystore(BaseKeystoreService baseKeystoreService, String keystoreType)
      throws IOException, KeystoreServiceException, KeyStoreException {
    File keystoreFile = testFolder.newFile("keystore." + keystoreType);
    char[] password = "password".toCharArray();

    baseKeystoreService.createKeystore(keystoreFile, keystoreType, password);
    assertTrue(baseKeystoreService.isKeystoreAvailable(keystoreFile, keystoreType, password));

    baseKeystoreService.getKeystore(keystoreFile, keystoreType, password);

    // Test bad file location
    assertFalse(baseKeystoreService.isKeystoreAvailable(new File("invalid_file_location"), keystoreType, password));
    assertEquals(0, baseKeystoreService.getKeystore(new File("invalid_file_location"), keystoreType, password).size());

    // Test bad type
    try {
      baseKeystoreService.isKeystoreAvailable(keystoreFile, ("PKCS12".equalsIgnoreCase(keystoreType)) ? "JCEKS" : "PKCS12", password);
      fail("Expected IOException due to bad keystore type.");
    } catch (IOException e) {
      // expected...
    }
    try {
      baseKeystoreService.getKeystore(keystoreFile, ("PKCS12".equalsIgnoreCase(keystoreType)) ? "JCEKS" : "PKCS12", password);
      fail("Expected KeystoreServiceException due to bad keystore type.");
    } catch (KeystoreServiceException e) {
      // expected...
    }

    // Test bad password
    try {
      baseKeystoreService.isKeystoreAvailable(keystoreFile, keystoreType, "wrong_password".toCharArray());
      fail("Expected IOException due to bad password.");
    } catch (IOException e) {
      // expected...
    }
    try {
      baseKeystoreService.getKeystore(keystoreFile, keystoreType, "wrong_password".toCharArray());
      fail("Expected KeystoreServiceException due to bad password.");
    } catch (KeystoreServiceException e) {
      // expected...
    }
  }

  private void testCreateGetAndRemoveCredential(BaseKeystoreService baseKeystoreService, String keystoreType) throws Exception {
    File keystoreFile = testFolder.newFile("keystore." + keystoreType);
    char[] keystorePassword = "keystore_password".toCharArray();
    char[] keyPassword = "key_password".toCharArray();

    baseKeystoreService.createKeystore(keystoreFile, keystoreType, keystorePassword);
    KeyStore keystore = baseKeystoreService.getKeystore(keystoreFile, keystoreType, keystorePassword);

    // Add credential
    baseKeystoreService.addCredential("my-alias", "my_secret", keystore, keyPassword);

    // Verify credential was added
    assertEquals("my_secret", String.valueOf(baseKeystoreService.getCredential("my-alias", keystore, keyPassword)));

    // Remove credential
    baseKeystoreService.removeCredential("my-alias", keystore);

    // Verify credential was removed
    assertNull(baseKeystoreService.getCredential("my-alias", keystore, keyPassword));

    // Test bad alias
    assertNull(baseKeystoreService.getCredential("not-my-alias", keystore, keyPassword));

    // Test password
    assertNull(baseKeystoreService.getCredential("my-alias", keystore, "wrong_password".toCharArray()));
  }

  private Certificate createCertificate() throws NoSuchAlgorithmException {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    return X509CertificateUtil.generateCertificate(
        String.format(Locale.ROOT, "CN=%s,OU=Test,O=Hadoop,L=Test,ST=Test,C=US", this.getClass().getName()),
        keyPairGenerator.generateKeyPair(),
        365,
        "SHA1withRSA");
  }
}
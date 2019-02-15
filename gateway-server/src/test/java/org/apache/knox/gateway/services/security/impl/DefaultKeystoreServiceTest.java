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

import static org.apache.knox.gateway.config.GatewayConfig.IDENTITY_KEYSTORE_PASSWORD_ALIAS;
import static org.apache.knox.gateway.config.GatewayConfig.IDENTITY_KEYSTORE_PATH;
import static org.apache.knox.gateway.config.GatewayConfig.IDENTITY_KEYSTORE_TYPE;
import static org.apache.knox.gateway.config.GatewayConfig.IDENTITY_KEY_ALIAS;
import static org.apache.knox.gateway.config.GatewayConfig.IDENTITY_KEY_PASSPHRASE_ALIAS;
import static org.apache.knox.gateway.config.GatewayConfig.SIGNING_KEYSTORE_NAME;
import static org.apache.knox.gateway.config.GatewayConfig.SIGNING_KEY_ALIAS;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Locale;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.MasterService;
import org.easymock.EasyMockSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DefaultKeystoreServiceTest extends EasyMockSupport {
  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void testKeystoreForGateway() throws Exception {
    char[] masterPassword = "master_password".toCharArray();

    MasterService masterService = createMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn(masterPassword).anyTimes();

    char[] keyPassword = "key_password".toCharArray();
    char[] customKeyPassword = "custom-key-passphrase".toCharArray();
    char[] customKeystorePassword = "custom-keystore-password".toCharArray();

    DefaultKeystoreService keystoreServiceAlt = createMockBuilder(DefaultKeystoreService.class)
        .addMockedMethod("getCredentialForCluster", String.class, String.class)
        .createMock();
    expect(keystoreServiceAlt.getCredentialForCluster(eq("__gateway"), eq("custom_key_passphrase_alias")))
        .andReturn(customKeyPassword)
        .anyTimes();
    expect(keystoreServiceAlt.getCredentialForCluster(eq("__gateway"), eq("custom_keystore_password_alias")))
        .andReturn(customKeystorePassword)
        .anyTimes();

    BaseKeystoreService baseKeystoreService = createMockBuilder(BaseKeystoreService.class).createMock();

    replayAll();

    File baseDir = testFolder.newFolder();
    GatewayConfigImpl config = createGatewayConfig(baseDir);
    DefaultKeystoreService keystoreService;

    /* *******************
     * Test Defaults
     */
    File defaultKeystoreFile = Paths.get(baseDir.getAbsolutePath(), "security", "keystores", "gateway.jks").toFile();

    keystoreService = new DefaultKeystoreService();
    keystoreService.setMasterService(masterService);
    keystoreService.init(config, Collections.emptyMap());

    testCreateGetAndCheckKeystoreForGateway(keystoreService,
        defaultKeystoreFile,
        GatewayConfigImpl.DEFAULT_IDENTITY_KEY_ALIAS,
        keyPassword, config);


    /* *******************
     * Test Custom Values
     */
    File customKeystoreFile = Paths.get(baseDir.getAbsolutePath(), "test", "keystore", "custom_keystore.p12").toFile();
    String customKeystoreType = "pkcs12";
    String customAlias = "custom_alias";

    config.set(IDENTITY_KEYSTORE_PATH, customKeystoreFile.getAbsolutePath());
    config.set(IDENTITY_KEYSTORE_TYPE, customKeystoreType);
    config.set(IDENTITY_KEYSTORE_PASSWORD_ALIAS, "custom_keystore_password_alias");
    config.set(IDENTITY_KEY_ALIAS, customAlias);
    config.set(IDENTITY_KEY_PASSPHRASE_ALIAS, "custom_key_passphrase_alias");

    keystoreServiceAlt.setMasterService(masterService);


    keystoreServiceAlt.init(config, Collections.emptyMap());
    keystoreServiceAlt.init(config, Collections.emptyMap());

    testCreateGetAndCheckKeystoreForGateway(keystoreServiceAlt, customKeystoreFile, customAlias, customKeyPassword, config);

    // Verify the keystore passwords are set properly...
    assertTrue(defaultKeystoreFile.exists());
    assertNotNull(baseKeystoreService.getKeystore(defaultKeystoreFile, GatewayConfigImpl.DEFAULT_IDENTITY_KEYSTORE_TYPE, masterPassword));
    assertTrue(customKeystoreFile.exists());
    assertNotNull(baseKeystoreService.getKeystore(customKeystoreFile, customKeystoreType, customKeystorePassword));

    verifyAll();
  }

  @Test
  public void testSigningKeystore() throws Exception {
    char[] masterPassword = "master_password".toCharArray();

    MasterService masterService = createMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn(masterPassword).anyTimes();

    DefaultKeystoreService keystoreServiceAlt = createMockBuilder(DefaultKeystoreService.class)
        .addMockedMethod("getCredentialForCluster", String.class, String.class)
        .createMock();

    BaseKeystoreService baseKeystoreService = createMockBuilder(BaseKeystoreService.class).createMock();

    replayAll();

    File baseDir = testFolder.newFolder();
    GatewayConfigImpl config = createGatewayConfig(baseDir);

    /* *******************
     * Test Defaults
     */
    File defaultFile = Paths.get(baseDir.getAbsolutePath(), "security", "keystores", "gateway.jks").toFile();
    String defaultAlias = "gateway-identity";

    DefaultKeystoreService keystoreService = new DefaultKeystoreService();
    keystoreService.setMasterService(masterService);

    try {
      keystoreService.init(config, Collections.emptyMap());
    } catch (ServiceLifecycleException e) {
      fail("Not expecting ServiceLifecycleException due to missing signing keystore file since a custom one is not specified");
    }

    createKeystore(baseKeystoreService, defaultFile, defaultAlias, masterPassword);

    keystoreService.init(config, Collections.emptyMap());

    testSigningKeystore(keystoreService, defaultFile, defaultAlias, masterPassword);

    /* *******************
     * Test Custom Values
     */
    String customFileName = "custom_signing.jks";
    File customFile = Paths.get(baseDir.getAbsolutePath(), "security", "keystores", customFileName).toFile();
    String customAlias = "custom_alias";

    config.set(SIGNING_KEYSTORE_NAME, customFileName);
    config.set(SIGNING_KEY_ALIAS, customAlias);

    keystoreServiceAlt.setMasterService(masterService);

    // Ensure the signing keystore exists before init-ing the keystore service
    createKeystore(baseKeystoreService, customFile, customAlias, masterPassword);

    keystoreServiceAlt.init(config, Collections.emptyMap());

    testSigningKeystore(keystoreServiceAlt, customFile, customAlias, masterPassword);

    // Verify the keystore passwords are set properly...
    assertTrue(defaultFile.exists());
    assertNotNull(baseKeystoreService.getKeystore(defaultFile, "JKS", masterPassword));
    assertTrue(customFile.exists());
    assertNotNull(baseKeystoreService.getKeystore(customFile, "JKS", masterPassword));

    verifyAll();
  }

  @Test(expected = ServiceLifecycleException.class)
  public void testSigningKeystoreMissingFile() throws Exception {
    DefaultKeystoreService keystoreServiceAlt = createMockBuilder(DefaultKeystoreService.class)
        .addMockedMethod("getCredentialForCluster", String.class, String.class)
        .createMock();

    replayAll();

    File baseDir = testFolder.newFolder();
    GatewayConfigImpl config = createGatewayConfig(baseDir);

    String customFileName = "custom_signing.jks";
    String customAlias = "custom_alias";

    config.set(SIGNING_KEYSTORE_NAME, customFileName);
    config.set(SIGNING_KEY_ALIAS, customAlias);

    keystoreServiceAlt.init(config, Collections.emptyMap());

    verifyAll();
  }


  @Test
  public void testCredentialsForCluster() throws Exception {
    String clusterName = "my_cluster";
    String credentialAlias = "my_alias";
    String credentialValue = "my_value";
    char[] masterPassword = "master_password".toCharArray();

    MasterService masterService = createMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn(masterPassword).anyTimes();

    File baseFolder = testFolder.newFolder();
    GatewayConfig config = createGatewayConfig(baseFolder);
    File expectedFile = new File(config.getGatewayKeystoreDir(), clusterName + "-credentials.jceks");

    replayAll();

    DefaultKeystoreService keystoreService = new DefaultKeystoreService();
    keystoreService.setMasterService(masterService);
    keystoreService.init(config, Collections.emptyMap());


    assertFalse(expectedFile.exists());
    assertFalse(keystoreService.isCredentialStoreForClusterAvailable(clusterName));

    // This should be an empty keystore...
    KeyStore emptyKeystore = keystoreService.getCredentialStoreForCluster(clusterName);
    assertNotNull(emptyKeystore);
    assertEquals(0, emptyKeystore.size());

    keystoreService.createCredentialStoreForCluster(clusterName);

    assertTrue(expectedFile.exists());
    assertTrue(keystoreService.isCredentialStoreForClusterAvailable(clusterName));

    KeyStore credentialStore = keystoreService.getCredentialStoreForCluster(clusterName);
    assertNotNull(credentialStore);
    assertEquals(0, credentialStore.size());

    keystoreService.addCredentialForCluster(clusterName, credentialAlias, credentialValue);

    // The previous version of the credential store was not updated
    assertEquals(0, credentialStore.size());

    // Get the updated credential store
    credentialStore = keystoreService.getCredentialStoreForCluster(clusterName);
    assertNotNull(credentialStore);
    assertEquals(1, credentialStore.size());

    // Make sure the expected alias and value exists in the credential store
    Key key = credentialStore.getKey(credentialAlias, masterPassword);
    assertNotNull(key);
    assertEquals(credentialValue, new String(key.getEncoded(), StandardCharsets.UTF_8));

    // A request for a existing alias should return the expected value
    char[] keyValue = keystoreService.getCredentialForCluster(clusterName, credentialAlias);
    assertNotNull(keyValue);
    assertEquals(credentialValue, new String(keyValue));

    // A request for an alias that does not exists, should return NULL
    assertNull(keystoreService.getCredentialForCluster(clusterName, "not!my_alias"));

    // Remove that credential
    keystoreService.removeCredentialForCluster(clusterName, credentialAlias);

    // Get the updated credential store
    credentialStore = keystoreService.getCredentialStoreForCluster(clusterName);
    assertNotNull(credentialStore);
    assertEquals(0, credentialStore.size());

    // Make sure the expected alias does not exist in the credential store
    key = credentialStore.getKey(credentialAlias, masterPassword);
    assertNull(key);

    // A request for a existing alias should return null
    assertNull(keystoreService.getCredentialForCluster(clusterName, credentialAlias));

    verifyAll();
  }


  private void testCreateGetAndCheckKeystoreForGateway(KeystoreService keystoreService,
                                                       File expectedKeystoreFile,
                                                       String expectedAlias,
                                                       char[] keyPassword,
                                                       GatewayConfig config) throws Exception {
    assertEquals(expectedKeystoreFile.getAbsolutePath(), keystoreService.getKeystorePath());
    assertFalse(expectedKeystoreFile.exists());

    assertFalse(keystoreService.isKeystoreForGatewayAvailable());

    KeyStore preCreateKeystore = keystoreService.getKeystoreForGateway();
    assertNotNull(preCreateKeystore); // This is odd since the keystore has not yet been created...
    // The keystore file is not yet created
    assertFalse(expectedKeystoreFile.exists());

    keystoreService.createKeystoreForGateway();
    // The keystore file has now been created
    assertTrue(expectedKeystoreFile.exists());
    KeyStore postCreateKeystore = keystoreService.getKeystoreForGateway();
    assertNotNull(postCreateKeystore);
    assertEquals(0, postCreateKeystore.size());

    keystoreService.addSelfSignedCertForGateway(config.getIdentityKeyAlias(), keyPassword, "localhost");
    assertNotNull(keystoreService.getKeyForGateway(expectedAlias, keyPassword));

    assertEquals(0, postCreateKeystore.size());
    // reread the keystore
    postCreateKeystore = keystoreService.getKeystoreForGateway();
    assertEquals(1, postCreateKeystore.size());

    assertNotNull(postCreateKeystore.getKey(expectedAlias, keyPassword));
    Certificate certificate = postCreateKeystore.getCertificate(expectedAlias);
    assertNotNull(certificate);
  }

  private void testSigningKeystore(KeystoreService keystoreService,
                                   File expectedKeystoreFile,
                                   String keyAlias,
                                   char[] masterPassword) throws Exception {
    assertTrue(expectedKeystoreFile.exists());
    assertNotNull(keystoreService.getSigningKeystore());
    assertNotNull(keystoreService.getSigningKey(keyAlias, masterPassword));
  }

  private GatewayConfigImpl createGatewayConfig(File baseDir) {
    GatewayConfigImpl config = new GatewayConfigImpl();
    config.set("gateway.data.dir", baseDir.getAbsolutePath());
    config.set("gateway.security.dir", new File(baseDir, "security").getAbsolutePath());
    return config;

  }

  private void createKeystore(BaseKeystoreService baseKeystoreService, File keystoreFile, String alias, char[] masterPassword)
      throws KeystoreServiceException, KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
    KeyStore keystore = baseKeystoreService.getKeystore(keystoreFile, "JKS", masterPassword);

    KeyPairGenerator keyPairGenerator;
    keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();

    X509Certificate cert = X509CertificateUtil.generateCertificate(
        String.format(Locale.ROOT, "CN=%s,OU=Test,O=Hadoop,L=Test,ST=Test,C=US", this.getClass().getName()),
        keyPair,
        365,
        "SHA1withRSA");

    keystore.setKeyEntry(alias, keyPair.getPrivate(),
        masterPassword,
        new java.security.cert.Certificate[]{cert});

    baseKeystoreService.writeKeystoreToFile(keystore, masterPassword, keystoreFile);
  }
}

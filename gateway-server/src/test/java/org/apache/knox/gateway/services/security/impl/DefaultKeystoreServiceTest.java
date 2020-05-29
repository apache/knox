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
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createMockBuilder;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.util.X509CertificateUtil;
import org.easymock.IAnswer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DefaultKeystoreServiceTest {
  @Rule
  public final TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void testGetTruststoreForHttpClientDefaults() throws Exception {
    final Path dataDir = testFolder.newFolder().toPath();

    GatewayConfigImpl config = new GatewayConfigImpl();
    config.set("gateway.data.dir", dataDir.toString());

    DefaultKeystoreService keystoreService = new DefaultKeystoreService();
    keystoreService.init(config, Collections.emptyMap());

    assertNull(keystoreService.getTruststoreForHttpClient());
  }

  @Test
  public void testGetTruststoreForHttpClientCustomTrustStore() throws Exception {
    final Path dataDir = testFolder.newFolder().toPath();
    final Path truststoreFile = testFolder.newFile().toPath();
    final String truststoreType = "jks";
    final String truststorePasswordAlias = "password-alias";
    final char[] truststorePassword = "truststore_password".toCharArray();

    GatewayConfigImpl config = new GatewayConfigImpl();
    config.set("gateway.data.dir", dataDir.toString());
    config.set("gateway.httpclient.truststore.path", truststoreFile.toString());
    config.set("gateway.httpclient.truststore.type", truststoreType);
    config.set("gateway.httpclient.truststore.password.alias", truststorePasswordAlias);

    KeyStore keystore = createNiceMock(KeyStore.class);

    DefaultKeystoreService keystoreService = createMockBuilder(DefaultKeystoreService.class)
        .addMockedMethod("loadKeyStore", Path.class, String.class, char[].class)
        .addMockedMethod("getCredentialForCluster", String.class, String.class)
        .createMock();
    expect(keystoreService.loadKeyStore(eq(truststoreFile), eq(truststoreType), eq(truststorePassword)))
        .andReturn(keystore)
        .once();
    expect(keystoreService.getCredentialForCluster(eq(AliasService.NO_CLUSTER_NAME), eq(truststorePasswordAlias)))
        .andReturn(truststorePassword)
        .once();

    replay(keystore, keystoreService);

    keystoreService.init(config, Collections.emptyMap());

    assertEquals(keystore, keystoreService.getTruststoreForHttpClient());

    verify(keystore, keystoreService);
  }

  @Test(expected = KeystoreServiceException.class)
  public void testGetTruststoreForHttpClientMissingCustomTrustStore() throws Exception {
    final Path dataDir = testFolder.newFolder().toPath();
    final String truststoreType = "jks";
    final String truststorePasswordAlias = "password-alias";
    final char[] truststorePassword = "truststore_password".toCharArray();

    GatewayConfigImpl config = new GatewayConfigImpl();
    config.set("gateway.data.dir", dataDir.toString());
    config.set("gateway.httpclient.truststore.path", Paths.get(dataDir.toString(), "missing_file.jks").toString());
    config.set("gateway.httpclient.truststore.type", truststoreType);
    config.set("gateway.httpclient.truststore.password.alias", truststorePasswordAlias);

    DefaultKeystoreService keystoreService = createMockBuilder(DefaultKeystoreService.class)
        .addMockedMethod("getCredentialForCluster", String.class, String.class)
        .createMock();
    expect(keystoreService.getCredentialForCluster(eq(AliasService.NO_CLUSTER_NAME), eq(truststorePasswordAlias)))
        .andReturn(truststorePassword)
        .once();

    replay(keystoreService);

    keystoreService.init(config, Collections.emptyMap());

    keystoreService.getTruststoreForHttpClient();

    verify(keystoreService);
  }

  @Test
  public void testGetTruststoreForHttpClientCustomTrustStoreMissingPasswordAlias() throws Exception {
    final Path dataDir = testFolder.newFolder().toPath();
    final Path truststoreFile = testFolder.newFile().toPath();
    final String truststoreType = "jks";
    final char[] masterSecret = "master_secret".toCharArray();

    GatewayConfigImpl config = new GatewayConfigImpl();
    config.set("gateway.data.dir", dataDir.toString());
    config.set("gateway.httpclient.truststore.path", truststoreFile.toString());
    config.set("gateway.httpclient.truststore.type", truststoreType);

    KeyStore keystore = createNiceMock(KeyStore.class);

    DefaultKeystoreService keystoreService = createMockBuilder(DefaultKeystoreService.class)
        .addMockedMethod("loadKeyStore", Path.class, String.class, char[].class)
        .addMockedMethod("getCredentialForCluster", String.class, String.class)
        .withConstructor()
        .createMock();
    expect(keystoreService.loadKeyStore(eq(truststoreFile), eq(truststoreType), eq(masterSecret)))
        .andReturn(keystore)
        .once();
    expect(keystoreService.getCredentialForCluster(eq(AliasService.NO_CLUSTER_NAME), eq(GatewayConfig.DEFAULT_HTTP_CLIENT_TRUSTSTORE_PASSWORD_ALIAS)))
        .andReturn(null)
        .once();

    MasterService masterService = createMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn(masterSecret);

    replay(keystore, keystoreService, masterService);

    keystoreService.init(config, Collections.emptyMap());
    keystoreService.setMasterService(masterService);

    assertEquals(keystore, keystoreService.getTruststoreForHttpClient());

    verify(keystore, keystoreService, masterService);
  }

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
    expect(keystoreServiceAlt.getCredentialForCluster(eq(AliasService.NO_CLUSTER_NAME), eq("custom_key_passphrase_alias")))
        .andReturn(customKeyPassword)
        .anyTimes();
    expect(keystoreServiceAlt.getCredentialForCluster(eq(AliasService.NO_CLUSTER_NAME), eq("custom_keystore_password_alias")))
        .andReturn(customKeystorePassword)
        .anyTimes();

    replay(keystoreServiceAlt, masterService);

    Path baseDir = testFolder.newFolder().toPath();
    GatewayConfigImpl config = createGatewayConfig(baseDir);

    /* *******************
     * Test Defaults
     */
    Path defaultKeystoreFile = baseDir.resolve("security").resolve("keystores").resolve("gateway.jks");

    DefaultKeystoreService keystoreService = new DefaultKeystoreService();
    keystoreService.setMasterService(masterService);
    keystoreService.init(config, Collections.emptyMap());

    testCreateGetAndCheckKeystoreForGateway(keystoreService,
        defaultKeystoreFile,
        GatewayConfigImpl.DEFAULT_IDENTITY_KEY_ALIAS,
        keyPassword, config);


    /* *******************
     * Test Custom Values
     */
    Path customKeystoreFile = baseDir.resolve("test").resolve("keystore").resolve("custom_keystore.p12");
    String customKeystoreType = "pkcs12";
    String customAlias = "custom_alias";

    config.set(IDENTITY_KEYSTORE_PATH, customKeystoreFile.toAbsolutePath().toString());
    config.set(IDENTITY_KEYSTORE_TYPE, customKeystoreType);
    config.set(IDENTITY_KEYSTORE_PASSWORD_ALIAS, "custom_keystore_password_alias");
    config.set(IDENTITY_KEY_ALIAS, customAlias);
    config.set(IDENTITY_KEY_PASSPHRASE_ALIAS, "custom_key_passphrase_alias");

    keystoreServiceAlt.setMasterService(masterService);


    keystoreServiceAlt.init(config, Collections.emptyMap());
    keystoreServiceAlt.init(config, Collections.emptyMap());

    testCreateGetAndCheckKeystoreForGateway(keystoreServiceAlt, customKeystoreFile, customAlias, customKeyPassword, config);

    // Verify the keystore passwords are set properly...
    assertTrue(Files.exists(defaultKeystoreFile));
    assertNotNull(keystoreService.loadKeyStore(defaultKeystoreFile, GatewayConfigImpl.DEFAULT_IDENTITY_KEYSTORE_TYPE, masterPassword));
    assertTrue(Files.exists(customKeystoreFile));
    assertNotNull(keystoreService.loadKeyStore(customKeystoreFile, customKeystoreType, customKeystorePassword));

    verify(keystoreServiceAlt, masterService);
  }

  @Test
  public void testSigningKeystore() throws Exception {
    char[] masterPassword = "master_password".toCharArray();

    MasterService masterService = createMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn(masterPassword).anyTimes();

    DefaultKeystoreService keystoreServiceAlt = createMockBuilder(DefaultKeystoreService.class)
        .addMockedMethod("getCredentialForCluster", String.class, String.class)
        .createMock();
    expect(keystoreServiceAlt.getCredentialForCluster(eq(AliasService.NO_CLUSTER_NAME), eq(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS)))
        .andReturn(null)
        .atLeastOnce();

    replay(keystoreServiceAlt, masterService);

    Path baseDir = testFolder.newFolder().toPath();
    GatewayConfigImpl config = createGatewayConfig(baseDir);

    /* *******************
     * Test Defaults
     */
    Path defaultFile = baseDir.resolve("security").resolve("keystores").resolve("gateway.jks");
    String defaultAlias = "gateway-identity";

    DefaultKeystoreService keystoreService = new DefaultKeystoreService();
    keystoreService.setMasterService(masterService);

    try {
      keystoreService.init(config, Collections.emptyMap());
    } catch (ServiceLifecycleException e) {
      fail("Not expecting ServiceLifecycleException due to missing signing keystore file since a custom one is not specified");
    }

    createKeystore(keystoreService, defaultFile, defaultAlias, masterPassword);

    keystoreService.init(config, Collections.emptyMap());

    testSigningKeystore(keystoreService, defaultFile, defaultAlias, masterPassword);

    /* *******************
     * Test Custom Values
     */
    String customFileName = "custom_signing.jks";
    Path customFile = baseDir.resolve("security").resolve("keystores").resolve(customFileName);
    String customKeyAlias = "custom_alias";

    config.set(SIGNING_KEYSTORE_NAME, customFileName);
    config.set(SIGNING_KEY_ALIAS, customKeyAlias);

    keystoreServiceAlt.setMasterService(masterService);

    // Ensure the signing keystore exists before init-ing the keystore service
    createKeystore(keystoreService, customFile, customKeyAlias, masterPassword);

    keystoreServiceAlt.init(config, Collections.emptyMap());

    testSigningKeystore(keystoreServiceAlt, customFile, customKeyAlias, masterPassword);

    // Verify the keystore passwords are set properly...
    assertTrue(Files.exists(defaultFile));
    assertNotNull(keystoreService.loadKeyStore(defaultFile, "JKS", masterPassword));
    assertTrue(Files.exists(customFile));
    assertNotNull(keystoreService.loadKeyStore(customFile, "JKS", masterPassword));

    verify(keystoreServiceAlt, masterService);
  }

  @Test
  public void testCredentialsForCluster() throws Exception {
    String clusterName = "my_cluster";
    String credentialAlias = "my_alias";
    String credentialValue = "my_value";
    char[] masterPassword = "master_password".toCharArray();

    MasterService masterService = createMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn(masterPassword).anyTimes();

    Path baseFolder = testFolder.newFolder().toPath();
    GatewayConfig config = createGatewayConfig(baseFolder);
    Path expectedFile = Paths.get(config.getGatewayKeystoreDir(), clusterName + "-credentials.jceks");

    replay(masterService);

    DefaultKeystoreService keystoreService = new DefaultKeystoreService();
    keystoreService.setMasterService(masterService);
    keystoreService.init(config, Collections.emptyMap());

    assertFalse(Files.exists(expectedFile));
    assertFalse(keystoreService.isCredentialStoreForClusterAvailable(clusterName));

    // This should be an empty keystore...
    KeyStore emptyKeystore = keystoreService.getCredentialStoreForCluster(clusterName);
    assertNotNull(emptyKeystore);
    assertEquals(0, emptyKeystore.size());

    keystoreService.createCredentialStoreForCluster(clusterName);

    assertTrue(Files.exists(expectedFile));
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

    verify(masterService);
  }

  @Test
  public void testCredentialCache() throws Exception {
    final String clusterName = "my_cluster";
    final String credentialAlias = "my_alias";
    final String credentialValue = "my_value";

    final MasterService masterService = createMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn("master_password".toCharArray()).anyTimes();
    replay(masterService);

    final GatewayConfigImpl gatewayConfig = createGatewayConfig(testFolder.newFolder().toPath());

    final DefaultKeystoreService keystoreService = new DefaultKeystoreService();
    keystoreService.setMasterService(masterService);
    keystoreService.init(gatewayConfig, null);

    keystoreService.addCredentialForCluster(clusterName, credentialAlias, credentialValue);

    // changing the security folder so that we "invalidate" the previously created
    // keystore and rely on the cache when fetching a credential for an alias
    gatewayConfig.set(GatewayConfigImpl.SECURITY_DIR, gatewayConfig.getGatewaySecurityDir() + "_other");
    keystoreService.init(gatewayConfig, null);

    // A request for a existing alias should return the expected value from cache.
    // Since the service now has a new security folder set it should return 'null'
    // if the cache did not contain the previously added value
    final char[] keyValue = keystoreService.getCredentialForCluster(clusterName, credentialAlias);
    assertNotNull(keyValue);
    assertEquals(credentialValue, new String(keyValue));

    verify(masterService);
  }

  @Test
  public void testAddSelfSignedCertForGatewayLocalhost() throws Exception {
    testAddSelfSignedCertForGateway(null);
  }

  @Test
  public void testAddSelfSignedCertForGatewayExplicitHostname() throws Exception {
    testAddSelfSignedCertForGateway("knox.example.com");
  }

  @Test
  public void testAddSelfSignedCertForGatewayCalculateHostname() throws Exception {
    testAddSelfSignedCertForGateway("hostname");
  }

  @Test
  public void testGetKeyAndCertificateForGateway() throws Exception {
    char[] masterPassword = "master_password".toCharArray();

    MasterService masterService = createMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn(masterPassword).anyTimes();

    replay(masterService);

    Path baseDir = testFolder.newFolder().toPath();
    GatewayConfigImpl config = createGatewayConfig(baseDir);

    DefaultKeystoreService keystoreService = new DefaultKeystoreService();
    keystoreService.setMasterService(masterService);
    keystoreService.init(config, Collections.emptyMap());

    createKeystore(keystoreService, Paths.get(config.getIdentityKeystorePath()), config.getIdentityKeyAlias(), masterPassword);

    assertNull(keystoreService.getKeyForGateway("wrongpassword".toCharArray()));
    assertNotNull(keystoreService.getKeyForGateway(masterPassword));
    assertNotNull(keystoreService.getKeyForGateway(null)); // implicitly should use master secret

    assertNotNull(keystoreService.getCertificateForGateway());

    verify(masterService);
  }

  @Test
  public void testAddRemoveCredentialForCluster() throws Exception {
    char[] masterPassword = "master_password".toCharArray();

    MasterService masterService = createMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn(masterPassword).anyTimes();

    replay(masterService);

    Path baseDir = testFolder.newFolder().toPath();
    GatewayConfigImpl config = createGatewayConfig(baseDir);

    DefaultKeystoreService keystoreService = new DefaultKeystoreService();
    keystoreService.setMasterService(masterService);
    keystoreService.init(config, Collections.emptyMap());


    String notClusterName = "cluster_not";
    String notAlias= "alias_not";
    String clusterName = "cluster";
    String alias = "alias1";
    String value = "value1";

    keystoreService.createCredentialStoreForCluster(clusterName);

    assertNull(keystoreService.getCredentialForCluster(clusterName, alias));
    assertNull(keystoreService.getCredentialForCluster(notClusterName, alias));

    keystoreService.addCredentialForCluster(clusterName, alias, value);
    assertEquals(value, String.valueOf(keystoreService.getCredentialForCluster(clusterName, alias)));
    assertNull(keystoreService.getCredentialForCluster(notClusterName, alias));
    assertNull(keystoreService.getCredentialForCluster(clusterName, notAlias));
    assertNull(keystoreService.getCredentialForCluster(notClusterName, notAlias));

    keystoreService.removeCredentialForCluster(clusterName, alias);
    assertNull(keystoreService.getCredentialForCluster(clusterName, alias));

    verify(masterService);
  }

  @Test
  public void testAddRemoveCredentialForClusterSynchronization() throws Exception {
    char[] masterPassword = "master_password".toCharArray();

    MasterService masterService = createMock(MasterService.class);
    IAnswer<? extends char[]> iAnswer = (IAnswer<char[]>) () -> {
      // Wait .5-1 seconds to return
//      Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1000));
      return masterPassword;
    };
    expect(masterService.getMasterSecret()).andAnswer(iAnswer).anyTimes();
    replay(masterService);

    Path baseDir = testFolder.newFolder().toPath();
    GatewayConfigImpl config = createGatewayConfig(baseDir);

    DefaultKeystoreService keystoreService = new DefaultKeystoreService();
    keystoreService.setMasterService(masterService);
    keystoreService.init(config, Collections.emptyMap());

    String clusterName = "cluster";
    keystoreService.createCredentialStoreForCluster(clusterName);

    int numberTotalRequests = 10;

    Set<Callable<Void>> addCallables = new HashSet<>(numberTotalRequests);
    for (int i = 0; i < numberTotalRequests; i++) {
      String alias = "alias" + i;
      String value = "value" + i;
      addCallables.add(() -> {
        keystoreService.addCredentialForCluster(clusterName, alias, value);
        return null;
      });
    }

    ExecutorService insertExecutor = Executors.newFixedThreadPool(numberTotalRequests);
    insertExecutor.invokeAll(addCallables);
    insertExecutor.shutdown();
    insertExecutor.awaitTermination(5, TimeUnit.SECONDS);
    assertThat(insertExecutor.isTerminated(), is(true));

    // Ensure not to use cache
    keystoreService.cache.invalidateAll();

    for (int i = 0; i < numberTotalRequests; i++) {
      String alias = "alias" + i;
      String value = "value" + i;
      assertEquals(value, String.valueOf(keystoreService.getCredentialForCluster(clusterName, alias)));
    }

    Set<Callable<Void>> removeCallables = new HashSet<>(numberTotalRequests);
    for (int i = 0; i < numberTotalRequests; i++) {
      String alias = "alias" + i;
      removeCallables.add(() -> {
        keystoreService.removeCredentialForCluster(clusterName, alias);
        return null;
      });
    }

    ExecutorService removeExecutor = Executors.newFixedThreadPool(numberTotalRequests);
    removeExecutor.invokeAll(removeCallables);
    removeExecutor.shutdown();
    removeExecutor.awaitTermination(5, TimeUnit.SECONDS);
    assertThat(removeExecutor.isTerminated(), is(true));

    // Ensure not to use cache
    keystoreService.cache.invalidateAll();

    for (int i = 0; i < numberTotalRequests; i++) {
      String alias = "alias" + i;
      assertNull(keystoreService.getCredentialForCluster(clusterName, alias));
    }

    verify(masterService);
  }

  /**
   * Test the bulk key removal method, which should only load the keystore file once, and subsequently write the
   * keystore file only once, rather than once each per key.
   */
  @Test
  public void testRemoveCredentialsForCluster() throws Exception {
    char[] masterPassword = "master_password".toCharArray();

    MasterService masterService = createMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn(masterPassword).anyTimes();

    replay(masterService);

    Path baseDir = testFolder.newFolder().toPath();
    GatewayConfigImpl config = createGatewayConfig(baseDir);

    CountingDefaultKeystoreService keystoreService = new CountingDefaultKeystoreService();
    keystoreService.setMasterService(masterService);
    keystoreService.init(config, Collections.emptyMap());

    String clusterName = "cluster";

    Map<String, String> testAliases = new HashMap<>();
    testAliases.put("alias1", "value1");
    testAliases.put("alias2", "value2");
    testAliases.put("alias3", "value3");

    Set<String> aliases = testAliases.keySet();

    keystoreService.createCredentialStoreForCluster(clusterName);

    for (String alias : aliases) {
      keystoreService.addCredentialForCluster(clusterName, alias, testAliases.get(alias));
      assertEquals(testAliases.get(alias), String.valueOf(keystoreService.getCredentialForCluster(clusterName, alias)));
    }

    // Clear the counts recorded from adding the credentials
    keystoreService.clearCounts();

    // Invoke the bulk removal method
    keystoreService.removeCredentialsForCluster(clusterName, aliases);

    // Validate the number of loads/writes of the keystore file
    assertEquals("Expected only a single load of the keystore file.", 1, keystoreService.loadCount);
    assertEquals("Expected only a single write to the keystore file.", 1, keystoreService.storeCount);

    for (String alias : aliases) {
      assertNull(keystoreService.getCredentialForCluster(clusterName, alias));
    }

    verify(masterService);
  }

  private void testAddSelfSignedCertForGateway(String hostname) throws Exception {
    char[] masterPassword = "master_password".toCharArray();

    MasterService masterService = createMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn(masterPassword).anyTimes();

    replay(masterService);

    Path baseFolder = testFolder.newFolder().toPath();
    GatewayConfig config = createGatewayConfig(baseFolder);

    Path defaultFile = baseFolder.resolve("security").resolve("keystores").resolve("gateway.jks");

    DefaultKeystoreService keystoreService = new DefaultKeystoreService();
    keystoreService.init(config, Collections.emptyMap());
    keystoreService.setMasterService(masterService);

    keystoreService.createKeyStore(defaultFile, "JKS", masterPassword);

    String alias;
    char[] password;
    String expectedSubjectName;
    if (hostname == null) {
      alias = "test_localhost";
      password = "test_localhost".toCharArray();
      expectedSubjectName = "CN=localhost, OU=Test, O=Hadoop, L=Test, ST=Test, C=US";

      keystoreService.addSelfSignedCertForGateway(alias, password);
    } else {
      alias = "test_" + hostname;
      password = ("test_" + hostname).toCharArray();

      if ("hostname".equals(hostname)) {
        expectedSubjectName = "CN=" + InetAddress.getLocalHost().getHostName() + ", OU=Test, O=Hadoop, L=Test, ST=Test, C=US";
      } else {
        expectedSubjectName = "CN=" + hostname + ", OU=Test, O=Hadoop, L=Test, ST=Test, C=US";
      }

      keystoreService.addSelfSignedCertForGateway(alias, password, hostname);
    }

    assertNotNull(keystoreService.getKeyForGateway(alias, password));

    KeyStore keystore = keystoreService.getKeystoreForGateway();
    assertNotNull(keystore);
    assertNotNull(keystore.getKey(alias, password));

    Certificate certificate = keystore.getCertificate(alias);
    assertTrue(certificate instanceof X509Certificate);

    Principal subject = ((X509Certificate) certificate).getSubjectDN();
    assertEquals(expectedSubjectName, subject.getName());

    verify(masterService);
  }

  private void testCreateGetAndCheckKeystoreForGateway(KeystoreService keystoreService,
                                                       Path expectedKeystoreFilePath,
                                                       String expectedAlias,
                                                       char[] keyPassword,
                                                       GatewayConfig config) throws Exception {
    assertEquals(expectedKeystoreFilePath.toAbsolutePath().toString(), keystoreService.getKeystorePath());
    assertFalse(Files.exists(expectedKeystoreFilePath));

    assertFalse(keystoreService.isKeystoreForGatewayAvailable());

    keystoreService.createKeystoreForGateway();
    // The keystore file has now been created
    assertTrue(Files.exists(expectedKeystoreFilePath));
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
                                   Path expectedKeystoreFilePath,
                                   String keyAlias,
                                   char[] masterPassword) throws Exception {
    assertTrue(Files.exists(expectedKeystoreFilePath));
    assertNotNull(keystoreService.getSigningKeystore());
    assertNotNull(keystoreService.getSigningKey(keyAlias, masterPassword));
  }

  private GatewayConfigImpl createGatewayConfig(Path baseDir) {
    GatewayConfigImpl config = new GatewayConfigImpl();
    config.set("gateway.data.dir", baseDir.toAbsolutePath().toString());
    config.set("gateway.security.dir", baseDir.resolve("security").toAbsolutePath().toString());
    return config;

  }

  private void createKeystore(DefaultKeystoreService keystoreService, Path keystoreFilePath, String alias, char[] password)
      throws KeystoreServiceException, KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
    KeyStore keystore = keystoreService.createKeyStore(keystoreFilePath, "JKS", password);

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
        password,
        new java.security.cert.Certificate[]{cert});

    keystoreService.writeKeyStoreToFile(keystore, keystoreFilePath, password);
  }


  private static class CountingDefaultKeystoreService extends DefaultKeystoreService {

    int loadCount;
    int storeCount;

    void clearCounts() {
      loadCount  = 0;
      storeCount = 0;
    }

    @Override
    synchronized KeyStore loadKeyStore(Path keyStoreFilePath, String storeType, char[] password) throws KeystoreServiceException {
      loadCount++;
      return super.loadKeyStore(keyStoreFilePath, storeType, password);
    }

    @Override
    synchronized void writeKeyStoreToFile(KeyStore keyStore, Path path, char[] password) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
      storeCount++;
      super.writeKeyStoreToFile(keyStore, path, password);
    }
  }
}

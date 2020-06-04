/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.services.security.impl;

import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.service.config.remote.zk.ZooKeeperClientService;
import org.apache.knox.gateway.service.config.remote.zk.ZooKeeperClientServiceProvider;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.MasterService;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.easymock.EasyMock.capture;

/**
 * Test for {@link ZookeeperRemoteAliasService} backed by Zookeeper.
 */
public class ZookeeperRemoteAliasServiceTest {

  @ClassRule
  public static final TemporaryFolder testFolder = new TemporaryFolder();

  private static TestingCluster zkNodes;
  private static GatewayConfig gc;

  @BeforeClass
  public static void setupSuite() throws Exception {
    String configMonitorName = "remoteConfigMonitorClient";

    configureAndStartZKCluster();

    // Setup the base GatewayConfig mock
    gc = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gc.getRemoteRegistryConfigurationNames())
        .andReturn(Collections.singletonList(configMonitorName)).anyTimes();

    final String registryConfig =
        GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE + "="
            + ZooKeeperClientService.TYPE + ";"
            + GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS + "=" + zkNodes
            .getConnectString();

    EasyMock.expect(gc.getRemoteRegistryConfiguration(configMonitorName))
        .andReturn(registryConfig).anyTimes();

    EasyMock.expect(gc.getRemoteConfigurationMonitorClientName())
        .andReturn(configMonitorName).anyTimes();

    EasyMock.expect(gc.getAlgorithm()).andReturn("AES").anyTimes();

    EasyMock.expect(gc.isRemoteAliasServiceEnabled())
        .andReturn(true).anyTimes();

    final Path baseFolder = Paths.get(testFolder.newFolder().getAbsolutePath());
    EasyMock.expect(gc.getGatewayDataDir()).andReturn(Paths.get(baseFolder.toString(), "data").toString()).anyTimes();
    EasyMock.expect(gc.getGatewayKeystoreDir()).andReturn(Paths.get(baseFolder.toString(), "data", "keystores").toString()).anyTimes();

    EasyMock.replay(gc);
  }

  private static void configureAndStartZKCluster() throws Exception {
    // Configure security for the ZK cluster instances
    Map<String, Object> customInstanceSpecProps = new HashMap<>();
    customInstanceSpecProps.put("authProvider.1",
        "org.apache.zookeeper.server.auth.SASLAuthenticationProvider");
    customInstanceSpecProps.put("requireClientAuthScheme", "sasl");
    customInstanceSpecProps.put("admin.enableServer", false);

    // Define the test cluster
    List<InstanceSpec> instanceSpecs = new ArrayList<>();
    for (int i = 0; i < 1; i++) {
      InstanceSpec is = new InstanceSpec(null, -1, -1, -1, false, (i + 1), -1,
          -1, customInstanceSpecProps);
      instanceSpecs.add(is);
    }
    zkNodes = new TestingCluster(instanceSpecs);

    // Start the cluster
    zkNodes.start();
  }

  @AfterClass
  public static void tearDownSuite() throws Exception {
    // Shutdown the ZK cluster
    zkNodes.close();
  }

  @Test
  public void testAliasForCluster() throws Exception {
    final String expectedClusterName = "sandbox";
    final String expectedAlias = "knox.test.Alias";
    final String expectedPassword = "dummyPassword";

    final String expectedClusterNameDev = "development";
    final String expectedAliasDev = "knox.test.alias.dev";
    final String expectedPasswordDev = "otherDummyPassword";

    // Mock Alias Service
    final DefaultAliasService defaultAlias = EasyMock
        .createNiceMock(DefaultAliasService.class);
    // Captures for validating the alias creation for a generated topology
    final Capture<String> capturedCluster = EasyMock.newCapture();
    final Capture<String> capturedAlias = EasyMock.newCapture();
    final Capture<String> capturedPwd = EasyMock.newCapture();

    defaultAlias
        .addAliasForCluster(capture(capturedCluster), capture(capturedAlias),
            capture(capturedPwd));
    EasyMock.expectLastCall().anyTimes();

    /* defaultAlias.getAliasesForCluster() never returns null */
    EasyMock.expect(defaultAlias.getAliasesForCluster(expectedClusterName))
        .andReturn(new ArrayList<>()).anyTimes();
    EasyMock.expect(defaultAlias.getAliasesForCluster(expectedClusterNameDev))
        .andReturn(new ArrayList<>()).anyTimes();

    EasyMock.replay(defaultAlias);

    final DefaultMasterService ms = EasyMock
        .createNiceMock(DefaultMasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("knox".toCharArray())
        .anyTimes();
    EasyMock.replay(ms);

    RemoteConfigurationRegistryClientService clientService = (new ZooKeeperClientServiceProvider())
        .newInstance();
    clientService.setAliasService(defaultAlias);
    clientService.init(gc, Collections.emptyMap());

    final ZookeeperRemoteAliasService zkAlias = new ZookeeperRemoteAliasService(defaultAlias, ms,
        clientService);
    zkAlias.init(gc, Collections.emptyMap());
    zkAlias.start();

    /* Put */
    zkAlias.addAliasForCluster(expectedClusterName, expectedAlias,
        expectedPassword);
    zkAlias.addAliasForCluster(expectedClusterNameDev, expectedAliasDev,
        expectedPasswordDev);

    /* GET all Aliases */
    List<String> aliases = zkAlias.getAliasesForCluster(expectedClusterName);
    List<String> aliasesDev = zkAlias
        .getAliasesForCluster(expectedClusterNameDev);

    Assert.assertEquals(aliases.size(), 1);
    Assert.assertEquals(aliasesDev.size(), 1);

    Assert.assertTrue("Expected alias '" + expectedAlias + "' not found ",
        aliases.contains(expectedAlias.toLowerCase(Locale.ROOT)));
    Assert.assertTrue("Expected alias '" + expectedAliasDev + "' not found ",
        aliasesDev.contains(expectedAliasDev));

    final char[] result = zkAlias
        .getPasswordFromAliasForCluster(expectedClusterName, expectedAlias);
    Assert.assertNotNull("Failed lookup for " + expectedAlias, result);

    final char[] result1 = zkAlias
        .getPasswordFromAliasForCluster(expectedClusterNameDev,
            expectedAliasDev);
    Assert.assertNotNull("Failed lookup for " + expectedAliasDev, result1);

    Assert.assertEquals(expectedPassword, new String(result));
    Assert.assertEquals(expectedPasswordDev, new String(result1));

    /* Remove */
    zkAlias.removeAliasForCluster(expectedClusterNameDev, expectedAliasDev);

    /* Make sure expectedAliasDev is removed*/
    aliases = zkAlias.getAliasesForCluster(expectedClusterName);
    aliasesDev = zkAlias.getAliasesForCluster(expectedClusterNameDev);

    Assert.assertEquals(aliasesDev.size(), 0);
    Assert.assertFalse("Expected alias '" + expectedAliasDev + "' to be removed but found.",
        aliasesDev.contains(expectedAliasDev));

    Assert.assertEquals(aliases.size(), 1);
    Assert.assertTrue("Expected alias '" + expectedAlias + "' not found ",
        aliases.contains(expectedAlias.toLowerCase(Locale.ROOT)));

    /* Test auto-generate password for alias */
    final String testAutoGeneratedpasswordAlias = "knox.test.alias.auto";

    final char[] autoGeneratedPassword = zkAlias
        .getPasswordFromAliasForCluster(expectedClusterName,
            testAutoGeneratedpasswordAlias, true);
    aliases = zkAlias.getAliasesForCluster(expectedClusterName);

    Assert.assertNotNull(autoGeneratedPassword);
    Assert.assertEquals(aliases.size(), 2);
    Assert.assertTrue("Expected alias 'knox.test.alias' not found ",
        aliases.contains(testAutoGeneratedpasswordAlias));
  }

  @Test
  public void testEncryptDecrypt() throws Exception {
    final String testPassword = "ApacheKnoxPassword123";

    final AliasService defaultAlias = EasyMock
        .createNiceMock(AliasService.class);
    EasyMock.replay(defaultAlias);

    final DefaultMasterService ms = EasyMock
        .createNiceMock(DefaultMasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("knox".toCharArray())
        .anyTimes();
    EasyMock.replay(ms);

    RemoteConfigurationRegistryClientService clientService = (new ZooKeeperClientServiceProvider())
                                                                 .newInstance();
    clientService.setAliasService(defaultAlias);
    clientService.init(gc, Collections.emptyMap());

    final ZookeeperRemoteAliasService zkAlias = new ZookeeperRemoteAliasService(defaultAlias, ms,
        clientService);
    zkAlias.init(gc, Collections.emptyMap());

    final String encrypted = zkAlias.encrypt(testPassword);
    Assert.assertNotNull(encrypted);
    final String clear = zkAlias.decrypt(encrypted);
    Assert.assertEquals(testPassword, clear);

    try {
      // Match default data that is put into a newly created znode
      final byte[] badData = new byte[0];
      zkAlias.decrypt(new String(badData, StandardCharsets.UTF_8));
      Assert.fail("Should have failed to decrypt.");
    } catch (IllegalArgumentException e) {
      Assert.assertEquals("Data should have 3 parts split by ::", e.getMessage());
    }
  }

  @Test
  public void testRemoveAliasesForCluster() throws Exception {
    final String expectedClusterName = "sandbox";
    final String expectedAlias = "knox.test.alias";
    final String expectedPassword = "dummyPassword";

    final int aliasCount = 5;
    final Set<String> expectedAliases = new HashSet<>();
    for (int i = 0; i < aliasCount ; i++) {
      expectedAliases.add(expectedAlias + i);
    }

    final String expectedClusterNameDev = "development";
    final String expectedAliasDev = "knox.test.alias.dev";
    final String expectedPasswordDev = "otherDummyPassword";

    final int devAliasCount = 3;
    final Set<String> expectedDevAliases = new HashSet<>();
    for (int i = 0; i < 3 ; i++) {
      expectedDevAliases.add(expectedAliasDev + i);
    }

    // Mock Alias Service
    final DefaultAliasService defaultAlias = EasyMock.createNiceMock(DefaultAliasService.class);
    // Captures for validating the alias creation for a generated topology
    final Capture<String> capturedCluster = EasyMock.newCapture();
    final Capture<String> capturedAlias = EasyMock.newCapture();
    final Capture<String> capturedPwd = EasyMock.newCapture();

    defaultAlias.addAliasForCluster(capture(capturedCluster), capture(capturedAlias), capture(capturedPwd));
    EasyMock.expectLastCall().anyTimes();

    // defaultAlias.getAliasesForCluster() never returns null
    EasyMock.expect(defaultAlias.getAliasesForCluster(expectedClusterName))
            .andReturn(new ArrayList<>()).anyTimes();
    EasyMock.expect(defaultAlias.getAliasesForCluster(expectedClusterNameDev))
            .andReturn(new ArrayList<>()).anyTimes();

    EasyMock.replay(defaultAlias);

    final DefaultMasterService ms = EasyMock.createNiceMock(DefaultMasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("knox".toCharArray()).anyTimes();
    EasyMock.replay(ms);

    RemoteConfigurationRegistryClientService clientService = (new ZooKeeperClientServiceProvider()).newInstance();
    clientService.setAliasService(defaultAlias);
    clientService.init(gc, Collections.emptyMap());

    final ZookeeperRemoteAliasService zkAlias = new ZookeeperRemoteAliasService(defaultAlias, ms, clientService);
    zkAlias.init(gc, Collections.emptyMap());
    zkAlias.start();

    int originalSize = zkAlias.getAliasesForCluster(expectedClusterName).size();

    // Put
    for (String alias : expectedAliases) {
      zkAlias.addAliasForCluster(expectedClusterName, alias, expectedPassword);
    }
    for (String alias : expectedDevAliases) {
      zkAlias.addAliasForCluster(expectedClusterNameDev, alias, expectedPasswordDev);
    }

    Assert.assertEquals(originalSize + aliasCount, zkAlias.getAliasesForCluster(expectedClusterName).size());
    Assert.assertEquals(devAliasCount, zkAlias.getAliasesForCluster(expectedClusterNameDev).size());

    // Invoke the bulk removal method for the dev cluster
    zkAlias.removeAliasesForCluster(expectedClusterNameDev, expectedDevAliases);
    List<String> aliasesDev = zkAlias.getAliasesForCluster(expectedClusterNameDev);
    Assert.assertEquals("Expected 'knox.test.alias.dev' aliases to have been removed.", 0, aliasesDev.size());

    // Invoke the bulk removal method for the sandbox cluster
    zkAlias.removeAliasesForCluster(expectedClusterName, expectedAliases);
    List<String> aliases = zkAlias.getAliasesForCluster(expectedClusterName);
    Assert.assertEquals("Expected 'knox.test.alias' aliases to have been removed.", originalSize, aliases.size());
  }

  @Test
  @Ignore("should be executed manually in case you'd like to measure how much time alias addition/fetch takes")
  public void testPerformance() throws Exception {
      final MasterService masterService = EasyMock.createNiceMock(MasterService.class);
      EasyMock.expect(masterService.getMasterSecret()).andReturn("ThisIsMyM4sterP4sW0r!d".toCharArray()).anyTimes();
      EasyMock.replay(masterService);

      final DefaultKeystoreService keystoreService = new DefaultKeystoreService();
      keystoreService.init(gc, null);
      keystoreService.setMasterService(masterService);

      final int rounds = 11;
      final int numOfAliases = 200;
      final String cluster = "myTestCluster";
      for (int round = 0; round < rounds; round++) {
          //re-creating the alias service every time so that its cache is empty too
          final DefaultAliasService aliasService = new DefaultAliasService();
          aliasService.init(gc, null);
          aliasService.setMasterService(masterService);
          aliasService.setKeystoreService(keystoreService);

          RemoteConfigurationRegistryClientService clientService = (new ZooKeeperClientServiceProvider()).newInstance();
          clientService.setAliasService(aliasService);
          clientService.init(gc, Collections.emptyMap());

          final ZookeeperRemoteAliasService zkAlias = new ZookeeperRemoteAliasService(aliasService, masterService, clientService);
          zkAlias.init(gc, Collections.emptyMap());
          zkAlias.start();
          final long start = System.currentTimeMillis();
          for (int i = 0; i < numOfAliases; i++) {
              zkAlias.addAliasForCluster(cluster, "alias" + i, "password" + i);
              Assert.assertEquals("password" + i, new String(zkAlias.getPasswordFromAliasForCluster(cluster, "alias" + i)));
          }
          System.out.println(System.currentTimeMillis() - start);
      }
  }
}

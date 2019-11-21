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
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.easymock.EasyMock.capture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test for {@link ZookeeperRemoteAliasService} backed by Zookeeper.
 */
public class ZookeeperRemoteAliasServiceTest {
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
      InstanceSpec is = new InstanceSpec(null, -1, -1, -1, false, (i + 1), -1, -1, customInstanceSpecProps);
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
  public void testAliasCaching() throws Exception {
    final String expectedClusterName = "sandbox";
    final String expectedAlias = "knox.test.caching.alias";
    final String expectedPassword = "dummyPassword";

    // Mock Alias Service
    final DefaultAliasService defaultAlias = EasyMock.createNiceMock(DefaultAliasService.class);

    // Captures for validating the alias creation for a generated topology
    final Capture<String> capturedCluster = EasyMock.newCapture();
    final Capture<String> capturedAlias = EasyMock.newCapture();
    final Capture<String> capturedPwd = EasyMock.newCapture();

    defaultAlias.addAliasForCluster(capture(capturedCluster),
                                    capture(capturedAlias),
                                    capture(capturedPwd));
    EasyMock.expectLastCall().anyTimes();

    // defaultAlias.getAliasesForCluster() never returns null
    EasyMock.expect(defaultAlias.getAliasesForCluster(expectedClusterName))
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

    RemoteConfigurationRegistryClient client = clientService.get(gc.getRemoteConfigurationMonitorClientName());
    assertNotNull(client);

    // Validate no znodes
    validateClusterZNodes(client, Collections.singletonList("__gateway"));
    Map<String, Map<String, String>> expectedAliasEntries = new HashMap<>();
    expectedAliasEntries.put("__gateway", Collections.emptyMap());
    validateClusterAliasZNodes(client, expectedAliasEntries);

    // Validate no cached aliases
    Field cacheField = zkAlias.getClass().getDeclaredField("aliasCache");
    cacheField.setAccessible(true);
    Map<String, Map<String, String>> aliasCache = (Map<String, Map<String, String>>) cacheField.get(zkAlias);
    validateAliasCache(aliasCache, Collections.emptyMap());

    // Add alias, validate cache and znodes
    zkAlias.addAliasForCluster(expectedClusterName, expectedAlias, expectedPassword);

    // Wait for the callback from ZK to update the cache
    waitForCallback();

    expectedAliasEntries.put(expectedClusterName, new HashMap<>());
    expectedAliasEntries.get(expectedClusterName).put(expectedAlias, expectedPassword);
    validateClusterAliasZNodes(client, expectedAliasEntries);

    Map<String, Map<String, String>> expectedCacheEntries = new HashMap<>();
    expectedCacheEntries.put(expectedClusterName, expectedAliasEntries.get(expectedClusterName));
    validateAliasCache(aliasCache, expectedCacheEntries);

    char[] password = zkAlias.getPasswordFromAliasForCluster(expectedClusterName, expectedAlias);
    assertNotNull("Missing password for " + expectedAlias, password);
    assertEquals("Password mismatch", expectedPassword, new String(password));

    // Remove alias, validate cache and znodes
    zkAlias.removeAliasForCluster(expectedClusterName, expectedAlias);

    // Wait for the callback from ZK to update the cache
    waitForCallback();

    // Validate the ZNode is removed
    expectedAliasEntries.get(expectedClusterName).clear();
    validateClusterAliasZNodes(client, expectedAliasEntries);

    // Validate the cache entry is removed
    expectedCacheEntries.get(expectedClusterName).clear();
    validateAliasCache(aliasCache, expectedCacheEntries);

    // Validate the alias service result
    assertNull(zkAlias.getPasswordFromAliasForCluster(expectedClusterName, expectedAlias));

    // Update the alias
    final String expectedUpdatedPassword = "updated_value";
    zkAlias.addAliasForCluster(expectedClusterName, expectedAlias, expectedUpdatedPassword);

    // Wait for the callback from ZK to update the cache
    waitForCallback();

    // Validate the updated ZNode entry
    expectedAliasEntries.get(expectedClusterName).put(expectedAlias, expectedUpdatedPassword);
    validateClusterAliasZNodeValues(client, zkAlias, expectedAliasEntries);

    // Validate the updated cache entry
    assertEquals("Cached password mismatch.", aliasCache.get(expectedClusterName).get(expectedAlias), expectedUpdatedPassword);

    // Validate the alias service result
    char[] updatedPassword = zkAlias.getPasswordFromAliasForCluster(expectedClusterName, expectedAlias);
    assertNotNull("Missing password for " + expectedAlias, updatedPassword);
    assertEquals("Password mismatch", expectedUpdatedPassword, new String(updatedPassword));

    // Remove the alias for cleanliness sake
    zkAlias.removeAliasForCluster(expectedClusterName, expectedAlias);
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
    final DefaultAliasService defaultAlias = EasyMock.createNiceMock(DefaultAliasService.class);
    // Captures for validating the alias creation for a generated topology
    final Capture<String> capturedCluster = EasyMock.newCapture();
    final Capture<String> capturedAlias = EasyMock.newCapture();
    final Capture<String> capturedPwd = EasyMock.newCapture();

    defaultAlias.addAliasForCluster(capture(capturedCluster),
                                    capture(capturedAlias),
                                    capture(capturedPwd));
    EasyMock.expectLastCall().anyTimes();

    /* defaultAlias.getAliasesForCluster() never returns null */
    EasyMock.expect(defaultAlias.getAliasesForCluster(expectedClusterName))
            .andReturn(new ArrayList<>()).anyTimes();
    EasyMock.expect(defaultAlias.getAliasesForCluster(expectedClusterNameDev))
        .andReturn(new ArrayList<>()).anyTimes();

    EasyMock.replay(defaultAlias);

    final DefaultMasterService ms = EasyMock.createNiceMock(DefaultMasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("knox".toCharArray()).anyTimes();
    EasyMock.replay(ms);

    RemoteConfigurationRegistryClientService clientService =
        (new ZooKeeperClientServiceProvider()).newInstance();
    clientService.setAliasService(defaultAlias);
    clientService.init(gc, Collections.emptyMap());

    final ZookeeperRemoteAliasService zkAlias =
        new ZookeeperRemoteAliasService(defaultAlias, ms, clientService);
    zkAlias.init(gc, Collections.emptyMap());
    zkAlias.start();

    /* Put */
    zkAlias.addAliasForCluster(expectedClusterName, expectedAlias, expectedPassword);
    zkAlias.addAliasForCluster(expectedClusterNameDev, expectedAliasDev, expectedPasswordDev);

    /* GET all Aliases */
    List<String> aliases = zkAlias.getAliasesForCluster(expectedClusterName);
    List<String> aliasesDev = zkAlias.getAliasesForCluster(expectedClusterNameDev);

    Assert.assertEquals(aliases.size(), 1);
    Assert.assertEquals(aliasesDev.size(), 1);

    assertTrue("Expected alias '" + expectedAlias + "' not found ",
               aliases.contains(expectedAlias.toLowerCase(Locale.ROOT)));
    assertTrue("Expected alias '" + expectedAliasDev + "' not found ",
               aliasesDev.contains(expectedAliasDev));

    final char[] result = zkAlias.getPasswordFromAliasForCluster(expectedClusterName, expectedAlias);
    assertNotNull("Failed lookup for " + expectedAlias, result);

    final char[] result1 = zkAlias.getPasswordFromAliasForCluster(expectedClusterNameDev,
                                                                  expectedAliasDev);
    assertNotNull("Failed lookup for " + expectedAliasDev, result1);

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
    assertTrue("Expected alias '" + expectedAlias + "' not found ",
                      aliases.contains(expectedAlias.toLowerCase(Locale.ROOT)));

    /* Test auto-generate password for alias */
    final String testAutoGeneratedpasswordAlias = "knox.test.alias.auto";

    final char[] autoGeneratedPassword =
        zkAlias.getPasswordFromAliasForCluster(expectedClusterName,
                                               testAutoGeneratedpasswordAlias,
                                               true);
    aliases = zkAlias.getAliasesForCluster(expectedClusterName);

    assertNotNull(autoGeneratedPassword);
    Assert.assertEquals(2, aliases.size());
    assertTrue("Expected alias '" + testAutoGeneratedpasswordAlias + "' not found ",
                      aliases.contains(testAutoGeneratedpasswordAlias));
  }

  @Test
  public void testEncryptDecrypt() throws Exception {
    final String testPassword = "ApacheKnoxPassword123";

    final AliasService defaultAlias = EasyMock.createNiceMock(AliasService.class);
    EasyMock.replay(defaultAlias);

    final DefaultMasterService ms = EasyMock.createNiceMock(DefaultMasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("knox".toCharArray()).anyTimes();
    EasyMock.replay(ms);

    RemoteConfigurationRegistryClientService clientService = (new ZooKeeperClientServiceProvider()).newInstance();
    clientService.setAliasService(defaultAlias);
    clientService.init(gc, Collections.emptyMap());

    final ZookeeperRemoteAliasService zkAlias = new ZookeeperRemoteAliasService(defaultAlias, ms, clientService);
    zkAlias.init(gc, Collections.emptyMap());

    final String encrypted = zkAlias.encrypt(testPassword);
    assertNotNull(encrypted);
    final String clear = zkAlias.decrypt(encrypted);
    Assert.assertEquals(testPassword, clear);

    try {
      // Match default data that is put into a newly created znode
      final byte[] badData = new byte[0];
      zkAlias.decrypt(new String(badData, StandardCharsets.UTF_8));
      fail("Should have failed to decrypt.");
    } catch (IllegalArgumentException e) {
      Assert.assertEquals("Data should have 3 parts split by ::", e.getMessage());
    }
  }


  private void validateAliasCache(final Map<String, Map<String, String>> aliasCache,
                                  final Map<String, Map<String, String>> expectedCacheContents) {
    for (String cluster : expectedCacheContents.keySet()) {
      assertTrue("Missing cluster " + cluster, aliasCache.containsKey(cluster));
      for (String expectedAlias : expectedCacheContents.get(cluster).keySet()) {
        assertTrue("Missing expected alias (" + expectedAlias + ") for " + cluster,
            aliasCache.get(cluster).containsKey(expectedAlias));
        assertEquals("Alias value mismatch",
            expectedCacheContents.get(cluster).get(expectedAlias),
            aliasCache.get(cluster).get(expectedAlias));
      }
    }
  }


  private void validateClusterZNodes(final RemoteConfigurationRegistryClient client, final List<String> expectedAliasNames) {
    List<String> aliasNodes = client.listChildEntries(ZookeeperRemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY);
    assertEquals("Unexpected alias nodes.", expectedAliasNames.size(), aliasNodes.size());
    assertTrue("Alias node mismatch!", aliasNodes.containsAll(expectedAliasNames));
  }


  private void validateClusterAliasZNodes(final RemoteConfigurationRegistryClient client,
                                          final Map<String, Map<String, String>>  expectedAliasNames) {
    for (String cluster : expectedAliasNames.keySet()) {
      List<String> aliasNodes =
          client.listChildEntries(ZookeeperRemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY + "/" + cluster);
      assertEquals("Unexpected alias nodes.", expectedAliasNames.get(cluster).size(), aliasNodes.size());
      assertTrue("Alias node mismatch!", aliasNodes.containsAll(expectedAliasNames.get(cluster).keySet()));
    }
  }


  private void validateClusterAliasZNodeValues(final RemoteConfigurationRegistryClient client,
                                               final ZookeeperRemoteAliasService       zkAliasService,
                                               final Map<String, Map<String, String>>  expectedAliases) {
    for (String cluster : expectedAliases.keySet()) {
      List<String> aliasNodes =
          client.listChildEntries(ZookeeperRemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY + "/" + cluster);
      assertEquals("Unexpected alias nodes.", expectedAliases.get(cluster).size(), aliasNodes.size());
      for (String alias : aliasNodes) {
        String entryData =
            client.getEntryData(ZookeeperRemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY +
                "/" + cluster + "/" + alias);
        String decrypted = null;
        try {
          decrypted = zkAliasService.decrypt(entryData);
        } catch (Exception e) {
          fail("Error decrypting alias value: " + e.getMessage());
        }
        assertNotNull(decrypted);
        assertEquals("ZNode value mismatch.", expectedAliases.get(cluster).get(alias), decrypted);
      }
    }
  }

  private void waitForCallback() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      //
    }
  }

}

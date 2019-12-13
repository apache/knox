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

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.service.config.remote.zk.ZooKeeperClientService;
import org.apache.knox.gateway.service.config.remote.zk.ZooKeeperClientServiceProvider;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.easymock.EasyMock.capture;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test the listener/monitor service for {@link ZookeeperRemoteAliasService}.
 */
public class ZookeeperRemoteAliasMonitorTest {
  private static final String configMonitorName = "remoteConfigMonitorClient";
  private static final String expectedClusterName = "sandbox";
  private static final String expectedAlias = "knox.test.alias";
  private static final String expectedPassword = "dummyPassword";
  private static final String expectedClusterNameDev = "development";
  private static final String expectedAliasDev = "knox.test.alias.dev";
  private static final String expectedPasswordDev = "otherDummyPassword";

  private static final String preferRemoteAlias = "prefer.remote.alias";
  private static final String preferRemoteAliasEncryptedPassword = "QmgrK2JBRlE1MUU9OjpIYzZlVUttKzdaWkFOSjlYZVVyVzNRPT06Om5kdTQ3WTJ1by9vSHprZUZHcjBqVG5TaGxsMFVUdUNyN0EvUlZDV1ZHQUU9";
  private static final String preferRemoteAliasClearPassword = "ApacheKnoxPassword123";

  private static TestingCluster zkNodes;
  private static CuratorFramework client;

  @BeforeClass
  public static void setupSuite() throws Exception {
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

    zkNodes.start();

    // Create the client for the test cluster
    client = CuratorFrameworkFactory.builder()
        .connectString(zkNodes.getConnectString())
        .retryPolicy(new ExponentialBackoffRetry(100, 3)).build();
    assertNotNull(client);
    client.start();
    assertTrue(client.blockUntilConnected(10, TimeUnit.SECONDS));

    // Create the knox config paths with an ACL for the sasl user configured for the client
    List<ACL> acls = new ArrayList<>();
    acls.add(new ACL(ZooDefs.Perms.ALL, ZooDefs.Ids.ANYONE_ID_UNSAFE));

    client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT)
        .withACL(acls).forPath(
        ZookeeperRemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY + ZookeeperRemoteAliasService.
            PATH_SEPARATOR + expectedClusterName);

    assertNotNull("Failed to create node:"
        + ZookeeperRemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY
        + ZookeeperRemoteAliasService.
        PATH_SEPARATOR + expectedClusterName, client.checkExists().forPath(
        ZookeeperRemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY + ZookeeperRemoteAliasService.
            PATH_SEPARATOR + expectedClusterName));

    client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT)
        .withACL(acls).forPath(
        ZookeeperRemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY + ZookeeperRemoteAliasService.
            PATH_SEPARATOR + expectedClusterNameDev);

    assertNotNull("Failed to create node:"
        + ZookeeperRemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY
        + ZookeeperRemoteAliasService.
        PATH_SEPARATOR + expectedClusterNameDev, client.checkExists().forPath(
        ZookeeperRemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY + ZookeeperRemoteAliasService.
            PATH_SEPARATOR + expectedClusterNameDev));

    /* Start Zookeeper with an existing alias */
    client.create().withMode(CreateMode.PERSISTENT).
        forPath(ZookeeperRemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY
                + ZookeeperRemoteAliasService.
                PATH_SEPARATOR + expectedClusterName
                + ZookeeperRemoteAliasService.PATH_SEPARATOR + preferRemoteAlias,
            preferRemoteAliasEncryptedPassword.getBytes(StandardCharsets.UTF_8));
  }

  @AfterClass
  public static void tearDownSuite() throws Exception {
    // Clean up the ZK nodes, and close the client
    if (client != null) {
      client.delete().deletingChildrenIfNeeded()
          .forPath(ZookeeperRemoteAliasService.PATH_KNOX_SECURITY);
      client.close();
    }

    // Shutdown the ZK cluster
    zkNodes.close();
  }

  @Test
  public void testListener() throws Exception {

    // Setup the base GatewayConfig mock
    GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
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

    EasyMock.expect(gc.isRemoteAliasServiceEnabled())
        .andReturn(true).anyTimes();
    EasyMock.replay(gc);

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

    EasyMock.expect(defaultAlias.getPasswordFromAliasForCluster(expectedClusterName, preferRemoteAlias))
        .andReturn(preferRemoteAliasClearPassword.toCharArray()).anyTimes();

    EasyMock.replay(defaultAlias);

    final DefaultMasterService ms = EasyMock
        .createNiceMock(DefaultMasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("knox".toCharArray())
        .anyTimes();
    EasyMock.replay(ms);

    final RemoteConfigurationRegistryClientService clientService = (new ZooKeeperClientServiceProvider())
        .newInstance();
    clientService.setAliasService(defaultAlias);
    clientService.init(gc, Collections.emptyMap());

    final ZookeeperRemoteAliasService zkAlias = new ZookeeperRemoteAliasService(defaultAlias,
        ms, clientService);
    zkAlias.init(gc, Collections.emptyMap());
    zkAlias.start();

    /* GET Aliases */
    List<String> aliases = zkAlias.getAliasesForCluster(expectedClusterName);
    List<String> aliasesDev = zkAlias
        .getAliasesForCluster(expectedClusterNameDev);

    /* no alias added so ist should be empty, except the one in ZK  */
    Assert.assertEquals(aliases.size(), 1);
    Assert.assertEquals(aliasesDev.size(), 0);

    /* Create an alias in Zookeeper externally */
    client.create().withMode(CreateMode.PERSISTENT).
        forPath(ZookeeperRemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY
                + ZookeeperRemoteAliasService.
                PATH_SEPARATOR + expectedClusterName
                + ZookeeperRemoteAliasService.PATH_SEPARATOR + expectedAlias,
            zkAlias.encrypt(expectedPassword).getBytes(StandardCharsets.UTF_8));

    /* Create an alias in Zookeeper externally */
    client.create().withMode(CreateMode.PERSISTENT).
        forPath(ZookeeperRemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY
                + ZookeeperRemoteAliasService.
                PATH_SEPARATOR + expectedClusterNameDev
                + ZookeeperRemoteAliasService.PATH_SEPARATOR + expectedAliasDev,
            zkAlias.encrypt(expectedPasswordDev).getBytes(StandardCharsets.UTF_8));

    /* Try */
    aliases = zkAlias.getAliasesForCluster(expectedClusterName);
    aliasesDev = zkAlias.getAliasesForCluster(expectedClusterNameDev);

    Assert.assertTrue("Expected alias 'knox.test.alias' not found ",
        aliases.contains(expectedAlias));
    Assert.assertTrue("Expected alias 'knox.test.alias.dev' not found ",
        aliasesDev.contains(expectedAliasDev));

    final char[] result = zkAlias
        .getPasswordFromAliasForCluster(expectedClusterName, expectedAlias);
    final char[] result1 = zkAlias
        .getPasswordFromAliasForCluster(expectedClusterNameDev,
            expectedAliasDev);

    /* make sure the externally added passwords match */
    Assert.assertEquals(expectedPassword, new String(result));
    Assert.assertEquals(expectedPasswordDev, new String(result1));

    /* test that remote alias service prefers remote over local */
    final char[] prefAliasResult = zkAlias
        .getPasswordFromAliasForCluster(expectedClusterName, preferRemoteAlias);
    Assert.assertEquals(preferRemoteAliasClearPassword, new String(prefAliasResult));

    zkAlias.stop();
  }
}

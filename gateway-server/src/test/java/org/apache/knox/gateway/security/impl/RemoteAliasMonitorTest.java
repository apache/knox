/**
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
package org.apache.knox.gateway.security.impl;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.service.config.remote.zk.ZooKeeperClientService;
import org.apache.knox.gateway.service.config.remote.zk.ZooKeeperClientServiceProvider;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.impl.DefaultAliasService;
import org.apache.knox.gateway.services.security.impl.DefaultMasterService;
import org.apache.knox.gateway.services.security.impl.RemoteAliasService;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testng.Assert;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.easymock.EasyMock.capture;
import static org.junit.Assert.assertNotNull;

/**
 * Test the listener/monitor service for
 * remote alias service.
 */
public class RemoteAliasMonitorTest {

  private static TestingCluster zkNodes;

  private static CuratorFramework client;
  private static String configMonitorName = "remoteConfigMonitorClient";
  private static String expectedClusterName = "sandbox";
  private static String expectedAlias = "knox.test.alias";
  private static String expectedPassword = "dummyPassword";
  private static String expectedClusterNameDev = "development";
  private static String expectedAliasDev = "knox.test.alias.dev";
  private static String expectedPasswordDev = "otherDummyPassword";
  /* For CLI tests */
  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
  private GatewayConfig gc;

  public RemoteAliasMonitorTest() {
    super();
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @BeforeClass
  public static void setupSuite() throws Exception {
    // Configure security for the ZK cluster instances
    Map<String, Object> customInstanceSpecProps = new HashMap<>();
    customInstanceSpecProps.put("authProvider.1",
        "org.apache.zookeeper.server.auth.SASLAuthenticationProvider");
    customInstanceSpecProps.put("requireClientAuthScheme", "sasl");

    // Define the test cluster
    List<InstanceSpec> instanceSpecs = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
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

    // Create the knox config paths with an ACL for the sasl user configured for the client
    List<ACL> acls = new ArrayList<>();
    acls.add(new ACL(ZooDefs.Perms.ALL, ZooDefs.Ids.ANYONE_ID_UNSAFE));

    client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT)
        .withACL(acls).forPath(
        RemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY + RemoteAliasService.
            PATH_SEPARATOR + expectedClusterName);

    assertNotNull("Failed to create node:"
        + RemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY
        + RemoteAliasService.
        PATH_SEPARATOR + expectedClusterName, client.checkExists().forPath(
        RemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY + RemoteAliasService.
            PATH_SEPARATOR + expectedClusterName));

    client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT)
        .withACL(acls).forPath(
        RemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY + RemoteAliasService.
            PATH_SEPARATOR + expectedClusterNameDev);
    assertNotNull("Failed to create node:"
        + RemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY
        + RemoteAliasService.
        PATH_SEPARATOR + expectedClusterNameDev, client.checkExists().forPath(
        RemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY + RemoteAliasService.
            PATH_SEPARATOR + expectedClusterNameDev));
  }

  @AfterClass
  public static void tearDownSuite() throws Exception {
    // Clean up the ZK nodes, and close the client
    if (client != null) {
      client.delete().deletingChildrenIfNeeded()
          .forPath(RemoteAliasService.PATH_KNOX_SECURITY);
      client.close();
    }

    // Shutdown the ZK cluster
    zkNodes.close();
  }

  @Test
  public void testListener() throws Exception {

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

    EasyMock.replay(defaultAlias);

    final DefaultMasterService ms = EasyMock
        .createNiceMock(DefaultMasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("knox".toCharArray())
        .anyTimes();
    EasyMock.replay(ms);

    final RemoteAliasService zkAlias = new RemoteAliasService();

    final RemoteConfigurationRegistryClientService clientService = (new ZooKeeperClientServiceProvider())
        .newInstance();
    clientService.setAliasService(zkAlias);
    clientService.init(gc, Collections.emptyMap());

    /* init */
    zkAlias.setLocalAliasService(defaultAlias);
    zkAlias.setRegistryClientService(clientService);
    zkAlias.setMasterService(ms);
    zkAlias.init(gc, Collections.emptyMap());
    zkAlias.start();

    /* GET Aliases */
    List<String> aliases = zkAlias.getAliasesForCluster(expectedClusterName);
    List<String> aliasesDev = zkAlias
        .getAliasesForCluster(expectedClusterNameDev);

    /* no alias added so ist should be empty */
    Assert.assertEquals(aliases.size(), 0);
    Assert.assertEquals(aliasesDev.size(), 0);


    /* Create an alias in Zookeeper externally */
    client.create().withMode(CreateMode.PERSISTENT).
        forPath(RemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY
                + RemoteAliasService.
                PATH_SEPARATOR + expectedClusterName
                + RemoteAliasService.PATH_SEPARATOR + expectedAlias,
            zkAlias.encrypt(expectedPassword).getBytes());

    /* Create an alias in Zookeeper externally */
    client.create().withMode(CreateMode.PERSISTENT).
        forPath(RemoteAliasService.PATH_KNOX_ALIAS_STORE_TOPOLOGY
                + RemoteAliasService.
                PATH_SEPARATOR + expectedClusterNameDev
                + RemoteAliasService.PATH_SEPARATOR + expectedAliasDev,
            zkAlias.encrypt(expectedPasswordDev).getBytes());

    /* Try */
    aliases = zkAlias.getAliasesForCluster(expectedClusterName);
    aliasesDev = zkAlias.getAliasesForCluster(expectedClusterNameDev);

    Assert.assertTrue(aliases.contains(expectedAlias),
        "Expected alias 'knox.test.alias' not found ");
    Assert.assertTrue(aliasesDev.contains(expectedAliasDev),
        "Expected alias 'knox.test.alias.dev' not found ");

    final char[] result = zkAlias
        .getPasswordFromAliasForCluster(expectedClusterName, expectedAlias);
    final char[] result1 = zkAlias
        .getPasswordFromAliasForCluster(expectedClusterNameDev,
            expectedAliasDev);

    /* make sure the externally added passwords match */
    Assert.assertEquals(expectedPassword, new String(result));
    Assert.assertEquals(expectedPasswordDev, new String(result1));

  }

}

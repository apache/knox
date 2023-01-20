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
package org.apache.knox.gateway.service.config.remote.zk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.service.config.remote.RemoteConfigurationRegistryClientServiceFactory;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.easymock.EasyMock;

public class RemoteConfigurationRegistryClientServiceTestBase {
    /*
     * Setup and start a secure test ZooKeeper cluster.
     */
    protected TestingCluster setupAndStartSecureTestZooKeeper(String principal, String digestPassword) throws Exception {
        final boolean applyAuthentication = (principal != null);
        // Configure security for the ZK cluster instances
        Map<String, Object> customInstanceSpecProps = new HashMap<>();
        customInstanceSpecProps.put("admin.enableServer", false);

        if (applyAuthentication) {
            customInstanceSpecProps.put("authProvider.1", "org.apache.zookeeper.server.auth.SASLAuthenticationProvider");
            customInstanceSpecProps.put("requireClientAuthScheme", "sasl");
        }

        // Define the test cluster
        List<InstanceSpec> instanceSpecs = new ArrayList<>();
        for (int i = 0 ; i < 1 ; i++) {
            InstanceSpec is = new InstanceSpec(null, -1, -1, -1, false, (i+1), -1, -1, customInstanceSpecProps);
            instanceSpecs.add(is);
        }
        TestingCluster zkCluster = new TestingCluster(instanceSpecs);

        if (applyAuthentication) {
            // Setup ZooKeeper server SASL
            Map<String, String> digestOptions = new HashMap<>();
            digestOptions.put("user_" + principal, digestPassword);
            final AppConfigurationEntry[] serverEntries =
                    {new AppConfigurationEntry("org.apache.zookeeper.server.auth.DigestLoginModule",
                            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                            digestOptions)};
            Configuration.setConfiguration(new Configuration() {
                @Override
                public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                    return ("Server".equalsIgnoreCase(name)) ? serverEntries : null;
                }
            });
        }

        // Start the cluster
        zkCluster.start();

        return zkCluster;
    }

    /**
     * Create a ZooKeeper client with SASL digest auth configured, and initialize the test znodes.
     * @param zkCluster zkCluster to initialize
     * @param principal principal for SASL digrest auth
     * @throws Exception exception on failure
     */
    protected CuratorFramework initializeTestClientAndZNodes(TestingCluster zkCluster, String principal) throws Exception {
        // Create the client for the test cluster
        CuratorFramework setupClient = CuratorFrameworkFactory.builder()
                                                              .connectString(zkCluster.getConnectString())
                                                              .retryPolicy(new ExponentialBackoffRetry(100, 3))
                                                              .build();
        assertNotNull(setupClient);
        setupClient.start();

        assertTrue(setupClient.blockUntilConnected(10, TimeUnit.SECONDS));

        List<ACL> acls = new ArrayList<>();
        if (principal != null) {
            acls.add(new ACL(ZooDefs.Perms.ALL, new Id("sasl", principal)));
        } else {
            acls.add(new ACL(ZooDefs.Perms.ALL, ZooDefs.Ids.ANYONE_ID_UNSAFE));
        }
        setupClient.create().creatingParentsIfNeeded().withACL(acls).forPath("/knox/config/descriptors");
        setupClient.create().creatingParentsIfNeeded().withACL(acls).forPath("/knox/config/shared-providers");

        List<ACL> negativeACLs = new ArrayList<>();
        if (principal != null) {
            negativeACLs.add(new ACL(ZooDefs.Perms.ALL, new Id("sasl", "notyou")));
        } else {
            negativeACLs.add(new ACL(ZooDefs.Perms.ALL, ZooDefs.Ids.ANYONE_ID_UNSAFE));
        }
        setupClient.create().creatingParentsIfNeeded().withACL(negativeACLs).forPath("/someotherconfig");

        return setupClient;
    }

    protected void doTestZooKeeperClient(final CuratorFramework setupClient,
                                       final String           testClientName,
                                       final GatewayConfig    config,
                                       final String           credentialAlias,
                                       final String           digestPassword) throws Exception {
        boolean isSecureTest = (credentialAlias != null && digestPassword != null);

        // Mock alias service
        AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
        EasyMock.expect(aliasService.getPasswordFromAliasForGateway(credentialAlias))
                .andReturn(isSecureTest ? digestPassword.toCharArray() : null)
                .anyTimes();
        EasyMock.replay(aliasService);

        // Create the client service instance
        RemoteConfigurationRegistryClientService clientService =
                RemoteConfigurationRegistryClientServiceFactory.newInstance(config);
        assertEquals("Wrong registry client service type.", clientService.getClass(), CuratorClientService.class);
        clientService.setAliasService(aliasService);
        clientService.init(config, null);
        clientService.start();

        doTestZooKeeperClient(setupClient, testClientName, clientService, isSecureTest);
    }

    /**
     * Test secure ZooKeeper client interactions.
     *
     * @param setupClient    The client used for interacting with ZooKeeper independent from the registry client service.
     * @param testClientName The name of the client to use from the registry client service.
     * @param clientService  The RemoteConfigurationRegistryClientService
     * @param isSecureTest   Flag to indicate whether this is a secure interaction test
     * @throws Exception exception on failure
     */
    protected void doTestZooKeeperClient(final CuratorFramework                         setupClient,
                                       final String                                   testClientName,
                                       final RemoteConfigurationRegistryClientService clientService,
                                       boolean                                        isSecureTest) throws Exception {

        RemoteConfigurationRegistryClient client = clientService.get(testClientName);
        assertNotNull(client);
        List<String> descriptors = client.listChildEntries("/knox/config/descriptors");
        assertNotNull(descriptors);

        List<String> providerConfigs = client.listChildEntries("/knox/config/shared-providers");
        assertNotNull(providerConfigs);

        List<String> someotherConfig = client.listChildEntries("/someotherconfig");
        if (isSecureTest) {
            assertNull("Expected null because of the ACL mismatch.", someotherConfig);
        } else {
            assertNotNull(someotherConfig);
        }

        // Test listeners
        final String MY_NEW_ZNODE = "/clientServiceTestNode";
        final String MY_NEW_DATA_ZNODE = MY_NEW_ZNODE + "/mydata";

        if (setupClient.checkExists().forPath(MY_NEW_ZNODE) != null) {
            setupClient.delete().deletingChildrenIfNeeded().forPath(MY_NEW_ZNODE);
        }

        final List<String> listenerLog = new ArrayList<>();
        client.addChildEntryListener(MY_NEW_ZNODE, (c, type, path) -> {
            listenerLog.add("EXTERNAL: " + type.toString() + ":" + path);
            if (RemoteConfigurationRegistryClient.ChildEntryListener.Type.ADDED.equals(type)) {
                try {
                    c.addEntryListener(path, (cc, p, d) -> listenerLog.add("EXTERNAL: " + p + ":" + (d != null ? new String(d, StandardCharsets.UTF_8) : "null")));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        client.createEntry(MY_NEW_ZNODE);
        client.createEntry(MY_NEW_DATA_ZNODE, "more test data");
        String testData = client.getEntryData(MY_NEW_DATA_ZNODE);
        assertNotNull(testData);
        assertEquals("more test data", testData);

        assertTrue(client.entryExists(MY_NEW_DATA_ZNODE));
        client.setEntryData(MY_NEW_DATA_ZNODE, "still more data");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            //
        }

        client.setEntryData(MY_NEW_DATA_ZNODE, "changed completely");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            //
        }

        client.deleteEntry(MY_NEW_DATA_ZNODE);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            //
        }

        assertFalse(listenerLog.isEmpty());
    }
}

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

import org.apache.commons.io.FileUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.service.config.remote.RemoteConfigurationRegistryClientServiceFactory;
import org.apache.knox.gateway.service.config.remote.util.RemoteRegistryConfigTestUtils;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RemoteConfigurationRegistryClientServiceTest {

    /*
     * Test a configuration for an unsecured remote registry, included in the gateway configuration.
     */
    @Test
    public void testUnsecuredZooKeeperWithSimpleRegistryConfig() throws Exception {
        final String REGISTRY_CLIENT_NAME = "unsecured-zk-registry-name";
        final String PRINCIPAL = null;
        final String PWD = null;
        final String CRED_ALIAS = null;

        // Configure and start a secure ZK cluster
        try (TestingCluster zkCluster = setupAndStartSecureTestZooKeeper(PRINCIPAL, PWD)) {
            // Create the setup client for the test cluster, and initialize the test znodes
            CuratorFramework setupClient = initializeTestClientAndZNodes(zkCluster, PRINCIPAL);

            // Mock configuration
            GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
            final String registryConfigValue =
                GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE + "=" + ZooKeeperClientService.TYPE + ";" +
                    GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS + "=" + zkCluster.getConnectString();
            EasyMock.expect(config.getRemoteRegistryConfiguration(REGISTRY_CLIENT_NAME))
                .andReturn(registryConfigValue)
                .anyTimes();
            EasyMock.expect(config.getRemoteRegistryConfigurationNames())
                .andReturn(Collections.singletonList(REGISTRY_CLIENT_NAME)).anyTimes();
            EasyMock.replay(config);

            doTestZooKeeperClient(setupClient, REGISTRY_CLIENT_NAME, config, CRED_ALIAS, PWD);
        }
    }

    /*
     * Test multiple configurations for an unsecured remote registry.
     */
    @Test
    public void testMultipleUnsecuredZooKeeperWithSimpleRegistryConfig() throws Exception {
        final String REGISTRY_CLIENT_NAME_1 = "zkclient1";
        final String REGISTRY_CLIENT_NAME_2 = "zkclient2";
        final String PRINCIPAL = null;
        final String PWD = null;

        // Configure and start a secure ZK cluster
        try (TestingCluster zkCluster = setupAndStartSecureTestZooKeeper(PRINCIPAL, PWD)) {
            // Create the setup client for the test cluster, and initialize the test znodes
            CuratorFramework setupClient = initializeTestClientAndZNodes(zkCluster, PRINCIPAL);

            // Mock configuration
            GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
            final String registryConfigValue1 =
                GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE + "=" + ZooKeeperClientService.TYPE + ";" +
                    GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS + "=" + zkCluster.getConnectString();
            EasyMock.expect(config.getRemoteRegistryConfiguration(REGISTRY_CLIENT_NAME_1))
                .andReturn(registryConfigValue1).anyTimes();
            final String registryConfigValue2 =
                GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE + "=" + ZooKeeperClientService.TYPE + ";" +
                    GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS + "=" + zkCluster.getConnectString();
            EasyMock.expect(config.getRemoteRegistryConfiguration(REGISTRY_CLIENT_NAME_2))
                .andReturn(registryConfigValue2).anyTimes();
            EasyMock.expect(config.getRemoteRegistryConfigurationNames())
                .andReturn(Arrays.asList(REGISTRY_CLIENT_NAME_1, REGISTRY_CLIENT_NAME_2)).anyTimes();
            EasyMock.replay(config);

            // Create the client service instance
            RemoteConfigurationRegistryClientService clientService =
                RemoteConfigurationRegistryClientServiceFactory.newInstance(config);
            assertEquals("Wrong registry client service type.", clientService.getClass(), CuratorClientService.class);
            clientService.setAliasService(null);
            clientService.init(config, null);
            clientService.start();

            RemoteConfigurationRegistryClient client1 = clientService.get(REGISTRY_CLIENT_NAME_1);
            assertNotNull(client1);

            RemoteConfigurationRegistryClient client2 = clientService.get(REGISTRY_CLIENT_NAME_2);
            assertNotNull(client2);

            doTestZooKeeperClient(setupClient, REGISTRY_CLIENT_NAME_1, clientService, false);
            doTestZooKeeperClient(setupClient, REGISTRY_CLIENT_NAME_2, clientService, false);
        }
    }

    /*
     * Test a configuration for a secure remote registry, included in the gateway configuration.
     */
    @Test
    public void testZooKeeperWithSimpleRegistryConfig() throws Exception {
        final String AUTH_TYPE = "digest";
        final String REGISTRY_CLIENT_NAME = "zk-registry-name";
        final String PRINCIPAL = "knox";
        final String PWD = "knoxtest";
        final String CRED_ALIAS = "zkCredential";

        // Configure and start a secure ZK cluster
        try (TestingCluster zkCluster = setupAndStartSecureTestZooKeeper(PRINCIPAL, PWD)) {
            // Create the setup client for the test cluster, and initialize the test znodes
            CuratorFramework setupClient = initializeTestClientAndZNodes(zkCluster, PRINCIPAL);

            // Mock configuration
            GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
            final String registryConfigValue =
                GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE + "=" + ZooKeeperClientService.TYPE + ";" +
                    GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS + "=" + zkCluster.getConnectString() + ";" +
                    GatewayConfig.REMOTE_CONFIG_REGISTRY_AUTH_TYPE + "=" + AUTH_TYPE + ";" +
                    GatewayConfig.REMOTE_CONFIG_REGISTRY_PRINCIPAL + "=" + PRINCIPAL + ";" +
                    GatewayConfig.REMOTE_CONFIG_REGISTRY_CREDENTIAL_ALIAS + "=" + CRED_ALIAS;
            EasyMock.expect(config.getRemoteRegistryConfiguration(REGISTRY_CLIENT_NAME))
                .andReturn(registryConfigValue)
                .anyTimes();
            EasyMock.expect(config.getRemoteRegistryConfigurationNames())
                .andReturn(Collections.singletonList(REGISTRY_CLIENT_NAME)).anyTimes();
            EasyMock.replay(config);

            doTestZooKeeperClient(setupClient, REGISTRY_CLIENT_NAME, config, CRED_ALIAS, PWD);
        }
    }

    /*
     * Test the remote registry configuration external to, and referenced from, the gateway configuration, for a secure
     * client.
     */
    @Test
    public void testZooKeeperWithSingleExternalRegistryConfig() throws Exception {
        final String AUTH_TYPE = "digest";
        final String REGISTRY_CLIENT_NAME = "my-zookeeper_registryNAME";
        final String PRINCIPAL = "knox";
        final String PWD = "knoxtest";
        final String CRED_ALIAS = "zkCredential";

        // Configure and start a secure ZK cluster
        File tmpRegConfigFile = null;
        try (TestingCluster zkCluster = setupAndStartSecureTestZooKeeper(PRINCIPAL, PWD)) {
            // Create the setup client for the test cluster, and initialize the test znodes
            CuratorFramework setupClient = initializeTestClientAndZNodes(zkCluster, PRINCIPAL);

            // Mock configuration
            Map<String, String> registryConfigProps = new HashMap<>();
            registryConfigProps.put("type", ZooKeeperClientService.TYPE);
            registryConfigProps.put("name", REGISTRY_CLIENT_NAME);
            registryConfigProps.put("address", zkCluster.getConnectString());
            registryConfigProps.put("secure", "true");
            registryConfigProps.put("authType", AUTH_TYPE);
            registryConfigProps.put("principal", PRINCIPAL);
            registryConfigProps.put("credentialAlias", CRED_ALIAS);
            String registryConfigXML =
                RemoteRegistryConfigTestUtils.createRemoteConfigRegistriesXML(Collections.singleton(registryConfigProps));
            tmpRegConfigFile = File.createTempFile("myRemoteRegistryConfig", "xml");
            FileUtils.writeStringToFile(tmpRegConfigFile, registryConfigXML, StandardCharsets.UTF_8);

            System.setProperty("org.apache.knox.gateway.remote.registry.config.file", tmpRegConfigFile.getAbsolutePath());

            GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
            EasyMock.replay(config);

            doTestZooKeeperClient(setupClient, REGISTRY_CLIENT_NAME, config, CRED_ALIAS, PWD);
        } finally {
            if (tmpRegConfigFile != null && tmpRegConfigFile.exists()) {
                tmpRegConfigFile.delete();
            }
            System.clearProperty("org.apache.knox.gateway.remote.registry.config.file");
        }
    }

    /*
     * Setup and start a secure test ZooKeeper cluster.
     */
    private TestingCluster setupAndStartSecureTestZooKeeper(String principal, String digestPassword) throws Exception {
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
        for (int i = 0 ; i < 3 ; i++) {
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
    private CuratorFramework initializeTestClientAndZNodes(TestingCluster zkCluster, String principal) throws Exception {
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

    private void doTestZooKeeperClient(final CuratorFramework setupClient,
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
    private void doTestZooKeeperClient(final CuratorFramework                         setupClient,
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

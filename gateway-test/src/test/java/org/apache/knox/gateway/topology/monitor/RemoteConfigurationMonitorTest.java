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
package org.apache.knox.gateway.topology.monitor;

import org.apache.commons.io.FileUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.service.config.remote.zk.ZooKeeperClientService;
import org.apache.knox.gateway.service.config.remote.zk.ZooKeeperClientServiceProvider;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.test.TestUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

/**
 * Test the RemoteConfigurationMonitor functionality with SASL configured, and znode ACLs applied.
 *
 * The expected implementation is org.apache.knox.gateway.topology.monitor.zk.ZooKeeperConfigMonitor
 *
 * Digest-based SASL is used for this test, but since that is dictated solely by the JAAS config, Kerberos-based SASL
 * should work in exactly the same way, simply by modifying the SASL config.
 */
public class RemoteConfigurationMonitorTest {

    private static final String PATH_KNOX = "/knox";
    private static final String PATH_KNOX_CONFIG = PATH_KNOX + "/config";
    private static final String PATH_KNOX_PROVIDERS = PATH_KNOX_CONFIG + "/shared-providers";
    private static final String PATH_KNOX_DESCRIPTORS = PATH_KNOX_CONFIG + "/descriptors";

    private static final String PATH_AUTH_TEST = "/auth_test/child_node";


    private static final String ALT_USERNAME = "notyou";
    private static final String ZK_USERNAME = "testsasluser";
    private static final String ZK_PASSWORD = "testsaslpwd";

    private static final ACL ANY_AUTHENTICATED_USER_ALL = new ACL(ZooDefs.Perms.ALL, new Id("auth", ""));
    private static final ACL SASL_TESTUSER_ALL = new ACL(ZooDefs.Perms.ALL, new Id("sasl", ZK_USERNAME));
    private static final ACL WORLD_ANYONE_READ = new ACL(ZooDefs.Perms.READ, new Id("world", "anyone"));

    private static File testTmp;
    private static File providersDir;
    private static File descriptorsDir;

    private TestingCluster zkCluster;
    private CuratorFramework client;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        testTmp = TestUtils.createTempDir(RemoteConfigurationMonitorTest.class.getName());
        File confDir = TestUtils.createTempDir(testTmp + "/conf");
        providersDir = TestUtils.createTempDir(confDir + "/shared-providers");
        descriptorsDir = TestUtils.createTempDir(confDir + "/descriptors");
    }

    @AfterClass
    public static void tearDownAfterClass() {
        // Delete the working dir
        testTmp.delete();
    }

    @Before
    public void setupTest() throws Exception {
        configureAndStartZKCluster();
    }

    @After
    public void tearDownTest() throws Exception {
        // Clean up the ZK nodes, and close the client
        if (client != null) {
            if (client.checkExists().forPath(PATH_KNOX) != null) {
                client.delete().deletingChildrenIfNeeded().forPath(PATH_KNOX);
            }
            client.close();
        }

        // Shutdown the ZK cluster
        zkCluster.close();
    }

    /**
     * Create and persist a JAAS configuration file, defining the SASL config for both the ZooKeeper cluster instances
     * and ZooKeeper clients.
     *
     * @param username The digest username
     * @param password The digest password
     *
     * @return The JAAS configuration file
     */
    private static File setupDigestSaslConfig(String username, String password) throws Exception {
        File saslConfigFile = new File(testTmp, "server-jaas.conf");
        try(OutputStream outputStream = Files.newOutputStream(saslConfigFile.toPath());
            Writer fw = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
          fw.write("Server {\n" +
                       "    org.apache.zookeeper.server.auth.DigestLoginModule required\n" +
                       "    user_" + username + " =\"" + password + "\";\n" +
                       "};\n" +
                       "Client {\n" +
                       "    org.apache.zookeeper.server.auth.DigestLoginModule required\n" +
                       "    username=\"" + username + "\"\n" +
                       "    password=\"" + password + "\";\n" +
                       "};\n");
        }
        return saslConfigFile;
    }

    /**
     * Configure and start the ZooKeeper test cluster, and create the znodes monitored by the RemoteConfigurationMonitor.
     */
    private void configureAndStartZKCluster() throws Exception {
        // Configure security for the ZK cluster instances
        Map<String, Object> customInstanceSpecProps = new HashMap<>();
        customInstanceSpecProps.put("authProvider.1", "org.apache.zookeeper.server.auth.SASLAuthenticationProvider");
        customInstanceSpecProps.put("requireClientAuthScheme", "sasl");
        customInstanceSpecProps.put("admin.enableServer", false);

        // Define the test cluster
        List<InstanceSpec> instanceSpecs = new ArrayList<>();
        for (int i = 0 ; i < 3 ; i++) {
            InstanceSpec is = new InstanceSpec(null, -1, -1, -1, false, (i+1), -1, -1, customInstanceSpecProps);
            instanceSpecs.add(is);
        }
        zkCluster = new TestingCluster(instanceSpecs);

        // Configure auth for the ZooKeeper servers and the clients
        File saslConfigFile = setupDigestSaslConfig(ZK_USERNAME, ZK_PASSWORD);

        // This system property is used by the ZooKeeper cluster instances, the test driver client, and the
        // RemoteConfigurationMonitor implementation for SASL authentication/authorization
        System.setProperty("java.security.auth.login.config", saslConfigFile.getAbsolutePath());

        // Start the cluster
        zkCluster.start();

        // Create the client for the test cluster
        client = CuratorFrameworkFactory.builder()
                                        .connectString(zkCluster.getConnectString())
                                        .retryPolicy(new ExponentialBackoffRetry(100, 3))
                                        .build();
        assertNotNull(client);
        client.start();

        assertTrue(client.blockUntilConnected(10, TimeUnit.SECONDS));

        // Create test config nodes with an ACL for a sasl user that is NOT configured for the test client
        List<ACL> acls = Arrays.asList(new ACL(ZooDefs.Perms.ALL, new Id("sasl", ALT_USERNAME)),
                                       new ACL(ZooDefs.Perms.READ, ZooDefs.Ids.ANYONE_ID_UNSAFE));
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_AUTH_TEST);
        assertNotNull("Failed to create node:" + PATH_AUTH_TEST,
                      client.checkExists().forPath(PATH_AUTH_TEST));
    }

    private void validateKnoxConfigNodeACLs(List<ACL> expectedACLS, List<ACL> actualACLs) {
        assertEquals(expectedACLS.size(), actualACLs.size());
        int matchedCount = 0;
        for (ACL expected : expectedACLS) {
            for (ACL actual : actualACLs) {
                Id expectedId = expected.getId();
                Id actualId = actual.getId();
                if (actualId.getScheme().equals(expectedId.getScheme()) && actualId.getId().equals(expectedId.getId())) {
                    matchedCount++;
                    assertEquals(expected.getPerms(), actual.getPerms());
                    break;
                }
            }
        }
        assertEquals("ACL mismatch despite being same quantity.", expectedACLS.size(), matchedCount);
    }

    @Test
    public void testZooKeeperConfigMonitorSASLNodesExistWithUnacceptableACL() throws Exception {
        final String configMonitorName = "zkConfigClient";
        final String alias = "zkPass";

        // Setup the base GatewayConfig mock
        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gc.getGatewayProvidersConfigDir()).andReturn(providersDir.getAbsolutePath()).anyTimes();
        EasyMock.expect(gc.getGatewayDescriptorsDir()).andReturn(descriptorsDir.getAbsolutePath()).anyTimes();
        EasyMock.expect(gc.getRemoteRegistryConfigurationNames())
            .andReturn(Collections.singletonList(configMonitorName))
            .anyTimes();
        final String registryConfig =
            GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE + "=" + ZooKeeperClientService.TYPE + ";" +
                GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS + "=" + zkCluster.getConnectString() + ";" +
                GatewayConfig.REMOTE_CONFIG_REGISTRY_PRINCIPAL + "=" + ZK_USERNAME + ";" +
                GatewayConfig.REMOTE_CONFIG_REGISTRY_AUTH_TYPE + "=Digest;" +
                GatewayConfig.REMOTE_CONFIG_REGISTRY_CREDENTIAL_ALIAS + "=" + alias;
        EasyMock.expect(gc.getRemoteRegistryConfiguration(configMonitorName))
            .andReturn(registryConfig).anyTimes();
        EasyMock.expect(gc.getRemoteConfigurationMonitorClientName()).andReturn(configMonitorName).anyTimes();
        EasyMock.replay(gc);

        AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
        EasyMock.expect(aliasService.getPasswordFromAliasForGateway(alias))
            .andReturn(ZK_PASSWORD.toCharArray())
            .anyTimes();
        EasyMock.replay(aliasService);

        RemoteConfigurationRegistryClientService clientService = (new ZooKeeperClientServiceProvider()).newInstance();
        clientService.setAliasService(aliasService);
        clientService.init(gc, Collections.emptyMap());
        clientService.start();

        RemoteConfigurationMonitorFactory.setClientService(clientService);

        RemoteConfigurationMonitor cm = RemoteConfigurationMonitorFactory.get(gc);
        assertNotNull("Failed to load RemoteConfigurationMonitor", cm);

        final ACL ANY_AUTHENTICATED_USER_ALL = new ACL(ZooDefs.Perms.ALL, new Id("auth", ""));
        List<ACL> acls = Arrays.asList(ANY_AUTHENTICATED_USER_ALL, new ACL(ZooDefs.Perms.WRITE, ZooDefs.Ids.ANYONE_ID_UNSAFE));
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX);
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX_CONFIG);
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX_PROVIDERS);
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX_DESCRIPTORS);

        // Make sure both ACLs were applied
        List<ACL> preACLs = client.getACL().forPath(PATH_KNOX);
        assertEquals(2, preACLs.size());

        // Check that the config nodes really do exist (the monitor will NOT create them if they're present)
        assertNotNull(client.checkExists().forPath(PATH_KNOX));
        assertNotNull(client.checkExists().forPath(PATH_KNOX_CONFIG));
        assertNotNull(client.checkExists().forPath(PATH_KNOX_PROVIDERS));
        assertNotNull(client.checkExists().forPath(PATH_KNOX_DESCRIPTORS));

        try {
            cm.start();

            // Validate the expected ACLs on the Knox config znodes (make sure the monitor removed the world:anyone ACL)
            List<ACL> expectedACLs = Collections.singletonList(SASL_TESTUSER_ALL);
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX));
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX_CONFIG));
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX_PROVIDERS));
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX_DESCRIPTORS));
        } finally {
            clientService.stop();
            cm.stop();
        }
    }


    /*
     * KNOX-1135
     */
    @Test
    public void testZooKeeperConfigMonitorSASLNodesExistWithUnacceptableACLAllowUnauthenticatedReads() throws Exception {
        final String configMonitorName = "zkConfigClient";
        final String alias = "zkPass";

        // Setup the base GatewayConfig mock
        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gc.getGatewayProvidersConfigDir()).andReturn(providersDir.getAbsolutePath()).anyTimes();
        EasyMock.expect(gc.getGatewayDescriptorsDir()).andReturn(descriptorsDir.getAbsolutePath()).anyTimes();
        EasyMock.expect(gc.getRemoteRegistryConfigurationNames())
            .andReturn(Collections.singletonList(configMonitorName))
            .anyTimes();
        final String registryConfig =
            GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE + "=" + ZooKeeperClientService.TYPE + ";" +
                GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS + "=" + zkCluster.getConnectString() + ";" +
                GatewayConfig.REMOTE_CONFIG_REGISTRY_PRINCIPAL + "=" + ZK_USERNAME + ";" +
                GatewayConfig.REMOTE_CONFIG_REGISTRY_AUTH_TYPE + "=Digest;" +
                GatewayConfig.REMOTE_CONFIG_REGISTRY_CREDENTIAL_ALIAS + "=" + alias;
        EasyMock.expect(gc.allowUnauthenticatedRemoteRegistryReadAccess()).andReturn(true).anyTimes();
        EasyMock.expect(gc.getRemoteRegistryConfiguration(configMonitorName))
            .andReturn(registryConfig).anyTimes();
        EasyMock.expect(gc.getRemoteConfigurationMonitorClientName()).andReturn(configMonitorName).anyTimes();
        EasyMock.replay(gc);

        AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
        EasyMock.expect(aliasService.getPasswordFromAliasForGateway(alias))
            .andReturn(ZK_PASSWORD.toCharArray())
            .anyTimes();
        EasyMock.replay(aliasService);

        RemoteConfigurationRegistryClientService clientService = (new ZooKeeperClientServiceProvider()).newInstance();
        clientService.setAliasService(aliasService);
        clientService.init(gc, Collections.emptyMap());
        clientService.start();

        RemoteConfigurationMonitorFactory.setClientService(clientService);

        RemoteConfigurationMonitor cm = RemoteConfigurationMonitorFactory.get(gc);
        assertNotNull("Failed to load RemoteConfigurationMonitor", cm);

        final ACL ANY_AUTHENTICATED_USER_ALL = new ACL(ZooDefs.Perms.ALL, new Id("auth", ""));
        List<ACL> acls = Arrays.asList(ANY_AUTHENTICATED_USER_ALL, new ACL(ZooDefs.Perms.WRITE, ZooDefs.Ids.ANYONE_ID_UNSAFE));
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX);
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX_CONFIG);
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX_PROVIDERS);
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX_DESCRIPTORS);

        // Make sure both ACLs were applied
        List<ACL> preACLs = client.getACL().forPath(PATH_KNOX);
        assertEquals(2, preACLs.size());

        // Check that the config nodes really do exist (the monitor will NOT create them if they're present)
        assertNotNull(client.checkExists().forPath(PATH_KNOX));
        assertNotNull(client.checkExists().forPath(PATH_KNOX_CONFIG));
        assertNotNull(client.checkExists().forPath(PATH_KNOX_PROVIDERS));
        assertNotNull(client.checkExists().forPath(PATH_KNOX_DESCRIPTORS));

        try {
            cm.start();

            // Validate the expected ACLs on the Knox config znodes (make sure the monitor removed the world:anyone ACL)
            List<ACL> expectedACLs = new ArrayList<>();
            expectedACLs.add(SASL_TESTUSER_ALL);
            expectedACLs.add(WORLD_ANYONE_READ);
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX));
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX_CONFIG));
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX_PROVIDERS));
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX_DESCRIPTORS));
        } finally {
            clientService.stop();
            cm.stop();
        }
    }


    @Test
    public void testZooKeeperConfigMonitorSASLNodesExistWithAcceptableACL() throws Exception {
        final String configMonitorName = "zkConfigClient";
        final String alias = "zkPass";

        // Setup the base GatewayConfig mock
        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gc.getGatewayProvidersConfigDir()).andReturn(providersDir.getAbsolutePath()).anyTimes();
        EasyMock.expect(gc.getGatewayDescriptorsDir()).andReturn(descriptorsDir.getAbsolutePath()).anyTimes();
        EasyMock.expect(gc.getRemoteRegistryConfigurationNames())
                .andReturn(Collections.singletonList(configMonitorName))
                .anyTimes();
        final String registryConfig =
                GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE + "=" + ZooKeeperClientService.TYPE + ";" +
                        GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS + "=" + zkCluster.getConnectString() + ";" +
                        GatewayConfig.REMOTE_CONFIG_REGISTRY_PRINCIPAL + "=" + ZK_USERNAME + ";" +
                        GatewayConfig.REMOTE_CONFIG_REGISTRY_AUTH_TYPE + "=Digest;" +
                        GatewayConfig.REMOTE_CONFIG_REGISTRY_CREDENTIAL_ALIAS + "=" + alias;
        EasyMock.expect(gc.getRemoteRegistryConfiguration(configMonitorName))
                .andReturn(registryConfig).anyTimes();
        EasyMock.expect(gc.getRemoteConfigurationMonitorClientName()).andReturn(configMonitorName).anyTimes();
        EasyMock.replay(gc);

        AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
        EasyMock.expect(aliasService.getPasswordFromAliasForGateway(alias))
                .andReturn(ZK_PASSWORD.toCharArray())
                .anyTimes();
        EasyMock.replay(aliasService);

        RemoteConfigurationRegistryClientService clientService = (new ZooKeeperClientServiceProvider()).newInstance();
        clientService.setAliasService(aliasService);
        clientService.init(gc, Collections.emptyMap());
        clientService.start();

        RemoteConfigurationMonitorFactory.setClientService(clientService);

        RemoteConfigurationMonitor cm = RemoteConfigurationMonitorFactory.get(gc);
        assertNotNull("Failed to load RemoteConfigurationMonitor", cm);

        List<ACL> acls = Collections.singletonList(ANY_AUTHENTICATED_USER_ALL);
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX);
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX_CONFIG);
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX_PROVIDERS);
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX_DESCRIPTORS);

        // Check that the config nodes really do exist (the monitor will NOT create them if they're present)
        assertNotNull(client.checkExists().forPath(PATH_KNOX));
        assertNotNull(client.checkExists().forPath(PATH_KNOX_CONFIG));
        assertNotNull(client.checkExists().forPath(PATH_KNOX_PROVIDERS));
        assertNotNull(client.checkExists().forPath(PATH_KNOX_DESCRIPTORS));

        try {
            cm.start();

            // Test auth violation
            clientService.get(configMonitorName).createEntry("/auth_test/child_node/test1");
            assertNull("Creation should have been prevented since write access is not granted to the test client.",
                client.checkExists().forPath("/auth_test/child_node/test1"));
            assertTrue("Creation should have been prevented since write access is not granted to the test client.",
                client.getChildren().forPath("/auth_test/child_node").isEmpty());

            // Validate the expected ACLs on the Knox config znodes (make sure the monitor didn't change them)
            List<ACL> expectedACLs = Collections.singletonList(SASL_TESTUSER_ALL);
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX));
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX_CONFIG));
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX_PROVIDERS));
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX_DESCRIPTORS));
        } finally {
            clientService.stop();
            cm.stop();
        }
    }


    @Test
    public void testZooKeeperConfigMonitorSASLCreateNodes() throws Exception {
        final String configMonitorName = "zkConfigClient";
        final String alias = "zkPass";

        // Setup the base GatewayConfig mock
        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gc.getGatewayProvidersConfigDir()).andReturn(providersDir.getAbsolutePath()).anyTimes();
        EasyMock.expect(gc.getGatewayDescriptorsDir()).andReturn(descriptorsDir.getAbsolutePath()).anyTimes();
        EasyMock.expect(gc.getRemoteRegistryConfigurationNames())
                .andReturn(Collections.singletonList(configMonitorName))
                .anyTimes();
        final String registryConfig =
                            GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE + "=" + ZooKeeperClientService.TYPE + ";" +
                            GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS + "=" + zkCluster.getConnectString() + ";" +
                            GatewayConfig.REMOTE_CONFIG_REGISTRY_PRINCIPAL + "=" + ZK_USERNAME + ";" +
                            GatewayConfig.REMOTE_CONFIG_REGISTRY_AUTH_TYPE + "=Digest;" +
                            GatewayConfig.REMOTE_CONFIG_REGISTRY_CREDENTIAL_ALIAS + "=" + alias;
        EasyMock.expect(gc.getRemoteRegistryConfiguration(configMonitorName))
                .andReturn(registryConfig).anyTimes();
        EasyMock.expect(gc.getRemoteConfigurationMonitorClientName()).andReturn(configMonitorName).anyTimes();
        EasyMock.replay(gc);

        AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
        EasyMock.expect(aliasService.getPasswordFromAliasForGateway(alias))
                .andReturn(ZK_PASSWORD.toCharArray())
                .anyTimes();
        EasyMock.replay(aliasService);

        RemoteConfigurationRegistryClientService clientService = (new ZooKeeperClientServiceProvider()).newInstance();
        clientService.setAliasService(aliasService);
        clientService.init(gc, Collections.emptyMap());
        clientService.start();

        RemoteConfigurationMonitorFactory.setClientService(clientService);

        RemoteConfigurationMonitor cm = RemoteConfigurationMonitorFactory.get(gc);
        assertNotNull("Failed to load RemoteConfigurationMonitor", cm);

        // Check that the config nodes really don't yet exist (the monitor will create them if they're not present)
        assertNull(client.checkExists().forPath(PATH_KNOX));
        assertNull(client.checkExists().forPath(PATH_KNOX_CONFIG));
        assertNull(client.checkExists().forPath(PATH_KNOX_PROVIDERS));
        assertNull(client.checkExists().forPath(PATH_KNOX_DESCRIPTORS));

        try {
            cm.start();

            // Test auth violation
            clientService.get(configMonitorName).createEntry("/auth_test/child_node/test1");
            assertNull("Creation should have been prevented since write access is not granted to the test client.",
                       client.checkExists().forPath("/auth_test/child_node/test1"));
            assertTrue("Creation should have been prevented since write access is not granted to the test client.",
                       client.getChildren().forPath("/auth_test/child_node").isEmpty());

            // Validate the expected ACLs on the Knox config znodes (make sure the monitor created them correctly)
            List<ACL> expectedACLs = Collections.singletonList(SASL_TESTUSER_ALL);
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX));
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX_CONFIG));
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX_PROVIDERS));
            validateKnoxConfigNodeACLs(expectedACLs, client.getACL().forPath(PATH_KNOX_DESCRIPTORS));

            // Test the Knox config nodes, for which authentication should be sufficient for access
            final String pc_one_znode = getProviderPath("providers-config1.xml");
            final File pc_one         = new File(providersDir, "providers-config1.xml");
            final String pc_two_znode = getProviderPath("providers-config2.xml");
            final File pc_two         = new File(providersDir, "providers-config2.xml");

            client.create().withMode(CreateMode.PERSISTENT).forPath(pc_one_znode, TEST_PROVIDERS_CONFIG_1.getBytes(StandardCharsets.UTF_8));
            Thread.sleep(100);
            assertTrue(pc_one.exists());
            assertEquals(TEST_PROVIDERS_CONFIG_1, FileUtils.readFileToString(pc_one, StandardCharsets.UTF_8));

            client.create().withMode(CreateMode.PERSISTENT).forPath(getProviderPath("providers-config2.xml"), TEST_PROVIDERS_CONFIG_2.getBytes(StandardCharsets.UTF_8));
            Thread.sleep(100);
            assertTrue(pc_two.exists());
            assertEquals(TEST_PROVIDERS_CONFIG_2, FileUtils.readFileToString(pc_two, StandardCharsets.UTF_8));

            client.setData().forPath(pc_two_znode, TEST_PROVIDERS_CONFIG_1.getBytes(StandardCharsets.UTF_8));
            Thread.sleep(100);
            assertTrue(pc_two.exists());
            assertEquals(TEST_PROVIDERS_CONFIG_1, FileUtils.readFileToString(pc_two, StandardCharsets.UTF_8));

            client.delete().forPath(pc_two_znode);
            Thread.sleep(100);
            assertFalse(pc_two.exists());

            client.delete().forPath(pc_one_znode);
            Thread.sleep(100);
            assertFalse(pc_one.exists());

            final String desc_one_znode   = getDescriptorPath("test1.json");
            final String desc_two_znode   = getDescriptorPath("test2.json");
            final String desc_three_znode = getDescriptorPath("test3.json");
            final File desc_one           = new File(descriptorsDir, "test1.json");
            final File desc_two           = new File(descriptorsDir, "test2.json");
            final File desc_three         = new File(descriptorsDir, "test3.json");

            client.create().withMode(CreateMode.PERSISTENT).forPath(desc_one_znode, TEST_DESCRIPTOR_1.getBytes(StandardCharsets.UTF_8));
            Thread.sleep(100);
            assertTrue(desc_one.exists());
            assertEquals(TEST_DESCRIPTOR_1, FileUtils.readFileToString(desc_one, StandardCharsets.UTF_8));

            client.create().withMode(CreateMode.PERSISTENT).forPath(desc_two_znode, TEST_DESCRIPTOR_1.getBytes(StandardCharsets.UTF_8));
            Thread.sleep(100);
            assertTrue(desc_two.exists());
            assertEquals(TEST_DESCRIPTOR_1, FileUtils.readFileToString(desc_two, StandardCharsets.UTF_8));

            client.setData().forPath(desc_two_znode, TEST_DESCRIPTOR_2.getBytes(StandardCharsets.UTF_8));
            Thread.sleep(100);
            assertTrue(desc_two.exists());
            assertEquals(TEST_DESCRIPTOR_2, FileUtils.readFileToString(desc_two, StandardCharsets.UTF_8));

            client.create().withMode(CreateMode.PERSISTENT).forPath(desc_three_znode, TEST_DESCRIPTOR_1.getBytes(StandardCharsets.UTF_8));
            Thread.sleep(100);
            assertTrue(desc_three.exists());
            assertEquals(TEST_DESCRIPTOR_1, FileUtils.readFileToString(desc_three, StandardCharsets.UTF_8));

            client.delete().forPath(desc_two_znode);
            Thread.sleep(100);
            assertFalse("Expected test2.json to have been deleted.", desc_two.exists());

            client.delete().forPath(desc_three_znode);
            Thread.sleep(100);
            assertFalse(desc_three.exists());

            client.delete().forPath(desc_one_znode);
            Thread.sleep(100);
            assertFalse(desc_one.exists());
        } finally {
            clientService.stop();
            cm.stop();
        }
    }

    private static String getDescriptorPath(String descriptorName) {
        return PATH_KNOX_DESCRIPTORS + "/" + descriptorName;
    }

    private static String getProviderPath(String providerConfigName) {
        return PATH_KNOX_PROVIDERS + "/" + providerConfigName;
    }


    private static final String TEST_PROVIDERS_CONFIG_1 =
                    "<gateway>\n" +
                    "    <provider>\n" +
                    "        <role>identity-assertion</role>\n" +
                    "        <name>Default</name>\n" +
                    "        <enabled>true</enabled>\n" +
                    "    </provider>\n" +
                    "    <provider>\n" +
                    "        <role>hostmap</role>\n" +
                    "        <name>static</name>\n" +
                    "        <enabled>true</enabled>\n" +
                    "        <param><name>localhost</name><value>sandbox,sandbox.hortonworks.com</value></param>\n" +
                    "    </provider>\n" +
                    "</gateway>\n";

    private static final String TEST_PROVIDERS_CONFIG_2 =
                    "<gateway>\n" +
                    "    <provider>\n" +
                    "        <role>authentication</role>\n" +
                    "        <name>ShiroProvider</name>\n" +
                    "        <enabled>true</enabled>\n" +
                    "        <param>\n" +
                    "            <name>sessionTimeout</name>\n" +
                    "            <value>30</value>\n" +
                    "        </param>\n" +
                    "        <param>\n" +
                    "            <name>main.ldapRealm</name>\n" +
                    "            <value>org.apache.knox.gateway.shirorealm.KnoxLdapRealm</value>\n" +
                    "        </param>\n" +
                    "        <param>\n" +
                    "            <name>main.ldapContextFactory</name>\n" +
                    "            <value>org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory</value>\n" +
                    "        </param>\n" +
                    "        <param>\n" +
                    "            <name>main.ldapRealm.contextFactory</name>\n" +
                    "            <value>$ldapContextFactory</value>\n" +
                    "        </param>\n" +
                    "        <param>\n" +
                    "            <name>main.ldapRealm.userDnTemplate</name>\n" +
                    "            <value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>\n" +
                    "        </param>\n" +
                    "        <param>\n" +
                    "            <name>main.ldapRealm.contextFactory.url</name>\n" +
                    "            <value>ldap://localhost:33389</value>\n" +
                    "        </param>\n" +
                    "        <param>\n" +
                    "            <name>main.ldapRealm.contextFactory.authenticationMechanism</name>\n" +
                    "            <value>simple</value>\n" +
                    "        </param>\n" +
                    "        <param>\n" +
                    "            <name>urls./**</name>\n" +
                    "            <value>authcBasic</value>\n" +
                    "        </param>\n" +
                    "    </provider>\n" +
                    "</gateway>\n";

    private static final String TEST_DESCRIPTOR_1 =
                    "{\n" +
                    "  \"discovery-type\":\"AMBARI\",\n" +
                    "  \"discovery-address\":\"http://sandbox.hortonworks.com:8080\",\n" +
                    "  \"discovery-user\":\"maria_dev\",\n" +
                    "  \"discovery-pwd-alias\":\"sandbox.ambari.discovery.password\",\n" +
                    "  \"provider-config-ref\":\"sandbox-providers.xml\",\n" +
                    "  \"cluster\":\"Sandbox\",\n" +
                    "  \"services\":[\n" +
                    "    {\"name\":\"NODEUI\"},\n" +
                    "    {\"name\":\"YARNUI\"},\n" +
                    "    {\"name\":\"HDFSUI\"},\n" +
                    "    {\"name\":\"OOZIEUI\"},\n" +
                    "    {\"name\":\"HBASEUI\"},\n" +
                    "    {\"name\":\"NAMENODE\"},\n" +
                    "    {\"name\":\"JOBTRACKER\"},\n" +
                    "    {\"name\":\"WEBHDFS\"},\n" +
                    "    {\"name\":\"WEBHCAT\"},\n" +
                    "    {\"name\":\"OOZIE\"},\n" +
                    "    {\"name\":\"WEBHBASE\"},\n" +
                    "    {\"name\":\"RESOURCEMANAGER\"},\n" +
                    "    {\"name\":\"AMBARI\", \"urls\":[\"http://c6401.ambari.apache.org:8080\"]},\n" +
                    "    {\"name\":\"AMBARIUI\", \"urls\":[\"http://c6401.ambari.apache.org:8080\"]}\n" +
                    "  ]\n" +
                    "}\n";

    private static final String TEST_DESCRIPTOR_2 =
                    "{\n" +
                    "  \"discovery-type\":\"AMBARI\",\n" +
                    "  \"discovery-address\":\"http://sandbox.hortonworks.com:8080\",\n" +
                    "  \"discovery-user\":\"maria_dev\",\n" +
                    "  \"discovery-pwd-alias\":\"sandbox.ambari.discovery.password\",\n" +
                    "  \"provider-config-ref\":\"sandbox-providers.xml\",\n" +
                    "  \"cluster\":\"Sandbox\",\n" +
                    "  \"services\":[\n" +
                    "    {\"name\":\"NAMENODE\"},\n" +
                    "    {\"name\":\"JOBTRACKER\"},\n" +
                    "    {\"name\":\"WEBHDFS\"},\n" +
                    "    {\"name\":\"WEBHCAT\"},\n" +
                    "    {\"name\":\"OOZIE\"},\n" +
                    "    {\"name\":\"WEBHBASE\"},\n" +
                    "    {\"name\":\"RESOURCEMANAGER\"}\n" +
                    "  ]\n" +
                    "}\n";
}

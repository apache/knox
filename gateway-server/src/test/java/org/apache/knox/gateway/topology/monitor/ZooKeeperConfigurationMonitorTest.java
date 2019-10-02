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
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test the ZooKeeperConfigMonitor WITHOUT SASL configured or znode ACLs applied.
 * The implementation of the monitor is the same regardless, since the ACLs are defined by the ZooKeeper znode
 * creator, and the SASL config is purely JAAS (and external to the implementation).
 */
public class ZooKeeperConfigurationMonitorTest {

    private static final String PATH_KNOX = "/knox";
    private static final String PATH_KNOX_CONFIG = PATH_KNOX + "/config";
    private static final String PATH_KNOX_PROVIDERS = PATH_KNOX_CONFIG + "/shared-providers";
    private static final String PATH_KNOX_DESCRIPTORS = PATH_KNOX_CONFIG + "/descriptors";

    private static File testTmp;
    private static File providersDir;
    private static File descriptorsDir;

    private static TestingCluster zkCluster;

    private static CuratorFramework client;

    private GatewayConfig gc;


    @BeforeClass
    public static void setupSuite() throws Exception {
        testTmp = TestUtils.createTempDir(ZooKeeperConfigurationMonitorTest.class.getName());
        File confDir   = TestUtils.createTempDir(testTmp + "/conf");
        providersDir   = TestUtils.createTempDir(confDir + "/shared-providers");
        descriptorsDir = TestUtils.createTempDir(confDir + "/descriptors");

        configureAndStartZKCluster();
    }

    private static void configureAndStartZKCluster() throws Exception {
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

        // Start the cluster
        zkCluster.start();

        // Create the client for the test cluster
        client = CuratorFrameworkFactory.builder()
                                        .connectString(zkCluster.getConnectString())
                                        .retryPolicy(new ExponentialBackoffRetry(100, 3))
                                        .build();
        assertNotNull(client);
        client.start();

        boolean connected = client.blockUntilConnected(10, TimeUnit.SECONDS);
        assertTrue(connected);

        // Create the knox config paths with an ACL for the sasl user configured for the client
        List<ACL> acls = new ArrayList<>();
        acls.add(new ACL(ZooDefs.Perms.ALL, ZooDefs.Ids.ANYONE_ID_UNSAFE));

        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX_DESCRIPTORS);
        assertNotNull("Failed to create node:" + PATH_KNOX_DESCRIPTORS,
                      client.checkExists().forPath(PATH_KNOX_DESCRIPTORS));
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(acls).forPath(PATH_KNOX_PROVIDERS);
        assertNotNull("Failed to create node:" + PATH_KNOX_PROVIDERS,
                      client.checkExists().forPath(PATH_KNOX_PROVIDERS));
    }

    @AfterClass
    public static void tearDownSuite() throws Exception {
        // Clean up the ZK nodes, and close the client
        if (client != null) {
            if (client.checkExists().forPath(PATH_KNOX) != null) {
                client.delete().deletingChildrenIfNeeded().forPath(PATH_KNOX);
            }
            client.close();
        }

        // Shutdown the ZK cluster
        zkCluster.close();

        // Delete the working dir
        testTmp.delete();
    }

    @Test
    public void testZooKeeperConfigMonitor() throws Exception {
        String configMonitorName = "remoteConfigMonitorClient";

        // Setup the base GatewayConfig mock
        gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gc.getGatewayProvidersConfigDir()).andReturn(providersDir.getAbsolutePath()).anyTimes();
        EasyMock.expect(gc.getGatewayDescriptorsDir()).andReturn(descriptorsDir.getAbsolutePath()).anyTimes();
        EasyMock.expect(gc.getRemoteRegistryConfigurationNames())
                .andReturn(Collections.singletonList(configMonitorName))
                .anyTimes();
        final String registryConfig =
                                GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE + "=" + ZooKeeperClientService.TYPE + ";" +
                                GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS + "=" + zkCluster.getConnectString();
        EasyMock.expect(gc.getRemoteRegistryConfiguration(configMonitorName))
                .andReturn(registryConfig)
                .anyTimes();
        EasyMock.expect(gc.getRemoteConfigurationMonitorClientName()).andReturn(configMonitorName).anyTimes();
        EasyMock.replay(gc);

        AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
        EasyMock.replay(aliasService);

        RemoteConfigurationRegistryClientService clientService = (new ZooKeeperClientServiceProvider()).newInstance();
        clientService.setAliasService(aliasService);
        clientService.init(gc, Collections.emptyMap());
        clientService.start();

        DefaultRemoteConfigurationMonitor cm = new DefaultRemoteConfigurationMonitor(gc, clientService);

        // Create a provider configuration in the test ZK, prior to starting the monitor, to make sure that the monitor
        // will download existing entries upon starting.
        final String preExistingProviderConfig = getProviderPath("pre-existing-providers.xml");
        client.create().withMode(CreateMode.PERSISTENT).forPath(preExistingProviderConfig,
                                                                TEST_PROVIDERS_CONFIG_1.getBytes(StandardCharsets.UTF_8));
        File preExistingProviderConfigLocalFile = new File(providersDir, "pre-existing-providers.xml");
        assertFalse("This file should not exist locally prior to monitor starting.",
                    preExistingProviderConfigLocalFile.exists());

        try {
            cm.start();
        } catch (Exception e) {
            fail("Failed to start monitor: " + e.getMessage());
        }

        assertTrue("This file should exist locally immediately after monitor starting.",
                    preExistingProviderConfigLocalFile.exists());


        try {
            final String pc_one_znode = getProviderPath("providers-config1.xml");
            final File pc_one         = new File(providersDir, "providers-config1.xml");
            final String pc_two_znode = getProviderPath("providers-config2.xml");
            final File pc_two         = new File(providersDir, "providers-config2.xml");

            client.create().withMode(CreateMode.PERSISTENT).forPath(pc_one_znode, TEST_PROVIDERS_CONFIG_1.getBytes(StandardCharsets.UTF_8));

            assertTrue(TestUtils.waitUntil(pc_one::exists, true,1000));
            assertEquals(TEST_PROVIDERS_CONFIG_1, FileUtils.readFileToString(pc_one, StandardCharsets.UTF_8));

            client.create().withMode(CreateMode.PERSISTENT).forPath(getProviderPath("providers-config2.xml"), TEST_PROVIDERS_CONFIG_2.getBytes(StandardCharsets.UTF_8));
            assertTrue(TestUtils.waitUntil(pc_two::exists, true,1000));
            assertTrue(TestUtils.waitUntil(
                () -> TEST_PROVIDERS_CONFIG_2.equals(FileUtils.readFileToString(pc_two, StandardCharsets.UTF_8)),
                true, 1000));

            client.setData().forPath(pc_two_znode, TEST_PROVIDERS_CONFIG_1.getBytes(StandardCharsets.UTF_8));
            assertTrue(TestUtils.waitUntil(pc_two::exists, true,1000));
            assertTrue(TestUtils.waitUntil(
                () -> TEST_PROVIDERS_CONFIG_1.equals(FileUtils.readFileToString(pc_two, StandardCharsets.UTF_8)),
                true, 1000));

            client.delete().forPath(pc_two_znode);
            assertFalse(TestUtils.waitUntil(pc_two::exists, false,1000));

            client.delete().forPath(pc_one_znode);
            assertFalse(TestUtils.waitUntil(pc_one::exists, false,1000));

            final String desc_one_znode   = getDescriptorPath("test1.json");
            final String desc_two_znode   = getDescriptorPath("test2.json");
            final String desc_three_znode = getDescriptorPath("test3.json");
            final File desc_one           = new File(descriptorsDir, "test1.json");
            final File desc_two           = new File(descriptorsDir, "test2.json");
            final File desc_three         = new File(descriptorsDir, "test3.json");

            client.create().withMode(CreateMode.PERSISTENT).forPath(desc_one_znode, TEST_DESCRIPTOR_1.getBytes(StandardCharsets.UTF_8));
            assertTrue(TestUtils.waitUntil(desc_one::exists, true,1000));
            assertTrue(TestUtils.waitUntil(
                () -> TEST_DESCRIPTOR_1.equals(FileUtils.readFileToString(desc_one, StandardCharsets.UTF_8)),
                true, 1000));

            client.create().withMode(CreateMode.PERSISTENT).forPath(desc_two_znode, TEST_DESCRIPTOR_1.getBytes(StandardCharsets.UTF_8));
            assertTrue(TestUtils.waitUntil(desc_two::exists, true,1000));
            assertTrue(TestUtils.waitUntil(
                () -> TEST_DESCRIPTOR_1.equals(FileUtils.readFileToString(desc_two, StandardCharsets.UTF_8)),
                true, 1000));

            client.setData().forPath(desc_two_znode, TEST_DESCRIPTOR_2.getBytes(StandardCharsets.UTF_8));
            assertTrue(TestUtils.waitUntil(desc_two::exists, true,1000));
            assertTrue(TestUtils.waitUntil(
                () -> TEST_DESCRIPTOR_2.equals(FileUtils.readFileToString(desc_two, StandardCharsets.UTF_8)),
                true, 1000));

            client.create().withMode(CreateMode.PERSISTENT).forPath(desc_three_znode, TEST_DESCRIPTOR_1.getBytes(StandardCharsets.UTF_8));
            assertTrue(TestUtils.waitUntil(desc_three::exists, true,1000));
            assertTrue(TestUtils.waitUntil(
                () -> TEST_DESCRIPTOR_1.equals(FileUtils.readFileToString(desc_three, StandardCharsets.UTF_8)),
                true, 1000));

            client.delete().forPath(desc_two_znode);
            assertFalse("Expected test2.json to have been deleted.",
                TestUtils.waitUntil(desc_two::exists, false,1000));

            client.delete().forPath(desc_three_znode);
            assertFalse(TestUtils.waitUntil(desc_three::exists, false,1000));

            client.delete().forPath(desc_one_znode);
            assertFalse(TestUtils.waitUntil(desc_one::exists, false,1000));
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

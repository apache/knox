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
import org.apache.curator.test.TestingCluster;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.service.config.remote.util.RemoteRegistryConfigTestUtils;

import org.easymock.EasyMock;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RemoteConfigurationRegistryClientServiceSecureTest extends RemoteConfigurationRegistryClientServiceTestBase {
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
}

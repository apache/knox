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
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collections;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.TestingCluster;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.service.config.remote.RemoteConfigurationRegistryClientServiceFactory;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.easymock.EasyMock;
import org.junit.Test;

public class RemoteConfigurationRegistryClientServiceUnsecureTest extends RemoteConfigurationRegistryClientServiceTestBase {
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
}

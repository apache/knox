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
package org.apache.knox.gateway.topology.discovery.ambari;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AmbariConfigurationMonitorTest {
    private File dataDir;

    @Before
    public void setUp() throws Exception {
        File targetDir = new File( System.getProperty("user.dir"), "target");
        File tempDir = new File(targetDir, this.getClass().getName() + "__data__" + UUID.randomUUID());
        FileUtils.forceMkdir(tempDir);
        dataDir = tempDir;
    }

    @After
    public void tearDown() throws Exception {
        dataDir.delete();
    }

    @Test
    public void testPollingMonitor() throws Exception {
        final String addr1 = "http://host1:8080";
        final String addr2 = "http://host2:8080";
        final String cluster1Name = "Cluster_One";
        final String cluster2Name = "Cluster_Two";


        GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(config.getGatewayDataDir()).andReturn(dataDir.getAbsolutePath()).anyTimes();
        EasyMock.expect(config.getClusterMonitorPollingInterval(AmbariConfigurationMonitor.getType()))
                .andReturn(10)
                .anyTimes();
        EasyMock.replay(config);

        // Create the monitor
        TestableAmbariConfigurationMonitor monitor = new TestableAmbariConfigurationMonitor(config);

        // Clear the system property now that the monitor has been initialized
        System.clearProperty(AmbariConfigurationMonitor.INTERVAL_PROPERTY_NAME);


        // Sequence of config changes for testing monitoring for updates
        Map<String, Map<String, List<List<AmbariCluster.ServiceConfiguration>>>> updateConfigurations = new HashMap<>();

        updateConfigurations.put(addr1, new HashMap<>());
        updateConfigurations.get(addr1).put(cluster1Name, Arrays.asList(Arrays.asList(createTestServiceConfig("zoo.cfg", "3"),
                                                                                      createTestServiceConfig("hive-site", "2")),
                                                                        Arrays.asList(createTestServiceConfig("zoo.cfg", "3"),
                                                                                      createTestServiceConfig("hive-site", "3")),
                                                                        Arrays.asList(createTestServiceConfig("zoo.cfg", "2"),
                                                                                      createTestServiceConfig("hive-site", "1"))));

        updateConfigurations.put(addr2, new HashMap<>());
        updateConfigurations.get(addr2).put(cluster2Name, Arrays.asList(Arrays.asList(createTestServiceConfig("zoo.cfg", "1"),
                                                                                      createTestServiceConfig("hive-site", "1")),
                                                                        Collections.singletonList(createTestServiceConfig("zoo.cfg", "1")),
                                                                        Arrays.asList(createTestServiceConfig("zoo.cfg", "1"),
                                                                                      createTestServiceConfig("hive-site", "2"))));

        updateConfigurations.get(addr2).put(cluster1Name, Arrays.asList(Arrays.asList(createTestServiceConfig("zoo.cfg", "2"),
                                                                                      createTestServiceConfig("hive-site", "4")),
                                                                        Arrays.asList(createTestServiceConfig("zoo.cfg", "3"),
                                                                                      createTestServiceConfig("hive-site", "4"),
                                                                                      createTestServiceConfig("yarn-site", "1")),
                                                                        Arrays.asList(createTestServiceConfig("zoo.cfg", "1"),
                                                                                      createTestServiceConfig("hive-site", "2"))));

        Map<String, Map<String, Integer>> configChangeIndex = new HashMap<>();
        configChangeIndex.put(addr1, new HashMap<>());
        configChangeIndex.get(addr1).put(cluster1Name, 0);
        configChangeIndex.get(addr1).put(cluster2Name, 0);
        configChangeIndex.put(addr2, new HashMap<>());
        configChangeIndex.get(addr2).put(cluster2Name, 0);

        // Setup the initial test update data
        // Cluster 1 data change
        monitor.addTestConfigVersion(addr1, cluster1Name, "zoo.cfg", "2");
        monitor.addTestConfigVersion(addr1, cluster1Name, "hive-site", "1");

        // Cluster 2 NO data change
        monitor.addTestConfigVersion(addr2, cluster1Name, "zoo.cfg", "1");
        monitor.addTestConfigVersion(addr2, cluster1Name, "hive-site", "1");

        // Cluster 3 data change
        monitor.addTestConfigVersion(addr2, cluster2Name, "zoo.cfg", "1");
        monitor.addTestConfigVersion(addr2, cluster2Name, "hive-site", "2");

        Map<String, Map<String, AmbariCluster.ServiceConfiguration>> initialAmbariClusterConfigs = new HashMap<>();

        Map<String, AmbariCluster.ServiceConfiguration> cluster1Configs = new HashMap<>();
        AmbariCluster.ServiceConfiguration zooCfg = createTestServiceConfig("zoo.cfg", "1");
        cluster1Configs.put("ZOOKEEPER", zooCfg);

        AmbariCluster.ServiceConfiguration hiveSite = createTestServiceConfig("hive-site", "1");
        cluster1Configs.put("Hive", hiveSite);

        initialAmbariClusterConfigs.put(cluster1Name, cluster1Configs);
        AmbariCluster cluster1 = createTestCluster(cluster1Name, initialAmbariClusterConfigs);

        // Tell the monitor about the cluster configurations
        monitor.addClusterConfigVersions(cluster1, createTestDiscoveryConfig(addr1));

        monitor.addClusterConfigVersions(createTestCluster(cluster2Name, initialAmbariClusterConfigs),
                                         createTestDiscoveryConfig(addr2));

        monitor.addClusterConfigVersions(createTestCluster(cluster1Name, initialAmbariClusterConfigs),
                                         createTestDiscoveryConfig(addr2));

        final Map<String, Map<String, Integer>> changeNotifications = new HashMap<>();
        monitor.addListener((src, cname) -> {
            // Record the notification
            Integer notificationCount  = changeNotifications.computeIfAbsent(src, s -> new HashMap<>())
                                                            .computeIfAbsent(cname, c -> 0);
            changeNotifications.get(src).put(cname, (notificationCount+=1));

            // Update the config version
            int changeIndex = configChangeIndex.get(src).get(cname);
            if (changeIndex < updateConfigurations.get(src).get(cname).size()) {
                List<AmbariCluster.ServiceConfiguration> changes = updateConfigurations.get(src).get(cname).get(changeIndex);

                for (AmbariCluster.ServiceConfiguration change : changes) {
                    monitor.updateConfigState(src, cname, change.getType(), change.getVersion());
                }

                // Increment the change index
                configChangeIndex.get(src).replace(cname, changeIndex + 1);
            }
        });

        try {
            monitor.start();

            long expiration = System.currentTimeMillis() + (1000 * 30);
            while (!areChangeUpdatesExhausted(updateConfigurations, configChangeIndex)
                                                                        && (System.currentTimeMillis() < expiration)) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    //
                }
            }

        } finally {
            monitor.stop();
        }

        assertNotNull("Expected changes to have been reported for source 1.",
                      changeNotifications.get(addr1));

        assertEquals("Expected changes to have been reported.",
                     3, changeNotifications.get(addr1).get(cluster1Name).intValue());

        assertNotNull("Expected changes to have been reported for source 2.",
                      changeNotifications.get(addr2));

        assertEquals("Expected changes to have been reported.",
                     3, changeNotifications.get(addr2).get(cluster2Name).intValue());

        assertNull("Expected changes to have been reported.",
                   changeNotifications.get(addr2).get(cluster1Name));

        // Verify the cache clearing behavior
        Map<String, Map<String, String>> src2ClustersData = monitor.ambariClusterConfigVersions.get(addr2);
        assertTrue("Expected data for this cluster.", src2ClustersData.containsKey(cluster1Name));
        assertTrue("Expected data for this cluster.", src2ClustersData.containsKey(cluster2Name));

        // Clear the cache for this source
        monitor.clearCache(addr2, cluster1Name);

        assertFalse("Expected NO data for this cluster.", src2ClustersData.containsKey(cluster1Name));
        assertTrue("Expected data for this cluster.", src2ClustersData.containsKey(cluster2Name));

        // Make sure the cache for the other source is unaffected
        Map<String, Map<String, String>> src1ClustersData = monitor.ambariClusterConfigVersions.get(addr1);
        assertTrue("Expected data for this cluster.", src1ClustersData.containsKey(cluster1Name));
    }


    private static boolean areChangeUpdatesExhausted(Map<String, Map<String, List<List<AmbariCluster.ServiceConfiguration>>>> updates,
                                              Map<String, Map<String, Integer>> configChangeIndeces) {
        boolean isExhausted = true;

        for (String address : updates.keySet()) {
            Map<String, List<List<AmbariCluster.ServiceConfiguration>>> clusterConfigs = updates.get(address);
            for (String clusterName : clusterConfigs.keySet()) {
                Integer configChangeCount = clusterConfigs.get(clusterName).size();
                if (configChangeIndeces.get(address).containsKey(clusterName)) {
                    if (configChangeIndeces.get(address).get(clusterName) < configChangeCount) {
                        isExhausted = false;
                        break;
                    }
                }
            }
        }

        return isExhausted;
    }

    /**
     *
     * @param name           The cluster name
     * @param serviceConfigs A map of service configurations (keyed by service name)
     *
     * @return a mocked AmbariCluster
     */
    private AmbariCluster createTestCluster(String name,
                                            Map<String, Map<String, AmbariCluster.ServiceConfiguration>> serviceConfigs) {
        AmbariCluster c = EasyMock.createNiceMock(AmbariCluster.class);
        EasyMock.expect(c.getName()).andReturn(name).anyTimes();
        EasyMock.expect(c.getServiceConfigurations()).andReturn(serviceConfigs).anyTimes();
        EasyMock.replay(c);
        return c;
    }

    private AmbariCluster.ServiceConfiguration createTestServiceConfig(String name, String version) {
        AmbariCluster.ServiceConfiguration sc = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
        EasyMock.expect(sc.getType()).andReturn(name).anyTimes();
        EasyMock.expect(sc.getVersion()).andReturn(version).anyTimes();
        EasyMock.replay(sc);
        return sc;
    }

    private ServiceDiscoveryConfig createTestDiscoveryConfig(String address) {
        return createTestDiscoveryConfig(address, null, null);
    }

    private ServiceDiscoveryConfig createTestDiscoveryConfig(String address, String username, String pwdAlias) {
        ServiceDiscoveryConfig sdc = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
        EasyMock.expect(sdc.getAddress()).andReturn(address).anyTimes();
        EasyMock.expect(sdc.getUser()).andReturn(username).anyTimes();
        EasyMock.expect(sdc.getPasswordAlias()).andReturn(pwdAlias).anyTimes();
        EasyMock.replay(sdc);
        return sdc;
    }

    /**
     * AmbariConfigurationMonitor extension that replaces the collection of updated configuration data with a static
     * mechanism rather than the REST invocation mechanism.
     */
    private static final class TestableAmbariConfigurationMonitor extends AmbariConfigurationMonitor {

        Map<String, Map<String, Map<String, String>>> configVersionData = new HashMap<>();

        TestableAmbariConfigurationMonitor(GatewayConfig config) {
            super(config, null, null);
        }

        void addTestConfigVersion(String address, String clusterName, String configType, String configVersion) {
            configVersionData.computeIfAbsent(address, a -> new HashMap<>())
                             .computeIfAbsent(clusterName, cl -> new HashMap<>())
                             .put(configType, configVersion);
        }

        void addTestConfigVersions(String address, String clusterName, Map<String, String> configVersions) {
            configVersionData.computeIfAbsent(address, a -> new HashMap<>())
                             .computeIfAbsent(clusterName, cl -> new HashMap<>())
                             .putAll(configVersions);
        }

        void updateTestConfigVersion(String address, String clusterName, String configType, String updatedVersions) {
            configVersionData.computeIfAbsent(address, a -> new HashMap<>())
                             .computeIfAbsent(clusterName, cl -> new HashMap<>())
                             .replace(configType, updatedVersions);
        }

        void updateTestConfigVersions(String address, String clusterName, Map<String, String> updatedVersions) {
            configVersionData.computeIfAbsent(address, a -> new HashMap<>())
                             .computeIfAbsent(clusterName, cl -> new HashMap<>())
                             .replaceAll((k,v) -> updatedVersions.get(k));
        }

        void updateConfigState(String address, String clusterName, String configType, String configVersion) {
            configVersionsLock.writeLock().lock();
            try {
                if (ambariClusterConfigVersions.containsKey(address)) {
                    ambariClusterConfigVersions.get(address).get(clusterName).replace(configType, configVersion);
                }
            } finally {
                configVersionsLock.writeLock().unlock();
            }
        }

        @Override
        Map<String, String> getUpdatedConfigVersions(String address, String clusterName) {
            Map<String, Map<String, String>> clusterConfigVersions = configVersionData.get(address);
            if (clusterConfigVersions != null) {
                return clusterConfigVersions.get(clusterName);
            }
            return null;
        }
    }
}

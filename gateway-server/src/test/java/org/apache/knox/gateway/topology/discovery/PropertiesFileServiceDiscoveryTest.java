/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery;

import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class PropertiesFileServiceDiscoveryTest {

    private static final Map<String, String> clusterProperties = new HashMap<>();
    static {
        clusterProperties.put("mycluster.name", "mycluster");
        clusterProperties.put("mycluster.NAMENODE.url", "hdfs://namenodehost:8020");
        clusterProperties.put("mycluster.JOBTRACKER.url", "rpc://jobtrackerhostname:8050");
        clusterProperties.put("mycluster.WEBHCAT.url", "http://webhcathost:50111/templeton");
        clusterProperties.put("mycluster.WEBHDFS.url", "http://webhdfshost1:50070/webhdfs,http://webhdfshost2:50070/webhdfs");
        clusterProperties.put("mycluster.OOZIE.url", "http://ooziehost:11000/oozie");
        clusterProperties.put("mycluster.HIVE.url", "http://hivehostname:10001/clipath");
        clusterProperties.put("mycluster.RESOURCEMANAGER.url", "http://remanhost:8088/ws");
        clusterProperties.put("mycluster.HIVE.haEnabled", "true");
        clusterProperties.put("mycluster.HIVE.ensemble", "http://host1:1281,http://host2:1281");
        clusterProperties.put("mycluster.HIVE.namespace", "hiveserver2");
    }

    private static final Properties config = new Properties();
    static {
        for (String name : clusterProperties.keySet()) {
            config.setProperty(name, clusterProperties.get(name));
        }
    }

    @Test
    public void testPropertiesFileServiceDiscovery() throws Exception {
        ServiceDiscovery sd = ServiceDiscoveryFactory.get("PROPERTIES_FILE");
        assertNotNull(sd);

        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.replay(gc);

        String discoveryAddress = this.getClass().getName() + "__test-discovery-source.properties";
        File discoverySource = new File(discoveryAddress);
        try (OutputStream outputStream = Files.newOutputStream(discoverySource.toPath())){
            config.store(outputStream, "Test discovery source for PropertiesFileServiceDiscovery");

            ServiceDiscovery.Cluster c =
                    sd.discover(gc, new DefaultServiceDiscoveryConfig(discoverySource.getAbsolutePath()), "mycluster");
            assertNotNull(c);
            for (String name : clusterProperties.keySet()) {
                if (name.endsWith("url")) {
                    String svcName = name.split("\\.")[1];
                    if ("WEBHDFS".equals(svcName)) {
                        List<String> webhdfsURLs = c.getServiceURLs(svcName);
                        assertEquals(2, webhdfsURLs.size());
                        assertEquals("http://webhdfshost1:50070/webhdfs", webhdfsURLs.get(0));
                        assertEquals("http://webhdfshost2:50070/webhdfs", webhdfsURLs.get(1));
                    } else {
                        assertEquals(clusterProperties.get(name), c.getServiceURLs(svcName).get(0));
                    }
                }
            }

            assertNull("Should not be any ZooKeeper config for RESOURCEMANAGER",
                       c.getZooKeeperConfiguration("RESOURCEMANAGER"));

            // HIVE ZooKeeper config
            ServiceDiscovery.Cluster.ZooKeeperConfig zkConf = c.getZooKeeperConfiguration("HIVE");
            assertNotNull(zkConf);
            assertEquals(Boolean.valueOf(clusterProperties.get("mycluster.HIVE.haEnabled")), zkConf.isEnabled());
            assertEquals(clusterProperties.get("mycluster.HIVE.ensemble"), zkConf.getEnsemble());
            assertEquals(clusterProperties.get("mycluster.HIVE.namespace"), zkConf.getNamespace());
        } finally {
            discoverySource.delete();
        }
    }
}

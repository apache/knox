/**
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
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;


public class PropertiesFileServiceDiscoveryTest {

    private static final Map<String, String> clusterProperties = new HashMap<>();
    static {
        clusterProperties.put("mycluster.name", "mycluster");
        clusterProperties.put("mycluster.NAMENODE", "hdfs://namenodehost:8020");
        clusterProperties.put("mycluster.JOBTRACKER", "rpc://jobtrackerhostname:8050");
        clusterProperties.put("mycluster.WEBHCAT", "http://webhcathost:50111/templeton");
        clusterProperties.put("mycluster.OOZIE", "http://ooziehost:11000/oozie");
        clusterProperties.put("mycluster.HIVE", "http://hivehostname:10001/clipath");
        clusterProperties.put("mycluster.RESOURCEMANAGER", "http://remanhost:8088/ws");
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
        try {
            config.store(new FileOutputStream(discoverySource), "Test discovery source for PropertiesFileServiceDiscovery");

            ServiceDiscovery.Cluster c =
                    sd.discover(gc, new DefaultServiceDiscoveryConfig(discoverySource.getAbsolutePath()), "mycluster");
            assertNotNull(c);
            for (String name : clusterProperties.keySet()) {
                assertEquals(clusterProperties.get(name), c.getServiceURLs(name.split("\\.")[1]).get(0));
            }
        } finally {
            discoverySource.delete();
        }
    }


    private void printServiceURLs(ServiceDiscovery.Cluster cluster, String...services) {
        for (String name : services) {
            String value = "";
            List<String> urls = cluster.getServiceURLs(name);
            if (urls != null && !urls.isEmpty()) {
                for (String url : urls) {
                    value += url + " ";
                }
            }
            System.out.println(String.format("%18s: %s", name, value));
        }
    }


}

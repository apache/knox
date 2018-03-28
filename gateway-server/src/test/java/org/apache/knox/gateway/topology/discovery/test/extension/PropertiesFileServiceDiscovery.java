/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.topology.discovery.test.extension;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.topology.discovery.GatewayService;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

class PropertiesFileServiceDiscovery implements ServiceDiscovery {

    static final String TYPE = "PROPERTIES_FILE";

    @GatewayService
    AliasService aliasService;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Map<String, ServiceDiscovery.Cluster> discover(GatewayConfig gatewayConfig,
                                                          ServiceDiscoveryConfig discoveryConfig) {

        Map<String, ServiceDiscovery.Cluster> result = new HashMap<>();

        Properties p = new Properties();
        try {
            p.load(new FileInputStream(discoveryConfig.getAddress()));

            Map<String, Map<String, List<String>>> clusters = new HashMap<>();
            for (Object key : p.keySet()) {
                String propertyKey = (String)key;
                String[] parts = propertyKey.split("\\.");
                if (parts.length == 2) {
                    String clusterName = parts[0];
                    String serviceName = parts[1];
                    String serviceURL  = p.getProperty(propertyKey);
                    if (!clusters.containsKey(clusterName)) {
                        clusters.put(clusterName, new HashMap<String, List<String>>());
                    }
                    Map<String, List<String>> serviceURLs = clusters.get(clusterName);
                    if (!serviceURLs.containsKey(serviceName)) {
                        serviceURLs.put(serviceName, new ArrayList<String>());
                    }
                    serviceURLs.get(serviceName).add(serviceURL);
                }
            }

            for (String clusterName : clusters.keySet()) {
                result.put(clusterName,
                        new PropertiesFileServiceDiscovery.Cluster(clusterName, clusters.get(clusterName)));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }


    @Override
    public ServiceDiscovery.Cluster discover(GatewayConfig          gwConfig,
                                             ServiceDiscoveryConfig discoveryConfig,
                                             String                 clusterName) {
        Map<String, ServiceDiscovery.Cluster> clusters = discover(gwConfig, discoveryConfig);
        return clusters.get(clusterName);
    }


    static class Cluster implements ServiceDiscovery.Cluster {
        private String name;
        private Map<String, List<String>> serviceURLS = new HashMap<>();

        Cluster(String name, Map<String, List<String>> serviceURLs) {
            this.name = name;
            this.serviceURLS.putAll(serviceURLs);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<String> getServiceURLs(String serviceName) {
            return getServiceURLs(serviceName, null);
        }

        @Override
        public List<String> getServiceURLs(String serviceName, Map<String, String> serviceParams) {
            return serviceURLS.get(serviceName);
        }

        @Override
        public ZooKeeperConfig getZooKeeperConfiguration(String serviceName) {
            return null; // TODO: PJZ: Implement me
        }
    }

}

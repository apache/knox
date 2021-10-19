/*
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

class PropertiesFileServiceDiscovery implements ServiceDiscovery {

    static final String TYPE = "PROPERTIES_FILE";

    @GatewayService
    AliasService aliasService;

    @Override
    public String getType() {
        return TYPE;
    }

    private Map<String, ServiceDiscovery.Cluster> discover(ServiceDiscoveryConfig discoveryConfig) {

        Map<String, ServiceDiscovery.Cluster> result = new HashMap<>();

        Properties p = new Properties();
        try (InputStream inputStream = Files.newInputStream(Paths.get(discoveryConfig.getAddress()))){
            p.load(inputStream);

            Map<String, Map<String, Map<String, String>>> clusterProperties = new HashMap<>();
            Map<String, Map<String, List<String>>> clusterURLs = new HashMap<>();
            for (Object key : p.keySet()) {
                String propertyKey = (String)key;
                String[] parts = propertyKey.split("\\.");
                if (parts.length == 3) {
                    String clusterName = parts[0];
                    if (!clusterURLs.containsKey(clusterName)) {
                        clusterURLs.put(clusterName, new HashMap<>());
                    }
                    if (!clusterProperties.containsKey(clusterName)) {
                        clusterProperties.put(clusterName, new HashMap<>());
                    }
                    String serviceName = parts[1];
                    String property    = parts[2];
                    if ("url".equals(property)) {
                        String serviceURL = p.getProperty(propertyKey);
                        Map<String, List<String>> serviceURLs = clusterURLs.get(clusterName);
                        if (!serviceURLs.containsKey(serviceName)) {
                            serviceURLs.put(serviceName, new ArrayList<>());
                        }

                        // Handle muliple URLs for the service (e.g., HA)
                        String[] svcURLs = serviceURL.split(",");
                        for (String url : svcURLs) {
                          serviceURLs.get(serviceName).add(url);
                        }
                    } else if (!"name".equalsIgnoreCase(property)) { // ZooKeeper config properties
                        Map<String, Map<String, String>> props = clusterProperties.get(clusterName);
                        if (!props.containsKey(serviceName)) {
                            props.put(serviceName, new HashMap<>());
                        }
                        props.get(serviceName).put(property, p.getProperty(propertyKey));
                    }
                }
            }

            for (String clusterName : clusterURLs.keySet()) {
                result.put(clusterName,
                           new PropertiesFileServiceDiscovery.Cluster(clusterName,
                                                                      clusterURLs.get(clusterName),
                                                                      clusterProperties.get(clusterName)));
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
        Map<String, ServiceDiscovery.Cluster> clusters = discover(discoveryConfig);
        return clusters.get(clusterName);
    }

    @Override
    public ServiceDiscovery.Cluster discover(GatewayConfig gwConfig, ServiceDiscoveryConfig config, String clusterName, Collection<String> includedServices) {
      return discover(gwConfig, config, clusterName);
    }


    static class Cluster implements ServiceDiscovery.Cluster {
        private String name;
        private Map<String, List<String>> serviceURLS = new HashMap<>();
        private Map<String, Map<String, String>> serviceConfigProps = new HashMap<>();

        Cluster(String name, Map<String, List<String>> serviceURLs, Map<String, Map<String, String>> svcProperties) {
            this.name = name;
            this.serviceURLS.putAll(serviceURLs);
            this.serviceConfigProps.putAll(svcProperties);
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
            ZooKeeperConfig zkConf = null;

            Map<String, String> svcProps = serviceConfigProps.get(serviceName);
            if (svcProps != null) {
                String enabled = svcProps.get("haEnabled");
                String ensemble = svcProps.get("ensemble");
                String namespace = svcProps.get("namespace");
                if (enabled != null && ensemble != null) {
                    zkConf = new ZooKeeperConfigImpl(Boolean.valueOf(enabled), ensemble, namespace);
                }
            }
            return zkConf;
        }

        private static class ZooKeeperConfigImpl implements ZooKeeperConfig {
            private boolean isEnabled;
            private String  ensemble;
            private String  namespace;

            ZooKeeperConfigImpl(boolean enabled, String ensemble, String namespace) {
                this.isEnabled = enabled;
                this.ensemble  = ensemble;
                this.namespace = namespace;
            }

            @Override
            public boolean isEnabled() {
                return isEnabled;
            }

            @Override
            public String getEnsemble() {
                return ensemble;
            }

            @Override
            public String getNamespace() {
                return namespace;
            }
        }
    }

}

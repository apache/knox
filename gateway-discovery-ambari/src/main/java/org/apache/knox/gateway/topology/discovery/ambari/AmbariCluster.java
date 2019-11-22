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
package org.apache.knox.gateway.topology.discovery.ambari;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

class AmbariCluster implements ServiceDiscovery.Cluster {

    private static final AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

    private static final String ZK_CONFIG_MAPPING_FILE = "ambari-service-discovery-zk-config-mapping.properties";

    private static final String ZK_CONFIG_MAPPING_OVERRIDE_FILE = "ambari-discovery-zk-config.properties";

    static final String ZK_CONFIG_MAPPING_SYSTEM_PROPERTY =
                                                     "org.apache.knox.gateway.topology.discovery.ambari.zk.mapping";

    // Mapping of service roles to Hadoop service configurations and ZooKeeper property names
    private static final Properties zooKeeperHAConfigMappings = new Properties();
    static {
        try {
            // Load all the default mappings
            Properties defaults = new Properties();
            defaults.load(AmbariServiceDiscovery.class.getClassLoader().getResourceAsStream(ZK_CONFIG_MAPPING_FILE));
            for (String name : defaults.stringPropertyNames()) {
                zooKeeperHAConfigMappings.setProperty(name, defaults.getProperty(name));
            }

            // Attempt to apply overriding or additional mappings from external source
            String overridesPath = null;

            // First, check for the well-known overrides config file
            String gatewayConfDir = System.getProperty(ServiceDiscovery.CONFIG_DIR_PROPERTY);
            if (gatewayConfDir != null) {
                File overridesFile = new File(gatewayConfDir, ZK_CONFIG_MAPPING_OVERRIDE_FILE);
                if (overridesFile.exists()) {
                    overridesPath = overridesFile.getAbsolutePath();
                }
            }

            // If no file in the config dir, check for the system property reference
            if (overridesPath == null) {
                overridesPath = System.getProperty(ZK_CONFIG_MAPPING_SYSTEM_PROPERTY);
            }

            if (overridesPath != null) {
                Properties overrides = new Properties();
                try (InputStream in = Files.newInputStream(Paths.get(overridesPath))) {
                    overrides.load(in);
                    for (String name : overrides.stringPropertyNames()) {
                        zooKeeperHAConfigMappings.setProperty(name, overrides.getProperty(name));
                    }
                }
            }
        } catch (Exception e) {
            log.failedToLoadZooKeeperConfigurationMapping(e);
        }
    }

    private String name;

    private ServiceURLFactory urlFactory;

    private Map<String, Map<String, ServiceConfiguration>> serviceConfigurations = new HashMap<>();

    private Map<String, AmbariComponent> components;


    AmbariCluster(String name) {
        this.name = name;
        components = new HashMap<>();
        urlFactory = ServiceURLFactory.newInstance(this);
    }

    void addServiceConfiguration(String serviceName, String configurationType, ServiceConfiguration serviceConfig) {
        if (!serviceConfigurations.keySet().contains(serviceName)) {
            serviceConfigurations.put(serviceName, new HashMap<>());
        }
        serviceConfigurations.get(serviceName).put(configurationType, serviceConfig);
    }


    void addComponent(AmbariComponent component) {
        components.put(component.getName(), component);
    }


    ServiceConfiguration getServiceConfiguration(String serviceName, String configurationType) {
        ServiceConfiguration sc = null;
        Map<String, ServiceConfiguration> configs = serviceConfigurations.get(serviceName);
        if (configs != null) {
            sc = configs.get(configurationType);
        }
        return sc;
    }


    Map<String, Map<String, ServiceConfiguration>> getServiceConfigurations() {
        return serviceConfigurations;
    }


    Map<String, AmbariComponent> getComponents() {
        return components;
    }


    AmbariComponent getComponent(String name) {
        return components.get(name);
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
        return new ArrayList<>(urlFactory.create(serviceName, serviceParams));
    }

    @Override
    public ZooKeeperConfig getZooKeeperConfiguration(String serviceName) {
        ZooKeeperConfig result = null;

        String config = zooKeeperHAConfigMappings.getProperty(serviceName + ".config");
        if (config != null) {
            String[] parts = config.split(":");
            if (parts.length == 2) {
                ServiceConfiguration sc = getServiceConfiguration(parts[0], parts[1]);
                if (sc != null) {
                    String enabledProp   = zooKeeperHAConfigMappings.getProperty(serviceName + ".enabled");
                    String ensembleProp  = zooKeeperHAConfigMappings.getProperty(serviceName + ".ensemble");
                    String portProp      = zooKeeperHAConfigMappings.getProperty(serviceName + ".port");
                    String namespaceProp = zooKeeperHAConfigMappings.getProperty(serviceName + ".namespace");
                    Map<String, String> scProps = sc.getProperties();
                    if (scProps != null) {

                        if (ensembleProp != null) {
                            // If there are multiple ensemble properties specified, then iteratively check for the first
                            // valid value, and use that one.
                            String[] ensembleProps = ensembleProp.split(",");
                            if (ensembleProps.length > 1) {
                                for (String prop : ensembleProps) {
                                    if (!prop.isEmpty()) {
                                        String value = scProps.get(prop);
                                        if (value != null) {
                                            ensembleProp = prop;
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        result =
                            new ZooKeeperConfiguration(enabledProp != null ? scProps.get(enabledProp) : null,
                                                       ensembleProp != null ? scProps.get(ensembleProp) : null,
                                                       portProp != null ? scProps.get(portProp) : null,
                                                       namespaceProp != null ? scProps.get(namespaceProp) : null);
                    }
                }
            }
        }

        return result;
    }


    static class ServiceConfiguration {

        private String type;
        private String version;
        private Map<String, String> props;

        ServiceConfiguration(String type, String version, Map<String, String> properties) {
            this.type = type;
            this.version = version;
            this.props = properties;
        }

        public String getVersion() {
            return version;
        }

        public String getType() {
            return type;
        }

        public Map<String, String> getProperties() {
            return props;
        }
    }


    static class ZooKeeperConfiguration implements ServiceDiscovery.Cluster.ZooKeeperConfig {
        boolean isEnabled;
        String ensemble;
        String namespace;

        ZooKeeperConfiguration(String enabled, String ensemble, String port, String namespace) {
            this.namespace = namespace;
            this.ensemble = (port == null) ? ensemble : applyPortToEnsemble(ensemble, port);
            this.isEnabled = (enabled != null ? Boolean.valueOf(enabled) : true);
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

        private String applyPortToEnsemble(String ensemble, String port) {
            StringBuilder updatedEnsemble = new StringBuilder();

            String[] hosts = ensemble.split(",");
            int index = 0;
            for (String host : hosts) {
                int portIndex = host.indexOf(':');
                String qualifiedHost = (portIndex > 0) ? host.substring(0, portIndex) : host;
                updatedEnsemble.append(qualifiedHost).append(':').append(port);
                index += 1;
                if (index < hosts.length) {
                    updatedEnsemble.append(',');
                }
            }

            return updatedEnsemble.toString();
        }
    }
}

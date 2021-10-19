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

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.topology.ClusterConfigurationMonitorService;
import org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitor;
import org.apache.knox.gateway.topology.discovery.GatewayService;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

class AmbariServiceDiscovery implements ServiceDiscovery {

    static final String TYPE = "Ambari";

    static final String AMBARI_CLUSTERS_URI = AmbariClientCommon.AMBARI_CLUSTERS_URI;

    static final String AMBARI_HOSTROLES_URI = AmbariClientCommon.AMBARI_HOSTROLES_URI;

    static final String AMBARI_SERVICECONFIGS_URI = AmbariClientCommon.AMBARI_SERVICECONFIGS_URI;

    static final String COMPONENT_CONFIG_MAPPING_SYSTEM_PROPERTY =
                                                  "org.apache.knox.gateway.topology.discovery.ambari.component.mapping";

    private static final String COMPONENT_CONFIG_MAPPING_FILE =
                                                        "ambari-service-discovery-component-config-mapping.properties";

    private static final String COMPONENT_CONFIG_OVERRIDES_FILENAME = "ambari-discovery-component-config.properties";

    private static final String GATEWAY_SERVICES_ACCESSOR_CLASS  = "org.apache.knox.gateway.GatewayServer";
    private static final String GATEWAY_SERVICES_ACCESSOR_METHOD = "getGatewayServices";

    private static final AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

    // Map of component names to service configuration types
    private static Map<String, String> componentServiceConfigs = new HashMap<>();
    static {
        initializeComponentConfigMappings();
    }

    @GatewayService
    private AliasService aliasService;

    @GatewayService
    private KeystoreService keystoreService;

    private RESTInvoker restClient;
    private AmbariClientCommon ambariClient;

    // This is used to update the monitor when new cluster configuration details are discovered.
    private AmbariConfigurationMonitor configChangeMonitor;

    private boolean isInitialized;

    //
    static void initializeComponentConfigMappings(){
        try {
            componentServiceConfigs.clear();

            Properties configMapping = new Properties();
            configMapping.load(AmbariServiceDiscovery.class.getClassLoader().getResourceAsStream(COMPONENT_CONFIG_MAPPING_FILE));
            for (String componentName : configMapping.stringPropertyNames()) {
                componentServiceConfigs.put(componentName, configMapping.getProperty(componentName));
            }

            // Attempt to apply overriding or additional mappings from external source
            String overridesPath = null;

            // First, check for the well-known overrides config file
            String gatewayConfDir = System.getProperty(CONFIG_DIR_PROPERTY);
            if (gatewayConfDir != null) {
                File overridesFile = new File(gatewayConfDir, COMPONENT_CONFIG_OVERRIDES_FILENAME);
                if (overridesFile.exists()) {
                    overridesPath = overridesFile.getAbsolutePath();
                }
            }

            // If no file in the config dir, check for the system property reference
            if (overridesPath == null) {
                overridesPath = System.getProperty(COMPONENT_CONFIG_MAPPING_SYSTEM_PROPERTY);
            }

            // If there is an overrides configuration file specified either way, then apply it
            if (overridesPath != null) {
                Properties overrides = new Properties();
                try (InputStream in = Files.newInputStream(Paths.get(overridesPath))) {
                    overrides.load(in);
                    for (String name : overrides.stringPropertyNames()) {
                        componentServiceConfigs.put(name, overrides.getProperty(name));
                    }
                }
            }
        } catch (Exception e) {
            log.failedToLoadServiceDiscoveryURLDefConfiguration(COMPONENT_CONFIG_MAPPING_FILE, e);
        }
    }


    AmbariServiceDiscovery() {
    }


    AmbariServiceDiscovery(RESTInvoker restClient) {
        this.restClient = restClient;
    }


    /**
     * Initialization must be subsequent to construction because the AliasService member isn't assigned until after
     * construction time. This is called internally prior to discovery invocations to make sure the clients have been
     * initialized.
     */
    private void init(GatewayConfig config) {
        if (!isInitialized) {
            if (this.restClient == null) {
                this.restClient = new RESTInvoker(config, aliasService, keystoreService);
            }
            this.ambariClient = new AmbariClientCommon(restClient);
            this.configChangeMonitor = getConfigurationChangeMonitor();

            isInitialized = true;
        }
    }


    /**
     * Get the Ambari configuration change monitor from the associated gateway service.
     */
    private AmbariConfigurationMonitor getConfigurationChangeMonitor() {
        AmbariConfigurationMonitor ambariMonitor = null;
        try {
            Class<?> clazz = Class.forName(GATEWAY_SERVICES_ACCESSOR_CLASS);
            if (clazz != null) {
                Method m = clazz.getDeclaredMethod(GATEWAY_SERVICES_ACCESSOR_METHOD);
                if (m != null) {
                    Object obj = m.invoke(null);
                    if (GatewayServices.class.isAssignableFrom(obj.getClass())) {
                        ClusterConfigurationMonitorService clusterMonitorService =
                              ((GatewayServices) obj).getService(ServiceType.CLUSTER_CONFIGURATION_MONITOR_SERVICE);
                        ClusterConfigurationMonitor monitor =
                            clusterMonitorService.getMonitor(AmbariConfigurationMonitor.getType());
                        if (monitor != null && AmbariConfigurationMonitor.class.isAssignableFrom(monitor.getClass())) {
                            ambariMonitor = (AmbariConfigurationMonitor) monitor;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.errorAccessingConfigurationChangeMonitor(e);
        }
        return ambariMonitor;
    }


    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Cluster discover(GatewayConfig gatewayConfig, ServiceDiscoveryConfig config, String clusterName) {
        AmbariCluster cluster = null;

        String discoveryAddress = config.getAddress();
        String discoveryUser = config.getUser();
        String discoveryPwdAlias = config.getPasswordAlias();

        // Handle missing discovery address value with the default if it has been defined
        if (discoveryAddress == null || discoveryAddress.isEmpty()) {
            discoveryAddress = gatewayConfig.getDefaultDiscoveryAddress();

            // If no default address could be determined
            if (discoveryAddress == null) {
                log.missingDiscoveryAddress();
            }
        }

        // Handle missing discovery cluster value with the default if it has been defined
        if (clusterName == null || clusterName.isEmpty()) {
            clusterName = gatewayConfig.getDefaultDiscoveryCluster();

            // If no default cluster could be determined
            if (clusterName == null) {
                log.missingDiscoveryCluster();
            }
        }

        // There must be a discovery address and cluster or discovery cannot be performed
        if (discoveryAddress != null && clusterName != null) {
            cluster = new AmbariCluster(clusterName);

            String encodedClusterName;
            try {
                encodedClusterName = URLEncoder.encode(clusterName, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace(); // TODO: Logging
                encodedClusterName = clusterName;
            }

            Map<String, String> serviceComponents = new HashMap<>();

            init(gatewayConfig);

            Map<String, List<String>> componentHostNames = new HashMap<>();
            String hostRolesURL =
                        String.format(Locale.ROOT, "%s" + AMBARI_HOSTROLES_URI, discoveryAddress, encodedClusterName);
            JSONObject hostRolesJSON = restClient.invoke(hostRolesURL, discoveryUser, discoveryPwdAlias);
            if (hostRolesJSON != null) {
                // Process the host roles JSON
                JSONArray items = (JSONArray) hostRolesJSON.get("items");
                for (Object obj : items) {
                    JSONArray components = (JSONArray) ((JSONObject) obj).get("components");
                    for (Object component : components) {
                        JSONArray hostComponents = (JSONArray) ((JSONObject) component).get("host_components");
                        for (Object hostComponent : hostComponents) {
                            JSONObject hostRoles = (JSONObject) ((JSONObject) hostComponent).get("HostRoles");
                            String serviceName = (String) hostRoles.get("service_name");
                            String componentName = (String) hostRoles.get("component_name");

                            serviceComponents.put(componentName, serviceName);

                            // Assuming public host name is more applicable than host_name
                            String hostName = (String) hostRoles.get("public_host_name");
                            if (hostName == null) {
                                // Some (even slightly) older versions of Ambari/HDP do not return public_host_name,
                                // so fall back to host_name in those cases.
                                hostName = (String) hostRoles.get("host_name");
                            }

                            if (hostName != null) {
                                log.discoveredServiceHost(serviceName, hostName);
                                if (!componentHostNames.containsKey(componentName)) {
                                    componentHostNames.put(componentName, new ArrayList<>());
                                }
                                // Avoid duplicates
                                if (!componentHostNames.get(componentName).contains(hostName)) {
                                    componentHostNames.get(componentName).add(hostName);
                                }
                            }
                        }
                    }
                }
            }

            // Service configurations
            Map<String, Map<String, AmbariCluster.ServiceConfiguration>> serviceConfigurations =
                ambariClient.getActiveServiceConfigurations(discoveryAddress,
                                                            encodedClusterName,
                                                            discoveryUser,
                                                            discoveryPwdAlias);
            if (serviceConfigurations.isEmpty()) {
                log.failedToAccessServiceConfigs(clusterName);
            }
            for (Entry<String, Map<String, AmbariCluster.ServiceConfiguration>> serviceConfiguration : serviceConfigurations.entrySet()) {
                for (Map.Entry<String, AmbariCluster.ServiceConfiguration> serviceConfig : serviceConfiguration.getValue().entrySet()) {
                    cluster.addServiceConfiguration(serviceConfiguration.getKey(), serviceConfig.getKey(), serviceConfig.getValue());
                }
            }

            // Construct the AmbariCluster model
            for (Entry<String, String> entry : serviceComponents.entrySet()) {
                String componentName = entry.getKey();
                String serviceName = entry.getValue();
                List<String> hostNames = componentHostNames.get(componentName);

                Map<String, AmbariCluster.ServiceConfiguration> configs = serviceConfigurations.get(serviceName);
                String configType = componentServiceConfigs.get(componentName);
                if (configType != null) {
                    AmbariCluster.ServiceConfiguration svcConfig = configs.get(configType);
                    if (svcConfig != null) {
                        AmbariComponent c = new AmbariComponent(componentName,
                                                                svcConfig.getVersion(),
                                                                encodedClusterName,
                                                                serviceName,
                                                                hostNames,
                                                                svcConfig.getProperties());
                        cluster.addComponent(c);
                    }
                }
            }

            if (configChangeMonitor != null) {
                // Notify the cluster config monitor about these cluster configuration details
                configChangeMonitor.addClusterConfigVersions(cluster, config);
            }
        }

        return cluster;
    }

    @Override
    public Cluster discover(GatewayConfig gwConfig, ServiceDiscoveryConfig config, String clusterName, Collection<String> includedServices) {
      throw new UnsupportedOperationException("Filtering Ambari service discovery by service names is not supported!");
    }

}

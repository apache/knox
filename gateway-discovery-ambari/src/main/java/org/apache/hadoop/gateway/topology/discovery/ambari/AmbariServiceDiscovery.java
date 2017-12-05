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
package org.apache.hadoop.gateway.topology.discovery.ambari;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.security.AliasService;
import org.apache.hadoop.gateway.topology.ClusterConfigurationMonitorService;
import org.apache.hadoop.gateway.topology.discovery.ClusterConfigurationMonitor;
import org.apache.hadoop.gateway.topology.discovery.GatewayService;
import org.apache.hadoop.gateway.topology.discovery.ServiceDiscovery;
import org.apache.hadoop.gateway.topology.discovery.ServiceDiscoveryConfig;


class AmbariServiceDiscovery implements ServiceDiscovery {

    static final String TYPE = "AMBARI";

    static final String AMBARI_CLUSTERS_URI = AmbariClientCommon.AMBARI_CLUSTERS_URI;

    static final String AMBARI_HOSTROLES_URI = AmbariClientCommon.AMBARI_HOSTROLES_URI;

    static final String AMBARI_SERVICECONFIGS_URI = AmbariClientCommon.AMBARI_SERVICECONFIGS_URI;

    private static final String COMPONENT_CONFIG_MAPPING_FILE =
                                                        "ambari-service-discovery-component-config-mapping.properties";

    private static final String GATEWAY_SERVICES_ACCESSOR_CLASS  = "org.apache.hadoop.gateway.GatewayServer";
    private static final String GATEWAY_SERVICES_ACCESSOR_METHOD = "getGatewayServices";

    private static final AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

    // Map of component names to service configuration types
    private static Map<String, String> componentServiceConfigs = new HashMap<>();
    static {
        try {
            Properties configMapping = new Properties();
            configMapping.load(AmbariServiceDiscovery.class.getClassLoader().getResourceAsStream(COMPONENT_CONFIG_MAPPING_FILE));
            for (String componentName : configMapping.stringPropertyNames()) {
                componentServiceConfigs.put(componentName, configMapping.getProperty(componentName));
            }
        } catch (Exception e) {
            log.failedToLoadServiceDiscoveryURLDefConfiguration(COMPONENT_CONFIG_MAPPING_FILE, e);
        }
    }

    @GatewayService
    private AliasService aliasService;

    private RESTInvoker restClient;
    private AmbariClientCommon ambariClient;

    // This is used to update the monitor when new cluster configuration details are discovered.
    private AmbariConfigurationMonitor configChangeMonitor;

    private boolean isInitialized = false;

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
    private void init() {
        if (!isInitialized) {
            if (this.restClient == null) {
                this.restClient = new RESTInvoker(aliasService);
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
            Class clazz = Class.forName(GATEWAY_SERVICES_ACCESSOR_CLASS);
            if (clazz != null) {
                Method m = clazz.getDeclaredMethod(GATEWAY_SERVICES_ACCESSOR_METHOD);
                if (m != null) {
                    Object obj = m.invoke(null);
                    if (GatewayServices.class.isAssignableFrom(obj.getClass())) {
                        ClusterConfigurationMonitorService clusterMonitorService =
                              ((GatewayServices) obj).getService(GatewayServices.CLUSTER_CONFIGURATION_MONITOR_SERVICE);
                        ClusterConfigurationMonitor monitor =
                                                 clusterMonitorService.getMonitor(AmbariConfigurationMonitor.getType());
                        if (monitor != null) {
                            if (AmbariConfigurationMonitor.class.isAssignableFrom(monitor.getClass())) {
                                ambariMonitor = (AmbariConfigurationMonitor) monitor;
                            }
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
    public Map<String, Cluster> discover(ServiceDiscoveryConfig config) {
        Map<String, Cluster> clusters = new HashMap<>();

        init();

        String discoveryAddress = config.getAddress();

        // Invoke Ambari REST API to discover the available clusters
        String clustersDiscoveryURL = String.format("%s" + AMBARI_CLUSTERS_URI, discoveryAddress);

        JSONObject json = restClient.invoke(clustersDiscoveryURL, config.getUser(), config.getPasswordAlias());

        // Parse the cluster names from the response, and perform the cluster discovery
        JSONArray clusterItems = (JSONArray) json.get("items");
        for (Object clusterItem : clusterItems) {
            String clusterName = (String) ((JSONObject)((JSONObject) clusterItem).get("Clusters")).get("cluster_name");
            try {
                Cluster c = discover(config, clusterName);
                clusters.put(clusterName, c);
            } catch (Exception e) {
                log.clusterDiscoveryError(clusterName, e);
            }
        }

        return clusters;
    }


    @Override
    public Cluster discover(ServiceDiscoveryConfig config, String clusterName) {
        AmbariCluster cluster = new AmbariCluster(clusterName);

        Map<String, String> serviceComponents = new HashMap<>();

        init();

        String discoveryAddress = config.getAddress();
        String discoveryUser = config.getUser();
        String discoveryPwdAlias = config.getPasswordAlias();

        Map<String, List<String>> componentHostNames = new HashMap<>();
        String hostRolesURL = String.format("%s" + AMBARI_HOSTROLES_URI, discoveryAddress, clusterName);
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
                            componentHostNames.get(componentName).add(hostName);
                        }
                    }
                }
            }
        }

        // Service configurations
        Map<String, Map<String, AmbariCluster.ServiceConfiguration>> serviceConfigurations =
                                                        ambariClient.getActiveServiceConfigurations(discoveryAddress,
                                                                                                    clusterName,
                                                                                                    discoveryUser,
                                                                                                    discoveryPwdAlias);
        for (String serviceName : serviceConfigurations.keySet()) {
            for (Map.Entry<String, AmbariCluster.ServiceConfiguration> serviceConfig : serviceConfigurations.get(serviceName).entrySet()) {
                cluster.addServiceConfiguration(serviceName, serviceConfig.getKey(), serviceConfig.getValue());
            }
        }

        // Construct the AmbariCluster model
        for (String componentName : serviceComponents.keySet()) {
            String serviceName = serviceComponents.get(componentName);
            List<String> hostNames = componentHostNames.get(componentName);

            Map<String, AmbariCluster.ServiceConfiguration> configs = serviceConfigurations.get(serviceName);
            String configType = componentServiceConfigs.get(componentName);
            if (configType != null) {
                AmbariCluster.ServiceConfiguration svcConfig = configs.get(configType);
                AmbariComponent c = new AmbariComponent(componentName,
                                                        svcConfig.getVersion(),
                                                        clusterName,
                                                        serviceName,
                                                        hostNames,
                                                        svcConfig.getProperties());
                cluster.addComponent(c);
            }
        }

        if (configChangeMonitor != null) {
            // Notify the cluster config monitor about these cluster configuration details
            configChangeMonitor.addClusterConfigVersions(cluster, config);
        }

        return cluster;
    }

}

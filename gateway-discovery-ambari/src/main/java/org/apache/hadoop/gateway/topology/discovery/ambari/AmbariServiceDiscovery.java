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

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.hadoop.gateway.config.ConfigurationException;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.security.AliasService;
import org.apache.hadoop.gateway.services.security.AliasServiceException;
import org.apache.hadoop.gateway.topology.discovery.GatewayService;
import org.apache.hadoop.gateway.topology.discovery.ServiceDiscovery;
import org.apache.hadoop.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.*;


class AmbariServiceDiscovery implements ServiceDiscovery {

    static final String TYPE = "AMBARI";

    static final String AMBARI_CLUSTERS_URI = "/api/v1/clusters";

    static final String AMBARI_HOSTROLES_URI =
                                       AMBARI_CLUSTERS_URI + "/%s/services?fields=components/host_components/HostRoles";

    static final String AMBARI_SERVICECONFIGS_URI =
            AMBARI_CLUSTERS_URI + "/%s/configurations/service_config_versions?is_current=true";

    // Map of component names to service configuration types
    private static Map<String, String> componentServiceConfigs = new HashMap<>();
    static {
        componentServiceConfigs.put("NAMENODE", "hdfs-site");
        componentServiceConfigs.put("RESOURCEMANAGER", "yarn-site");
        componentServiceConfigs.put("OOZIE_SERVER", "oozie-site");
        componentServiceConfigs.put("HIVE_SERVER", "hive-site");
        componentServiceConfigs.put("WEBHCAT_SERVER", "webhcat-site");
        componentServiceConfigs.put("HBASE_MASTER", "hbase-site");
    } // TODO: Are there other service components, for which the endpoints can be discovered via Ambari?

    private static final String DEFAULT_USER_ALIAS = "ambari.discovery.user";
    private static final String DEFAULT_PWD_ALIAS  = "ambari.discovery.password";

    private static AmbariServiceURLCreator urlCreator = new AmbariServiceURLCreator();

    private AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

    @GatewayService
    private AliasService aliasService;

    private CloseableHttpClient httpClient = null;

    private Map<String, Map<String, String>> serviceConfiguration = new HashMap<>();


    AmbariServiceDiscovery() {
        httpClient = org.apache.http.impl.client.HttpClients.createDefault();
    }


    @Override
    public String getType() {
        return TYPE;
    }


    @Override
    public Map<String, Cluster> discover(ServiceDiscoveryConfig config) {
        Map<String, Cluster> clusters = new HashMap<String, Cluster>();

        String discoveryAddress = config.getAddress();

        // Invoke Ambari REST API to discover the available clusters
        String clustersDiscoveryURL = String.format("%s" + AMBARI_CLUSTERS_URI, discoveryAddress);

        JSONObject json = invokeREST(clustersDiscoveryURL, config.getUser(), config.getPasswordAlias());

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

        String discoveryAddress = config.getAddress();
        String discoveryUser = config.getUser();
        String discoveryPwdAlias = config.getPasswordAlias();

        Map<String, List<String>> componentHostNames = new HashMap<>();
        String hostRolesURL = String.format("%s" + AMBARI_HOSTROLES_URI, discoveryAddress, clusterName);
        JSONObject hostRolesJSON = invokeREST(hostRolesURL, discoveryUser, discoveryPwdAlias);
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

//                    String hostName = (String) hostRoles.get("host_name");
                        String hostName = (String) hostRoles.get("public_host_name"); // Assuming public host name is most applicable
                        log.discoveredServiceHost(serviceName, hostName);
                        if (!componentHostNames.containsKey(componentName)) {
                            componentHostNames.put(componentName, new ArrayList<String>());
                        }
                        componentHostNames.get(componentName).add(hostName);
                    }
                }
            }
        }

        Map<String, Map<String, AmbariCluster.ServiceConfiguration>> serviceConfigurations =
                                                 new HashMap<String, Map<String, AmbariCluster.ServiceConfiguration>>();
        String serviceConfigsURL = String.format("%s" + AMBARI_SERVICECONFIGS_URI, discoveryAddress, clusterName);
        JSONObject serviceConfigsJSON = invokeREST(serviceConfigsURL, discoveryUser, discoveryPwdAlias);
        if (serviceConfigsJSON != null) {
            // Process the service configurations
            JSONArray serviceConfigs = (JSONArray) serviceConfigsJSON.get("items");
            for (Object serviceConfig : serviceConfigs) {
                String serviceName = (String) ((JSONObject) serviceConfig).get("service_name");
                JSONArray configurations = (JSONArray) ((JSONObject) serviceConfig).get("configurations");
                for (Object configuration : configurations) {
                    String configType = (String) ((JSONObject) configuration).get("type");
                    String configVersion = String.valueOf(((JSONObject) configuration).get("version"));

                    Map<String, String> configProps = new HashMap<String, String>();
                    JSONObject configProperties = (JSONObject) ((JSONObject) configuration).get("properties");
                    for (String propertyName : configProperties.keySet()) {
                        configProps.put(propertyName, String.valueOf(((JSONObject) configProperties).get(propertyName)));
                    }
                    if (!serviceConfigurations.containsKey(serviceName)) {
                        serviceConfigurations.put(serviceName, new HashMap<String, AmbariCluster.ServiceConfiguration>());
                    }
                    serviceConfigurations.get(serviceName).put(configType, new AmbariCluster.ServiceConfiguration(configType, configVersion, configProps));
                    cluster.addServiceConfiguration(serviceName, configType, new AmbariCluster.ServiceConfiguration(configType, configVersion, configProps));
                }
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

        return cluster;
    }


    protected JSONObject invokeREST(String url, String username, String passwordAlias) {
        JSONObject result = null;

        CloseableHttpResponse response = null;
        try {
            HttpGet request = new HttpGet(url);

            // If no configured username, then use default username alias
            String password = null;
            if (username == null) {
                if (aliasService != null) {
                    try {
                        char[] defaultUser = aliasService.getPasswordFromAliasForGateway(DEFAULT_USER_ALIAS);
                        if (defaultUser != null) {
                            username = new String(defaultUser);
                        }
                    } catch (AliasServiceException e) {
                        log.aliasServiceUserError(DEFAULT_USER_ALIAS, e.getLocalizedMessage());
                    }
                }

                // If username is still null
                if (username == null) {
                    log.aliasServiceUserNotFound();
                    throw new ConfigurationException("No username is configured for Ambari service discovery.");
                }
            }

            if (aliasService != null) {
                // If not password alias is configured, then try the default alias
                if (passwordAlias == null) {
                    passwordAlias = DEFAULT_PWD_ALIAS;
                }
                try {
                    char[] pwd = aliasService.getPasswordFromAliasForGateway(passwordAlias);
                    if (pwd != null) {
                        password = new String(pwd);
                    }

                } catch (AliasServiceException e) {
                    log.aliasServicePasswordError(passwordAlias, e.getLocalizedMessage());
                }
            }

            // If the password could not be determined
            if (password == null) {
                log.aliasServicePasswordNotFound();
                throw new ConfigurationException("No password is configured for Ambari service discovery.");
            }

            // Add an auth header if credentials are available
            String encodedCreds =
                    org.apache.commons.codec.binary.Base64.encodeBase64String((username + ":" + password).getBytes());
            request.addHeader(new BasicHeader("Authorization", "Basic " + encodedCreds));

            response = httpClient.execute(request);

            if (HttpStatus.SC_OK == response.getStatusLine().getStatusCode()) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    result = (JSONObject) JSONValue.parse((EntityUtils.toString(entity)));
                    log.debugJSON(result.toJSONString());
                } else {
                    log.noJSON(url);
                }
            } else {
                log.unexpectedRestResponseStatusCode(url, response.getStatusLine().getStatusCode());
            }

        } catch (IOException e) {
            log.restInvocationError(url, e);
        } finally {
            if(response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        return result;
    }


}

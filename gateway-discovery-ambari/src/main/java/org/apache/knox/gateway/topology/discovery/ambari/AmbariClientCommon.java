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

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

class AmbariClientCommon {

    static final String AMBARI_CLUSTERS_URI = "/api/v1/clusters";

    static final String AMBARI_HOSTROLES_URI =
                                    AMBARI_CLUSTERS_URI + "/%s/services?fields=components/host_components/HostRoles";

    static final String AMBARI_SERVICECONFIGS_URI =
                                    AMBARI_CLUSTERS_URI + "/%s/configurations/service_config_versions?is_current=true";

    private RESTInvoker restClient;


    AmbariClientCommon(GatewayConfig config, AliasService aliasService, KeystoreService keystoreService) {
        this(new RESTInvoker(config, aliasService, keystoreService));
    }


    AmbariClientCommon(RESTInvoker restInvoker) {
        this.restClient = restInvoker;
    }



    Map<String, Map<String, AmbariCluster.ServiceConfiguration>> getActiveServiceConfigurations(String clusterName,
                                                                                                ServiceDiscoveryConfig config) {
        Map<String, Map<String, AmbariCluster.ServiceConfiguration>> activeConfigs = null;

        if (config != null) {
            activeConfigs = getActiveServiceConfigurations(config.getAddress(),
                                                           clusterName,
                                                           config.getUser(),
                                                           config.getPasswordAlias());
        }

        return activeConfigs;
    }


    Map<String, Map<String, AmbariCluster.ServiceConfiguration>> getActiveServiceConfigurations(String discoveryAddress,
                                                                                                String clusterName,
                                                                                                String discoveryUser,
                                                                                                String discoveryPwdAlias) {
        Map<String, Map<String, AmbariCluster.ServiceConfiguration>> serviceConfigurations = new HashMap<>();

        String serviceConfigsURL = String.format(Locale.ROOT,"%s" + AMBARI_SERVICECONFIGS_URI, discoveryAddress, clusterName);

        JSONObject serviceConfigsJSON = restClient.invoke(serviceConfigsURL, discoveryUser, discoveryPwdAlias);
        if (serviceConfigsJSON != null) {
            // Process the service configurations
            JSONArray serviceConfigs = (JSONArray) serviceConfigsJSON.get("items");
            for (Object serviceConfig : serviceConfigs) {
                String serviceName = (String) ((JSONObject) serviceConfig).get("service_name");
                JSONArray configurations = (JSONArray) ((JSONObject) serviceConfig).get("configurations");
                for (Object configuration : configurations) {
                    String configType = (String) ((JSONObject) configuration).get("type");
                    String configVersion = String.valueOf(((JSONObject) configuration).get("version"));

                    Map<String, String> configProps = new HashMap<>();
                    JSONObject configProperties = (JSONObject) ((JSONObject) configuration).get("properties");
                    for (Entry<String, Object> entry : configProperties.entrySet()) {
                        configProps.put(entry.getKey(), String.valueOf(entry.getValue()));
                    }
                    if (!serviceConfigurations.containsKey(serviceName)) {
                        serviceConfigurations.put(serviceName, new HashMap<>());
                    }
                    serviceConfigurations.get(serviceName).put(configType,
                                                               new AmbariCluster.ServiceConfiguration(configType,
                                                                                                      configVersion,
                                                                                                      configProps));
                }
            }
        }

        return serviceConfigurations;
    }


}

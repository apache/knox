/**
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
package org.apache.hadoop.gateway.topology.discovery.ambari;

import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


class AmbariDynamicServiceURLCreator implements ServiceURLCreator {

    static final String MAPPING_CONFIG_OVERRIDE_PROPERTY = "org.apache.gateway.topology.discovery.ambari.config";

    private AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

    private AmbariCluster cluster = null;
    private ServiceURLPropertyConfig config;

    AmbariDynamicServiceURLCreator(AmbariCluster cluster) {
        this.cluster = cluster;

        String mappingConfiguration = System.getProperty(MAPPING_CONFIG_OVERRIDE_PROPERTY);
        if (mappingConfiguration != null) {
            File mappingConfigFile = new File(mappingConfiguration);
            if (mappingConfigFile.exists()) {
                try {
                    config = new ServiceURLPropertyConfig(mappingConfigFile);
                    log.loadedComponentConfigMappings(mappingConfigFile.getAbsolutePath());
                } catch (Exception e) {
                    log.failedToLoadComponentConfigMappings(mappingConfigFile.getAbsolutePath(), e);
                }
            }
        }

        // If there is no valid override configured, fall-back to the internal mapping configuration
        if (config == null) {
            config = new ServiceURLPropertyConfig();
        }
    }

    AmbariDynamicServiceURLCreator(AmbariCluster cluster, File mappingConfiguration) throws IOException {
        this.cluster = cluster;
        config = new ServiceURLPropertyConfig(new FileInputStream(mappingConfiguration));
    }

    AmbariDynamicServiceURLCreator(AmbariCluster cluster, String mappings) {
        this.cluster = cluster;
        config = new ServiceURLPropertyConfig(new ByteArrayInputStream(mappings.getBytes()));
    }

    public List<String> create(String serviceName) {
        List<String> urls = new ArrayList<>();

        Map<String, String> placeholderValues = new HashMap<>();
        List<String> componentHostnames = new ArrayList<>();
        String hostNamePlaceholder = null;

        ServiceURLPropertyConfig.URLPattern pattern = config.getURLPattern(serviceName);
        if (pattern != null) {
            for (String propertyName : pattern.getPlaceholders()) {
                ServiceURLPropertyConfig.Property configProperty = config.getConfigProperty(serviceName, propertyName);

                String propertyValue = null;
                String propertyType = configProperty.getType();
                if (ServiceURLPropertyConfig.Property.TYPE_SERVICE.equals(propertyType)) {
                    log.lookingUpServiceConfigProperty(configProperty.getService(), configProperty.getServiceConfig(), configProperty.getValue());
                    AmbariCluster.ServiceConfiguration svcConfig =
                        cluster.getServiceConfiguration(configProperty.getService(), configProperty.getServiceConfig());
                    if (svcConfig != null) {
                        propertyValue = svcConfig.getProperties().get(configProperty.getValue());
                    }
                } else if (ServiceURLPropertyConfig.Property.TYPE_COMPONENT.equals(propertyType)) {
                    String compName = configProperty.getComponent();
                    if (compName != null) {
                        AmbariComponent component = cluster.getComponent(compName);
                        if (component != null) {
                            if (ServiceURLPropertyConfig.Property.PROP_COMP_HOSTNAME.equals(configProperty.getValue())) {
                                log.lookingUpComponentHosts(compName);
                                componentHostnames.addAll(component.getHostNames());
                                hostNamePlaceholder = propertyName; // Remember the host name placeholder
                            } else {
                                log.lookingUpComponentConfigProperty(compName, configProperty.getValue());
                                propertyValue = component.getConfigProperty(configProperty.getValue());
                            }
                        }
                    }
                } else { // Derived property
                    log.handlingDerivedProperty(serviceName, configProperty.getType(), configProperty.getName());
                    ServiceURLPropertyConfig.Property p = config.getConfigProperty(serviceName, configProperty.getName());
                    propertyValue = p.getValue();
                    if (propertyValue == null) {
                        if (p.getConditionHandler() != null) {
                            propertyValue = p.getConditionHandler().evaluate(config, cluster);
                        }
                    }
                }

                log.determinedPropertyValue(configProperty.getName(), propertyValue);
                placeholderValues.put(configProperty.getName(), propertyValue);
            }

            // For patterns with a placeholder value for the hostname (e.g., multiple URL scenarios)
            if (!componentHostnames.isEmpty()) {
                for (String componentHostname : componentHostnames) {
                    String url = pattern.get().replace("{" + hostNamePlaceholder + "}", componentHostname);
                    urls.add(createURL(url, placeholderValues));
                }
            } else { // Single URL result case
                urls.add(createURL(pattern.get(), placeholderValues));
            }
        }

        return urls;
    }

    private String createURL(String pattern, Map<String, String> placeholderValues) {
        String url = null;
        if (pattern != null) {
            url = pattern;
            for (String placeHolder : placeholderValues.keySet()) {
                String value = placeholderValues.get(placeHolder);
                if (value != null) {
                    url = url.replace("{" + placeHolder + "}", value);
                }
            }
        }
        return url;
    }

}

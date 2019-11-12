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

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

class AmbariDynamicServiceURLCreator implements ServiceURLCreator {

    static final String MAPPING_CONFIG_OVERRIDE_FILE = "ambari-discovery-url-mappings.xml";

    static final String MAPPING_CONFIG_OVERRIDE_PROPERTY = "org.apache.gateway.topology.discovery.ambari.config";

    private AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

    private AmbariCluster cluster;
    private ServiceURLPropertyConfig config;

    AmbariDynamicServiceURLCreator() {
    }

    AmbariDynamicServiceURLCreator(AmbariCluster cluster) {
        this();
        init(cluster);
    }

    AmbariDynamicServiceURLCreator(AmbariCluster cluster, File mappingConfiguration) throws IOException {
        this.cluster = cluster;
        try (InputStream inputStream = Files.newInputStream(mappingConfiguration.toPath())) {
          config = new ServiceURLPropertyConfig(inputStream);
        }
    }

    AmbariDynamicServiceURLCreator(AmbariCluster cluster, String mappings) {
        this.cluster = cluster;
        config = new ServiceURLPropertyConfig(new ByteArrayInputStream(mappings.getBytes(StandardCharsets.UTF_8)));
    }


    @Override
    public void init(AmbariCluster cluster) {
        this.cluster = cluster;

        // Load the default internal configuration
        config = new ServiceURLPropertyConfig();

        // Attempt to apply overriding or additional mappings from external source
        String mappingConfiguration = null;

        // First, check for the well-known override mapping file
        String gatewayConfDir = System.getProperty(ServiceDiscovery.CONFIG_DIR_PROPERTY);
        if (gatewayConfDir != null) {
            File overridesFile = new File(gatewayConfDir, MAPPING_CONFIG_OVERRIDE_FILE);
            if (overridesFile.exists()) {
                mappingConfiguration = overridesFile.getAbsolutePath();
            }
        }

        // If no file in the config dir, check for the system property reference
        if (mappingConfiguration == null) {
            mappingConfiguration = System.getProperty(MAPPING_CONFIG_OVERRIDE_PROPERTY);
        }

        // If there is an overrides configuration file specified either way, then apply it
        if (mappingConfiguration != null) {
            File mappingConfigFile = new File(mappingConfiguration);
            if (mappingConfigFile.exists()) {
                try {
                    ServiceURLPropertyConfig overrides = new ServiceURLPropertyConfig(mappingConfigFile);
                    log.loadedComponentConfigMappings(mappingConfigFile.getAbsolutePath());
                    config.setAll(overrides); // Apply overrides/additions
                } catch (Exception e) {
                    log.failedToLoadComponentConfigMappings(mappingConfigFile.getAbsolutePath(), e);
                }
            }
        }
    }


    @Override
    public String getTargetService() {
        return null;
    }

    @Override
    public List<String> create(String serviceName, Map<String, String> serviceParams) {
        List<String> urls = new ArrayList<>();

        Map<String, String> placeholderValues = new HashMap<>();
        List<String> componentHostnames = new ArrayList<>();
        String hostNamePlaceholder = null;

        ServiceURLPropertyConfig.URLPattern pattern = config.getURLPattern(serviceName);
        if (pattern != null) {
            for (String propertyName : pattern.getPlaceholders()) {
                ServiceURLPropertyConfig.Property configProperty = config.getConfigProperty(serviceName, propertyName);

                String propertyValue = null;
                if (configProperty != null) {
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
                        if (propertyValue == null && p.getConditionHandler() != null) {
                            propertyValue = p.getConditionHandler().evaluate(config, cluster);
                        }
                    }

                    log.determinedPropertyValue(configProperty.getName(), propertyValue);
                    placeholderValues.put(configProperty.getName(), propertyValue);
                }
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
            for (Entry<String, String> placeHolder : placeholderValues.entrySet()) {
                String value = placeHolder.getValue();
                if (value != null) {
                    url = url.replace("{" + placeHolder.getKey() + "}", value);
                }
            }
        }
        return url;
    }

}

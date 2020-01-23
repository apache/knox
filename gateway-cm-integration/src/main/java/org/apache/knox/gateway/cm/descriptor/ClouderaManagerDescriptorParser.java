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
package org.apache.knox.gateway.cm.descriptor;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.knox.gateway.ClouderaManagerIntegrationMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.topology.discovery.advanced.AdvancedServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.advanced.AdvancedServiceDiscoveryConfigChangeListener;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor;
import org.apache.knox.gateway.topology.simple.SimpleDescriptorImpl;
import org.apache.knox.gateway.topology.simple.SimpleDescriptorImpl.ApplicationImpl;
import org.apache.knox.gateway.topology.simple.SimpleDescriptorImpl.ServiceImpl;

public class ClouderaManagerDescriptorParser implements AdvancedServiceDiscoveryConfigChangeListener {
  private static final ClouderaManagerIntegrationMessages log = MessagesFactory.get(ClouderaManagerIntegrationMessages.class);
  private static final String CONFIG_NAME_DISCOVERY_TYPE = "discoveryType";
  private static final String CONFIG_NAME_DISCOVERY_ADDRESS = "discoveryAddress";
  private static final String CONFIG_NAME_DISCOVERY_USER = "discoveryUser";
  private static final String CONFIG_NAME_DISCOVERY_PASSWORD_ALIAS = "discoveryPasswordAlias";
  private static final String CONFIG_NAME_DISCOVERY_CLUSTER = "cluster";
  private static final String CONFIG_NAME_PROVIDER_CONFIG_REFERENCE = "providerConfigRef";
  private static final String CONFIG_NAME_APPLICATION_PREFIX = "app";
  private static final String CONFIG_NAME_SERVICE_URL = "url";
  private static final String CONFIG_NAME_SERVICE_VERSION = "version";

  private Map<String, AdvancedServiceDiscoveryConfig> advancedServiceDiscoveryConfigMap;

  public ClouderaManagerDescriptorParser() {
    advancedServiceDiscoveryConfigMap = new ConcurrentHashMap<>();
  }

  /**
   * Produces a set of {@link SimpleDescriptor}s from the specified file. Parses ALL descriptors listed in the given file.
   *
   * @param path
   *          The path to the configuration file which holds descriptor information in a pre-defined format.
   * @return A SimpleDescriptor based on the contents of the given file.
   */
  public Set<SimpleDescriptor> parse(String path) {
    return parse(path, null);
  }

  /**
   * Produces a set of {@link SimpleDescriptor}s from the specified file.
   *
   * @param path
   *          The path to the configuration file which holds descriptor information in a pre-defined format.
   * @param topologyName
   *          if set, the parser should only parse a descriptor with the same name
   * @return A SimpleDescriptor based on the contents of the given file.
   */
  public Set<SimpleDescriptor> parse(String path, String topologyName) {
    try {
      log.parseClouderaManagerDescriptor(path, topologyName == null ? "all topologies" : topologyName);
      final Configuration xmlConfiguration = new Configuration(false);
      xmlConfiguration.addResource(Paths.get(path).toUri().toURL());
      xmlConfiguration.reloadConfiguration();
      final Set<SimpleDescriptor> descriptors = parseXmlConfig(xmlConfiguration, topologyName);
      log.parsedClouderaManagerDescriptor(String.join(", ", descriptors.stream().map(descriptor -> descriptor.getName()).collect(Collectors.toSet())), path);
      return descriptors;
    } catch (Exception e) {
      log.failedToParseXmlConfiguration(path, e.getMessage(), e);
      return Collections.emptySet();
    }
  }

  private Set<SimpleDescriptor> parseXmlConfig(Configuration xmlConfiguration, String topologyName) {
    final Set<SimpleDescriptor> descriptors = new LinkedHashSet<>();
    xmlConfiguration.forEach(xmlDescriptor -> {
      String descriptorName = xmlDescriptor.getKey();
      if (topologyName == null || descriptorName.equals(topologyName)) {
        SimpleDescriptor descriptor = parseXmlDescriptor(descriptorName, xmlDescriptor.getValue());
        if (descriptor != null) {
          descriptors.add(descriptor);
        }
      }
    });
    return descriptors;
  }

  private SimpleDescriptor parseXmlDescriptor(String name, String xmlValue) {
    try {
      final SimpleDescriptorImpl descriptor = new SimpleDescriptorImpl();
      descriptor.setName(name);
      final String[] configurationPairs = xmlValue.split(";");
      for (String configurationPair : configurationPairs) {
        String[] parameterPairParts = configurationPair.trim().split("=", 2);
        String parameterName = parameterPairParts[0].trim();
        switch (parameterName) {
        case CONFIG_NAME_DISCOVERY_TYPE:
          descriptor.setDiscoveryType(parameterPairParts[1].trim());
          break;
        case CONFIG_NAME_DISCOVERY_ADDRESS:
          descriptor.setDiscoveryAddress(parameterPairParts[1].trim());
          break;
        case CONFIG_NAME_DISCOVERY_USER:
          descriptor.setDiscoveryUser(parameterPairParts[1].trim());
          break;
        case CONFIG_NAME_DISCOVERY_PASSWORD_ALIAS:
          descriptor.setDiscoveryPasswordAlias(parameterPairParts[1].trim());
          break;
        case CONFIG_NAME_DISCOVERY_CLUSTER:
          descriptor.setCluster(parameterPairParts[1].trim());
          break;
        case CONFIG_NAME_PROVIDER_CONFIG_REFERENCE:
          descriptor.setProviderConfig(parameterPairParts[1].trim());
          break;
        default:
          if (parameterName.startsWith(CONFIG_NAME_APPLICATION_PREFIX)) {
            parseApplication(descriptor, configurationPair.trim());
          } else {
            parseService(descriptor, configurationPair.trim());
          }
          break;
        }
      }

      final AdvancedServiceDiscoveryConfig advancedServiceDiscoveryConfig = advancedServiceDiscoveryConfigMap.get(name);
      if (advancedServiceDiscoveryConfig != null) {
        setDiscoveryDetails(descriptor, advancedServiceDiscoveryConfig);
        addEnabledServices(descriptor, advancedServiceDiscoveryConfig);
      }
      return descriptor;
    } catch (Exception e) {
      log.failedToParseDescriptor(name, e.getMessage(), e);
      return null;
    }
  }

  private void setDiscoveryDetails(SimpleDescriptorImpl descriptor, AdvancedServiceDiscoveryConfig advancedServiceDiscoveryConfig) {
    if (StringUtils.isBlank(descriptor.getDiscoveryAddress())) {
      descriptor.setDiscoveryAddress(advancedServiceDiscoveryConfig.getDiscoveryAddress());
    }

    if (StringUtils.isBlank(descriptor.getCluster())) {
      descriptor.setCluster(advancedServiceDiscoveryConfig.getDiscoveryCluster());
    }

    if (StringUtils.isBlank(descriptor.getDiscoveryType())) {
      descriptor.setDiscoveryType("ClouderaManager");
    }
  }

  /*
   * Adds any enabled service which is not listed in the CM descriptor
   */
  private void addEnabledServices(SimpleDescriptorImpl descriptor, AdvancedServiceDiscoveryConfig advancedServiceDiscoveryConfig) {
    advancedServiceDiscoveryConfig.getEnabledServiceNames().forEach(enabledServiceName -> {
      if (descriptor.getService(enabledServiceName) == null) {
        ServiceImpl service = new ServiceImpl();
        service.setName(enabledServiceName);
        descriptor.addService(service);
      }
    });
  }

  /**
   * An application consists of the following parts: <code>app:$APPLICATION_NAME[:$PARAMETER_NAME=$PARAMETER_VALUE]</code>. Parameters are
   * optional. <br>
   * Sample application configurations:
   * <ul>
   * <li>app:KNOX</li>
   * <li>app:knoxauth:param1.name=param1.value</li>
   * </ul>
   */
  private void parseApplication(SimpleDescriptorImpl descriptor, String configurationPair) {
    final String[] applicationParts = configurationPair.split(":");
    final String applicationName = applicationParts[1].trim();
    ApplicationImpl application = (ApplicationImpl) descriptor.getApplication(applicationName);
    if (application == null) {
      application = new ApplicationImpl();
      descriptor.addApplication(application);
      application.setName(applicationParts[1]);
    }

    if (applicationParts.length > 2) {
      // parameter value may contain ":" (for instance http://host:port) -> considering a parameter name/value pair everything after 'app:$APPLICATION_NAME:'
      final String applicationParameters = configurationPair.substring(applicationName.length() + 5); // 'app:' and trailing colon takes 5 chars
      final String[] applicationParameterParts = applicationParameters.split("=", 2);
      application.addParam(applicationParameterParts[0], applicationParameterParts[1]);
    }
  }

  /**
   * A service consists of the following parts:
   * <ul>
   * <li><code>$SERVICE_NAME</code></li>
   * <li><code>$SERVICE_NAME:url=$URL</code></li>
   * <li><code>$SERVICE_NAME:version=$VERSION</code> (optional)</li>
   * <li><code>$SERVICE_NAME[:$PARAMETER_NAME=$PARAMETER_VALUE] (optional)</code></li>
   * </ul>
   * Sample application configurations:
   * <ul>
   * <li>HIVE:url=http://localhost:123</li>
   * <li>HIVE:version=1.0</li>
   * <li>HIVE:param1.name=param1.value</li>
   * </ul>
   */
  private void parseService(SimpleDescriptorImpl descriptor, String configurationPair) {
    final String[] serviceParts = configurationPair.split(":");
    final String serviceName = serviceParts[0].trim();
    if (isServiceEnabled(descriptor.getName(), serviceName)) {
      ServiceImpl service = (ServiceImpl) descriptor.getService(serviceName);
      if (service == null) {
        service = new ServiceImpl();
        service.setName(serviceName);
        descriptor.addService(service);
      }

      if (serviceParts.length > 1) {
        // configuration value may contain ":" (for instance http://host:port) -> considering a configuration name/value pair everything after '$SERVICE_NAME:'
        final String serviceConfiguration = configurationPair.substring(serviceName.length() + 1).trim();
        final String[] serviceConfigurationParts = serviceConfiguration.split("=", 2);
        final String serviceConfigurationName = serviceConfigurationParts[0].trim();
        final String serviceConfigurationValue = serviceConfigurationParts[1].trim();
        switch (serviceConfigurationName) {
        case CONFIG_NAME_SERVICE_URL:
          service.addUrl(serviceConfigurationValue);
          break;
        case CONFIG_NAME_SERVICE_VERSION:
          service.setVersion(serviceConfigurationValue);
          break;
        default:
          service.addParam(serviceConfigurationName, serviceConfigurationValue);
          break;
        }
      }
    } else {
      log.serviceDisabled(serviceName, descriptor.getName());
    }
  }

  private boolean isServiceEnabled(String descriptorName, String serviceName) {
    return advancedServiceDiscoveryConfigMap.containsKey(descriptorName) ? advancedServiceDiscoveryConfigMap.get(descriptorName).isServiceEnabled(serviceName) : true;
  }

  @Override
  public void onAdvancedServiceDiscoveryConfigurationChange(Properties newConfiguration) {
    final AdvancedServiceDiscoveryConfig advancedServiceDiscoveryConfig = new AdvancedServiceDiscoveryConfig(newConfiguration);
    final String topologyName = advancedServiceDiscoveryConfig.getTopologyName();
    if (StringUtils.isBlank(topologyName)) {
      throw new IllegalArgumentException("Invalid advanced service discovery configuration: topology name is missing!");
    }
    advancedServiceDiscoveryConfigMap.put(topologyName, advancedServiceDiscoveryConfig);
    log.updatedAdvanceServiceDiscoverytConfiguration(topologyName);
  }

}

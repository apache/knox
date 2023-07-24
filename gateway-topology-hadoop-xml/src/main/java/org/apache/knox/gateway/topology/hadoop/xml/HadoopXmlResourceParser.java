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
package org.apache.knox.gateway.topology.hadoop.xml;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.topology.discovery.advanced.AdvancedServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.advanced.AdvancedServiceDiscoveryConfigChangeListener;
import org.apache.knox.gateway.topology.simple.JSONProviderConfiguration;
import org.apache.knox.gateway.topology.simple.JSONProviderConfiguration.JSONProvider;
import org.apache.knox.gateway.topology.simple.ProviderConfiguration;
import org.apache.knox.gateway.topology.simple.ProviderConfigurationParser;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor;
import org.apache.knox.gateway.topology.simple.SimpleDescriptorImpl;
import org.apache.knox.gateway.topology.simple.SimpleDescriptorImpl.ApplicationImpl;
import org.apache.knox.gateway.topology.simple.SimpleDescriptorImpl.ServiceImpl;

/**
 * Parses Knox descriptors and provider configurations from Hadoop style XML config
 *
 */
public class HadoopXmlResourceParser implements AdvancedServiceDiscoveryConfigChangeListener {
  private static final HadoopXmlResourceMessages log = MessagesFactory.get(HadoopXmlResourceMessages.class);

  //shared provider related constants
  private static final String CONFIG_NAME_PROVIDER_CONFIGS_PREFIX = "providerConfigs:";
  private static final String CONFIG_NAME_PROVIDER_CONFIGS_ROLE_PREFIX = "role=";
  private static final String CONFIG_NAME_PROVIDER_CONFIGS_NAME_PREFIX = "name=";
  private static final String CONFIG_NAME_PROVIDER_CONFIGS_ENABLED_PREFIX = "enabled=";
  private static final String CONFIG_NAME_PROVIDER_CONFIGS_PARAM_PREFIX = "param.";

  //descriptor related constants
  private static final String CONFIG_NAME_DISCOVERY_TYPE = "discoveryType";
  private static final String CONFIG_NAME_DISCOVERY_ADDRESS = "discoveryAddress";
  private static final String CONFIG_NAME_DISCOVERY_USER = "discoveryUser";
  private static final String CONFIG_NAME_DISCOVERY_PASSWORD_ALIAS = "discoveryPasswordAlias";
  private static final String CONFIG_NAME_DISCOVERY_CLUSTER = "cluster";
  private static final String CONFIG_NAME_PROVIDER_CONFIG_REFERENCE = "providerConfigRef";
  private static final String CONFIG_NAME_APPLICATION_PREFIX = "app";
  private static final String CONFIG_NAME_SERVICE_URL = "url";
  private static final String CONFIG_NAME_SERVICE_VERSION = "version";
  private static final String REMOVE = "remove";

  private final Map<String, AdvancedServiceDiscoveryConfig> advancedServiceDiscoveryConfigMap;
  private final String sharedProvidersDir;
  private final GatewayConfig gatewayConfig;

  public HadoopXmlResourceParser(GatewayConfig gatewayConfig) {
    this.gatewayConfig = gatewayConfig;
    this.advancedServiceDiscoveryConfigMap = new ConcurrentHashMap<>();
    this.sharedProvidersDir = gatewayConfig.getGatewayProvidersConfigDir();
  }

  /**
   * Produces a set of {@link SimpleDescriptor}s from the specified file.
   *
   * @param path
   *          The path to the configuration file which holds descriptor information in a pre-defined format.
   *          if set, the parser should only parse a descriptor with the same name
   * @return A SimpleDescriptor based on the contents of the given file.
   */
  public HadoopXmlResourceParserResult parse(String path) {
    try {
      log.parseHadoopXmlResource(path);
      final Configuration xmlConfiguration = new Configuration(false);
      xmlConfiguration.addResource(Paths.get(path).toUri().toURL());
      xmlConfiguration.reloadConfiguration();
      final HadoopXmlResourceParserResult parserResult = parseXmlConfig(xmlConfiguration);
      logParserResult(path, parserResult);
      return parserResult;
    } catch (Exception e) {
      log.failedToParseXmlConfiguration(path, e.getMessage(), e);
      return new HadoopXmlResourceParserResult();
    }
  }

  private void logParserResult(String path, final HadoopXmlResourceParserResult parserResult) {
    if (!parserResult.getDescriptors().isEmpty()) {
      log.foundKnoxDescriptors(String.join(", ", parserResult.getDescriptors().stream().map(SimpleDescriptor::getName).collect(Collectors.toSet())), path);
    }
    if (!parserResult.getProviders().isEmpty()) {
      log.foundKnoxProviderConfigurations(String.join(", ", new HashSet<>(parserResult.getProviders().keySet())), path);
    }
    if (!parserResult.getDeletedDescriptors().isEmpty()) {
      log.foundKnoxDeletedDescriptors(String.join(", ", parserResult.getDeletedDescriptors()), path);
    }
    if (!parserResult.getDeletedProviders().isEmpty()) {
      log.foundKnoxDeletedProviderConfigurations(String.join(", ", parserResult.getDeletedProviders()), path);
    }
  }

  private HadoopXmlResourceParserResult parseXmlConfig(Configuration xmlConfiguration) {
    final Map<String, ProviderConfiguration> providers = new LinkedHashMap<>();
    final Set<SimpleDescriptor> descriptors = new LinkedHashSet<>();
    Set<String> deletedDescriptors = new HashSet<>();
    Set<String> deletedProviders = new HashSet<>();
    xmlConfiguration.forEach(xmlDescriptor -> {
      String xmlConfigurationKey = xmlDescriptor.getKey();
      if (xmlConfigurationKey.startsWith(CONFIG_NAME_PROVIDER_CONFIGS_PREFIX)) {
        final String[] providerConfigurations = xmlConfigurationKey.replace(CONFIG_NAME_PROVIDER_CONFIGS_PREFIX, "").split(",");
        Arrays.stream(providerConfigurations).map(String::trim).forEach(providerConfigurationName ->
                parseProvider(providerConfigurationName, xmlDescriptor.getValue(), providers, deletedProviders));
      } else {
        parseDescriptor(xmlConfigurationKey, xmlDescriptor.getValue(), descriptors, deletedDescriptors);
      }
    });
    return new HadoopXmlResourceParserResult(providers, descriptors, deletedDescriptors, deletedProviders);
  }

  private void parseProvider(String providerConfigurationName, String value, Map<String, ProviderConfiguration> providers, Set<String> deletedProviders) {
    if (gatewayConfig.getReadOnlyOverrideProviderNames().contains(providerConfigurationName)) {
      log.skipReadOnlyProvider(providerConfigurationName);
      return;
    }
    final File providerConfigFile = resolveProviderConfiguration(providerConfigurationName);
    try {
      final ProviderConfiguration providerConfiguration = getProviderConfiguration(providers, providerConfigFile, providerConfigurationName);
      providerConfiguration.setReadOnly(true);
      if (isRemoved(value)) {
        deletedProviders.add(providerConfigurationName);
      } else {
        providerConfiguration.saveOrUpdateProviders(parseProviderConfigurations(value, providerConfiguration));
        providers.put(providerConfigurationName, providerConfiguration);
      }
    } catch (Exception e) {
      log.failedToParseProviderConfiguration(providerConfigurationName, e.getMessage(), e);
    }
  }

  private void parseDescriptor(String topologyName, String value, Set<SimpleDescriptor> descriptors, Set<String> deletedDescriptors) {
    if (gatewayConfig.getReadOnlyOverrideTopologyNames().contains(topologyName)) {
      log.skipReadOnlyDescriptor(topologyName);
      return;
    }
    if (isRemoved(value)) {
      deletedDescriptors.add(topologyName);
    } else {
      SimpleDescriptor descriptor = parseXmlDescriptor(topologyName, value);
      if (descriptor != null) {
        descriptors.add(descriptor);
      }
    }
  }

  private boolean isRemoved(String value) {
    return value.trim().equalsIgnoreCase(REMOVE);
  }

  private ProviderConfiguration getProviderConfiguration(Map<String, ProviderConfiguration> providers, File providerConfigFile, String providerConfigName)
      throws Exception {
    if (providers.containsKey(providerConfigName)) {
      return providers.get(providerConfigName);
    } else {
      return providerConfigFile == null ? new JSONProviderConfiguration() : ProviderConfigurationParser.parse(providerConfigFile);
    }
  }

  private File resolveProviderConfiguration(String providerConfigurationName) {
    for (String supportedExtension : ProviderConfigurationParser.SUPPORTED_EXTENSIONS) {
      File providerConfigFile = new File(sharedProvidersDir, providerConfigurationName + "." + supportedExtension);
      if (providerConfigFile.exists()) {
        return providerConfigFile;
      }
    }
    return null;
  }

  private Set<ProviderConfiguration.Provider> parseProviderConfigurations(String xmlValue, ProviderConfiguration providerConfiguration) {
    final Set<ProviderConfiguration.Provider> providers = new LinkedHashSet<>();
    final List<String> configurationPairs = Arrays.asList(xmlValue.split("#"));
    final Set<String> roles = configurationPairs.stream().filter(configurationPair -> configurationPair.trim().startsWith(CONFIG_NAME_PROVIDER_CONFIGS_ROLE_PREFIX))
        .map(configurationPair -> configurationPair.replace(CONFIG_NAME_PROVIDER_CONFIGS_ROLE_PREFIX, "").trim()).collect(Collectors.toSet());
    for (String role : roles) {
      providers.add(parseProvider(configurationPairs, role, providerConfiguration));
    }
    return providers;
  }

  private ProviderConfiguration.Provider parseProvider(List<String> configurationPairs, String role, ProviderConfiguration providerConfiguration) {
    final JSONProvider provider = new JSONProvider();
    provider.setRole(role);
    getParamsForRole(role, providerConfiguration).forEach(provider::addParam); //initializing parameters (if any)
    provider.setEnabled(true); //may be overwritten later, but defaulting to 'true'
    final Set<String> roleConfigurations = configurationPairs.stream().filter(configurationPair -> configurationPair.trim().startsWith(role))
        .map(configurationPair -> configurationPair.replace(role + ".", "").trim()).collect(Collectors.toSet());
    for (String roleConfiguration : roleConfigurations) {
      if (roleConfiguration.startsWith(CONFIG_NAME_PROVIDER_CONFIGS_PARAM_PREFIX)) {
        String[] paramKeyValue = roleConfiguration.replace(CONFIG_NAME_PROVIDER_CONFIGS_PARAM_PREFIX, "").split("=", 2);
        if (REMOVE.equals(paramKeyValue[0])) {
          provider.removeParam(paramKeyValue[1]);
        } else {
          provider.addParam(paramKeyValue[0], paramKeyValue[1]);
        }
      } else if (roleConfiguration.startsWith(CONFIG_NAME_PROVIDER_CONFIGS_NAME_PREFIX)) {
        provider.setName(roleConfiguration.replace(CONFIG_NAME_PROVIDER_CONFIGS_NAME_PREFIX, ""));
      } else if (roleConfiguration.startsWith(CONFIG_NAME_PROVIDER_CONFIGS_ENABLED_PREFIX)) {
        provider.setEnabled(Boolean.valueOf(roleConfiguration.replace(CONFIG_NAME_PROVIDER_CONFIGS_ENABLED_PREFIX, "")));
      } else {
        throw new IllegalArgumentException("Invalid role configuration: " + roleConfiguration + " in provider: " + provider.getName());
      }
    }
    return provider;
  }

  private Map<String, String> getParamsForRole(String role, ProviderConfiguration providerConfiguration) {
    final Optional<ProviderConfiguration.Provider> provider = providerConfiguration.getProviders().stream().filter(p -> p.getRole().equals(role)).findFirst();
    return provider.isPresent() ? provider.get().getParams() : new TreeMap<>();
  }

  private SimpleDescriptor parseXmlDescriptor(String name, String xmlValue) {
    try {
      final SimpleDescriptorImpl descriptor = new SimpleDescriptorImpl();
      descriptor.setReadOnly(true);
      descriptor.setName(name);
      final String[] configurationPairs = xmlValue.split("#");
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

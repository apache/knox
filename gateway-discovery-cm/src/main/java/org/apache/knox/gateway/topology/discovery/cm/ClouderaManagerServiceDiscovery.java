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
package org.apache.knox.gateway.topology.discovery.cm;

import com.cloudera.api.swagger.RolesResourceApi;
import com.cloudera.api.swagger.ServicesResourceApi;
import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiRoleConfig;
import com.cloudera.api.swagger.model.ApiRoleConfigList;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import com.cloudera.api.swagger.model.ApiServiceList;
import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.topology.ClusterConfigurationMonitorService;
import org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitor;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.cm.monitor.ClouderaManagerClusterConfigurationMonitor;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


/**
 * ClouderaManager-based service discovery implementation.
 */
public class ClouderaManagerServiceDiscovery implements ServiceDiscovery, ClusterConfigurationMonitor.ConfigurationChangeListener {

  static final String TYPE = "ClouderaManager";

  private static final ClouderaManagerServiceDiscoveryMessages log =
                                        MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  private static final GatewaySpiMessages LOGGER = MessagesFactory.get(GatewaySpiMessages.class);

  private static final String VIEW_SUMMARY     = "summary";
  private static final String VIEW_FULL        = "full";

  public static final String CM_SERVICE_TYPE  = "CM";
  public static final String CM_ROLE_TYPE  = "CM_SERVER";

  private static final ApiService CM_SERVICE = new ApiService()
  .name(CM_SERVICE_TYPE.toLowerCase(Locale.ROOT))
  .type(CM_SERVICE_TYPE);

  private static final ApiRoleConfigList CM_SERVICE_ROLE_CONFIGS = new ApiRoleConfigList()
  .addItemsItem(new ApiRoleConfig().name(CM_ROLE_TYPE).roleType(CM_ROLE_TYPE));

  public static final String CORE_SETTINGS_TYPE = "CORE_SETTINGS";

  private ServiceModelGeneratorsHolder serviceModelGeneratorsHolder = ServiceModelGeneratorsHolder.getInstance();

  private boolean debug;

  private AliasService aliasService;
  private KeyStore truststore;

  private ClouderaManagerClusterConfigurationMonitor configChangeMonitor;

  private final ClouderaManagerServiceDiscoveryRepository repository = ClouderaManagerServiceDiscoveryRepository.getInstance();

  private final AtomicInteger retryAttempts = new AtomicInteger(0);
  private final int retrySleepSeconds = 3;  // It's been agreed that we not expose this config
  private int maxRetryAttempts = -1;
  private Collection<String> excludedServiceTypes = Collections.emptySet();

  ClouderaManagerServiceDiscovery(GatewayConfig gatewayConfig) {
    this(false, gatewayConfig);
  }

  ClouderaManagerServiceDiscovery(boolean debug, GatewayConfig gatewayConfig) {
    GatewayServices gwServices = GatewayServer.getGatewayServices();
    if (gwServices != null) {
      this.aliasService = gwServices.getService(ServiceType.ALIAS_SERVICE);
      KeystoreService keystoreService = gwServices.getService(ServiceType.KEYSTORE_SERVICE);
      if (keystoreService != null) {
        try {
          truststore = keystoreService.getTruststoreForHttpClient();
        } catch (KeystoreServiceException e) {
          LOGGER.failedToLoadTruststore(e.getMessage(), e);
        }
      }
    }
    this.debug = debug;
    this.configChangeMonitor = getConfigurationChangeMonitor();

    if (gatewayConfig != null) {
      repository.setCacheEntryTTL(gatewayConfig.getClouderaManagerServiceDiscoveryRepositoryEntryTTL());
      configureRetryParams(gatewayConfig);
      excludedServiceTypes = getLowercaseStringCollection(gatewayConfig.getClouderaManagerServiceDiscoveryExcludedServiceTypes());
    }
  }

  private Collection<String> getLowercaseStringCollection(Collection<String> original) {
    return original == null ? Collections.emptySet() : original.stream().map(serviceType -> serviceType.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
  }

  private void configureRetryParams(GatewayConfig gatewayConfig) {
    final int configuredMaxRetryAttempts = gatewayConfig.getClouderaManagerServiceDiscoveryMaximumRetryAttempts();
    if (configuredMaxRetryAttempts > 0) {
      final int configuredRetryDurationSeconds = configuredMaxRetryAttempts * retrySleepSeconds;
      final int pollingInterval = gatewayConfig.getClusterMonitorPollingInterval(ClouderaManagerClusterConfigurationMonitor.getType());
      final int retryDurationLimit = pollingInterval / 2;
      if (retryDurationLimit > configuredRetryDurationSeconds) {
        this.maxRetryAttempts = configuredMaxRetryAttempts;
      } else {
        this.maxRetryAttempts = retryDurationLimit / retrySleepSeconds;
        log.updateMaxRetryAttempts(configuredMaxRetryAttempts, maxRetryAttempts);
      }
    }
  }

  @Override
  public String getType() {
    return TYPE;
  }

  private DiscoveryApiClient getClient(GatewayConfig gatewayConfig, ServiceDiscoveryConfig discoveryConfig) {
    String discoveryAddress = discoveryConfig.getAddress();
    if (discoveryAddress == null || discoveryAddress.isEmpty()) {
      log.missingDiscoveryAddress();
      throw new IllegalArgumentException("Missing or invalid discovery address.");
    }

    DiscoveryApiClient client = new DiscoveryApiClient(gatewayConfig, discoveryConfig, aliasService, truststore);
    client.setDebugging(debug);
    return client;
  }

  /**
   * Get the ClouderaManager configuration change monitor from the associated gateway service.
   */
  private ClouderaManagerClusterConfigurationMonitor getConfigurationChangeMonitor() {
    ClouderaManagerClusterConfigurationMonitor cmMonitor = null;

    try {
      GatewayServices gwServices = GatewayServer.getGatewayServices();
      if (gwServices != null) {
        ClusterConfigurationMonitorService clusterMonitorService =
            GatewayServer.getGatewayServices().getService(ServiceType.CLUSTER_CONFIGURATION_MONITOR_SERVICE);
        ClusterConfigurationMonitor monitor =
            clusterMonitorService.getMonitor(ClouderaManagerClusterConfigurationMonitor.getType());
        if (monitor != null && ClouderaManagerClusterConfigurationMonitor.class.isAssignableFrom(monitor.getClass())) {
          cmMonitor = (ClouderaManagerClusterConfigurationMonitor) monitor;
          cmMonitor.addListener(this);
        }
      }
    } catch (Exception e) {
      log.errorAccessingConfigurationChangeMonitor(e);
    }
    return cmMonitor;
  }

  @Override
  public ClouderaManagerCluster discover(GatewayConfig          gatewayConfig,
                                         ServiceDiscoveryConfig discoveryConfig,
                                         String                 clusterName) {
    return discover(gatewayConfig, discoveryConfig, clusterName, Collections.emptySet());
  }

  @Override
  public ClouderaManagerCluster discover(GatewayConfig          gatewayConfig,
                                         ServiceDiscoveryConfig discoveryConfig,
                                         String                 clusterName,
                                         Collection<String>     includedServices) {
    return discover(gatewayConfig, discoveryConfig, clusterName, includedServices, getClient(gatewayConfig, discoveryConfig));
  }

  protected ClouderaManagerCluster discover(GatewayConfig          gatewayConfig,
                                            ServiceDiscoveryConfig discoveryConfig,
                                            String                 clusterName,
                                            Collection<String>     includedServices,
                                            DiscoveryApiClient     client) {
    ClouderaManagerCluster cluster = null;

    if (clusterName == null || clusterName.isEmpty()) {
      log.missingDiscoveryCluster();
      throw new IllegalArgumentException("The cluster configuration is missing from, or invalid in, the discovery configuration.");
    }

    try {
        cluster = discoverCluster(gatewayConfig, client, clusterName, includedServices);

        if (configChangeMonitor != null && cluster != null) {
        // Notify the cluster config monitor about these cluster configuration details
        configChangeMonitor.addServiceConfiguration(cluster, discoveryConfig);
      }
      resetRetryAttempts();
    } catch (ApiException e) {
      log.clusterDiscoveryError(clusterName, e);
      if (shouldRetryServiceDiscovery(e)) {
        log.retryDiscovery(retrySleepSeconds, retryAttempts.get());
        try {
          Thread.sleep(retrySleepSeconds * 1000L);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
        cluster = discover(gatewayConfig, discoveryConfig, clusterName, includedServices, client);
      } else {
        resetRetryAttempts();
      }
    }

    return cluster;
  }

  private void resetRetryAttempts() {
    retryAttempts.set(0);
  }

  private boolean shouldRetryServiceDiscovery(ApiException e) {
    if (maxRetryAttempts > 0 && maxRetryAttempts > retryAttempts.getAndIncrement()) {
      final Throwable cause = e.getCause();
      if (cause != null) {
        if (SocketException.class.isAssignableFrom(cause.getClass())
                || SocketTimeoutException.class.isAssignableFrom(cause.getClass())
                || UnknownHostException.class.isAssignableFrom(cause.getClass())) {
          return true;
        }
      }
    }
    return false;
  }

  private ClouderaManagerCluster discoverCluster(GatewayConfig gatewayConfig, DiscoveryApiClient client,
                                                 String clusterName, Collection<String> includedServices)
      throws ApiException {
    ServicesResourceApi servicesResourceApi = new ServicesResourceApi(client);
    RolesResourceApi rolesResourceApi = new RolesResourceApi(client);
    ServiceRoleCollector roleCollector =
            ServiceRoleCollectorBuilder.newBuilder()
                    .gatewayConfig(gatewayConfig)
                    .rolesResourceApi(rolesResourceApi)
                    .build();

    log.discoveringCluster(clusterName);

    List<ApiService> serviceList = getClusterServices(client.getConfig(), servicesResourceApi);

      // if Legacy Cloudera Manager API Clients Compatibility is turned off, some HDFS settings are in CORE_SETTINGS
    ApiServiceConfig coreSettingsConfig = coreSettingsConfig(client, servicesResourceApi, serviceList);

    Set<ServiceModel> serviceModels = new HashSet<>();
    for (ApiService service : serviceList) {
        serviceModels.addAll(
          discoverService(client, clusterName, includedServices, service, servicesResourceApi, roleCollector, coreSettingsConfig));
    }

    ClouderaManagerCluster cluster = new ClouderaManagerCluster(clusterName);
    cluster.addServiceModels(serviceModels);
    log.discoveredCluster(clusterName);
    return cluster;
  }

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private Set<ServiceModel> discoverService(DiscoveryApiClient client, String clusterName, Collection<String> includedServices,
                                            ApiService service, ServicesResourceApi servicesResourceApi,
                                            ServiceRoleCollector roleCollector, ApiServiceConfig coreSettingsConfig) throws ApiException {
    Set<ServiceModel> serviceModels = new HashSet<>();
    final List<ServiceModelGenerator> modelGenerators = serviceModelGeneratorsHolder.getServiceModelGenerators(service.getType());
    //if (shouldSkipServiceDiscovery(modelGenerators, includedServices)) {
      //log.skipServiceDiscovery(service.getName(), service.getType());
      //continue;
    //}
    log.discoveringService(service.getName(), service.getType());
    ApiServiceConfig serviceConfig = null;
    /* no reason to check service config for CM or CORE_SETTINGS services */
    if (!CM_SERVICE_TYPE.equals(service.getType()) && !CORE_SETTINGS_TYPE.equals(service.getType())) {
      serviceConfig = getServiceConfig(client.getConfig(), servicesResourceApi, service);
    }
    ApiRoleConfigList roleConfigList = getAllServiceRoleConfigurations(client.getConfig(), roleCollector, clusterName, service);
    if (roleConfigList != null && roleConfigList.getItems() != null) {
      for (ApiRoleConfig roleConfig : roleConfigList.getItems()) {
        ApiRole role = new ApiRole()
        .name(roleConfig.getName())
        .type(roleConfig.getRoleType())
        .hostRef(roleConfig.getHostRef());
        ApiConfigList roleConfigs = roleConfig.getConfig();
        ServiceRoleDetails serviceRoleDetails = new ServiceRoleDetails(service, serviceConfig, role, roleConfigs);

        serviceModels.addAll(generateServiceModels(client, serviceRoleDetails, coreSettingsConfig, modelGenerators));
      }
    }

    log.discoveredService(service.getName(), service.getType());
    return serviceModels;
  }

  private Set<ServiceModel> generateServiceModels(DiscoveryApiClient client, ServiceRoleDetails serviceRoleDetails, ApiServiceConfig coreSettingsConfig, List<ServiceModelGenerator> modelGenerators) throws ApiException {
    Set<ServiceModel> serviceModels = new HashSet<>();
    log.discoveringServiceRole(serviceRoleDetails.getRole().getName(), serviceRoleDetails.getRole().getType());

    if (modelGenerators != null) {
      for (ServiceModelGenerator serviceModelGenerator : modelGenerators) {
        ServiceModel serviceModel = generateServiceModel(client, serviceRoleDetails, coreSettingsConfig, serviceModelGenerator);
        if (serviceModel != null) {
          serviceModels.add(serviceModel);
        }
      }
    }

    log.discoveredServiceRole(serviceRoleDetails.getRole().getName(), serviceRoleDetails.getRole().getType());
    return serviceModels;
  }

  private static ServiceModel generateServiceModel(DiscoveryApiClient client, ServiceRoleDetails sd,
                                                   ApiServiceConfig coreSettingsConfig, ServiceModelGenerator serviceModelGenerator) throws ApiException {
    ServiceModelGeneratorHandleResponse response = serviceModelGenerator.handles(sd.getService(), sd.getServiceConfig(), sd.getRole(), sd.getRoleConfig());
    if (response.handled()) {
      serviceModelGenerator.setApiClient(client);
      return serviceModelGenerator.generateService(sd.getService(), sd.getServiceConfig(), sd.getRole(), sd.getRoleConfig(), coreSettingsConfig);
    } else if (!response.getConfigurationIssues().isEmpty()) {
      log.serviceRoleHasConfigurationIssues(sd.getRole().getName(), String.join(";", response.getConfigurationIssues()));
    }
    return null;
  }

  private ApiServiceConfig coreSettingsConfig(DiscoveryApiClient client, ServicesResourceApi servicesResourceApi, List<ApiService> serviceList) throws ApiException {
    for (ApiService service : serviceList) {
      if (CORE_SETTINGS_TYPE.equals(service.getType())) {
        return getServiceConfig(client.getConfig(), servicesResourceApi, service);
      }
    }
    return null;
  }

  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean shouldSkipServiceDiscovery(List<ServiceModelGenerator> modelGenerators, Collection<String> includedServices) {
    if (includedServices == null || includedServices.isEmpty()) {
      // per the contract of org.apache.knox.gateway.topology.discovery.ServiceDiscovery.discover(GatewayConfig, ServiceDiscoveryConfig, String, Collection<String>):
      // if included services is null or empty -> discover all services in the given cluster
      return false;
    }

    if (modelGenerators != null) {
      for (ServiceModelGenerator modelGenerator : modelGenerators) {
        if (includedServices.contains(modelGenerator.getService())) {
          return false;
        }
      }
    }
    return true;
  }

  private List<ApiService> getClusterServices(ServiceDiscoveryConfig serviceDiscoveryConfig, ServicesResourceApi servicesResourceApi) throws ApiException {
    log.lookupClusterServicesFromRepository();
    List<ApiService> services = repository.getServices(serviceDiscoveryConfig);
    if (services.isEmpty()) {
      try {
        log.lookupClusterServicesFromCM();
        final ApiServiceList serviceList = servicesResourceApi.readServices(serviceDiscoveryConfig.getCluster(), VIEW_SUMMARY);
        services = serviceList == null ? new ArrayList<>() : serviceList.getItems();

        services = services.stream().filter(service -> {
          if (excludedServiceTypes.contains(service.getType().toLowerCase(Locale.ROOT))) {
            log.skipServiceDiscovery(service.getName(), service.getType());
            return false;
          }
          return true;
        }).collect(Collectors.toList());

        // make sure that services are populated in the repository
        services.forEach(service -> repository.addService(serviceDiscoveryConfig, service));
      } catch (ApiException e) {
        log.failedToAccessServiceConfigs(serviceDiscoveryConfig.getCluster(), e);
        throw e;
      }
    }
    /*
     Since Cloudera Manager does not have a service for itself, we will add a skeleton CM
     service so that we can add CM service to topology when auto-discovery is
     turned on and CM service is selected in the descriptor
    */
    services.add(CM_SERVICE);
    return services;
  }

  private ApiServiceConfig getServiceConfig(ServiceDiscoveryConfig serviceDiscoveryConfig, ServicesResourceApi servicesResourceApi, ApiService service) throws ApiException {
    log.lookupServiceConfigsFromRepository();
    // first, try in the service discovery repository
    ApiServiceConfig serviceConfig = repository.getServiceConfig(serviceDiscoveryConfig, service);

    if (serviceConfig == null) {
      // no service config in the repository -> query CM
      try {
        log.lookupServiceConfigsFromCM();
        serviceConfig = servicesResourceApi.readServiceConfig(serviceDiscoveryConfig.getCluster(), service.getName(), VIEW_FULL);

        // make sure that service config is populated in the service discovery repository to avoid subsequent CM calls
        repository.addServiceConfig(serviceDiscoveryConfig, service, serviceConfig);
      } catch (ApiException e) {
        log.failedToAccessServiceConfigs(serviceDiscoveryConfig.getCluster(), e);
        throw e;
      }
    }
    return serviceConfig;
  }

  private ApiRoleConfigList getAllServiceRoleConfigurations(ServiceDiscoveryConfig serviceDiscoveryConfig,
                                                            ServiceRoleCollector roleCollector,
                                                            String clusterName, ApiService service) throws ApiException {
    log.lookupRolesFromRepository();
    //first, try in the service discovery repository
    ApiRoleConfigList roleConfigs = repository.getServiceRoleConfigs(serviceDiscoveryConfig, service);
    if (roleConfigs == null) {
      // no roles in the repository -> query CM
      final String serviceName = service.getName();
      try {
        /* Populate roles for CM Service since they are not discoverable */
        if (CM_SERVICE_TYPE.equalsIgnoreCase(serviceName)) {
          roleConfigs  = CM_SERVICE_ROLE_CONFIGS;
        } else if (CORE_SETTINGS_TYPE.equalsIgnoreCase(serviceName)) { //CORE_SETTINGS has no roles, it does not make sense to discover them
          log.noRoles();
        } else {
          log.lookupRoleConfigsFromCM();
          roleConfigs = roleCollector.getAllServiceRoleConfigurations(clusterName, serviceName);
        }

        // make sure that role is populated in the service discovery repository to avoid subsequent CM calls
        if (roleConfigs != null) {
          repository.setServiceRoleConfigs(serviceDiscoveryConfig, service, roleConfigs);
        }
      } catch (ApiException e) {
        log.failedToAccessServiceRoleConfigs(serviceName, "N/A", clusterName, e);
        throw e;
      }
    }

    return roleConfigs;
  }

  @Override
  public void onConfigurationChange(String source, String clusterName) {
    log.clearServiceDiscoveryRepository();
    repository.clear();
  }

  private static class ServiceRoleDetails {
    private final ApiService service;
    private final ApiServiceConfig serviceConfig;
    private final ApiRole role;
    private final ApiConfigList roleConfig;

    ServiceRoleDetails(ApiService service, ApiServiceConfig serviceConfig, ApiRole role, ApiConfigList roleConfig) {
      this.service = service;
      this.serviceConfig = serviceConfig;
      this.role = role;
      this.roleConfig = roleConfig;
    }

    public ApiService getService() {
      return service;
    }

    public ApiServiceConfig getServiceConfig() {
      return serviceConfig;
    }

    public ApiRole getRole() {
      return role;
    }

    public ApiConfigList getRoleConfig() {
      return roleConfig;
    }
  }
}

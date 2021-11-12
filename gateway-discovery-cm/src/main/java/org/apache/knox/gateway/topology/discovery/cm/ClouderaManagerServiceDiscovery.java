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
import com.cloudera.api.swagger.model.ApiRoleList;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import com.cloudera.api.swagger.model.ApiServiceList;
import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.topology.ClusterConfigurationMonitorService;
import org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitor;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.cm.monitor.ClouderaManagerClusterConfigurationMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;


/**
 * ClouderaManager-based service discovery implementation.
 */
public class ClouderaManagerServiceDiscovery implements ServiceDiscovery {

  static final String TYPE = "ClouderaManager";

  private static final ClouderaManagerServiceDiscoveryMessages log =
                                        MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  static final String API_PATH = "api/v32";

  private static final String VIEW_SUMMARY     = "summary";
  private static final String VIEW_FULL        = "full";

  static final String DEFAULT_USER_ALIAS = "cm.discovery.user";
  static final String DEFAULT_PWD_ALIAS  = "cm.discovery.password";

  public static final String CM_SERVICE_TYPE  = "CM";
  public static final String CM_ROLE_TYPE  = "CM_SERVER";

  private static Map<String, List<ServiceModelGenerator>> serviceModelGenerators = new HashMap<>();
  static {
    ServiceLoader<ServiceModelGenerator> loader = ServiceLoader.load(ServiceModelGenerator.class);
    for (ServiceModelGenerator serviceModelGenerator : loader) {
      List<ServiceModelGenerator> smgList =
          serviceModelGenerators.computeIfAbsent(serviceModelGenerator.getServiceType(), k -> new ArrayList<>());
      smgList.add(serviceModelGenerator);
    }
  }

  private boolean debug;

  private AliasService aliasService;
  private KeystoreService keystoreService;

  private ClouderaManagerClusterConfigurationMonitor configChangeMonitor;


  ClouderaManagerServiceDiscovery() {
    this(false);
  }

  ClouderaManagerServiceDiscovery(boolean debug) {
    GatewayServices gwServices = GatewayServer.getGatewayServices();
    if (gwServices != null) {
      this.aliasService = gwServices.getService(ServiceType.ALIAS_SERVICE);
      this.keystoreService = gwServices.getService(ServiceType.KEYSTORE_SERVICE);
    }
    this.debug = debug;
    this.configChangeMonitor = getConfigurationChangeMonitor();
  }

  @Override
  public String getType() {
    return TYPE;
  }

  private DiscoveryApiClient getClient(ServiceDiscoveryConfig discoveryConfig) {
    String discoveryAddress = discoveryConfig.getAddress();
    if (discoveryAddress == null || discoveryAddress.isEmpty()) {
      log.missingDiscoveryAddress();
      throw new IllegalArgumentException("Missing or invalid discovery address.");
    }

    DiscoveryApiClient client = new DiscoveryApiClient(discoveryConfig, aliasService, keystoreService);
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
    return discover(discoveryConfig, clusterName, getClient(discoveryConfig));
  }

  protected ClouderaManagerCluster discover(ServiceDiscoveryConfig discoveryConfig,
                                            String clusterName,
                                            DiscoveryApiClient client) {
    ClouderaManagerCluster cluster = null;

    if (clusterName == null || clusterName.isEmpty()) {
      log.missingDiscoveryCluster();
      throw new IllegalArgumentException("The cluster configuration is missing from, or invalid in, the discovery configuration.");
    }

    try {
      cluster = discoverCluster(client, clusterName);

      if (configChangeMonitor != null && cluster != null) {
        // Notify the cluster config monitor about these cluster configuration details
        configChangeMonitor.addServiceConfiguration(cluster, discoveryConfig);
      }
    } catch (ApiException e) {
      log.clusterDiscoveryError(clusterName, e);
    }

    return cluster;
  }

  private static ClouderaManagerCluster discoverCluster(DiscoveryApiClient client, String clusterName)
      throws ApiException {
    ServicesResourceApi servicesResourceApi = new ServicesResourceApi(client);
    RolesResourceApi rolesResourceApi = new RolesResourceApi(client);

    log.discoveringCluster(clusterName);

    Set<ServiceModel> serviceModels = new HashSet<>();

    ApiServiceList serviceList = getClusterServices(servicesResourceApi, clusterName);

    if (serviceList != null) {
      /*
      Since Cloudera Manager does not have a service for itself, we will add a skeleton CM
      service so that we can add CM service to topology when auto-discovery is
      turned on and CM service is selected in the descriptor
      */
      final ApiService cmService = new ApiService();
      cmService.setName(CM_SERVICE_TYPE.toLowerCase(Locale.ROOT));
      cmService.setType(CM_SERVICE_TYPE);
      serviceList.addItemsItem(cmService);

      for (ApiService service : serviceList.getItems()) {
        String serviceName = service.getName();
        log.discoveredService(serviceName, service.getType());
        ApiServiceConfig serviceConfig = null;
        /* no reason to check service config for CM service */
        if(!CM_SERVICE_TYPE.equals(service.getType())) {
          serviceConfig =
              getServiceConfig(servicesResourceApi, clusterName, serviceName);
        }
        ApiRoleList roleList = getRoles(rolesResourceApi, clusterName, serviceName);
        if (roleList != null) {
          for (ApiRole role : roleList.getItems()) {
            String roleName = role.getName();
            log.discoveredServiceRole(roleName, role.getType());
            ApiConfigList roleConfig = null;
            /* no reason to check role config for CM service */
            if(!CM_SERVICE_TYPE.equals(service.getType())) {
              roleConfig =
                  getRoleConfig(rolesResourceApi, clusterName, serviceName, roleName);
            }

            List<ServiceModelGenerator> smgList = serviceModelGenerators.get(service.getType());
            if (smgList != null) {
              for (ServiceModelGenerator serviceModelGenerator : smgList) {
                ServiceModelGeneratorHandleResponse response = serviceModelGenerator.handles(service, serviceConfig, role, roleConfig);
                if (response.handled()) {
                  serviceModelGenerator.setApiClient(client);
                  ServiceModel serviceModel = serviceModelGenerator.generateService(service, serviceConfig, role, roleConfig);
                  serviceModels.add(serviceModel);
                } else if (!response.getConfigurationIssues().isEmpty()) {
                  log.serviceRoleHasConfigurationIssues(roleName, String.join(";", response.getConfigurationIssues()));
                }
              }
            }
          }
        }
      }
      ClouderaManagerCluster cluster = new ClouderaManagerCluster(clusterName);
      cluster.addServiceModels(serviceModels);
      return cluster;
    }
    return null;
  }

  private static ApiServiceList getClusterServices(final ServicesResourceApi servicesResourceApi,
                                                   final String              clusterName) {
    ApiServiceList services = null;
    try {
      services = servicesResourceApi.readServices(clusterName, VIEW_SUMMARY);
    } catch (ApiException e) {
      log.failedToAccessServiceConfigs(clusterName, e);
    }
    return services;
  }

  private static ApiServiceConfig getServiceConfig(final ServicesResourceApi servicesResourceApi,
                                                   final String clusterName,
                                                   final String serviceName) {
    ApiServiceConfig serviceConfig = null;
    try {
      serviceConfig = servicesResourceApi.readServiceConfig(clusterName, serviceName, VIEW_FULL);
    } catch (Exception e) {
      log.failedToAccessServiceConfigs(clusterName, e);
    }
    return serviceConfig;
  }

  private static ApiRoleList getRoles(RolesResourceApi rolesResourceApi,
                                      String clusterName,
                                      String serviceName) {
    ApiRoleList roles = null;
    try {
      /* Populate roles for CM Service since they are not discoverable */
      if(CM_SERVICE_TYPE.equalsIgnoreCase(serviceName)) {
        roles = new ApiRoleList();
        final ApiRole cmRole = new ApiRole();
        cmRole.setName(CM_ROLE_TYPE);
        cmRole.setType(CM_ROLE_TYPE);
        roles.addItemsItem(cmRole);
        return roles;
      } else {
        roles = rolesResourceApi.readRoles(clusterName, serviceName, "", VIEW_SUMMARY);
      }
    } catch (Exception e) {
      log.failedToAccessServiceRoleConfigs(clusterName, e);
    }
    return roles;
  }

  private static ApiConfigList getRoleConfig(RolesResourceApi rolesResourceApi,
                                             String           clusterName,
                                             String           serviceName,
                                             String           roleName) {
    ApiConfigList configList = null;
    try {
      configList = rolesResourceApi.readRoleConfig(clusterName, roleName, serviceName, VIEW_FULL);
    } catch (Exception e) {
      log.failedToAccessServiceRoleConfigs(clusterName, e);
    }
    return configList;
  }

}

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

import com.cloudera.api.swagger.ClustersResourceApi;
import com.cloudera.api.swagger.RolesResourceApi;
import com.cloudera.api.swagger.ServicesResourceApi;
import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiCluster;
import com.cloudera.api.swagger.model.ApiClusterList;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiRoleList;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import com.cloudera.api.swagger.model.ApiServiceList;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.topology.discovery.GatewayService;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

  private static final String CLUSTER_TYPE_ANY = "any";
  private static final String VIEW_SUMMARY     = "summary";
  private static final String VIEW_FULL        = "full";

  static final String DEFAULT_USER_ALIAS = "cm.discovery.user";
  static final String DEFAULT_PWD_ALIAS  = "cm.discovery.password";

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

  @GatewayService
  private AliasService aliasService;


  ClouderaManagerServiceDiscovery() {
    this(false);
  }

  ClouderaManagerServiceDiscovery(boolean debug) {
    this.debug = debug;
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

    DiscoveryApiClient client = new DiscoveryApiClient(discoveryConfig, aliasService);
    client.setDebugging(debug);
    client.setVerifyingSsl(false);
    return client;
  }

  @Override
  public Map<String, Cluster> discover(GatewayConfig gatewayConfig, ServiceDiscoveryConfig discoveryConfig) {
    Map<String, Cluster> clusters = new HashMap<>();

    DiscoveryApiClient client = getClient(discoveryConfig);
    List<ApiCluster> apiClusters = getClusters(client);
    for (ApiCluster apiCluster : apiClusters) {
      String clusterName = apiCluster.getName();
      log.discoveredCluster(clusterName, apiCluster.getFullVersion());

      Cluster cluster = discover(gatewayConfig, discoveryConfig, clusterName, client);
      clusters.put(clusterName, cluster);
    }

    return clusters;
  }

  @Override
  public Cluster discover(GatewayConfig gatewayConfig, ServiceDiscoveryConfig discoveryConfig, String clusterName) {
    return discover(gatewayConfig, discoveryConfig, clusterName, getClient(discoveryConfig));
  }

  protected Cluster discover(GatewayConfig          gatewayConfig,
                             ServiceDiscoveryConfig discoveryConfig,
                             String                 clusterName,
                             DiscoveryApiClient     client) {
    ServiceDiscovery.Cluster cluster = null;

    if (clusterName == null || clusterName.isEmpty()) {
      log.missingDiscoveryCluster();
      throw new IllegalArgumentException("The cluster configuration is missing from, or invalid in, the discovery configuration.");
    }

    try {
      cluster = discoverCluster(client, clusterName);
    } catch (ApiException e) {
      log.clusterDiscoveryError(clusterName, e);
    }

    return cluster;
  }

  private static List<ApiCluster> getClusters(DiscoveryApiClient client) {
    List<ApiCluster> clusters = new ArrayList<>();
    try {
      ClustersResourceApi clustersResourceApi = new ClustersResourceApi(client);
      ApiClusterList clusterList = clustersResourceApi.readClusters(CLUSTER_TYPE_ANY, VIEW_SUMMARY);
      if (clusterList != null) {
        clusters.addAll(clusterList.getItems());
      }
    } catch (Exception e) {
      log.clusterDiscoveryError(CLUSTER_TYPE_ANY, e);
    }
    return clusters;
  }

  private static Cluster discoverCluster(DiscoveryApiClient client, String clusterName) throws ApiException {
    ClouderaManagerCluster cluster = null;

    ServicesResourceApi servicesResourceApi = new ServicesResourceApi(client);
    RolesResourceApi rolesResourceApi = new RolesResourceApi(client);

    log.discoveringCluster(clusterName);

    cluster = new ClouderaManagerCluster(clusterName);

    Set<ServiceModel> serviceModels = new HashSet<>();

    ApiServiceList serviceList = getClusterServices(servicesResourceApi, clusterName);
    if (serviceList != null) {
      for (ApiService service : serviceList.getItems()) {
        String serviceName = service.getName();
        log.discoveredService(serviceName, service.getType());
        ApiServiceConfig serviceConfig =
            getServiceConfig(servicesResourceApi, clusterName, serviceName);
        ApiRoleList roleList = getRoles(rolesResourceApi, clusterName, serviceName);
        if (roleList != null) {
          for (ApiRole role : roleList.getItems()) {
            String roleName = role.getName();
            log.discoveredServiceRole(roleName, role.getType());
            ApiConfigList roleConfig =
                getRoleConfig(rolesResourceApi, clusterName, serviceName, roleName);

            List<ServiceModelGenerator> smgList = serviceModelGenerators.get(service.getType());
            if (smgList != null) {
              for (ServiceModelGenerator serviceModelGenerator : smgList) {
                if (serviceModelGenerator != null) {
                  if (serviceModelGenerator.handles(service, serviceConfig, role, roleConfig)) {
                    serviceModelGenerator.setApiClient(client);
                    ServiceModel serviceModel = serviceModelGenerator.generateService(service, serviceConfig, role, roleConfig);
                    serviceModels.add(serviceModel);
                  }
                }
              }
            }
          }
        }
      }
    }

    cluster.addServiceModels(serviceModels);

    return cluster;
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
      roles = rolesResourceApi.readRoles(clusterName, serviceName, "", VIEW_SUMMARY);
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

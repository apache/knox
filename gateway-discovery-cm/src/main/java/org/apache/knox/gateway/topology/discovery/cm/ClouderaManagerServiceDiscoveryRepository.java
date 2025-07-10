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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;

import com.cloudera.api.swagger.model.ApiRoleConfigList;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

class ClouderaManagerServiceDiscoveryRepository {

  private static final ClouderaManagerServiceDiscoveryRepository INSTANCE = new ClouderaManagerServiceDiscoveryRepository();
  private final Map<RepositoryKey, Cache<ApiService, ServiceDetails>> repository;
  private long cacheEntryTTL = GatewayConfig.DEFAULT_CM_SERVICE_DISCOVERY_CACHE_ENTRY_TTL;

  private ClouderaManagerServiceDiscoveryRepository() {
    this.repository = new ConcurrentHashMap<>();
  }

  static ClouderaManagerServiceDiscoveryRepository getInstance() {
    return INSTANCE;
  }

  void setCacheEntryTTL(long cacheEntryTTL) {
    this.cacheEntryTTL = cacheEntryTTL;
  }

  void clear() {
    repository.clear();
  }

  void addService(ServiceDiscoveryConfig serviceDiscoveryConfig, ApiService service) {
    getClusterServices(serviceDiscoveryConfig).put(service, new ServiceDetails());
  }

  List<ApiService> getServices(ServiceDiscoveryConfig serviceDiscoveryConfig) {
    final Cache<ApiService, ServiceDetails> clusterServices = getClusterServices(serviceDiscoveryConfig);
    return clusterServices.asMap().entrySet().stream().filter(entry -> entry.getValue() != null).map(entry -> entry.getKey())
            .collect(Collectors.toList());
  }

  private Cache<ApiService, ServiceDetails> getClusterServices(ServiceDiscoveryConfig serviceDiscoveryConfig) {
    return repository.computeIfAbsent(RepositoryKey.of(serviceDiscoveryConfig), (k) -> Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(cacheEntryTTL)).build());
  }

  void addServiceConfig(ServiceDiscoveryConfig serviceDiscoveryConfig, ApiService service, ApiServiceConfig serviceConfig) {
    final ServiceDetails serviceDetails = getServiceDetails(serviceDiscoveryConfig, service);
    if (serviceDetails != null) {
      serviceDetails.setServiceConfig(serviceConfig);
    }
  }

  private ServiceDetails getServiceDetails(ServiceDiscoveryConfig serviceDiscoveryConfig, ApiService service) {
    return getClusterServices(serviceDiscoveryConfig).getIfPresent(service);
  }

  ApiServiceConfig getServiceConfig(ServiceDiscoveryConfig serviceDiscoveryConfig, ApiService service) {
    final ServiceDetails serviceDetails = getServiceDetails(serviceDiscoveryConfig, service);
    return serviceDetails == null ? null : serviceDetails.getServiceConfig();
  }

  void setServiceRoleConfigs(ServiceDiscoveryConfig serviceDiscoveryConfig, ApiService service, ApiRoleConfigList roleConfigs) {
    final ServiceDetails serviceDetails = getServiceDetails(serviceDiscoveryConfig, service);
    if (serviceDetails != null) {
      serviceDetails.setAllServiceRoleConfigs(roleConfigs);
    }
  }

  ApiRoleConfigList getServiceRoleConfigs(ServiceDiscoveryConfig serviceDiscoveryConfig, ApiService service) {
    final ServiceDetails serviceDetails = getServiceDetails(serviceDiscoveryConfig, service);
    return serviceDetails == null ? null : serviceDetails.getAllServiceRoleConfigs();
  }

  private static final class RepositoryKey {
    private final String address;
    private final String clusterName;

    private RepositoryKey(String address, String clusterName) {
      this.address = address;
      this.clusterName = clusterName;
    }

    static RepositoryKey of(ServiceDiscoveryConfig serviceDiscoveryConfig) {
      return new RepositoryKey(serviceDiscoveryConfig.getAddress(), serviceDiscoveryConfig.getCluster());
    }

    String getAddress() {
      return address;
    }

    String getClusterName() {
      return clusterName;
    }

    @Override
    public int hashCode() {
      return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
      return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public String toString() {
      return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
  }

  private static class ServiceDetails {
    private ApiServiceConfig serviceConfig;
    private ApiRoleConfigList roleConfigList;

    public ApiServiceConfig getServiceConfig() {
      return serviceConfig;
    }

    public void setServiceConfig(ApiServiceConfig serviceConfig) {
      this.serviceConfig = serviceConfig;
    }

    public ApiRoleConfigList getAllServiceRoleConfigs() {
      return roleConfigList;
    }

    public void setAllServiceRoleConfigs(ApiRoleConfigList roleConfigList) {
      this.roleConfigList = roleConfigList;
    }


    @Override
    public int hashCode() {
      return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
      return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public String toString() {
      return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

  }

}

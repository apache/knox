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
package org.apache.knox.gateway.topology.discovery.cm.monitor;

import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Cache of configuration used by the ClouderaManager cluster configuration monitor.
 */
class ClusterConfigurationCache {

  // ClouderaManager address
  //    clusterName -> ServiceDiscoveryConfig
  //
  private final Map<String, Map<String, ServiceDiscoveryConfig>> clusterMonitorConfigurations =
      new ConcurrentHashMap<>();

  // ClouderaManager address
  //    clusterName
  //        serviceType -> Properties
  //
  private final Map<String, Map<String, Map<String, ServiceConfigurationModel>>> clusterServiceConfigurations =
      new ConcurrentHashMap<>();

  private final ReadWriteLock serviceConfigurationsLock = new ReentrantReadWriteLock();

  private final ReadWriteLock clusterMonitorConfigurationsLock = new ReentrantReadWriteLock();


  void addServiceConfiguration(final String address,
                               final String cluster,
                               final Map<String, ServiceConfigurationModel> configs) {
    serviceConfigurationsLock.writeLock().lock();
    try {
      clusterServiceConfigurations.computeIfAbsent(address, k -> new HashMap<>()).put(cluster, configs);
    } finally {
      serviceConfigurationsLock.writeLock().unlock();
    }
  }

  /**
   * Add discovery configuration details for the specified cluster, so the monitor
   * knows how to connect to check for changes.
   *
   * @param config The associated service discovery configuration.
   */
  void addDiscoveryConfig(final ServiceDiscoveryConfig config) {

    clusterMonitorConfigurationsLock.writeLock().lock();
    try {
      clusterMonitorConfigurations.computeIfAbsent(config.getAddress(), k -> new HashMap<>())
                                  .put(config.getCluster(), config);
    } finally {
      clusterMonitorConfigurationsLock.writeLock().unlock();
    }
  }

  /**
   * Remove the specified cluster from monitoring.
   *
   * @param address     The address of the ClouderaManager instance.
   * @param clusterName The name of the cluster.
   */
  void removeServiceConfiguration(final String address, final String clusterName) {
    serviceConfigurationsLock.writeLock().lock();
    try {
      clusterServiceConfigurations.get(address).remove(clusterName);
    } finally {
      serviceConfigurationsLock.writeLock().unlock();
    }
  }

  /**
   * Get the service discovery configuration associated with the specified ClouderaManager instance and cluster.
   *
   * @param address     An ClouderaManager instance address.
   * @param clusterName The name of a cluster associated with the ClouderaManager instance.
   * @return The associated ServiceDiscoveryConfig object.
   */
  ServiceDiscoveryConfig getDiscoveryConfig(final String address, final String clusterName) {
    ServiceDiscoveryConfig config = null;
    clusterMonitorConfigurationsLock.readLock().lock();
    try {
      Map<String, ServiceDiscoveryConfig> clusterMap = clusterMonitorConfigurations.get(address);
      if (clusterMap != null) {
        config = clusterMap.get(clusterName);
      }
    } finally {
      clusterMonitorConfigurationsLock.readLock().unlock();
    }
    return config;
  }

  /**
   * Get the service configuration details for the specified cluster and ClouderaManager instance.
   *
   * @param address     A ClouderaManager instance address.
   * @param clusterName The name of a cluster associated with the ClouderaManager instance.
   * @return A Map of service types to their corresponding configuration properties.
   */
  Map<String, ServiceConfigurationModel> getClusterServiceConfigurations(final String address,
                                                                         final String clusterName) {
    Map<String, ServiceConfigurationModel> result = new HashMap<>();

    serviceConfigurationsLock.readLock().lock();
    try {
      Map<String, Map<String, ServiceConfigurationModel>> clusterMap =
                                     clusterServiceConfigurations.get(address);
      if (clusterMap != null) {
        result.putAll(clusterMap.get(clusterName));
      }
    } finally {
      serviceConfigurationsLock.readLock().unlock();
    }

    return result;
  }

  /**
   * Get all the clusters the monitor knows about.
   *
   * @return A Map of ClouderaManager instance addresses to associated cluster names.
   */
  Map<String, List<String>> getClusterNames() {
    Map<String, List<String>> result = new HashMap<>();

    serviceConfigurationsLock.readLock().lock();
    try {
      for (Map.Entry<String, Map<String, Map<String, ServiceConfigurationModel>>> configs :
                                                                            clusterServiceConfigurations.entrySet()) {
         result.put(configs.getKey(), new ArrayList<>(configs.getValue().keySet()));
      }
    } finally {
      serviceConfigurationsLock.readLock().unlock();
    }

    return result;
  }

}

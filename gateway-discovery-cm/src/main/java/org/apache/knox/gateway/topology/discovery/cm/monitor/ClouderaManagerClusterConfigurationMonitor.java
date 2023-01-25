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

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitor;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerCluster;
import org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscoveryMessages;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitor.ConfigurationChangeListener;

/**
 * ClusterConfigurationMonitor implementation for clusters managed by ClouderaManager.
 */
public class ClouderaManagerClusterConfigurationMonitor implements ClusterConfigurationMonitor,
                                                                   ConfigurationChangeListener {

  private static final String TYPE = "CM";

  private static final ClouderaManagerServiceDiscoveryMessages log =
      MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  // The internal monitor implementation
  private PollingConfigurationAnalyzer internalMonitor;

  // Listeners to notify when configuration changes are discovered
  private final List<ConfigurationChangeListener> changeListeners = new ArrayList<>();

  // Cache of configuration data used by the monitor
  private ClusterConfigurationCache configCache;

  // Persistent store of discovery configuration
  private DiscoveryConfigurationStore discoveryConfigStore;

  // Persistent store of cluster service configurations
  private ClusterConfigurationStore serviceConfigStore;

  private ExecutorService executorService;


  public static String getType() {
    return TYPE;
  }


  ClouderaManagerClusterConfigurationMonitor(final GatewayConfig config, final AliasService aliasService,
                                             final KeystoreService keystoreService) {
    // Initialize the config cache
    configCache = new ClusterConfigurationCache();

    // Initialize the persistent stores
    discoveryConfigStore = new DiscoveryConfigurationFileStore(config);
    serviceConfigStore = new ClusterConfigurationFileStore(config);

    // Configure the executor service
    ThreadFactory tf =
          new BasicThreadFactory.Builder().namingPattern("ClouderaManagerConfigurationMonitor-%d").build();
    this.executorService = Executors.newSingleThreadExecutor(tf);

    // Initialize the internal monitor
    internalMonitor = new PollingConfigurationAnalyzer(config, configCache, aliasService, keystoreService, this);

    // Override the default polling interval if it has been configured
    // (org.apache.knox.gateway.topology.discovery.cm.monitor.interval)
    int interval = config.getClusterMonitorPollingInterval(getType());
    if (interval > 0) {
      setPollingInterval(interval);
    }

    // Load any previously-persisted discovery configuration data
    loadDiscoveryConfiguration();

    // Load any previously-persisted cluster service configuration data
    loadServiceConfiguration();
  }

  @Override
  public void start() {
    log.startingClouderaManagerConfigMonitor();
    executorService.execute(internalMonitor);
  }

  @Override
  public void stop() {
    log.stoppingClouderaManagerConfigMonitor();
    internalMonitor.stop();
  }

  @Override
  public void setPollingInterval(int interval) {
    internalMonitor.setInterval(interval);
  }

  @Override
  public void addListener(final ConfigurationChangeListener listener) {
    changeListeners.add(listener);
  }

  /**
   * Add the specified cluster service configurations to the monitor.
   *
   * @param cluster         The cluster to be added.
   * @param discoveryConfig The discovery configuration associated with the cluster.
   */
  public void addServiceConfiguration(final ClouderaManagerCluster cluster,
                                      final ServiceDiscoveryConfig discoveryConfig) {

    String address     = discoveryConfig.getAddress();
    String clusterName = cluster.getName();

    // Because this is the result of a discovery, disregard restart events which
    // occurred before "now" in future polling
    internalMonitor.setEventQueryTimestamp(address, clusterName, Instant.now());

    // Persist the discovery configuration
    discoveryConfigStore.store(discoveryConfig);

    // Add the discovery configuration to the cache
    configCache.addDiscoveryConfig(discoveryConfig);

    Map<String, List<ServiceModel>> serviceModels = cluster.getServiceModels();

    // Process the service models
    Map<String, ServiceConfigurationModel> scpMap = new HashMap<>();
    for (String service : serviceModels.keySet()) {
      for (ServiceModel model : serviceModels.get(service)) {
        ServiceConfigurationModel scp =
            scpMap.computeIfAbsent(model.getServiceType(), p -> new ServiceConfigurationModel());

        Map<String, String> serviceProps = model.getServiceProperties();
        for (Map.Entry<String, String> entry : serviceProps.entrySet()) {
          scp.addServiceProperty(entry.getKey(), entry.getValue());
        }

        Map<String, Map<String, String>> roleProps = model.getRoleProperties();
        for (String roleName : roleProps.keySet()) {
          Map<String, String> rp = roleProps.get(roleName);
          for (Map.Entry<String, String> entry : rp.entrySet()) {
            scp.addRoleProperty(roleName, entry.getKey(), entry.getValue());
          }
        }
      }
    }

    // Persist the service configurations
    serviceConfigStore.store(address, clusterName, scpMap);

    // Add the service configurations to the cache
    configCache.addServiceConfiguration(address, clusterName, scpMap);
  }

  /**
   * Load any previously-persisted service discovery configurations.
   */
  private void loadDiscoveryConfiguration() {
    for (ServiceDiscoveryConfig sdc : discoveryConfigStore.getAll()) {
      configCache.addDiscoveryConfig(sdc);
    }
  }

  /**
   * Load any previously-persisted cluster service configuration data records, so the monitor can check
   * previously-deployed topologies against the current cluster configuration, even across gateway restarts.
   */
  private void loadServiceConfiguration() {
    for (ServiceConfigurationRecord record : serviceConfigStore.getAll()) {
      configCache.addServiceConfiguration(record.getDiscoveryAddress(),
                                            record.getClusterName(),
                                            record.getConfigs());
    }
  }

  @Override
  public void clearCache(final String source, final String clusterName) {
    configCache.removeServiceConfiguration(source, clusterName);

    // Delete the associated persisted record
    serviceConfigStore.remove(source, clusterName);
  }

  @Override
  public void onConfigurationChange(final String source, final String clusterName) {
    // Respond to change notifications from the internal monitor by notifying
    // the listeners registered with this object
    notifyChangeListeners(source, clusterName);
  }

  /**
   * Notify any registered change listeners.
   *
   * @param source      The address of the ClouderaManager instance from which the cluster details were determined.
   * @param clusterName The name of the cluster whose configuration details have changed.
   */
  private void notifyChangeListeners(final String source, final String clusterName) {
    for (ConfigurationChangeListener listener : changeListeners) {
      listener.onConfigurationChange(source, clusterName);
    }
  }

}

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

import com.cloudera.api.swagger.EventsResourceApi;
import com.cloudera.api.swagger.RolesResourceApi;
import com.cloudera.api.swagger.ServicesResourceApi;
import com.cloudera.api.swagger.client.ApiClient;
import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiEvent;
import com.cloudera.api.swagger.model.ApiEventAttribute;
import com.cloudera.api.swagger.model.ApiEventCategory;
import com.cloudera.api.swagger.model.ApiEventQueryResult;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiRoleList;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.ClusterConfigurationMonitorService;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscoveryMessages;
import org.apache.knox.gateway.topology.discovery.cm.DiscoveryApiClient;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor;
import org.apache.knox.gateway.topology.simple.SimpleDescriptorFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitor.ConfigurationChangeListener;

@SuppressWarnings("PMD.DoNotUseThreads")
public class PollingConfigurationAnalyzer implements Runnable {

  private static final String COMMAND = "COMMAND";

  private static final String COMMAND_STATUS = "COMMAND_STATUS";

  private static final String STARTED_STATUS = "STARTED";

  private static final String SUCCEEDED_STATUS = "SUCCEEDED";

  private static final String RESTART_COMMAND = "Restart";

  private static final String START_COMMAND = "Start";

  // The format of the filter employed when start events are queried from ClouderaManager
  private static final String EVENTS_QUERY_FORMAT =
                                "category==" + ApiEventCategory.AUDIT_EVENT.getValue() +
                                ";attributes.cluster==\"%s\"%s";

  // The format of the timestamp element of the start events query filter
  private static final String EVENTS_QUERY_TIMESTAMP_FORMAT = ";timeOccurred=gt=%s";

  // The default amount of time before "now" to check for start events the first time
  private static final long DEFAULT_EVENT_QUERY_DEFAULT_TIMESTAMP_OFFSET = (60 * 60 * 1000); // one hour

  private static final int DEFAULT_POLLING_INTERVAL = 60;

  private static final ClouderaManagerServiceDiscoveryMessages log =
                  MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  // Fully-qualified cluster name delimiter
  private static final String FQCN_DELIM = "::";

  private ClusterConfigurationCache configCache;

  // Single listener for configuration change events
  private ConfigurationChangeListener changeListener;

  private AliasService aliasService;

  private KeystoreService keystoreService;

  private TopologyService topologyService;

  private ClusterConfigurationMonitorService ccms;

  // Polling interval in seconds
  private int interval;

  // Cache of ClouderaManager API clients, keyed by discovery address
  private final Map<String, DiscoveryApiClient> clients = new ConcurrentHashMap<>();

  // Timestamp records of the most recent start event query per discovery address
  private Map<String, String> eventQueryTimestamps = new ConcurrentHashMap<>();

  // The amount of time before "now" to will check for start events the first time
  private long eventQueryDefaultTimestampOffset = DEFAULT_EVENT_QUERY_DEFAULT_TIMESTAMP_OFFSET;

  private boolean isActive;


  PollingConfigurationAnalyzer(final ClusterConfigurationCache   configCache,
                               final AliasService                aliasService,
                               final KeystoreService             keystoreService,
                               final ConfigurationChangeListener changeListener) {
    this(configCache, aliasService, keystoreService, changeListener, DEFAULT_POLLING_INTERVAL);
  }

  PollingConfigurationAnalyzer(final ClusterConfigurationCache   configCache,
                               final AliasService                aliasService,
                               final KeystoreService             keystoreService,
                               final ConfigurationChangeListener changeListener,
                               int                               interval) {
    this.configCache     = configCache;
    this.aliasService    = aliasService;
    this.keystoreService = keystoreService;
    this.changeListener  = changeListener;
    this.interval        = interval;
  }

  void setInterval(int interval) {
    this.interval = interval;
  }

  void stop() {
    isActive = false;
  }

  private void waitFor(long seconds) {
    try {
      Thread.sleep(seconds * 1000L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void run() {
    log.startedClouderaManagerConfigMonitor(interval);
    isActive = true;

    while (isActive) {
      List<String> clustersToStopMonitoring = new ArrayList<>();

      for (Map.Entry<String, List<String>> entry : configCache.getClusterNames().entrySet()) {
        String address = entry.getKey();
        for (String clusterName : entry.getValue()) {
          log.checkingClusterConfiguration(clusterName, address);

          // Check here for existing descriptor references, and add to the removal list if there are not any
          if (!clusterReferencesExist(address, clusterName)) {
            clustersToStopMonitoring.add(address + FQCN_DELIM + clusterName);
            continue;
          }

          // Configuration changes don't mean anything without corresponding service start/restarts. Therefore, monitor
          // start events, and check the configuration only of the restarted service(s) to identify changes
          // that should trigger re-discovery.
          List<StartEvent> relevantEvents = getRelevantEvents(address, clusterName);

          // If there are no recent start events, then nothing to do now
          if (!relevantEvents.isEmpty()) {
            boolean configHasChanged = false;

            // If there are start events, then check the previously-recorded properties for the same service to
            // identify if the configuration has changed
            Map<String, ServiceConfigurationModel> serviceConfigurations =
                                    configCache.getClusterServiceConfigurations(address, clusterName);

            // Those services for which a start even has been handled
            List<String> handledServiceTypes = new ArrayList<>();

            for (StartEvent re : relevantEvents) {
              String serviceType = re.getServiceType();

              // Determine if we've already handled a start event for this service type
              if (!handledServiceTypes.contains(serviceType)) {

                // Get the previously-recorded configuration
                ServiceConfigurationModel serviceConfig = serviceConfigurations.get(re.getServiceType());

                if (serviceConfig != null) {
                  // Get the current config for the started service, and compare with the previously-recorded config
                  ServiceConfigurationModel currentConfig =
                                  getCurrentServiceConfiguration(address, clusterName, re.getService());

                  if (currentConfig != null) {
                    log.analyzingCurrentServiceConfiguration(re.getService());
                    try {
                      configHasChanged = hasConfigurationChanged(serviceConfig, currentConfig);
                    } catch (Exception e) {
                      log.errorAnalyzingCurrentServiceConfiguration(re.getService(), e);
                    }
                  }
                } else {
                  // A new service (no prior config) represent a config change, since a descriptor may have referenced
                  // the "new" service, but discovery had previously not succeeded because the service had not been
                  // configured (appropriately) at that time.
                  log.serviceEnabled(re.getService());
                  configHasChanged = true;
                }

                handledServiceTypes.add(serviceType);
              }

              if (configHasChanged) {
                break; // No need to continue checking once we've identified one reason to perform discovery again
              }
            }

            // If a change has occurred, notify the listeners
            if (configHasChanged) {
              notifyChangeListener(address, clusterName);
            }
          }
        }
      }

      // Remove outdated entries from the cache
      for (String fqcn : clustersToStopMonitoring) {
        String[] parts = fqcn.split(FQCN_DELIM);
        stopMonitoring(parts[0], parts[1]);
      }
      clustersToStopMonitoring.clear(); // reset the removal list

      waitFor(interval);
    }

    log.stoppedClouderaManagerConfigMonitor();
  }

  private TopologyService getTopologyService() {
    if (topologyService == null) {
      GatewayServices gws = GatewayServer.getGatewayServices();
      if (gws != null) {
        topologyService = gws.getService(ServiceType.TOPOLOGY_SERVICE);
      }
    }
    return topologyService;
  }

  private ClusterConfigurationMonitorService getConfigMonitorService() {
    if (ccms == null) {
      GatewayServices gws = GatewayServer.getGatewayServices();
      if (gws != null) {
        ccms = gws.getService(ServiceType.CLUSTER_CONFIGURATION_MONITOR_SERVICE);
      }
    }
    return ccms;
  }

  /**
   * Determine if any descriptors reference the specified discovery source and cluster.
   *
   * @param source      A discovery source
   * @param clusterName A discovery cluster name
   *
   * @return true, if at least one descriptor references the specified discovery information; Otherwise, false.
   */
  private boolean clusterReferencesExist(final String source, final String clusterName) {
    boolean remainingClusterRefs = false;

    if (source != null && clusterName != null) {
      TopologyService ts = getTopologyService();
      if (ts != null) {
        for (File f : ts.getDescriptors()) {
          try {
            SimpleDescriptor sd = SimpleDescriptorFactory.parse(f.toPath().toAbsolutePath().toString());
            if (source.equals(sd.getDiscoveryAddress()) && clusterName.equals(sd.getCluster())) {
              remainingClusterRefs = true;
              break;
            }
          } catch (IOException e) {
            // Ignore these errors
          }
        }
      } else {
        remainingClusterRefs = true; // If the TopologyService is unavailable, assume references remain
      }
    }

    return remainingClusterRefs;
  }

  /**
   * Stop monitoring the specified cluster for configuration changes.
   *
   * @param source      The discovery source
   * @param clusterName The name of the cluster
   */
  private void stopMonitoring(final String source, final String clusterName) {
    ClusterConfigurationMonitorService ms = getConfigMonitorService();
    if (ms != null) {
      log.stoppingConfigMonitoring(source, clusterName);
      ms.clearCache(source, clusterName);
    }
  }

  /**
   * Notify the registered change listener.
   *
   * @param source      The address of the ClouderaManager instance from which the cluster details were determined.
   * @param clusterName The name of the cluster whose configuration details have changed.
   */
  private void notifyChangeListener(final String source, final String clusterName) {
    if (changeListener != null) {
      changeListener.onConfigurationChange(source, clusterName);
    }
  }

  void setEventQueryTimestamp(final String address, final String cluster, final Instant timestamp) {
    eventQueryTimestamps.put((address + ":" + cluster), timestamp.toString());
  }

  private String getEventQueryTimestamp(final String address, final String cluster) {
    return eventQueryTimestamps.get(address + ":" + cluster);
  }

  /**
   * Get a DiscoveryApiClient for the ClouderaManager instance described by the specified discovery configuration.
   *
   * @param discoveryConfig The discovery configuration for interacting with a ClouderaManager instance.
   */
  private DiscoveryApiClient getApiClient(final ServiceDiscoveryConfig discoveryConfig) {
    return clients.computeIfAbsent(discoveryConfig.getAddress(),
                                   c -> new DiscoveryApiClient(discoveryConfig, aliasService, keystoreService));
  }

  /**
   * Get relevant events for the specified ClouderaManager cluster.
   *
   * @param address     The address of the ClouderaManager instance.
   * @param clusterName The name of the cluster.
   *
   * @return A List of StartEvent objects for service start events since the last time they were queried.
   */
  private List<StartEvent> getRelevantEvents(final String address, final String clusterName) {
    List<StartEvent> relevantEvents = new ArrayList<>();

    // Get the last event query timestamp
    String lastTimestamp = getEventQueryTimestamp(address, clusterName);

    // If this is the first query, then define the last timestamp
    if (lastTimestamp == null) {
      lastTimestamp = Instant.now().minus(eventQueryDefaultTimestampOffset, ChronoUnit.MILLIS).toString();
    }

    log.queryingRestartEventsFromCluster(clusterName, address, lastTimestamp);

    // Record the new event query timestamp for this address/cluster
    setEventQueryTimestamp(address, clusterName, Instant.now());

    // Query the event log from CM for service/cluster start events
    List<ApiEvent> events = queryEvents(getApiClient(configCache.getDiscoveryConfig(address, clusterName)),
                                               clusterName,
                                               lastTimestamp);
    for (ApiEvent event : events) {
      if(isRelevantEvent(event)) {
        relevantEvents.add(new StartEvent(event));
      }
    }

    return relevantEvents;
  }

  @SuppressWarnings("unchecked")
  private boolean isRelevantEvent(ApiEvent event) {
    final Map<String, Object> attributeMap = getAttributeMap(event.getAttributes());
    final String command = attributeMap.containsKey(COMMAND) ? (String) ((List<String>) attributeMap.get(COMMAND)).get(0) : "";
    final String status = attributeMap.containsKey(COMMAND_STATUS) ? (String) ((List<String>) attributeMap.get(COMMAND_STATUS)).get(0) : "";
    if ((START_COMMAND.equals(command) || RESTART_COMMAND.equals(command)) && (SUCCEEDED_STATUS.equals(status) || STARTED_STATUS.equals(status))) {
      return true;
    }
    return false;
  }

  private Map<String, Object> getAttributeMap(List<ApiEventAttribute> attributes) {
    return attributes == null ? Collections.emptyMap() : attributes.stream().collect(Collectors.toMap(ApiEventAttribute::getName, ApiEventAttribute::getValues));
  }

  /**
   * Query the ClouderaManager instance associated with the specified client for any service start events in the
   * specified cluster since the specified time.
   *
   * @param client      A ClouderaManager API client.
   * @param clusterName The name of the cluster for which events should be queried.
   * @param since       The ISO8601 timestamp indicating from which time to query.
   *
   * @return A List of ApiEvent objects representing the relevant events since the specified time.
   */
  protected List<ApiEvent> queryEvents(final ApiClient client, final String clusterName, final String since) {
    List<ApiEvent> events = new ArrayList<>();

    // Setup the query for events
    String timeFilter =
        (since != null) ? String.format(Locale.ROOT, EVENTS_QUERY_TIMESTAMP_FORMAT, since) : "";

    String queryString = String.format(Locale.ROOT,
                                       EVENTS_QUERY_FORMAT,
                                       clusterName,
                                       timeFilter);

    try {
      ApiEventQueryResult eventsResult = (new EventsResourceApi(client)).readEvents(20, queryString, 0);
      events.addAll(eventsResult.getItems());
    } catch (ApiException e) {
      log.clouderaManagerEventsAPIError(e);
    }

    return events;
  }

  /**
   * Get the current configuration for the specified service.
   *
   * @param address     The address of the ClouderaManager instance.
   * @param clusterName The name of the cluster.
   * @param service     The name of the service.
   *
   * @return A ServiceConfigurationModel object with the configuration properties associated with the specified
   * service.
   */
  protected ServiceConfigurationModel getCurrentServiceConfiguration(final String address,
                                                                     final String clusterName,
                                                                     final String service) {
    ServiceConfigurationModel currentConfig = null;

    log.gettingCurrentClusterConfiguration(service, clusterName, address);

    ApiClient apiClient = getApiClient(configCache.getDiscoveryConfig(address, clusterName));
    ServicesResourceApi api = new ServicesResourceApi(apiClient);
    try {
      ApiServiceConfig svcConfig = api.readServiceConfig(clusterName, service, "full");

      Map<ApiRole, ApiConfigList> roleConfigs = new HashMap<>();
      RolesResourceApi rolesApi = (new RolesResourceApi(apiClient));
      ApiRoleList roles = rolesApi.readRoles(clusterName, service, "", "full");
      for (ApiRole role : roles.getItems()) {
        ApiConfigList config = rolesApi.readRoleConfig(clusterName, role.getName(), service, "full");
        roleConfigs.put(role, config);
      }
      currentConfig = new ServiceConfigurationModel(svcConfig, roleConfigs);
    } catch (ApiException e) {
      log.clouderaManagerConfigurationAPIError(e);
    }
    return currentConfig;
  }

  /**
   * Examine the ServiceConfigurationModel objects for significant differences.
   *
   * @param previous The previously-recorded service configuration properties.
   * @param current  The current service configuration properties.
   *
   * @return true, if the current service configuration values differ from those properties defined in the previous
   * service configuration; Otherwise, false.
   */
  private boolean hasConfigurationChanged(final ServiceConfigurationModel previous,
                                          final ServiceConfigurationModel current) {
    boolean hasChanged = false;

    // Compare the service configuration properties first
    Map<String, String> previousProps = previous.getServiceProps();
    Map<String, String> currentProps = current.getServiceProps();
    for (String name : previousProps.keySet()) {
      String prevValue = previousProps.get(name);
      String currValue = currentProps.get(name);
      if (!prevValue.equals(currValue)) {
        log.serviceConfigurationPropertyHasChanged(name, prevValue, currValue);
        hasChanged = true;
        break;
      }
    }

    // If service config has not changed, check the role configuration properties
    if (!hasChanged) {
      Set<String> previousRoleTypes = previous.getRoleTypes();
      Set<String> currentRoleTypes = current.getRoleTypes();
      for (String roleType : previousRoleTypes) {
        if (!currentRoleTypes.contains(roleType)) {
          log.roleTypeRemoved(roleType);
          hasChanged = true;
          break;
        } else {
          previousProps = previous.getRoleProps(roleType);
          currentProps = current.getRoleProps(roleType);
          for (String name : previousProps.keySet()) {
            String prevValue = previousProps.get(name);
            String currValue = currentProps.get(name);
            if (currValue == null) { // A missing/removed property
              if (!(prevValue == null || "null".equals(prevValue))) {
                log.roleConfigurationPropertyHasChanged(name, prevValue, "null");
                hasChanged = true;
                break;
              }
            } else if (!currValue.equals(prevValue)) {
              log.roleConfigurationPropertyHasChanged(name, prevValue, currValue);
              hasChanged = true;
              break;
            }
          }
        }
      }
    }

    return hasChanged;
  }

  /**
   * Internal representation of a ClouderaManager service start event
   */
  static final class StartEvent {

    private static final String ATTR_CLUSTER = "CLUSTER";
    private static final String ATTR_SERVICE_TYPE = "SERVICE_TYPE";
    private static final String ATTR_SERVICE = "SERVICE";

    private static List<String> attrsOfInterest = new ArrayList<>();

    static {
      attrsOfInterest.add(ATTR_CLUSTER);
      attrsOfInterest.add(ATTR_SERVICE_TYPE);
      attrsOfInterest.add(ATTR_SERVICE);
    }

    private ApiEvent auditEvent;
    private String clusterName;
    private String serviceType;
    private String service;

    StartEvent(final ApiEvent auditEvent) {
      if (ApiEventCategory.AUDIT_EVENT != auditEvent.getCategory()) {
        throw new IllegalArgumentException("Invalid event category " + auditEvent.getCategory().getValue());
      }
      this.auditEvent = auditEvent;
      for (ApiEventAttribute attribute : auditEvent.getAttributes()) {
        if (attrsOfInterest.contains(attribute.getName())) {
          setPropertyFromAttribute(attribute);
        }
      }
    }

    String getTimestamp() {
      return auditEvent.getTimeOccurred();
    }

    String getClusterName() {
      return clusterName;
    }

    String getServiceType() {
      return serviceType;
    }

    String getService() {
      return service;
    }

    private void setPropertyFromAttribute(final ApiEventAttribute attribute) {
      switch (attribute.getName()) {
        case ATTR_CLUSTER:
          clusterName = attribute.getValues().get(0);
          break;
        case ATTR_SERVICE_TYPE:
          serviceType = attribute.getValues().get(0);
          break;
        case ATTR_SERVICE:
          service = attribute.getValues().get(0);
          break;
        default:
      }
    }
  }

}

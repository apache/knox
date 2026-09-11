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
import com.cloudera.api.swagger.model.ApiEvent;
import com.cloudera.api.swagger.model.ApiEventAttribute;
import com.cloudera.api.swagger.model.ApiEventCategory;
import com.cloudera.api.swagger.model.ApiEventQueryResult;
import com.cloudera.api.swagger.model.ApiRoleConfigList;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import com.cloudera.api.swagger.model.ApiServiceList;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.services.topology.impl.GatewayStatusService;
import org.apache.knox.gateway.topology.ClusterConfigurationMonitorService;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.cm.ApiClientFactory;
import org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscoveryMessages;
import org.apache.knox.gateway.topology.discovery.cm.DiscoveryApiClient;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelFactory;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGeneratorsHolder;
import org.apache.knox.gateway.topology.discovery.cm.ServiceRoleCollector;
import org.apache.knox.gateway.topology.discovery.cm.ServiceRoleCollectorBuilder;
import org.apache.knox.gateway.topology.discovery.cm.TypeNameFilter;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor;
import org.apache.knox.gateway.topology.simple.SimpleDescriptorFactory;

import java.io.File;
import java.math.BigDecimal;
import java.security.KeyStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitor.ConfigurationChangeListener;

@SuppressWarnings("PMD.DoNotUseThreads")
public class PollingConfigurationAnalyzer implements Runnable {

  private static final String COMMAND = "COMMAND";

  private static final String COMMAND_STATUS = "COMMAND_STATUS";

  static final String SUCCEEDED_STATUS = "SUCCEEDED";

  static final String RESTART_COMMAND = "Restart";

  static final String START_COMMAND = "Start";

  static final String ROLLING_RESTART_COMMAND = "RollingRestart";

  static final String RESTART_WAITING_FOR_STALENESS_SUCCESS_COMMAND = "RestartWaitingForStalenessSuccess";

  static final String CM_SERVICE_TYPE = "ManagerServer";
  static final String CM_SERVICE      = "ClouderaManager";

  public static final String EVENT_CODE_ROLE_DELETED = "EV_ROLE_DELETED";
  public static final String EVENT_CODE_ROLE_CREATED = "EV_ROLE_CREATED";

  // Collection of those commands which represent the potential activation of service configuration changes
  private static final Collection<String> START_COMMANDS = Arrays.asList(START_COMMAND, RESTART_COMMAND, ROLLING_RESTART_COMMAND,
      RESTART_WAITING_FOR_STALENESS_SUCCESS_COMMAND);

  private static final Collection<String> DELETED_EVENT_CODES = Arrays.asList(EVENT_CODE_ROLE_DELETED);
  private static final Collection<String> CREATED_EVENT_CODES = Arrays.asList(EVENT_CODE_ROLE_CREATED);

  // The format of the filter employed when start events are queried from ClouderaManager
  private static final String EVENTS_QUERY_FORMAT =
                                "category==" + ApiEventCategory.AUDIT_EVENT.getValue() +
                                ";attributes.cluster==\"%s\"%s";

  // The format of the timestamp element of the start events query filter
  private static final String EVENTS_QUERY_TIMESTAMP_FORMAT = ";timeOccurred=gt=%s";

  // The default amount of time before "now" to check for start events the first time
  private static final long DEFAULT_EVENT_QUERY_DEFAULT_TIMESTAMP_OFFSET = (60 * 60 * 1000); // one hour

  private static final int DEFAULT_POLLING_INTERVAL = 60;

  private static final ClouderaManagerServiceDiscoveryMessages log = MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  private static final GatewaySpiMessages LOGGER = MessagesFactory.get(GatewaySpiMessages.class);

  // Fully-qualified cluster name delimiter
  private static final String FQCN_DELIM = "::";

  private ClusterConfigurationCache configCache;

  // Single listener for configuration change events
  private ConfigurationChangeListener changeListener;

  private AliasService aliasService;

  private KeyStore truststore;

  private TopologyService topologyService;

  private ClusterConfigurationMonitorService ccms;

  // Polling interval in seconds
  private int interval;

  private final Cache<String, Long> processedEvents;

  // Cache of ClouderaManager API clients, keyed by discovery address
  private final Map<String, DiscoveryApiClient> clients = new ConcurrentHashMap<>();

  // Timestamp records of the most recent start event query per discovery address
  private Map<String, Instant> eventQueryTimestamps = new ConcurrentHashMap<>();

  // The amount of time before "now" to will check for start events the first time
  private long eventQueryDefaultTimestampOffset = DEFAULT_EVENT_QUERY_DEFAULT_TIMESTAMP_OFFSET;

  private ServiceModelGeneratorsHolder serviceModelGeneratorsHolder = ServiceModelGeneratorsHolder.getInstance();

  // Filters CM role types the same way discovery does: the configured excluded-role-types deny-list combined with the
  // allow-list of role types some ServiceModelGenerator actually uses. Lazily built so it picks up gatewayConfig.
  private TypeNameFilter roleTypeFilter;

  private boolean isActive;

  private final GatewayConfig gatewayConfig;

  private GatewayStatusService gatewayStatusService;

  PollingConfigurationAnalyzer(final GatewayConfig gatewayConfig,
                               final ClusterConfigurationCache   configCache,
                               final AliasService                aliasService,
                               final KeystoreService             keystoreService,
                               final ConfigurationChangeListener changeListener) {
    this(gatewayConfig, configCache, aliasService, keystoreService, changeListener, DEFAULT_POLLING_INTERVAL);
  }

  PollingConfigurationAnalyzer(final GatewayConfig gatewayConfig,
                               final ClusterConfigurationCache   configCache,
                               final AliasService                aliasService,
                               final KeystoreService             keystoreService,
                               final ConfigurationChangeListener changeListener,
                               int                               interval) {
    this.gatewayConfig = gatewayConfig;
    this.configCache     = configCache;
    this.aliasService    = aliasService;

    if (keystoreService != null) {
      try {
        truststore = keystoreService.getTruststoreForHttpClient();
      } catch (KeystoreServiceException e) {
        LOGGER.failedToLoadTruststore(e.getMessage(), e);
      }
    }

    this.changeListener  = changeListener;
    this.interval        = interval;
    this.processedEvents = Caffeine.newBuilder().expireAfterAccess(interval * 3, TimeUnit.SECONDS).maximumSize(1000).build();
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

    boolean gatewayStatusOk = false;
    while (isActive) {
      if (!gatewayStatusOk) {
        gatewayStatusOk = getGatewayStatusService() != null && getGatewayStatusService().status();
      }
      if (gatewayStatusOk) {
        monitorClusterConfigurationChanges();
      } else {
        log.gatewayIsNotYetReadyToMonitorClouderaManagerConfigs();
      }
      waitFor(interval);
    }

    log.stoppedClouderaManagerConfigMonitor();
  }

  private void monitorClusterConfigurationChanges() {
    try {
      final List<String> clustersToStopMonitoring = new ArrayList<>();

      for (Map.Entry<String, List<String>> entry : configCache.getClusterNames().entrySet()) {
        String address = entry.getKey();
        for (String clusterName : entry.getValue()) {
          if (configCache.getDiscoveryConfig(address, clusterName) == null) {
            log.noClusterConfiguration(clusterName, address);
            continue;
          }
          log.checkingClusterConfiguration(clusterName, address);

          // Check here for existing descriptor references, and add to the removal list if there are not any
          if (!clusterReferencesExist(address, clusterName)) {
            clustersToStopMonitoring.add(address + FQCN_DELIM + clusterName);
            continue;
          }

          // Configuration changes don't mean anything without corresponding service start/restarts. Therefore, monitor
          // start events, and check the configuration only of the restarted service(s) to identify changes
          // that should trigger re-discovery.
          final List<RelevantEvent> relevantEvents = getRelevantEvents(address, clusterName);

          // If there are no recent start events, then nothing to do now
          if (!relevantEvents.isEmpty()) {
            // If a change has occurred, notify the listeners
            if (hasConfigChanged(address, clusterName, relevantEvents) || hasScaleEvent(relevantEvents)) {
              notifyChangeListener(address, clusterName);
            }
            // these events should not be processed again even if the next CM query result contains them
            relevantEvents.forEach(re -> processedEvents.put(re.auditEvent.getId(), 1L));
          }
        }
      }

      // Remove outdated entries from the cache
      for (String fqcn : clustersToStopMonitoring) {
        String[] parts = fqcn.split(FQCN_DELIM);
        stopMonitoring(parts[0], parts[1]);
      }
      clustersToStopMonitoring.clear(); // reset the removal list

    } catch (Exception e) {
      log.clouderaManagerConfigurationChangesMonitoringError(e);
    }
  }

  private boolean hasScaleEvent(List<RelevantEvent> relevantEvents) {
    boolean found = false;
    for (RelevantEvent event: relevantEvents) {
      if (alreadyProcessed(event)) {
        log.activationEventAlreadyProcessed(event.auditEvent.getId());
        continue;
      }
      if (event.getRole() != null) {
        if (event.isRoleAddedEvent()) {
          log.foundUpScaleEvent(event.getRole(), event.getHosts());
          found = true;
          break;
        }
        if (event.isRoleDeletedEvent()) {
          log.foundDownScaleEvent(event.getRole(), event.getHosts());
          found = true;
          break;
        }
      }
    }
    return found;
  }

  private boolean alreadyProcessed(RelevantEvent event) {
    return processedEvents.getIfPresent(event.auditEvent.getId()) != null;
  }

  private boolean hasConfigChanged(String address, String clusterName, List<RelevantEvent> relevantEvents) {
    // If there are start events, then check the previously-recorded properties for the same service to
    // identify if the configuration has changed
    final Map<String, ServiceConfigurationModel> serviceConfigurations =
                          configCache.getClusterServiceConfigurations(address, clusterName);

    // Those services for which a start even has been handled
    final List<String> handledServiceTypes = new ArrayList<>();

    boolean configHasChanged = false;
    for (RelevantEvent re : relevantEvents) {
      if (alreadyProcessed(re)) {
        log.activationEventAlreadyProcessed(re.auditEvent.getId());
        continue;
      }

      if (re.isRoleAddedEvent() || re.isRoleDeletedEvent()) {
        continue;
      }

      String serviceType = re.getServiceType();

      if (CM_SERVICE_TYPE.equals(serviceType)) {
        if (CM_SERVICE.equals(re.getService())) {
          // This is a 'rolling cluster restart' or 'restart waiting for staleness' event, so assume configuration has changed
          configHasChanged = true;
        }
      }

      // Determine if we've already handled a start event for this service type
      if (!configHasChanged && !handledServiceTypes.contains(serviceType)) {
        // Get the previously-recorded configuration
        ServiceConfigurationModel serviceConfig = serviceConfigurations.get(re.getServiceType());

        // Get the current (model-derived) config for the started service. This is null when the service produces no
        // model (e.g. invalid configuration), just as such a service is absent from the recorded baseline.
        ServiceConfigurationModel currentConfig =
                        getCurrentServiceConfiguration(address, clusterName, re.getService(), re.getServiceType());

        if (serviceConfig == null && currentConfig == null) {
          // Was and remains in an invalid configuration state (no model either time): nothing to proxy, no change.
          log.skippingConfigChangeForInvalidService(re.getService(), re.getServiceType());
        } else if (serviceConfig != null && currentConfig != null) {
          // Valid before and now: compare the recorded and current configs to detect a change.
          log.analyzingCurrentServiceConfiguration(re.getService());
          try {
            configHasChanged = hasConfigurationChanged(serviceConfig, currentConfig);
          } catch (Exception e) {
            log.errorAnalyzingCurrentServiceConfiguration(re.getService(), e);
          }
        } else if (currentConfig != null) {
          // No prior config, but the service now produces a model: new / became valid -> re-discover.
          log.serviceEnabled(re.getService());
          configHasChanged = true;
        } else {
          // Had a prior config but produces no model now: became invalid / was removed -> re-discover so the
          // service is dropped from the affected topologies (and the scoped-replace merge clears its baseline).
          log.serviceDisabled(re.getService());
          configHasChanged = true;
        }

        handledServiceTypes.add(serviceType);
      }

      if (configHasChanged) {
        break; // No need to continue checking once we've identified one reason to perform discovery again
      }
    }

    return configHasChanged;
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

  private GatewayStatusService getGatewayStatusService() {
    if (gatewayStatusService == null) {
      final GatewayServices gatewayServices = GatewayServer.getGatewayServices();
      if (gatewayServices != null) {
        gatewayStatusService = gatewayServices.getService(ServiceType.GATEWAY_STATUS_SERVICE);
      }
    }
    return gatewayStatusService;
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
        boolean referencesIncomplete = false;
        for (File f : ts.getDescriptors()) {
          try {
            SimpleDescriptor sd = SimpleDescriptorFactory.parse(f.toPath().toAbsolutePath().toString());
            if (source.equals(sd.getDiscoveryAddress()) && clusterName.equals(sd.getCluster())) {
              remainingClusterRefs = true;
              break;
            }
          } catch (Exception e) {
            // A descriptor we cannot read/parse might reference this cluster. Concluding "no references" on
            // incomplete information would tear down a still-referenced cluster's cache, so remember the gap and
            // assume a reference remains below.
            log.errorCheckingClusterReferences(f.getName(), source, clusterName, e);
            referencesIncomplete = true;
          }
        }
        if (!remainingClusterRefs && referencesIncomplete) {
          // Could not rule out a reference from an unreadable/unparseable descriptor; assume a reference remains and
          // keep monitoring rather than evict the cache (mirroring the unavailable-TopologyService case below).
          remainingClusterRefs = true;
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
    eventQueryTimestamps.put((address + ":" + cluster), timestamp);
  }

  private Instant getEventQueryTimestamp(final String address, final String cluster) {
    return eventQueryTimestamps.get(address + ":" + cluster);
  }

  /**
   * Get a DiscoveryApiClient for the ClouderaManager instance described by the specified discovery configuration.
   *
   * @param discoveryConfig The discovery configuration for interacting with a ClouderaManager instance.
   */
  private DiscoveryApiClient getApiClient(final ServiceDiscoveryConfig discoveryConfig) {
    return clients.computeIfAbsent(discoveryConfig.getAddress(),
                                   c -> ApiClientFactory.getApiClient(gatewayConfig, discoveryConfig, aliasService, truststore));
  }

  /**
   * Get relevant events for the specified ClouderaManager cluster.
   *
   * @param address     The address of the ClouderaManager instance.
   * @param clusterName The name of the cluster.
   *
   * @return A List of StartEvent objects for service start events since the last time they were queried.
   */
  private List<RelevantEvent> getRelevantEvents(final String address, final String clusterName) {
    List<RelevantEvent> relevantEvents = new ArrayList<>();

    // Get the last event query timestamp
    Instant lastTimestamp = getEventQueryTimestamp(address, clusterName);

    // If this is the first query, then define the last timestamp
    if (lastTimestamp == null) {
      lastTimestamp = Instant.now().minus(eventQueryDefaultTimestampOffset, ChronoUnit.MILLIS);
    }

    // Go back in time an '2 x interval' more to mitigate the chance of losing a relevant audit event
    lastTimestamp = lastTimestamp.minus(interval * 2, ChronoUnit.SECONDS);

    log.queryingConfigActivationEventsFromCluster(clusterName, address, lastTimestamp.toString());

    // Record the new event query timestamp for this address/cluster
    setEventQueryTimestamp(address, clusterName, Instant.now());

    // Query the event log from CM for service/cluster start events
    final List<ApiEvent> events = queryEvents(getApiClient(configCache.getDiscoveryConfig(address, clusterName)), clusterName, lastTimestamp.toString());

    if (events.isEmpty()) {
      log.noActivationEventFound();
    } else {
      // The CM service types referenced by the deployed descriptors for this cluster. Events for services no
      // descriptor references are irrelevant: with per-descriptor discovery filtering they are never discovered, so
      // they must not be treated as "new services" and trigger churn. A null value means references cannot be
      // determined (no TopologyService), in which case no reference filtering is applied.
      final Set<String> referencedServiceTypes = getReferencedServiceTypes(address, clusterName);
      for (ApiEvent event : events) {
        if (isStartEvent(event, referencedServiceTypes) || isScaleEvent(event, referencedServiceTypes)) {
          relevantEvents.add(new RelevantEvent(event));
        }
      }
    }

    return relevantEvents;
  }

  private boolean isStartEvent(ApiEvent event, Set<String> referencedServiceTypes) {
    final Map<String, Object> attributeMap = getAttributeMap(event.getAttributes());
    final String command = getAttribute(attributeMap, COMMAND);
    final String status = getAttribute(attributeMap, COMMAND_STATUS);
    final String serviceType = getAttribute(attributeMap, RelevantEvent.ATTR_SERVICE_TYPE);
    final String service = getAttribute(attributeMap, RelevantEvent.ATTR_SERVICE);
    final boolean serviceModelGeneratorExists = serviceModelGeneratorsHolder.getServiceModelGenerators(serviceType) != null;
    final boolean clusterRollingOrStalenessRestart = CM_SERVICE.equals(service) && CM_SERVICE_TYPE.equals(serviceType)
            && (ROLLING_RESTART_COMMAND.equals(command) || RESTART_WAITING_FOR_STALENESS_SUCCESS_COMMAND.equals(command));
    final boolean relevant = (clusterRollingOrStalenessRestart && SUCCEEDED_STATUS.equals(status))
            || (START_COMMANDS.contains(command) && SUCCEEDED_STATUS.equals(status) && serviceModelGeneratorExists
                && !isExcludedServiceType(serviceType) && isReferencedServiceType(serviceType, referencedServiceTypes));
    log.activationEventRelevance(event.getId(), relevant, command, status, serviceType, serviceModelGeneratorExists, clusterRollingOrStalenessRestart);
    return relevant;
  }

  private boolean isScaleEvent(ApiEvent event, Set<String> referencedServiceTypes) {
    final Map<String, Object> attributeMap = getAttributeMap(event.getAttributes());
    final String serviceType = getAttribute(attributeMap, RelevantEvent.ATTR_SERVICE_TYPE);
    final String roleType = getAttribute(attributeMap, RelevantEvent.ATTR_ROLE);
    final String eventCode = getAttribute(attributeMap, RelevantEvent.ATTR_EVENT_CODE);
    final boolean serviceModelGeneratorExists = serviceModelGeneratorsHolder.getServiceModelGenerators(serviceType) != null;
    final boolean relevant = serviceModelGeneratorExists && !isExcludedServiceType(serviceType)
            && isReferencedServiceType(serviceType, referencedServiceTypes)
            && !isExcludedRoleType(roleType)
            && (CREATED_EVENT_CODES.contains(eventCode) || DELETED_EVENT_CODES.contains(eventCode));
    log.scaleEventRelevance(event.getId(), String.valueOf(relevant), eventCode, serviceType, roleType, serviceModelGeneratorExists);
    return relevant;
  }

  /**
   * @return true if the given CM service type is referenced by a deployed descriptor for the cluster, or if
   * references could not be determined ({@code referencedServiceTypes} is null, so no filtering is applied).
   */
  private boolean isReferencedServiceType(final String serviceType, final Set<String> referencedServiceTypes) {
    return referencedServiceTypes == null || referencedServiceTypes.contains(serviceType);
  }

  /**
   * Determine the CM service types referenced by the deployed descriptors targeting the given discovery source and
   * cluster, by mapping each descriptor's declared Knox service names to CM service types via the registered service
   * model generators.
   * <p>
   * The set is built only from descriptors that could be read and parsed. An unreadable/unparseable descriptor is
   * logged and skipped: a re-discovery can only ever refresh a descriptor that is parseable (an unparseable one cannot
   * be regenerated until it is fixed, which itself triggers a fresh discovery), so filtering relevance on the services
   * of the parseable descriptors never drops an actionable re-discovery. This intentionally differs from
   * {@link #clusterReferencesExist(String, String)}, which must instead assume references remain so an unreadable
   * descriptor never causes the monitored baseline to be discarded.
   *
   * @return the referenced CM service types (possibly empty), or null if the TopologyService is unavailable
   */
  private Set<String> getReferencedServiceTypes(final String source, final String clusterName) {
    final TopologyService ts = getTopologyService();
    if (ts == null) {
      return null;
    }

    final Set<String> referencedServices = new HashSet<>();
    for (File f : ts.getDescriptors()) {
      try {
        SimpleDescriptor sd = SimpleDescriptorFactory.parse(f.toPath().toAbsolutePath().toString());
        if (source.equals(sd.getDiscoveryAddress()) && clusterName.equals(sd.getCluster())) {
          for (SimpleDescriptor.Service service : sd.getServices()) {
            referencedServices.add(service.getName());
          }
        }
      } catch (Exception e) {
        // A descriptor we cannot read/parse is skipped: it cannot be re-generated until it is fixed, so it cannot be
        // the target of a useful re-discovery now. Log the offending file and keep filtering on the descriptors we
        // could read.
        log.errorDeterminingReferencedServiceTypes(f.getName(), source, clusterName, e);
      }
    }
    return serviceModelGeneratorsHolder.getServiceTypesForServices(referencedServices);
  }

  /**
   * Determine whether the given CM service type is configured to be excluded from CM service discovery via
   * {@code gateway.cloudera.manager.service.discovery.excluded.service.types}. Excluded service types are never
   * discovered (see ClouderaManagerServiceDiscovery#getClusterServices), so their configuration is never present in
   * the monitored baseline. Treating their start/scale events as relevant would otherwise cause the analyzer to see a
   * missing baseline and trigger an unnecessary re-discovery on every restart of such a service.
   *
   * @param serviceType the CM service type from an audit event
   * @return true if the service type is excluded from discovery; false otherwise
   */
  private boolean isExcludedServiceType(final String serviceType) {
    if (serviceType == null) {
      return false;
    }
    final Collection<String> excludedServiceTypes = gatewayConfig.getClouderaManagerServiceDiscoveryExcludedServiceTypes();
    if (excludedServiceTypes == null || excludedServiceTypes.isEmpty()) {
      return false;
    }
    return excludedServiceTypes.stream().anyMatch(serviceType::equalsIgnoreCase);
  }

  /**
   * Determine whether the given CM role type would be excluded from CM service discovery, using the exact same filter
   * discovery applies when collecting role configurations (see ServiceRoleCollectorBuilder): the configured
   * {@code gateway.cloudera.manager.service.discovery.excluded.role.types} deny-list combined with the allow-list of
   * role types some ServiceModelGenerator actually uses ({@link ServiceModelGeneratorsHolder#getAllRoleTypes()}).
   * Role types discovery never collects can never produce a service model, so a scale (role added/removed) event for
   * such a role type must not be treated as relevant - it would otherwise trigger an unnecessary re-discovery that
   * would recompute an identical model.
   * <p>
   * Fails open on a missing/empty role type: the event is kept relevant rather than dropped on missing information.
   *
   * @param roleType the CM role type from an audit event
   * @return true if the role type is excluded from discovery; false otherwise (including when it is null/empty)
   */
  private boolean isExcludedRoleType(final String roleType) {
    if (roleType == null || roleType.isEmpty()) {
      return false;
    }
    if (roleTypeFilter == null) {
      roleTypeFilter = new TypeNameFilter(gatewayConfig.getClouderaManagerServiceDiscoveryExcludedRoleTypes(),
              serviceModelGeneratorsHolder.getAllRoleTypes());
    }
    return roleTypeFilter.isExcluded(roleType);
  }

  @SuppressWarnings("unchecked")
  private String getAttribute( Map<String, Object> attributeMap, String attributeName) {
    return attributeMap.containsKey(attributeName) ? ((List<String>) attributeMap.get(attributeName)).get(0) : "";
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
    String timeFilter = (since != null) ? String.format(Locale.ROOT, EVENTS_QUERY_TIMESTAMP_FORMAT, since) : "";

    String queryString = String.format(Locale.ROOT, EVENTS_QUERY_FORMAT, clusterName, timeFilter);

    try {
      // giving 'null' as maximum result size results in fetching all events from CM within the given time interval
      ApiEventQueryResult eventsResult = (new EventsResourceApi(client)).readEvents(null, queryString,
          BigDecimal.valueOf(0));
      events.addAll(eventsResult.getItems());
    } catch (ApiException e) {
      log.clouderaManagerEventsAPIError(e);
    }

    return events;
  }

  /**
   * Get the current configuration for the specified service, built by running the service model generators exactly
   * as cluster discovery does and transforming the resulting models the same way the persisted baseline is built.
   * <p>
   * Because the baseline is model-derived, computing the current snapshot the same way keeps the two comparable, and
   * a service whose configuration is invalid (no generator produces a model) yields {@code null} here - mirroring its
   * absence from the baseline, so a service that was and remains invalid is not misread as a change.
   *
   * @param address     The address of the ClouderaManager instance.
   * @param clusterName The name of the cluster.
   * @param service     The name of the service.
   * @param serviceType The type of the service.
   *
   * @return A ServiceConfigurationModel with the model-derived configuration of the service, or {@code null} if the
   * service produces no model (e.g. invalid configuration).
   */
  protected ServiceConfigurationModel getCurrentServiceConfiguration(final String address,
                                                                     final String clusterName,
                                                                     final String service,
                                                                     final String serviceType) {
    ServiceConfigurationModel currentConfig = null;

    log.gettingCurrentClusterConfiguration(service, clusterName, address);

    DiscoveryApiClient apiClient = getApiClient(configCache.getDiscoveryConfig(address, clusterName));
    ServicesResourceApi api = new ServicesResourceApi(apiClient);
    try {
      ApiServiceConfig svcConfig = api.readServiceConfig(clusterName, service, "full");

      RolesResourceApi rolesResourceApi = new RolesResourceApi(apiClient);
      ServiceRoleCollector roleCollector = ServiceRoleCollectorBuilder.newBuilder()
              .gatewayConfig(gatewayConfig)
              .rolesResourceApi(rolesResourceApi)
              .build();

      ApiRoleConfigList roleConfigList = roleCollector.getAllServiceRoleConfigurations(clusterName, service);

      final ApiService apiService = new ApiService().name(service).type(serviceType);
      final ApiServiceConfig coreSettingsConfig = getCoreSettingsConfig(api, clusterName);
      final Set<ServiceModel> serviceModels =
              ServiceModelFactory.generateServiceModels(apiClient, apiService, svcConfig, roleConfigList, coreSettingsConfig);
      currentConfig = ServiceConfigurationModel.fromServiceModels(serviceModels).get(serviceType);
    } catch (ApiException e) {
      log.clouderaManagerConfigurationAPIError(e);
    }
    return currentConfig;
  }

  /**
   * Look up the CORE_SETTINGS service configuration for the cluster, needed to run generators faithfully (some HDFS
   * models read settings from CORE_SETTINGS). Returns {@code null} if the cluster has no CORE_SETTINGS service.
   */
  private ApiServiceConfig getCoreSettingsConfig(final ServicesResourceApi api, final String clusterName) throws ApiException {
    final ApiServiceList serviceList = api.readServices(clusterName, "summary");
    if (serviceList != null && serviceList.getItems() != null) {
      for (ApiService service : serviceList.getItems()) {
        if (ClouderaManagerServiceDiscovery.CORE_SETTINGS_TYPE.equals(service.getType())) {
          return api.readServiceConfig(clusterName, service.getName(), "full");
        }
      }
    }
    return null;
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
  static final class RelevantEvent {

    private static final String ATTR_CLUSTER      = "CLUSTER";
    private static final String ATTR_SERVICE_TYPE = "SERVICE_TYPE";
    private static final String ATTR_SERVICE      = "SERVICE";
    private static final String ATTR_ROLE         = "ROLE_TYPE";
    private static final String ATTR_HOST         = "HOSTS";
    private static final String ATTR_EVENT_CODE   = "EVENTCODE";

    private static List<String> attrsOfInterest = new ArrayList<>();

    static {
      attrsOfInterest.add(ATTR_CLUSTER);
      attrsOfInterest.add(ATTR_SERVICE_TYPE);
      attrsOfInterest.add(ATTR_SERVICE);
      attrsOfInterest.add(ATTR_ROLE);
      attrsOfInterest.add(ATTR_HOST);
      attrsOfInterest.add(ATTR_EVENT_CODE);
    }

    private ApiEvent auditEvent;
    private String clusterName;
    private String serviceType;
    private String service;
    private String role;
    private String eventCode;
    private Set<String> hosts = new HashSet<>();

    RelevantEvent(final ApiEvent auditEvent) {
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

    Set<String> getHosts() {
      return hosts;
    }

    String getRole() {
      return role;
    }

    boolean isRoleAddedEvent() {
      return EVENT_CODE_ROLE_CREATED.equals(eventCode);
    }

    boolean isRoleDeletedEvent() {
      return EVENT_CODE_ROLE_DELETED.equals(eventCode);
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
        case ATTR_HOST:
          if (attribute.getValues() != null && !attribute.getValues().isEmpty()) {
            hosts.addAll(attribute.getValues());
          }
          break;
        case ATTR_ROLE:
          role = attribute.getValues().get(0);
          break;
        case ATTR_EVENT_CODE:
          eventCode = attribute.getValues().get(0);
          break;
        default:
      }
    }
  }
}

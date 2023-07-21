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

import com.cloudera.api.swagger.client.ApiException;
import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger="org.apache.knox.gateway.topology.discovery.cm")
public interface ClouderaManagerServiceDiscoveryMessages {

  @Message(level = MessageLevel.INFO, text = "Discovering cluster: {0} ({1})")
  void discoveringCluster(String clusterName, String version);

  @Message(level = MessageLevel.INFO, text = "Discovered cluster: {0} ({1})")
  void discoveredCluster(String clusterName, String version);

  @Message(level = MessageLevel.INFO, text = "Performing cluster discovery for \"{0}\"")
  void discoveringCluster(String clusterName);

  @Message(level = MessageLevel.INFO, text = "Discovering service: {0} ({1}) ...")
  void discoveringService(String serviceName, String serviceType);

  @Message(level = MessageLevel.INFO, text = "Discovered service: {0} ({1})")
  void discoveredService(String serviceName, String serviceType);

  @Message(level = MessageLevel.INFO, text = "Skipping service discovery: {0} ({1})")
  void skipServiceDiscovery(String serviceName, String serviceType);

  @Message(level = MessageLevel.INFO, text = "Discovering service role: {0} ({1}) ...")
  void discoveringServiceRole(String roleName, String roleType);

  @Message(level = MessageLevel.INFO, text = "Discovered service role: {0} ({1})")
  void discoveredServiceRole(String roleName, String roleType);

  @Message(level = MessageLevel.WARN, text = "Service role {0} has configuration issues: {1}")
  void serviceRoleHasConfigurationIssues(String roleName, String configurationIssuesText);

  @Message(level = MessageLevel.INFO, text = "Attempting to authenticate Knox using {0} ...")
  void attemptingKerberosLogin(String loginConfigPath);

  @Message(level = MessageLevel.ERROR,
           text = "Failed Kerberos login {0} ({1}): {2}")
  void failedKerberosLogin(String jaasLoginConfig,
                           String entryName,
                           @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.DEBUG, text = "Using JAAS configuration file implementation: {0}")
  void usingJAASConfigurationFileImplementation(String implementation);

  @Message(level = MessageLevel.ERROR,
           text = "Failed to load JAAS configuration file implementation {0}: {1}")
  void failedToLoadJAASConfigurationFileImplementation(String implementationName,
                                                       @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
           text = "Failed to instantiate JAAS configuration file implementation {0}: {1}")
  void failedToInstantiateJAASConfigurationFileImplementation(String implementationName,
                                                              @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "No JAAS configuration file implementation found.")
  void noJAASConfigurationFileImplementation();

  @Message(level = MessageLevel.ERROR,
           text = "Encountered an error during cluster ({0}) discovery: {1}")
  void clusterDiscoveryError(String clusterName, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.INFO, text = "Sleeping {0} second(s) before retrying Cloudera Manager service discovery for the {1}. time")
  void retryDiscovery(long retrySleep, int retryAttempt);

  @Message(level = MessageLevel.ERROR,
           text = "Failed to access the service configurations for cluster ({0}) discovery: {1}")
  void failedToAccessServiceConfigs(String clusterName, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Failed to access the service role configurations ({0} / {1}) for cluster ({2}) discovery: {3}")
  void failedToAccessServiceRoleConfigs(String serviceName, String roleName, String clusterName, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
           text = "No address for Cloudera Manager service discovery has been configured.")
  void missingDiscoveryAddress();

  @Message(level = MessageLevel.ERROR,
           text = "No cluster for Cloudera Manager service discovery has been configured.")
  void missingDiscoveryCluster();

  @Message(level = MessageLevel.ERROR,
           text = "Encountered an error attempting to determine the user for alias {0} : {1}")
  void aliasServiceUserError(String alias, String error);

  @Message(level = MessageLevel.ERROR,
           text = "Encountered an error attempting to determine the password for alias {0} : {1}")
  void aliasServicePasswordError(String alias, String error);

  @Message(level = MessageLevel.ERROR,
           text = "No user configured for Cloudera Manager service discovery.")
  void aliasServiceUserNotFound();

  @Message(level = MessageLevel.ERROR,
           text = "No password configured for Cloudera Manager service discovery.")
  void aliasServicePasswordNotFound();

  @Message(level = MessageLevel.INFO,
          text = "The value of the qualifying parameter {1} for service {0} does not match: Expected={2}, Actual={3}")
  void qualifyingServiceParamMismatch(String serviceName, String paramName, String expectedValue, String actualValue);

  @Message(level = MessageLevel.ERROR,
           text = "Unable to access the ClouderaManager Configuration Change Monitor: {0}")
  void errorAccessingConfigurationChangeMonitor(@StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.INFO, text = "Starting ClouderaManager cluster configuration monitor")
  void startingClouderaManagerConfigMonitor();

  @Message(level = MessageLevel.INFO,
           text = "Started ClouderaManager cluster configuration monitor (checking every {0} seconds)")
  void startedClouderaManagerConfigMonitor(long pollingInterval);

  @Message(level = MessageLevel.INFO, text = "Stopping ClouderaManager cluster configuration monitor")
  void stoppingClouderaManagerConfigMonitor();

  @Message(level = MessageLevel.INFO,
      text = "Stopped ClouderaManager cluster configuration monitor")
  void stoppedClouderaManagerConfigMonitor();

  @Message(level = MessageLevel.INFO,
           text = "Terminating monitoring of {1} @ {0} for configuration changes because there are no referencing descriptors.")
  void stoppingConfigMonitoring(String discoverySource, String clusterName);

  @Message(level = MessageLevel.WARN, text = "Missing property in previously saved service discovery configuration {0}")
  void missingServiceDiscoveryConfigProperty(String propertyName);

  @Message(level = MessageLevel.DEBUG, text = "There is no cluster configuration for {0} @ {1} to check yet.")
  void noClusterConfiguration(String clusterName, String discoveryAddress);

  @Message(level = MessageLevel.DEBUG, text = "Checking {0} @ {1} for configuration changes...")
  void checkingClusterConfiguration(String clusterName, String discoveryAddress);

  @Message(level = MessageLevel.ERROR, text = "Error while monitoring ClouderaManager configuration changes: {0}")
  void clouderaManagerConfigurationChangesMonitoringError(@StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Error getting service configuration details from ClouderaManager: {0}")
  void clouderaManagerConfigurationAPIError(@StackTrace(level = MessageLevel.DEBUG) ApiException e);

  @Message(level = MessageLevel.ERROR,
      text = "Error querying restart events from ClouderaManager: {0}")
  void clouderaManagerEventsAPIError(@StackTrace(level = MessageLevel.DEBUG) ApiException e);

  @Message(level = MessageLevel.ERROR,
           text = "Failed to persist data for cluster configuration monitor {0} {1}: {2}")
  void failedToPersistClusterMonitorData(String monitor,
                                         String filename,
                                         @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
           text = "Failed to load persisted service discovery configuration for cluster monitor {0} : {1}")
  void failedToLoadClusterMonitorServiceDiscoveryConfig(String monitor,
                                                        @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
           text = "Failed to load persisted service configuration data for cluster monitor {0} : {1}")
  void failedToLoadClusterMonitorServiceConfigurations(String monitor,
                                                       @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Failed to remove persisted data for cluster configuration monitor {0} {1}")
  void failedToRemovPersistedClusterMonitorData(String monitor, String filename);

  @Message(level = MessageLevel.DEBUG, text = "Getting current configuration for {0} from {1} @ {2}")
  void gettingCurrentClusterConfiguration(String serviceName,
                                          String clusterName,
                                          String discoveryAddress);

  @Message(level = MessageLevel.DEBUG,
          text = "Querying configuration activation events from {0} @ {1} since {2}")
  void queryingConfigActivationEventsFromCluster(String clusterName,
                                                 String discoveryAddress,
                                                 String sinceTimestamp);

  @Message(level = MessageLevel.DEBUG, text = "There is no any activation event found within the given time period")
  void noActivationEventFound();

  @Message(level = MessageLevel.DEBUG, text = "Activation event relevance: {0} = {1} ({2} / {3} / {4} / {5})")
  void activationEventRelevance(String eventId, String relevance, String command, String status, String serviceType, boolean serviceModelGeneratorExists);

  @Message(level = MessageLevel.DEBUG, text = "Activation event - {0} - has already been processed, skipping ...")
  void activationEventAlreadyProcessed(String eventId);

  @Message(level = MessageLevel.DEBUG, text = "Analyzing current {0} configuration for changes...")
  void analyzingCurrentServiceConfiguration(String serviceName);

  @Message(level = MessageLevel.ERROR, text = "Error analyzing current {0} configuration for changes: {1}")
  void errorAnalyzingCurrentServiceConfiguration(String serviceName,
                                                @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.INFO, text = "Service property {0} value has changed from {1} to {2}")
  void serviceConfigurationPropertyHasChanged(String propertyName,
                                              String previousValue,
                                              String currentValue);

  @Message(level = MessageLevel.INFO, text = "Role property {0} value has changed from {1} to {2}")
  void roleConfigurationPropertyHasChanged(String propertyName,
                                           String previousValue,
                                           String currentValue);

  @Message(level = MessageLevel.INFO,
           text = "The {0} service configuration has changed, such that it has been enabled for proxying.")
  void serviceEnabled(String serviceName);

  @Message(level = MessageLevel.INFO, text = "Role type {0} has been removed.")
  void roleTypeRemoved(String roleType);

  @Message(level = MessageLevel.WARN, text = "Failed to create persistence directory {0}")
  void failedToCreatePersistenceDirectory(String path);

  @Message(level = MessageLevel.ERROR, text = "Failed to configure truststore")
  void failedToConfigureTruststore();

  @Message(level = MessageLevel.DEBUG, text = "Looking up cluster services from service discovery repository...")
  void lookupClusterServicesFromRepository();

  @Message(level = MessageLevel.DEBUG, text = "Looking up service configuration from service discovery repository...")
  void lookupServiceConfigsFromRepository();

  @Message(level = MessageLevel.DEBUG, text = "Looking up roles from service discovery repository...")
  void lookupRolesFromRepository();

  @Message(level = MessageLevel.DEBUG, text = "Looking up role configuration from service discovery repository...")
  void lookupRoleConfigsFromRepository();

  @Message(level = MessageLevel.DEBUG, text = "Looking up cluster services from the configured Cloudera Manager discovery endpoint...")
  void lookupClusterServicesFromCM();

  @Message(level = MessageLevel.DEBUG, text = "Looking up service configuration from the configured Cloudera Manager discovery endpoint...")
  void lookupServiceConfigsFromCM();

  @Message(level = MessageLevel.DEBUG, text = "Looking up roles from the configured Cloudera Manager discovery endpoint...")
  void lookupRolesFromCM();

  @Message(level = MessageLevel.DEBUG, text = "No roles to look up for this service.")
  void noRoles();

  @Message(level = MessageLevel.DEBUG, text = "Looking up role configuration from the configured Cloudera Manager discovery endpoint...")
  void lookupRoleConfigsFromCM();

  @Message(level = MessageLevel.DEBUG, text = "Clearing service discovery repository...")
  void clearServiceDiscoveryRepository();

  @Message(level = MessageLevel.WARN, text = "The configured maximum retry attempts of {0} may overlap with the configured polling interval settings; using {1} retry attempts")
  void updateMaxRetryAttempts(int configured, int actual);
}

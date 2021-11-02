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
package org.apache.knox.gateway.topology.discovery.ambari;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger="org.apache.knox.gateway.topology.discovery.ambari")
public interface AmbariServiceDiscoveryMessages {

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
            text = "Failed to load persisted cluster configuration version data for cluster monitor {0} : {1}")
    void failedToLoadClusterMonitorConfigVersions(String monitor,
                                                  @StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.ERROR,
             text = "Unable to access the Ambari Configuration Change Monitor: {0}")
    void errorAccessingConfigurationChangeMonitor(@StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.ERROR,
             text = "Failed to load service discovery URL definition configuration: {0}")
    void failedToLoadServiceDiscoveryURLDefConfiguration(@StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.ERROR,
             text = "Failed to load ZooKeeper configuration property mappings: {0}")
    void failedToLoadZooKeeperConfigurationMapping(@StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.ERROR,
             text = "Failed to load service discovery URL definition configuration {0}: {1}")
    void failedToLoadServiceDiscoveryURLDefConfiguration(String configuration,
                                                         @StackTrace(level = MessageLevel.ERROR) Exception e);

    @Message(level = MessageLevel.ERROR,
             text = "Encountered an error during cluster ({0}) discovery: {1}")
    void clusterDiscoveryError(String clusterName, @StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.ERROR,
        text = "Failed to access the service configurations for cluster ({0}) discovery")
    void failedToAccessServiceConfigs(String clusterName);

    @Message(level = MessageLevel.ERROR,
             text = "REST invocation {0} timed out")
    void restInvocationTimedOut(String url, @StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.ERROR,
             text = "REST invocation {0} failed: {1}")
    void restInvocationError(String url, @StackTrace(level = MessageLevel.ERROR) Exception e);

    @Message(level = MessageLevel.ERROR,
            text = "No address for Ambari service discovery has been configured.")
    void missingDiscoveryAddress();

    @Message(level = MessageLevel.ERROR,
        text = "No cluster for Ambari service discovery has been configured.")
    void missingDiscoveryCluster();

    @Message(level = MessageLevel.ERROR,
            text = "Encountered an error attempting to determine the value for alias {0} : {1}")
    void aliasServiceError(String alias, String error);

    @Message(level = MessageLevel.ERROR,
             text = "Encountered an error attempting to determine the user for alias {0} : {1}")
    void aliasServiceUserError(String alias, String error);

    @Message(level = MessageLevel.ERROR,
             text = "Encountered an error attempting to determine the password for alias {0} : {1}")
    void aliasServicePasswordError(String alias, String error);

    @Message(level = MessageLevel.ERROR,
             text = "No user configured for Ambari service discovery.")
    void aliasServiceUserNotFound();

    @Message(level = MessageLevel.ERROR,
             text = "No password configured for Ambari service discovery.")
    void aliasServicePasswordNotFound();

    @Message(level = MessageLevel.ERROR,
             text = "Unexpected REST invocation response code for {0} : {1}")
    void unexpectedRestResponseStatusCode(String url, int responseStatusCode);

    @Message(level = MessageLevel.ERROR,
             text = "REST invocation {0} yielded a response without any JSON.")
    void noJSON(String url);

    @Message(level = MessageLevel.TRACE,
             text = "REST invocation result: {0}")
    void debugJSON(String json);

    @Message(level = MessageLevel.DEBUG,
             text = "Loaded component configuration mappings: {0}")
    void loadedComponentConfigMappings(String mappings);

    @Message(level = MessageLevel.ERROR,
             text = "Failed to load component configuration property mappings {0}: {1}")
    void failedToLoadComponentConfigMappings(String mappings,
                                             @StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.TRACE,
             text = "Discovered: Service: {0}, Host: {1}")
    void discoveredServiceHost(String serviceName, String hostName);

    @Message(level = MessageLevel.DEBUG,
             text = "Querying the cluster for the {0} configuration ({1}) property: {2}")
    void lookingUpServiceConfigProperty(String serviceName, String configType, String propertyName);

    @Message(level = MessageLevel.DEBUG,
             text = "Querying the cluster for the {0} component configuration property: {1}")
    void lookingUpComponentConfigProperty(String componentName, String propertyName);

    @Message(level = MessageLevel.DEBUG,
             text = "Querying the cluster for the {0} component's hosts")
    void lookingUpComponentHosts(String componentName);

    @Message(level = MessageLevel.DEBUG,
            text = "Handling a derived service URL mapping property for the {0} service: type = {1}, name = {2}")
    void handlingDerivedProperty(String serviceName, String propertyType, String propertyName);

    @Message(level = MessageLevel.DEBUG,
             text = "Determined the service URL mapping property {0} value: {1}")
    void determinedPropertyValue(String propertyName, String propertyValue);

    @Message(level = MessageLevel.INFO,
             text = "Started Ambari cluster configuration monitor (checking every {0} seconds)")
    void startedAmbariConfigMonitor(long pollingInterval);

    @Message(level = MessageLevel.WARN,
             text = "The declared nameservice {0} is not defined in the HDFS configuration.")
    void undefinedHDFSNameService(String nameservice);
}

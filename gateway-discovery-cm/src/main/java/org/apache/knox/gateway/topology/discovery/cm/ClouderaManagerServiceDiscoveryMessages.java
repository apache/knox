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

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger="org.apache.knox.gateway.topology.discovery.cm")
public interface ClouderaManagerServiceDiscoveryMessages {

  @Message(level = MessageLevel.INFO, text = "Discovered cluster: {0} ({1})")
  void discoveredCluster(String clusterName, String version);

  @Message(level = MessageLevel.INFO, text = "Performing cluster discovery for \"{0}\"")
  void discoveringCluster(String clusterName);

  @Message(level = MessageLevel.INFO, text = "Discovered service: {0} ({1})")
  void discoveredService(String serviceName, String serviceType);

  @Message(level = MessageLevel.INFO, text = "Discovered service role: {0} ({1})")
  void discoveredServiceRole(String roleName, String roleType);

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

  @Message(level = MessageLevel.ERROR,
      text = "Failed to access the service configurations for cluster ({0}) discovery")
  void failedToAccessServiceConfigs(String clusterName, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Failed to access the service role configurations for cluster ({0}) discovery")
  void failedToAccessServiceRoleConfigs(String clusterName, @StackTrace(level = MessageLevel.DEBUG) Exception e);

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

}

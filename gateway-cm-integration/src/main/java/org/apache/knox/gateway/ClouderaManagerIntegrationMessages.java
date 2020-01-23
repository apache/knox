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
package org.apache.knox.gateway;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger = "org.apache.knox.gateway")
public interface ClouderaManagerIntegrationMessages {

  @Message(level = MessageLevel.INFO, text = "Monitoring Cloudera Manager descriptors in {0} ...")
  void monitoringClouderaManagerDescriptor(String path);

  @Message(level = MessageLevel.INFO, text = "Parsing Cloudera Manager descriptor {0}. Looking up {1}...")
  void parseClouderaManagerDescriptor(String path, String topologyName);

  @Message(level = MessageLevel.INFO, text = "Found Knox descriptors {0} in {1}")
  void parsedClouderaManagerDescriptor(String descriptorList, String path);

  @Message(level = MessageLevel.INFO, text = "Ignoring {0} Knox descriptor update because it did not change.")
  void descriptorDidNotChange(String descriptorName);

  @Message(level = MessageLevel.ERROR, text = "Parsing Knox descriptor {0} failed: {1}")
  void failedToParseDescriptor(String name, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "Parsing XML configuration {0} failed: {1}")
  void failedToParseXmlConfiguration(String path, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "Error while monitoring CM descriptor {0}: {1}")
  void failedToMonitorClouderaManagerDescriptor(String descriptorPath, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "Error while producing Knox descriptor: {0}")
  void failedToProduceKnoxDescriptor(String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.WARN, text = "Service {0} is disabled. It will NOT be added in {1}")
  void serviceDisabled(String serviceName, String descriptorName);

  @Message(level = MessageLevel.INFO, text = "Updated advanced service discovery configuration for {0}.")
  void updatedAdvanceServiceDiscoverytConfiguration(String topologyName);
}

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
package org.apache.knox.gateway.topology.hadoop.xml;

import java.nio.file.attribute.FileTime;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger = "org.apache.knox.gateway")
public interface HadoopXmlResourceMessages {

  @Message(level = MessageLevel.INFO, text = "Monitoring Knox resources in Hadoop style XML configurations in {0} ...")
  void monitoringHadoopXmlResources(String path);

  @Message(level = MessageLevel.INFO, text = "Monitoring Knox resources in Hadoop style XML configurations is disabled.")
  void disableMonitoringHadoopXmlResources();

  @Message(level = MessageLevel.INFO, text = "Parsing  Knox resources in Hadoop style XML {0}...")
  void parseHadoopXmlResource(String path);

  @Message(level = MessageLevel.INFO, text = "Found Knox descriptors {0} in {1}")
  void foundKnoxDescriptors(String descriptorList, String path);

  @Message(level = MessageLevel.INFO, text = "Found Knox provider configurations {0} in {1}")
  void foundKnoxProviderConfigurations(String providerConfigurationList, String path);

  @Message(level = MessageLevel.INFO, text = "Saved Knox {0} into {1}")
  void savedResource(String resourceType, String path);

  @Message(level = MessageLevel.INFO, text = "Ignoring {0} Knox {1} update because it did not change.")
  void resourceDidNotChange(String resourceName, String resourceType);

  @Message(level = MessageLevel.ERROR, text = "Parsing Knox shared provider configuration {0} failed: {1}")
  void failedToParseProviderConfiguration(String name, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "Parsing Knox descriptor {0} failed: {1}")
  void failedToParseDescriptor(String name, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "Parsing XML configuration {0} failed: {1}")
  void failedToParseXmlConfiguration(String path, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.DEBUG, text = "Processing Hadoop XML resource {0} (force = {1}; lastReloadTime = {2}; lastModified = {3})")
  void processHadoopXmlResource(String descriptorPath, boolean force, FileTime lastReloadTime, FileTime lastModifiedTime);

  @Message(level = MessageLevel.DEBUG, text = "Skipping Hadoop XML resource monitoring of {0} (force = {1}; lastReloadTime = {2}; lastModified = {3})")
  void skipMonitorHadoopXmlResource(String descriptorPath, boolean force, FileTime lastReloadTime, FileTime lastModifiedTime);

  @Message(level = MessageLevel.ERROR, text = "Error while monitoring Hadoop style XML configuration {0}: {1}")
  void failedToMonitorHadoopXmlResource(String descriptorPath, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "Error while producing Knox descriptor: {0}")
  void failedToProduceKnoxDescriptor(String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "Error while producing Knox provider: {0}")
  void failedToProduceKnoxProvider(String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.WARN, text = "Service {0} is disabled. It will NOT be added in {1}")
  void serviceDisabled(String serviceName, String descriptorName);

  @Message(level = MessageLevel.INFO, text = "Updated advanced service discovery configuration for {0}.")
  void updatedAdvanceServiceDiscoverytConfiguration(String topologyName);

  @Message(level = MessageLevel.WARN, text = "Skipping read only descriptor: {0}.")
  void skipReadOnlyDescriptor(String name);

  @Message(level = MessageLevel.WARN, text = "Skipping read only provider: {0}.")
  void skipReadOnlyProvider(String key);

  @Message(level = MessageLevel.INFO, text = "Found deleted descriptors {0} in {1}")
  void foundKnoxDeletedDescriptors(String descriptorList, String path);

  @Message(level = MessageLevel.INFO, text = "Found deleted provider configurations {0} in {1}")
  void foundKnoxDeletedProviderConfigurations(String providerConfigurationList, String path);

  @Message(level = MessageLevel.INFO, text = "Deleting file {0}")
  void deleteFile(String name);

  @Message(level = MessageLevel.WARN, text = "Not deleting provider {0} as it is referenced by on ore more descriptors.")
  void notDeletingReferenceProvider(String provider);
}

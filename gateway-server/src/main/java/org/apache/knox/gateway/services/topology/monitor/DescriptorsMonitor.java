/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.services.topology.monitor;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.topology.impl.DefaultTopologyService;
import org.apache.knox.gateway.topology.simple.DiscoveryException;
import org.apache.knox.gateway.topology.simple.SimpleDescriptorHandler;

public class DescriptorsMonitor extends FileAlterationListenerAdaptor implements FileFilter {

  public static final List<String> SUPPORTED_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("json", "yml", "yaml"));

  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);

  private final GatewayConfig gatewayConfig;
  private final File topologiesDir;
  private final AliasService aliasService;
  private final Map<String, List<String>> providerConfigReferences = new HashMap<>();

  public DescriptorsMonitor(GatewayConfig config, File topologiesDir, AliasService aliasService) {
    this.gatewayConfig = config;
    this.topologiesDir = topologiesDir;
    this.aliasService = aliasService;
  }

  public List<String> getReferencingDescriptors(String providerConfigPath) {
    final String normalizedPath = FilenameUtils.normalize(providerConfigPath);
    return providerConfigReferences.computeIfAbsent(normalizedPath, p -> new ArrayList<>());
  }

  @Override
  public void onFileCreate(File file) {
    onFileChange(file);
  }

  @Override
  public void onFileDelete(File file) {
    // For simple descriptors, we need to make sure to delete any corresponding full topology descriptors to trigger undeployment
    for (String ext : DefaultTopologyService.SUPPORTED_TOPOLOGY_FILE_EXTENSIONS) {
      File topologyFile = new File(topologiesDir, FilenameUtils.getBaseName(file.getName()) + "." + ext);
      if (topologyFile.exists()) {
        LOG.deletingTopologyForDescriptorDeletion(topologyFile.getName(), file.getName());
        topologyFile.delete();
      }
    }

    final String normalizedFilePath = FilenameUtils.normalize(file.getAbsolutePath());
    String reference = null;
    for (Map.Entry<String, List<String>> entry : providerConfigReferences.entrySet()) {
      if (entry.getValue().contains(normalizedFilePath)) {
        reference = entry.getKey();
        break;
      }
    }

    if (reference != null) {
      providerConfigReferences.get(reference).remove(normalizedFilePath);
      LOG.removedProviderConfigurationReference(normalizedFilePath, reference);
    }
  }

  @Override
  public void onFileChange(File file) {
    try {
      // When a simple descriptor has been created or modified, generate the new topology descriptor
      Map<String, File> result = SimpleDescriptorHandler.handle(gatewayConfig, file, topologiesDir, aliasService, GatewayServer.getGatewayServices());
      LOG.generatedTopologyForDescriptorChange(result.get(SimpleDescriptorHandler.RESULT_TOPOLOGY).getName(), file.getName());

      // Add the provider config reference relationship for handling updates to the provider config
      String providerConfig = FilenameUtils.normalize(result.get(SimpleDescriptorHandler.RESULT_REFERENCE).getAbsolutePath());
      if (!providerConfigReferences.containsKey(providerConfig)) {
        providerConfigReferences.put(providerConfig, new ArrayList<>());
      }
      List<String> refs = providerConfigReferences.get(providerConfig);
      String descriptorName = FilenameUtils.normalize(file.getAbsolutePath());
      if (!refs.contains(descriptorName)) {
        // Need to check if descriptor had previously referenced another provider config, so it can be removed
        for (List<String> descs : providerConfigReferences.values()) {
          descs.remove(descriptorName);
        }

        // Add the current reference relationship
        refs.add(descriptorName);
        LOG.addedProviderConfigurationReference(descriptorName, providerConfig);
      }
    } catch (IllegalArgumentException e) {
      LOG.simpleDescriptorHandlingError(file.getName(), e);

      // If the referenced provider configuration is invalid, remove any existing reference relationships for the
      // referencing descriptor.
      String descriptorName = FilenameUtils.normalize(file.getAbsolutePath());
      // Need to check if descriptor had previously referenced another provider config, so it can be removed
      for (List<String> descs : providerConfigReferences.values()) {
        descs.remove(descriptorName);
      }
    } catch (DiscoveryException e) {
      LOG.failedToDiscoverClusterServices(e.getClusterName(), e.getTopologyName(), e);
    } catch (Exception e) {
      LOG.simpleDescriptorHandlingError(file.getName(), e);
    }
  }

  @Override
  public boolean accept(File file) {
    boolean accept = false;
    if (!file.isDirectory() && file.canRead()) {
      String extension = FilenameUtils.getExtension(file.getName());
      if (SUPPORTED_EXTENSIONS.contains(extension)) {
        accept = true;
      }
    }
    return accept;
  }
}

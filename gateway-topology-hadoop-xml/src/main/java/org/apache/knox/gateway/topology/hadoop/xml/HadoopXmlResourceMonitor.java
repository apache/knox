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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.topology.discovery.advanced.AdvancedServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.advanced.AdvancedServiceDiscoveryConfigChangeListener;
import org.apache.knox.gateway.util.JsonUtils;

/**
 * Monitoring KNOX_DESCRIPTOR_DIR for *.hxr files - which is a Hadoop XML configuration - and processing those files if they were modified
 * since the last time it they were processed
 */
public class HadoopXmlResourceMonitor implements AdvancedServiceDiscoveryConfigChangeListener {

  private static final String HADOOP_XML_RESOURCE_FILE_EXTENSION = ".hxr";
  private static final HadoopXmlResourceMessages LOG = MessagesFactory.get(HadoopXmlResourceMessages.class);
  private final String sharedProvidersDir;
  private final String descriptorsDir;
  private final String topologyDir;
  private final long monitoringInterval;
  private final HadoopXmlResourceParser hadoopXmlResourceParser;
  private final Map<Path, FileTime> lastReloadTimes;
  private final Lock monitorLock = new ReentrantLock();

  public HadoopXmlResourceMonitor(GatewayConfig gatewayConfig, HadoopXmlResourceParser hadoopXmlResourceParser) {
    this.hadoopXmlResourceParser = hadoopXmlResourceParser;
    this.sharedProvidersDir = gatewayConfig.getGatewayProvidersConfigDir();
    this.descriptorsDir = gatewayConfig.getGatewayDescriptorsDir();
    this.topologyDir = gatewayConfig.getGatewayTopologyDir();
    this.monitoringInterval = gatewayConfig.getClouderaManagerDescriptorsMonitoringInterval();
    this.lastReloadTimes = new ConcurrentHashMap<>();
  }

  public void setupMonitor() {
    if (monitoringInterval > 0) {
      final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(new BasicThreadFactory.Builder().namingPattern("ClouderaManagerDescriptorMonitor-%d").build());
      monitorClouderaManagerDescriptors(false); // call it explicitly first to generate descriptors up front, so that the health checker can pick them up
      executorService.scheduleAtFixedRate(() -> monitorClouderaManagerDescriptors(false), monitoringInterval, monitoringInterval, TimeUnit.MILLISECONDS);
      LOG.monitoringHadoopXmlResources(descriptorsDir);
    }
  }

  private void monitorClouderaManagerDescriptors(boolean force) {
    final File[] clouderaManagerDescriptorFiles = new File(descriptorsDir).listFiles((FileFilter) new SuffixFileFilter(HADOOP_XML_RESOURCE_FILE_EXTENSION));
    for (File clouderaManagerDescriptorFile : clouderaManagerDescriptorFiles) {
      monitorClouderaManagerDescriptor(Paths.get(clouderaManagerDescriptorFile.getAbsolutePath()), force);
    }
  }

  private void monitorClouderaManagerDescriptor(Path clouderaManagerDescriptorFile, boolean force) {
    monitorLock.lock();
    try {
      if (Files.isReadable(clouderaManagerDescriptorFile)) {
        final FileTime lastModifiedTime = Files.getLastModifiedTime(clouderaManagerDescriptorFile);
        FileTime lastReloadTime = lastReloadTimes.get(clouderaManagerDescriptorFile);
        if (force || lastReloadTime == null || lastReloadTime.compareTo(lastModifiedTime) < 0) {
          lastReloadTimes.put(clouderaManagerDescriptorFile, lastModifiedTime);
          LOG.processHadoopXmlResource(clouderaManagerDescriptorFile.toString(), force, lastReloadTime, lastModifiedTime);
          processClouderaManagerDescriptor(clouderaManagerDescriptorFile.toString());
        } else {
          LOG.skipMonitorHadoopXmlResource(clouderaManagerDescriptorFile.toString(), force, lastReloadTime, lastModifiedTime);
        }
      } else {
        LOG.failedToMonitorHadoopXmlResource(clouderaManagerDescriptorFile.toString(), "File is not readable!", null);
      }
    } catch (IOException e) {
      LOG.failedToMonitorHadoopXmlResource(clouderaManagerDescriptorFile.toString(), e.getMessage(), e);
    } finally {
      monitorLock.unlock();
    }
  }

  private void processClouderaManagerDescriptor(String descriptorFilePath) {
    final HadoopXmlResourceParserResult result = hadoopXmlResourceParser.parse(descriptorFilePath);
    processSharedProviders(result);
    processDescriptors(result);
    processDeleted(topologyDir, result.getDeletedDescriptors(), ".xml");
    processDeleted(descriptorsDir, result.getDeletedDescriptors(), ".json");
    processDeleted(sharedProvidersDir, result.getDeletedProviders(), ".json");
  }

  private void processDeleted(String parentDirectory, Set<String> deletedFileNames, String extension) {
    for (String each : deletedFileNames) {
      File fileToBeDeleted = new File(parentDirectory, each + extension);
      if (fileToBeDeleted.exists()) {
        LOG.deleteFile(fileToBeDeleted.getAbsolutePath());
        fileToBeDeleted.delete();
      }
    }
  }

  private void processSharedProviders(final HadoopXmlResourceParserResult result) {
    result.getProviders().forEach((key, value) -> {
      try {
        final File knoxProviderConfigFile = new File(sharedProvidersDir, key + ".json");
        final String providersConfiguration = JsonUtils.renderAsJsonString(value);
        if (isResourceChangedOrNew(knoxProviderConfigFile, providersConfiguration)) {
          FileUtils.writeStringToFile(knoxProviderConfigFile, providersConfiguration, StandardCharsets.UTF_8);
          LOG.savedResource("shared provider", knoxProviderConfigFile.getAbsolutePath());
        } else {
          LOG.resourceDidNotChange(key, "shared provider");
        }
      } catch (IOException e) {
        LOG.failedToProduceKnoxProvider(e.getMessage(), e);
      }
    });
  }

  private void processDescriptors(final HadoopXmlResourceParserResult result) {
    result.getDescriptors().forEach(simpleDescriptor -> {
      try {
        final File knoxDescriptorFile = new File(descriptorsDir, simpleDescriptor.getName() + ".json");
        final String simpleDescriptorJsonString = JsonUtils.renderAsJsonString(simpleDescriptor);
        if (isResourceChangedOrNew(knoxDescriptorFile, simpleDescriptorJsonString)) {
          FileUtils.writeStringToFile(knoxDescriptorFile, JsonUtils.renderAsJsonString(simpleDescriptor), StandardCharsets.UTF_8);
          LOG.savedResource("descriptor", knoxDescriptorFile.getAbsolutePath());
        } else {
          LOG.resourceDidNotChange(simpleDescriptor.getName(), "descriptor");
        }
      } catch (IOException e) {
        LOG.failedToProduceKnoxDescriptor(e.getMessage(), e);
      }
    });
  }

  private boolean isResourceChangedOrNew(File knoxDescriptorFile, String simpleDescriptorJsonString) throws IOException {
    if (knoxDescriptorFile.exists()) {
      final String currentContent = FileUtils.readFileToString(knoxDescriptorFile, StandardCharsets.UTF_8);
      return !simpleDescriptorJsonString.equals(currentContent);
    }
    return true;
  }

  @Override
  public void onAdvancedServiceDiscoveryConfigurationChange(Properties newConfiguration) {
    final String topologyName = new AdvancedServiceDiscoveryConfig(newConfiguration).getTopologyName();
    if (StringUtils.isBlank(topologyName)) {
      throw new IllegalArgumentException("Invalid advanced service discovery configuration: topology name is missing!");
    }
    monitorClouderaManagerDescriptors(true);
  }
}

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
package org.apache.knox.gateway.topology.discovery.advanced;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

/**
 * Monitoring <code>$KNOX_CONF_DIR/auto-discovery-advanced-configuration.properties</code> (if exists) and notifies any
 * {@link AdvancedServiceDiscoveryConfigChangeListener} if the file is changed since the last time it was loaded
 *
 */
public class AdvancedServiceDiscoveryConfigurationMonitor {

  private static final String ADVANCED_CONFIGURATION_FILE_NAME_PREFIX = "auto-discovery-advanced-configuration-";
  private static final AdvanceServiceDiscoveryConfigurationMessages LOG = MessagesFactory.get(AdvanceServiceDiscoveryConfigurationMessages.class);

  private final List<AdvancedServiceDiscoveryConfigChangeListener> listeners;
  private final String gatewayConfigurationDir;
  private final long monitoringInterval;
  private final Map<Path, FileTime> lastReloadTimes;

  public AdvancedServiceDiscoveryConfigurationMonitor(GatewayConfig gatewayConfig) {
    this.gatewayConfigurationDir = gatewayConfig.getGatewayConfDir();
    this.monitoringInterval = gatewayConfig.getClouderaManagerAdvancedServiceDiscoveryConfigurationMonitoringInterval();
    this.listeners = new ArrayList<>();
    this.lastReloadTimes = new ConcurrentHashMap<>();
  }

  public void init() {
    monitorAdvancedServiceConfigurations();
    setupMonitor();
  }

  private void setupMonitor() {
    if (monitoringInterval > 0) {
      final ScheduledExecutorService executorService = newSingleThreadScheduledExecutor(new BasicThreadFactory.Builder().namingPattern("AdvancedServiceDiscoveryConfigurationMonitor-%d").build());
      executorService.scheduleAtFixedRate(() -> monitorAdvancedServiceConfigurations(), 0, monitoringInterval, TimeUnit.MILLISECONDS);
      LOG.monitorStarted(gatewayConfigurationDir, ADVANCED_CONFIGURATION_FILE_NAME_PREFIX);
    }
  }

  public void registerListener(AdvancedServiceDiscoveryConfigChangeListener listener) {
    listeners.add(listener);
  }

  private void monitorAdvancedServiceConfigurations() {
    final File[] advancedConfigurationFiles = new File(gatewayConfigurationDir).listFiles((FileFilter) new PrefixFileFilter(ADVANCED_CONFIGURATION_FILE_NAME_PREFIX));
    if (advancedConfigurationFiles != null) {
      for (File advancedConfigurationFile : advancedConfigurationFiles) {
        monitorAdvancedServiceConfiguration(Paths.get(advancedConfigurationFile.getAbsolutePath()));
      }
    }
  }

  private void monitorAdvancedServiceConfiguration(Path resourcePath) {
    try {
      if (Files.exists(resourcePath) && Files.isReadable(resourcePath)) {
        FileTime lastModifiedTime = Files.getLastModifiedTime(resourcePath);
        FileTime lastReloadTime = lastReloadTimes.get(resourcePath);
        if (lastReloadTime == null || lastReloadTime.compareTo(lastModifiedTime) < 0) {
          lastReloadTimes.put(resourcePath, lastModifiedTime);
          try (InputStream advanceconfigurationFileInputStream = Files.newInputStream(resourcePath)) {
            Properties properties = new Properties();
            properties.load(advanceconfigurationFileInputStream);
            notifyListeners(resourcePath.toString(), properties);
          }
        }
      }
    } catch (IOException e) {
      LOG.failedToMonitorClouderaManagerAdvancedConfiguration(e.getMessage(), e);
    }
  }

  private void notifyListeners(String path, Properties properties) {
    LOG.notifyListeners(path);
    listeners.forEach(listener -> listener.onAdvancedServiceDiscoveryConfigurationChange(properties));
  }

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.monitor.db;

import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.topology.monitor.RemoteConfigurationMonitor;

public class DbRemoteConfigurationMonitor implements RemoteConfigurationMonitor {
  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  public static final int OFFSET_SECONDS = 5;
  private final RemoteConfigDatabase db;
  private final LocalDirectory providersDir;
  private final LocalDirectory descriptorsDir;
  private long intervalSeconds;
  private final ScheduledExecutorService executor;
  private Instant lastSyncTime;

  public DbRemoteConfigurationMonitor(RemoteConfigDatabase db, LocalDirectory providersDir, LocalDirectory descriptorsDir, long intervalSeconds) {
    this.db = db;
    this.providersDir = providersDir;
    this.descriptorsDir = descriptorsDir;
    this.executor = Executors.newSingleThreadScheduledExecutor();
    this.intervalSeconds = intervalSeconds;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {}

  @Override
  public void start() throws ServiceLifecycleException {
    LOG.startingDbRemoteConfigurationMonitor(intervalSeconds);
    executor.scheduleWithFixedDelay(this::sync, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    executor.shutdown();
  }

  @Override
  public boolean createProvider(String name, String content) {
    return db.putProvider(name, content);
  }

  @Override
  public boolean createDescriptor(String name, String content) {
    return db.putDescriptor(name, content);
  }

  @Override
  public boolean deleteProvider(String name) {
    return db.deleteProvider(name);
  }

  @Override
  public boolean deleteDescriptor(String name) {
    return db.deleteDescriptor(name);
  }

  public void sync() {
    try {
      syncLocalWithRemote(db.selectProviders(), providersDir);
      syncLocalWithRemote(db.selectDescriptors(), descriptorsDir);
      lastSyncTime = Instant.now();
    } catch (Exception e) {
      LOG.errorWhileSyncingLocalFileSystem(e);
    }
  }

  private void syncLocalWithRemote(List<RemoteConfig> remoteConfigs, LocalDirectory localDir) {
    createLocalFiles(remoteConfigs, localDir);
    deleteLocalFiles(remoteConfigs, localDir);
  }

  private void createLocalFiles(List<RemoteConfig> remoteConfigs, LocalDirectory localDir) {
    Set<String> localFiles = localDir.list();
    for (RemoteConfig remoteConfig : remoteConfigs) {
      try {
        String remoteContent = remoteConfig.getContent();
        if (!localFiles.contains(remoteConfig.getName())) {
          // if file does not exist locally, create it
          LOG.downloadingProviderDescriptor(remoteConfig.getName(), localDir);
          localDir.writeFile(remoteConfig.getName(), remoteContent);
        } else if (shouldUpdateContent(remoteConfig, localDir)) {
            // exists locally, overwrite content only if necessary
            LOG.downloadingProviderDescriptor(remoteConfig.getName(), localDir);
            localDir.writeFile(remoteConfig.getName(), remoteContent);
         }
      } catch (IOException e) {
        LOG.errorSynchronizingLocalProviderDescriptor(localDir, e);
      }
    }
  }

  private boolean shouldUpdateContent(RemoteConfig remoteConfig, LocalDirectory localDir) throws IOException {
      if (lastSyncTime == null || remoteConfig.getLastModified() // remote changed since last sync?
              .isAfter(lastSyncTime.minusSeconds(OFFSET_SECONDS))) {
      // Change in remote config can happen during a sync.
      // We apply an offset on lastSyncTime to make sure the changes are picked up at the next sync.
      // If a remote change happened after (lastSync-offset) we'll still sync. If it happened before (lastSync-offset) we won't.
      return !remoteConfig.getContent().equals(localDir.fileContent(remoteConfig.getName()));
    } else {
      return false;
    }
  }

  private void deleteLocalFiles(List<RemoteConfig> remoteConfigs, LocalDirectory localDir) {
    Set<String> remoteConfigNames = remoteConfigs.stream().map(RemoteConfig::getName).collect(toSet());
    for (String localFileName : localDir.list()) {
      if (!remoteConfigNames.contains(localFileName)) {
        LOG.deletingProviderDescriptor(localFileName, localDir);
        localDir.deleteFile(localFileName);
      }
    }
  }
}

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

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscoveryMessages;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.apache.knox.gateway.topology.discovery.cm.monitor.ClouderaManagerClusterConfigurationMonitor.getType;

public abstract class AbstractConfigurationStore {

  // The name of the directory, under the gateway data directory, in which files will be persisted
  static final String CLUSTERS_DATA_DIR_NAME = getType().toLowerCase(Locale.ROOT) + "-clusters";

  protected static final ClouderaManagerServiceDiscoveryMessages log =
                      MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  protected GatewayConfig gatewayConfig;

  public AbstractConfigurationStore(GatewayConfig gatewayConfig) {
    this.gatewayConfig = gatewayConfig;
  }

  public void remove(String address, String cluster) {
    // Delete the associated persisted record
    File persisted = getPersistenceFile(address, cluster);
    if (persisted.exists()) {
      if (!persisted.delete()) {
        log.failedToRemovPersistedClusterMonitorData(ClouderaManagerClusterConfigurationMonitor.getType(),
                                                     persisted.getAbsolutePath());
      }
    }
  }

  protected abstract File getPersistenceFile(String address, String cluster);

  protected String getMonitorType() {
    return getType();
  }

  protected File getPersistenceFile(final String address, final String clusterName, final String ext) {
    String fileName = address.replace(":", "_").replace("/", "_") + "-" + clusterName + "." + ext;
    return getPersistenceDir().resolve(fileName).toFile();
  }

  protected Path getPersistenceDir() {
    Path persistenceDir = null;

    Path dataDir = Paths.get(gatewayConfig.getGatewayDataDir());
    if (Files.exists(dataDir)) {
      Path clustersDir = dataDir.resolve(CLUSTERS_DATA_DIR_NAME);
      if (Files.notExists(clustersDir)) {
        try {
          Files.createDirectories(clustersDir);
        } catch (IOException e) {
          log.failedToCreatePersistenceDirectory(clustersDir.toAbsolutePath().toString());
        }
      }
      persistenceDir = clustersDir;
    }

    return persistenceDir;
  }
}

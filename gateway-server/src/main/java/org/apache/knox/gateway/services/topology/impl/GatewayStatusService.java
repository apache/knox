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
package org.apache.knox.gateway.services.topology.impl;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;

public class GatewayStatusService implements Service {
  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  private final Set<String> deployedTopologies = new HashSet<>();
  private Set<String> topologyNamesToCheck = new HashSet<>();
  private GatewayConfig config;

  public synchronized void onTopologyReady(String topologyName) {
    deployedTopologies.add(topologyName);
  }

  public synchronized boolean status() {
    if (topologyNamesToCheck.isEmpty()) {
      LOG.noTopologiesToCheck();
      return false;
    }
    Set<String> missing = new HashSet<>(topologyNamesToCheck);
    missing.removeAll(deployedTopologies);
    LOG.checkingGatewayStatus(deployedTopologies, missing);
    return missing.isEmpty();
  }

  /**
   * The list of topologies (which will be used to check the gateway status.)
   * are either coming from the config or collected automatically.
   * In the later case this should be called at startup, after the hadoop xml resource parser
   * already generated the descriptors from the hxr
   */
  public synchronized void initTopologiesToCheck() {
    Set<String> healthCheckTopologies = config.getHealthCheckTopologies();
    if (healthCheckTopologies.isEmpty()) {
      topologyNamesToCheck = collectTopologies(config);
    } else {
      topologyNamesToCheck = healthCheckTopologies;
    }
    LOG.startingStatusMonitor(topologyNamesToCheck);
  }

  private Set<String> collectTopologies(GatewayConfig config) {
    Set<String> result = new HashSet<>();
    collectFiles(result, config.getGatewayTopologyDir(), ".xml");
    collectFiles(result, config.getGatewayDescriptorsDir(), ".json");
    LOG.collectedTopologiesForHealthCheck(result);
    return result;
  }

  private void collectFiles(Set<String> result, String directory, String extension) {
    File[] files = new File(directory).getAbsoluteFile()
            .listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(extension));
    if (files != null) {
      for (File file : files) {
        result.add(FilenameUtils.getBaseName(file.getName()));
      }
    }
  }
  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    this.config = config;
    // this is soon to collect the topologies, topologies are collected in initTopologiesToCheck
  }

  @Override
  public void start() throws ServiceLifecycleException {}

  @Override
  public void stop() throws ServiceLifecycleException {}
}

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

import java.util.Map;

/**
 * Model of the persisted data for a ClouderaManager-managed cluster service configurations.
 */
final class ServiceConfigurationRecord {
  private String clusterName;
  private String discoveryAddress;

  // Map of services to their associated configuration models
  private Map<String, ServiceConfigurationModel> configs;


  public void setClusterName(final String clusterName) {
    this.clusterName = clusterName;
  }

  public void setDiscoveryAddress(final String discoveryAddress) {
    this.discoveryAddress = discoveryAddress;
  }

  public void setConfigs(Map<String, ServiceConfigurationModel> configs) {
    this.configs = configs;
  }

  public String getClusterName() {
    return clusterName;
  }

  public String getDiscoveryAddress() {
    return discoveryAddress;
  }

  public Map<String, ServiceConfigurationModel> getConfigs() {
    return configs;
  }
}

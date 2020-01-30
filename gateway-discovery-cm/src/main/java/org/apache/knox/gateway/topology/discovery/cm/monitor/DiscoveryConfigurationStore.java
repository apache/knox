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

import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;

import java.util.Set;

/**
 * Interface for managing the persistence of discovery configurations.
 */
public interface DiscoveryConfigurationStore {

  /**
   * Store the specified configuration.
   *
   * @param config A ServiceDiscoveryConfig
   */
  void store(ServiceDiscoveryConfig config);

  /**
   * Get all the stored discovery configurations.
   *
   * @return A Set of ServiceDiscoveryConfig objects
   */
  Set<ServiceDiscoveryConfig> getAll();

  /**
   * Remove the discovery configuration identified by the specified discovery address and cluster name.
   *
   * @param address The discovery address
   * @param cluster The cluster name
   */
  void remove(String address, String cluster);

}

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
import java.util.Set;

/**
 * Interface for managing the persistence of cluster configuration data.
 */
public interface ClusterConfigurationStore {

  /**
   * Store the configuration for the specified cluster.
   *
   * @param address The address of the ClouderaManager instance managing the cluster
   * @param cluster The name of the cluster
   * @param configs The cluster configuration
   */
  void store(String address, String cluster, Map<String, ServiceConfigurationModel> configs);

  /**
   * Get all the stored cluster configurations.
   *
   * @return A Set of all the stored configurations
   */
  Set<ServiceConfigurationRecord> getAll();

  /**
   * Get the stored configuration for the specified cluster.
   *
   * @param address The address of the ClouderaManager instance managing the cluster
   * @param cluster The name of the cluster
   *
   * @return A ServiceConfigurationRecord object
   */
  ServiceConfigurationRecord get(String address, String cluster);

  /**
   * Remove the configuration for the specified cluster.
   *
   * @param address The address of the ClouderaManager instance managing the cluster
   * @param cluster The name of the cluster
   */
  void remove(String address, String cluster);

}

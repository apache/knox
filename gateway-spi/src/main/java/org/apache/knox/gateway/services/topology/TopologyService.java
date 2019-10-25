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
package org.apache.knox.gateway.services.topology;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.service.definition.ServiceDefinitionChangeListener;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.topology.TopologyListener;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;


public interface TopologyService extends Service, ServiceDefinitionChangeListener {

  void reloadTopologies();

  void deployTopology(Topology t);

  void redeployTopologies(String topologyName);

  void addTopologyChangeListener(TopologyListener listener);

  void startMonitor() throws Exception;

  void stopMonitor() throws Exception;

  Collection<Topology> getTopologies();

  boolean deployProviderConfiguration(String name, String content);

  Collection<File> getProviderConfigurations();

  boolean deployDescriptor(String name, String content);

  Collection<File> getDescriptors();

  void deleteTopology(Topology t);

  boolean deleteDescriptor(String name);

  boolean deleteProviderConfiguration(String name);

  boolean deleteProviderConfiguration(String name, boolean force);

  Map<String, List<String>> getServiceTestURLs(Topology t, GatewayConfig config);

}

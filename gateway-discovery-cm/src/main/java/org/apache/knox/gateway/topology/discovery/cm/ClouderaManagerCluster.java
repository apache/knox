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
package org.apache.knox.gateway.topology.discovery.cm;

import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.cm.collector.ServiceURLCollectors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ClouderaManager-based service discovery cluster model implementation.
 */
public class ClouderaManagerCluster implements ServiceDiscovery.Cluster {

  private String name;

  private Map<String, List<ServiceModel>> serviceModels = new HashMap<>();

  ClouderaManagerCluster(String clusterName) {
    this.name = clusterName;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<String> getServiceURLs(String serviceName) {
    return getServiceURLs(serviceName, null);
  }

  @Override
  public List<String> getServiceURLs(String serviceName, Map<String, String> serviceParams) {
    List<String> urls = new ArrayList<>();

    if (serviceModels.containsKey(serviceName)) {
      Map<String, List<ServiceModel>> roleModels = new HashMap<>();
      for (ServiceModel model : serviceModels.get(serviceName)) {

        // Check for discovery qualifier attributes of the service model
        boolean isMatchingModel = true;
        if (serviceParams != null) {
          for (Map.Entry<String, String> serviceParam : serviceParams.entrySet()) {
            if (!serviceParam.getValue().equals(model.getQualifyingServiceParam(serviceParam.getKey()))) {
              isMatchingModel = false;
            }
          }
        }

        if (isMatchingModel) {
          roleModels.computeIfAbsent(model.getRoleType(), l -> new ArrayList<>()).add(model);
        }
      }

      urls.addAll((ServiceURLCollectors.getCollector(serviceName)).collect(roleModels));
    }
    return urls;
  }

  @Override
  public ZooKeeperConfig getZooKeeperConfiguration(String serviceName) {
    return null;
  }

  void addServiceModels(Set<ServiceModel> serviceModels) {
    for (ServiceModel model : serviceModels) {
      this.serviceModels.computeIfAbsent(model.getService(), l -> new ArrayList<>()).add(model);
    }
  }

  public Map<String, List<ServiceModel>> getServiceModels() {
    return serviceModels;
  }

}

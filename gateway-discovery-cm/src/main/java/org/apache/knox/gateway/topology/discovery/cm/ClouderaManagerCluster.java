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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ClouderaManager-based service discovery cluster model implementation.
 */
public class ClouderaManagerCluster implements ServiceDiscovery.Cluster {

  private String name;

  private Set<ServiceModel> serviceModels = new HashSet<>();

  ClouderaManagerCluster(String clusterName) {
    this.name = clusterName;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<String> getServiceURLs(String serviceName) {
    List<String> urls = new ArrayList<>();

    for (ServiceModel sm : serviceModels) {
      if (sm.getService().equals(serviceName)) {
        urls.add(sm.getServiceUrl());
      }
    }

    return urls;
  }

  @Override
  public List<String> getServiceURLs(String serviceName, Map<String, String> serviceParams) {
    return getServiceURLs(serviceName); // TODO: PJZ: Support things like HDFS nameservice params for providing the correct URL(s)?
  }

  @Override
  public ZooKeeperConfig getZooKeeperConfiguration(String serviceName) {
    return null;
  }

  void addServiceModels(Set<ServiceModel> serviceModels) {
    this.serviceModels.addAll(serviceModels);
  }

  static class ServiceConfiguration {

    private String type;
    private String version;
    private Map<String, String> props;

    ServiceConfiguration(String type, String version, Map<String, String> properties) {
      this.type = type;
      this.version = version;
      this.props = properties;
    }

    public String getVersion() {
      return version;
    }

    public String getType() {
      return type;
    }

    public Map<String, String> getProperties() {
      return props;
    }
  }




}

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
package org.apache.knox.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DescriptorConfiguration {
  @JsonProperty("discovery-type")
  private String discoveryType;

  @JsonProperty("discovery-address")
  private String discoveryAddress;

  @JsonProperty("discovery-user")
  private String discoveryUser;

  @JsonProperty("discovery-pwd-alias")
  private String discoveryPasswordAlias;

  @JsonProperty("provider-config-ref")
  private String providerConfig;

  @JsonProperty("cluster")
  private String cluster;

  @JsonProperty("services")
  private List<Topology.Service> services;

  @JsonProperty("applications")
  private List<Topology.Application> applications;

  private String name;

  public void setName(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public DescriptorConfiguration() {
    super();
  }

  public String getDiscoveryType() {
    return discoveryType;
  }

  public void setDiscoveryType(String discoveryType) {
    this.discoveryType = discoveryType;
  }

  public String getDiscoveryAddress() {
    return discoveryAddress;
  }

  public void setDiscoveryAddress(String discoveryAddress) {
    this.discoveryAddress = discoveryAddress;
  }

  public String getDiscoveryUser() {
    return discoveryUser;
  }

  public void setDiscoveryUser(String discoveryUser) {
    this.discoveryUser = discoveryUser;
  }

  public String getDiscoveryPasswordAlias() {
    return discoveryPasswordAlias;
  }

  public void setDiscoveryPasswordAlias(String discoveryPasswordAlias) {
    this.discoveryPasswordAlias = discoveryPasswordAlias;
  }

  public String getProviderConfig() {
    return providerConfig;
  }

  public void setProviderConfig(String providerConfig) {
    this.providerConfig = providerConfig;
  }

  public String getCluster() {
    return cluster;
  }

  public void setCluster(String cluster) {
    this.cluster = cluster;
  }

  public List<Topology.Service> getServices() {
    return services;
  }

  public void setServices(List<Topology.Service> services) {
    this.services = services;
  }

  public List<Topology.Application> getApplications() {
    return applications;
  }

  public void setApplications(List<Topology.Application> applications) {
    this.applications = applications;
  }
}

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
package org.apache.knox.gateway.service.metadata;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "topology")
public class TopologyInformation implements Comparable<TopologyInformation>{

  @XmlElement(name = "topology")
  private String topologyName;

  @XmlElement(name = "pinned")
  private boolean pinned;

  @XmlElement(name = "service")
  @XmlElementWrapper(name = "apiServices")
  private Set<ServiceModel> apiServices;

  @XmlElement(name = "service")
  @XmlElementWrapper(name = "uiServices")
  private Set<ServiceModel> uiServices;

  public String getTopologyName() {
    return topologyName;
  }

  public void setTopologyName(String topologyName) {
    this.topologyName = topologyName;
  }

  public boolean isPinned() {
    return pinned;
  }

  public void setPinned(boolean pinned) {
    this.pinned = pinned;
  }

  public Set<ServiceModel> getApiServices() {
    return apiServices;
  }

  public void setApiServices(Set<ServiceModel> apiServices) {
    this.apiServices = apiServices;
  }

  public Set<ServiceModel> getUiServices() {
    return uiServices;
  }

  public void setUiServices(Set<ServiceModel> uiServices) {
    this.uiServices = uiServices;
  }

  @Override
  public int compareTo(TopologyInformation other) {
    final int byPinned =  Boolean.compare(other.pinned, pinned);
    return byPinned == 0 ? topologyName.compareTo(other.topologyName) : byPinned;
  }

}

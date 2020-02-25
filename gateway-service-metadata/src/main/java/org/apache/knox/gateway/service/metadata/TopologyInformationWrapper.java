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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

@XmlAccessorType(XmlAccessType.NONE)
public class TopologyInformationWrapper {

  @XmlElement(name = "topologyInformation")
  @XmlElementWrapper(name = "topologyInformations")
  private Set<TopologyInformation> topologies = new HashSet<>();

  public Set<TopologyInformation> getTopologies() {
    return topologies;
  }

  public void addTopology(String name, List<ServiceModel> apiServices, List<ServiceModel> uiServices) {
    final TopologyInformation topology = new TopologyInformation();
    topology.setTopologyName(name);
    topology.setApiServices(apiServices);
    topology.setUiServices(uiServices);
    this.topologies.add(topology);
  }

}

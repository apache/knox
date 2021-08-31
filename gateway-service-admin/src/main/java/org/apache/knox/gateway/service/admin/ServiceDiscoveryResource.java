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

package org.apache.knox.gateway.service.admin;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(value = "serviceDiscovery",  description = "The Knox Admin API to interact with service discovery information.")
@Path("/api/v1")
public class ServiceDiscoveryResource {

  @ApiOperation(value="Get available service discoveries", notes="Get available service discoveriy types (Ambari, ClouderaManager, etc...) and their implementation classes", response=ServiceDiscoveryWrapper.class)
  @GET
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  @Path("servicediscoveries")
  public ServiceDiscoveryWrapper getServiceDiscoveries() {
    final ServiceDiscoveryWrapper serviceDiscoveryWrapper = new ServiceDiscoveryWrapper();
    serviceDiscoveryWrapper.setKnoxServiceDiscoveries(ServiceDiscoveryFactory.getAllServiceDiscoveries());
    return serviceDiscoveryWrapper;
  }

  @XmlAccessorType(XmlAccessType.NONE)
  public static class KnoxServiceDiscovery {

    @XmlElement
    private final String type;

    @XmlElement
    private final String implementation;

    // having a no-argument constructor is required by JAXB
    public KnoxServiceDiscovery() {
      this(null, null);
    }

    public KnoxServiceDiscovery(String type, String implementation) {
      this.type = type;
      this.implementation = implementation;
    }

    public String getType() {
      return type;
    }

    public String getImplementation() {
      return implementation;
    }

  }

  @XmlAccessorType(XmlAccessType.FIELD)
  public static class ServiceDiscoveryWrapper {

    @XmlElement(name = "knoxServiceDiscovery")
    @XmlElementWrapper(name = "knoxServiceDiscoveries")
    private Set<KnoxServiceDiscovery> knoxServiceDiscoveries = new HashSet<>();

    public Set<KnoxServiceDiscovery> getKnoxServiceDiscoveries() {
      return knoxServiceDiscoveries;
    }

    public void setKnoxServiceDiscoveries(Set<KnoxServiceDiscovery> knoxServiceDiscoveries) {
      this.knoxServiceDiscoveries = knoxServiceDiscoveries;
    }

    void setKnoxServiceDiscoveries(Collection<ServiceDiscovery> serviceDiscoveries) {
      serviceDiscoveries.forEach(serviceDiscovery -> this.knoxServiceDiscoveries.add(
          new KnoxServiceDiscovery(serviceDiscovery.getType(), serviceDiscovery.getClass().getCanonicalName())));
    }
  }
}

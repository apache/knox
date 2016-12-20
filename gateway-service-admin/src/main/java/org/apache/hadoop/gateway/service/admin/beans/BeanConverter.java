/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.service.admin.beans;

import org.apache.hadoop.gateway.topology.Version;

import java.util.Collection;

public class BeanConverter {

  public static Topology getTopology(org.apache.hadoop.gateway.topology.Topology topology) {
    Topology topologyResource = new Topology();
    topologyResource.setName(topology.getName());
    topologyResource.setTimestamp(topology.getTimestamp());
    topologyResource.setUri(topology.getUri());
    for ( org.apache.hadoop.gateway.topology.Provider provider : topology.getProviders() ) {
      topologyResource.getProviders().add( getProvider(provider) );
    }
    for ( org.apache.hadoop.gateway.topology.Service service : topology.getServices() ) {
      topologyResource.getServices().add( getService(service) );
    }
    for ( org.apache.hadoop.gateway.topology.Application application : topology.getApplications() ) {
      topologyResource.getApplications().add( getApplication(application) );
    }
    return topologyResource;
  }

  public static org.apache.hadoop.gateway.topology.Topology getTopology(Topology topology) {
    org.apache.hadoop.gateway.topology.Topology deploymentTopology = new org.apache.hadoop.gateway.topology.Topology();
    deploymentTopology.setName(topology.getName());
    deploymentTopology.setTimestamp(topology.getTimestamp());
    deploymentTopology.setUri(topology.getUri());
    for ( Provider provider : topology.getProviders() ) {
      deploymentTopology.addProvider( getProvider(provider) );
    }
    for ( Service service : topology.getServices() ) {
      deploymentTopology.addService( getService(service) );
    }
    for ( Application application : topology.getApplications() ) {
      deploymentTopology.addApplication( getApplication(application) );
    }
    return deploymentTopology;
  }

  private static Provider getProvider(org.apache.hadoop.gateway.topology.Provider provider) {
    Provider providerResource = new Provider();
    providerResource.setName(provider.getName());
    providerResource.setEnabled(provider.isEnabled());
    providerResource.setRole(provider.getRole());
    Collection<org.apache.hadoop.gateway.topology.Param> paramsList = provider.getParamsList();
    if (paramsList != null && !paramsList.isEmpty()) {
      for ( org.apache.hadoop.gateway.topology.Param param : paramsList ) {
        providerResource.getParams().add(getParam(param));
      }
    }
    return providerResource;
  }

  private static org.apache.hadoop.gateway.topology.Provider getProvider(Provider provider) {
    org.apache.hadoop.gateway.topology.Provider deploymentProvider = new org.apache.hadoop.gateway.topology.Provider();
    deploymentProvider.setName(provider.getName());
    deploymentProvider.setEnabled(provider.isEnabled());
    deploymentProvider.setRole(provider.getRole());
    for ( Param param : provider.getParams() ) {
      deploymentProvider.addParam( getParam(param) );
    }
    return deploymentProvider;
  }

  private static Service getService(org.apache.hadoop.gateway.topology.Service service) {
    Service serviceResource = new Service();
    serviceResource.setRole(service.getRole());
    serviceResource.setName(service.getName());
    Version version = service.getVersion();
    if (version != null) {
      serviceResource.setVersion(version.toString());
    }
    Collection<org.apache.hadoop.gateway.topology.Param> paramsList = service.getParamsList();
    if (paramsList != null && !paramsList.isEmpty()) {
      for ( org.apache.hadoop.gateway.topology.Param param : paramsList ) {
        serviceResource.getParams().add(getParam(param));
      }
    }
    for ( String url : service.getUrls() ) {
      serviceResource.getUrls().add( url );
    }
    return serviceResource;
  }

  private static org.apache.hadoop.gateway.topology.Service getService(Service service) {
    org.apache.hadoop.gateway.topology.Service deploymentService = new org.apache.hadoop.gateway.topology.Service();
    deploymentService.setRole(service.getRole());
    deploymentService.setName(service.getName());
    if (service.getVersion() != null) {
      deploymentService.setVersion(new Version(service.getVersion()));
    }
    for ( Param param : service.getParams() ) {
      deploymentService.addParam( getParam(param) );
    }
    for ( String url : service.getUrls() ) {
      deploymentService.addUrl( url );
    }
    return deploymentService;
  }

  private static Application getApplication(org.apache.hadoop.gateway.topology.Application application) {
    Application applicationResource = new Application();
    applicationResource.setRole(application.getRole());
    applicationResource.setName(application.getName());
    Version version = application.getVersion();
    if (version != null) {
      applicationResource.setVersion(version.toString());
    }
    Collection<org.apache.hadoop.gateway.topology.Param> paramsList = application.getParamsList();
    if (paramsList != null && !paramsList.isEmpty()) {
      for ( org.apache.hadoop.gateway.topology.Param param : paramsList ) {
        applicationResource.getParams().add(getParam(param));
      }
    }
    for ( String url : application.getUrls() ) {
      applicationResource.getUrls().add( url );
    }
    return applicationResource;
  }

  private static org.apache.hadoop.gateway.topology.Application getApplication(Application application) {
    org.apache.hadoop.gateway.topology.Application applicationResource = new org.apache.hadoop.gateway.topology.Application();
    applicationResource.setRole(application.getRole());
    applicationResource.setName(application.getName());
    if (application.getVersion() != null) {
      applicationResource.setVersion(new Version(application.getVersion()));
    }
    for ( Param param : application.getParams() ) {
      applicationResource.addParam( getParam(param) );
    }
    for ( String url : application.getUrls() ) {
      applicationResource.getUrls().add( url );
    }
    return applicationResource;
  }

  private static Param getParam(org.apache.hadoop.gateway.topology.Param param) {
    return new Param(param.getName(), param.getValue());
  }

  private static org.apache.hadoop.gateway.topology.Param getParam(Param param) {
    return new org.apache.hadoop.gateway.topology.Param(param.getName(), param.getValue());
  }
}

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
package org.apache.knox.gateway.deploy;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeploymentContextImpl implements DeploymentContext {

  private GatewayConfig gatewayConfig;
  private Topology topology;
  private GatewayDescriptor gatewayDescriptor;
  private WebArchive webArchive;
  private WebAppDescriptor webAppDescriptor;
  Map<String,List<ProviderDeploymentContributor>> providers;
  private Map<String,Object> descriptors;

  public DeploymentContextImpl(
      GatewayConfig gatewayConfig,
      Topology topology,
      GatewayDescriptor gatewayDescriptor,
      WebArchive webArchive,
      WebAppDescriptor webAppDescriptor,
      Map<String,List<ProviderDeploymentContributor>> providers ) {
    this.gatewayConfig = gatewayConfig;
    this.topology = topology;
    this.gatewayDescriptor = gatewayDescriptor;
    this.webArchive = webArchive;
    this.webAppDescriptor = webAppDescriptor;
    this.providers = providers;
    this.descriptors = new HashMap<>();
  }

//  @Override
//  public ServiceDeploymentContributor getServiceContributor( String role, String name ) {
//    ServiceDeploymentContributor contributor = null;
//    if( name == null ) {
//      List<ServiceDeploymentContributor> list = services.get( role );
//      if( !list.isEmpty() ) {
//        contributor = list.get( 0 );
//      }
//    } else {
//      contributor = DeploymentFactory.getServiceContributor( role, name );
//    }
//    return contributor;
//  }

//  @Override
//  public ResourceDescriptorFactory getResourceDescriptorFactory( Service service ) {
//    return GatewayDescriptorFactory.getResourceDescriptorFactory( service );
//  }
//
//  @Override
//  public FilterDescriptorFactory getFilterDescriptorFactory( String filterRole ) {
//    return GatewayDescriptorFactory.getFilterDescriptorFactory( filterRole );
//  }

  @Override
  public GatewayConfig getGatewayConfig() {
    return gatewayConfig;
  }

  @Override
  public Topology getTopology() {
    return topology;
  }

  @Override
  public WebArchive getWebArchive() {
    return webArchive;
  }

  @Override
  public WebAppDescriptor getWebAppDescriptor() {
    return webAppDescriptor;
  }

  @Override
  public GatewayDescriptor getGatewayDescriptor() {
    return gatewayDescriptor;
  }

  @Override
  public void contributeFilter(
      Service service,
      ResourceDescriptor resource,
      String role,
      String name,
      List<FilterParamDescriptor> params ) {
    ProviderDeploymentContributor contributor = DeploymentFactory.getProviderContributor( providers, role, name );
    Provider provider = getTopology().getProvider( role, name );
    if( provider == null ) {
      provider = new Provider();
      provider.setRole( role );
      provider.setName( name );
      provider.setEnabled( true );
    }
    if( provider.isEnabled() ) {
      contributor.contributeFilter( this, provider, service, resource, params );
    }
  }

  @Override
  public void addDescriptor( String name, Object descriptor ) {
    descriptors.put( name, descriptor );
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getDescriptor( String name ) {
    return (T)descriptors.get( name );
  }

}

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
package org.apache.knox.gateway.svcregfunc.impl;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFunctionDescriptor;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.HaServletContextListener;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.registry.ServiceRegistry;

abstract class ServiceRegistryFunctionProcessorBase<T extends UrlRewriteFunctionDescriptor> implements UrlRewriteFunctionProcessor<T> {

  private String cluster;
  private GatewayServices services;
  private ServiceRegistry registry;
  private HaProvider haProvider;

  @Override
  public void initialize( UrlRewriteEnvironment environment, T descriptor ) throws Exception {
    if( environment == null ) {
      throw new IllegalArgumentException( "environment==null" );
    }
    cluster = environment.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE );
    if( cluster == null ) {
      throw new IllegalArgumentException( "cluster==null" );
    }
    services = environment.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE );
    if( services == null ) {
      throw new IllegalArgumentException( "services==null" );
    }
    registry = services.getService( ServiceType.SERVICE_REGISTRY_SERVICE );
    if( registry == null ) {
      throw new IllegalArgumentException( "registry==null" );
    }
    haProvider = environment.getAttribute(HaServletContextListener.PROVIDER_ATTRIBUTE_NAME);
  }

  @Override
  public void destroy() throws Exception {
    registry = null;
    cluster = null;
  }

  public String lookupServiceUrl( String role ) throws Exception {
    if (haProvider != null && haProvider.isHaEnabled(role)) {
       return haProvider.getActiveURL(role);
    }
    return registry.lookupServiceURL( cluster, role );
  }

  String cluster() {
    return cluster;
  }

  GatewayServices services() {
    return services;
  }

  ServiceRegistry registry() {
    return registry;
  }

}


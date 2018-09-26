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
package org.apache.knox.gateway.deploy.impl;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.knox.gateway.descriptor.FilterDescriptor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.dispatch.GatewayDispatchFilter;
import org.apache.knox.gateway.dispatch.DefaultDispatch;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;

import java.util.List;
import java.util.Map;

public class DispatchDeploymentContributor extends
    ProviderDeploymentContributorBase {
  
  private static final String DISPATCH_IMPL_PARAM = "dispatch-impl";

  @Override
  public String getRole() {
    return "dispatch";
  }

  @Override
  public String getName() {
    return "http-client";
  }

  @Override
  public void contributeFilter( DeploymentContext context, Provider provider, Service service, ResourceDescriptor resource, List<FilterParamDescriptor> params ) {
    FilterDescriptor filter = resource.addFilter().name( getName() ).role( getRole() ).impl( GatewayDispatchFilter.class );
    filter.param().name(DISPATCH_IMPL_PARAM).value(DefaultDispatch.class.getName());
    for ( Map.Entry<String,String> serviceParam : service.getParams().entrySet() ) {
      filter.param().name( serviceParam.getKey() ).value( serviceParam.getValue() );
    }
    if( context.getGatewayConfig().isHadoopKerberosSecured() ) {
      filter.param().name("kerberos").value("true");
    }
  }

}

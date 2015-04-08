/**
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
package org.apache.hadoop.gateway.deploy.impl;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterDescriptor;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.dispatch.GatewayDispatchFilter;
import org.apache.hadoop.gateway.dispatch.DefaultDispatch;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;

import java.util.List;
import java.util.Map;

public class DispatchDeploymentContributor extends ProviderDeploymentContributorBase {
  
  private static final String REPLAY_BUFFER_SIZE_PARAM = "replayBufferSize";

  private static final String DISPATCH_IMPL_PARAM = "dispatch-impl";

  // Default global replay buffer size in KB
  public static final String DEFAULT_REPLAY_BUFFER_SIZE = "8";

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
    FilterParamDescriptor filterParam = filter.param().name( REPLAY_BUFFER_SIZE_PARAM ).value( DEFAULT_REPLAY_BUFFER_SIZE );
    for ( Map.Entry<String,String> serviceParam : service.getParams().entrySet() ) {
      if ( REPLAY_BUFFER_SIZE_PARAM.equals( serviceParam.getKey() ) ) {
        filterParam.value( serviceParam.getValue() );
      }
    }
    if ( params != null ) {
      for ( FilterParamDescriptor customParam : params ) {
        if ( REPLAY_BUFFER_SIZE_PARAM.equals( customParam.name() ) ) {
          filterParam.value( customParam.value() );
        }
      }
    }
    if( context.getGatewayConfig().isHadoopKerberosSecured() ) {
      filter.param().name("kerberos").value("true");
    }
  }

}

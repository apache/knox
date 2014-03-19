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
package org.apache.hadoop.gateway.hive;

import java.util.List;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterDescriptor;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;

/**
 *
 */
public class HiveDispatchDeploymentContributor extends ProviderDeploymentContributorBase {
  
  private static final String REPLAY_BUFFER_SIZE_PARAM = "replayBufferSize";
  
  // Default global replay buffer size in KB
  public static final String DEFAULT_REPLAY_BUFFER_SIZE = "4";

  @Override
  public String getRole() {
    return "dispatch";
  }

  @Override
  public String getName() {
    return "hive";
  }

  @Override
  public void contributeFilter( DeploymentContext context, Provider provider, Service service, ResourceDescriptor resource, List<FilterParamDescriptor> params ) {
    String replayBufferSize = DEFAULT_REPLAY_BUFFER_SIZE;
    if (params != null) {
      for (FilterParamDescriptor paramDescriptor : params) {
        if (REPLAY_BUFFER_SIZE_PARAM.equals( paramDescriptor.name() )) {
          replayBufferSize = paramDescriptor.value();
          break;
        }
      }
    }
    FilterDescriptor filter = resource.addFilter().name( getName() ).role( getRole() ).impl( HiveHttpClientDispatch.class );
    filter.param().name("replayBufferSize").value(replayBufferSize);
    if( context.getGatewayConfig().isHadoopKerberosSecured() ) {
      filter.param().name("kerberos").value("true");
    }
    else {
      filter.param().name("basicAuthPreemptive").value("true");
    }
  }
}

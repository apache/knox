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
package org.apache.hadoop.gateway.jersey;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ServiceDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.topology.Service;

import java.util.ArrayList;
import java.util.List;

public abstract class JerseyServiceDeploymentContributorBase extends ServiceDeploymentContributorBase {

  private static final String PACKAGES_PARAM = "jersey.config.server.provider.packages";
  private static final String TRACE_LOGGING_PARAM = "jersey.config.server.tracing";

  protected abstract String[] getPackages();

  protected abstract String[] getPatterns();

  public void contributeService( DeploymentContext context, Service service ) throws Exception {
    String packages = StringUtils.join( getPackages(), ";" );
    for( String pattern : getPatterns() ) {
      ResourceDescriptor resource = context.getGatewayDescriptor().addResource();
      resource.role( service.getRole() );
      resource.pattern( pattern );
      addAuthenticationFilter( context, service, resource );
      addIdentityAssertionFilter( context, service, resource );
      addAuthorizationFilter( context, service, resource );
      // addRewriteFilter( context, service, resource, null );
      List<FilterParamDescriptor> params = new ArrayList<FilterParamDescriptor>();
      FilterParamDescriptor param = resource.createFilterParam();
      param.name( PACKAGES_PARAM );
      param.value( packages );
      params.add( param );
//      FilterParamDescriptor trace = resource.createFilterParam();
//      param.name( TRACE_LOGGING_PARAM );
//      param.value( "ALL" );
//      params.add( trace );
      context.contributeFilter( service, resource, "pivot", "jersey", params );
    }
  }
}

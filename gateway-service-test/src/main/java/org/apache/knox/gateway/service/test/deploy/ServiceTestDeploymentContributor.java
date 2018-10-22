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
package org.apache.knox.gateway.service.test.deploy;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.jersey.JerseyServiceDeploymentContributorBase;
import org.apache.knox.gateway.topology.Service;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class ServiceTestDeploymentContributor extends JerseyServiceDeploymentContributorBase {

  private static final String PACKAGES_PARAM = "jersey.config.server.provider.packages";

  /* (non-Javadoc)
   * @see ServiceDeploymentContributor#getRole()
   */
  @Override
  public String getRole() {
    return "SERVICE-TEST";
  }

  /* (non-Javadoc)
   * @see ServiceDeploymentContributor#getName()
   */
  @Override
  public String getName() {
    return "service-test";
  }

  /* (non-Javadoc)
   * @see JerseyServiceDeploymentContributorBase#getPackages()
   */
  @Override
  protected String[] getPackages() {
    return new String[]{ "org.apache.knox.gateway.service.test" };
  }

  /* (non-Javadoc)
   * @see JerseyServiceDeploymentContributorBase#getPatterns()
   */
  @Override
  protected String[] getPatterns() {
    return new String[]{ "*/**?**", "/*" };
  }

  @Override
  public void contributeService( DeploymentContext context, Service service ) throws Exception {
    String packages = StringUtils.join(getPackages(), ";");
    for (String pattern : getPatterns()) {
      ResourceDescriptor resource = context.getGatewayDescriptor().addResource();
      resource.role(service.getRole());
      resource.pattern(pattern);
      addXForwardedFilter(context, service, resource);
//      addAuthenticationFilter(context, service, resource);
//      addIdentityAssertionFilter(context, service, resource);
//      addAuthorizationFilter(context, service, resource);
//       addRewriteFilter( context, service, resource, null );
      List<FilterParamDescriptor> params = new ArrayList<>();
      FilterParamDescriptor param = resource.createFilterParam();
      param.name(PACKAGES_PARAM);
      param.value(packages);
      params.add(param);

      FilterParamDescriptor traceType = resource.createFilterParam();
      traceType.name( "jersey.config.server.tracing" );
      traceType.value( "ALL" );
      params.add( traceType );
      FilterParamDescriptor traceLevel = resource.createFilterParam();
      traceLevel.name( "jersey.config.server.tracing.threshold" );
      traceLevel.value( "VERBOSE" );
      params.add( traceLevel );
      context.contributeFilter( service, resource, "pivot", "jersey", params );


      context.contributeFilter(service, resource, "pivot", "jersey", params);

    }
  }
}

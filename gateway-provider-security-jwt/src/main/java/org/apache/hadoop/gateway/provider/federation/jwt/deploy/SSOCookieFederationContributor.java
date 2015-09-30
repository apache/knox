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
package org.apache.hadoop.gateway.provider.federation.jwt.deploy;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class SSOCookieFederationContributor extends ProviderDeploymentContributorBase {

  private static final String FILTER_CLASSNAME = "org.apache.hadoop.gateway.provider.federation.jwt.filter.SSOCookieFederationFilter";
  private static final String CORS_FILTER_CLASSNAME = "com.thetransactioncompany.cors.CORSFilter";

  @Override
  public String getRole() {
    return "federation";
  }

  @Override
  public String getName() {
    return "SSOCookieProvider";
  }

  @Override
  public void contributeProvider( DeploymentContext context, Provider provider ) {
  }

  @Override
  public void contributeFilter( DeploymentContext context, Provider provider, Service service, ResourceDescriptor resource, List<FilterParamDescriptor> params ) {
    // blindly add all the provider params as filter init params
    if (params == null) {
      params = new ArrayList<FilterParamDescriptor>();
    }
    Map<String, String> providerParams = provider.getParams();
    for(Entry<String, String> entry : providerParams.entrySet()) {
      params.add( resource.createFilterParam().name( entry.getKey().toLowerCase() ).value( entry.getValue() ) );
    }
    resource.addFilter().name( "CORS" ).role( getRole() ).impl( CORS_FILTER_CLASSNAME ).params( params );
    resource.addFilter().name( getName() ).role( getRole() ).impl( FILTER_CLASSNAME ).params( params );
  }
}

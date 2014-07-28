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
package org.apache.hadoop.gateway.hadoopauth.deploy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;

public class HadoopAuthDeploymentContributor extends
    ProviderDeploymentContributorBase {

  private static final String ROLE = "authentication";
  private static final String NAME = "HadoopAuth";

  private static final String HADOOPAUTH_FILTER_CLASSNAME = "org.apache.hadoop.gateway.hadoopauth.filter.HadoopAuthFilter";
  private static final String HADOOPAUTH_POSTFILTER_CLASSNAME = "org.apache.hadoop.gateway.hadoopauth.filter.HadoopAuthPostFilter";

  @Override
  public String getRole() {
    return ROLE;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void initializeContribution(DeploymentContext context) {
    super.initializeContribution(context);
  }

  @Override
  public void contributeFilter(DeploymentContext context, Provider provider, Service service, 
      ResourceDescriptor resource, List<FilterParamDescriptor> params) {
    // blindly add all the provider params as filter init params
    if (params == null) {
      params = new ArrayList<FilterParamDescriptor>();
    }
    Map<String, String> providerParams = provider.getParams();
    for(Entry<String, String> entry : providerParams.entrySet()) {
      params.add( resource.createFilterParam().name( entry.getKey().toLowerCase() ).value( entry.getValue() ) );
    }
    resource.addFilter().name( getName() ).role( getRole() ).impl( HADOOPAUTH_FILTER_CLASSNAME ).params( params );
    resource.addFilter().name( "Post" + getName() ).role( getRole() ).impl( HADOOPAUTH_POSTFILTER_CLASSNAME ).params( params );
  }
}

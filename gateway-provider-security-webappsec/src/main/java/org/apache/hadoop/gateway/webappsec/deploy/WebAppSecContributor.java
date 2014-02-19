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
package org.apache.hadoop.gateway.webappsec.deploy;

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

public class WebAppSecContributor extends
    ProviderDeploymentContributorBase {
  private static final String ROLE = "webappsec";
  private static final String NAME = "WebAppSec";
  private static final String CSRF_SUFFIX = "_CSRF";
  private static final String CSRF_FILTER_CLASSNAME = "org.apache.hadoop.gateway.webappsec.filter.CSRFPreventionFilter";
  private static final String CSRF_ENABLED = "csrf.enabled";

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
    
    Provider webappsec = context.getTopology().getProvider(ROLE, NAME);
    if (webappsec != null && webappsec.isEnabled()) {
      Map<String,String> map = provider.getParams();
      String csrfEnabled = map.get(CSRF_ENABLED);
      if (params == null) {
        params = new ArrayList<FilterParamDescriptor>();
      }
      // blindly add all the provider params as filter init params
      Map<String, String> providerParams = provider.getParams();
      for(Entry<String, String> entry : providerParams.entrySet()) {
        params.add( resource.createFilterParam().name( entry.getKey().toLowerCase() ).value( entry.getValue() ) );
      }
      if ( csrfEnabled != null && csrfEnabled.equals("true")) {
        resource.addFilter().name( getName() + CSRF_SUFFIX ).role( getRole() ).impl( CSRF_FILTER_CLASSNAME ).params( params );
      }
    }
  }
}

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
package org.apache.knox.gateway.pac4j.deploy;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Descriptor of the pac4j provider. This module must be deployed for the KnoxSSO service.
 *
 * @since 0.8.0
 */
public class Pac4jFederationProviderContributor extends
    ProviderDeploymentContributorBase {

  private static final String ROLE = "federation";
  private static final String NAME = "pac4j";
  private static final String DISPATCHER_FILTER_CLASSNAME = "org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter";
  private static final String IDENTITY_ADAPTER_CLASSNAME = "org.apache.knox.gateway.pac4j.filter.Pac4jIdentityAdapter";

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
  public void contributeProvider(DeploymentContext context, Provider provider) {
  }

  @Override
  public void contributeFilter(DeploymentContext context, Provider provider, Service service,
      ResourceDescriptor resource, List<FilterParamDescriptor> params) {
    // blindly add all the provider params as filter init params
    if (params == null) {
      params = new ArrayList<>();
    }
    Map<String, String> providerParams = provider.getParams();
    for(Entry<String, String> entry : providerParams.entrySet()) {
      params.add( resource.createFilterParam().name( entry.getKey() ).value( entry.getValue() ) );
    }
    resource.addFilter().name( getName() ).role( getRole() ).impl( DISPATCHER_FILTER_CLASSNAME ).params( params );
    resource.addFilter().name( getName() ).role( getRole() ).impl( IDENTITY_ADAPTER_CLASSNAME ).params( params );
  }
}

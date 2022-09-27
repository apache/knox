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
package org.apache.knox.gateway.identityasserter.common.filter;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public abstract class AbstractIdentityAsserterDeploymentContributor extends
    ProviderDeploymentContributorBase {

  /* Service specific impersonation params that needs to be scrubbed */
  public static final String IMPERSONATION_PARAMS = "impersonation.params";

  @Override
  public String getRole() {
    return "identity-assertion";
  }

  @Override
  public void contributeFilter( DeploymentContext context, Provider provider, Service service,
      ResourceDescriptor resource, List<FilterParamDescriptor> params ) {
    params = buildFilterInitParms(provider, resource, params);
    /* extract params from service definition */
    params = buildServiceParams(service, resource, params);
    resource.addFilter().name(getName()).role(getRole()).impl(getFilterClassname()).params(params);
  }

  protected List<FilterParamDescriptor> buildServiceParams(Service service,
      ResourceDescriptor resource, List<FilterParamDescriptor> params) {
    // blindly add all the service params as filter init params
    if (params == null) {
      params = new ArrayList<>();
    }
    Map<String, String> serviceParams = service.getParams();
    for(Entry<String, String> entry : serviceParams.entrySet()) {
      FilterParamDescriptor f = resource
          .createFilterParam()
          .name(entry.getKey().toLowerCase(Locale.ROOT))
          .value(entry.getValue());
      if(!params.contains(f)) {
        params.add(f);
      }
    }
    return params;
  }

  public List<FilterParamDescriptor> buildFilterInitParms(Provider provider,
      ResourceDescriptor resource, List<FilterParamDescriptor> params) {
    // blindly add all the provider params as filter init params
    if (params == null) {
      params = new ArrayList<>();
    }
    Map<String, String> providerParams = provider.getParams();
    for(Entry<String, String> entry : providerParams.entrySet()) {
      params.add( resource.createFilterParam().name(entry.getKey().toLowerCase(Locale.ROOT)).value(entry.getValue()));
    }
    return params;
  }

  @Override
  public void contributeProvider(final DeploymentContext context, final Provider provider ) {
    super.contributeProvider(context, provider);
    final String impersonationParams = provider.getParams().get(IMPERSONATION_PARAMS);
    context.getWebAppDescriptor().createContextParam().paramName(IMPERSONATION_PARAMS).paramValue(impersonationParams);
  }

  protected abstract String getFilterClassname();
}

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
import org.apache.knox.gateway.deploy.DeploymentFactory;
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

public class CompositeAuthzDeploymentContributor extends ProviderDeploymentContributorBase {
  @Override
  public String getRole() {
    return "authorization";
  }

  @Override
  public String getName() {
    return "CompositeAuthz";
  }

  @Override
  public void initializeContribution(DeploymentContext context) {
    super.initializeContribution(context);
  }

  @Override
  public void contributeProvider( DeploymentContext context, Provider provider ) {
  }

  @Override
  public void contributeFilter( DeploymentContext context, Provider provider, Service service,
      ResourceDescriptor resource, List<FilterParamDescriptor> params ) {

    if (params == null) {
      params = new ArrayList<>();
    }

    Map<String, String> providerParams = provider.getParams();
    String providerNames = providerParams.get("composite.provider.names");
    String[] names = parseProviderNames(providerNames);
    for (String name : names) {
      getProviderSpecificParams(resource, params, providerParams, name);
      DeploymentFactory.getProviderContributor("authorization", name)
        .contributeFilter(context, provider, service, resource, params);
      params.clear();
    }
  }

  String[] parseProviderNames(String providerNames) {
    String[] names = providerNames.split(",\\s*");
    return names;
  }

  void getProviderSpecificParams(ResourceDescriptor resource, List<FilterParamDescriptor> params,
      Map<String, String> providerParams, String name) {
    String entryName;
    for(Entry<String, String> entry : providerParams.entrySet()) {
      if (entry.getKey().startsWith(name + ".")) {
        entryName = entry.getKey().substring(entry.getKey().indexOf('.') + 1);
        FilterParamDescriptor fpd = resource.createFilterParam();
        params.add(fpd.name(entryName.toLowerCase(Locale.ROOT)).value(entry.getValue()));
      }
    }
  }
}

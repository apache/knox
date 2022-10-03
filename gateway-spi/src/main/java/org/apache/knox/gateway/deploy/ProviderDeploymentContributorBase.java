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
package org.apache.knox.gateway.deploy;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.topology.Provider;

public abstract class ProviderDeploymentContributorBase extends DeploymentContributorBase implements ProviderDeploymentContributor {

  @Override
  public void initializeContribution(DeploymentContext context ) {
    // Noop.
  }

  @Override
  public void contributeProvider(DeploymentContext context, Provider provider ) {
    // Noop.
  }

  @Override
  public void finalizeContribution(DeploymentContext context ) {
    // Noop.
  }

  protected void copyAllProviderParams(Provider provider, ResourceDescriptor resource, List<FilterParamDescriptor> params) {
    copyAllProviderParams(provider, resource, params, true);
  }

  protected void copyAllProviderParams(Provider provider, ResourceDescriptor resource, List<FilterParamDescriptor> params, boolean useLowerCaseKeys) {
    for (Map.Entry<String, String> entry : provider.getParams().entrySet()) {
      String key = useLowerCaseKeys ? entry.getKey().toLowerCase(Locale.ROOT) : entry.getKey();
      params.add(resource.createFilterParam().name(key).value(entry.getValue()));
    }
  }

}

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
package org.apache.hadoop.gateway.identityasserter.filter;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.identityasserter.common.filter.AbstractIdentityAsserterDeploymentContributor;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;

import java.util.List;

public class IdentityAsserterDeploymentContributor extends AbstractIdentityAsserterDeploymentContributor {

  private static final String FILTER_CLASSNAME = IdentityAsserterFilter.class.getName();
  private static final String PRINCIPAL_MAPPING_PARAM_NAME = "principal.mapping";
  private static final String GROUP_PRINCIPAL_MAPPING_PARAM_NAME = "group.principal.mapping";

  @Override
  public String getName() {
    return "Pseudo";
  }

  @Override
  public void contributeProvider( DeploymentContext context, Provider provider ) {
    super.contributeProvider(context, provider);
    String mappings = provider.getParams().get(PRINCIPAL_MAPPING_PARAM_NAME);
    String groupMappings = provider.getParams().get(GROUP_PRINCIPAL_MAPPING_PARAM_NAME);

    context.getWebAppDescriptor().createContextParam().paramName(PRINCIPAL_MAPPING_PARAM_NAME).paramValue(mappings);
    context.getWebAppDescriptor().createContextParam().paramName(GROUP_PRINCIPAL_MAPPING_PARAM_NAME).paramValue(groupMappings);
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.identityasserter.common.filter.AbstractIdentityAsserterDeploymentContributor#getFilterClassname()
   */
  @Override
  protected String getFilterClassname() {
    return FILTER_CLASSNAME;
  }
}

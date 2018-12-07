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
package org.apache.knox.gateway.identityasserter.hadoop.groups.filter;

import org.apache.hadoop.conf.Configuration;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.identityasserter.common.filter.AbstractIdentityAsserterDeploymentContributor;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A provider deployment contributor for looking up authenticated user groups as
 * seen by Hadoop implementation.
 *
 * @since 0.11.0
 */

public class HadoopGroupProviderDeploymentContributor
    extends AbstractIdentityAsserterDeploymentContributor {

  /**
   * Name of our <b>identity-assertion</b> provider.
   */
  public static final String HADOOP_GROUP_PROVIDER = "HadoopGroupProvider";

  /* create an instance */
  public HadoopGroupProviderDeploymentContributor() {
    super();
  }

  @Override
  public String getName() {
    return HADOOP_GROUP_PROVIDER;
  }

  @Override
  protected String getFilterClassname() {
    return HadoopGroupProviderFilter.class.getName();
  }

  @Override
  public void contributeFilter( DeploymentContext context, Provider provider, Service service,
      ResourceDescriptor resource, List<FilterParamDescriptor> params ) {
    Map<String, String> p = provider.getParams();
    String prefix = p.get("CENTRAL_GROUP_CONFIG_PREFIX");
    if (prefix != null && !prefix.isEmpty()) {
      if (!prefix.endsWith(".")) {
          prefix += ".";
      }
      Map<String, String> groupMappingParams =
              ((Configuration)context.getGatewayConfig()).getPropsWithPrefix(prefix);
      if (groupMappingParams != null) {
        params = createParamList(resource, params, groupMappingParams);
      }
    }

    if (params == null || params.isEmpty()) {
        params = buildFilterInitParms(provider, resource, params);
    }
    resource.addFilter().name(getName()).role(getRole()).impl(getFilterClassname()).params(params);
  }

  @Override
  public List<FilterParamDescriptor> buildFilterInitParms(Provider provider,
      ResourceDescriptor resource, List<FilterParamDescriptor> params) {
  // blindly add all the provider params as filter init params
    if (params == null) {
      params = new ArrayList<>();
    }
    Map<String, String> providerParams = provider.getParams();
    return createParamList(resource, params, providerParams);
  }

  private List<FilterParamDescriptor> createParamList(ResourceDescriptor resource, List<FilterParamDescriptor> params,
        Map<String, String> providerParams) {
    if (params == null) {
      params = new ArrayList<>();
    }
    for(Entry<String, String> entry : providerParams.entrySet()) {
      params.add( resource.createFilterParam().name(entry.getKey().toLowerCase(Locale.ROOT)).value(entry.getValue()));
    }
    return params;
  }
}

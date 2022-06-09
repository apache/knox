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
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
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

  private static final HadoopGroupProviderMessages LOG = MessagesFactory.get(HadoopGroupProviderMessages.class);

  static final String CENTRAL_GROUP_CONFIG_PREFIX_PARAM_NAME = "CENTRAL_GROUP_CONFIG_PREFIX";

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
  public void contributeFilter(DeploymentContext context, Provider provider, Service service, ResourceDescriptor resource, List<FilterParamDescriptor> params) {
    final List<FilterParamDescriptor> filterParams = params == null ? new ArrayList<>() : new ArrayList<>(params);

    // add group mapping parameters from gateway-site.xml, if any
    final List<FilterParamDescriptor> groupMappingParamsList = getParamsFromGatewaySiteWithCentralGroupConfigPrefix(provider, context, resource);
    filterParams.addAll(groupMappingParamsList);

    // add provider parameters except for the CENTRAL_GROUP_CONFIG_PREFIX_PARAM_NAME one (that is used only to provide a bridge between the gateway and provider levels)
    provider.getParams().entrySet().stream().filter(entry -> !entry.getKey().startsWith(CENTRAL_GROUP_CONFIG_PREFIX_PARAM_NAME)).forEach(entry -> {
      // if a property already exists with the same name as this provider parameter, it
      // should be removed because the provider-level property should be able to
      // override the gateway-site parameter
      filterParams.removeIf(filterParam -> filterParam.name().equals(entry.getKey()));
      filterParams.add(createFilterParam(resource, entry.getKey(), entry.getValue()));
    });

    resource.addFilter().name(getName()).role(getRole()).impl(getFilterClassname()).params(filterParams);
  }

  private FilterParamDescriptor createFilterParam(ResourceDescriptor resource, String name, String value) {
    return resource.createFilterParam().name(name.toLowerCase(Locale.ROOT)).value(value);
  }

  private List<FilterParamDescriptor> getParamsFromGatewaySiteWithCentralGroupConfigPrefix(Provider provider, DeploymentContext context, ResourceDescriptor resource) {
    final List<FilterParamDescriptor> groupMappingParamsList = new ArrayList<>();
    final Map<String, String> providerParams = provider.getParams();
    String prefix = providerParams.get(CENTRAL_GROUP_CONFIG_PREFIX_PARAM_NAME);
    if (prefix != null && !prefix.isEmpty()) {
      if (!prefix.endsWith(".")) {
        prefix += ".";
      }

      final GatewayConfig gatewayConfig = context.getGatewayConfig();
      final Map<String, String> groupMappingParams = gatewayConfig == null ? null : ((Configuration) gatewayConfig).getPropsWithPrefix(prefix);
      if (groupMappingParams != null && !groupMappingParams.isEmpty()) {
        LOG.groupMappingFound();
        for (Entry<String, String> entry : groupMappingParams.entrySet()) {
          groupMappingParamsList.add(createFilterParam(resource, entry.getKey(), entry.getValue()));
        }
      }
    }
    return groupMappingParamsList;
  }

  @Override
  public List<FilterParamDescriptor> buildFilterInitParms(Provider provider, ResourceDescriptor resource, List<FilterParamDescriptor> params) {
    final List<FilterParamDescriptor> filterInitParams = params == null ? new ArrayList<>() : new ArrayList<>(params);
    // blindly add all the provider params as filter init params
    provider.getParams().forEach((paramName, paramValue) -> {
      filterInitParams.add(createFilterParam(resource, paramName, paramValue));
    });
    return filterInitParams;
  }
}

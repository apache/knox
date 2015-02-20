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
package org.apache.hadoop.gateway.deploy.impl;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ServiceDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterDescriptor;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.dispatch.GatewayDispatchFilter;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.service.definition.*;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;
import org.apache.hadoop.gateway.topology.Version;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceDefinitionDeploymentContributor extends ServiceDeploymentContributorBase {

  private static final String DISPATCH_ROLE = "dispatch";

  private static final String DISPATCH_IMPL_PARAM = "dispatch-impl";

  private static final String REPLAY_BUFFER_SIZE_PARAM = "replayBufferSize";

  public static final String DEFAULT_REPLAY_BUFFER_SIZE = "8";

  private ServiceDefinition serviceDefinition;

  private UrlRewriteRulesDescriptor serviceRules;

  public ServiceDefinitionDeploymentContributor(ServiceDefinition serviceDefinition, UrlRewriteRulesDescriptor serviceRules) {
    this.serviceDefinition = serviceDefinition;
    this.serviceRules = serviceRules;
  }

  @Override
  public String getRole() {
    return serviceDefinition.getRole();
  }

  @Override
  public String getName() {
    return serviceDefinition.getName();
  }

  @Override
  public Version getVersion() {
    return new Version(serviceDefinition.getVersion());
  }

  @Override
  public void contributeService(DeploymentContext context, Service service) throws Exception {
    contributeRewriteRules(context, service);
    contributeResources(context, service);
  }

  private void contributeRewriteRules(DeploymentContext context, Service service) {
    if ( serviceRules != null ) {
      UrlRewriteRulesDescriptor clusterRules = context.getDescriptor("rewrite");
      clusterRules.addRules(serviceRules);
    }
  }

  private void contributeResources(DeploymentContext context, Service service) {
    Map<String, String> filterParams = new HashMap<String, String>();
    List<Route> bindings = serviceDefinition.getRoutes();
    for ( Route binding : bindings ) {
      List<Rewrite> filters = binding.getRewrites();
      if ( filters != null && !filters.isEmpty() ) {
        filterParams.clear();
        for ( Rewrite filter : filters ) {
          filterParams.put(filter.getTo(), filter.getApply());
        }
      }
      try {
        contributeResource(context, service, binding, filterParams);
      } catch ( URISyntaxException e ) {
        e.printStackTrace();
      }
    }

  }

  private void contributeResource(DeploymentContext context, Service service, Route binding, Map<String, String> filterParams) throws URISyntaxException {
    List<FilterParamDescriptor> params = new ArrayList<FilterParamDescriptor>();
    ResourceDescriptor resource = context.getGatewayDescriptor().addResource();
    resource.role(service.getRole());
    resource.pattern(binding.getPath());
    List<Policy> policyBindings = binding.getPolicies();
    if ( policyBindings == null ) {
      policyBindings = serviceDefinition.getPolicies();
    }
    if ( policyBindings == null ) {
      //add default set
      addDefaultPolicies(context, service, filterParams, params, resource);
    } else {
      addPolicies(context, service, filterParams, params, resource, policyBindings);
    }
    addDispatchFilter(context, service, resource, binding);
  }

  private void addPolicies(DeploymentContext context, Service service, Map<String, String> filterParams, List<FilterParamDescriptor> params, ResourceDescriptor resource, List<Policy> policyBindings) throws URISyntaxException {
    for ( Policy policyBinding : policyBindings ) {
      String role = policyBinding.getRole();
      if ( role == null ) {
        throw new IllegalArgumentException("Policy defined has no role for service " + service.getName());
      }
      role = role.trim().toLowerCase();
      if ( role.equals("rewrite") ) {
        addRewriteFilter(context, service, filterParams, params, resource);
      } else if ( topologyContainsProviderType(context, role) ) {
        context.contributeFilter(service, resource, role, policyBinding.getName(), null);
      }
    }
  }

  private void addDefaultPolicies(DeploymentContext context, Service service, Map<String, String> filterParams, List<FilterParamDescriptor> params, ResourceDescriptor resource) throws URISyntaxException {
    addWebAppSecFilters(context, service, resource);
    addAuthenticationFilter(context, service, resource);
    addRewriteFilter(context, service, filterParams, params, resource);
    addIdentityAssertionFilter(context, service, resource);
    addAuthorizationFilter(context, service, resource);
  }

  private void addRewriteFilter(DeploymentContext context, Service service, Map<String, String> filterParams, List<FilterParamDescriptor> params, ResourceDescriptor resource) throws URISyntaxException {
    if ( !filterParams.isEmpty() ) {
      for ( Map.Entry<String, String> filterParam : filterParams.entrySet() ) {
        params.add(resource.createFilterParam().name(filterParam.getKey()).value(filterParam.getValue()));
      }
    }
    addRewriteFilter(context, service, resource, params);
  }

  private void addDispatchFilter(DeploymentContext context, Service service, ResourceDescriptor resource, Route binding) {
    CustomDispatch customDispatch = binding.getDispatch();
    if ( customDispatch == null ) {
      customDispatch = serviceDefinition.getDispatch();
    }
    if ( customDispatch != null ) {
      boolean isHaEnabled = isHaEnabled(context);
      String haContributorName = customDispatch.getHaContributorName();
      String haClassName = customDispatch.getHaClassName();
      if ( isHaEnabled && (haContributorName != null || haClassName != null)) {
        if (haContributorName != null) {
          addDispatchFilter(context, service, resource, DISPATCH_ROLE, haContributorName);
        } else {
          addDispatchFilterForClass(context, service, resource, haClassName);
        }
      } else {
        String contributorName = customDispatch.getContributorName();
        if ( contributorName != null ) {
          addDispatchFilter(context, service, resource, DISPATCH_ROLE, contributorName);
        } else {
          String className = customDispatch.getClassName();
          if ( className != null ) {
            addDispatchFilterForClass(context, service, resource, className);
          }
        }
      }
    } else {
      addDispatchFilter(context, service, resource, DISPATCH_ROLE, "http-client");
    }
  }

  private void addDispatchFilterForClass(DeploymentContext context, Service service, ResourceDescriptor resource, String className) {
    FilterDescriptor filter = resource.addFilter().name(getName()).role(DISPATCH_ROLE).impl(GatewayDispatchFilter.class);
    filter.param().name(DISPATCH_IMPL_PARAM).value(className);
    FilterParamDescriptor filterParam = filter.param().name(REPLAY_BUFFER_SIZE_PARAM).value(DEFAULT_REPLAY_BUFFER_SIZE);
    for ( Map.Entry<String, String> serviceParam : service.getParams().entrySet() ) {
      if ( REPLAY_BUFFER_SIZE_PARAM.equals(serviceParam.getKey()) ) {
        filterParam.value(serviceParam.getValue());
      }
    }
    if ( context.getGatewayConfig().isHadoopKerberosSecured() ) {
      filter.param().name("kerberos").value("true");
    } else {
      //TODO: [sumit] Get rid of special case. Add config/param capabilities to service definitions?
      //special case for hive
      filter.param().name("basicAuthPreemptive").value("true");
    }
  }

  private boolean isHaEnabled(DeploymentContext context) {
    Provider provider = getProviderByRole(context, "ha");
    if ( provider != null && provider.isEnabled() ) {
      Map<String, String> params = provider.getParams();
      if ( params != null ) {
        if ( params.containsKey(getRole()) ) {
          return true;
        }
      }
    }
    return false;
  }

}

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

import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ServiceDeploymentContributorBase;
import org.apache.knox.gateway.descriptor.FilterDescriptor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.dispatch.GatewayDispatchFilter;
import org.apache.knox.gateway.filter.XForwardedHeaderFilter;
import org.apache.knox.gateway.filter.rewrite.api.CookieScopeServletFilter;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.service.definition.CustomDispatch;
import org.apache.knox.gateway.service.definition.Policy;
import org.apache.knox.gateway.service.definition.Rewrite;
import org.apache.knox.gateway.service.definition.Route;
import org.apache.knox.gateway.service.definition.ServiceDefinition;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Version;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ServiceDefinitionDeploymentContributor extends ServiceDeploymentContributorBase {

  private static final String DISPATCH_ROLE = "dispatch";

  private static final String DISPATCH_IMPL_PARAM = "dispatch-impl";

  private static final String HTTP_CLIENT_FACTORY_PARAM = "httpClientFactory";

  private static final String SERVICE_ROLE_PARAM = "serviceRole";

  private static final String XFORWARDED_FILTER_NAME = "XForwardedHeaderFilter";

  private static final String XFORWARDED_FILTER_ROLE = "xforwardedheaders";

  private static final String DEFAULT_HA_DISPATCH_CLASS = "org.apache.knox.gateway.ha.dispatch.DefaultHaDispatch";

  private static final String COOKIE_SCOPING_FILTER_NAME = "CookieScopeServletFilter";

  private static final String COOKIE_SCOPING_FILTER_ROLE = "cookiescopef";

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
    Map<String, String> filterParams = new HashMap<>();
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
    List<FilterParamDescriptor> params = new ArrayList<>();
    ResourceDescriptor resource = context.getGatewayDescriptor().addResource();
    resource.role(service.getRole());
    resource.pattern(binding.getPath());
    //add x-forwarded filter if enabled in config
    if (context.getGatewayConfig().isXForwardedEnabled()) {
      resource.addFilter().name(XFORWARDED_FILTER_NAME).role(XFORWARDED_FILTER_ROLE).impl(XForwardedHeaderFilter.class);
    }
    if (context.getGatewayConfig().isCookieScopingToPathEnabled()) {
      FilterDescriptor filter = resource.addFilter().name(COOKIE_SCOPING_FILTER_NAME).role(COOKIE_SCOPING_FILTER_ROLE).impl(CookieScopeServletFilter.class);
      filter.param().name(GatewayConfigImpl.HTTP_PATH).value(context.getGatewayConfig().getGatewayPath());
    }
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
      role = role.trim().toLowerCase(Locale.ROOT);
      if ( "rewrite".equals(role) ) {
        addRewriteFilter(context, service, filterParams, params, resource);
      } else if ( topologyContainsProviderType(context, role) ) {
        context.contributeFilter(service, resource, role, policyBinding.getName(), null);
      }
      /* handle the case where topology has federation provider but service defines Anonymous authentication see KNOX-1197 */
      else if (role.equalsIgnoreCase("authentication") && topologyContainsProviderType(context, "federation")) {
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

    /* Override dispatch specified in the topology <service> element */
    if(service.getDispatch() != null) {
      customDispatch = service.getDispatch();
    }

    if ( customDispatch == null ) {
      customDispatch = serviceDefinition.getDispatch();
    }
    boolean isHaEnabled = isHaEnabled(context);
    if ( customDispatch != null ) {
      String haContributorName = customDispatch.getHaContributorName();
      String haClassName = customDispatch.getHaClassName();
      String httpClientFactory = customDispatch.getHttpClientFactory();
      boolean useTwoWaySsl = customDispatch.getUseTwoWaySsl();
      if ( isHaEnabled) {
        if (haContributorName != null) {
          addDispatchFilter(context, service, resource, DISPATCH_ROLE, haContributorName);
        } else if (haClassName != null) {
          addDispatchFilterForClass(context, service, resource, haClassName, httpClientFactory, useTwoWaySsl);
        } else {
          addDefaultHaDispatchFilter(context, service, resource);
        }
      } else {
        String contributorName = customDispatch.getContributorName();
        if ( contributorName != null ) {
          addDispatchFilter(context, service, resource, DISPATCH_ROLE, contributorName);
        } else {
          String className = customDispatch.getClassName();
          if ( className != null ) {
            addDispatchFilterForClass(context, service, resource, className, httpClientFactory, useTwoWaySsl);
          } else {
            //final fallback to the default dispatch
            addDispatchFilter(context, service, resource, DISPATCH_ROLE, "http-client");
          }
        }
      }
    } else if (isHaEnabled) {
      addDefaultHaDispatchFilter(context, service, resource);
    } else {
      addDispatchFilter(context, service, resource, DISPATCH_ROLE, "http-client");
    }
  }

  private void addDefaultHaDispatchFilter(DeploymentContext context, Service service, ResourceDescriptor resource) {
    FilterDescriptor filter = addDispatchFilterForClass(context, service, resource, DEFAULT_HA_DISPATCH_CLASS, null);
    filter.param().name(SERVICE_ROLE_PARAM).value(service.getRole());
  }

  private FilterDescriptor addDispatchFilterForClass(DeploymentContext context, Service service, ResourceDescriptor resource, String dispatchClass, String httpClientFactory, boolean useTwoWaySsl) {
    FilterDescriptor filter = resource.addFilter().name(getName()).role(DISPATCH_ROLE).impl(GatewayDispatchFilter.class);
    filter.param().name(DISPATCH_IMPL_PARAM).value(dispatchClass);
    if (httpClientFactory != null) {
      filter.param().name(HTTP_CLIENT_FACTORY_PARAM).value(httpClientFactory);
    }
    // let's take the value of useTwoWaySsl which is derived from the service definition
    // then allow it to be overridden by service params from the topology
    filter.param().name("useTwoWaySsl").value(Boolean.toString(useTwoWaySsl));
    for ( Map.Entry<String, String> serviceParam : service.getParams().entrySet() ) {
      filter.param().name(serviceParam.getKey()).value(serviceParam.getValue());
    }
    if ( context.getGatewayConfig().isHadoopKerberosSecured() ) {
      filter.param().name("kerberos").value("true");
    } else {
      //TODO: [sumit] Get rid of special case. Add config/param capabilities to service definitions?
      //special case for hive
      filter.param().name("basicAuthPreemptive").value("true");
    }
    return filter;
  }

  private FilterDescriptor addDispatchFilterForClass(DeploymentContext context, Service service, ResourceDescriptor resource, String dispatchClass, String httpClientFactory) {
    return addDispatchFilterForClass(context, service, resource, dispatchClass, httpClientFactory, false);
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

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

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.DeploymentException;
import org.apache.knox.gateway.deploy.ServiceDeploymentContributorBase;
import org.apache.knox.gateway.descriptor.FilterDescriptor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.filter.XForwardedHeaderFilter;
import org.apache.knox.gateway.filter.rewrite.api.CookieScopeServletFilter;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.knox.gateway.service.definition.Policy;
import org.apache.knox.gateway.service.definition.Rewrite;
import org.apache.knox.gateway.service.definition.Route;
import org.apache.knox.gateway.service.definition.ServiceDefinition;
import org.apache.knox.gateway.topology.Application;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Version;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ApplicationDeploymentContributor extends ServiceDeploymentContributorBase {
  private static final JAXBContext jaxbContext = getJAXBContext();

  public static final String SERVICE_DEFINITION_FILE_NAME = "service.xml";
  public static final String REWRITE_RULES_FILE_NAME = "rewrite.xml";
  private static final String XFORWARDED_FILTER_NAME = "XForwardedHeaderFilter";
  private static final String XFORWARDED_FILTER_ROLE = "xforwardedheaders";
  private static final String COOKIE_SCOPING_FILTER_NAME = "CookieScopeServletFilter";
  private static final String COOKIE_SCOPING_FILTER_ROLE = "cookiescopef";
  private static final String APPEND_SERVICE_NAME_PARAM = "isAppendServiceName";

  private ServiceDefinition serviceDefinition;

  private UrlRewriteRulesDescriptor serviceRules;

  private static JAXBContext getJAXBContext() {
    try {
      return JAXBContext.newInstance( ServiceDefinition.class );
    } catch (JAXBException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ServiceDefinition loadServiceDefinition( Application application, File file ) throws JAXBException, FileNotFoundException, IOException {
    ServiceDefinition definition;
    if( !file.exists() ) {
      definition = new ServiceDefinition();
      definition.setName( application.getName() );
      List<Route> routes = new ArrayList<>(1);
      Route route;
      route = new Route();
      route.setPath( "/?**" );
      routes.add( route );
      route = new Route();
      route.setPath( "/**?**" );
      routes.add( route );
      definition.setRoutes( routes );
    } else {
      Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
      try(InputStream inputStream = Files.newInputStream(file.toPath()) ) {
          definition = (ServiceDefinition) unmarshaller.unmarshal( inputStream );
      }
    }
    return definition;
  }

  private static UrlRewriteRulesDescriptor loadRewriteRules( File file ) throws IOException {
    UrlRewriteRulesDescriptor rules;
    if( !file.exists() ) {
      rules = UrlRewriteRulesDescriptorFactory.load( "xml", new StringReader( "<rules/>" ) );
    } else {
      try(InputStream inputStream = Files.newInputStream(file.toPath());
          InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
        rules = UrlRewriteRulesDescriptorFactory.load("xml", reader);
      }
    }
    return rules;
  }

  public ApplicationDeploymentContributor( GatewayConfig config, Application application ) throws
      DeploymentException {
    try {
      File appsDir = new File( config.getGatewayApplicationsDir() );
      File appDir = new File( appsDir, application.getName() );
      File serviceFile = new File( appDir, SERVICE_DEFINITION_FILE_NAME );
      File rewriteFile = new File( appDir, REWRITE_RULES_FILE_NAME );
      serviceDefinition = loadServiceDefinition( application, serviceFile );
      serviceRules = loadRewriteRules( rewriteFile );
    } catch ( IOException | JAXBException e ) {
      throw new DeploymentException( "Failed to deploy application: " + application.getName(), e );
    }
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
    contributeRewriteRules(context);
    contributeResources(context, service);
  }

  private void contributeRewriteRules(DeploymentContext context) {
    if ( serviceRules != null ) {
      UrlRewriteRulesDescriptor clusterRules = context.getDescriptor("rewrite");
      // Coverity CID 1352312
      if( clusterRules != null ) {
        clusterRules.addRules( serviceRules );
      }
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

  private void contributeResource( DeploymentContext context, Service service, Route binding, Map<String, String> filterParams) throws URISyntaxException {
    List<FilterParamDescriptor> params = new ArrayList<>();
    ResourceDescriptor resource = context.getGatewayDescriptor().addResource();
    resource.role(service.getRole());
    resource.pattern(binding.getPath());
    //add x-forwarded filter if enabled in config
    if (context.getGatewayConfig().isXForwardedEnabled()) {
      final FilterDescriptor filter = resource.addFilter()
          .name(XFORWARDED_FILTER_NAME).role(XFORWARDED_FILTER_ROLE)
          .impl(XForwardedHeaderFilter.class);
      /* check if we need to add service name to the context */
      if (context.getGatewayConfig().getXForwardContextAppendServices() != null
          && !context.getGatewayConfig().getXForwardContextAppendServices()
          .isEmpty() && context.getGatewayConfig()
          .getXForwardContextAppendServices().contains(service.getRole())) {
        filter.param().name(APPEND_SERVICE_NAME_PARAM).value("true");
      }
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
  }

  private void addPolicies( DeploymentContext context, Service service, Map<String, String> filterParams, List<FilterParamDescriptor> params, ResourceDescriptor resource, List<Policy> policyBindings) throws URISyntaxException {
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
    }
  }

  private void addDefaultPolicies( DeploymentContext context, Service service, Map<String, String> filterParams, List<FilterParamDescriptor> params, ResourceDescriptor resource) throws URISyntaxException {
    addWebAppSecFilters(context, service, resource);
    addAuthenticationFilter(context, service, resource);
    addRewriteFilter(context, service, filterParams, params, resource);
    addIdentityAssertionFilter(context, service, resource);
    addAuthorizationFilter(context, service, resource);
  }

  private void addRewriteFilter( DeploymentContext context, Service service, Map<String, String> filterParams, List<FilterParamDescriptor> params, ResourceDescriptor resource) throws URISyntaxException {
    if ( !filterParams.isEmpty() ) {
      for ( Map.Entry<String, String> filterParam : filterParams.entrySet() ) {
        params.add(resource.createFilterParam().name(filterParam.getKey()).value(filterParam.getValue()));
      }
    }
    addRewriteFilter(context, service, resource, params);
  }

}

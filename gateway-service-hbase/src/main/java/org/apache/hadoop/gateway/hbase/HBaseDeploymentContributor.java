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
package org.apache.hadoop.gateway.hbase;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ServiceDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.hadoop.gateway.topology.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class HBaseDeploymentContributor extends ServiceDeploymentContributorBase {

  private static final String RULES_RESOURCE = HBaseDeploymentContributor.class.getName().replace( '.', '/' ) + "/rewrite.xml";
  private static final String EXTERNAL_PATH = "/hbase";
  private static final String CLUSTER_URL_FUNCTION = "{gateway.url}";

  @Override 
  public String getRole() {
    return "WEBHBASE";
  }

  @Override
  public String getName() {
    return "webhbase";
  }

  @Override
  public void contributeService( DeploymentContext context, Service service ) throws Exception {
    contributeRewriteRules( context, service );
    contributeResources( context, service );
  }

  private void contributeRewriteRules( DeploymentContext context, Service service ) throws URISyntaxException, IOException {
    UrlRewriteRulesDescriptor hbaseRules = loadRulesFromTemplate();
    UrlRewriteRulesDescriptor clusterRules = context.getDescriptor( "rewrite" );
    clusterRules.addRules( hbaseRules );
  }

  private void contributeResources( DeploymentContext context, Service service ) throws URISyntaxException {
    List<FilterParamDescriptor> params;

    ResourceDescriptor rootResource = context.getGatewayDescriptor().addResource();
    rootResource.role( service.getRole() );
    rootResource.pattern( EXTERNAL_PATH + "/?**" );
    addWebAppSecFilters(context, service, rootResource);
    addAuthenticationFilter( context, service, rootResource );
    params = new ArrayList<FilterParamDescriptor>();
    params.add( rootResource.createFilterParam().name( "response.headers" ).value( getQualifiedName() + "/headers/outbound" ) );
    addRewriteFilter( context, service, rootResource, params );
    addIdentityAssertionFilter( context, service, rootResource );
    addAuthorizationFilter(context, service, rootResource);
    addDispatchFilter( context, service, rootResource );
    
    ResourceDescriptor pathResource = context.getGatewayDescriptor().addResource();
    pathResource.role( service.getRole() );
    pathResource.pattern( EXTERNAL_PATH + "/**?**" );
    addWebAppSecFilters(context, service, pathResource);
    addAuthenticationFilter( context, service, pathResource );
    params = new ArrayList<FilterParamDescriptor>();
    params.add( rootResource.createFilterParam().name( "response.headers" ).value( getQualifiedName() + "/headers/outbound" ) );
    addRewriteFilter( context, service, pathResource, params );
    addIdentityAssertionFilter( context, service, pathResource );
    addAuthorizationFilter(context, service, pathResource);
    addDispatchFilter( context, service, pathResource );

    ResourceDescriptor statusResource = context.getGatewayDescriptor().addResource();
    statusResource.role( service.getRole() );
    statusResource.pattern( EXTERNAL_PATH + "/status/cluster?**" );
    addWebAppSecFilters(context, service, statusResource);
    addAuthenticationFilter( context, service, statusResource );
    params = new ArrayList<FilterParamDescriptor>();
    params.add( statusResource.createFilterParam().name( "response.body" ).value( getQualifiedName() + "/status/outbound" ) );
    addRewriteFilter( context, service, statusResource, params );
    addIdentityAssertionFilter( context, service, statusResource );
    addAuthorizationFilter(context, service, statusResource);
    addDispatchFilter( context, service, statusResource );

    ResourceDescriptor regionResource = context.getGatewayDescriptor().addResource();
    regionResource.role( service.getRole() );
    regionResource.pattern( EXTERNAL_PATH + "/*/regions?**" );
    addWebAppSecFilters(context, service, regionResource);
    addAuthenticationFilter( context, service, regionResource );
    params = new ArrayList<FilterParamDescriptor>();
    params.add( regionResource.createFilterParam().name( "response.body" ).value( getQualifiedName() + "/regions/outbound" ) );
    addRewriteFilter( context, service, regionResource, params );
    addIdentityAssertionFilter( context, service, regionResource );
    addAuthorizationFilter(context, service, regionResource);
    addDispatchFilter( context, service, regionResource );
  }

  private void addDispatchFilter(
    DeploymentContext context, Service service, ResourceDescriptor resource ) {
    context.contributeFilter( service, resource, "dispatch", "hbase", null );
  }

  private String getQualifiedName() {
    return getRole() + "/" + getName();
  }

  UrlRewriteRulesDescriptor loadRulesFromTemplate() throws IOException {
    InputStream stream = this.getClass().getClassLoader().getResourceAsStream( RULES_RESOURCE );
    Reader reader = new InputStreamReader( stream );
    UrlRewriteRulesDescriptor rules = UrlRewriteRulesDescriptorFactory.load( "xml", reader );
    reader.close();
    stream.close();
    return rules;
  }

}

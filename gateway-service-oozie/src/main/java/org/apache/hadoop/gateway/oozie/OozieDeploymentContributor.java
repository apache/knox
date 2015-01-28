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
package org.apache.hadoop.gateway.oozie;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ServiceDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.hadoop.gateway.topology.Service;

public class OozieDeploymentContributor extends ServiceDeploymentContributorBase {

  private static final String RULES_RESOURCE = OozieDeploymentContributor.class.getName().replace( '.', '/' ) + "/rewrite.xml";
  private static final String EXTERNAL_PATH = "/oozie";
  
  private static final String REPLAY_BUFFER_SIZE_PARAM = "replayBufferSize";
  
  // Oozie replay buffer size in KB
  private static final String DEFAULT_REPLAY_BUFFER_SIZE = "8";

  @Override
  public String getRole() {
    return "OOZIE";
  }

  @Override
  public String getName() {
    return "oozie";
  }

  @Override
  public void contributeService( DeploymentContext context, Service service ) throws Exception {
    contributeRewriteRules( context, service );
    contributeResources( context, service );
  }

  private void contributeRewriteRules( DeploymentContext context, Service service ) throws URISyntaxException, IOException {
    UrlRewriteRulesDescriptor oozieRules = loadRulesFromTemplate();
    UrlRewriteRulesDescriptor clusterRules = context.getDescriptor( "rewrite" );
    clusterRules.addRules( oozieRules );
  }

  public void contributeResources( DeploymentContext context, Service service ) throws URISyntaxException {
    ResourceDescriptor rootResource = context.getGatewayDescriptor().addResource();
    rootResource.role( service.getRole() );
    rootResource.pattern( EXTERNAL_PATH + "/**?**" );
    addWebAppSecFilters(context, service, rootResource);
    addAuthenticationFilter( context, service, rootResource );
    addRewriteFilter( context, service, rootResource );
    addIdentityAssertionFilter( context, service, rootResource );
    addAuthorizationFilter(context, service, rootResource);
    addDispatchFilter( context, service, rootResource );

    ResourceDescriptor v1Resource = context.getGatewayDescriptor().addResource();
    v1Resource.role( service.getRole() );
    v1Resource.pattern( EXTERNAL_PATH + "/v1/**?**" );
    addWebAppSecFilters(context, service, v1Resource);
    addAuthenticationFilter( context, service, v1Resource );
    addRewriteFilter( context, service, v1Resource );
    addIdentityAssertionFilter( context, service, v1Resource );
    addAuthorizationFilter(context, service, v1Resource);
    addDispatchFilter( context, service, v1Resource );

    ResourceDescriptor v2Resource = context.getGatewayDescriptor().addResource();
    v2Resource.role( service.getRole() );
    v2Resource.pattern( EXTERNAL_PATH + "/v2/**?**" );
    addWebAppSecFilters(context, service, v2Resource);
    addAuthenticationFilter( context, service, v2Resource );
    addRewriteFilter( context, service, v2Resource );
    addIdentityAssertionFilter( context, service, v2Resource );
    addAuthorizationFilter(context, service, v2Resource);
    addDispatchFilter( context, service, v2Resource );
  }

  private void addRewriteFilter(
      DeploymentContext context, Service service, ResourceDescriptor resource ) throws URISyntaxException {
    List<FilterParamDescriptor> params = new ArrayList<FilterParamDescriptor>();
    params.add( resource.createFilterParam().name( "request.body" ).value( "OOZIE/oozie/configuration" ) );
    context.contributeFilter( service, resource, "rewrite", null, params );
  }

  private void addDispatchFilter(DeploymentContext context, Service service,
      ResourceDescriptor resource) {
    context.contributeFilter(service, resource, "dispatch", "http-client", null );
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
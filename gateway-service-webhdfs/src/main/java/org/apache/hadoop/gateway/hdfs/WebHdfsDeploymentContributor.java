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
package org.apache.hadoop.gateway.hdfs;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ServiceDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteServletFilter;
import org.apache.hadoop.gateway.topology.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class WebHdfsDeploymentContributor extends ServiceDeploymentContributorBase {

  private static final String RULES_RESOURCE = WebHdfsDeploymentContributor.class.getName().replace( '.', '/' ) + "/rewrite.xml";
  private static final String WEBHDFS_EXTERNAL_PATH = "/webhdfs/v1";
  private static final String DATANODE_EXTERNAL_PATH = "/webhdfs/data/v1";
//  private static final String WEBHDFS_INTERNAL_PATH = "/webhdfs";
//  private static final String CLUSTER_URL_FUNCTION = "{gateway.url}";

  @Override
  public String getRole() {
    return "WEBHDFS";
  }

  @Override
  public String getName() {
    return "webhdfs";
  }

  @Override
  public void contributeService( DeploymentContext context, Service service ) throws Exception {
    contributeRewriteRules( context, service );
    contributeNameNodeResource( context, service );
    contributeDataNodeResource( context, service );
  }

  private void contributeRewriteRules( DeploymentContext context, Service service ) throws URISyntaxException, IOException {
    UrlRewriteRulesDescriptor serviceRules = loadRulesFromClassPath();
    UrlRewriteRulesDescriptor clusterRules = context.getDescriptor( "rewrite" );
    clusterRules.addRules( serviceRules );
  }

  public void contributeNameNodeResource( DeploymentContext context, Service service ) throws URISyntaxException {
    List<FilterParamDescriptor> params;
    ResourceDescriptor rootResource = context.getGatewayDescriptor().addResource();
    rootResource.role( service.getRole() );
    rootResource.pattern( WEBHDFS_EXTERNAL_PATH + "/?**" );
    addWebAppSecFilters(context, service, rootResource);
    addAuthenticationFilter( context, service, rootResource );
    params = new ArrayList<FilterParamDescriptor>();
    params.add( rootResource.createFilterParam().
        name( UrlRewriteServletFilter.REQUEST_URL_RULE_PARAM ).value( getQualifiedName() + "/inbound/namenode/root" ) );
    addRewriteFilter( context, service, rootResource, params );
    addIdentityAssertionFilter( context, service, rootResource );
    addAuthorizationFilter( context, service, rootResource );
    addDispatchFilter( context, service, rootResource, "dispatch", "http-client" );

    ResourceDescriptor fileResource = context.getGatewayDescriptor().addResource();
    fileResource.role( service.getRole() );
    fileResource.pattern( WEBHDFS_EXTERNAL_PATH + "/**?**" );
    addWebAppSecFilters(context, service, fileResource);
    addAuthenticationFilter( context, service, fileResource );
    params = new ArrayList<FilterParamDescriptor>();
    params.add( fileResource.createFilterParam().
        name( UrlRewriteServletFilter.REQUEST_URL_RULE_PARAM ).value( getQualifiedName() + "/inbound/namenode/file" ) );
    params.add( fileResource.createFilterParam().
        name( UrlRewriteServletFilter.RESPONSE_HEADERS_FILTER_PARAM ).value( getQualifiedName() + "/outbound/namenode/headers" ) );
    addRewriteFilter( context, service, fileResource, params );
    addIdentityAssertionFilter( context, service, fileResource );
    addAuthorizationFilter( context, service, fileResource );
    addDispatchFilter( context, service, fileResource, "dispatch", "http-client" );

    ResourceDescriptor homeResource = context.getGatewayDescriptor().addResource();
    homeResource.role( service.getRole() );
    homeResource.pattern( WEBHDFS_EXTERNAL_PATH + "/~?**" );
    addWebAppSecFilters(context, service, homeResource);
    addAuthenticationFilter( context, service, homeResource );
    params = new ArrayList<FilterParamDescriptor>();
    params.add( homeResource.createFilterParam().
        name( UrlRewriteServletFilter.REQUEST_URL_RULE_PARAM ).value( getQualifiedName() + "/inbound/namenode/home" ) );
    addRewriteFilter( context, service, homeResource, params );
    addIdentityAssertionFilter( context, service, homeResource );
    addAuthorizationFilter( context, service, homeResource );
    addDispatchFilter( context, service, homeResource, "dispatch", "http-client" );

    ResourceDescriptor homeFileResource = context.getGatewayDescriptor().addResource();
    homeFileResource.role( service.getRole() );
    homeFileResource.pattern( WEBHDFS_EXTERNAL_PATH + "/~/**?**" );
    addWebAppSecFilters(context, service, homeFileResource);
    addAuthenticationFilter( context, service, homeFileResource );
    params = new ArrayList<FilterParamDescriptor>();
    params.add( homeFileResource.createFilterParam().
        name( UrlRewriteServletFilter.REQUEST_URL_RULE_PARAM ).value( getQualifiedName() + "/inbound/namenode/home/file" ) );
    params.add( homeFileResource.createFilterParam().
        name( UrlRewriteServletFilter.RESPONSE_HEADERS_FILTER_PARAM ).value( getQualifiedName() + "/outbound/namenode/headers" ) );
    addRewriteFilter( context, service, homeFileResource, params );
    addIdentityAssertionFilter( context, service, homeFileResource );
    addAuthorizationFilter( context, service, homeFileResource );
    addDispatchFilter( context, service, homeFileResource, "dispatch", "http-client" );
  }

  public void contributeDataNodeResource( DeploymentContext context, Service service ) throws URISyntaxException {
    List<FilterParamDescriptor> params;
    ResourceDescriptor fileResource = context.getGatewayDescriptor().addResource();
    fileResource.role( service.getRole() );
    fileResource.pattern( DATANODE_EXTERNAL_PATH + "/**?**" );
    addWebAppSecFilters(context, service, fileResource);
    addAuthenticationFilter( context, service, fileResource );
    addIdentityAssertionFilter( context, service, fileResource );
    addAuthorizationFilter( context, service, fileResource );
    params = new ArrayList<FilterParamDescriptor>();
    params.add( fileResource.createFilterParam().
        name( UrlRewriteServletFilter.REQUEST_URL_RULE_PARAM ).value( getQualifiedName() + "/inbound/datanode" ) );
    addRewriteFilter( context, service, fileResource, params );
    addDispatchFilter( context, service, fileResource, "dispatch", "http-client" );
  }

  String getQualifiedName() {
    return getRole() + "/" + getName();
  }

  UrlRewriteRulesDescriptor loadRulesFromClassPath() throws IOException {
    InputStream stream = this.getClass().getClassLoader().getResourceAsStream( RULES_RESOURCE );
    Reader reader = new InputStreamReader( stream );
    UrlRewriteRulesDescriptor rules = UrlRewriteRulesDescriptorFactory.load( "xml", reader );
    reader.close();
    stream.close();
    return rules;
  }

}

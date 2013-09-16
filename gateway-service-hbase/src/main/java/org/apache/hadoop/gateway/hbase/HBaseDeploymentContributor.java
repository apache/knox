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
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteFilterDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.ext.UrlRewriteActionRewriteDescriptorExt;
import org.apache.hadoop.gateway.filter.rewrite.ext.UrlRewriteMatchDescriptor;
import org.apache.hadoop.gateway.topology.Service;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class HBaseDeploymentContributor extends ServiceDeploymentContributorBase {

  private static final String EXTERNAL_PATH = "/hbase/api/v1";
  private static final String CLUSTER_URL_FUNCTION = "{gateway.url}";

  @Override
  public String getRole() {
    return "HBASE";
  }

  @Override
  public String getName() {
    return "hbase";
  }

  @Override
  public void contributeService( DeploymentContext context, Service service ) throws Exception {
    contributeRewriteRules( context, service );
    contributeResources( context, service );
  }

  private void contributeRewriteRules( DeploymentContext context, Service service ) throws URISyntaxException {
    UrlRewriteRulesDescriptor rules = context.getDescriptor( "rewrite" );
    UrlRewriteRuleDescriptor rule;
    UrlRewriteActionRewriteDescriptorExt rewrite;
    UrlRewriteMatchDescriptor match;

    rule = rules.addRule( getQualifiedName() + "/root/inbound" )
        .directions( "inbound" )
        .pattern( "*://*:*/**" + EXTERNAL_PATH + "/?{**}" );
    rewrite = rule.addStep( "rewrite" );
    rewrite.template( service.getUrl() + "/?{**}" );
    
    rule = rules.addRule( getQualifiedName() + "/root/inbound" )
        .directions( "inbound" )
        .pattern( "*://*:*/**" + EXTERNAL_PATH + "/{**}?{**}" );
    rewrite = rule.addStep( "rewrite" );
    rewrite.template( service.getUrl() + "/{**}?{**}" );
    
    rule = rules.addRule( getQualifiedName() + "/hbase/outbound" )
        .directions( "outbound" );
    match = rule.addStep( "match" );
    match.pattern( "*://*:*/{path=**}?{**}" );
    rewrite = rule.addStep( "rewrite" );
    rewrite.template( CLUSTER_URL_FUNCTION + EXTERNAL_PATH + "/{path}?{**}" );

    UrlRewriteFilterDescriptor filter = rules.addFilter( getQualifiedName() + "/hbase/outbound" );
    UrlRewriteFilterContentDescriptor content = filter.addContent( "application/x-http-headers" );
    content.addApply( "Location", getQualifiedName() + "/hbase/outbound" );
  }

  private void contributeResources( DeploymentContext context, Service service ) throws URISyntaxException {
    ResourceDescriptor rootResource = context.getGatewayDescriptor().addResource();
    rootResource.role( service.getRole() );
    rootResource.pattern( EXTERNAL_PATH + "/?**" );
    addAuthenticationFilter( context, service, rootResource );
    addRewriteFilter( context, service, rootResource );
    addIdentityAssertionFilter( context, service, rootResource );
    addDispatchFilter( context, service, rootResource );
    
    ResourceDescriptor fileResource = context.getGatewayDescriptor().addResource();
    fileResource.role( service.getRole() );
    fileResource.pattern( EXTERNAL_PATH + "/**?**" );
    addAuthenticationFilter( context, service, fileResource );
    addRewriteFilter( context, service, fileResource );
    addIdentityAssertionFilter( context, service, fileResource );
    addAuthorizationFilter(context, service, fileResource);
    addDispatchFilter( context, service, fileResource );
  }

  private void addRewriteFilter(
      DeploymentContext context, Service service, ResourceDescriptor resource ) throws URISyntaxException {
    List<FilterParamDescriptor> params = new ArrayList<FilterParamDescriptor>();
    params.add( resource.createFilterParam().name( "response.headers" ).value( getQualifiedName() + "/hbase/outbound" ) );
    context.contributeFilter( service, resource, "rewrite", null, params );
  }

  private void addDispatchFilter(
      DeploymentContext context, Service service, ResourceDescriptor resource ) {
    context.contributeFilter( service, resource, "dispatch", null, null );
  }

  private String getQualifiedName() {
    return getRole() + "/" + getName();
  }

}

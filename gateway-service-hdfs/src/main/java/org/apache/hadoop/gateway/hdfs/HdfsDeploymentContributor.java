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
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.ext.UrlRewriteActionRewriteDescriptorExt;
import org.apache.hadoop.gateway.filter.rewrite.ext.UrlRewriteMatchDescriptor;
import org.apache.hadoop.gateway.topology.Service;

import java.net.URISyntaxException;

public class HdfsDeploymentContributor extends ServiceDeploymentContributorBase {

  private static final String NAMENODE_EXTERNAL_PATH = "/namenode/api/v1";
  private static final String DATANODE_INTERNAL_PATH = "/webhdfs/v1";
  private static final String DATANODE_EXTERNAL_PATH = "/datanode/api/v1";
  private static final String CLUSTER_URL_FUNCTION = "{gateway.url}";

  @Override
  public String getRole() {
    return "NAMENODE";
  }

  @Override
  public String getName() {
    return "hdfs";
  }

  @Override
  public void contributeService( DeploymentContext context, Service service ) throws Exception {
    contributeRewriteRules( context, service );
    contributeNameNodeResource( context, service );
    contributeDataNodeResource( context, service );
  }

  private void contributeRewriteRules( DeploymentContext context, Service service ) throws URISyntaxException {
    UrlRewriteRulesDescriptor rules = context.getDescriptor( "rewrite" );
    UrlRewriteRuleDescriptor rule;
    UrlRewriteActionRewriteDescriptorExt rewrite;
    UrlRewriteMatchDescriptor match;

    rule = rules.addRule( getRole() + "/" + getName() + "/namenode/root/inbound" )
        .directions( "inbound" )
        .pattern( "*://*:*/**" + NAMENODE_EXTERNAL_PATH + "/?{**}" );
    rewrite = rule.addStep( "rewrite" );
    rewrite.template( service.getUrl().toExternalForm() + "/?{**}" );

    rule = rules.addRule( getRole() + "/" + getName() + "/namenode/file/inbound" )
        .directions( "inbound" )
        .pattern( "*://*:*/**" + NAMENODE_EXTERNAL_PATH + "/{path=**}?{**}" );
    rewrite = rule.addStep( "rewrite" );
    rewrite.template( service.getUrl().toExternalForm() + "/{path=**}?{**}" );

    rule = rules.addRule( getRole() + "/" + getName() + "/datanode/inbound" )
        .directions( "inbound" )
        .pattern( "*://*:*/**" + DATANODE_EXTERNAL_PATH + "/{path=**}?**" );
    //TODO: If the input type is wrong it throws a NPE.
    rule.addStep( "decode-query" );
    match = rule.addStep( "match" );
    match.pattern( "*://*:*/**" + DATANODE_EXTERNAL_PATH + "/{path=**}?{host}&{port}&{**}" );
    rewrite = rule.addStep( "rewrite" );
    rewrite.template( "http://{host}:{port}/{path=**}?{**}" );

    rule = rules.addRule( getRole() + "/" + getName() + "/datanode/outbound" )
        .directions( "outbound" )
        .pattern( "*://*:*/**?**" );
    match = rule.addStep( "match" );
    match.pattern( "*://{host}:{port}/{path=**}?{**}" );
    rewrite = rule.addStep( "rewrite" );
    rewrite.template( CLUSTER_URL_FUNCTION + DATANODE_EXTERNAL_PATH + "/{path=**}?{host}&{port}&{**}" );
    rule.addStep( "encode-query" );
  }

  public void contributeNameNodeResource( DeploymentContext context, Service service ) throws URISyntaxException {
    ResourceDescriptor rootResource = context.getGatewayDescriptor().addResource();
    rootResource.role( service.getRole() );
    rootResource.pattern( NAMENODE_EXTERNAL_PATH + "/?**" );
    addAuthenticationFilter( context, service, rootResource );
    addIdentityAssertionFilter( context, service, rootResource );
    addDispatchFilter( context, service, rootResource, "dispatch", null );

    ResourceDescriptor fileResource = context.getGatewayDescriptor().addResource();
    fileResource.role( service.getRole() );
    fileResource.pattern( NAMENODE_EXTERNAL_PATH + "/**?**" );
    addAuthenticationFilter( context, service, fileResource );
    addRewriteFilter( context, service, fileResource );
    addIdentityAssertionFilter( context, service, fileResource );
    addDispatchFilter( context, service, fileResource, "dispatch", null );
  }

  public void contributeDataNodeResource( DeploymentContext context, Service service ) throws URISyntaxException {
    ResourceDescriptor fileResource = context.getGatewayDescriptor().addResource();
    fileResource.role( service.getRole() );
    fileResource.pattern( DATANODE_EXTERNAL_PATH + "/**?**" );
    addAuthenticationFilter( context, service, fileResource );
    addRewriteFilter( context, service, fileResource );
    addIdentityAssertionFilter( context, service, fileResource );
    addDispatchFilter( context, service, fileResource, "dispatch", null );
  }

  private void addAuthenticationFilter( DeploymentContext context, Service service, ResourceDescriptor resource ) {
    context.contributeFilter( service, resource, "authentication", null, null );
  }

  private void addIdentityAssertionFilter(DeploymentContext context, Service service, ResourceDescriptor resource) {
    context.contributeFilter( service, resource, "identity-assertion", null, null );
  }

  private void addDispatchFilter(
      DeploymentContext context, Service service, ResourceDescriptor resource, String role, String name ) {
    context.contributeFilter( service, resource, role, name, null );
  }

  private void addRewriteFilter(
      DeploymentContext context, Service service, ResourceDescriptor resource ) throws URISyntaxException {
    context.contributeFilter( service, resource, "rewrite", null, null );

  }

}

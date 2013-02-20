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

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ServiceDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.ext.UrlRewriteActionRewriteDescriptorExt;
import org.apache.hadoop.gateway.topology.Service;

import java.net.URISyntaxException;

public class OozieDeploymentContributor extends ServiceDeploymentContributorBase {

  private static final String EXTERNAL_PATH = "/oozie/api";

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

  private void contributeRewriteRules( DeploymentContext context, Service service ) throws URISyntaxException {
    UrlRewriteRulesDescriptor rules = context.getDescriptor( "rewrite" );
    UrlRewriteRuleDescriptor rule;
    UrlRewriteActionRewriteDescriptorExt rewrite;

    rule = rules.addRule( getRole() + "/" + getName() + "/root/inbound" )
        .directions( "inbound" )
        .pattern( "*://*:*/**" + EXTERNAL_PATH + "/{**}?{**}" );
    rewrite = rule.addStep( "rewrite" );
    rewrite.template( service.getUrl().toExternalForm() + "/{**}?{**}" );

    rule = rules.addRule( getRole() + "/" + getName() + "/api/inbound" )
        .directions( "inbound" )
        .pattern( "*://*:*/**" + EXTERNAL_PATH + "/v1/{**}?{**}" );
    rewrite = rule.addStep( "rewrite" );
    rewrite.template( service.getUrl().toExternalForm() + "/v1/{**}?{**}" );
  }

  public void contributeResources( DeploymentContext context, Service service ) throws URISyntaxException {
    ResourceDescriptor rootResource = context.getGatewayDescriptor().addResource();
    rootResource.role( service.getRole() );
    rootResource.pattern( EXTERNAL_PATH + "/**?**" );
    addAuthenticationFilter( context, service, rootResource );
    addRewriteFilter( context, service, rootResource );
    addIdentityAssertionFilter( context, service, rootResource );
    addDispatchFilter( context, service, rootResource );

    ResourceDescriptor apiResource = context.getGatewayDescriptor().addResource();
    apiResource.role( service.getRole() );
    apiResource.pattern( EXTERNAL_PATH + "/v1/**?**" );
    addAuthenticationFilter( context, service, apiResource );
    addRewriteFilter( context, service, apiResource );
    addIdentityAssertionFilter( context, service, apiResource );
    addDispatchFilter( context, service, apiResource );
  }

  private void addAuthenticationFilter( DeploymentContext context, Service service, ResourceDescriptor resource ) {
    context.contributeFilter( service, resource, "authentication", null, null );
  }

  private void addRewriteFilter(
      DeploymentContext context, Service service, ResourceDescriptor resource ) throws URISyntaxException {
    context.contributeFilter( service, resource, "rewrite", null, null );
  }

  private void addIdentityAssertionFilter(DeploymentContext context, Service service, ResourceDescriptor resource) {
    context.contributeFilter( service, resource, "identity-assertion", null, null );
  }

  private void addDispatchFilter(
      DeploymentContext context, Service service, ResourceDescriptor resource ) {
    context.contributeFilter( service, resource, "dispatch", null, null );
  }

}

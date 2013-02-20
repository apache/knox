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
package org.apache.hadoop.gateway.templeton;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ServiceDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.ext.UrlRewriteActionRewriteDescriptorExt;
import org.apache.hadoop.gateway.topology.Service;

import java.net.URISyntaxException;

public class TempletonDeploymentContributor extends ServiceDeploymentContributorBase {

  private static final String TEMPLETON_EXTERNAL_PATH = "/templeton/api/v1";

  @Override
  public String getRole() {
    return "TEMPLETON";
  }

  @Override
  public String getName() {
    return "templeton";
  }

  @Override
  public void contributeService( DeploymentContext context, Service service ) throws URISyntaxException {
    UrlRewriteRulesDescriptor rules = context.getDescriptor( "rewrite" );
    UrlRewriteRuleDescriptor rule;
    UrlRewriteActionRewriteDescriptorExt rewrite;

    rule = rules.addRule( getRole() + "/" + getName() + "/templeton/request" )
        .directions( "request" )
        .pattern( "*://*:*/**" + TEMPLETON_EXTERNAL_PATH + "/{path=**}?{**}" );
    rewrite = rule.addStep( "rewrite" );
    rewrite.template( service.getUrl().toExternalForm() + "/{path=**}?{**}" );

    ResourceDescriptor resource = context.getGatewayDescriptor().addResource();
    resource.role( service.getRole() );
    resource.pattern( TEMPLETON_EXTERNAL_PATH + "/**?**" );
    context.contributeFilter( service, resource, "authentication", null, null );
    context.contributeFilter( service, resource, "rewrite", null, null );
    context.contributeFilter( service, resource, "identity-assertion", null, null );
    context.contributeFilter( service, resource, "dispatch", null, null );
  }

}
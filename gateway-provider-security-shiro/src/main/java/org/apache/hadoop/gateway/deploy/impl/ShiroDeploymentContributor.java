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
import org.apache.hadoop.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;
import org.jboss.shrinkwrap.api.asset.StringAsset;

import java.util.List;

public class ShiroDeploymentContributor extends ProviderDeploymentContributorBase {

  private static final String LISTENER_CLASSNAME = "org.apache.shiro.web.env.EnvironmentLoaderListener";
  private static final String FILTER_CLASSNAME = "org.apache.shiro.web.servlet.ShiroFilter";
  private static final String FILTER_CLASSNAME2 = "org.apache.hadoop.gateway.filter.PostAuthenticationFilter";

  @Override
  public String getRole() {
    return "authentication";
  }

  @Override
  public String getName() {
    return "shiro";
  }

  @Override
  public void contributeProvider( DeploymentContext context, Provider provider ) {
    // add servletContextListener
    context.getWebAppDescriptor().createListener().listenerClass( LISTENER_CLASSNAME );
    // LJM TEMP: add filter
//    context.getWebAppDescriptor().createFilter().filterName("ShiroFilter").filterClass(FILTER_CLASSNAME);
//    context.getWebAppDescriptor().createFilter().filterName("PostShiroFilter").filterClass(FILTER_CLASSNAME2);
//    context.getWebAppDescriptor().createFilterMapping().filterName("ShiroFilter").servletName("cluster");
//    context.getWebAppDescriptor().createFilterMapping().filterName("PostShiroFilter").servletName("cluster");
    // Write the provider specific config out to the war for cluster specific config
//    String config = provider.getParams().get( "config" );
    String config = new ShiroConfig(provider).toString();
    if ( config != null ) {
      context.getWebArchive().addAsWebInfResource( new StringAsset( config ), "shiro.ini" );
    }
  }

  @Override
  public void contributeFilter( DeploymentContext context, Provider provider, Service service, ResourceDescriptor resource, List<FilterParamDescriptor> params ) {
    resource.addFilter().name( getName() ).role( getRole() ).impl( FILTER_CLASSNAME ).params( params );
    resource.addFilter().name( "Post" + getName() ).role( getRole() ).impl( FILTER_CLASSNAME2 ).params( params );
  }
}

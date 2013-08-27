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
  private static final String SHIRO_FILTER_CLASSNAME = "org.apache.shiro.web.servlet.ShiroFilter";
  private static final String POST_FILTER_CLASSNAME = "org.apache.hadoop.gateway.filter.PostAuthenticationFilter";
  private static final String COOKIE_FILTER_CLASSNAME = "org.apache.hadoop.gateway.filter.ResponseCookieFilter";

  @Override
  public String getRole() {
    return "authentication";
  }

  @Override
  public String getName() {
    return "ShiroProvider";
  }

  @Override
  public void contributeProvider( DeploymentContext context, Provider provider ) {
	// Many filter based authentication mechanisms require a ServletContextListener
	// to be added and the Knox deployment machinery provides the ability to add this
	// through the DeploymentContext.
	
    // add servletContextListener
    context.getWebAppDescriptor().createListener().listenerClass( LISTENER_CLASSNAME );

    // Writing provider specific config out to the war for cluster specific config can be
	// accomplished through the DeploymentContext as well. The JBoss shrinkwrap API can be
	// used to write the asset to the war.
    String config = new ShiroConfig( provider ).toString();
    if( config != null ) {
      context.getWebArchive().addAsWebInfResource( new StringAsset( config ), "shiro.ini" );
    }
  }

  @Override
  public void contributeFilter( DeploymentContext context, Provider provider, Service service, ResourceDescriptor resource, List<FilterParamDescriptor> params ) {
	// Leveraging a third party filter is a primary usecase for Knox
	// in order to do so, we need to make sure that the end result of the third party integration
	// puts a standard javax.security.auth.Subject on the current thread through a doAs.
	// As many filters do not use the standard java Subject, often times a post processing filter will
	// need to be added in order to canonicalize the result into an expected security context.
	
	// You may also need to do some additional processing of the response in order to not return cookies or other
	// filter specifics that are not needed for integration with Knox. Below we do that in the pre-processing filter.
    resource.addFilter().name( "Pre" + getName() ).role( getRole() ).impl( COOKIE_FILTER_CLASSNAME ).params( params );
    resource.addFilter().name( getName() ).role( getRole() ).impl( SHIRO_FILTER_CLASSNAME ).params( params );
    resource.addFilter().name( "Post" + getName() ).role( getRole() ).impl( POST_FILTER_CLASSNAME ).params( params );
  }
}

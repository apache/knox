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

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.filter.RedirectToUrlFilter;
import org.apache.knox.gateway.filter.ResponseCookieFilter;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;
import org.jboss.shrinkwrap.descriptor.api.webcommon30.SessionConfigType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShiroDeploymentContributor extends ProviderDeploymentContributorBase {

  private static final String LISTENER_CLASSNAME = "org.apache.shiro.web.env.EnvironmentLoaderListener";
  private static final String SHIRO_FILTER_CLASSNAME = "org.apache.shiro.web.servlet.ShiroFilter";
  private static final String POST_FILTER_CLASSNAME = "org.apache.knox.gateway.filter.ShiroSubjectIdentityAdapter";
  private static final String COOKIE_FILTER_CLASSNAME = "org.apache.knox.gateway.filter.ResponseCookieFilter";
  private static final String REDIRECT_FILTER_CLASSNAME = "org.apache.knox.gateway.filter.RedirectToUrlFilter";
  private static final String SESSION_TIMEOUT = "sessionTimeout";
  private static final String REMEMBER_ME = "rememberme";
  private static final String SHRIO_CONFIG_FILE_NAME = "shiro.ini";
  private static final int DEFAULT_SESSION_TIMEOUT = 30; // 30min

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

    // Writing provider specific config out to the war for cluster specific config can be
    // accomplished through the DeploymentContext as well. The JBoss shrinkwrap API can be
    // used to write the asset to the war.

    // add servletContextListener
    context.getWebAppDescriptor().createListener().listenerClass( LISTENER_CLASSNAME );

    // add session timeout
    int st = DEFAULT_SESSION_TIMEOUT;
    SessionConfigType<WebAppDescriptor> sessionConfig = context.getWebAppDescriptor().createSessionConfig();
    Map<String, String> params = provider.getParams();
    String sts = params.get( SESSION_TIMEOUT );
    if( sts != null && !sts.trim().isEmpty()) {
      st = Integer.parseInt( sts.trim() );
    }
    if( st <= 0 ) {
      // user default session timeout
      st = DEFAULT_SESSION_TIMEOUT;
    }
    sessionConfig.sessionTimeout( st );
    sessionConfig.getOrCreateCookieConfig().httpOnly( true );
    sessionConfig.getOrCreateCookieConfig().secure( true );

    String clusterName = context.getTopology().getName();
    ShiroConfig config = new ShiroConfig( provider, clusterName );
    String configStr = config.toString();
    context.getWebArchive().addAsWebInfResource( new StringAsset( configStr ), SHRIO_CONFIG_FILE_NAME );
  }

  @Override
  public void contributeFilter( DeploymentContext context, Provider provider,
      Service service, ResourceDescriptor resource, List<FilterParamDescriptor> params ) {
    // Leveraging a third party filter is a primary usecase for Knox
    // in order to do so, we need to make sure that the end result of the third party integration
    // puts a standard javax.security.auth.Subject on the current thread through a doAs.
    // As many filters do not use the standard java Subject, often times a post processing filter will
    // need to be added in order to canonicalize the result into an expected security context.

    // You may also need to do some additional processing of the response in order to not return cookies or other
    // filter specifics that are not needed for integration with Knox. Below we do that in the pre-processing filter.
    if (params == null) {
      params = new ArrayList<>();
    }
    Map<String, String> providerParams = provider.getParams();
    String redirectToUrl = providerParams.get(RedirectToUrlFilter.REDIRECT_TO_URL);
    if (redirectToUrl != null) {
      params.add( resource.createFilterParam()
          .name(RedirectToUrlFilter.REDIRECT_TO_URL)
          .value(redirectToUrl));
      resource.addFilter().name( "Redirect" + getName() ).role(
          getRole() ).impl( REDIRECT_FILTER_CLASSNAME ).params( params );
      params.clear();
    }

    String cookies = providerParams.get( ResponseCookieFilter.RESTRICTED_COOKIES );
    if (cookies == null) {
      params.add( resource.createFilterParam()
          .name( ResponseCookieFilter.RESTRICTED_COOKIES )
          .value( REMEMBER_ME ) );
    }
    else {
      params.add( resource.createFilterParam()
          .name(ResponseCookieFilter.RESTRICTED_COOKIES ).value( cookies ) );
    }

    resource.addFilter().name( "Pre" + getName() ).role(
        getRole() ).impl( COOKIE_FILTER_CLASSNAME ).params( params );
    params.clear();

    resource.addFilter().name( getName() ).role(
        getRole() ).impl( SHIRO_FILTER_CLASSNAME ).params( params );
    resource.addFilter().name( "Post" + getName() ).role(
        getRole() ).impl( POST_FILTER_CLASSNAME ).params( params );
  }
}

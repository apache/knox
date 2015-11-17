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
package org.apache.hadoop.gateway.pac4j.filter;

import org.pac4j.cas.client.CasClient;
import org.pac4j.cas.profile.CasProfile;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.Pac4jConstants;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.j2e.filter.CallbackFilter;
import org.pac4j.j2e.filter.RequiresAuthenticationFilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class Pac4jDispatcherFilter extends Pac4jSessionFilter {

  private static final String SSO_AUTHENTICATION_PROVIDER_URL = "sso.authentication.provider.url";

  private CallbackFilter callbackFilter;

  private RequiresAuthenticationFilter requiresAuthenticationFilter;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    // url to SSO authentication provider
    final String authenticationProviderUrl = filterConfig.getInitParameter(SSO_AUTHENTICATION_PROVIDER_URL);
    if (authenticationProviderUrl == null) {
      throw new ServletException("Required authentication provider URL is missing.");
    }

    callbackFilter = new CallbackFilter();
    requiresAuthenticationFilter = new RequiresAuthenticationFilter();
    // CAS client:
    requiresAuthenticationFilter.setClientName("CasClient");
    requiresAuthenticationFilter.setConfig(new Config(authenticationProviderUrl, new CasClient("https://casserverpac4j.herokuapp.com")));
    // Basic auth:
    //requiresAuthenticationFilter.setClientName("DirectBasicAuthClient");
    //requiresAuthenticationFilter.setConfig(new Config(new DirectBasicAuthClient(new SimpleTestUsernamePasswordAuthenticator())));
  }

  @Override
  public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

    logger.info("#############");
    logger.info("-> Pac4jDispatcherFilter");

    final HttpServletRequest request = (HttpServletRequest) servletRequest;
    final HttpServletResponse response = (HttpServletResponse) servletResponse;
    final J2EContext context = new J2EContext(request, response);

    logger.info("Incoming request: {}?{}", request.getRequestURI(), request.getQueryString());
    // it's a callback from an identity provider
    if (request.getParameter(Clients.DEFAULT_CLIENT_NAME_PARAMETER) != null) {
      // restore the web session from the cookie
      loadSession(request, response);
      // simulate CAS authentication
      final CasProfile profile = new CasProfile();
      profile.setId("jleleu");
      final ProfileManager manager = new ProfileManager(context);
      manager.save(true, profile);
      final String requestedUrl = (String) context.getSessionAttribute(Pac4jConstants.REQUESTED_URL);
      logger.debug("requestedUrl: {}", requestedUrl);
      context.setSessionAttribute(Pac4jConstants.REQUESTED_URL, null);
      response.sendRedirect("https://127.0.0.1:8443/gateway/idp/knoxsso/api/v1?originalUrl=https://127.0.0.1:8443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS");
      // apply CallbackFilter
      //callbackFilter.doFilter(servletRequest, servletResponse, filterChain);
    } else {
      // otherwise just apply security and requires authentication
      // apply RequiresAuthenticationFilter
      requiresAuthenticationFilter.doFilter(servletRequest, servletResponse, filterChain);
    }
    // save the web session into a cookie
    saveSession(request, response);
  }

  @Override
  public void destroy() { }
}

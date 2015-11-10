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

import org.pac4j.core.config.Config;
import org.pac4j.core.context.J2EContext;
import org.pac4j.j2e.filter.CallbackFilter;
import org.pac4j.j2e.filter.RequiresAuthenticationFilter;
import org.pac4j.oauth.client.FacebookClient;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class Pac4jDispatcherFilter implements Filter {

  private CallbackFilter callbackFilter;

  private RequiresAuthenticationFilter requiresAuthenticationFilter;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    callbackFilter = new CallbackFilter();
    requiresAuthenticationFilter = new RequiresAuthenticationFilter();
    requiresAuthenticationFilter.setClientName("FacebookClient");
    requiresAuthenticationFilter.setConfig(new Config("https://localhost:8443/gateway/sandbox/callback", new FacebookClient("x", "y")));
  }

  @Override
  public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

    final HttpServletRequest request = (HttpServletRequest) servletRequest;
    final HttpServletResponse response = (HttpServletResponse) servletResponse;
    final J2EContext context = new J2EContext(request, response);

    // it's a callback from an identity provider
    if (request.getContextPath().endsWith("/callback")) {
      // apply CallbackFilter
      callbackFilter.doFilter(servletRequest, servletResponse, filterChain);
    } else {
      // otherwise just apply security and requires authentication
      // apply RequiresAuthenticationFilter
      requiresAuthenticationFilter.doFilter(servletRequest, servletResponse, filterChain);
    }
  }

  @Override
  public void destroy() { }
}

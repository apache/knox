/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.gateway.provider.federation.jwt.filter;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Deprecated
public class SSOCookieFederationFilter extends org.apache.knox.gateway.provider.federation.jwt.filter.SSOCookieFederationFilter {

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    super.init(filterConfig);
  }

  @Override
  public void destroy() {
    super.destroy();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    super.doFilter(request, response, chain);
  }

  @Override
  protected void handleValidationError(HttpServletRequest request,
      HttpServletResponse response, int status, String error)
      throws IOException {
    super.handleValidationError(request, response, status, error);
  }

  /**
   * Encapsulate the acquisition of the JWT token from HTTP cookies within the
   * request.
   *
   * @param req servlet request to get the JWT token from
   * @return serialized JWT token
   */
  @Override
  protected String getJWTFromCookie(HttpServletRequest req) {
    return super.getJWTFromCookie(req);
  }

  /**
   * Create the URL to be used for authentication of the user in the absence of
   * a JWT token within the incoming request.
   *
   * @param request for getting the original request URL
   * @return url to use as login url for redirect
   */
  @Override
  protected String constructLoginURL(HttpServletRequest request) {
    return super.constructLoginURL(request);
  }
}

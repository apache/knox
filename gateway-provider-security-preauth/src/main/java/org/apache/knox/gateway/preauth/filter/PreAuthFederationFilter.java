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
package org.apache.knox.gateway.preauth.filter;

import java.io.IOException;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class PreAuthFederationFilter implements Filter {
  private static final String CUSTOM_HEADER_PARAM = "preauth.customHeader";
  private List<PreAuthValidator> validators;
  private FilterConfig filterConfig;
  private String headerName = "SM_USER";

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    String customHeader = filterConfig.getInitParameter(CUSTOM_HEADER_PARAM);
    if (customHeader != null) {
      headerName = customHeader;
    }
    this.filterConfig = filterConfig;
    validators = PreAuthService.getValidators(filterConfig);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
                       FilterChain chain) throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    if (httpRequest.getHeader(headerName) != null) {
      if (PreAuthService.validate(httpRequest, filterConfig, validators)) {
        // TODO: continue as subject
        chain.doFilter(request, response);
      } else {
        // TODO: log preauthenticated SSO validation failure
        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing Required Header for SSO Validation");
      }
    } else {
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing Required Header for PreAuth SSO Federation");
    }
  }

  @Override
  public void destroy() {

  }
}

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
package org.apache.knox.gateway.webappsec.filter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CSRFPreventionFilter implements Filter {
  private static final String CUSTOM_HEADER_PARAM = "csrf.customheader";
  private static final String CUSTOM_METHODS_TO_IGNORE_PARAM = "csrf.methodstoignore";
  private String  headerName = "X-XSRF-Header";
  private String  mti = "GET,OPTIONS,HEAD";
  private Set<String> methodsToIgnore = null;
  
  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    String customHeader = filterConfig.getInitParameter(CUSTOM_HEADER_PARAM);
    if (customHeader != null) {
      headerName = customHeader;
    }
    String customMTI = filterConfig.getInitParameter(CUSTOM_METHODS_TO_IGNORE_PARAM);
    if (customMTI != null) {
      mti = customMTI;
    }
    String[] methods = mti.split(",");
    methodsToIgnore = new HashSet<>();
    for (int i = 0; i < methods.length; i++) {
      methodsToIgnore.add(methods[i]);
    }
  }
  
  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest)request;
    if ( methodsToIgnore.contains( httpRequest.getMethod() ) || httpRequest.getHeader(headerName) != null ) {
      chain.doFilter(request, response);
    } else {
      ((HttpServletResponse)response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing Required Header for Vulnerability Protection");
    }
  }

  /* (non-Javadoc)
   * @see javax.servlet.Filter#destroy()
   */
  @Override
  public void destroy() {
    // TODO Auto-generated method stub
    
  }
}

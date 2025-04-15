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

import org.apache.knox.gateway.webappsec.deploy.WebAppSecContributor;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SecurityHeaderFilter implements Filter {

  private Map<String, String> securityHeaders = new HashMap<>();

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    // Dynamically add headers based on init parameters
    Enumeration<String> initParamNames = filterConfig.getInitParameterNames();
    while (initParamNames.hasMoreElements()) {
      String headerName = initParamNames.nextElement();
      if (!WebAppSecContributor.ENABLED.equals(headerName)) {
        String headerValue = filterConfig.getInitParameter(headerName);
        securityHeaders.put(headerName, headerValue);
      }
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
          throws IOException, ServletException {

    HttpServletResponse httpResponse = new SecurityHeaderResponseWrapper((HttpServletResponse) response);

    // Dynamically add headers based on init parameters
    for (Map.Entry<String, String> entry : securityHeaders.entrySet()) {
      String headerName = entry.getKey();
      String headerValue = entry.getValue();
      httpResponse.setHeader(headerName, headerValue);
    }

    // Continue the filter chain
    chain.doFilter(request, httpResponse);
  }

  @Override
  public void destroy() {
    // Cleanup logic if needed
  }

  class SecurityHeaderResponseWrapper extends HttpServletResponseWrapper {

    SecurityHeaderResponseWrapper(HttpServletResponse res) {
      super(res);
    }

    @Override
    public void addHeader(String name, String value) {
      if (!WebAppSecContributor.ENABLED.equals(name)) {
        super.addHeader(name, value);
      }
    }

    @Override
    public void setHeader(String name, String value) {
      if (!WebAppSecContributor.ENABLED.equals(name)) {
        super.setHeader(name, value);
      }
    }

    @Override
    public String getHeader(String name) {
      String value;
      value = securityHeaders.get(name);
      if (value == null) {
        value = super.getHeader(name);
      }
      return value;
    }

    @Override
    public Collection<String> getHeaderNames() {
      Set<String> names = new HashSet<>(securityHeaders.keySet());
      if (super.getHeaderNames() != null) {
        names.addAll(super.getHeaderNames());
      }
      return names;
    }

    @Override
    public Collection<String> getHeaders(String name) {
      Set<String> values = new HashSet<>(securityHeaders.values());
      if (super.getHeaders(name) != null) {
        values.addAll(super.getHeaders(name));
      }
      return values;
    }
  }
}

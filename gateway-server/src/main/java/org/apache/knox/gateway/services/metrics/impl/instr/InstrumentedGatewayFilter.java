/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.services.metrics.impl.instr;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.apache.knox.gateway.GatewayFilter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

public class InstrumentedGatewayFilter extends GatewayFilter {

  private GatewayFilter gatewayFilter;

  private MetricRegistry metricRegistry;

  public InstrumentedGatewayFilter(GatewayFilter gatewayFilter, MetricRegistry metricRegistry) {
    this.gatewayFilter = gatewayFilter;
    this.metricRegistry = metricRegistry;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    gatewayFilter.init(filterConfig);
  }

  @Override
  public void destroy() {
    gatewayFilter.destroy();
  }

  @Override
  public void addFilter(String path, String name, Filter filter, Map<String, String> params, String resourceRole) throws URISyntaxException {
    gatewayFilter.addFilter(path, name, filter, params, resourceRole);
  }

  @Override
  public void addFilter(String path, String name, String clazz, Map<String, String> params, String resourceRole) throws URISyntaxException {
    gatewayFilter.addFilter(path, name, clazz, params, resourceRole);
  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
    Timer.Context timerContext = this.timer(servletRequest).time();
    try {
      gatewayFilter.doFilter(servletRequest, servletResponse, filterChain);
    } finally {
      timerContext.stop();
    }
  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) throws IOException, ServletException {
    Timer.Context timerContext = this.timer(servletRequest).time();
    try {
      gatewayFilter.doFilter(servletRequest, servletResponse);
    } finally {
      timerContext.stop();
    }
  }

  private Timer timer(ServletRequest request) {
    StringBuilder builder = new StringBuilder();
    builder.append("client.");
    builder.append(request.getServletContext().getContextPath());
    if (request instanceof HttpServletRequest) {
      HttpServletRequest httpServletRequest = (HttpServletRequest) request;
      builder.append(InstrUtils.getResourcePath(httpServletRequest.getPathInfo()));
      builder.append(".");
      builder.append(httpServletRequest.getMethod());
      builder.append("-requests");
    }
    return metricRegistry.timer(builder.toString());
  }
}

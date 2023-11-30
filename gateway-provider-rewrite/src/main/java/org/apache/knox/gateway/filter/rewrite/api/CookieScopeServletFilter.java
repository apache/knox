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
package org.apache.knox.gateway.filter.rewrite.api;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.filter.rewrite.impl.CookieScopeResponseWrapper;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

public class CookieScopeServletFilter extends AbstractGatewayFilter {
  private static final GatewaySpiMessages LOG = MessagesFactory.get(GatewaySpiMessages.class);
  private String gatewayPath;
  private String topologyName;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    super.init( filterConfig );
    gatewayPath = filterConfig.getInitParameter("gateway.path");
    topologyName = filterConfig.getInitParameter("topologyName");
  }

  @Override
  protected void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain )
      throws IOException, ServletException {
    if (Boolean.parseBoolean((String)request.getAttribute(DEFAULT_TOPOLOGY_FORWARD_ATTRIBUTE_NAME))) {
      LOG.ignoringCookiePathScopeForDefaultTopology();
      chain.doFilter(request, response);
    } else {
      chain.doFilter(request, new CookieScopeResponseWrapper(response, gatewayPath, topologyName));
    }
  }

}

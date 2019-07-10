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

package org.apache.knox.gateway.filter;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.config.GatewayConfig;

public class RedirectToUrlFilter extends AbstractGatewayFilter {
  public static final String REDIRECT_TO_URL = "redirectToUrl";
  private static final String GATEWAY_PATH_PLACEHOLDER = "${GATEWAY_PATH}";

  private String redirectUrl;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    super.init(filterConfig);
    redirectUrl = filterConfig.getInitParameter(REDIRECT_TO_URL);
    if (redirectUrl != null && redirectUrl.contains(GATEWAY_PATH_PLACEHOLDER)) {
      final GatewayConfig gatewayConfig = (GatewayConfig) filterConfig.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
      redirectUrl = redirectUrl.replaceAll(Pattern.quote(GATEWAY_PATH_PLACEHOLDER), gatewayConfig.getGatewayPath());
    }
  }

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    if (redirectUrl != null && request.getHeader("Authorization") == null) {
      response.sendRedirect(redirectUrl + getOriginalQueryString(request));
    }
    chain.doFilter(request, response);
  }

  private String getOriginalQueryString(HttpServletRequest request) {
    String originalQueryString = request.getQueryString();
    return (originalQueryString == null) ? "" : "?" + originalQueryString;
  }

  String getRedirectUrl() {
    return redirectUrl;
  }
}

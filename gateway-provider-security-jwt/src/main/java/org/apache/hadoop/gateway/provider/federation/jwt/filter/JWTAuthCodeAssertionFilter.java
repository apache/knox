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
package org.apache.hadoop.gateway.provider.federation.jwt.filter;

import java.io.IOException;
import java.security.AccessController;
import java.util.HashMap;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hadoop.gateway.filter.security.AbstractIdentityAssertionFilter;
import org.apache.hadoop.gateway.provider.federation.jwt.JWTAuthority;
import org.apache.hadoop.gateway.provider.federation.jwt.JWTToken;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.security.CryptoService;
import org.apache.hadoop.gateway.util.JsonUtils;

public class JWTAuthCodeAssertionFilter extends AbstractIdentityAssertionFilter {
  private static final String BEARER = "Bearer ";
  
  private CryptoService crypto = null;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    super.init(filterConfig);
    String validityStr = filterConfig.getInitParameter("validity");
    if (validityStr == null) {
      validityStr = "3600"; // 1 hr. in secs
    }
//    validity = Long.parseLong(validityStr);

    GatewayServices services = (GatewayServices) filterConfig.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    crypto = (CryptoService) services.getService(GatewayServices.CRYPTO_SERVICE);
  }
  
  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {

      Subject subject = Subject.getSubject(AccessController.getContext());
      String principalName = getPrincipalName(subject);
      principalName = mapper.mapPrincipal(principalName);
      JWTAuthority authority = new JWTAuthority(crypto);
      JWTToken authCode = authority.issueToken(subject, "RS256");
      
      HashMap<String, Object> map = new HashMap<String, Object>();
      // TODO: populate map from JWT authorization code
      map.put("iss", authCode.getIssuer());
      map.put("sub", authCode.getPrincipal());
      map.put("aud", authCode.getAudience());
      map.put("exp", authCode.getExpires());
      map.put("code", authCode.toString());
      
      String jsonResponse = JsonUtils.renderAsJsonString(map);
      
      response.getWriter().write(jsonResponse);
      response.getWriter().flush();
      return; // break filter chain
  }
}

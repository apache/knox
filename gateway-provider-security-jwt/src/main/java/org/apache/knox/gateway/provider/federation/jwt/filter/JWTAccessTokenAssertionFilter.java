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
package org.apache.knox.gateway.provider.federation.jwt.filter;

import java.io.IOException;
import java.security.AccessController;
import java.security.Principal;
import java.text.ParseException;
import java.util.HashMap;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.filter.security.AbstractIdentityAssertionFilter;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.registry.ServiceRegistry;
import org.apache.knox.gateway.services.security.token.JWTokenAttributes;
import org.apache.knox.gateway.services.security.token.JWTokenAttributesBuilder;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.JsonUtils;

public class JWTAccessTokenAssertionFilter extends AbstractIdentityAssertionFilter {
  private static final String SVC_URL = "svc";
  private static final String EXPIRES_IN = "expires_in";
  private static final String TOKEN_TYPE = "token_type";
  private static final String ACCESS_TOKEN = "access_token";
  private static final String BEARER = "Bearer ";
  private static JWTMessages log = MessagesFactory.get( JWTMessages.class );
  private long validity;
  private JWTokenAuthority authority;
  private ServiceRegistry sr;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    super.init(filterConfig);
    String validityStr = filterConfig.getInitParameter("validity");
    if (validityStr == null) {
      validityStr = "3600"; // 1 hr. in secs
    }
    validity = Long.parseLong(validityStr);

    GatewayServices services = (GatewayServices) filterConfig.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    authority = services.getService(ServiceType.TOKEN_SERVICE);
    sr = services.getService(ServiceType.SERVICE_REGISTRY_SERVICE);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    String jsonResponse;

    String header = ((HttpServletRequest) request).getHeader("Authorization");
    if (header != null && header.startsWith(BEARER)) {
      // what follows the bearer designator should be the JWT token being used to request or as an access token
      String wireToken = header.substring(BEARER.length());
      JWTToken token;
      try {
        token = JWTToken.parseToken(wireToken);
      } catch (ParseException e) {
        throw new ServletException("ParseException encountered while processing the JWT token: ", e);
      }
      // ensure that there is a valid jwt token available and that there isn't a misconfiguration of filters
      if (token != null) {
        try {
          authority.verifyToken(token);
        }
        catch (TokenServiceException e) {
          log.unableToVerifyToken(e);
        }
      }
      else {
        throw new ServletException("Expected JWT Token not provided as Bearer token");
      }

      // authorization of the user for the requested service (and resource?) should have been done by
      // the JWTFederationFilter - once we get here we can assume that it is authorized and we just need
      // to assert the identity via an access token

      Subject subject = Subject.getSubject(AccessController.getContext());
      String principalName = getPrincipalName(subject);
      principalName = mapper.mapUserPrincipal(principalName);

      // calculate expiration timestamp: validity * 1000 + currentTimeInMillis
      long expires = System.currentTimeMillis() + validity * 1000;

      String serviceName = request.getParameter("service-name");
      String clusterName = request.getParameter("cluster-name");
      String accessToken = getAccessToken(principalName, serviceName, expires);

      String serviceURL = sr.lookupServiceURL(clusterName, serviceName);

      HashMap<String, Object> map = new HashMap<>();
      // TODO: populate map from JWT authorization code
      map.put(ACCESS_TOKEN, accessToken);
      map.put(TOKEN_TYPE, BEARER);
      map.put(EXPIRES_IN, expires);

      // TODO: this url needs to be rewritten when in gateway deployments....
      map.put(SVC_URL, serviceURL);

      jsonResponse = JsonUtils.renderAsJsonString(map);

      response.getWriter().write(jsonResponse);
    }
    else {
      // no token provided in header
      // something is really wrong since the JWTFederationFilter should have verified its existence already
      // TODO: may have to check cookie and url as well before sending error
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }

  private String getAccessToken(final String principalName, String serviceName, long expires) {
    String accessToken = null;

    Principal p = new Principal() {
      @Override
      public String getName() {
        return principalName;
      }
    };
    JWT token;
    try {
      final JWTokenAttributes jwtAttributes = new JWTokenAttributesBuilder().setPrincipal(p).setAudiences(serviceName).setAlgorithm(signatureAlgorithm).setExpires(expires).build();
      token = authority.issueToken(jwtAttributes);
      // Coverity CID 1327961
      if( token != null ) {
        accessToken = token.toString();
      }
    } catch (TokenServiceException e) {
      log.unableToIssueToken(e);
    }

    return accessToken;
  }
}

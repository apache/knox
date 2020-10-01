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

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.Tokens;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class AccessTokenFederationFilter implements Filter {
  private static JWTMessages log = MessagesFactory.get( JWTMessages.class );
  private static final String BEARER = "Bearer ";

  private JWTokenAuthority authority;

  private TokenStateService tokenStateService;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    GatewayServices services = (GatewayServices) filterConfig.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    authority = services.getService(ServiceType.TOKEN_SERVICE);

    if (TokenUtils.isServerManagedTokenStateEnabled(filterConfig)) {
      tokenStateService = services.getService(ServiceType.TOKEN_STATE_SERVICE);
    }
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
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

      boolean verified = false;
      try {
        verified = authority.verifyToken(token);
      } catch (TokenServiceException e) {
        log.unableToVerifyToken(e);
      }

      final String tokenId = TokenUtils.getTokenId(token);
      final String displayableToken = Tokens.getTokenDisplayText(token.toString());
      if (verified) {
        try {
          if (!isExpired(token)) {
            if (((HttpServletRequest) request).getRequestURL().indexOf(token.getAudience().toLowerCase(Locale.ROOT)) != -1) {
              Subject subject = createSubjectFromToken(token);
              continueWithEstablishedSecurityContext(subject, (HttpServletRequest)request, (HttpServletResponse)response, chain);
            } else {
              log.failedToValidateAudience(tokenId, displayableToken);
              sendUnauthorized(response);
            }
          } else {
            log.tokenHasExpired(tokenId, displayableToken);
            sendUnauthorized(response);
          }
        } catch (UnknownTokenException e) {
          log.unableToVerifyExpiration(e);
          sendUnauthorized(response);
        }
      } else {
        log.failedToVerifyTokenSignature(tokenId, displayableToken);
        sendUnauthorized(response);
      }
    } else {
      log.missingBearerToken();
      sendUnauthorized(response);
    }
  }

  private boolean isExpired(JWTToken token) throws UnknownTokenException {
    return (tokenStateService != null) ? tokenStateService.isExpired(token)
                                       : (Long.parseLong(token.getExpires()) <= System.currentTimeMillis());
  }

  private void sendUnauthorized(ServletResponse response) throws IOException {
    ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
  }

  private void continueWithEstablishedSecurityContext(Subject subject,
                                                      final HttpServletRequest request,
                                                      final HttpServletResponse response,
                                                      final FilterChain chain) throws IOException, ServletException {
    try {
      Subject.doAs(
        subject,
        new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws Exception {
            chain.doFilter(request, response);
            return null;
          }
        }
        );
    }
    catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      else if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      else {
        throw new ServletException(t);
      }
    }
  }

  private Subject createSubjectFromToken(JWTToken token) {
    final String principal = token.getPrincipal();

    HashSet emptySet = new HashSet();
    Set<Principal> principals = new HashSet<>();
    Principal p = new PrimaryPrincipal(principal);
    principals.add(p);

    // The newly constructed Sets check whether this Subject has been set read-only
    // before permitting subsequent modifications. The newly created Sets also prevent
    // illegal modifications by ensuring that callers have sufficient permissions.
    //
    // To modify the Principals Set, the caller must have AuthPermission("modifyPrincipals").
    // To modify the public credential Set, the caller must have AuthPermission("modifyPublicCredentials").
    // To modify the private credential Set, the caller must have AuthPermission("modifyPrivateCredentials").
    return new javax.security.auth.Subject(true, principals, emptySet, emptySet);
  }
}

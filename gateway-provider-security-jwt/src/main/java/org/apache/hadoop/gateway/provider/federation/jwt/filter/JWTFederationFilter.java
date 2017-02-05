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

import org.apache.commons.logging.Log;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.provider.federation.jwt.JWTMessages;
import org.apache.hadoop.gateway.security.PrimaryPrincipal;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.security.token.JWTokenAuthority;
import org.apache.hadoop.gateway.services.security.token.TokenServiceException;
import org.apache.hadoop.gateway.services.security.token.impl.JWTToken;

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
import java.util.Set;

public class JWTFederationFilter implements Filter {

  private static final String BEARER = "Bearer ";
  private static JWTMessages log = MessagesFactory.get( JWTMessages.class );
  private JWTokenAuthority authority = null;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    GatewayServices services = (GatewayServices) filterConfig.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    authority = (JWTokenAuthority) services.getService(GatewayServices.TOKEN_SERVICE);
  }

  public void destroy() {
  }

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
      throws IOException, ServletException {
    String header = ((HttpServletRequest) request).getHeader("Authorization");
    if (header != null && header.startsWith(BEARER)) {
      // what follows the bearer designator should be the JWT token being used to request or as an access token
      String wireToken = header.substring(BEARER.length());
      JWTToken token;
//      try {
        token = new JWTToken(wireToken);
//        token = JWTToken.parseToken(wireToken);
//      } catch (ParseException e) {
//        throw new ServletException("ParseException encountered while processing the JWT token: ", e);
//      }
      boolean verified = false;
      try {
        verified = authority.verifyToken(token);
      } catch (TokenServiceException e) {
        log.unableToVerifyToken(e);
      }
      if (verified) {
        // TODO: validate expiration
        // confirm that audience matches intended target - which for this filter must be KNOXSSO
        if (token.getIssuer().equals("KNOXSSO")) {
          // TODO: verify that the user requesting access to the service/resource is authorized for it - need scopes?
          Subject subject = createSubjectFromToken(token);
          continueWithEstablishedSecurityContext(subject, (HttpServletRequest)request, (HttpServletResponse)response, chain);
        }
        else {
          ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
          return; //break filter chain
        }
      }
      else {
        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return; //break filter chain
      }
    }
    else {
      // no token provided in header
      // TODO: may have to check cookie and url as well before sending error
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return; //break filter chain
    }
  }
  
  private void continueWithEstablishedSecurityContext(Subject subject, final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain) throws IOException, ServletException {
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
    final String principal = token.getSubject();

    @SuppressWarnings("rawtypes")
    HashSet emptySet = new HashSet();
    Set<Principal> principals = new HashSet<Principal>();
    Principal p = new PrimaryPrincipal(principal);
    principals.add(p);
    
//        The newly constructed Sets check whether this Subject has been set read-only 
//        before permitting subsequent modifications. The newly created Sets also prevent 
//        illegal modifications by ensuring that callers have sufficient permissions.
//
//        To modify the Principals Set, the caller must have AuthPermission("modifyPrincipals"). 
//        To modify the public credential Set, the caller must have AuthPermission("modifyPublicCredentials"). 
//        To modify the private credential Set, the caller must have AuthPermission("modifyPrivateCredentials").
    javax.security.auth.Subject subject = new javax.security.auth.Subject(true, principals, emptySet, emptySet);
    return subject;
  }

}

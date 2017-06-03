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
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.provider.federation.jwt.JWTMessages;
import org.apache.hadoop.gateway.security.PrimaryPrincipal;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.security.token.JWTokenAuthority;
import org.apache.hadoop.gateway.services.security.token.TokenServiceException;
import org.apache.hadoop.gateway.services.security.token.impl.JWTToken;

/**
 *
 */
public abstract class AbstractJWTFilter implements Filter {
  static JWTMessages log = MessagesFactory.get( JWTMessages.class );
  protected List<String> audiences;
  protected JWTokenAuthority authority;
  protected String verificationPEM = null;
  protected RSAPublicKey publicKey = null;

  public abstract void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException;

  /**
   * 
   */
  public AbstractJWTFilter() {
    super();
  }

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    ServletContext context = filterConfig.getServletContext();
    if (context != null) {
      GatewayServices services = (GatewayServices) context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      if (services != null) {
        authority = (JWTokenAuthority) services.getService(GatewayServices.TOKEN_SERVICE);
      }
    }
  }

  /**
   * @param expectedAudiences
   * @return
   */
  protected List<String> parseExpectedAudiences(String expectedAudiences) {
    ArrayList<String> audList = null;
    // setup the list of valid audiences for token validation
    if (expectedAudiences != null) {
      // parse into the list
      String[] audArray = expectedAudiences.split(",");
      audList = new ArrayList<String>();
      for (String a : audArray) {
        audList.add(a);
      }
    }
    return audList;
  }

  protected boolean tokenIsStillValid(JWTToken jwtToken) {
    // if there is no expiration date then the lifecycle is tied entirely to
    // the cookie validity - otherwise ensure that the current time is before
    // the designated expiration time
    Date expires = jwtToken.getExpiresDate();
    return (expires == null || expires != null && new Date().before(expires));
  }

  /**
   * Validate whether any of the accepted audience claims is present in the
   * issued token claims list for audience. Override this method in subclasses
   * in order to customize the audience validation behavior.
   *
   * @param jwtToken
   *          the JWT token where the allowed audiences will be found
   * @return true if an expected audience is present, otherwise false
   */
  protected boolean validateAudiences(JWTToken jwtToken) {
    boolean valid = false;
    
    String[] tokenAudienceList = jwtToken.getAudienceClaims();
    // if there were no expected audiences configured then just
    // consider any audience acceptable
    if (audiences == null) {
      valid = true;
    } else {
      // if any of the configured audiences is found then consider it
      // acceptable
      if (tokenAudienceList != null) {
        for (String aud : tokenAudienceList) {
          if (audiences.contains(aud)) {
            log.jwtAudienceValidated();
            valid = true;
            break;
          }
        }
      }
    }
    return valid;
  }

  protected void continueWithEstablishedSecurityContext(Subject subject, final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain) throws IOException, ServletException {
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

  protected Subject createSubjectFromToken(JWTToken token) {
    final String principal = token.getSubject();

    @SuppressWarnings("rawtypes")
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
    javax.security.auth.Subject subject = new javax.security.auth.Subject(true, principals, emptySet, emptySet);
    return subject;
  }
  
  protected boolean validateToken(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain, JWTToken token)
      throws IOException, ServletException {
    boolean verified = false;
    try {
      if (publicKey == null) {
        verified = authority.verifyToken(token);
      }
      else {
        verified = authority.verifyToken(token, publicKey);
      }
    } catch (TokenServiceException e) {
      log.unableToVerifyToken(e);
    }
    
    if (verified) {
      // confirm that issue matches intended target - which for this filter must be KNOXSSO
      if (token.getIssuer().equals("KNOXSSO")) {
        // if there is no expiration data then the lifecycle is tied entirely to
        // the cookie validity - otherwise ensure that the current time is before
        // the designated expiration time
        if (tokenIsStillValid(token)) {
          boolean audValid = validateAudiences(token);
          if (audValid) {
            return true;
          }
          else {
            log.failedToValidateAudience();
            handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST, 
                                  "Bad request: missing required token audience");
          }
        }
        else {
          log.tokenHasExpired();
          handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST, 
                                "Bad request: token has expired");
        }
      }
      else {
        handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, null);
      }
    }
    else {
      log.failedToVerifyTokenSignature();
      handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED, null);
    }

    return false;
  }
  
  protected abstract void handleValidationError(HttpServletRequest request, HttpServletResponse response, int status, 
                                                String error) throws IOException;
  
}
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
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.provider.federation.jwt.JWTMessages;
import org.apache.hadoop.gateway.security.PrimaryPrincipal;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.security.token.JWTokenAuthority;
import org.apache.hadoop.gateway.services.security.token.TokenServiceException;
import org.apache.hadoop.gateway.services.security.token.impl.JWTToken;

public class SSOCookieFederationFilter implements Filter {
  private static JWTMessages log = MessagesFactory.get( JWTMessages.class );
  private static final String ORIGINAL_URL_QUERY_PARAM = "originalUrl=";
  public static final String SSO_COOKIE_NAME = "sso.cookie.name";
  public static final String SSO_EXPECTED_AUDIENCES = "sso.expected.audiences";
  public static final String SSO_AUTHENTICATION_PROVIDER_URL = "sso.authentication.provider.url";
  private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";

  protected JWTokenAuthority authority = null;
  private String cookieName = null;
  private List<String> audiences = null;
  private String authenticationProviderUrl = null;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    ServletContext context = filterConfig.getServletContext();
    if (context != null) {
      GatewayServices services = (GatewayServices) context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      if (services != null) {
        authority = (JWTokenAuthority) services.getService(GatewayServices.TOKEN_SERVICE);
      }
    }
    
    // configured cookieName
    cookieName = filterConfig.getInitParameter(SSO_COOKIE_NAME);
    if (cookieName == null) {
      cookieName = DEFAULT_SSO_COOKIE_NAME;
    }

    // expected audiences or null
    String expectedAudiences = filterConfig.getInitParameter(SSO_EXPECTED_AUDIENCES);
    if (expectedAudiences != null) {
      audiences = parseExpectedAudiences(expectedAudiences);
    }

    // url to SSO authentication provider
    authenticationProviderUrl = filterConfig.getInitParameter(SSO_AUTHENTICATION_PROVIDER_URL);
    if (authenticationProviderUrl == null) {
      log.missingAuthenticationProviderUrlConfiguration();
      throw new ServletException("Required authentication provider URL is missing.");
    }
  }

  /**
   * @param expectedAudiences
   * @return
   */
  private List<String> parseExpectedAudiences(String expectedAudiences) {
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

  public void destroy() {
  }

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
      throws IOException, ServletException {
    String wireToken = null;
    HttpServletRequest req = (HttpServletRequest) request;

    String loginURL = constructLoginURL(req);
    wireToken = getJWTFromCookie(req);
    if (wireToken == null) {
      if (req.getMethod().equals("OPTIONS")) {
        // CORS preflight requests to determine allowed origins and related config
        // must be able to continue without being redirected
        Subject sub = new Subject();
        sub.getPrincipals().add(new PrimaryPrincipal("anonymous"));
        continueWithEstablishedSecurityContext(sub, req, (HttpServletResponse) response, chain);
      }
      log.sendRedirectToLoginURL(loginURL);
      ((HttpServletResponse) response).sendRedirect(loginURL);
    }
    else {
      JWTToken token = new JWTToken(wireToken);
      boolean verified = false;
      try {
        verified = authority.verifyToken(token);
        if (verified) {
          Date expires = token.getExpiresDate();
          if (expires != null && new Date().before(expires)) {
            boolean audValid = validateAudiences(token);
            if (audValid) {
              Subject subject = createSubjectFromToken(token);
              continueWithEstablishedSecurityContext(subject, (HttpServletRequest)request, (HttpServletResponse)response, chain);
            }
            else {
              log.failedToValidateAudience();
              ((HttpServletResponse) response).sendRedirect(loginURL);
            }
          }
          else {
            log.tokenHasExpired();
          ((HttpServletResponse) response).sendRedirect(loginURL);
          }
        }
        else {
          log.failedToVerifyTokenSignature();
        ((HttpServletResponse) response).sendRedirect(loginURL);
        }
      } catch (TokenServiceException e) {
        log.unableToVerifyToken(e);
      ((HttpServletResponse) response).sendRedirect(loginURL);
      }
    }
  }

  /**
   * Encapsulate the acquisition of the JWT token from HTTP cookies within the
   * request.
   *
   * @param req servlet request to get the JWT token from
   * @return serialized JWT token
   */
  protected String getJWTFromCookie(HttpServletRequest req) {
    String serializedJWT = null;
    Cookie[] cookies = req.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (cookieName.equals(cookie.getName())) {
          log.cookieHasBeenFound(cookieName);
          serializedJWT = cookie.getValue();
          break;
        }
      }
    }
    return serializedJWT;
  }

  /**
   * Create the URL to be used for authentication of the user in the absence of
   * a JWT token within the incoming request.
   *
   * @param request for getting the original request URL
   * @return url to use as login url for redirect
   */
  protected String constructLoginURL(HttpServletRequest request) {
    String delimiter = "?";
    if (authenticationProviderUrl.contains("?")) {
      delimiter = "&";
    }
    String loginURL = authenticationProviderUrl + delimiter
        + ORIGINAL_URL_QUERY_PARAM
        + request.getRequestURL().append(getOriginalQueryString(request));
    return loginURL;
  }

  private String getOriginalQueryString(HttpServletRequest request) {
    String originalQueryString = request.getQueryString();
    return (originalQueryString == null) ? "" : "?" + originalQueryString;
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

  private void sendUnauthorized(ServletResponse response) throws IOException {
    ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    return;
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

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
package org.apache.knox.gateway.provider.federation.jwt.filter;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.CertificateUtils;
import org.eclipse.jetty.http.MimeTypes;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;

public class SSOCookieFederationFilter extends AbstractJWTFilter {
  public static final String SSO_COOKIE_NAME = "sso.cookie.name";
  public static final String SSO_EXPECTED_AUDIENCES = "sso.expected.audiences";
  public static final String SSO_AUTHENTICATION_PROVIDER_URL = "sso.authentication.provider.url";
  public static final String SSO_VERIFICATION_PEM = "sso.token.verification.pem";

  private static final String ORIGINAL_URL_QUERY_PARAM = "originalUrl=";
  private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";
  private static final String XHR_HEADER = "X-Requested-With";
  private static final String XHR_VALUE = "XMLHttpRequest";
  private static JWTMessages log = MessagesFactory.get( JWTMessages.class );

  private String cookieName;
  private String authenticationProviderUrl;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    super.init(filterConfig);

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

    // token verification pem
    String verificationPEM = filterConfig.getInitParameter(SSO_VERIFICATION_PEM);
    // setup the public key of the token issuer for verification
    if (verificationPEM != null) {
      publicKey = CertificateUtils.parseRSAPublicKey(verificationPEM);
    }

    configureExpectedParameters(filterConfig);
  }

  public void destroy() {
  }

  @Override
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
      try {
        JWT token = new JWTToken(wireToken);
        if (validateToken((HttpServletRequest)request, (HttpServletResponse)response, chain, token)) {
          Subject subject = createSubjectFromToken(token);
          continueWithEstablishedSecurityContext(subject, (HttpServletRequest)request, (HttpServletResponse)response, chain);
        }
      } catch (ParseException ex) {
        ((HttpServletResponse) response).sendRedirect(loginURL);
      }
    }
  }

  protected void handleValidationError(HttpServletRequest request, HttpServletResponse response, int status,
                                       String error) throws IOException {
    String loginURL = constructLoginURL(request);

    /* We don't need redirect if this is a XHR request */
    if (request.getHeader(XHR_HEADER) != null && request.getHeader(XHR_HEADER)
        .equalsIgnoreCase(XHR_VALUE)) {
      final byte[] data = error.getBytes("UTF-8");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MimeTypes.Type.TEXT_PLAIN.toString());
      response.setContentLength(data.length);
      response.getOutputStream().write(data);
    } else {
      response.sendRedirect(loginURL);
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

}

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

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.CertificateUtils;
import org.apache.knox.gateway.util.CookieUtils;
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
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;

public class SSOCookieFederationFilter extends AbstractJWTFilter {
  private static final JWTMessages LOGGER = MessagesFactory.get( JWTMessages.class );

  public static final String XHR_HEADER = "X-Requested-With";
  public static final String XHR_VALUE = "XMLHttpRequest";

  private static final String GATEWAY_PATH = "gateway.path";
  public static final String SSO_COOKIE_NAME = "sso.cookie.name";
  public static final String SSO_EXPECTED_AUDIENCES = "sso.expected.audiences";
  public static final String SSO_AUTHENTICATION_PROVIDER_URL = "sso.authentication.provider.url";
  public static final String SSO_VERIFICATION_PEM = "sso.token.verification.pem";
  public static final String X_FORWARDED_HOST = "X-Forwarded-Host";
  public static final String X_FORWARDED_PORT = "X-Forwarded-Port";
  public static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";

  private static final String ORIGINAL_URL_QUERY_PARAM = "originalUrl=";
  private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";

  private String cookieName;
  private String authenticationProviderUrl;
  private String gatewayPath;

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
      LOGGER.missingAuthenticationProviderUrlConfiguration();
    }

    // token verification pem
    String verificationPEM = filterConfig.getInitParameter(SSO_VERIFICATION_PEM);
    // setup the public key of the token issuer for verification
    if (verificationPEM != null) {
      publicKey = CertificateUtils.parseRSAPublicKey(verificationPEM);
    }

    // gateway path for deriving an idp url when missing
    setGatewayPath(filterConfig);

    configureExpectedParameters(filterConfig);
  }

  private void setGatewayPath(FilterConfig filterConfig) {
    gatewayPath = filterConfig.getInitParameter(GATEWAY_PATH);
    if (gatewayPath == null || gatewayPath.isEmpty()) {
      final GatewayConfig gatewayConfig = filterConfig.getServletContext() == null ? null
          : (GatewayConfig) filterConfig.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
      if (gatewayConfig != null) {
        gatewayPath = gatewayConfig.getGatewayPath();
      }
    }
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;

    List<Cookie> ssoCookies = CookieUtils.getCookiesForName(req, cookieName);
    if (ssoCookies.isEmpty()) {
      if (req.getMethod().equals("OPTIONS")) {
        // CORS preflight requests to determine allowed origins and related config
        // must be able to continue without being redirected
        Subject sub = new Subject();
        sub.getPrincipals().add(new PrimaryPrincipal("anonymous"));
        continueWithEstablishedSecurityContext(sub, req, res, chain);
      } else {
        sendRedirectToLoginURL(req, res);
      }
    } else {
      for(Cookie ssoCookie : ssoCookies) {
        String wireToken = ssoCookie.getValue();
        try {
          JWT token = new JWTToken(wireToken);
          if (validateToken(req, res, chain, token)) {
            Subject subject = createSubjectFromToken(token);
            continueWithEstablishedSecurityContext(subject, req, res, chain);

            // we found a valid cookie we don't need to keep checking anymore
            return;
          }
        } catch (ParseException ignore) {
          // Ignore the error since cookie was invalid
          // Fall through to keep checking if there are more cookies
        }
      }

      // There were no valid cookies found so redirect to login url
      if(res != null && !res.isCommitted()) {
        sendRedirectToLoginURL(req, res);
      }
    }
  }

  private void sendRedirectToLoginURL(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String loginURL = constructLoginURL(request);
    LOGGER.sendRedirectToLoginURL(loginURL);
    response.sendRedirect(loginURL);
  }

  @Override
  protected void handleValidationError(HttpServletRequest request, HttpServletResponse response,
                                       int status, String error) throws IOException {
    /* We don't need redirect if this is a XHR request */
    if (request.getHeader(XHR_HEADER) != null &&
            request.getHeader(XHR_HEADER).equalsIgnoreCase(XHR_VALUE)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MimeTypes.Type.TEXT_PLAIN.toString());
      if(error != null && !error.isEmpty()) {
        final byte[] data = error.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(data.length);
        response.getOutputStream().write(data);
      }
    } else {
      String loginURL = constructLoginURL(request);
      response.sendRedirect(loginURL);
    }
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
    if (authenticationProviderUrl == null) {
      authenticationProviderUrl = deriveDefaultAuthenticationProviderUrl(request);
    }
    if (authenticationProviderUrl.contains("?")) {
      delimiter = "&";
    }
    return authenticationProviderUrl + delimiter
        + ORIGINAL_URL_QUERY_PARAM
        + request.getRequestURL().append(getOriginalQueryString(request));
  }

  /**
   * Derive a provider URL from the request assuming that the
   * KnoxSSO endpoint is local to the endpoint serving this request.
   * @param request origin request
   * @return url that is based on KnoxSSO endpoint
   */
  public String deriveDefaultAuthenticationProviderUrl(HttpServletRequest request) {
    String scheme;
    String host;
    int port;
    if (!beingProxied(request)) {
      scheme = request.getScheme();
      host = request.getServerName();
      port = request.getServerPort();
    }
    else {
      scheme = request.getHeader(X_FORWARDED_PROTO);
      host = request.getHeader(X_FORWARDED_HOST);
      port = Integer.parseInt(request.getHeader(X_FORWARDED_PORT));
    }
    StringBuilder sb = new StringBuilder(scheme);
    sb.append("://").append(host);
    if (!host.contains(":")) {
      sb.append(':').append(port);
    }
    sb.append('/').append(gatewayPath).append("/knoxsso/api/v1/websso");

    return sb.toString();
  }

  private boolean beingProxied(HttpServletRequest request) {
    return (request.getHeader(X_FORWARDED_HOST) != null);
  }

  private String getOriginalQueryString(HttpServletRequest request) {
    String originalQueryString = request.getQueryString();
    return (originalQueryString == null) ? "" : "?" + originalQueryString;
  }
}

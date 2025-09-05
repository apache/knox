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

import org.apache.http.HttpHeaders;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.session.SessionInvalidators;
import org.apache.knox.gateway.util.AuthFilterUtils;
import org.apache.knox.gateway.util.CertificateUtils;
import org.apache.knox.gateway.util.CookieUtils;
import org.apache.knox.gateway.util.Urls;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SSOCookieFederationFilter extends AbstractJWTFilter {
  private static final JWTMessages LOGGER = MessagesFactory.get( JWTMessages.class );

  public static final String XHR_HEADER = "X-Requested-With";
  public static final String XHR_VALUE = "XMLHttpRequest";

  private static final String GATEWAY_PATH = "gateway.path";
  public static final String SSO_COOKIE_NAME = "sso.cookie.name";
  public static final String SSO_EXPECTED_AUDIENCES = "sso.expected.audiences";
  public static final String SSO_AUTHENTICATION_PROVIDER_URL = "sso.authentication.provider.url";
  public static final String SSO_VERIFICATION_PEM = "sso.token.verification.pem";
  public static final String SSO_IDLE_TIMEOUT_SECONDS = "sso.idle.timeout.seconds";
  public static final String X_FORWARDED_HOST = "X-Forwarded-Host";
  public static final String X_FORWARDED_PORT = "X-Forwarded-Port";
  public static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";

  /* Overwrite original from header */
  /* Feature flag to turn the original url from header for SSO ON */
  public static final String SHOULD_USE_ORIGINAL_URL_FROM_HEADER = "sso.use.original.url.from.header";
  public static final String X_ORIGINAL_URL = "X-Original-URL";
  /* Users can choose to use custom header names */
  public static final String X_ORIGINAL_URL_HEADER_NAME = "sso.original.url.from.header.name";
  private static final boolean DEFAULT_SHOULD_USE_ORIGINAL_URL_FROM_HEADER = false;
  /* Should we check for domain in configured whitelist? */
  public static final String VERIFY_ORIGINAL_URL_FROM_HEADER_DOMAIN = "sso.original.url.from.header.verify.domain";
  /*
  * This is ONLY needed when you want tighter access,
  * we already have `knoxsso.redirect.whitelist.regex` property
  * that checks for redirect URL. If you add domains to whitelist here
  * make sure they are added there as well.
  */
  private static final boolean DEFAULT_VERIFY_ORIGINAL_URL_FROM_HEADER_DOMAIN = false;
  /* Param that specifies the whitelist for original url header domains, domains are comma seperated list */
  public static final String VERIFY_ORIGINAL_URL_FROM_HEADER_DOMAIN_WHITELIST = "sso.original.url.from.header.domain.whitelist";

  private static final String ORIGINAL_URL_QUERY_PARAM = "originalUrl=";
  public static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";

  /* A semicolon separated list of paths that need to bypass authentication */
  private static final String SSO_UNAUTHENTICATED_PATHS_PARAM = "sso.unauthenticated.path.list";
  private static final String DEFAULT_SSO_UNAUTHENTICATED_PATHS_PARAM = "/favicon.ico;/knoxtoken/api/v1/jwks.json";
  private String cookieName;
  private String authenticationProviderUrl;
  private String gatewayPath;
  private final Set<String> unAuthenticatedPaths = new HashSet<>(20);

  private boolean shouldUseOriginalUrlFromHeader = DEFAULT_SHOULD_USE_ORIGINAL_URL_FROM_HEADER;
  private boolean verifyOriginalUrlFromHeaderDomain = DEFAULT_VERIFY_ORIGINAL_URL_FROM_HEADER_DOMAIN;
  private final List<String> verifyOriginalUrlFromHeaderDomainWhitelist = new ArrayList<>();
  private String originalUrlHeaderName;

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

    final String unAuthPathString = filterConfig
        .getInitParameter(SSO_UNAUTHENTICATED_PATHS_PARAM);
    /* prepare a list of allowed unauthenticated paths */
    AuthFilterUtils.addUnauthPaths(unAuthenticatedPaths, unAuthPathString, DEFAULT_SSO_UNAUTHENTICATED_PATHS_PARAM);

    // gateway path for deriving an idp url when missing
    setGatewayPath(filterConfig);

    final String ssoIdleTimeoutSeconds = filterConfig.getInitParameter(SSO_IDLE_TIMEOUT_SECONDS);
    if (ssoIdleTimeoutSeconds != null) {
      idleTimeoutSeconds = Long.parseLong(ssoIdleTimeoutSeconds);
      LOGGER.configuredIdleTimeout(idleTimeoutSeconds, topologyName);
    }

    /* Support to overwrite originalUrl by providing an option to pick it up from the request header value */
    final String shouldUseOriginalUrlFromHeaderFilterParam = filterConfig.getInitParameter(SHOULD_USE_ORIGINAL_URL_FROM_HEADER);
    if (shouldUseOriginalUrlFromHeaderFilterParam != null) {
      shouldUseOriginalUrlFromHeader = Boolean.parseBoolean(shouldUseOriginalUrlFromHeaderFilterParam);
    } else {
      shouldUseOriginalUrlFromHeader = DEFAULT_SHOULD_USE_ORIGINAL_URL_FROM_HEADER;
    }

    /*
    * If the feature to use update orignalurl for SSO to use headers is on populate
    * required fields, else don't bother
    */
    if(shouldUseOriginalUrlFromHeader) {
      originalUrlHeaderName = filterConfig.getInitParameter(X_ORIGINAL_URL_HEADER_NAME);
      if (originalUrlHeaderName == null) {
        originalUrlHeaderName = X_ORIGINAL_URL;
      }

      final String verifyOriginalUrlFromHeaderDomainFilterParam = filterConfig.getInitParameter(VERIFY_ORIGINAL_URL_FROM_HEADER_DOMAIN);
      if (verifyOriginalUrlFromHeaderDomainFilterParam != null) {
        verifyOriginalUrlFromHeaderDomain = Boolean.parseBoolean(verifyOriginalUrlFromHeaderDomainFilterParam);
      } else {
        verifyOriginalUrlFromHeaderDomain = DEFAULT_VERIFY_ORIGINAL_URL_FROM_HEADER_DOMAIN;
      }

      /* populate the whitelisted domains */
      final String verifyOriginalUrlDomainWhitelistParam = filterConfig.getInitParameter(VERIFY_ORIGINAL_URL_FROM_HEADER_DOMAIN_WHITELIST);
      if (verifyOriginalUrlFromHeaderDomain && verifyOriginalUrlDomainWhitelistParam != null) {
        final String[] domains = verifyOriginalUrlDomainWhitelistParam.split(",");
        for (final String domain : domains) {
          final String trimmedDomain = domain.trim();
          if (!trimmedDomain.isEmpty()) {
            verifyOriginalUrlFromHeaderDomainWhitelist.add(trimmedDomain);
          }
        }
      }
    }



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

    /* check for unauthenticated paths to bypass */
    if(AuthFilterUtils.doesRequestContainUnauthPath(unAuthenticatedPaths, request)) {
      /* This path is configured as an unauthenticated path let the request through */
      final Subject sub = new Subject();
      sub.getPrincipals().add(new PrimaryPrincipal("anonymous"));
      LOGGER.unauthenticatedPathBypass(req.getRequestURI(), unAuthenticatedPaths.toString());
      continueWithEstablishedSecurityContext(sub, req, res, chain);
      return;
    }

    List<Cookie> ssoCookies = CookieUtils.getCookiesForName(req, cookieName);
    if (ssoCookies.isEmpty()) {
      if ("OPTIONS".equals(req.getMethod())) {
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
            request.setAttribute(TokenUtils.ATTR_CURRENT_KNOXSSO_COOKIE_TOKEN_ID, token.getClaim(JWTToken.KNOX_ID_CLAIM));
            continueWithEstablishedSecurityContext(subject, req, res, chain);

            // we found a valid cookie we don't need to keep checking anymore
            return;
          }
        } catch (ParseException | UnknownTokenException ignore) {
          // Ignore the error since cookie was invalid
          // Fall through to keep checking if there are more cookies
        }
      }

      // There were no valid cookies found so redirect to login url
      if(res != null && !res.isCommitted()) {
        // only if the Location header is not set already by a session invalidator
        if (res.getHeader(HttpHeaders.LOCATION) == null) {
          sendRedirectToLoginURL(req, res);
        }
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
  protected void handleValidationError(HttpServletRequest request, HttpServletResponse response, int status, String error) throws IOException {
    if (isInvalidSsoCookie(error)) {
      LOGGER.invalidSsoCookie();
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      removeAuthenticationToken(request, response);
      SessionInvalidators.KNOX_SSO_INVALIDATOR.getSessionInvalidators().forEach(sessionInvalidator -> {
        sessionInvalidator.onAuthenticationError(request, response);
      });
      if (AuthFilterUtils.shouldDoGlobalLogout(request)) {
        final String redirectTo = constructGlobalLogoutUrl(request);
        LOGGER.sendRedirectToLogoutURL(redirectTo);
        response.setHeader(HttpHeaders.LOCATION, redirectTo);
        response.sendRedirect(redirectTo);
        return;
      }
    }

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

  private boolean isInvalidSsoCookie(String error) {
    return error != null && error.startsWith(TOKEN_PREFIX) && (error.endsWith(DISABLED_POSTFIX) || error.endsWith(IDLE_TIMEOUT_POSTFIX));
  }

  private String constructGlobalLogoutUrl(HttpServletRequest request) {
    final StringBuilder logoutUrlBuilder = new StringBuilder(deriveDefaultAuthenticationProviderUrl(request, true));
    logoutUrlBuilder.append('&').append(ORIGINAL_URL_QUERY_PARAM).append(deriveDefaultAuthenticationProviderUrl(request, false)); //orignalUrl=WebSSO login
    return logoutUrlBuilder.toString();
  }

  /**
   * Create the URL to be used for authentication of the user in the absence of
   * a JWT token within the incoming request.
   *
   * @param request for getting the original request URL
   * @return url to use as login url for redirect
   */
  protected String constructLoginURL(HttpServletRequest request) {
    String providerURL = null;
    String delimiter = "?";
    if (authenticationProviderUrl == null) {
      providerURL = deriveDefaultAuthenticationProviderUrl(request);
    }
    else {
      providerURL = authenticationProviderUrl;
    }
    if (providerURL.contains("?")) {
      delimiter = "&";
    }

    if(shouldUseOriginalUrlFromHeader && (request.getHeader(originalUrlHeaderName) != null) && !request.getHeader(originalUrlHeaderName).trim().isEmpty()) {
      final String originalUrlFromHeader = request.getHeader(originalUrlHeaderName);
      LOGGER.usingOriginalUrlFromHeader(originalUrlFromHeader);
      /* verify if the original request domain and the domain in the header matches */
      if(verifyOriginalUrlFromHeaderDomain) {
        try {
          final URL originalUrl = new URL(originalUrlFromHeader);
          final String originalDomain = originalUrl.getHost();
          if (!verifyOriginalUrlFromHeaderDomainWhitelist.contains(originalDomain)) {
            LOGGER.invalidOriginalUrlDomain(originalDomain);
            throw new IllegalArgumentException("Original URL domain '" + originalDomain +
                                             "' is not in the allowed whitelist");
          }
        } catch (final MalformedURLException e) {
          LOGGER.malformedOriginalUrlDomain(originalUrlFromHeader);
          throw new IllegalArgumentException("Invalid original URL format: " + originalUrlFromHeader, e);
        }
      }

      LOGGER.originalHeaderURLForwarding(originalUrlFromHeader, originalUrlHeaderName);
      return providerURL + delimiter
              + ORIGINAL_URL_QUERY_PARAM
              + originalUrlFromHeader;
    } else {
      return providerURL + delimiter
              + ORIGINAL_URL_QUERY_PARAM
              + request.getRequestURL().append(getOriginalQueryString(request));
    }

  }

  public String deriveDefaultAuthenticationProviderUrl(HttpServletRequest request) {
    return deriveDefaultAuthenticationProviderUrl(request, false);
  }

  /**
   * Derive a provider URL from the request assuming that the
   * KnoxSSO endpoint is local to the endpoint serving this request.
   * @param request origin request
   * @return url that is based on KnoxSSO endpoint
   */
  public String deriveDefaultAuthenticationProviderUrl(HttpServletRequest request, boolean logout) {
    String providerURL = null;
    String scheme;
    String host;
    int port;
    try {
      URL url = new URL(request.getRequestURL().toString());
      scheme = url.getProtocol();
      host = url.getHost();
      port = url.getPort();

      StringBuilder sb = new StringBuilder(scheme);
      sb.append("://").append(host);
      if (!host.contains(":") && port != -1) {
        sb.append(':').append(port);
      }
      sb.append('/').append(gatewayPath).append(logout ? "/knoxsso/knoxauth/logout.jsp?autoGlobalLogout=1" : "/knoxsso/api/v1/websso");
      providerURL = sb.toString();
    } catch (MalformedURLException e) {
      LOGGER.failedToDeriveAuthenticationProviderUrl(e);
    }

    return providerURL;
  }

  private String getOriginalQueryString(HttpServletRequest request) {
    String originalQueryString = request.getQueryString();
    return (originalQueryString == null) ? "" : "?" + originalQueryString;
  }

  private void removeAuthenticationToken(HttpServletRequest request, HttpServletResponse response) {
    final Cookie c = new Cookie(cookieName, null);
    c.setMaxAge(0);
    c.setPath("/");
    try {
      String domainName = Urls.getDomainName(request.getRequestURL().toString(), null);
      if(domainName != null) {
        c.setDomain(domainName);
      }
    } catch (MalformedURLException e) {
      //log.problemWithCookieDomainUsingDefault();
      // we are probably not going to be able to
      // remove the cookie due to this error but it
      // isn't necessarily not going to work.
    }
    response.addCookie(c);
  }
}

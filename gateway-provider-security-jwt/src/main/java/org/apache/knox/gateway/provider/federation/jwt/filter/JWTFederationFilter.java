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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.knox.gateway.util.AuthFilterUtils.DEFAULT_AUTH_UNAUTHENTICATED_PATHS_PARAM;

import java.io.IOException;
import java.text.ParseException;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.AuthFilterUtils;
import org.apache.knox.gateway.util.CertificateUtils;
import org.apache.knox.gateway.util.CookieUtils;

import com.nimbusds.jose.JOSEObjectType;

public class JWTFederationFilter extends AbstractJWTFilter {

  private static final JWTMessages LOGGER = MessagesFactory.get( JWTMessages.class );
  /* A semicolon separated list of paths that need to bypass authentication */
  public static final String JWT_UNAUTHENTICATED_PATHS_PARAM = "jwt.unauthenticated.path.list";

  public enum TokenType {
    JWT, Passcode;
  }

  public static final String KNOX_TOKEN_AUDIENCES = "knox.token.audiences";
  public static final String TOKEN_VERIFICATION_PEM = "knox.token.verification.pem";
  public static final String KNOX_TOKEN_QUERY_PARAM_NAME = "knox.token.query.param.name";
  public static final String TOKEN_PRINCIPAL_CLAIM = "knox.token.principal.claim";
  public static final String JWKS_URL = "knox.token.jwks.url";
  public static final String ALLOWED_JWS_TYPES = "knox.token.allowed.jws.types";
  public static final String BEARER   = "Bearer ";
  public static final String BASIC    = "Basic";
  public static final String TOKEN    = "Token";
  public static final String PASSCODE = "Passcode";

  //cookie verification support
  public static final String KNOX_TOKEN_USE_COOKIE = "knox.token.use.cookie";
  public static final String KNOX_TOKEN_COOKIE_NAME = "knox.token.cookie.name";
  private boolean useCookie; //defaults to false
  private String cookieName;

  private String paramName;
  private Set<String> unAuthenticatedPaths = new HashSet<>(20);

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    super.init(filterConfig);

    // expected audiences or null
    String expectedAudiences = filterConfig.getInitParameter(KNOX_TOKEN_AUDIENCES);
    if (expectedAudiences != null) {
      audiences = parseExpectedAudiences(expectedAudiences);
    }

    // query param name for finding the provided knoxtoken
    String queryParamName = filterConfig.getInitParameter(KNOX_TOKEN_QUERY_PARAM_NAME);
    if (queryParamName != null) {
      paramName = queryParamName;
    }

    //  JWKSUrl
    String oidcjwksurl = filterConfig.getInitParameter(JWKS_URL);
    if (oidcjwksurl != null) {
      expectedJWKSUrl = oidcjwksurl;
    }

    allowedJwsTypes = new HashSet<>();
    final String allowedTypes = filterConfig.getInitParameter(ALLOWED_JWS_TYPES);
    if (allowedTypes != null) {
      Stream.of(allowedTypes.trim().split(",")).forEach(allowedType -> allowedJwsTypes.add(new JOSEObjectType(allowedType.trim())));
    } else {
      allowedJwsTypes.add(JOSEObjectType.JWT);
    }

    //cookie auth support
    final String useCookieParam = filterConfig.getInitParameter(KNOX_TOKEN_USE_COOKIE);
    useCookie = StringUtils.isBlank(useCookieParam) ? false : Boolean.parseBoolean(useCookieParam);

    final String cookieNameParam = filterConfig.getInitParameter(KNOX_TOKEN_COOKIE_NAME);
    cookieName = StringUtils.isBlank(cookieNameParam) ? SSOCookieFederationFilter.DEFAULT_SSO_COOKIE_NAME : cookieNameParam;

    // expected claim
    String oidcPrincipalclaim = filterConfig.getInitParameter(TOKEN_PRINCIPAL_CLAIM);
    if (oidcPrincipalclaim != null) {
      expectedPrincipalClaim = oidcPrincipalclaim;
    }

    // token verification pem
    String verificationPEM = filterConfig.getInitParameter(TOKEN_VERIFICATION_PEM);
    // setup the public key of the token issuer for verification
    if (verificationPEM != null) {
      publicKey = CertificateUtils.parseRSAPublicKey(verificationPEM);
    }

    final String unAuthPathString = filterConfig
        .getInitParameter(JWT_UNAUTHENTICATED_PATHS_PARAM);
    /* prepare a list of allowed unauthenticated paths */
    AuthFilterUtils.addUnauthPaths(unAuthenticatedPaths, unAuthPathString, DEFAULT_AUTH_UNAUTHENTICATED_PATHS_PARAM);

    configureExpectedParameters(filterConfig);
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    /* check for unauthenticated paths to bypass */
    if(AuthFilterUtils
        .doesRequestContainUnauthPath(unAuthenticatedPaths, request)) {
      continueWithAnonymousSubject(request, response, chain);
      return;
    }

    if (useCookie) {
      try {
        if (authenticateWithCookies((HttpServletRequest) request, (HttpServletResponse) response, chain)) {
          // if there was a valid cookie authentication was handled, there is no point in
          // going forward to check the JWT path in the header
          return;
        }
      } catch (NoValidCookiesException e) {
        log.missingValidCookie();
        handleValidationError((HttpServletRequest) request, (HttpServletResponse) response, HttpServletResponse.SC_UNAUTHORIZED,
            "There is no valid cookie found");
        return;
      }
    }

    final Pair<TokenType, String> wireToken = getWireToken(request);

    if (wireToken != null && wireToken.getLeft() != null && wireToken.getRight() != null) {
      TokenType tokenType  = wireToken.getLeft();
      String    tokenValue = wireToken.getRight();

      if (TokenType.JWT.equals(tokenType)) {
        try {
          JWT token = new JWTToken(tokenValue);
          if (validateToken((HttpServletRequest) request, (HttpServletResponse) response, chain, token)) {
            Subject subject = createSubjectFromToken(token);
            continueWithEstablishedSecurityContext(subject, (HttpServletRequest) request, (HttpServletResponse) response, chain);
          }
        } catch (ParseException | UnknownTokenException ex) {
          ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
      } else if (TokenType.Passcode.equals(tokenType)) {
        // Validate the token based on the server-managed metadata
        // The received token value must be a Base64 encoded value of Base64(tokenId)::Base64(rawPasscode)
        String tokenId = null;
        String passcode = null;
        try {
          final String[] base64DecodedTokenIdAndPasscode = decodeBase64(tokenValue).split("::");
          tokenId = decodeBase64(base64DecodedTokenIdAndPasscode[0]);
          passcode = decodeBase64(base64DecodedTokenIdAndPasscode[1]);
        } catch (Exception e) {
          log.failedToParsePasscodeToken(e);
          handleValidationError((HttpServletRequest) request, (HttpServletResponse) response, HttpServletResponse.SC_UNAUTHORIZED,
              "Error while parsing the received passcode token");
        }

        if (validateToken((HttpServletRequest) request, (HttpServletResponse) response, chain, tokenId, passcode)) {
          try {
            Subject subject = createSubjectFromTokenIdentifier(tokenId);
            continueWithEstablishedSecurityContext(subject, (HttpServletRequest) request, (HttpServletResponse) response, chain);
          } catch (UnknownTokenException e) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
          }
        }
      }
    } else {
      // no token provided in header
      log.missingTokenFromHeader(wireToken);
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }

  private String decodeBase64(String toBeDecoded) {
    return new String(Base64.getDecoder().decode(toBeDecoded.getBytes(UTF_8)), UTF_8);
  }

  public Pair<TokenType, String> getWireToken(final ServletRequest request) {
      Pair<TokenType, String> parsed = null;
      String token = null;
      final String header = ((HttpServletRequest)request).getHeader("Authorization");
      if (header != null) {
          if (header.startsWith(BEARER)) {
              // what follows the bearer designator should be the JWT token being used
              // to request or as an access token
              token = header.substring(BEARER.length());
              parsed = Pair.of(TokenType.JWT, token);
          } else if (header.toLowerCase(Locale.ROOT).startsWith(BASIC.toLowerCase(Locale.ROOT))) {
              // what follows the Basic designator should be the JWT token or the unique token ID being used
              // to request or as an access token
              parsed = parseFromHTTPBasicCredentials(header);
          }
      }

      if (parsed == null) {
          token = request.getParameter(this.paramName);
          if (token != null) {
            parsed = Pair.of(TokenType.JWT, token);
          }
      }

      return parsed;
  }

    private Pair<TokenType, String> parseFromHTTPBasicCredentials(final String header) {
      Pair<TokenType, String> parsed = null;
      final String base64Credentials = header.substring(BASIC.length()).trim();
      final byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
      final String credentials = new String(credDecoded, UTF_8);
      final String[] values = credentials.split(":", 2);
      String username = values[0];
      String passcode = values[1].isEmpty() ? null : values[1];
      if (TOKEN.equalsIgnoreCase(username) || PASSCODE.equalsIgnoreCase(username)) {
          parsed = Pair.of(TOKEN.equalsIgnoreCase(username) ? TokenType.JWT : TokenType.Passcode, passcode);
      }

      return parsed;
  }

  /*
   * Attempts to authenticate using session cookies.
   */
  private boolean authenticateWithCookies(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws NoValidCookiesException, ServletException, IOException {
    final List<Cookie> relevantCookies = CookieUtils.getCookiesForName(request, cookieName);
    for (Cookie ssoCookie : relevantCookies) {
      try {
        final JWT token = new JWTToken(ssoCookie.getValue());
        if (validateToken(request, response, chain, token)) {
          final Subject subject = createSubjectFromToken(token);
          continueWithEstablishedSecurityContext(subject, request, response, chain);
          // we found a valid cookie we don't need to keep checking anymore
          return true;
        }
      } catch (ParseException | UnknownTokenException ignore) {
        // Ignore the error since cookie was invalid
        // Fall through to keep checking if there are more cookies
      }
    }

    if (!relevantCookies.isEmpty()) {
      // No valid cookies found but cookie was present so reject this request and do
      // no further processing
      throw new NoValidCookiesException();
    }

    return false;
  }

  @Override
  protected void handleValidationError(HttpServletRequest request, HttpServletResponse response, int status,
                                       String error) throws IOException {
    if (error != null) {
      response.sendError(status, error);
    }
    else {
      response.sendError(status);
    }
  }

  /**
   * A function that let's configured unauthenticated path requests to
   * pass through without requiring authentication.
   * An anonymous subject is created and the request is audited.
   *
   * Fail gracefully by logging error message.
   * @param request
   * @param response
   * @param chain
   */
  private void continueWithAnonymousSubject(final ServletRequest request,
      final ServletResponse response, final FilterChain chain)
      throws ServletException, IOException {
    try {
      /* This path is configured as an unauthenticated path let the request through */
      final Subject sub = new Subject();
      sub.getPrincipals().add(new PrimaryPrincipal("anonymous"));
      LOGGER.unauthenticatedPathBypass(((HttpServletRequest) request).getRequestURI(), unAuthenticatedPaths.toString());
      continueWithEstablishedSecurityContext(sub, (HttpServletRequest) request,
          (HttpServletResponse) response, chain);

    } catch (final Exception e) {
      LOGGER.unauthenticatedPathError(
          ((HttpServletRequest) request).getRequestURI(), e.toString());
      throw e;
    }
  }

  /**
   * An exception indicating that cookies are present, but none of them contain a
   * valid JWT.
   */
  @SuppressWarnings("serial")
  private class NoValidCookiesException extends Exception {
    NoValidCookiesException() {
      super("None of the presented cookies are valid.");
    }
  }

}

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

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;

import static org.apache.knox.gateway.util.AuthFilterUtils.DEFAULT_AUTH_UNAUTHENTICATED_PATHS_PARAM;

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
  public static final String BEARER   = "Bearer ";
  public static final String BASIC    = "Basic";
  public static final String TOKEN    = "Token";
  public static final String PASSCODE = "Passcode";
  private String paramName;
  private Set<String> unAuthenticatedPaths = new HashSet(20);

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

    /* add default unauthenticated paths list */
    AuthFilterUtils.parseAndAddUnauthPathList(unAuthenticatedPaths, DEFAULT_AUTH_UNAUTHENTICATED_PATHS_PARAM);

    /* add provided unauthenticated paths list if specified */
    final String unAuthPathString = filterConfig
        .getInitParameter(JWT_UNAUTHENTICATED_PATHS_PARAM);
    /* if list specified add it */
    if (!StringUtils.isBlank(unAuthPathString)) {
      AuthFilterUtils.parseAndAddUnauthPathList(unAuthenticatedPaths, unAuthPathString);
    }

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
    final Pair<TokenType, String> wireToken = getWireToken(request);

    if (wireToken != null) {
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
        if (validateToken((HttpServletRequest) request, (HttpServletResponse) response, chain, tokenValue)) {
          try {
            Subject subject = createSubjectFromTokenIdentifier(tokenValue);
            continueWithEstablishedSecurityContext(subject, (HttpServletRequest) request, (HttpServletResponse) response, chain);
          } catch (UnknownTokenException e) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
          }
        }
      }
    } else {
      // no token provided in header
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
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
      final String credentials = new String(credDecoded, StandardCharsets.UTF_8);
      final String[] values = credentials.split(":", 2);
      String username = values[0];
      String passcode = values[1].isEmpty() ? null : values[1];
      if (TOKEN.equalsIgnoreCase(username) || PASSCODE.equalsIgnoreCase(username)) {
          parsed = Pair.of(TOKEN.equalsIgnoreCase(username) ? TokenType.JWT : TokenType.Passcode, passcode);
      }

      return parsed;
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

}

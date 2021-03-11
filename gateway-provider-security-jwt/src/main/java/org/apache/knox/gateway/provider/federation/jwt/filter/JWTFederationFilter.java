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

import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.CertificateUtils;
import org.apache.knox.gateway.services.security.token.impl.JWT;

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
import java.util.Locale;

public class JWTFederationFilter extends AbstractJWTFilter {

  public static final String KNOX_TOKEN_AUDIENCES = "knox.token.audiences";
  public static final String TOKEN_VERIFICATION_PEM = "knox.token.verification.pem";
  public static final String KNOX_TOKEN_QUERY_PARAM_NAME = "knox.token.query.param.name";
  public static final String TOKEN_PRINCIPAL_CLAIM = "knox.token.principal.claim";
  public static final String JWKS_URL = "knox.token.jwks.url";
  private static final String BEARER = "Bearer ";
  private static final String BASIC = "Basic";
  private static final String TOKEN = "Token";
  private String paramName;

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

    configureExpectedParameters(filterConfig);
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    final String wireToken = getWireToken(request);

    if (wireToken != null) {
      try {
        JWT token = new JWTToken(wireToken);
        if (validateToken((HttpServletRequest)request, (HttpServletResponse)response, chain, token)) {
          Subject subject = createSubjectFromToken(token);
          continueWithEstablishedSecurityContext(subject, (HttpServletRequest)request, (HttpServletResponse)response, chain);
        }
      } catch (ParseException ex) {
        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
      }
    }
    else {
      // no token provided in header
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }

  public String getWireToken(final ServletRequest request) {
      String token = null;
      final String header = ((HttpServletRequest)request).getHeader("Authorization");
      if (header != null) {
          if (header.startsWith(BEARER)) {
              // what follows the bearer designator should be the JWT token being used
            // to request or as an access token
              token = header.substring(BEARER.length());
          }
          else if (header.toLowerCase(Locale.ROOT).startsWith(BASIC.toLowerCase(Locale.ROOT))) {
              // what follows the Basic designator should be the JWT token being used
            // to request or as an access token
              token = this.parseFromHTTPBasicCredentials(token, header);
          }
      }
      if (token == null) {
          token = request.getParameter(this.paramName);
      }
      return token;
  }

  private String parseFromHTTPBasicCredentials(String token, final String header) {
      final String base64Credentials = header.substring(BASIC.length()).trim();
      final byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
      final String credentials = new String(credDecoded, StandardCharsets.UTF_8);
      final String[] values = credentials.split(":", 2);
      if (values[0].equalsIgnoreCase(TOKEN)) {
          token = values[1];
      }
      return token;
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

}

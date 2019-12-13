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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.CertificateUtils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;

public class JWTFederationFilter extends AbstractJWTFilter {

  public static final String KNOX_TOKEN_AUDIENCES = "knox.token.audiences";
  public static final String TOKEN_VERIFICATION_PEM = "knox.token.verification.pem";
  private static final String KNOX_TOKEN_QUERY_PARAM_NAME = "knox.token.query.param.name";
  public static final String TOKEN_PRINCIPAL_CLAIM = "jwt.expected.principal.claim";
  public static final String JWKS_URL = "jwt.expected.jwks.url";
  private static final String BEARER = "Bearer ";
  private String paramName = "knoxtoken";

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
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

    // token verification pem
    String verificationPEM = filterConfig.getInitParameter(TOKEN_VERIFICATION_PEM);
    // setup the public key of the token issuer for verification
    if (verificationPEM != null) {
      publicKey = CertificateUtils.parseRSAPublicKey(verificationPEM);
    }
    // JWKSUrl
    String oidcjwksurl = filterConfig.getInitParameter(JWKS_URL);
    if (oidcjwksurl != null) {
      expectedJWKSUrl = oidcjwksurl;
    }
    // expected claim
    String oidcPartyPclaim = filterConfig.getInitParameter(TOKEN_PRINCIPAL_CLAIM);
    if (oidcPartyPclaim != null) {
      expectedPrincipalClaim = oidcPartyPclaim;
    }

    configureExpectedParameters(filterConfig);
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String header = ((HttpServletRequest) request).getHeader("Authorization");
    String header_hive = ((HttpServletRequest) request).getHeader("HiveAuthToken");
    String wireToken;
    if (header != null && header.startsWith(BEARER)) {
      // what follows the bearer designator should be the JWT token being used to
      // request or as an access token
      wireToken = header.substring(BEARER.length());
    } else if (header_hive != null) {
      // what follows the bearer designator should be the JWT token being used to
      // request or as an access token in hive beeeline
      wireToken = header_hive;
    } else {
      // check for query param
      wireToken = request.getParameter(paramName);
    }

    if (wireToken != null && !wireToken.isEmpty()) {
      // validate JWT token with JWT Issuer
      validateJWTtoken(wireToken, request, response, chain);
    } else {
      // no token provided in header
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

  }

  /**
   * @param wireToken
   * @param request
   * @param response
   * @param chain
   * @throws IOException
   */
  private void validateJWTtoken(String wireToken, ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException {
    try {
      Subject subject = null;
      JWT token = new JWTToken(wireToken);
      boolean validate = false;
      if (expectedJWKSUrl != null) {
        JWSAlgorithm expectedJWSAlg = JWSAlgorithm.parse(expectedSigAlg);
        JWKSource<SecurityContext> keySource = new RemoteJWKSet(new URL(expectedJWKSUrl));
        JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector(expectedJWSAlg, keySource);
        // Create a JWT processor for the access tokens
        ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor();

        jwtProcessor.setJWSKeySelector(keySelector);
        JWTClaimsSetVerifier<SecurityContext> claimsVerifier = new DefaultJWTClaimsVerifier<SecurityContext>();
        jwtProcessor.setJWTClaimsSetVerifier(claimsVerifier);

        // Process the token
        SecurityContext ctx = null; // optional context parameter, not required here
        jwtProcessor.process(wireToken, ctx);
        validate = true;
      }

      if (publicKey != null && !validate) {

        boolean validateToken = validateToken((HttpServletRequest) request, (HttpServletResponse) response, chain,
            token);
        if (!validateToken) {
          throw new JOSEException(" Token is not Valid");
        }
      }

      subject = createSubjectFromToken(token);

      continueWithEstablishedSecurityContext(subject, (HttpServletRequest) request, (HttpServletResponse) response,
          chain);

    } catch (ParseException | BadJOSEException | JOSEException | MalformedURLException | ServletException ex) {
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }

  @Override
  protected void handleValidationError(HttpServletRequest request, HttpServletResponse response, int status,
      String error) throws IOException {
    if (error != null) {
      response.sendError(status, error);
    } else {
      response.sendError(status);
    }
  }
}
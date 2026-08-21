/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import org.apache.knox.gateway.security.ActorChainPrincipalImpl;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.TokenExchangePrincipal;
import org.apache.knox.gateway.security.TokenExchangePrincipalImpl;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.util.ServletRequestUtils;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.Principal;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter.ACTOR_TOKEN_TYPE;
import static org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter.SUBJECT_TOKEN_TYPE;
import static org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter.TOKEN_TYPE_ACCESS_TOKEN;
import static org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter.TOKEN_TYPE_JWT;
/**
 * Handles RFC 8693 (OAuth 2.0 Token Exchange) requests on behalf of {@link JWTFederationFilter}.
 *
 * <p>The exchange parameters are sent in the {@code application/x-www-form-urlencoded} body and are
 * therefore read from the <em>unwrapped</em> request (the filter chain wraps the request in a form
 * that hides the body from {@code getParameter()}). The owning filter is used for JWT validation
 * and for establishing the resulting security context.</p>
 *
 * <p>Per RFC 8693 section 2.1: {@code subject_token} and {@code subject_token_type} are required;
 * {@code actor_token} is optional, and {@code actor_token_type} is required when {@code actor_token}
 * is present and must not be present otherwise. Only JWT-family token types are supported. When an
 * {@code actor_token} is present the request is treated as delegation (on-behalf-of): the actor is
 * the authenticated party and the subject is the impersonated party; otherwise the subject_token is
 * simply exchanged for a token representing the subject.</p>
 */
class TokenExchangeHandler {

  private final JWTFederationFilter filter;

  TokenExchangeHandler(JWTFederationFilter filter) {
    this.filter = filter;
  }

  /**
   * Handle a token-exchange request that has already been identified by its grant type.
   *
   * @param request  the HTTP request (wrapped; passed through to downstream processing)
   * @param response the HTTP response
   * @param chain    the filter chain
   * @throws IOException      if an I/O error occurs
   * @throws ServletException if a servlet error occurs
   */
  void handle(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    // The parameters live in the x-www-form-urlencoded body, which is only readable on the
    // unwrapped request. The wrapped request is still used below so downstream processing is
    // unchanged.
    final HttpServletRequest bodyRequest = ServletRequestUtils.unwrapHttpServletRequest(request);

    final String subjectTokenValue = bodyRequest.getParameter(JWTFederationFilter.SUBJECT_TOKEN);
    final String subjectTokenType = bodyRequest.getParameter(SUBJECT_TOKEN_TYPE);
    final String actorTokenValue = bodyRequest.getParameter(JWTFederationFilter.ACTOR_TOKEN);
    final String actorTokenType = bodyRequest.getParameter(ACTOR_TOKEN_TYPE);
    final boolean hasActorToken = actorTokenValue != null && !actorTokenValue.isEmpty();
    final boolean hasActorTokenType = actorTokenType != null && !actorTokenType.isEmpty();

    // RFC 8693 section 2.1: subject_token and subject_token_type are REQUIRED.
    if (subjectTokenValue == null || subjectTokenValue.isEmpty()) {
      filter.handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST,
          "invalid_request", "the subject_token parameter is required");
      return;
    }
    if (subjectTokenType == null || subjectTokenType.isEmpty()) {
      filter.handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST,
          "invalid_request", "the subject_token_type parameter is required");
      return;
    }
    // RFC 8693 section 2.1: actor_token_type is REQUIRED when actor_token is present and MUST NOT
    // be present otherwise.
    if (hasActorToken && !hasActorTokenType) {
      filter.handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST,
          "invalid_request", "actor_token_type is required when actor_token is present");
      return;
    }
    if (!hasActorToken && hasActorTokenType) {
      filter.handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST,
          "invalid_request", "actor_token_type must not be present without actor_token");
      return;
    }
    // Only JWT-family token types are supported.
    if (isNotSupportedTokenType(subjectTokenType)) {
      filter.handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST,
          "unsupported_token_type", "unsupported subject_token_type " + subjectTokenType);
      return;
    }
    if (hasActorToken && isNotSupportedTokenType(actorTokenType)) {
      filter.handleValidationError(request, response, HttpServletResponse.SC_BAD_REQUEST,
          "unsupported_token_type", "unsupported actor_token_type " + actorTokenType);
      return;
    }

    try {
      final JWT subjectToken = filter.parseAndValidateJWT(request, response, chain, subjectTokenValue);
      if (subjectToken == null) {
        // Validation failed, error response already sent
        return;
      }

      final Subject subject;
      if (hasActorToken) {
        final JWT actorToken = filter.parseAndValidateJWT(request, response, chain, actorTokenValue);
        if (actorToken == null) {
          // Validation failed, error response already sent
          return;
        }
        // Delegation (OBO): actor as PrimaryPrincipal, subject as the impersonated party
        subject = createSubjectForTokenExchange(subjectToken, actorToken);
      } else {
        // No actor_token: exchange the subject_token for a token representing the subject itself
        subject = filter.createSubjectFromToken(subjectToken);
      }

      filter.continueWithEstablishedSecurityContext(subject, request, response, chain);
    } catch (ParseException | UnknownTokenException e) {
      filter.handleValidationError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
          "invalid_grant", "Failed to parse token in token exchange: " + e.getMessage());
    }
  }

  /**
   * Token exchange only supports JWT-family token types. The access_token URN is accepted as an
   * alias for jwt because Knox labels its issued (JWT) access tokens with that type.
   *
   * @param tokenType the RFC 8693 token type identifier
   * @return true if the type does NOT map to a Knox JWT
   */
  private boolean isNotSupportedTokenType(String tokenType) {
    return !TOKEN_TYPE_JWT.equals(tokenType) && !TOKEN_TYPE_ACCESS_TOKEN.equals(tokenType);
  }

  /**
   * Create a Subject for a delegation (on-behalf-of) token exchange: the actor is the primary
   * (authenticated) principal, and the subject is carried for the identity assertion layer, along
   * with any pre-existing actor chain from the subject_token.
   *
   * @param subjectToken the validated subject token
   * @param actorToken   the validated actor token
   * @return a Subject configured for token exchange
   */
  private Subject createSubjectForTokenExchange(JWT subjectToken, JWT actorToken) {
    final String subjectPrincipalName = subjectToken.getSubject();
    final String subjectIssuer = subjectToken.getIssuer();
    final String actorPrincipalName = actorToken.getSubject();
    final String actorIssuer = actorToken.getIssuer();

    // PrimaryPrincipal is the ACTOR (the authenticated party)
    final PrimaryPrincipal primaryPrincipal = new PrimaryPrincipal(actorPrincipalName);

    // TokenExchangePrincipal carries metadata for the identity assertion layer
    final TokenExchangePrincipal tokenExchangePrincipal =
        new TokenExchangePrincipalImpl(subjectPrincipalName, subjectIssuer, actorPrincipalName, actorIssuer);

    // Extract actor chain from subject_token (if present) using existing logic
    final List<Map<String, Object>> actorChain = TokenUtils.extractActorChain(subjectToken);

    final Set<Principal> principals = new HashSet<>();
    principals.add(primaryPrincipal);
    principals.add(tokenExchangePrincipal);
    if (!actorChain.isEmpty()) {
      principals.add(new ActorChainPrincipalImpl(actorChain));
    }

    @SuppressWarnings("rawtypes")
    final HashSet emptySet = new HashSet();
    return new Subject(true, principals, emptySet, emptySet);
  }
}

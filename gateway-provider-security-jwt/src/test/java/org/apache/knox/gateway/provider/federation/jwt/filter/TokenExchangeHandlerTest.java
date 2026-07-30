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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.TokenExchangePrincipal;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link TokenExchangeHandler} covering the RFC 8693 request-validation and
 * subject-construction business logic. The owning {@link JWTFederationFilter}'s callbacks
 * (JWT validation and security-context establishment) are stubbed by {@link RecordingFilter}.
 */
public class TokenExchangeHandlerTest {

  private static final String JWT_TYPE = TokenExchangeHandler.TOKEN_TYPE_JWT;
  private static final String ACCESS_TOKEN_TYPE = TokenExchangeHandler.TOKEN_TYPE_ACCESS_TOKEN;
  private static final String SAML2_TYPE = "urn:ietf:params:oauth:token-type:saml2";

  private RecordingFilter filter;
  private TokenExchangeHandler handler;
  private HttpServletResponse response;
  private FilterChain chain;

  @Before
  public void setUp() {
    filter = new RecordingFilter();
    handler = new TokenExchangeHandler(filter);
    response = EasyMock.createNiceMock(HttpServletResponse.class);
    chain = EasyMock.createNiceMock(FilterChain.class);
    EasyMock.replay(response, chain);
  }

  @Test
  public void testSubjectTokenRequired() throws Exception {
    handler.handle(request(null, JWT_TYPE, null, null), response, chain);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertTrue(filter.errorMessage.contains("subject_token"));
    assertFalse(filter.continued);
  }

  @Test
  public void testSubjectTokenTypeRequired() throws Exception {
    handler.handle(request("subtok", null, null, null), response, chain);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertTrue(filter.errorMessage.contains("subject_token_type"));
    assertFalse(filter.continued);
  }

  @Test
  public void testActorTokenTypeRequiredWhenActorPresent() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, "acttok", null), response, chain);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertTrue(filter.errorMessage.contains("actor_token_type is required"));
    assertFalse(filter.continued);
  }

  @Test
  public void testActorTokenTypeForbiddenWithoutActor() throws Exception {
    handler.handle(request("subtok", JWT_TYPE, null, JWT_TYPE), response, chain);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertTrue(filter.errorMessage.contains("must not be present"));
    assertFalse(filter.continued);
  }

  @Test
  public void testUnsupportedSubjectTokenType() throws Exception {
    handler.handle(request("subtok", SAML2_TYPE, null, null), response, chain);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertTrue(filter.errorMessage.contains("unsupported_token_type"));
    assertFalse(filter.continued);
  }

  @Test
  public void testUnsupportedActorTokenType() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, "acttok", SAML2_TYPE), response, chain);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertTrue(filter.errorMessage.contains("unsupported_token_type"));
    assertFalse(filter.continued);
  }

  @Test
  public void testAccessTokenTypeIsAcceptedAsJwt() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", ACCESS_TOKEN_TYPE, null, null), response, chain);
    // access_token URN is accepted (no unsupported_token_type error) and the exchange proceeds
    assertEquals(-1, filter.errorStatus);
    assertTrue(filter.continued);
  }

  @Test
  public void testSubjectOnlyExchangeEstablishesSubjectIdentity() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, null, null), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.establishedSubject);
    // Plain subject exchange: subject is the primary identity, no delegation principal
    assertEquals("alice", primaryName(filter.establishedSubject));
    assertTrue(filter.establishedSubject.getPrincipals(TokenExchangePrincipal.class).isEmpty());
  }

  @Test
  public void testDelegationExchangeMakesActorPrimaryWithTokenExchangePrincipal() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.establishedSubject);
    // OBO: the actor is the authenticated (primary) party ...
    assertEquals("svc-dataservice", primaryName(filter.establishedSubject));
    // ... and a TokenExchangePrincipal carries the subject/actor metadata
    final TokenExchangePrincipal tep =
        filter.establishedSubject.getPrincipals(TokenExchangePrincipal.class).iterator().next();
    assertEquals("alice", tep.getSubjectPrincipalName());
    assertEquals("svc-dataservice", tep.getActorPrincipalName());
  }

  @Test
  public void testSubjectValidationFailureDoesNotEstablishContext() throws Exception {
    // "subtok" is not in the valid map -> parseAndValidateJWT returns null (error already sent)
    handler.handle(request("subtok", JWT_TYPE, null, null), response, chain);
    assertFalse(filter.continued);
  }

  @Test
  public void testActorValidationFailureDoesNotEstablishContext() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    // "acttok" is not valid
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE), response, chain);
    assertFalse(filter.continued);
  }

  private static String primaryName(Subject subject) {
    return subject.getPrincipals(PrimaryPrincipal.class).iterator().next().getName();
  }

  private HttpServletRequest request(String subjectToken, String subjectTokenType,
                                     String actorToken, String actorTokenType) {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter(TokenExchangeHandler.SUBJECT_TOKEN)).andReturn(subjectToken).anyTimes();
    EasyMock.expect(request.getParameter(TokenExchangeHandler.SUBJECT_TOKEN_TYPE)).andReturn(subjectTokenType).anyTimes();
    EasyMock.expect(request.getParameter(TokenExchangeHandler.ACTOR_TOKEN)).andReturn(actorToken).anyTimes();
    EasyMock.expect(request.getParameter(TokenExchangeHandler.ACTOR_TOKEN_TYPE)).andReturn(actorTokenType).anyTimes();
    EasyMock.replay(request);
    return request;
  }

  private static JWT jwt(String subject, String issuer) {
    final JWT jwt = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(jwt.getSubject()).andReturn(subject).anyTimes();
    EasyMock.expect(jwt.getIssuer()).andReturn(issuer).anyTimes();
    // no actor chain in the token
    EasyMock.expect(jwt.getClaimAsObject(EasyMock.anyString())).andReturn(null).anyTimes();
    EasyMock.replay(jwt);
    return jwt;
  }

  /**
   * A {@link JWTFederationFilter} whose validation and context-establishment callbacks are
   * replaced with recording stubs, so the handler's own logic can be exercised in isolation.
   */
  private static final class RecordingFilter extends JWTFederationFilter {
    private final Map<String, JWT> valid = new HashMap<>();
    private int errorStatus = -1;
    private String errorMessage;
    private boolean continued;
    private Subject establishedSubject;

    @Override
    JWT parseAndValidateJWT(HttpServletRequest request, HttpServletResponse response,
                            FilterChain chain, String tokenValue)
        throws ParseException, IOException, ServletException {
      return valid.get(tokenValue);
    }

    @Override
    protected Subject createSubjectFromToken(final JWT token) {
      final Subject subject = new Subject();
      subject.getPrincipals().add(new PrimaryPrincipal(token.getSubject()));
      return subject;
    }

    @Override
    protected void continueWithEstablishedSecurityContext(Subject subject, HttpServletRequest request,
                                                          HttpServletResponse response, FilterChain chain) {
      this.continued = true;
      this.establishedSubject = subject;
    }

    @Override
    protected void handleValidationError(HttpServletRequest request, HttpServletResponse response,
                                         int status, String error) {
      this.errorStatus = status;
      this.errorMessage = error == null ? "" : error;
    }
  }
}

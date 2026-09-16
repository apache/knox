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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.security.ActorChainPrincipal;
import org.apache.knox.gateway.security.CommonTokenConstants;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.TokenExchangePrincipal;
import org.apache.knox.gateway.services.knoxidf.delegation.DelegationGroupLookupUnavailableException;
import org.apache.knox.gateway.services.knoxidf.delegation.PolicyCheckRequest;
import org.apache.knox.gateway.services.knoxidf.delegation.PolicyDecision;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.Subject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for {@link TokenExchangeHandler} covering the RFC 8693 request-validation and
 * subject-construction business logic. The owning {@link JWTFederationFilter}'s callbacks
 * (JWT validation and security-context establishment) are stubbed by {@link RecordingFilter}.
 */
public class TokenExchangeHandlerTest {

  private static final String JWT_TYPE = JWTFederationFilter.TOKEN_TYPE_JWT;
  private static final String ACCESS_TOKEN_TYPE = JWTFederationFilter.TOKEN_TYPE_ACCESS_TOKEN;
  private static final String SAML2_TYPE = "urn:ietf:params:oauth:token-type:saml2";

  private RecordingFilter filter;
  private TokenExchangeHandler handler;
  private HttpServletResponse response;
  private FilterChain chain;
  private Capture<Object> requestedAudiencesAttr;
  private Capture<Object> requestedTtlAttr;
  private static final Auditor ORIGINAL_AUDITOR = TokenExchangeHandler.auditor;
  private Auditor auditor;

  @Before
  public void setUp() {
    filter = new RecordingFilter();
    handler = new TokenExchangeHandler(filter);
    response = EasyMock.createNiceMock(HttpServletResponse.class);
    chain = EasyMock.createNiceMock(FilterChain.class);
    EasyMock.replay(response, chain);
    // Default to a nice, already-replayed Auditor so every test that does not itself care
    // about auditing (including every pre-existing test in this file) keeps passing
    // unmodified.
    auditor = EasyMock.createNiceMock(Auditor.class);
    EasyMock.replay(auditor);
    TokenExchangeHandler.auditor = auditor;
  }

  @After
  public void tearDown() {
    TokenExchangeHandler.auditor = ORIGINAL_AUDITOR;
  }

  /**
   * Replaces TokenExchangeHandler.auditor with a strict mock expecting exactly one audit()
   * call matching the given action/resourceName/resourceType/outcome, replays it, and returns
   * a Capture of the message argument for the caller to assert further content against after
   * invoking handler.handle(...) and calling EasyMock.verify(auditor).
   */
  private Capture<String> expectAudit(String action, String resourceName, String resourceType,
                                       String outcome) {
    auditor = EasyMock.createMock(Auditor.class);
    final Capture<String> message = EasyMock.newCapture();
    auditor.audit(EasyMock.eq(action), EasyMock.eq(resourceName), EasyMock.eq(resourceType),
        EasyMock.eq(outcome), EasyMock.capture(message));
    EasyMock.expectLastCall().once();
    EasyMock.replay(auditor);
    TokenExchangeHandler.auditor = auditor;
    return message;
  }

  @Test
  public void testSubjectTokenRequired() throws Exception {
    handler.handle(request(null, JWT_TYPE, null, null), response, chain);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertTrue(filter.errorDescription.contains("subject_token"));
    assertFalse(filter.continued);
  }

  @Test
  public void testSubjectTokenTypeRequired() throws Exception {
    handler.handle(request("subtok", null, null, null), response, chain);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertTrue(filter.errorDescription.contains("subject_token_type"));
    assertFalse(filter.continued);
  }

  @Test
  public void testActorTokenTypeRequiredWhenActorPresent() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, "acttok", null), response, chain);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertTrue(filter.errorDescription.contains("actor_token_type is required"));
    assertFalse(filter.continued);
  }

  @Test
  public void testActorTokenTypeForbiddenWithoutActor() throws Exception {
    handler.handle(request("subtok", JWT_TYPE, null, JWT_TYPE), response, chain);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertTrue(filter.errorDescription.contains("must not be present"));
    assertFalse(filter.continued);
  }

  @Test
  public void testUnsupportedSubjectTokenType() throws Exception {
    handler.handle(request("subtok", SAML2_TYPE, null, null), response, chain);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertTrue(filter.errorDescription.contains("subject_token_type"));
    assertFalse(filter.continued);
  }

  @Test
  public void testUnsupportedActorTokenType() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, "acttok", SAML2_TYPE), response, chain);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertTrue(filter.errorDescription.contains("actor_token_type"));
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
    filter.delegationServerEnabled = true;
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
  public void testActorTokenDelegationPolicyDenialRejectsWithInvalidRequestAndAuditsFailure()
      throws Exception {
    filter.delegationServerEnabled = true;
    filter.policyDecision = new PolicyDecision("resource_not_allowed", 0);
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    final Capture<String> auditMessage = expectAudit(Action.TOKEN_EXCHANGE, "USER/svc-dataservice",
        ResourceType.PRINCIPAL, ActionOutcome.FAILURE);
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE), response, chain);

    assertFalse(filter.continued);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertTrue(filter.errorDescription.contains("rejected by policy"));
    EasyMock.verify(auditor);
    assertTrue(auditMessage.getValue().contains("event_type=token_exchange_denied"));
    assertTrue(auditMessage.getValue().contains("deny_reason=resource_not_allowed"));
    assertTrue(auditMessage.getValue().contains("actor_authority=USER"));
    assertTrue(auditMessage.getValue().contains("actor_id=svc-dataservice"));
    assertTrue(auditMessage.getValue().contains("subject_token_iss=KNOXSSO"));
    assertTrue(auditMessage.getValue().contains("subject_token_sub=alice"));
  }

  @Test
  public void testHeadlessDelegationPolicyDenialRejectsWithInvalidRequestAndAuditsFailure()
      throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.policyDecision = new PolicyDecision("subject_not_allowed", 0);
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    final Capture<String> auditMessage = expectAudit(Action.TOKEN_EXCHANGE, "USER/alice",
        ResourceType.PRINCIPAL, ActionOutcome.FAILURE);
    handler.handle(request("subtok", JWT_TYPE, null, null, "bob"), response, chain);

    assertFalse(filter.continued);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertTrue(filter.errorDescription.contains("rejected by policy"));
    EasyMock.verify(auditor);
    assertTrue(auditMessage.getValue().contains("event_type=token_exchange_denied"));
    assertTrue(auditMessage.getValue().contains("deny_reason=subject_not_allowed"));
    assertTrue(auditMessage.getValue().contains("actor_authority=USER"));
    assertTrue(auditMessage.getValue().contains("actor_id=alice"));
    assertTrue(auditMessage.getValue().contains("requested_subject=bob"));
  }

  @Test
  public void testCanActForGroupsDenialMapsToBadRequest() throws Exception {
    // The canActFor.groups check now runs inside policy evaluation and fails closed to a denial
    // (rather than the former "not implemented" 501): a group-based denial is an ordinary policy
    // rejection and must surface as HTTP 400, indistinguishable from a user-based denial.
    filter.delegationServerEnabled = true;
    filter.policyDecision = new PolicyDecision("subject_not_allowed", 0);
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE), response, chain);

    assertFalse(filter.continued);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
  }

  @Test
  public void testGroupLookupUnavailableMapsToServerError() throws Exception {
    // A group-based policy whose LDAP directory is disabled cannot be evaluated: the exchange must
    // fail with server_error directing the operator to enable LDAP, not a subject_not_allowed deny.
    filter.delegationServerEnabled = true;
    filter.policyEvaluationException = new DelegationGroupLookupUnavailableException("alice");
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE), response, chain);

    assertFalse(filter.continued);
    assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, filter.errorStatus);
    assertEquals("server_error", filter.error);
    assertTrue(filter.errorDescription.contains("LDAP"));
  }

  @Test
  public void testSameSubjectExchangeNeverCallsPolicyEvaluation() throws Exception {
    filter.policyEvaluationException = new UnsupportedOperationException("must not be called");
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, null, null), response, chain);

    assertTrue(filter.continued);
    assertNull(filter.capturedPolicyCheckRequest);
  }

  @Test
  public void testAuthorizedOboDelegationConveysPolicyTtlToTokenService() throws Exception {
    filter.delegationServerEnabled = true;
    filter.policyDecision = new PolicyDecision(null, 3600);
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    handler.handle(delegationRequest("subtok", "acttok", JWT_TYPE, null, null, null), response, chain);

    assertTrue(filter.continued);
    // KNOX-3459: the policy's effective TTL is conveyed to the downstream KNOXTOKEN service so it
    // becomes the minted token's authoritative expiry basis.
    assertTrue(requestedTtlAttr.hasCaptured());
    assertEquals(Integer.valueOf(3600), requestedTtlAttr.getValue());
  }

  @Test
  public void testAuthorizedHeadlessDelegationConveysPolicyTtlToTokenService() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.policyDecision = new PolicyDecision(null, 1800);
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", null, null, "bob", null, null), response, chain);

    assertTrue(filter.continued);
    assertTrue(requestedTtlAttr.hasCaptured());
    assertEquals(Integer.valueOf(1800), requestedTtlAttr.getValue());
  }

  @Test
  public void testSameSubjectExchangeDoesNotConveyPolicyTtl() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    // A plain (non-delegation) exchange is not policy-governed, so no authoritative TTL is conveyed
    // and KNOXTOKEN falls back to its topology knox.token.ttl.
    handler.handle(delegationRequest("subtok", null, null, null, null, null), response, chain);

    assertTrue(filter.continued);
    assertFalse(requestedTtlAttr.hasCaptured());
  }

  @Test
  public void testActorTokenIdentityDerivedFromK8sServiceAccountSubjectAuditsSuccess()
      throws Exception {
    filter.delegationServerEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("acttok", jwt("system:serviceaccount:ns1:sa1", "https://k8s"));
    final Capture<String> auditMessage = expectAudit(Action.TOKEN_EXCHANGE,
        "K8S_SA/https://k8s:ns1:sa1", ResourceType.PRINCIPAL, ActionOutcome.SUCCESS);
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.capturedPolicyCheckRequest);
    assertEquals("K8S_SA", filter.capturedPolicyCheckRequest.getActorAuthority());
    assertEquals("https://k8s:ns1:sa1", filter.capturedPolicyCheckRequest.getActorId());
    EasyMock.verify(auditor);
    assertTrue(auditMessage.getValue().contains("event_type=token_exchange_allowed"));
    assertTrue(auditMessage.getValue().contains("actor_authority=K8S_SA"));
    assertTrue(auditMessage.getValue().contains("actor_id=https://k8s:ns1:sa1"));
  }

  @Test
  public void testActorTokenIdentityFallsBackToUserAuthorityAndSubjectAuditsSuccess()
      throws Exception {
    filter.delegationServerEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    final Capture<String> auditMessage = expectAudit(Action.TOKEN_EXCHANGE,
        "USER/svc-dataservice", ResourceType.PRINCIPAL, ActionOutcome.SUCCESS);
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.capturedPolicyCheckRequest);
    assertEquals("USER", filter.capturedPolicyCheckRequest.getActorAuthority());
    assertEquals("svc-dataservice", filter.capturedPolicyCheckRequest.getActorId());
    EasyMock.verify(auditor);
    assertTrue(auditMessage.getValue().contains("event_type=token_exchange_allowed"));
    assertTrue(auditMessage.getValue().contains("actor_authority=USER"));
    assertTrue(auditMessage.getValue().contains("actor_id=svc-dataservice"));
    // subject_token carries no prior 'act' chain, so the audited incoming depth is 0
    assertTrue(auditMessage.getValue().contains("act_chain_depth=0"));
  }

  @Test
  public void testAuditRecordsIncomingActorChainDepth() throws Exception {
    filter.delegationServerEnabled = true;
    // subject_token carries a two-deep prior delegation chain (act -> act)
    filter.valid.put("subtok",
        jwtWithActClaim("alice", "KNOXSSO", Map.of("sub", "actor-1", "act", Map.of("sub", "actor-2"))));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    final Capture<String> auditMessage = expectAudit(Action.TOKEN_EXCHANGE,
        "USER/svc-dataservice", ResourceType.PRINCIPAL, ActionOutcome.SUCCESS);
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE), response, chain);

    EasyMock.verify(auditor);
    assertTrue(auditMessage.getValue().contains("act_chain_depth=2"));
  }

  @Test
  public void testActorTokenDelegationPolicyCheckRequestSubjectNameAndHeadlessFlag() throws Exception {
    filter.delegationServerEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.capturedPolicyCheckRequest);
    assertEquals("alice", filter.capturedPolicyCheckRequest.getSubjectName());
    assertFalse(filter.capturedPolicyCheckRequest.isHeadlessExchange());
    assertTrue(filter.capturedPolicyCheckRequest.getRequestedScopes().isEmpty());
  }

  @Test
  public void testHeadlessDelegationPolicyCheckRequestUsesSubjectTokenAsActorAndRequestedSubjectAsSubjectName() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, null, null, "bob"), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.capturedPolicyCheckRequest);
    assertEquals("USER", filter.capturedPolicyCheckRequest.getActorAuthority());
    assertEquals("alice", filter.capturedPolicyCheckRequest.getActorId());
    assertEquals("bob", filter.capturedPolicyCheckRequest.getSubjectName());
    assertTrue(filter.capturedPolicyCheckRequest.isHeadlessExchange());
    assertTrue(filter.capturedPolicyCheckRequest.getRequestedScopes().isEmpty());
  }

  @Test
  public void testActorTokenDelegationPolicyCheckRequestCarriesRequestedResources() throws Exception {
    filter.delegationServerEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    handler.handle(delegationRequest("subtok", "acttok", JWT_TYPE, null,
        new String[] {"https://svc.example.com/"}, null), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.capturedPolicyCheckRequest);
    assertEquals(Set.of("https://svc.example.com/"),
        filter.capturedPolicyCheckRequest.getRequestedResources());
  }

  @Test
  public void testSubjectTokenWithActClaimCreatesActorChainPrincipal() throws Exception {
    filter.delegationServerEnabled = true;
    // subject_token already carries a prior delegation chain (an 'act' claim) ...
    filter.valid.put("subtok", jwtWithActClaim("alice", "KNOXSSO", Map.of("sub", "prior-actor")));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.establishedSubject);
    // ... which is preserved as an ActorChainPrincipal in the exchanged Subject
    final Set<ActorChainPrincipal> actorChainPrincipals =
        filter.establishedSubject.getPrincipals(ActorChainPrincipal.class);
    assertFalse("ActorChainPrincipal should be present", actorChainPrincipals.isEmpty());
    assertEquals("prior-actor", actorChainPrincipals.iterator().next().getCurrentActor());
  }

  @Test
  public void testSubjectValidationFailureDoesNotEstablishContext() throws Exception {
    // "subtok" is not in the valid map -> parseAndValidateJWT returns null (error already sent)
    handler.handle(request("subtok", JWT_TYPE, null, null), response, chain);
    assertFalse(filter.continued);
  }

  @Test
  public void testActorValidationFailureDoesNotEstablishContext() throws Exception {
    filter.delegationServerEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    // "acttok" is not valid
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE), response, chain);
    assertFalse(filter.continued);
    // RecordingFilter.parseAndValidateJWT does not call handleValidationError itself when a
    // token is missing from `valid` -- unlike the real filter, it leaves filter.error/
    // errorDescription/errorStatus at their defaults for this case, so there is no rejection
    // message here to assert on the way other tests in this class do. Asserting the defaults
    // are unchanged still guards against this test passing for the wrong reason: if the gate
    // check or the actor-token/requested_subject conflict check had rejected the request instead
    // of actor-token parsing failing, handler.handle() would have called
    // filter.handleValidationError(...) and these fields would be non-default.
    assertEquals(-1, filter.errorStatus);
    assertNull(filter.error);
    assertNull(filter.errorDescription);
  }

  @Test
  public void testUnparseableSubjectTokenReturnsInvalidRequest() throws Exception {
    filter.throwOnParse.add("subtok");
    handler.handle(request("subtok", JWT_TYPE, null, null), response, chain);
    assertEquals(HttpServletResponse.SC_UNAUTHORIZED, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertTrue(filter.errorDescription.contains("Failed to parse token in token exchange"));
    assertFalse(filter.continued);
  }

  @Test
  public void testResourceBodyParamConveyedAsRequestedAudiences() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(exchangeRequest("subtok",
        new String[] {"https://recipient1", "https://recipient2"}, null), response, chain);

    assertTrue(filter.continued);
    assertTrue(requestedAudiencesAttr.hasCaptured());
    assertEquals(Arrays.asList("https://recipient1", "https://recipient2"), requestedAudiencesAttr.getValue());
  }

  @Test
  public void testAudienceBodyParamConveyedAsRequestedAudiences() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    // RFC 8693 audience is a logical service name and is not URI-constrained
    handler.handle(exchangeRequest("subtok", null, new String[] {"service-a"}), response, chain);

    assertTrue(filter.continued);
    assertEquals(Arrays.asList("service-a"), requestedAudiencesAttr.getValue());
  }

  @Test
  public void testResourceAndAudienceCombinedResourceFirst() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(exchangeRequest("subtok",
        new String[] {"https://recipient1"}, new String[] {"service-a"}), response, chain);

    assertEquals(Arrays.asList("https://recipient1", "service-a"), requestedAudiencesAttr.getValue());
  }

  @Test
  public void testCommaSeparatedResourceValuesAreSplit() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(exchangeRequest("subtok",
        new String[] {"https://recipient1, https://recipient2"}, null), response, chain);

    assertEquals(Arrays.asList("https://recipient1", "https://recipient2"), requestedAudiencesAttr.getValue());
  }

  @Test
  public void testInvalidResourceUriRejectedAsInvalidTarget() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(exchangeRequest("subtok", new String[] {"not-a-uri"}, null), response, chain);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_target", filter.error);
    assertFalse(filter.continued);
  }

  @Test
  public void testResourceUriWithFragmentRejectedAsInvalidTarget() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(exchangeRequest("subtok", new String[] {"https://recipient1#fragment"}, null), response, chain);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_target", filter.error);
    assertFalse(filter.continued);
  }

  @Test
  public void testEmptyResourceValueRejectedAsInvalidTarget() throws Exception {
    // An empty resource value (e.g. "resource=") is not an absolute URI and is surfaced as an error
    // rather than silently dropped.
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(exchangeRequest("subtok", new String[] {""}, null), response, chain);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_target", filter.error);
    assertFalse(filter.continued);
  }

  @Test
  public void testNoResourceOrAudienceLeavesRequestAttributeUnset() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(exchangeRequest("subtok", null, null), response, chain);

    assertTrue(filter.continued);
    assertFalse(requestedAudiencesAttr.hasCaptured());
  }

  @Test
  public void testGateRejectsActorTokenPresentWhenDelegationServerDisabled() throws Exception {
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE), response, chain);

    assertFalse(filter.continued);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertTrue(filter.errorDescription.contains("Delegation is not enabled for this topology"));
  }

  @Test
  public void testGateRejectsHeadlessCandidateWhenDelegationServerDisabled() throws Exception {
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, null, null, "bob"), response, chain);

    assertFalse(filter.continued);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertTrue(filter.errorDescription.contains("Delegation is not enabled for this topology"));
  }

  @Test
  public void testGateRejectsActorTokenWithHeadlessCandidateWhenDelegationServerDisabled() throws Exception {
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    handler.handle(request("subtok", JWT_TYPE,"acttok", JWT_TYPE, "bob"), response, chain);

    assertFalse(filter.continued);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertTrue(filter.errorDescription.contains("Delegation is not enabled for this topology"));
  }

  @Test
  public void testGateRejectsActorTokenPresentRequestedSubjectPresentAndDifferentFromSubject() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE, "bob"), response, chain);

    assertFalse(filter.continued);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertEquals("requested_subject must not differ from subject_token's subject when actor_token is present",
        filter.errorDescription);
  }

  @Test
  public void testActorTokenPresentWithRequestedSubjectEqualToSubjectIsTreatedAsActorTokenDelegation() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
    handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE, "alice"), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.establishedSubject);
    assertEquals("svc-dataservice", primaryName(filter.establishedSubject));
  }

  @Test
  public void testActorTokenPresentWithRequestedSubjectEqualToSubjectIsTreatedAsActorTokenDelegationIgnoresMalformedRequestedSubject() throws Exception {
      filter.delegationServerEnabled = true;
      filter.delegationRequestedSubjectEnabled = true;
      filter.valid.put("subtok", jwt("a\u0007lice", "KNOXSSO"));
      filter.valid.put("acttok", jwt("svc-dataservice", "https://k8s"));
      handler.handle(request("subtok", JWT_TYPE, "acttok", JWT_TYPE, "a\u0007lice"), response, chain);

      assertTrue(filter.continued);
      assertNotNull(filter.establishedSubject);
      assertEquals("svc-dataservice", primaryName(filter.establishedSubject));
  }

  @Test
  public void testHeadlessDelegationCreatesTokenExchangePrincipalWhenGateEnabled() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, null, null, "bob"), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.establishedSubject);
    // Headless delegation: subject_token's own identity (alice) is the actor ...
    assertEquals("alice", primaryName(filter.establishedSubject));
    // ... and requested_subject (bob) is carried as the impersonated party via
    // TokenExchangePrincipal, with a null subjectIssuer.
    final TokenExchangePrincipal tep =
        filter.establishedSubject.getPrincipals(TokenExchangePrincipal.class).iterator().next();
    assertEquals("bob", tep.getSubjectPrincipalName());
    assertNull(tep.getSubjectIssuer());
    assertEquals("alice", tep.getActorPrincipalName());
    assertEquals("KNOXSSO", tep.getActorIssuer());
  }

  @Test
  public void testHeadlessDelegationIgnoresSubjectTokenActClaim() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    // subject_token carries an 'act' claim of its own (e.g. it was itself issued via some
    // earlier, unrelated delegation) -- this describes the actor's own history, not bob's.
    filter.valid.put("subtok", jwtWithActClaim("alice", "KNOXSSO", Map.of("sub", "prior-actor")));
    handler.handle(request("subtok", JWT_TYPE, null, null, "bob"), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.establishedSubject);
    // ... and must NOT be attributed to requested_subject (bob): no ActorChainPrincipal is
    // added at all, regardless of what subject_token's own act claim contains.
    final Set<ActorChainPrincipal> actorChainPrincipals =
        filter.establishedSubject.getPrincipals(ActorChainPrincipal.class);
    assertTrue("no ActorChainPrincipal should be present", actorChainPrincipals.isEmpty());
  }

  @Test
  public void testHeadlessDelegationTrimsRequestedSubjectEndToEnd() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    // Assert the principal actually built carries the trimmed value, not the raw padded one.
    handler.handle(request("subtok", JWT_TYPE, null, null, "  bob  "), response, chain);

    assertTrue(filter.continued);
    final TokenExchangePrincipal tep =
        filter.establishedSubject.getPrincipals(TokenExchangePrincipal.class).iterator().next();
    assertEquals("bob", tep.getSubjectPrincipalName());
  }

  @Test
  public void testHeadlessDelegationPreservesRequestedSubjectCase() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, null, null, "Bob"), response, chain);

    assertTrue(filter.continued);
    final TokenExchangePrincipal tep =
        filter.establishedSubject.getPrincipals(TokenExchangePrincipal.class).iterator().next();
    // No case-folding: "Bob" must not become "bob" or vice versa.
    assertEquals("Bob", tep.getSubjectPrincipalName());
  }

  @Test
  public void testHeadlessDelegationWithLongRequestedSubjectValue() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    final StringBuilder sb = new StringBuilder("bob-");
    // Max length matches TokenExchangeHandler private constant MAX_REQUESTED_SUBJECT_LENGTH
    for (int i = 0; i < 4096 - "bob-".length(); i++) {
      sb.append('x');
    }
    final String longSubject = sb.toString();
    handler.handle(request("subtok", JWT_TYPE, null, null, longSubject), response, chain);

    assertTrue(filter.continued);
    final TokenExchangePrincipal tep =
        filter.establishedSubject.getPrincipals(TokenExchangePrincipal.class).iterator().next();
    assertEquals(longSubject, tep.getSubjectPrincipalName());
  }

  @Test
  public void testHeadlessDelegationWithSubjectTokenMissingIssuer() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    // subject_token with no issuer claim: null issuer is valid in TokenExchangePrincipalImpl
    filter.valid.put("subtok", jwt("alice", null));
    handler.handle(request("subtok", JWT_TYPE, null, null, "bob"), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.establishedSubject);
    assertEquals("alice", primaryName(filter.establishedSubject));
    final TokenExchangePrincipal tep =
        filter.establishedSubject.getPrincipals(TokenExchangePrincipal.class).iterator().next();
    assertNull(tep.getActorIssuer());
  }

  @Test
  public void testRequestedSubjectIgnoredWhenFlagDisabledTreatedAsSameSubjectExchange() throws Exception {
    // delegation.requested.subject.enabled left false (default): requested_subject is not read
    // at all, so this request is classified as same-subject regardless of delegation.server.enabled.
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, null, null, "bob"), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.establishedSubject);
    assertEquals("alice", primaryName(filter.establishedSubject));
  }

  @Test
  public void testEmptyRequestedSubjectTreatedAsAbsent() throws Exception {
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, null, null, "   "), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.establishedSubject);
    assertEquals("alice", primaryName(filter.establishedSubject));
  }

  @Test
  public void testWhitespaceRequestedSubjectTreatedAsAbsent() throws Exception {
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, null, null, "   "), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.establishedSubject);
    assertEquals("alice", primaryName(filter.establishedSubject));
  }

  @Test
  public void testRequestedSubjectEqualToSubjectTreatedAsSameSubjectExchange() throws Exception {
    // Delegation is not enabled, so a delegation exchange would be denied, but a
    // requested_subject that matches the subject_token sub claim is treated as a same-
    // subject exchange.
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(request("subtok", JWT_TYPE, null, null, "alice"), response, chain);

    assertTrue(filter.continued);
    assertNotNull(filter.establishedSubject);
    assertEquals("alice", primaryName(filter.establishedSubject));
  }

  @Test
  public void testControlCharacterInRequestedSubjectRejectedAsInvalidRequest() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", null, null, "b\u0007ob", null, null), response, chain);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertEquals("The requested_subject value is malformed", filter.errorDescription);
    assertFalse(filter.continued);
  }

  @Test
  public void testOverlongRequestedSubjectRejectedAsInvalidRequest() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    final StringBuilder sb = new StringBuilder();
    // Length just larger than TokenExchangeHandler private constant MAX_REQUESTED_SUBJECT_LENGTH
    for (int i = 0; i < 4097; i++) {
      sb.append('x');
    }
    handler.handle(delegationRequest("subtok", null, null, sb.toString(), null, null), response, chain);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertEquals("The requested_subject value is malformed", filter.errorDescription);
    assertFalse(filter.continued);
  }

  @Test
  public void testRequestedSubjectAtMaxLengthIsNotRejectedForLengthAlone() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    final StringBuilder sb = new StringBuilder();
    // Max length matches TokenExchangeHandler private constant MAX_REQUESTED_SUBJECT_LENGTH
    for (int i = 0; i < 4096; i++) {
      sb.append('x');
    }
    handler.handle(delegationRequest("subtok", null, null, sb.toString(), null, null), response, chain);

    assertTrue(filter.continued);
  }

  @Test
  public void testMissingAudienceRejectedWhenRequiredDelegationWithActorToken() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationEnforceRequestedAudienceRequired = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("actortok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", "actortok", JWT_TYPE, null, null, null), response, chain);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertEquals("At least one audience or resource value is required for a delegation exchange",
        filter.errorDescription);
    assertFalse(filter.continued);
  }

  @Test
  public void testMissingAudienceRejectedWhenRequiredHeadlessDelegation() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.delegationEnforceRequestedAudienceRequired = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", null, null, "bob", null, null), response, chain);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertEquals("At least one audience or resource value is required for a delegation exchange",
        filter.errorDescription);
    assertFalse(filter.continued);
  }

  @Test
  public void testBlankAudienceValueDoesNotCountAsPresentWhenRequiredActorTokenDelegation() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationEnforceRequestedAudienceRequired = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("actortok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", "actortok", JWT_TYPE, null, null, new String[] {""}), response, chain);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertEquals("At least one audience or resource value is required for a delegation exchange",
        filter.errorDescription);
    assertFalse(filter.continued);
  }

  @Test
  public void testBlankAudienceValueDoesNotCountAsPresentWhenRequiredHeadlessDelegation() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.delegationEnforceRequestedAudienceRequired = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", null, null, "bob", null, new String[] {""}), response, chain);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertEquals("At least one audience or resource value is required for a delegation exchange",
        filter.errorDescription);
    assertFalse(filter.continued);
    }

  @Test
  public void testAudiencePresentSatisfiesRequiredCheck() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationEnforceRequestedAudienceRequired = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("actortok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", "actortok", JWT_TYPE, null, null, new String[] {"aud1"}),
        response, chain);

    assertTrue(filter.continued);
  }

  @Test
  public void testMoreThanOneDistinctAudienceRejectedWhenMaxOne() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationEnforceRequestedAudienceMaxOne = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("actortok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", "actortok", JWT_TYPE, null, null,
        new String[] {"aud1", "aud2"}), response, chain);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertEquals("Exactly one combined audience or resource value is allowed for a delegation exchange",
        filter.errorDescription);
    assertFalse(filter.continued);
  }

  @Test
  public void testDuplicateValueAcrossResourceAndAudienceNotRejectedWhenMaxOne() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationEnforceRequestedAudienceMaxOne = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("actortok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", "actortok", JWT_TYPE, null,
        new String[] {"https://svc"}, new String[] {"https://svc"}), response, chain);

    assertTrue(filter.continued);
  }

  @Test
  public void testZeroAudienceAllowedWhenMaxOneAloneEnabled() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationEnforceRequestedAudienceMaxOne = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("actortok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", "actortok", JWT_TYPE, null, null, null), response, chain);

    assertTrue(filter.continued);
  }

  @Test
  public void testExactlyOneAudienceRequiredWhenBothFlagsOnAndSatisfied() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationEnforceRequestedAudienceRequired = true;
    filter.delegationEnforceRequestedAudienceMaxOne = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("actortok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", "actortok", JWT_TYPE, null, null, new String[] {"aud1"}),
        response, chain);

    assertTrue(filter.continued);
  }

  @Test
  public void testExactlyOneAudienceRequiredZeroValuesRejectedWithRequiredMessage() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationEnforceRequestedAudienceRequired = true;
    filter.delegationEnforceRequestedAudienceMaxOne = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("actortok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", "actortok", JWT_TYPE, null, null, null), response, chain);

    assertEquals("At least one audience or resource value is required for a delegation exchange",
        filter.errorDescription);
  }

  @Test
  public void testExactlyOneAudienceRequiredTwoValuesRejectedWithMaxOneMessage() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationEnforceRequestedAudienceRequired = true;
    filter.delegationEnforceRequestedAudienceMaxOne = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    filter.valid.put("actortok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", "actortok", JWT_TYPE, null, null,
        new String[] {"aud1", "aud2"}), response, chain);

    assertEquals("Exactly one combined audience or resource value is allowed for a delegation exchange",
        filter.errorDescription);
  }

  @Test
  public void testMoreThanOneDistinctAudienceRejectedWhenMaxOneHeadlessDelegation() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.delegationEnforceRequestedAudienceMaxOne = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", null, null, "bob", null,
        new String[] {"aud1", "aud2"}), response, chain);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, filter.errorStatus);
    assertEquals("invalid_request", filter.error);
    assertEquals("Exactly one combined audience or resource value is allowed for a delegation exchange",
        filter.errorDescription);
    assertFalse(filter.continued);
  }

  @Test
  public void testExactlyOneAudienceRequiredWhenBothFlagsOnAndSatisfiedHeadlessDelegation() throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.delegationEnforceRequestedAudienceRequired = true;
    filter.delegationEnforceRequestedAudienceMaxOne = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", null, null, "bob", null, new String[] {"aud1"}),
        response, chain);

    assertTrue(filter.continued);
  }

  @Test
  public void testExactlyOneAudienceRequiredZeroValuesRejectedWithRequiredMessageHeadlessDelegation()
      throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.delegationEnforceRequestedAudienceRequired = true;
    filter.delegationEnforceRequestedAudienceMaxOne = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", null, null, "bob", null, null), response, chain);

    assertEquals("At least one audience or resource value is required for a delegation exchange",
        filter.errorDescription);
  }

  @Test
  public void testExactlyOneAudienceRequiredTwoValuesRejectedWithMaxOneMessageHeadlessDelegation()
      throws Exception {
    filter.delegationServerEnabled = true;
    filter.delegationRequestedSubjectEnabled = true;
    filter.delegationEnforceRequestedAudienceRequired = true;
    filter.delegationEnforceRequestedAudienceMaxOne = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(delegationRequest("subtok", null, null, "bob", null,
        new String[] {"aud1", "aud2"}), response, chain);

    assertEquals("Exactly one combined audience or resource value is allowed for a delegation exchange",
        filter.errorDescription);
  }

  @Test
  public void testAudienceRequiredFlagDoesNotApplyToNonDelegationExchange() throws Exception {
    filter.delegationEnforceRequestedAudienceRequired = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(exchangeRequest("subtok", null, null), response, chain);

    assertTrue(filter.continued);
  }

  @Test
  public void testAudienceMaxOneFlagDoesNotApplyToNonDelegationExchange() throws Exception {
    filter.delegationEnforceRequestedAudienceMaxOne = true;
    filter.valid.put("subtok", jwt("alice", "KNOXSSO"));
    handler.handle(exchangeRequest("subtok", null, new String[] {"aud1", "aud2"}), response, chain);

    assertTrue(filter.continued);
    assertEquals(Arrays.asList("aud1", "aud2"), requestedAudiencesAttr.getValue());
  }

  private static String primaryName(Subject subject) {
    return subject.getPrincipals(PrimaryPrincipal.class).iterator().next().getName();
  }

  private HttpServletRequest request(String subjectToken, String subjectTokenType,
                                     String actorToken, String actorTokenType) {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter(JWTFederationFilter.SUBJECT_TOKEN)).andReturn(subjectToken).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.SUBJECT_TOKEN_TYPE)).andReturn(subjectTokenType).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.ACTOR_TOKEN)).andReturn(actorToken).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.ACTOR_TOKEN_TYPE)).andReturn(actorTokenType).anyTimes();
    EasyMock.replay(request);
    return request;
  }

  private HttpServletRequest request(String subjectToken, String subjectTokenType,
                                     String actorToken, String actorTokenType,
                                     String requestedSubject) {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter(JWTFederationFilter.SUBJECT_TOKEN)).andReturn(subjectToken).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.SUBJECT_TOKEN_TYPE)).andReturn(subjectTokenType).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.ACTOR_TOKEN)).andReturn(actorToken).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.ACTOR_TOKEN_TYPE)).andReturn(actorTokenType).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.REQUESTED_SUBJECT)).andReturn(requestedSubject).anyTimes();
    EasyMock.replay(request);
    return request;
  }

  /**
   * Build a subject-only token-exchange request carrying the given {@code resource}/{@code audience}
   * body parameters, capturing the requested-audiences request attribute the handler stashes for the
   * downstream KNOXTOKEN service.
   */
  private HttpServletRequest exchangeRequest(String subjectToken, String[] resources, String[] audiences) {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter(JWTFederationFilter.SUBJECT_TOKEN)).andReturn(subjectToken).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.SUBJECT_TOKEN_TYPE)).andReturn(JWT_TYPE).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.ACTOR_TOKEN)).andReturn(null).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.ACTOR_TOKEN_TYPE)).andReturn(null).anyTimes();
    EasyMock.expect(request.getParameterValues(CommonTokenConstants.RESOURCE)).andReturn(resources).anyTimes();
    EasyMock.expect(request.getParameterValues(CommonTokenConstants.AUDIENCE)).andReturn(audiences).anyTimes();
    requestedAudiencesAttr = EasyMock.newCapture();
    request.setAttribute(EasyMock.eq(CommonTokenConstants.REQUESTED_AUDIENCES_REQUEST_ATTR),
        EasyMock.capture(requestedAudiencesAttr));
    EasyMock.expectLastCall().anyTimes();
    EasyMock.replay(request);
    return request;
  }

  /**
   * Build a full delegation-candidate token-exchange request: subject_token plus every
   * optional parameter this task's checks read (actor_token/actor_token_type,
   * requested_subject, resource/audience), capturing the requested-audiences request
   * attribute the handler may stash for the downstream KNOXTOKEN service. Pass null for any
   * parameter not relevant to a given test.
   */
  private HttpServletRequest delegationRequest(String subjectToken, String actorToken, String actorTokenType,
                                               String requestedSubject, String[] resources, String[] audiences) {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter(JWTFederationFilter.SUBJECT_TOKEN)).andReturn(subjectToken).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.SUBJECT_TOKEN_TYPE)).andReturn(JWT_TYPE).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.ACTOR_TOKEN)).andReturn(actorToken).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.ACTOR_TOKEN_TYPE)).andReturn(actorTokenType).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.REQUESTED_SUBJECT)).andReturn(requestedSubject).anyTimes();
    EasyMock.expect(request.getParameterValues(CommonTokenConstants.RESOURCE)).andReturn(resources).anyTimes();
    EasyMock.expect(request.getParameterValues(CommonTokenConstants.AUDIENCE)).andReturn(audiences).anyTimes();
    requestedAudiencesAttr = EasyMock.newCapture();
    request.setAttribute(EasyMock.eq(CommonTokenConstants.REQUESTED_AUDIENCES_REQUEST_ATTR),
        EasyMock.capture(requestedAudiencesAttr));
    EasyMock.expectLastCall().anyTimes();
    requestedTtlAttr = EasyMock.newCapture();
    request.setAttribute(EasyMock.eq(CommonTokenConstants.REQUESTED_TTL_REQUEST_ATTR),
        EasyMock.capture(requestedTtlAttr));
    EasyMock.expectLastCall().anyTimes();
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

  private static JWT jwtWithActClaim(String subject, String issuer, Map<String, Object> actClaim) {
    final JWT jwt = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(jwt.getSubject()).andReturn(subject).anyTimes();
    EasyMock.expect(jwt.getIssuer()).andReturn(issuer).anyTimes();
    // subject_token carries a prior delegation chain via its 'act' claim
    EasyMock.expect(jwt.getClaimAsObject(JWTToken.ACT_CLAIM)).andReturn(actClaim).anyTimes();
    EasyMock.replay(jwt);
    return jwt;
  }

  /**
   * A {@link JWTFederationFilter} whose validation and context-establishment callbacks are
   * replaced with recording stubs, so the handler's own logic can be exercised in isolation.
   */
  private static final class RecordingFilter extends JWTFederationFilter {
    private final Map<String, JWT> valid = new HashMap<>();
    private final Set<String> throwOnParse = new HashSet<>();
    private int errorStatus = -1;
    private String error;
    private String errorDescription;
    private boolean continued;
    private Subject establishedSubject;
    private boolean delegationServerEnabled;
    private boolean delegationRequestedSubjectEnabled;
    private boolean delegationEnforceRequestedAudienceRequired;
    private boolean delegationEnforceRequestedAudienceMaxOne;
    // Defaults to an "allow" decision so every pre-existing test that never sets this field
    // keeps passing unmodified.
    private PolicyDecision policyDecision = new PolicyDecision(null, 0);
    private RuntimeException policyEvaluationException;
    private PolicyCheckRequest capturedPolicyCheckRequest;

    @Override
    boolean isDelegationServerEnabled() {
      return delegationServerEnabled;
    }

    @Override
    boolean isDelegationRequestedSubjectEnabled() {
      return delegationRequestedSubjectEnabled;
    }

    @Override
    boolean isDelegationEnforceRequestedAudienceRequired() {
      return delegationEnforceRequestedAudienceRequired;
    }

    @Override
    boolean isDelegationEnforceRequestedAudienceMaxOne() {
      return delegationEnforceRequestedAudienceMaxOne;
    }

    @Override
    PolicyDecision evaluateDelegationPolicy(PolicyCheckRequest request) {
      this.capturedPolicyCheckRequest = request;
      if (policyEvaluationException != null) {
        throw policyEvaluationException;
      }
      return policyDecision;
    }

    @Override
    JWT parseAndValidateJWT(HttpServletRequest request, HttpServletResponse response,
                            FilterChain chain, String tokenValue)
        throws ParseException, IOException, ServletException {
      if (throwOnParse.contains(tokenValue)) {
        throw new ParseException("cannot parse " + tokenValue, 0);
      }
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
    void handleValidationError(HttpServletRequest request, HttpServletResponse response,
                               int status, String error, String description) {
      this.errorStatus = status;
      this.error = error == null ? "" : error;
      this.errorDescription = description == null ? "" : description;
    }
  }
}

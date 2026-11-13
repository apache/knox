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

import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.actorChain;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.filterConfig;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.withAttributes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The audit record {@link K8sDestinationAudienceValidator} writes for a delegated token (KNOX-3568).
 */
public class K8sDestinationAudienceValidatorAuditTest {

  private static final String SERVER_NAME_HEADER = "x-dest-server-name";
  private static final String SPIFFE_HEADER = "x-dest-spiffe-id";
  private static final String PATH_HEADER = "x-dest-path";
  private static final String DEST_SERVICE = "secret-service.platform.svc.cluster.local";
  private static final String REQUEST_URI = "/gateway/console/auth/api/v1/extauthz/api/v1/secrets";
  private static final String SOURCE_URI = "https://localhost:8443" + REQUEST_URI;

  private Auditor originalAuditor;
  private RecordingAuditor auditor;

  @Before
  public void setUp() {
    originalAuditor = DelegatedRequestAuditing.auditor;
    auditor = new RecordingAuditor();
    DelegatedRequestAuditing.auditor = auditor;
  }

  @After
  public void tearDown() {
    DelegatedRequestAuditing.auditor = originalAuditor;
  }

  private static K8sDestinationAudienceValidator validator(final Map<String, String> params) throws Exception {
    final K8sDestinationAudienceValidator validator = new K8sDestinationAudienceValidator();
    validator.init(filterConfig(params));
    return validator;
  }

  private static K8sDestinationAudienceValidator serverNameValidator() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    return validator(params);
  }

  private static JWT token(final String subject, final Object actClaim, final String... audiences) {
    final JWT token = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(token.getSubject()).andReturn(subject).anyTimes();
    EasyMock.expect(token.getClaimAsObject(JWTToken.ACT_CLAIM)).andReturn(actClaim).anyTimes();
    EasyMock.expect(token.getAudienceClaims()).andReturn(audiences).anyTimes();
    EasyMock.replay(token);
    return token;
  }

  private static HttpServletRequest request(final Map<String, String> headers) {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    for (final Map.Entry<String, String> header : headers.entrySet()) {
      EasyMock.expect(request.getHeader(header.getKey())).andReturn(header.getValue()).anyTimes();
    }
    EasyMock.expect(request.getRequestURI()).andReturn(REQUEST_URI).anyTimes();
    EasyMock.replay(request);
    final HttpServletRequest withAttributes = withAttributes(request);
    withAttributes.setAttribute(AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME,
        SOURCE_URI + "?page=2");
    return withAttributes;
  }

  private static Map<String, String> serverNameHeader(final String value) {
    final Map<String, String> headers = new HashMap<>();
    headers.put(SERVER_NAME_HEADER, value);
    return headers;
  }

  @Test
  public void testAcceptedDelegatedTokenIsAudited() throws Exception {
    final HttpServletRequest request = request(serverNameHeader(DEST_SERVICE));
    final JWT token = token("alice", actorChain("spiffe://td/ns/engines/sa/engine"),
        "https://" + DEST_SERVICE);

    assertTrue(serverNameValidator().validate(request, token, null).isValid());

    assertEquals(1, auditor.entries.size());
    final RecordingAuditor.Entry entry = auditor.entries.get(0);
    assertEquals(Action.AUDIENCE_VALIDATION, entry.action());
    // the source URL without its query string
    assertEquals(SOURCE_URI, entry.resourceName());
    assertEquals(ResourceType.URI, entry.resourceType());
    assertEquals(ActionOutcome.SUCCESS, entry.outcome());
    assertEquals("event_type=delegated_audience_check claims_verified=false token_id= sub=alice"
        + " act_chain=spiffe://td/ns/engines/sa/engine dest_service=" + DEST_SERVICE
        + " path=" + REQUEST_URI, entry.message());
    assertEquals(new DelegatedRequestAuditing.Destination(DEST_SERVICE, null, REQUEST_URI),
        request.getAttribute(DelegatedRequestAuditing.DESTINATION_ATTRIBUTE));
  }

  @Test
  public void testAudienceMismatchIsAuditedWithReason() throws Exception {
    final HttpServletRequest request = request(serverNameHeader(DEST_SERVICE));
    final JWT token = token("alice", actorChain("spiffe://td/ns/engines/sa/engine"),
        "https://other-service.platform.svc.cluster.local");

    final AudienceValidationResult result = serverNameValidator().validate(request, token, null);
    assertFalse(result.isValid());

    assertEquals(1, auditor.entries.size());
    final RecordingAuditor.Entry entry = auditor.entries.get(0);
    assertEquals(Action.AUDIENCE_VALIDATION, entry.action());
    assertEquals(ActionOutcome.FAILURE, entry.outcome());
    assertEquals("event_type=delegated_audience_check claims_verified=false token_id= sub=alice"
        + " act_chain=spiffe://td/ns/engines/sa/engine dest_service=" + DEST_SERVICE
        + " path=" + REQUEST_URI + " reason=audience_invalid "
        + DelegatedRequestAuditing.field("detail", result.message()), entry.message());
  }

  @Test
  public void testNestedActorChainIsAuditedMostRecentActorFirst() throws Exception {
    final HttpServletRequest request = request(serverNameHeader(DEST_SERVICE));
    // svc-c acted for svc-b, which acted for svc-a, which acted for alice
    final JWT token = token("alice", actorChain("svc-c", "svc-b", "svc-a"), "https://" + DEST_SERVICE);

    assertTrue(serverNameValidator().validate(request, token, null).isValid());

    assertEquals(1, auditor.entries.size());
    final String message = auditor.entries.get(0).message();
    assertTrue(message, message.contains(" sub=alice act_chain=svc-c<-svc-b<-svc-a "));
  }

  @Test
  public void testTokenWithoutActClaimIsNotAudited() throws Exception {
    final HttpServletRequest request = request(serverNameHeader(DEST_SERVICE));
    final JWT token = token("alice", null, "https://" + DEST_SERVICE);

    serverNameValidator().validate(request, token, null);

    assertTrue(auditor.entries.isEmpty());
    assertNull(request.getAttribute(DelegatedRequestAuditing.DESTINATION_ATTRIBUTE));
  }

  @Test
  public void testLineBreaksInClaimsAndHeadersCannotForgeLogLines() throws Exception {
    final HttpServletRequest request = request(serverNameHeader(DEST_SERVICE + "\r\nforged=1"));
    final JWT token = token("alice\nresult=accepted", actorChain("svc-a\u2028x"), "https://" + DEST_SERVICE);

    serverNameValidator().validate(request, token, null);

    assertEquals(1, auditor.entries.size());
    final String message = auditor.entries.get(0).message();
    assertTrue(message, message.contains(" sub=alice?result=accepted "));
    assertTrue(message, message.contains(" act_chain=svc-a?x "));
    assertTrue(message, message.contains(" dest_service=" + DEST_SERVICE + "??forged=1 "));
    assertFalse(message, message.chars().anyMatch(c -> c == '\r' || c == '\n' || c == '\u2028'));
  }

  @Test
  public void testConfiguredButAbsentHeaderIsAuditedEmptyAndPathQueryIsDropped() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    params.put(K8sDestinationAudienceValidator.NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SPIFFE_HEADER);
    params.put(K8sDestinationAudienceValidator.PATH_HEADER_PARAM, PATH_HEADER);
    final Map<String, String> headers = serverNameHeader(DEST_SERVICE);
    headers.put(PATH_HEADER, "/api/v1/secrets?token=secret");
    final JWT token = token("alice", actorChain("svc-a"), "https://" + DEST_SERVICE + "/api/v1/secrets");

    validator(params).validate(request(headers), token, null);

    assertEquals(1, auditor.entries.size());
    final String message = auditor.entries.get(0).message();
    assertTrue(message, message.contains(
        " dest_service=" + DEST_SERVICE + " dest_spiffe_id= path=/api/v1/secrets"));
    assertFalse(message, message.contains("token=secret"));
  }
}

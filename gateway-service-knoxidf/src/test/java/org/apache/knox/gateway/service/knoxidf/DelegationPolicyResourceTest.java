/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.service.knoxidf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.delegation.DelegationPolicy;
import org.apache.knox.gateway.services.knoxidf.delegation.DelegationPolicyAlreadyExistsException;
import org.apache.knox.gateway.services.knoxidf.delegation.DelegationPolicyList;
import org.apache.knox.gateway.services.knoxidf.delegation.DelegationPolicyNotFoundException;
import org.apache.knox.gateway.services.knoxidf.delegation.DelegationPolicyService;
import org.apache.knox.gateway.services.knoxidf.delegation.RegisterOrUpdateResult;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.security.Principal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DelegationPolicyResourceTest {

  private static final String ACTOR_AUTHORITY = "k8s_sa";
  private static final String ACTOR_ID = "svc-1";
  private static final String REGISTRATION_ID = "reg-123";
  private static final String OPERATOR = "admin";
  private static final int MIN_TTL = GatewayConfig.DELEGATION_SERVICE_MIN_TOKEN_TTL_SEC_DEFAULT;
  private static final int MAX_TTL = GatewayConfig.DELEGATION_SERVICE_MAX_TOKEN_TTL_SEC_DEFAULT;

  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
  private static final String ISO_INSTANT_PATTERN =
      "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";

  // Capture the real static Auditor so @After can restore it.
  private static final Auditor ORIGINAL_AUDITOR = DelegationPolicyResource.auditor;

  private DelegationPolicyResource resource;
  private DelegationPolicyService mockService;
  private Auditor mockAuditor;

  @Before
  public void setUp() throws Exception {
    mockService = EasyMock.createMock(DelegationPolicyService.class);
    mockAuditor = EasyMock.createMock(Auditor.class);
    DelegationPolicyResource.auditor = mockAuditor;
    resource = buildResource(buildPrincipal(OPERATOR));
  }

  @After
  public void tearDown() {
    DelegationPolicyResource.auditor = ORIGINAL_AUDITOR;
  }

  // ---------------------------------------------------------------------------
  // POST - success paths
  // ---------------------------------------------------------------------------

  @Test
  public void testRegisterAllDefaults() throws Exception {
    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.register(EasyMock.capture(captured)))
        .andAnswer(() -> withRegistrationId(captured.getValue(), REGISTRATION_ID));
    expectAudit(REGISTRATION_ID, ActionOutcome.SUCCESS, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.register(toJson(minimalFields()));

    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    final DelegationPolicyResponse body = parseResponse(response);
    assertEquals(REGISTRATION_ID, body.getRegistrationId());
    assertEquals(ACTOR_AUTHORITY, body.getActorAuthority());
    assertEquals(ACTOR_ID, body.getActorId());
    assertNull(body.getName());
    assertEquals("active", body.getStatus());
    assertNull(body.getTokenTtlSec());
    assertNull(body.getDescription());
    assertFalse(body.isAllowHeadlessExchange());
    assertEquals(Collections.singleton("alice"), body.getCanActForUsers());
    assertTrue(body.getCanActForGroups().isEmpty());
    assertTrue(body.getResourcePolicy().isEmpty());
    assertEquals(OPERATOR, body.getCreatedBy());
    assertNotNull(body.getCreatedAt());
    assertNotNull(body.getUpdatedAt());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterAllFieldsRoundTrip() throws Exception {
    final Map<String, Object> fields = allFields();
    // Client-supplied server-managed fields must be ignored.
    fields.put("registrationId", "client-supplied-id");
    fields.put("createdBy", "client-supplied-by");
    fields.put("createdAt", "2000-01-01T00:00:00Z");
    fields.put("updatedAt", "2000-01-01T00:00:00Z");

    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.register(EasyMock.capture(captured)))
        .andAnswer(() -> withRegistrationId(captured.getValue(), REGISTRATION_ID));
    expectAudit(REGISTRATION_ID, ActionOutcome.SUCCESS, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.register(toJson(fields));

    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    final DelegationPolicyResponse body = parseResponse(response);
    assertEquals(REGISTRATION_ID, body.getRegistrationId());
    assertNotEquals("client-supplied-by", body.getCreatedBy());
    assertEquals(OPERATOR, body.getCreatedBy());
    assertEquals(ACTOR_AUTHORITY, body.getActorAuthority());
    assertEquals(ACTOR_ID, body.getActorId());
    assertEquals("My Policy", body.getName());
    assertEquals("revoked", body.getStatus());
    assertEquals(Integer.valueOf(1800), body.getTokenTtlSec());
    assertEquals("desc", body.getDescription());
    assertTrue(body.isAllowHeadlessExchange());
    assertEquals(new LinkedHashSet<>(java.util.Arrays.asList("alice", "bob")), body.getCanActForUsers());
    assertEquals(Collections.singleton("team-a"), body.getCanActForGroups());
    assertEquals(2, body.getResourcePolicy().size());
    assertTrue(body.getResourcePolicy().get("https://x.com").isEmpty());
    assertEquals(Collections.singleton("read"), body.getResourcePolicy().get("https://y.com"));
    assertNotNull(body.getCreatedAt());
    assertNotNull(body.getUpdatedAt());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterUnknownFieldIgnored() throws Exception {
    final Map<String, Object> fields = minimalFields();
    fields.put("somethingUnexpected", "value");
    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.register(EasyMock.capture(captured)))
        .andAnswer(() -> withRegistrationId(captured.getValue(), REGISTRATION_ID));
    expectAudit(REGISTRATION_ID, ActionOutcome.SUCCESS, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.CREATED.getStatusCode(),
        resource.register(toJson(fields)).getStatus());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterDuplicateEntriesCollapseViaSet() throws Exception {
    final Map<String, Object> fields = minimalFields();
    fields.put("canActForUsers", java.util.Arrays.asList("alice", "alice"));
    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.register(EasyMock.capture(captured)))
        .andAnswer(() -> withRegistrationId(captured.getValue(), REGISTRATION_ID));
    expectAudit(REGISTRATION_ID, ActionOutcome.SUCCESS, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    resource.register(toJson(fields));

    assertEquals(1, captured.getValue().getCanActForUsers().size());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterNullElementInCanActForUsersFiltered() throws Exception {
    // JSON null elements in arrays are filtered out rather than causing a 500 storage_error.
    final Map<String, Object> fields = minimalFields();
    fields.put("canActForUsers", java.util.Arrays.asList("alice", null));
    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.register(EasyMock.capture(captured)))
        .andAnswer(() -> withRegistrationId(captured.getValue(), REGISTRATION_ID));
    expectAudit(REGISTRATION_ID, ActionOutcome.SUCCESS, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.register(toJson(fields));

    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    assertEquals(Collections.singleton("alice"), captured.getValue().getCanActForUsers());
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // POST - boundary values: actorAuthority / actorId
  // ---------------------------------------------------------------------------

  @Test
  public void testRegisterMissingActorAuthority() throws Exception {
    assertRegisterRejected(fieldsWithout("actorAuthority"));
  }

  @Test
  public void testRegisterNullActorAuthority() throws Exception {
    assertRegisterRejected(fieldsWith("actorAuthority", null));
  }

  @Test
  public void testRegisterEmptyActorAuthority() throws Exception {
    assertRegisterRejected(fieldsWith("actorAuthority", ""));
  }

  @Test
  public void testRegisterWhitespaceActorAuthority() throws Exception {
    assertRegisterRejected(fieldsWith("actorAuthority", "   "));
  }

  @Test
  public void testRegisterMissingActorId() throws Exception {
    assertRegisterRejected(fieldsWithout("actorId"));
  }

  @Test
  public void testRegisterNullActorId() throws Exception {
    assertRegisterRejected(fieldsWith("actorId", null));
  }

  @Test
  public void testRegisterEmptyActorId() throws Exception {
    assertRegisterRejected(fieldsWith("actorId", ""));
  }

  @Test
  public void testRegisterWhitespaceActorId() throws Exception {
    assertRegisterRejected(fieldsWith("actorId", "   "));
  }

  // ---------------------------------------------------------------------------
  // POST - boundary values: name / description
  // ---------------------------------------------------------------------------

  @Test
  public void testRegisterNameOmittedIsNull() throws Exception {
    assertEquals(null, registerAndGetField(fieldsWithout("name"), DelegationPolicyResponse::getName));
  }

  @Test
  public void testRegisterEmptyNameNormalizedToNull() throws Exception {
    assertEquals(null, registerAndGetField(fieldsWith("name", ""), DelegationPolicyResponse::getName));
  }

  @Test
  public void testRegisterRealNameStored() throws Exception {
    assertEquals("My Policy", registerAndGetField(fieldsWith("name", "My Policy"), DelegationPolicyResponse::getName));
  }

  @Test
  public void testRegisterDescriptionOmittedIsNull() throws Exception {
    assertEquals(null, registerAndGetField(fieldsWithout("description"), DelegationPolicyResponse::getDescription));
  }

  @Test
  public void testRegisterEmptyDescriptionNormalizedToNull() throws Exception {
    assertEquals(null, registerAndGetField(fieldsWith("description", ""), DelegationPolicyResponse::getDescription));
  }

  @Test
  public void testRegisterRealDescriptionStored() throws Exception {
    assertEquals("desc", registerAndGetField(fieldsWith("description", "desc"), DelegationPolicyResponse::getDescription));
  }

  // ---------------------------------------------------------------------------
  // POST - boundary values: status
  // ---------------------------------------------------------------------------

  @Test
  public void testRegisterStatusOmittedDefaultsToActive() throws Exception {
    assertEquals("active", registerAndGetField(fieldsWithout("status"), DelegationPolicyResponse::getStatus));
  }

  @Test
  public void testRegisterEmptyStatusDefaultsToActive() throws Exception {
    assertEquals("active", registerAndGetField(fieldsWith("status", ""), DelegationPolicyResponse::getStatus));
  }

  @Test
  public void testRegisterExplicitActiveStatus() throws Exception {
    assertEquals("active", registerAndGetField(fieldsWith("status", "active"), DelegationPolicyResponse::getStatus));
  }

  @Test
  public void testRegisterExplicitRevokedStatus() throws Exception {
    assertEquals("revoked", registerAndGetField(fieldsWith("status", "revoked"), DelegationPolicyResponse::getStatus));
  }

  @Test
  public void testRegisterInvalidStatusValueRejected() throws Exception {
    assertRegisterRejected(fieldsWith("status", "bogus"));
  }

  @Test
  public void testRegisterWrongCaseStatusRejected() throws Exception {
    assertRegisterRejected(fieldsWith("status", "Active"));
  }

  // ---------------------------------------------------------------------------
  // POST - boundary values: tokenTtlSec
  // ---------------------------------------------------------------------------

  @Test
  public void testRegisterTokenTtlAbsentAccepted() throws Exception {
    assertEquals(null, registerAndGetField(fieldsWithout("tokenTtlSec"), DelegationPolicyResponse::getTokenTtlSec));
  }

  @Test
  public void testRegisterTokenTtlAtMinAccepted() throws Exception {
    assertEquals(Integer.valueOf(MIN_TTL),
        registerAndGetField(fieldsWith("tokenTtlSec", MIN_TTL), DelegationPolicyResponse::getTokenTtlSec));
  }

  @Test
  public void testRegisterTokenTtlAtMaxAccepted() throws Exception {
    assertEquals(Integer.valueOf(MAX_TTL),
        registerAndGetField(fieldsWith("tokenTtlSec", MAX_TTL), DelegationPolicyResponse::getTokenTtlSec));
  }

  @Test
  public void testRegisterTokenTtlBelowMinRejected() throws Exception {
    assertRegisterRejected(fieldsWith("tokenTtlSec", MIN_TTL - 1));
  }

  @Test
  public void testRegisterTokenTtlAboveMaxRejected() throws Exception {
    assertRegisterRejected(fieldsWith("tokenTtlSec", MAX_TTL + 1));
  }

  @Test
  public void testRegisterTokenTtlNegativeRejected() throws Exception {
    assertRegisterRejected(fieldsWith("tokenTtlSec", -1));
  }

  @Test
  public void testRegisterTokenTtlZeroRejected() throws Exception {
    assertRegisterRejected(fieldsWith("tokenTtlSec", 0));
  }

  @Test
  public void testRegisterTokenTtlWrongTypeReturnsBadRequest() {
    expectAuditAnyId(ActionOutcome.FAILURE, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.register(
        "{\"actorAuthority\":\"" + ACTOR_AUTHORITY + "\",\"actorId\":\"" + ACTOR_ID
            + "\",\"canActForUsers\":[\"alice\"],\"tokenTtlSec\":\"not-a-number\"}");

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertErrorField(response, "invalid_request");
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // POST - boundary values: allowHeadlessExchange
  // ---------------------------------------------------------------------------

  @Test
  public void testRegisterAllowHeadlessExchangeOmittedDefaultsFalse() throws Exception {
    assertEquals(false, registerAndGetField(fieldsWithout("allowHeadlessExchange"), DelegationPolicyResponse::isAllowHeadlessExchange));
  }

  @Test
  public void testRegisterAllowHeadlessExchangeTruePersisted() throws Exception {
    assertEquals(true, registerAndGetField(fieldsWith("allowHeadlessExchange", true), DelegationPolicyResponse::isAllowHeadlessExchange));
  }

  // ---------------------------------------------------------------------------
  // POST - boundary values: canActForUsers / canActForGroups non-empty rule
  // ---------------------------------------------------------------------------

  @Test
  public void testRegisterBothCanActForOmittedRejected() throws Exception {
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("actorAuthority", ACTOR_AUTHORITY);
    fields.put("actorId", ACTOR_ID);
    assertRegisterRejected(fields);
  }

  @Test
  public void testRegisterBothCanActForExplicitEmptyRejected() throws Exception {
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("actorAuthority", ACTOR_AUTHORITY);
    fields.put("actorId", ACTOR_ID);
    fields.put("canActForUsers", Collections.emptySet());
    fields.put("canActForGroups", Collections.emptySet());
    assertRegisterRejected(fields);
  }

  @Test
  public void testRegisterOnlyCanActForUsersAccepted() throws Exception {
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("actorAuthority", ACTOR_AUTHORITY);
    fields.put("actorId", ACTOR_ID);
    fields.put("canActForUsers", Collections.singleton("alice"));
    assertEquals(Collections.singleton("alice"), registerAndGetField(fields, DelegationPolicyResponse::getCanActForUsers));
  }

  @Test
  public void testRegisterOnlyCanActForGroupsAccepted() throws Exception {
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("actorAuthority", ACTOR_AUTHORITY);
    fields.put("actorId", ACTOR_ID);
    fields.put("canActForGroups", Collections.singleton("team-a"));
    assertEquals(Collections.singleton("team-a"), registerAndGetField(fields, DelegationPolicyResponse::getCanActForGroups));
  }

  @Test
  public void testRegisterBothCanActForNonEmptyAccepted() throws Exception {
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("actorAuthority", ACTOR_AUTHORITY);
    fields.put("actorId", ACTOR_ID);
    fields.put("canActForUsers", Collections.singleton("alice"));
    fields.put("canActForGroups", Collections.singleton("team-a"));
    assertEquals(Collections.singleton("alice"), registerAndGetField(fields, DelegationPolicyResponse::getCanActForUsers));
  }

  // ---------------------------------------------------------------------------
  // POST - boundary values: resourcePolicy
  // ---------------------------------------------------------------------------

  @Test
  public void testRegisterResourcePolicyOmittedIsEmptyMap() throws Exception {
    assertTrue(registerAndGetField(fieldsWithout("resourcePolicy"), DelegationPolicyResponse::getResourcePolicy).isEmpty());
  }

  @Test
  public void testRegisterResourcePolicyExplicitEmptyObjectIsEmptyMap() throws Exception {
    assertTrue(registerAndGetField(fieldsWith("resourcePolicy", Collections.emptyMap()), DelegationPolicyResponse::getResourcePolicy).isEmpty());
  }

  @Test
  public void testRegisterResourcePolicyEmptyArrayMeansAllScopes() throws Exception {
    final Map<String, Object> rp = new LinkedHashMap<>();
    rp.put("https://x.com", Collections.emptySet());
    final Map<String, Set<String>> result = registerAndGetField(fieldsWith("resourcePolicy", rp), DelegationPolicyResponse::getResourcePolicy);
    assertTrue(result.containsKey("https://x.com"));
    assertTrue(result.get("https://x.com").isEmpty());
  }

  @Test
  public void testRegisterResourcePolicyNonEmptyArrayRestrictsScopes() throws Exception {
    final Map<String, Object> rp = new LinkedHashMap<>();
    rp.put("https://y.com", Collections.singleton("read"));
    final Map<String, Set<String>> result = registerAndGetField(fieldsWith("resourcePolicy", rp), DelegationPolicyResponse::getResourcePolicy);
    assertEquals(Collections.singleton("read"), result.get("https://y.com"));
  }

  @Test
  public void testRegisterResourcePolicyMixedEntries() throws Exception {
    final Map<String, Object> rp = new LinkedHashMap<>();
    rp.put("https://x.com", Collections.emptySet());
    rp.put("https://y.com", Collections.singleton("read"));
    final Map<String, Set<String>> result = registerAndGetField(fieldsWith("resourcePolicy", rp), DelegationPolicyResponse::getResourcePolicy);
    assertEquals(2, result.size());
    assertTrue(result.get("https://x.com").isEmpty());
    assertEquals(Collections.singleton("read"), result.get("https://y.com"));
  }

  // ---------------------------------------------------------------------------
  // POST - malformed body / negative / error paths
  // ---------------------------------------------------------------------------

  @Test
  public void testRegisterInvalidJson() {
    expectAudit("INVALID_REQUEST", ActionOutcome.FAILURE, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(),
        resource.register("{ not valid json }").getStatus());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterDuplicateActor() throws Exception {
    EasyMock.expect(mockService.register(EasyMock.anyObject(DelegationPolicy.class)))
        .andThrow(new DelegationPolicyAlreadyExistsException(ACTOR_AUTHORITY, ACTOR_ID, new RuntimeException("dup")))
        .once();
    expectAudit(ACTOR_AUTHORITY + "/" + ACTOR_ID, ActionOutcome.FAILURE, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.register(toJson(minimalFields()));

    assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
    assertErrorField(response, "actor_exists");
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testAuditRegisterStorageFailure() throws Exception {
    EasyMock.expect(mockService.register(EasyMock.anyObject(DelegationPolicy.class)))
        .andThrow(new RuntimeException("DB error"))
        .once();
    expectAudit(ACTOR_AUTHORITY + "/" + ACTOR_ID, ActionOutcome.FAILURE, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.register(toJson(minimalFields()));

    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertErrorField(response, "storage_error");
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterNullPrincipalAuditsAnonymous() throws Exception {
    final DelegationPolicyResource res = buildResource(null);
    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.register(EasyMock.capture(captured)))
        .andAnswer(() -> withRegistrationId(captured.getValue(), REGISTRATION_ID));
    mockAuditor.audit(
        EasyMock.eq(Action.DELEGATION_LIFECYCLE), EasyMock.eq(REGISTRATION_ID),
        EasyMock.eq(ResourceType.DELEGATION_POLICY), EasyMock.eq(ActionOutcome.SUCCESS),
        EasyMock.contains("performed_by=ANONYMOUS"));
    EasyMock.expectLastCall().once();
    EasyMock.replay(mockService, mockAuditor);

    final Response response = res.register(toJson(minimalFields()));

    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    assertNull(captured.getValue().getCreatedBy());
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // PUT (registerOrUpdate)
  // ---------------------------------------------------------------------------

  @Test
  public void testRegisterOrUpdateCreatesNewActor() throws Exception {
    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.registerOrUpdate(EasyMock.capture(captured)))
        .andAnswer(() -> new RegisterOrUpdateResult(withRegistrationId(captured.getValue(), REGISTRATION_ID), true));
    expectAudit(REGISTRATION_ID, ActionOutcome.SUCCESS, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.registerOrUpdate(toJson(minimalFields()));

    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    final DelegationPolicyResponse body = parseResponse(response);
    assertEquals(REGISTRATION_ID, body.getRegistrationId());
    assertEquals(ACTOR_AUTHORITY, body.getActorAuthority());
    assertEquals(ACTOR_ID, body.getActorId());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterOrUpdateReplacesExistingActor() throws Exception {
    final Instant originalCreatedAt = Instant.now().minusSeconds(3600);
    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.registerOrUpdate(EasyMock.capture(captured)))
        .andAnswer(() -> new RegisterOrUpdateResult(
            withCreationMetadata(withRegistrationId(captured.getValue(), REGISTRATION_ID),
                "original-operator", originalCreatedAt, Instant.now()),
            false));
    expectAudit(REGISTRATION_ID, ActionOutcome.SUCCESS, "policy_updated");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.registerOrUpdate(toJson(minimalFields()));

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    final DelegationPolicyResponse body = parseResponse(response);
    assertEquals(REGISTRATION_ID, body.getRegistrationId());
    // createdBy/createdAt come back from the persisted row, not from the request.
    assertEquals("original-operator", body.getCreatedBy());
    assertEquals(originalCreatedAt, body.getCreatedAt());
    assertNotEquals(originalCreatedAt, body.getUpdatedAt());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterOrUpdateMissingActorAuthorityRejected() throws Exception {
    assertRegisterOrUpdateRejected(fieldsWithout("actorAuthority"));
  }

  @Test
  public void testRegisterOrUpdateBothCanActForEmptyRejected() throws Exception {
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("actorAuthority", ACTOR_AUTHORITY);
    fields.put("actorId", ACTOR_ID);
    assertRegisterOrUpdateRejected(fields);
  }

  @Test
  public void testRegisterOrUpdateTokenTtlOutOfBoundsRejected() throws Exception {
    assertRegisterOrUpdateRejected(fieldsWith("tokenTtlSec", MAX_TTL + 1));
  }

  @Test
  public void testRegisterOrUpdateInvalidStatusRejected() throws Exception {
    assertRegisterOrUpdateRejected(fieldsWith("status", "bogus"));
  }

  @Test
  public void testRegisterOrUpdateMalformedJson() {
    expectAudit("INVALID_REQUEST", ActionOutcome.FAILURE, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.registerOrUpdate("{ not valid json }");

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertErrorField(response, "invalid_request");
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterOrUpdateActorExistsConflict() throws Exception {
    EasyMock.expect(mockService.registerOrUpdate(EasyMock.anyObject(DelegationPolicy.class)))
        .andThrow(new DelegationPolicyAlreadyExistsException(ACTOR_AUTHORITY, ACTOR_ID, new RuntimeException("dup")))
        .once();
    expectAudit(ACTOR_AUTHORITY + "/" + ACTOR_ID, ActionOutcome.FAILURE, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.registerOrUpdate(toJson(minimalFields()));

    assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
    assertErrorField(response, "actor_exists");
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterOrUpdatePolicyNotFound() throws Exception {
    EasyMock.expect(mockService.registerOrUpdate(EasyMock.anyObject(DelegationPolicy.class)))
        .andThrow(new DelegationPolicyNotFoundException(REGISTRATION_ID))
        .once();
    expectAudit(ACTOR_AUTHORITY + "/" + ACTOR_ID, ActionOutcome.FAILURE, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.registerOrUpdate(toJson(minimalFields()));

    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    assertErrorField(response, "policy_not_found");
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterOrUpdateStorageFailure() throws Exception {
    EasyMock.expect(mockService.registerOrUpdate(EasyMock.anyObject(DelegationPolicy.class)))
        .andThrow(new RuntimeException("DB error"))
        .once();
    expectAudit(ACTOR_AUTHORITY + "/" + ACTOR_ID, ActionOutcome.FAILURE, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.registerOrUpdate(toJson(minimalFields()));

    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertErrorField(response, "storage_error");
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterOrUpdateNullPrincipalAuditsAnonymous() throws Exception {
    final DelegationPolicyResource res = buildResource(null);
    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.registerOrUpdate(EasyMock.capture(captured)))
        .andAnswer(() -> new RegisterOrUpdateResult(withRegistrationId(captured.getValue(), REGISTRATION_ID), true));
    mockAuditor.audit(
        EasyMock.eq(Action.DELEGATION_LIFECYCLE), EasyMock.eq(REGISTRATION_ID),
        EasyMock.eq(ResourceType.DELEGATION_POLICY), EasyMock.eq(ActionOutcome.SUCCESS),
        EasyMock.contains("performed_by=ANONYMOUS"));
    EasyMock.expectLastCall().once();
    EasyMock.replay(mockService, mockAuditor);

    final Response response = res.registerOrUpdate(toJson(minimalFields()));

    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    assertNull(captured.getValue().getCreatedBy());
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // PUT (registerOrUpdate) - actor field variants (mirror POST coverage)
  // ---------------------------------------------------------------------------

  @Test
  public void testRegisterOrUpdateNullActorAuthorityRejected() throws Exception {
    assertRegisterOrUpdateRejected(fieldsWith("actorAuthority", null));
  }

  @Test
  public void testRegisterOrUpdateEmptyActorAuthorityRejected() throws Exception {
    assertRegisterOrUpdateRejected(fieldsWith("actorAuthority", ""));
  }

  @Test
  public void testRegisterOrUpdateWhitespaceActorAuthorityRejected() throws Exception {
    assertRegisterOrUpdateRejected(fieldsWith("actorAuthority", "   "));
  }

  @Test
  public void testRegisterOrUpdateMissingActorIdRejected() throws Exception {
    assertRegisterOrUpdateRejected(fieldsWithout("actorId"));
  }

  @Test
  public void testRegisterOrUpdateNullActorIdRejected() throws Exception {
    assertRegisterOrUpdateRejected(fieldsWith("actorId", null));
  }

  @Test
  public void testRegisterOrUpdateEmptyActorIdRejected() throws Exception {
    assertRegisterOrUpdateRejected(fieldsWith("actorId", ""));
  }

  @Test
  public void testRegisterOrUpdateWhitespaceActorIdRejected() throws Exception {
    assertRegisterOrUpdateRejected(fieldsWith("actorId", "   "));
  }

  // ---------------------------------------------------------------------------
  // PUT (registerOrUpdate) - status / tokenTtlSec boundary values
  // ---------------------------------------------------------------------------

  @Test
  public void testRegisterOrUpdateWrongCaseStatusRejected() throws Exception {
    assertRegisterOrUpdateRejected(fieldsWith("status", "Active"));
  }

  @Test
  public void testRegisterOrUpdateTokenTtlNegativeRejected() throws Exception {
    assertRegisterOrUpdateRejected(fieldsWith("tokenTtlSec", -1));
  }

  @Test
  public void testRegisterOrUpdateTokenTtlZeroRejected() throws Exception {
    assertRegisterOrUpdateRejected(fieldsWith("tokenTtlSec", 0));
  }

  @Test
  public void testRegisterOrUpdateTokenTtlWrongTypeReturnsBadRequest() {
    expectAuditAnyId(ActionOutcome.FAILURE, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.registerOrUpdate(
        "{\"actorAuthority\":\"" + ACTOR_AUTHORITY + "\",\"actorId\":\"" + ACTOR_ID
            + "\",\"canActForUsers\":[\"alice\"],\"tokenTtlSec\":\"not-a-number\"}");

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertErrorField(response, "invalid_request");
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // GET (list)
  // ---------------------------------------------------------------------------

  @Test
  public void testListNoFilter() throws Exception {
    EasyMock.expect(mockService.list(null))
        .andReturn(new DelegationPolicyList(Collections.singletonList(existingPolicy(REGISTRATION_ID, Instant.now())), false))
        .once();
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.list(null);

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    final DelegationPolicyListResponse body = parseListResponse(response);
    assertEquals(1, body.getPolicies().size());
    assertFalse(body.isHasMore());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testListWithFilter() throws Exception {
    EasyMock.expect(mockService.list(ACTOR_AUTHORITY))
        .andReturn(new DelegationPolicyList(Collections.emptyList(), false))
        .once();
    EasyMock.replay(mockService, mockAuditor);

    resource.list(ACTOR_AUTHORITY);

    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testListEmptyFilterTreatedAsNoFilter() throws Exception {
    EasyMock.expect(mockService.list(null))
        .andReturn(new DelegationPolicyList(Collections.emptyList(), false))
        .once();
    EasyMock.replay(mockService, mockAuditor);

    resource.list("");

    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testListReturnsEmptyArray() throws Exception {
    EasyMock.expect(mockService.list(null))
        .andReturn(new DelegationPolicyList(Collections.emptyList(), false))
        .once();
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.list(null);

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    final DelegationPolicyListResponse body = parseListResponse(response);
    assertTrue(body.getPolicies().isEmpty());
    assertFalse(body.isHasMore());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testListHasMoreSurfaced() throws Exception {
    EasyMock.expect(mockService.list(null))
        .andReturn(new DelegationPolicyList(Collections.emptyList(), true))
        .once();
    EasyMock.replay(mockService, mockAuditor);

    final DelegationPolicyListResponse body = parseListResponse(resource.list(null));

    assertTrue(body.isHasMore());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testListStorageFailure() {
    EasyMock.expect(mockService.list(null)).andThrow(new RuntimeException("DB error")).once();
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.list(null);

    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertErrorField(response, "storage_error");
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // GET (one)
  // ---------------------------------------------------------------------------

  @Test
  public void testGetOneFound() throws Exception {
    final DelegationPolicy stored = existingPolicy(REGISTRATION_ID, Instant.now());
    EasyMock.expect(mockService.get(REGISTRATION_ID)).andReturn(Optional.of(stored)).once();
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.getOne(REGISTRATION_ID);

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(REGISTRATION_ID, parseResponse(response).getRegistrationId());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testGetOneNotFound() {
    EasyMock.expect(mockService.get(REGISTRATION_ID)).andReturn(Optional.empty()).once();
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.getOne(REGISTRATION_ID);

    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    assertErrorField(response, "policy_not_found");
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testGetOneStorageFailure() {
    EasyMock.expect(mockService.get(REGISTRATION_ID)).andThrow(new RuntimeException("DB error")).once();
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.getOne(REGISTRATION_ID);

    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertErrorField(response, "storage_error");
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // PUT (update)
  // ---------------------------------------------------------------------------

  @Test
  public void testUpdateFullReplaceResetsOmittedFields() throws Exception {
    final Instant originalCreatedAt = Instant.now().minusSeconds(3600);
    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.update(EasyMock.eq(REGISTRATION_ID), EasyMock.capture(captured)))
        .andAnswer(() -> withCreationMetadata(captured.getValue(), "original-operator", originalCreatedAt, Instant.now()));
    expectAudit(REGISTRATION_ID, ActionOutcome.SUCCESS, "policy_updated");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.update(REGISTRATION_ID, toJson(minimalFields()));

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    final DelegationPolicyResponse body = parseResponse(response);
    assertFalse(body.isAllowHeadlessExchange());
    assertEquals("active", body.getStatus());
    assertTrue(body.getCanActForGroups().isEmpty());
    // createdBy/createdAt come back from the persisted row, not from the request --
    // the resource never controls these on update.
    assertEquals(originalCreatedAt, body.getCreatedAt());
    assertEquals("original-operator", body.getCreatedBy());
    assertNotEquals(originalCreatedAt, body.getUpdatedAt());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testUpdateIgnoresClientSuppliedServerManagedFields() throws Exception {
    final Instant originalCreatedAt = Instant.now().minusSeconds(3600);
    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.update(EasyMock.eq(REGISTRATION_ID), EasyMock.capture(captured)))
        .andAnswer(() -> withCreationMetadata(captured.getValue(), "original-operator", originalCreatedAt, Instant.now()));
    expectAudit(REGISTRATION_ID, ActionOutcome.SUCCESS, "policy_updated");
    EasyMock.replay(mockService, mockAuditor);

    final Map<String, Object> fields = minimalFields();
    fields.put("registrationId", "client-id");
    fields.put("createdBy", "client-by");
    fields.put("createdAt", "2000-01-01T00:00:00Z");

    final Response response = resource.update(REGISTRATION_ID, toJson(fields));

    assertEquals(REGISTRATION_ID, captured.getValue().getRegistrationId());
    final DelegationPolicyResponse body = parseResponse(response);
    assertEquals("original-operator", body.getCreatedBy());
    assertEquals(originalCreatedAt, body.getCreatedAt());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testUpdateMissingActorAuthorityRejected() throws Exception {
    assertUpdateRejected(fieldsWithout("actorAuthority"));
  }

  @Test
  public void testUpdateBlankActorIdRejected() throws Exception {
    assertUpdateRejected(fieldsWith("actorId", "   "));
  }

  @Test
  public void testUpdateBothCanActForEmptyRejected() throws Exception {
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("actorAuthority", ACTOR_AUTHORITY);
    fields.put("actorId", ACTOR_ID);
    assertUpdateRejected(fields);
  }

  @Test
  public void testUpdateTokenTtlOutOfBoundsRejected() throws Exception {
    assertUpdateRejected(fieldsWith("tokenTtlSec", MAX_TTL + 1));
  }

  @Test
  public void testUpdateInvalidStatusRejected() throws Exception {
    assertUpdateRejected(fieldsWith("status", "bogus"));
  }

  @Test
  public void testUpdateMalformedJson() {
    expectAudit(REGISTRATION_ID, ActionOutcome.FAILURE, "policy_updated");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.update(REGISTRATION_ID, "{ not valid json }");

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertErrorField(response, "invalid_request");
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testUpdateNotFound() throws Exception {
    EasyMock.expect(mockService.update(EasyMock.eq(REGISTRATION_ID), EasyMock.anyObject(DelegationPolicy.class)))
        .andThrow(new DelegationPolicyNotFoundException(REGISTRATION_ID)).once();
    expectAudit(REGISTRATION_ID, ActionOutcome.FAILURE, "policy_updated");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.update(REGISTRATION_ID, toJson(minimalFields()));

    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    assertErrorField(response, "policy_not_found");
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testUpdateStorageFailure() throws Exception {
    EasyMock.expect(mockService.update(EasyMock.eq(REGISTRATION_ID), EasyMock.anyObject(DelegationPolicy.class)))
        .andThrow(new RuntimeException("DB error")).once();
    expectAudit(REGISTRATION_ID, ActionOutcome.FAILURE, "policy_updated");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.update(REGISTRATION_ID, toJson(minimalFields()));

    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertErrorField(response, "storage_error");
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // PUT (update) - actor field variants (mirror POST coverage per spec)
  // ---------------------------------------------------------------------------

  @Test
  public void testUpdateNullActorAuthorityRejected() throws Exception {
    assertUpdateRejected(fieldsWith("actorAuthority", null));
  }

  @Test
  public void testUpdateEmptyActorAuthorityRejected() throws Exception {
    assertUpdateRejected(fieldsWith("actorAuthority", ""));
  }

  @Test
  public void testUpdateWhitespaceActorAuthorityRejected() throws Exception {
    assertUpdateRejected(fieldsWith("actorAuthority", "   "));
  }

  @Test
  public void testUpdateMissingActorIdRejected() throws Exception {
    assertUpdateRejected(fieldsWithout("actorId"));
  }

  @Test
  public void testUpdateNullActorIdRejected() throws Exception {
    assertUpdateRejected(fieldsWith("actorId", null));
  }

  @Test
  public void testUpdateEmptyActorIdRejected() throws Exception {
    assertUpdateRejected(fieldsWith("actorId", ""));
  }

  // ---------------------------------------------------------------------------
  // PUT (update) - status / tokenTtlSec boundary values (mirror POST coverage)
  // ---------------------------------------------------------------------------

  @Test
  public void testUpdateWrongCaseStatusRejected() throws Exception {
    assertUpdateRejected(fieldsWith("status", "Active"));
  }

  @Test
  public void testUpdateTokenTtlAtMinAccepted() throws Exception {
    assertEquals(Integer.valueOf(MIN_TTL),
        updateAndGetField(fieldsWith("tokenTtlSec", MIN_TTL), DelegationPolicyResponse::getTokenTtlSec));
  }

  @Test
  public void testUpdateTokenTtlAtMaxAccepted() throws Exception {
    assertEquals(Integer.valueOf(MAX_TTL),
        updateAndGetField(fieldsWith("tokenTtlSec", MAX_TTL), DelegationPolicyResponse::getTokenTtlSec));
  }

  @Test
  public void testUpdateTokenTtlNegativeRejected() throws Exception {
    assertUpdateRejected(fieldsWith("tokenTtlSec", -1));
  }

  @Test
  public void testUpdateTokenTtlZeroRejected() throws Exception {
    assertUpdateRejected(fieldsWith("tokenTtlSec", 0));
  }

  @Test
  public void testUpdateTokenTtlWrongTypeReturnsBadRequest() {
    expectAudit(REGISTRATION_ID, ActionOutcome.FAILURE, "policy_updated");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.update(REGISTRATION_ID,
        "{\"actorAuthority\":\"" + ACTOR_AUTHORITY + "\",\"actorId\":\"" + ACTOR_ID
            + "\",\"canActForUsers\":[\"alice\"],\"tokenTtlSec\":\"not-a-number\"}");

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertErrorField(response, "invalid_request");
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // DELETE
  // ---------------------------------------------------------------------------

  @Test
  public void testDeleteExisting() {
    mockService.delete(REGISTRATION_ID);
    EasyMock.expectLastCall().once();
    expectAudit(REGISTRATION_ID, ActionOutcome.SUCCESS, "policy_deleted");
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), resource.delete(REGISTRATION_ID).getStatus());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testDeleteNotFound() {
    mockService.delete(REGISTRATION_ID);
    EasyMock.expectLastCall().andThrow(new DelegationPolicyNotFoundException(REGISTRATION_ID)).once();
    expectAudit(REGISTRATION_ID, ActionOutcome.FAILURE, "policy_deleted");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.delete(REGISTRATION_ID);

    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    assertErrorField(response, "policy_not_found");
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testDeleteStorageFailure() {
    mockService.delete(REGISTRATION_ID);
    EasyMock.expectLastCall().andThrow(new RuntimeException("DB error")).once();
    expectAudit(REGISTRATION_ID, ActionOutcome.FAILURE, "policy_deleted");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.delete(REGISTRATION_ID);

    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertErrorField(response, "storage_error");
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testDeleteNullPrincipalAuditsAnonymous() throws Exception {
    final DelegationPolicyResource res = buildResource(null);
    mockService.delete(REGISTRATION_ID);
    EasyMock.expectLastCall().once();
    mockAuditor.audit(
        EasyMock.eq(Action.DELEGATION_LIFECYCLE), EasyMock.eq(REGISTRATION_ID),
        EasyMock.eq(ResourceType.DELEGATION_POLICY), EasyMock.eq(ActionOutcome.SUCCESS),
        EasyMock.contains("performed_by=ANONYMOUS"));
    EasyMock.expectLastCall().once();
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), res.delete(REGISTRATION_ID).getStatus());
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // Cross-cutting
  // ---------------------------------------------------------------------------

  @Test
  public void testInstantFieldsSerializeAsIso8601() throws Exception {
    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.register(EasyMock.capture(captured)))
        .andAnswer(() -> withRegistrationId(captured.getValue(), REGISTRATION_ID));
    expectAudit(REGISTRATION_ID, ActionOutcome.SUCCESS, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.register(toJson(minimalFields()));
    final String rawJson = (String) response.getEntity();

    assertTrue("createdAt should be ISO-8601: " + rawJson,
        rawJson.replaceAll("\\s", "").matches(".*\"createdAt\":\"" + ISO_INSTANT_PATTERN + "\".*"));
    assertTrue("updatedAt should be ISO-8601: " + rawJson,
        rawJson.replaceAll("\\s", "").matches(".*\"updatedAt\":\"" + ISO_INSTANT_PATTERN + "\".*"));
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // @PostConstruct wiring
  // ---------------------------------------------------------------------------

  @Test
  public void testInitWiresServiceFromGatewayServices() throws Exception {
    final DelegationPolicyService svc = EasyMock.createNiceMock(DelegationPolicyService.class);
    EasyMock.replay(svc);

    final GatewayServices gws = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gws.getService(ServiceType.DELEGATION_POLICY_SERVICE)).andReturn(svc).once();
    EasyMock.replay(gws);

    final ServletContext ctx = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(ctx.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gws).once();
    EasyMock.expect(ctx.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(null).once();
    EasyMock.replay(ctx);

    final DelegationPolicyResource res = new DelegationPolicyResource();
    injectField(res, "servletContext", ctx);
    injectField(res, "request", buildRequest(buildPrincipal(OPERATOR)));
    res.init();

    assertEquals(MIN_TTL, ((Number) readField(res, "minTokenTtlSec")).intValue());
    assertEquals(MAX_TTL, ((Number) readField(res, "maxTokenTtlSec")).intValue());
    EasyMock.verify(gws, ctx);
  }

  @Test
  public void testInitWiresTokenTtlBoundsFromGatewayConfig() throws Exception {
    final DelegationPolicyService svc = EasyMock.createNiceMock(DelegationPolicyService.class);
    EasyMock.replay(svc);

    final GatewayServices gws = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gws.getService(ServiceType.DELEGATION_POLICY_SERVICE)).andReturn(svc).once();
    EasyMock.replay(gws);

    final GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getDelegationServiceMinTokenTtlSec()).andReturn(120).once();
    EasyMock.expect(config.getDelegationServiceMaxTokenTtlSec()).andReturn(7200).once();
    EasyMock.replay(config);

    final ServletContext ctx = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(ctx.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gws).once();
    EasyMock.expect(ctx.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(config).once();
    EasyMock.replay(ctx);

    final DelegationPolicyResource res = new DelegationPolicyResource();
    injectField(res, "servletContext", ctx);
    injectField(res, "request", buildRequest(buildPrincipal(OPERATOR)));
    res.init();

    assertEquals(120, ((Number) readField(res, "minTokenTtlSec")).intValue());
    assertEquals(7200, ((Number) readField(res, "maxTokenTtlSec")).intValue());
    EasyMock.verify(gws, ctx, config);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private DelegationPolicyResource buildResource(Principal principal) throws Exception {
    final DelegationPolicyResource res = new DelegationPolicyResource();
    injectField(res, "request", buildRequest(principal));
    injectField(res, "policyService", mockService);
    return res;
  }

  private HttpServletRequest buildRequest(Principal principal) {
    final HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(req.getUserPrincipal()).andReturn(principal).anyTimes();
    EasyMock.replay(req);
    return req;
  }

  private Principal buildPrincipal(String name) {
    if (name == null) {
      return null;
    }
    final Principal p = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(p.getName()).andReturn(name).anyTimes();
    EasyMock.replay(p);
    return p;
  }

  private void expectAudit(String registrationId, String outcome, String eventType) {
    mockAuditor.audit(
        EasyMock.eq(Action.DELEGATION_LIFECYCLE),
        EasyMock.eq(registrationId),
        EasyMock.eq(ResourceType.DELEGATION_POLICY),
        EasyMock.eq(outcome),
        EasyMock.contains(eventType));
    EasyMock.expectLastCall().once();
  }

  private void expectAuditAnyId(String outcome, String eventType) {
    mockAuditor.audit(
        EasyMock.eq(Action.DELEGATION_LIFECYCLE),
        EasyMock.anyString(),
        EasyMock.eq(ResourceType.DELEGATION_POLICY),
        EasyMock.eq(outcome),
        EasyMock.contains(eventType));
    EasyMock.expectLastCall().once();
  }

  private void assertRegisterRejected(Map<String, Object> fields) throws Exception {
    expectAuditAnyId(ActionOutcome.FAILURE, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.register(toJson(fields));

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertErrorField(response, "invalid_request");
    EasyMock.verify(mockService, mockAuditor);
  }

  private void assertUpdateRejected(Map<String, Object> fields) throws Exception {
    expectAudit(REGISTRATION_ID, ActionOutcome.FAILURE, "policy_updated");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.update(REGISTRATION_ID, toJson(fields));

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertErrorField(response, "invalid_request");
    EasyMock.verify(mockService, mockAuditor);
  }

  private void assertRegisterOrUpdateRejected(Map<String, Object> fields) throws Exception {
    expectAuditAnyId(ActionOutcome.FAILURE, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.registerOrUpdate(toJson(fields));

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertErrorField(response, "invalid_request");
    EasyMock.verify(mockService, mockAuditor);
  }

  private <T> T registerAndGetField(Map<String, Object> fields, java.util.function.Function<DelegationPolicyResponse, T> extractor) throws Exception {
    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.register(EasyMock.capture(captured)))
        .andAnswer(() -> withRegistrationId(captured.getValue(), REGISTRATION_ID));
    expectAuditAnyId(ActionOutcome.SUCCESS, "policy_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.register(toJson(fields));
    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    final T result = extractor.apply(parseResponse(response));
    EasyMock.verify(mockService, mockAuditor);
    return result;
  }

  private <T> T updateAndGetField(Map<String, Object> fields, java.util.function.Function<DelegationPolicyResponse, T> extractor) throws Exception {
    final Capture<DelegationPolicy> captured = EasyMock.newCapture();
    EasyMock.expect(mockService.update(EasyMock.eq(REGISTRATION_ID), EasyMock.capture(captured)))
        .andAnswer(() -> withRegistrationId(captured.getValue(), REGISTRATION_ID));
    expectAudit(REGISTRATION_ID, ActionOutcome.SUCCESS, "policy_updated");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.update(REGISTRATION_ID, toJson(fields));
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    final T result = extractor.apply(parseResponse(response));
    EasyMock.verify(mockService, mockAuditor);
    return result;
  }

  private static Map<String, Object> minimalFields() {
    final Map<String, Object> m = new LinkedHashMap<>();
    m.put("actorAuthority", ACTOR_AUTHORITY);
    m.put("actorId", ACTOR_ID);
    m.put("canActForUsers", Collections.singleton("alice"));
    return m;
  }

  private static Map<String, Object> allFields() {
    final Map<String, Object> m = new LinkedHashMap<>();
    m.put("actorAuthority", ACTOR_AUTHORITY);
    m.put("actorId", ACTOR_ID);
    m.put("name", "My Policy");
    m.put("status", "revoked");
    m.put("tokenTtlSec", 1800);
    m.put("description", "desc");
    m.put("allowHeadlessExchange", true);
    m.put("canActForUsers", new LinkedHashSet<>(java.util.Arrays.asList("alice", "bob")));
    m.put("canActForGroups", Collections.singleton("team-a"));
    final Map<String, Object> rp = new LinkedHashMap<>();
    rp.put("https://x.com", Collections.emptySet());
    rp.put("https://y.com", Collections.singleton("read"));
    m.put("resourcePolicy", rp);
    return m;
  }

  private static Map<String, Object> fieldsWithout(String key) {
    final Map<String, Object> m = minimalFields();
    m.remove(key);
    return m;
  }

  private static Map<String, Object> fieldsWith(String key, Object value) {
    final Map<String, Object> m = minimalFields();
    m.put(key, value);
    return m;
  }

  private static DelegationPolicy existingPolicy(String registrationId, Instant createdAt) {
    return new DelegationPolicy(registrationId, ACTOR_AUTHORITY, ACTOR_ID, "old-name", "active", 1200,
        "old-desc", "original-operator", createdAt, createdAt, false,
        Collections.singleton("alice"), Collections.emptySet(), Collections.emptyMap());
  }

  private static DelegationPolicy withRegistrationId(DelegationPolicy p, String registrationId) {
    return new DelegationPolicy(registrationId, p.getActorAuthority(), p.getActorId(), p.getName(),
        p.getStatus(), p.getTokenTtlSec(), p.getDescription(), p.getCreatedBy(), p.getCreatedAt(),
        p.getUpdatedAt(), p.isAllowHeadlessExchange(), p.getCanActForUsers(), p.getCanActForGroups(),
        p.getResourcePolicy());
  }

  // Simulates what update() returns: the persisted row, with identity/creation fields as the
  // storage layer actually preserved them rather than whatever the request happened to carry.
  private static DelegationPolicy withCreationMetadata(
      DelegationPolicy p, String createdBy, Instant createdAt, Instant updatedAt) {
    return new DelegationPolicy(p.getRegistrationId(), p.getActorAuthority(), p.getActorId(), p.getName(),
        p.getStatus(), p.getTokenTtlSec(), p.getDescription(), createdBy, createdAt,
        updatedAt, p.isAllowHeadlessExchange(), p.getCanActForUsers(), p.getCanActForGroups(),
        p.getResourcePolicy());
  }

  private static String toJson(Map<String, Object> fields) {
    try {
      return MAPPER.writeValueAsString(fields);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static DelegationPolicyResponse parseResponse(Response response) throws Exception {
    return MAPPER.readValue((String) response.getEntity(), DelegationPolicyResponse.class);
  }

  private static DelegationPolicyListResponse parseListResponse(Response response) throws Exception {
    return MAPPER.readValue((String) response.getEntity(), DelegationPolicyListResponse.class);
  }

  private static void assertErrorField(Response response, String expectedError) {
    assertNotNull(response.getEntity());
    final String body = response.getEntity().toString();
    assertFalse("Error body must not be empty", body.isEmpty());
    assertTrue("Expected error field '" + expectedError + "' in: " + body,
        body.contains(expectedError));
  }

  private static void injectField(Object target, String fieldName, Object value) throws Exception {
    final Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object readField(Object target, String fieldName) throws Exception {
    final Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }
}

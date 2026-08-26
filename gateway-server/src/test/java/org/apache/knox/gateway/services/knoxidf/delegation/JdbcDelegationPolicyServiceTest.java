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
package org.apache.knox.gateway.services.knoxidf.delegation;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.database.AbstractDataSourceFactory;
import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JdbcDelegationPolicyServiceTest {

  private static final String DB_NAME = "delegation_svc_test";
  private static final String DERBY_CREATE_URL = "jdbc:derby:memory:" + DB_NAME + ";create=true";
  private static final String DERBY_URL = "jdbc:derby:memory:" + DB_NAME;
  private static final String DERBY_SHUTDOWN_URL = "jdbc:derby:memory:" + DB_NAME + ";shutdown=true";

  private static final int CONFIGURED_TTL = 7200;

  private GatewayConfig gatewayConfig;
  private AliasService aliasService;
  private JdbcDelegationPolicyService service;

  @BeforeClass
  public static void setUpClass() throws Exception {
    java.util.Locale.setDefault(java.util.Locale.US);
    DriverManager.getConnection(DERBY_CREATE_URL).close();
  }

  @AfterClass
  public static void tearDownClass() {
    try {
      DriverManager.getConnection(DERBY_SHUTDOWN_URL);
    } catch (SQLException e) {
      if (!(e.getErrorCode() == 45000 && "08006".equals(e.getSQLState()))) {
        throw new RuntimeException("Unexpected Derby shutdown error", e);
      }
    }
  }

  @Before
  public void setUp() throws Exception {
    clearTables();

    gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.DERBY.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("memory:" + DB_NAME).anyTimes();
    EasyMock.expect(gatewayConfig.getDelegationServiceTokenTtlSec()).andReturn(CONFIGURED_TTL).anyTimes();
    EasyMock.expect(gatewayConfig.getDelegationServiceListMaxTotal())
        .andReturn(GatewayConfig.DELEGATION_SERVICE_LIST_MAX_TOTAL_DEFAULT).anyTimes();
    EasyMock.expect(gatewayConfig.getDelegationServiceListMaxPerAuthority())
        .andReturn(GatewayConfig.DELEGATION_SERVICE_LIST_MAX_PER_AUTHORITY_DEFAULT).anyTimes();
    EasyMock.replay(gatewayConfig);

    aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(
        AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(
        AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.replay(aliasService);

    service = new JdbcDelegationPolicyService();
    service.setAliasService(aliasService);
    service.init(gatewayConfig, null);
  }

  // ------------------------------------------------------------------
  // CRUD lifecycle
  // ------------------------------------------------------------------

  @Test
  public void testRegisterAndGetAllFields() throws Exception {
    final Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    final DelegationPolicy input = policy("oidc", "actor@example.com",
        "policy-name", "active", 3600, "desc", "admin", now,
        new HashSet<>(Arrays.asList("alice", "bob")),
        new HashSet<>(Collections.singleton("admins")),
        singleResourcePolicy("/api/v1", "read", "write"));

    final DelegationPolicy registered = service.register(input);
    assertNotNull("register must return policy with generated id", registered.getRegistrationId());

    final DelegationPolicy fetched = service.get(registered.getRegistrationId()).orElseThrow(AssertionError::new);
    assertEquals("oidc", fetched.getActorAuthority());
    assertEquals("actor@example.com", fetched.getActorId());
    assertEquals("policy-name", fetched.getName());
    assertEquals("active", fetched.getStatus());
    assertEquals(Integer.valueOf(3600), fetched.getMaxTokenTtlSec());
    assertEquals("desc", fetched.getDescription());
    assertEquals("admin", fetched.getCreatedBy());
    assertEquals(2, fetched.getCanActForUsers().size());
    assertTrue(fetched.getCanActForUsers().contains("alice"));
    assertTrue(fetched.getCanActForUsers().contains("bob"));
    assertEquals(1, fetched.getCanActForGroups().size());
    assertTrue(fetched.getCanActForGroups().contains("admins"));
    assertTrue(fetched.getResourcePolicy().containsKey("/api/v1"));
    assertEquals(new HashSet<>(Arrays.asList("read", "write")), fetched.getResourcePolicy().get("/api/v1"));
  }

  @Test
  public void testRegisterWithNullableFieldsNull() throws Exception {
    final DelegationPolicy input = policy("oidc", "actor2@example.com",
        null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());

    final DelegationPolicy registered = service.register(input);
    final DelegationPolicy fetched = service.get(registered.getRegistrationId()).orElseThrow(AssertionError::new);

    assertNull(fetched.getName());
    assertNull(fetched.getMaxTokenTtlSec());
    assertNull(fetched.getDescription());
    assertNull(fetched.getCreatedBy());
  }

  @Test
  public void testRegisterDuplicateActorThrows() throws Exception {
    final DelegationPolicy first = policy("oidc", "dup@example.com",
        null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
    service.register(first);
    assertThrows(RuntimeException.class, () -> service.register(first));
  }

  @Test
  public void testUpdateReplacesAllFields() throws Exception {
    final DelegationPolicy original = policy("oidc", "actor3@example.com",
        "original", "active", 1800, null, null, Instant.now(),
        new HashSet<>(Collections.singleton("alice")),
        Collections.emptySet(), Collections.emptyMap());

    final DelegationPolicy registered = service.register(original);
    final String id = registered.getRegistrationId();

    final DelegationPolicy updated = policy("oidc", "actor3@example.com",
        "updated", "active", 3600, "new-desc", "admin2", Instant.now(),
        new HashSet<>(Arrays.asList("alice", "bob", "carol")),
        new HashSet<>(Collections.singleton("ops")),
        singleResourcePolicy("/api/v2", "write"));

    service.update(id, updated);

    final DelegationPolicy fetched = service.get(id).orElseThrow(AssertionError::new);
    assertEquals("updated", fetched.getName());
    assertEquals(Integer.valueOf(3600), fetched.getMaxTokenTtlSec());
    assertEquals("new-desc", fetched.getDescription());
    assertEquals(3, fetched.getCanActForUsers().size());
    assertEquals(1, fetched.getCanActForGroups().size());
    assertTrue(fetched.getResourcePolicy().containsKey("/api/v2"));
  }

  @Test
  public void testUpdateReplaceChildRowsCompletely() throws Exception {
    // Register with 2 users, update with 3 different users — result must be exactly 3
    final DelegationPolicy original = policy("oidc", "actor4@example.com",
        null, "active", null, null, null, Instant.now(),
        new HashSet<>(Arrays.asList("alice", "bob")),
        Collections.emptySet(), Collections.emptyMap());
    final DelegationPolicy registered = service.register(original);

    final DelegationPolicy updated = policy("oidc", "actor4@example.com",
        null, "active", null, null, null, Instant.now(),
        new HashSet<>(Arrays.asList("charlie", "dave", "eve")),
        Collections.emptySet(), Collections.emptyMap());
    service.update(registered.getRegistrationId(), updated);

    final DelegationPolicy fetched = service.get(registered.getRegistrationId()).orElseThrow(AssertionError::new);
    assertEquals("Must have exactly 3 users after update (not 5, not 2)", 3, fetched.getCanActForUsers().size());
    assertTrue(fetched.getCanActForUsers().contains("charlie"));
    assertTrue(fetched.getCanActForUsers().contains("dave"));
    assertTrue(fetched.getCanActForUsers().contains("eve"));
    assertFalse(fetched.getCanActForUsers().contains("alice"));
  }

  @Test
  public void testDeleteRemovesPolicyAndChildRows() throws Exception {
    final DelegationPolicy registered = service.register(policy("oidc", "actor5@example.com",
        null, "active", null, null, null, Instant.now(),
        new HashSet<>(Collections.singleton("alice")),
        Collections.emptySet(),
        singleResourcePolicy("/api/v1", "read")));

    service.delete(registered.getRegistrationId());

    assertFalse(service.get(registered.getRegistrationId()).isPresent());

    // Verify child rows are gone too
    try (Connection conn = DriverManager.getConnection(DERBY_URL);
         PreparedStatement ps = conn.prepareStatement(
             "SELECT COUNT(*) FROM DELEGATION_POLICY_USERS WHERE registration_id = ?")) {
      ps.setString(1, registered.getRegistrationId());
      try (java.sql.ResultSet rs = ps.executeQuery()) {
        rs.next();
        assertEquals(0, rs.getInt(1));
      }
    }
  }

  @Test
  public void testGetNonExistentReturnsEmpty() {
    assertFalse(service.get(java.util.UUID.randomUUID().toString()).isPresent());
  }

  @Test
  public void testFindByActorNonExistentReturnsEmpty() {
    assertFalse(service.findByActor("oidc", "nobody@example.com").isPresent());
  }

  @Test
  public void testListNoFilter() throws Exception {
    service.register(policy("oidc", "a1@example.com", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    service.register(policy("saml", "a2@example.com", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));

    final DelegationPolicyList all = service.list(null);
    assertEquals(2, all.getPolicies().size());
    assertFalse(all.hasMore());
  }

  @Test
  public void testListFilteredByActorAuthority() throws Exception {
    service.register(policy("oidc", "a3@example.com", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    service.register(policy("saml", "a4@example.com", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));

    final DelegationPolicyList oidcOnly = service.list("oidc");
    assertEquals(1, oidcOnly.getPolicies().size());
    assertFalse(oidcOnly.hasMore());
    assertEquals("oidc", oidcOnly.getPolicies().get(0).getActorAuthority());
  }

  @Test
  public void testListTotalLimitEnforced() throws Exception {
    for (int i = 0; i < 3; i++) {
      service.register(policy("k8s_sa", "svc-lim" + i + "@cluster", null, "active", null, null, null, Instant.now(),
          Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    }
    service.register(policy("oidc", "extra1@issuer", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    service.register(policy("oidc", "extra2@issuer", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));

    final JdbcDelegationPolicyService limitedService = createServiceWithLimits(3, 10_000);
    final DelegationPolicyList result = limitedService.list(null);
    assertEquals("Total limit of 3 must be respected even though 5 rows exist", 3, result.getPolicies().size());
    assertTrue("hasMore must be true when results are truncated", result.hasMore());
  }

  @Test
  public void testListPerAuthorityLimitEnforced() throws Exception {
    for (int i = 0; i < 4; i++) {
      service.register(policy("k8s_sa", "svc-pa" + i + "@cluster", null, "active", null, null, null, Instant.now(),
          Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    }
    service.register(policy("oidc", "pa1@issuer", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    service.register(policy("oidc", "pa2@issuer", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));

    final JdbcDelegationPolicyService limitedService = createServiceWithLimits(10_000, 2);

    final DelegationPolicyList k8sResult = limitedService.list("k8s_sa");
    assertEquals("Per-authority limit of 2 must crop 4 k8s_sa rows to 2", 2, k8sResult.getPolicies().size());
    assertTrue("hasMore must be true when results are truncated", k8sResult.hasMore());

    final DelegationPolicyList oidcResult = limitedService.list("oidc");
    assertEquals("Per-authority limit of 2 does not crop oidc (only 2 registered)", 2, oidcResult.getPolicies().size());
    assertFalse("hasMore must be false when results fit within limit", oidcResult.hasMore());
  }

  @Test
  public void testListHasMoreFalseWhenExactlyAtLimit() throws Exception {
    for (int i = 0; i < 3; i++) {
      service.register(policy("oidc", "exact" + i + "@example.com", null, "active", null, null, null, Instant.now(),
          Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    }

    final JdbcDelegationPolicyService limitedService = createServiceWithLimits(3, 10_000);
    final DelegationPolicyList result = limitedService.list(null);
    assertEquals(3, result.getPolicies().size());
    assertFalse("hasMore must be false when result count equals the limit exactly", result.hasMore());
  }

  @Test
  public void testListDefaultLimitsDoNotCropSmallResultSet() throws Exception {
    // Default limits (DELEGATION_SERVICE_LIST_MAX_TOTAL_DEFAULT = 10_000) must not crop small datasets.
    // This also verifies that the service initialized with default config values functions correctly.
    service.register(policy("oidc", "dflt1@example.com", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    service.register(policy("k8s_sa", "dflt2@cluster", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    service.register(policy("saml", "dflt3@idp", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));

    final DelegationPolicyList all = service.list(null);
    assertEquals(3, all.getPolicies().size());
    assertFalse(all.hasMore());

    final DelegationPolicyList oidcList = service.list("oidc");
    assertEquals(1, oidcList.getPolicies().size());
    assertFalse(oidcList.hasMore());

    final DelegationPolicyList k8sList = service.list("k8s_sa");
    assertEquals(1, k8sList.getPolicies().size());
    assertFalse(k8sList.hasMore());

    final DelegationPolicyList samlList = service.list("saml");
    assertEquals(1, samlList.getPolicies().size());
    assertFalse(samlList.hasMore());
  }

  // ------------------------------------------------------------------
  // resourcePolicy round-trips
  // ------------------------------------------------------------------

  @Test
  public void testResourceWithTwoExplicitScopes() throws Exception {
    final DelegationPolicy registered = service.register(policy("oidc", "rp1@example.com",
        null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(),
        singleResourcePolicy("/api/v1", "read", "write")));

    final DelegationPolicy fetched = service.get(registered.getRegistrationId()).orElseThrow(AssertionError::new);
    assertEquals(new HashSet<>(Arrays.asList("read", "write")), fetched.getResourcePolicy().get("/api/v1"));
  }

  @Test
  public void testResourceWithEmptyScopeSet() throws Exception {
    final Map<String, Set<String>> rp = new HashMap<>();
    rp.put("/api/v1", Collections.emptySet());
    final DelegationPolicy registered = service.register(policy("oidc", "rp2@example.com",
        null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), rp));

    final DelegationPolicy fetched = service.get(registered.getRegistrationId()).orElseThrow(AssertionError::new);
    assertTrue("Empty scope set must round-trip as empty (all-scopes sentinel)",
        fetched.getResourcePolicy().get("/api/v1").isEmpty());
  }

  @Test
  public void testEmptyResourcePolicy() throws Exception {
    final DelegationPolicy registered = service.register(policy("oidc", "rp3@example.com",
        null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));

    final DelegationPolicy fetched = service.get(registered.getRegistrationId()).orElseThrow(AssertionError::new);
    assertTrue(fetched.getResourcePolicy().isEmpty());
  }

  @Test
  public void testTwoResourcesWithDifferentScopes() throws Exception {
    final Map<String, Set<String>> rp = new HashMap<>();
    rp.put("/api/v1", new HashSet<>(Arrays.asList("read")));
    rp.put("/api/v2", new HashSet<>(Arrays.asList("read", "write")));
    final DelegationPolicy registered = service.register(policy("oidc", "rp4@example.com",
        null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), rp));

    final DelegationPolicy fetched = service.get(registered.getRegistrationId()).orElseThrow(AssertionError::new);
    assertEquals(new HashSet<>(Arrays.asList("read")), fetched.getResourcePolicy().get("/api/v1"));
    assertEquals(new HashSet<>(Arrays.asList("read", "write")), fetched.getResourcePolicy().get("/api/v2"));
  }

  @Test
  public void testMixedResourcesOneWithScopesOneWithout() throws Exception {
    final Map<String, Set<String>> rp = new HashMap<>();
    rp.put("/api/v1", new HashSet<>(Arrays.asList("read")));
    rp.put("/api/v2", Collections.emptySet());
    final DelegationPolicy registered = service.register(policy("oidc", "rp5@example.com",
        null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), rp));

    final DelegationPolicy fetched = service.get(registered.getRegistrationId()).orElseThrow(AssertionError::new);
    assertEquals(new HashSet<>(Arrays.asList("read")), fetched.getResourcePolicy().get("/api/v1"));
    assertTrue(fetched.getResourcePolicy().get("/api/v2").isEmpty());
  }

  // ------------------------------------------------------------------
  // evaluate() — authorized paths
  // ------------------------------------------------------------------

  @Test
  public void testEvaluateAuthorizedSubjectInUsersResourceAndScopeMatch() throws Exception {
    registerPolicy("eval-auth1", "oidc", "eval@example.com",
        Collections.singleton("alice"),
        Collections.emptySet(),
        singleResourcePolicy("/api/v1", "read"));

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "eval@example.com", "alice", "/api/v1", "read", false));
    assertNull(decision.getDenyReason());
  }

  @Test
  public void testEvaluateAuthorizedAllScopesWhenScopeSetEmpty() throws Exception {
    final Map<String, Set<String>> rp = new HashMap<>();
    rp.put("/api/v1", Collections.emptySet());
    registerPolicy("eval-auth2", "oidc", "eval2@example.com",
        Collections.singleton("alice"), Collections.emptySet(), rp);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "eval2@example.com", "alice", "/api/v1", "any-scope", false));
    assertNull(decision.getDenyReason());
  }

  @Test
  public void testEvaluateAuthorizedMultipleResourcesUsesRequested() throws Exception {
    final Map<String, Set<String>> rp = new HashMap<>();
    rp.put("/api/v1", new HashSet<>(Arrays.asList("read")));
    rp.put("/api/v2", new HashSet<>(Arrays.asList("write")));
    registerPolicy("eval-auth3", "oidc", "eval3@example.com",
        Collections.singleton("alice"), Collections.emptySet(), rp);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "eval3@example.com", "alice", "/api/v1", "read", false));
    assertNull(decision.getDenyReason());
  }

  @Test
  public void testEvaluateAuthorizedHeadlessExchangeAllowed() throws Exception {
    registerPolicy("eval-headless", "oidc", "headless@example.com",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api", "read"), true);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "headless@example.com", "alice", "/api", "read", true));
    assertNull(decision.getDenyReason());
  }

  // ------------------------------------------------------------------
  // evaluate() — denied paths
  // ------------------------------------------------------------------

  @Test
  public void testEvaluateDenyActorNotRegistered() {
    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "nobody@example.com", "alice", "/api", "read", false));
    assertEquals("actor_not_registered", decision.getDenyReason());
    assertEquals(0, decision.getEffectiveMaxTtlSec());
  }

  @Test
  public void testEvaluateDenySubjectNotInUsersAndNoGroups() throws Exception {
    registerPolicy("eval-deny-subj", "oidc", "deny1@example.com",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api", "read"));

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "deny1@example.com", "bob", "/api", "read", false));
    assertNotNull(decision.getDenyReason());
    assertEquals("subject_not_allowed", decision.getDenyReason());
  }

  @Test
  public void testEvaluateDenyEmptyUsersAndEmptyGroups() throws Exception {
    registerPolicy("eval-deny-empty", "oidc", "deny2@example.com",
        Collections.emptySet(), Collections.emptySet(),
        singleResourcePolicy("/api", "read"));

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "deny2@example.com", "alice", "/api", "read", false));
    assertNotNull(decision.getDenyReason());
    assertEquals("subject_not_allowed", decision.getDenyReason());
  }

  @Test
  public void testEvaluateDenyResourceNotInPolicy() throws Exception {
    registerPolicy("eval-deny-res", "oidc", "deny3@example.com",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api/v1", "read"));

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "deny3@example.com", "alice", "/api/v2", "read", false));
    assertNotNull(decision.getDenyReason());
    assertEquals("resource_not_allowed", decision.getDenyReason());
  }

  @Test
  public void testEvaluateDenyScopeNotInSet() throws Exception {
    registerPolicy("eval-deny-scope", "oidc", "deny4@example.com",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api/v1", "read"));

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "deny4@example.com", "alice", "/api/v1", "write", false));
    assertNotNull(decision.getDenyReason());
    assertEquals("scope_not_allowed", decision.getDenyReason());
  }

  @Test
  public void testEvaluateDenyScopeConstraintIsPerResource() throws Exception {
    // /api/v1 has "read" scope; /api/v2 has "write" scope. Requesting /api/v1 with "read" is OK
    // even though scope constraint exists for /api/v2.
    final Map<String, Set<String>> rp = new HashMap<>();
    rp.put("/api/v1", new HashSet<>(Arrays.asList("read")));
    rp.put("/api/v2", new HashSet<>(Arrays.asList("write")));
    registerPolicy("eval-scope-per-res", "oidc", "scopetest@example.com",
        Collections.singleton("alice"), Collections.emptySet(), rp);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "scopetest@example.com", "alice", "/api/v1", "read", false));
    assertNull(decision.getDenyReason());
  }

  @Test
  public void testEvaluateDenyHeadlessNotAllowed() throws Exception {
    registerPolicy("eval-headless-deny", "oidc", "headless2@example.com",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api", "read"), false);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "headless2@example.com", "alice", "/api", "read", true));
    assertNotNull(decision.getDenyReason());
    assertEquals("headless_not_allowed", decision.getDenyReason());
  }

  @Test
  public void testEvaluateGroupsNotEmptyThrowsServerError() throws Exception {
    registerPolicy("eval-group-err", "oidc", "groups@example.com",
        Collections.emptySet(),
        new HashSet<>(Collections.singleton("admins")),
        singleResourcePolicy("/api", "read"));

    assertThrows(UnsupportedOperationException.class, () ->
        service.evaluate(new PolicyCheckRequest("oidc", "groups@example.com", "alice", "/api", "read", false)));
  }

  // ------------------------------------------------------------------
  // evaluate() — TTL computation
  // ------------------------------------------------------------------

  @Test
  public void testEvaluateTtlPolicyLowerThanConfigured() throws Exception {
    registerPolicy("eval-ttl1", "oidc", "ttl1@example.com",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api", "read"), false, 3600);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "ttl1@example.com", "alice", "/api", "read", false));
    assertNull(decision.getDenyReason());
    // CONFIGURED_TTL=7200, policy TTL=3600 → min is 3600
    assertEquals(3600, decision.getEffectiveMaxTtlSec());
  }

  @Test
  public void testEvaluateTtlConfiguredLowerThanPolicy() throws Exception {
    registerPolicy("eval-ttl2", "oidc", "ttl2@example.com",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api", "read"), false, 10000);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "ttl2@example.com", "alice", "/api", "read", false));
    assertNull(decision.getDenyReason());
    // CONFIGURED_TTL=7200, policy TTL=10000 → min is 7200
    assertEquals(CONFIGURED_TTL, decision.getEffectiveMaxTtlSec());
  }

  @Test
  public void testEvaluateTtlNullPolicyUsesConfigured() throws Exception {
    registerPolicy("eval-ttl3", "oidc", "ttl3@example.com",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api", "read"), false, null);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "ttl3@example.com", "alice", "/api", "read", false));
    assertNull(decision.getDenyReason());
    assertEquals(CONFIGURED_TTL, decision.getEffectiveMaxTtlSec());
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private JdbcDelegationPolicyService createServiceWithLimits(int maxTotal, int maxPerAuthority)
      throws ServiceLifecycleException {
    final GatewayConfig cfg = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(cfg.getDatabaseType()).andReturn(DatabaseType.DERBY.type()).anyTimes();
    EasyMock.expect(cfg.getDatabaseName()).andReturn("memory:" + DB_NAME).anyTimes();
    EasyMock.expect(cfg.getDelegationServiceTokenTtlSec()).andReturn(CONFIGURED_TTL).anyTimes();
    EasyMock.expect(cfg.getDelegationServiceListMaxTotal()).andReturn(maxTotal).anyTimes();
    EasyMock.expect(cfg.getDelegationServiceListMaxPerAuthority()).andReturn(maxPerAuthority).anyTimes();
    EasyMock.replay(cfg);
    final JdbcDelegationPolicyService svc = new JdbcDelegationPolicyService();
    svc.setAliasService(aliasService);
    svc.init(cfg, null);
    return svc;
  }

  private void registerPolicy(String actorId, String authority, String actorSubject,
      Set<String> users, Set<String> groups, Map<String, Set<String>> resourcePolicy) throws Exception {
    service.register(policy(authority, actorSubject, null, "active", null, null, null,
        Instant.now(), users, groups, resourcePolicy));
  }

  private void registerPolicy(String actorId, String authority, String actorSubject,
      Set<String> users, Set<String> groups, Map<String, Set<String>> resourcePolicy,
      boolean allowHeadless) throws Exception {
    service.register(new DelegationPolicy(null, authority, actorSubject, null, "active", null, null, null,
        Instant.now(), Instant.now(), allowHeadless, users, groups, resourcePolicy));
  }

  private void registerPolicy(String actorId, String authority, String actorSubject,
      Set<String> users, Set<String> groups, Map<String, Set<String>> resourcePolicy,
      boolean allowHeadless, Integer policyTtl) throws Exception {
    service.register(new DelegationPolicy(null, authority, actorSubject, null, "active", policyTtl, null, null,
        Instant.now(), Instant.now(), allowHeadless, users, groups, resourcePolicy));
  }

  private static DelegationPolicy policy(String actorAuthority, String actorId,
      String name, String status, Integer maxTtl, String description, String createdBy,
      Instant createdAt, Set<String> users, Set<String> groups, Map<String, Set<String>> rp) {
    return new DelegationPolicy(
        null, actorAuthority, actorId, name, status, maxTtl, description, createdBy,
        createdAt, createdAt, false, users, groups, rp);
  }

  private static Map<String, Set<String>> singleResourcePolicy(String resource, String... scopes) {
    final Map<String, Set<String>> rp = new HashMap<>();
    rp.put(resource, new HashSet<>(Arrays.asList(scopes)));
    return rp;
  }

  private static void clearTables() {
    try (Connection conn = DriverManager.getConnection(DERBY_URL)) {
      for (String table : new String[]{
          "DELEGATION_POLICY_RESOURCE_SCOPES", "DELEGATION_POLICY_RESOURCES",
          "DELEGATION_POLICY_GROUPS", "DELEGATION_POLICY_USERS", "DELEGATION_POLICIES"}) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table)) {
          ps.executeUpdate();
        } catch (SQLException e) {
          // Table may not exist yet; service.init() will create it
        }
      }
    } catch (SQLException e) {
      // DB may not exist yet on first setUp
    }
  }
}

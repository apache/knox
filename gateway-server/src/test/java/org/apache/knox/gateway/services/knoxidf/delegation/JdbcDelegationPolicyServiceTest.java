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
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JdbcDelegationPolicyServiceTest {

  private static final String DB_NAME = "delegation_svc_test";
  // In-memory H2; DB_CLOSE_DELAY=-1 keeps the database alive across connection open/close for the
  // whole test class (the service and the direct JDBC cleanup below each open their own connections).
  private static final String H2_URL = "jdbc:h2:mem:" + DB_NAME + ";DB_CLOSE_DELAY=-1";

  private static final int CONFIGURED_TTL = 7200;

  // A held-open connection guarantees the in-memory database survives even when every other
  // connection is briefly closed between tests.
  private static Connection keepAlive;

  private GatewayConfig gatewayConfig;
  private AliasService aliasService;
  private JdbcDelegationPolicyService service;

  @BeforeClass
  public static void setUpClass() throws Exception {
    // Create and pin the in-memory H2 database for the lifetime of the test class.
    keepAlive = DriverManager.getConnection(H2_URL);
  }

  @AfterClass
  public static void tearDownClass() throws SQLException {
    // Dropping all objects releases the in-memory database once the class finishes.
    if (keepAlive != null) {
      try (Statement stmt = keepAlive.createStatement()) {
        stmt.execute("DROP ALL OBJECTS");
      }
      keepAlive.close();
    }
  }

  @Before
  public void setUp() throws Exception {
    clearTables();

    gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("mem:" + DB_NAME + ";DB_CLOSE_DELAY=-1").anyTimes();
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
    final DelegationPolicy input = policy("oidc", "actorId",
        "policy-name", "active", 3600, "desc", "admin", now,
        new HashSet<>(Arrays.asList("alice", "bob")),
        new HashSet<>(Collections.singleton("admins")),
        singleResourcePolicy("/api/v1", "read", "write"));

    final DelegationPolicy registered = service.register(input);
    assertNotNull("register must return policy with generated id", registered.getRegistrationId());

    final DelegationPolicy fetched = service.get(registered.getRegistrationId()).orElseThrow(AssertionError::new);
    assertEquals("oidc", fetched.getActorAuthority());
    assertEquals("actorId", fetched.getActorId());
    assertEquals("policy-name", fetched.getName());
    assertEquals("active", fetched.getStatus());
    assertEquals(Integer.valueOf(3600), fetched.getTokenTtlSec());
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
    final DelegationPolicy input = policy("oidc", "actorId2",
        null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());

    final DelegationPolicy registered = service.register(input);
    final DelegationPolicy fetched = service.get(registered.getRegistrationId()).orElseThrow(AssertionError::new);

    assertNull(fetched.getName());
    assertNull(fetched.getTokenTtlSec());
    assertNull(fetched.getDescription());
    assertNull(fetched.getCreatedBy());
  }

  @Test
  public void testRegisterDuplicateActorThrows() throws Exception {
    final DelegationPolicy first = policy("oidc", "duplicated",
        null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
    service.register(first);
    final DelegationPolicyAlreadyExistsException e =
        assertThrows(DelegationPolicyAlreadyExistsException.class, () -> service.register(first));
    assertNotNull("cause chain must retain the original SQLException", findSQLException(e));
  }

  @Test
  public void testRegisterUnrelatedStorageFailureThrowsPlainRuntimeException() throws Exception {
    // A VARCHAR(20) column overflow triggers Derby's data-exception SQLState 22001, which is not
    // an integrity-constraint violation -- confirms isUniqueConstraintViolation doesn't over-match.
    final DelegationPolicy tooLongAuthority = policy(
        "this-authority-is-far-too-long-for-the-column", "actorIdForOverflow",
        null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
    final RuntimeException e = assertThrows(RuntimeException.class, () -> service.register(tooLongAuthority));
    assertFalse(e instanceof DelegationPolicyAlreadyExistsException);
  }

  private static SQLException findSQLException(Throwable t) {
    final int maxDepth = 1000;
    for (int depth = 0; t != null && depth < maxDepth; t = t.getCause(), depth++) {
      if (t instanceof SQLException) {
        return (SQLException) t;
      }
    }
    return null;
  }

  @Test
  public void testUpdateReplacesAllFields() throws Exception {
    final DelegationPolicy original = policy("oidc", "actorId3",
        "original", "active", 1800, null, null, Instant.now(),
        new HashSet<>(Collections.singleton("alice")),
        Collections.emptySet(), Collections.emptyMap());

    final DelegationPolicy registered = service.register(original);
    final String id = registered.getRegistrationId();

    final DelegationPolicy updated = policy("oidc", "actorId3",
        "updated", "active", 3600, "new-desc", "admin2", Instant.now(),
        new HashSet<>(Arrays.asList("alice", "bob", "carol")),
        new HashSet<>(Collections.singleton("ops")),
        singleResourcePolicy("/api/v2", "write"));

    service.update(id, updated);

    final DelegationPolicy fetched = service.get(id).orElseThrow(AssertionError::new);
    assertEquals("updated", fetched.getName());
    assertEquals(Integer.valueOf(3600), fetched.getTokenTtlSec());
    assertEquals("new-desc", fetched.getDescription());
    assertEquals(3, fetched.getCanActForUsers().size());
    assertEquals(1, fetched.getCanActForGroups().size());
    assertTrue(fetched.getResourcePolicy().containsKey("/api/v2"));
  }

  @Test
  public void testUpdateReplaceChildRowsCompletely() throws Exception {
    // Register with 2 users, update with 3 different users - result must be exactly 3
    final DelegationPolicy original = policy("oidc", "actorId4",
        null, "active", null, null, null, Instant.now(),
        new HashSet<>(Arrays.asList("alice", "bob")),
        Collections.emptySet(), Collections.emptyMap());
    final DelegationPolicy registered = service.register(original);

    final DelegationPolicy updated = policy("oidc", "actorId4",
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
  public void testUpdateNonExistentRegistrationIdThrowsNotFound() throws Exception {
    final DelegationPolicy update = policy("oidc", "actorId-missing",
        "name", "active", null, null, null, Instant.now(),
        Collections.singleton("alice"), Collections.emptySet(), Collections.emptyMap());
    assertThrows(DelegationPolicyNotFoundException.class,
        () -> service.update("nonexistent-registration-id", update));
  }

  @Test
  public void testUpdateRejectsActorAuthorityChange() throws Exception {
    final DelegationPolicy original = policy("oidc", "actorId6",
        "original", "active", null, null, null, Instant.now(),
        Collections.singleton("alice"), Collections.emptySet(), Collections.emptyMap());
    final String id = service.register(original).getRegistrationId();

    final DelegationPolicy attempt = policy("different-authority", "actorId6",
        "updated", "active", null, null, null, Instant.now(),
        Collections.singleton("alice"), Collections.emptySet(), Collections.emptyMap());

    assertThrows(DelegationPolicyNotFoundException.class, () -> service.update(id, attempt));

    // Identity is immutable, so a rejected update must leave the stored row untouched.
    final DelegationPolicy fetched = service.get(id).orElseThrow(AssertionError::new);
    assertEquals("original", fetched.getName());
    assertEquals("oidc", fetched.getActorAuthority());
  }

  @Test
  public void testUpdateRejectsActorIdChange() throws Exception {
    final DelegationPolicy original = policy("oidc", "actorId7",
        "original", "active", null, null, null, Instant.now(),
        Collections.singleton("alice"), Collections.emptySet(), Collections.emptyMap());
    final String id = service.register(original).getRegistrationId();

    final DelegationPolicy attempt = policy("oidc", "different-actor-id",
        "updated", "active", null, null, null, Instant.now(),
        Collections.singleton("alice"), Collections.emptySet(), Collections.emptyMap());

    assertThrows(DelegationPolicyNotFoundException.class, () -> service.update(id, attempt));

    final DelegationPolicy fetched = service.get(id).orElseThrow(AssertionError::new);
    assertEquals("original", fetched.getName());
    assertEquals("actorId7", fetched.getActorId());
  }

  @Test
  public void testUpdatePreservesCreatedByAndCreatedAtAndReturnsPersistedRow() throws Exception {
    final Instant originalCreatedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).minusSeconds(3600);
    final DelegationPolicy original = policy("oidc", "actorId8",
        "original", "active", null, null, "original-admin", originalCreatedAt,
        Collections.singleton("alice"), Collections.emptySet(), Collections.emptyMap());
    final String id = service.register(original).getRegistrationId();

    // Client-supplied createdBy/createdAt on the update payload must be ignored entirely.
    final DelegationPolicy attempt = policy("oidc", "actorId8",
        "updated", "active", null, null, "spoofed-admin", Instant.now().minusSeconds(9999),
        Collections.singleton("alice"), Collections.emptySet(), Collections.emptyMap());

    final DelegationPolicy result = service.update(id, attempt);

    assertEquals("original-admin", result.getCreatedBy());
    assertEquals(originalCreatedAt, result.getCreatedAt());
    assertNotEquals(originalCreatedAt, result.getUpdatedAt());

    final DelegationPolicy fetched = service.get(id).orElseThrow(AssertionError::new);
    assertEquals("original-admin", fetched.getCreatedBy());
    assertEquals(originalCreatedAt, fetched.getCreatedAt());
  }

  // ------------------------------------------------------------------
  // registerOrUpdate
  // ------------------------------------------------------------------

  @Test
  public void testRegisterOrUpdateCreatesNewActor() throws Exception {
    final DelegationPolicy input = policy("oidc", "actorId-upsert-1",
        "name", "active", null, null, "admin1", Instant.now(),
        Collections.singleton("alice"), Collections.emptySet(), Collections.emptyMap());

    final RegisterOrUpdateResult result = service.registerOrUpdate(input);

    assertTrue("first call for a new actor must create", result.isCreated());
    assertNotNull("a registrationId must be generated", result.getPolicy().getRegistrationId());
    final DelegationPolicy fetched = service.findByActor("oidc", "actorId-upsert-1").orElseThrow(AssertionError::new);
    assertEquals(result.getPolicy().getRegistrationId(), fetched.getRegistrationId());
  }

  @Test
  public void testRegisterOrUpdateReplacesExistingActorInPlace() throws Exception {
    final Instant originalCreatedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).minusSeconds(3600);
    final DelegationPolicy first = policy("oidc", "actorId-upsert-2",
        "name-v1", "active", 1200, null, "admin1", originalCreatedAt,
        Collections.singleton("alice"), Collections.emptySet(), Collections.emptyMap());
    final RegisterOrUpdateResult created = service.registerOrUpdate(first);
    assertTrue(created.isCreated());
    final String registrationId = created.getPolicy().getRegistrationId();
    assertEquals("admin1", created.getPolicy().getCreatedBy());
    assertEquals(originalCreatedAt, created.getPolicy().getCreatedAt());

    // actorAuthority/actorId are the same as `first` (identity, required to hit the update
    // branch); createdBy/createdAt are client-supplied here (as they would be in every reconcile
    // payload) and must be ignored -- identity/creation fields stay immutable after registration,
    // same as a plain update() call.
    final DelegationPolicy second = policy("oidc", "actorId-upsert-2",
        "name-v2", "active", 3600, "updated-desc", "spoofed-admin", Instant.now().minusSeconds(9999),
        new HashSet<>(Arrays.asList("alice", "bob")), Collections.emptySet(), Collections.emptyMap());
    final RegisterOrUpdateResult updated = service.registerOrUpdate(second);

    assertFalse("second call for the same actor must update, not create", updated.isCreated());
    assertEquals("registrationId must be stable across reconciles", registrationId, updated.getPolicy().getRegistrationId());
    assertEquals("oidc", updated.getPolicy().getActorAuthority());
    assertEquals("actorId-upsert-2", updated.getPolicy().getActorId());
    assertEquals("name-v2", updated.getPolicy().getName());
    assertEquals(Integer.valueOf(3600), updated.getPolicy().getTokenTtlSec());
    assertEquals("updated-desc", updated.getPolicy().getDescription());
    assertEquals(2, updated.getPolicy().getCanActForUsers().size());
    assertEquals("createdBy is immutable and must survive an upsert-triggered update",
        "admin1", updated.getPolicy().getCreatedBy());
    assertEquals("createdAt is immutable and must survive an upsert-triggered update",
        originalCreatedAt, updated.getPolicy().getCreatedAt());
    assertNotEquals(originalCreatedAt, updated.getPolicy().getUpdatedAt());

    final DelegationPolicy fetched = service.get(registrationId).orElseThrow(AssertionError::new);
    assertEquals("admin1", fetched.getCreatedBy());
    assertEquals(originalCreatedAt, fetched.getCreatedAt());
  }

  @Test
  public void testDeleteRemovesPolicyAndChildRows() throws Exception {
    final DelegationPolicy registered = service.register(policy("oidc", "actorId5",
        null, "active", null, null, null, Instant.now(),
        new HashSet<>(Collections.singleton("alice")),
        Collections.emptySet(),
        singleResourcePolicy("/api/v1", "read")));

    service.delete(registered.getRegistrationId());

    assertFalse(service.get(registered.getRegistrationId()).isPresent());

    // Verify child rows are gone too
    try (Connection conn = DriverManager.getConnection(H2_URL);
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
  public void testDeleteNonExistentRegistrationIdThrowsNotFound() {
    assertThrows(DelegationPolicyNotFoundException.class,
        () -> service.delete("nonexistent-registration-id"));
  }

  @Test
  public void testGetNonExistentReturnsEmpty() {
    assertFalse(service.get(java.util.UUID.randomUUID().toString()).isPresent());
  }

  @Test
  public void testFindByActorNonExistentReturnsEmpty() {
    assertFalse(service.findByActor("oidc", "nobody").isPresent());
  }

  @Test
  public void testListNoFilter() throws Exception {
    service.register(policy("oidc", "a1", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    service.register(policy("saml", "a2", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));

    final DelegationPolicyList all = service.list(null);
    assertEquals(2, all.getPolicies().size());
    assertFalse(all.hasMore());
  }

  @Test
  public void testListFilteredByActorAuthority() throws Exception {
    service.register(policy("oidc", "a3", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    service.register(policy("saml", "a4", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));

    final DelegationPolicyList oidcOnly = service.list("oidc");
    assertEquals(1, oidcOnly.getPolicies().size());
    assertFalse(oidcOnly.hasMore());
    assertEquals("oidc", oidcOnly.getPolicies().get(0).getActorAuthority());
  }

  @Test
  public void testListTotalLimitEnforced() throws Exception {
    for (int i = 0; i < 3; i++) {
      service.register(policy("k8s_sa", "svc-lim" + i, null, "active", null, null, null, Instant.now(),
          Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    }
    service.register(policy("oidc", "extra1", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    service.register(policy("oidc", "extra2", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));

    final JdbcDelegationPolicyService limitedService = createServiceWithLimits(3, 10_000);
    final DelegationPolicyList result = limitedService.list(null);
    assertEquals("Total limit of 3 must be respected even though 5 rows exist", 3, result.getPolicies().size());
    assertTrue("hasMore must be true when results are truncated", result.hasMore());
  }

  @Test
  public void testListPerAuthorityLimitEnforced() throws Exception {
    for (int i = 0; i < 4; i++) {
      service.register(policy("k8s_sa", "svc-pa" + i, null, "active", null, null, null, Instant.now(),
          Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    }
    service.register(policy("oidc", "pa1", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    service.register(policy("oidc", "pa2", null, "active", null, null, null, Instant.now(),
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
      service.register(policy("oidc", "exact" + i, null, "active", null, null, null, Instant.now(),
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
    service.register(policy("oidc", "dflt1", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    service.register(policy("k8s_sa", "dflt2", null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));
    service.register(policy("saml", "dflt3", null, "active", null, null, null, Instant.now(),
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
    final DelegationPolicy registered = service.register(policy("oidc", "rp1",
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
    final DelegationPolicy registered = service.register(policy("oidc", "rp2",
        null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), rp));

    final DelegationPolicy fetched = service.get(registered.getRegistrationId()).orElseThrow(AssertionError::new);
    assertTrue("Empty scope set must round-trip as empty (all-scopes sentinel)",
        fetched.getResourcePolicy().get("/api/v1").isEmpty());
  }

  @Test
  public void testEmptyResourcePolicy() throws Exception {
    final DelegationPolicy registered = service.register(policy("oidc", "rp3",
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
    final DelegationPolicy registered = service.register(policy("oidc", "rp4",
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
    final DelegationPolicy registered = service.register(policy("oidc", "rp5",
        null, "active", null, null, null, Instant.now(),
        Collections.emptySet(), Collections.emptySet(), rp));

    final DelegationPolicy fetched = service.get(registered.getRegistrationId()).orElseThrow(AssertionError::new);
    assertEquals(new HashSet<>(Arrays.asList("read")), fetched.getResourcePolicy().get("/api/v1"));
    assertTrue(fetched.getResourcePolicy().get("/api/v2").isEmpty());
  }

  // ------------------------------------------------------------------
  // evaluate() - authorized paths
  // ------------------------------------------------------------------

  @Test
  public void testEvaluateAuthorizedSubjectInUsersResourceAndScopeMatch() throws Exception {
    registerPolicy("oidc", "eval",
        Collections.singleton("alice"),
        Collections.emptySet(),
        singleResourcePolicy("/api/v1", "read"));

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "eval", "alice", "/api/v1", Collections.singleton("read"), false));
    assertNull(decision.getDenyReason());
  }

  @Test
  public void testEvaluateAuthorizedAllScopesWhenScopeSetEmpty() throws Exception {
    final Map<String, Set<String>> rp = new HashMap<>();
    rp.put("/api/v1", Collections.emptySet());
    registerPolicy("oidc", "eval2",
        Collections.singleton("alice"), Collections.emptySet(), rp);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "eval2", "alice", "/api/v1", Collections.singleton("any-scope"), false));
    assertNull(decision.getDenyReason());
  }

  @Test
  public void testEvaluateAuthorizedMultipleResourcesUsesRequested() throws Exception {
    final Map<String, Set<String>> rp = new HashMap<>();
    rp.put("/api/v1", new HashSet<>(Arrays.asList("read")));
    rp.put("/api/v2", new HashSet<>(Arrays.asList("write")));
    registerPolicy("oidc", "eval3",
        Collections.singleton("alice"), Collections.emptySet(), rp);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "eval3", "alice", "/api/v1", Collections.singleton("read"), false));
    assertNull(decision.getDenyReason());
  }

  @Test
  public void testEvaluateAuthorizedHeadlessExchangeAllowed() throws Exception {
    registerPolicy("oidc", "headless",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api", "read"), true);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "headless", "alice", "/api", Collections.singleton("read"), true));
    assertNull(decision.getDenyReason());
  }

  // ------------------------------------------------------------------
  // evaluate() - denied paths
  // ------------------------------------------------------------------

  @Test
  public void testEvaluateDenyActorNotRegistered() {
    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "nobody", "alice", "/api", Collections.singleton("read"), false));
    assertEquals("actor_not_registered", decision.getDenyReason());
    assertEquals(0, decision.getEffectiveTtlSec());
  }

  @Test
  public void testEvaluateDenySubjectNotInUsersAndNoGroups() throws Exception {
    registerPolicy("oidc", "deny1",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api", "read"));

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "deny1", "bob", "/api", Collections.singleton("read"), false));
    assertNotNull(decision.getDenyReason());
    assertEquals("subject_not_allowed", decision.getDenyReason());
  }

  @Test
  public void testEvaluateDenyEmptyUsersAndEmptyGroups() throws Exception {
    registerPolicy("oidc", "deny2",
        Collections.emptySet(), Collections.emptySet(),
        singleResourcePolicy("/api", "read"));

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "deny2", "alice", "/api", Collections.singleton("read"), false));
    assertNotNull(decision.getDenyReason());
    assertEquals("subject_not_allowed", decision.getDenyReason());
  }

  @Test
  public void testEvaluateDenyResourceNotInPolicy() throws Exception {
    registerPolicy("oidc", "deny3",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api/v1", "read"));

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "deny3", "alice", "/api/v2", Collections.singleton("read"), false));
    assertNotNull(decision.getDenyReason());
    assertEquals("resource_not_allowed", decision.getDenyReason());
  }

  @Test
  public void testEvaluateDenyScopeNotInSet() throws Exception {
    registerPolicy("oidc", "deny4",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api/v1", "read"));

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "deny4", "alice", "/api/v1", Collections.singleton("write"), false));
    assertNotNull(decision.getDenyReason());
    assertEquals("scope_not_allowed", decision.getDenyReason());
  }

  @Test
  public void testEvaluateAuthorizedEmptyRequestedScopes() throws Exception {
    registerPolicy("oidc", "noscope",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api/v1", "read"));

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "noscope", "alice", "/api/v1", Collections.emptySet(), false));
    assertNull("empty requested scopes must be authorized (scope is optional)", decision.getDenyReason());
  }

  @Test
  public void testEvaluateAuthorizedMultipleRequestedScopesAllInSet() throws Exception {
    registerPolicy("oidc", "multiscope",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api/v1", "read", "write"));

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "multiscope", "alice", "/api/v1",
            new HashSet<>(Arrays.asList("read", "write")), false));
    assertNull("all requested scopes in allowed set must be authorized", decision.getDenyReason());
  }

  @Test
  public void testEvaluateDenyMultipleRequestedScopesPartialMatch() throws Exception {
    registerPolicy("oidc", "partial",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api/v1", "read"));

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "partial", "alice", "/api/v1",
            new HashSet<>(Arrays.asList("read", "write")), false));
    assertEquals("scope_not_allowed", decision.getDenyReason());
  }

  @Test
  public void testEvaluateAuthorizedMultipleRequestedScopesEmptyPolicySet() throws Exception {
    final Map<String, Set<String>> rp = new HashMap<>();
    rp.put("/api/v1", Collections.emptySet());
    registerPolicy("oidc", "multiany",
        Collections.singleton("alice"), Collections.emptySet(), rp);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "multiany", "alice", "/api/v1",
            new HashSet<>(Arrays.asList("read", "write")), false));
    assertNull("empty policy scope set allows any requested scopes", decision.getDenyReason());
  }

  @Test
  public void testEvaluateDenyScopeConstraintIsPerResource() throws Exception {
    // /api/v1 has "read" scope; /api/v2 has "write" scope. Requesting /api/v1 with "read" is OK
    // even though scope constraint exists for /api/v2.
    final Map<String, Set<String>> rp = new HashMap<>();
    rp.put("/api/v1", new HashSet<>(Arrays.asList("read")));
    rp.put("/api/v2", new HashSet<>(Arrays.asList("write")));
    registerPolicy("oidc", "scopetest",
        Collections.singleton("alice"), Collections.emptySet(), rp);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "scopetest", "alice", "/api/v1", Collections.singleton("read"), false));
    assertNull(decision.getDenyReason());
  }

  @Test
  public void testEvaluateDenyHeadlessNotAllowed() throws Exception {
    registerPolicy("oidc", "headless2",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api", "read"), false);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "headless2", "alice", "/api", Collections.singleton("read"), true));
    assertNotNull(decision.getDenyReason());
    assertEquals("headless_not_allowed", decision.getDenyReason());
  }

  @Test
  public void testEvaluateGroupsNotEmptyThrowsServerError() throws Exception {
    registerPolicy("oidc", "groups",
        Collections.emptySet(),
        new HashSet<>(Collections.singleton("admins")),
        singleResourcePolicy("/api", "read"));

    assertThrows(UnsupportedOperationException.class, () ->
        service.evaluate(new PolicyCheckRequest("oidc", "groups", "alice", "/api", Collections.singleton("read"), false)));
  }

  // ------------------------------------------------------------------
  // evaluate() - TTL computation
  // ------------------------------------------------------------------

  @Test
  public void testEvaluateTtlPolicyLowerThanConfigured() throws Exception {
    registerPolicy("oidc", "ttl1",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api", "read"), false, 1000);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "ttl1", "alice", "/api", Collections.singleton("read"), false));
    assertNull(decision.getDenyReason());
    // policy TTL=1000 is set so use policy value directly
    assertEquals(1000, decision.getEffectiveTtlSec());
  }

  @Test
  public void testEvaluateTtlConfiguredLowerThanPolicy() throws Exception {
    registerPolicy("oidc", "ttl2",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api", "read"), false, 10000);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "ttl2", "alice", "/api", Collections.singleton("read"), false));
    assertNull(decision.getDenyReason());
    // policy TTL=10000 is set so use policy value directly; configured default is irrelevant
    assertEquals(10000, decision.getEffectiveTtlSec());
  }

  @Test
  public void testEvaluateTtlNullPolicyUsesConfigured() throws Exception {
    registerPolicy("oidc", "ttl3",
        Collections.singleton("alice"), Collections.emptySet(),
        singleResourcePolicy("/api", "read"), false, null);

    final PolicyDecision decision = service.evaluate(
        new PolicyCheckRequest("oidc", "ttl3", "alice", "/api", Collections.singleton("read"), false));
    assertNull(decision.getDenyReason());
    assertEquals(CONFIGURED_TTL, decision.getEffectiveTtlSec());
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private JdbcDelegationPolicyService createServiceWithLimits(int maxTotal, int maxPerAuthority)
      throws ServiceLifecycleException {
    final GatewayConfig cfg = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(cfg.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(cfg.getDatabaseName()).andReturn("mem:" + DB_NAME + ";DB_CLOSE_DELAY=-1").anyTimes();
    EasyMock.expect(cfg.getDelegationServiceTokenTtlSec()).andReturn(CONFIGURED_TTL).anyTimes();
    EasyMock.expect(cfg.getDelegationServiceListMaxTotal()).andReturn(maxTotal).anyTimes();
    EasyMock.expect(cfg.getDelegationServiceListMaxPerAuthority()).andReturn(maxPerAuthority).anyTimes();
    EasyMock.replay(cfg);
    final JdbcDelegationPolicyService svc = new JdbcDelegationPolicyService();
    svc.setAliasService(aliasService);
    svc.init(cfg, null);
    return svc;
  }

  private void registerPolicy(String authority, String actorId,
      Set<String> users, Set<String> groups, Map<String, Set<String>> resourcePolicy) throws Exception {
    service.register(policy(authority, actorId, null, "active", null, null, null,
        Instant.now(), users, groups, resourcePolicy));
  }

  private void registerPolicy(String authority, String actorId,
      Set<String> users, Set<String> groups, Map<String, Set<String>> resourcePolicy,
      boolean allowHeadless) throws Exception {
    service.register(new DelegationPolicy(null, authority, actorId, null, "active", null, null, null,
        Instant.now(), Instant.now(), allowHeadless, users, groups, resourcePolicy));
  }

  private void registerPolicy(String authority, String actorId,
      Set<String> users, Set<String> groups, Map<String, Set<String>> resourcePolicy,
      boolean allowHeadless, Integer policyTtl) throws Exception {
    service.register(new DelegationPolicy(null, authority, actorId, null, "active", policyTtl, null, null,
        Instant.now(), Instant.now(), allowHeadless, users, groups, resourcePolicy));
  }

  private static DelegationPolicy policy(String actorAuthority, String actorId,
      String name, String status, Integer tokenTtlSec, String description, String createdBy,
      Instant createdAt, Set<String> users, Set<String> groups, Map<String, Set<String>> rp) {
    return new DelegationPolicy(
        null, actorAuthority, actorId, name, status, tokenTtlSec, description, createdBy,
        createdAt, createdAt, false, users, groups, rp);
  }

  private static Map<String, Set<String>> singleResourcePolicy(String resource, String... scopes) {
    final Map<String, Set<String>> rp = new HashMap<>();
    rp.put(resource, new HashSet<>(Arrays.asList(scopes)));
    return rp;
  }

  private static void clearTables() {
    try (Connection conn = DriverManager.getConnection(H2_URL)) {
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

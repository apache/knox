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
package org.apache.knox.gateway.services.knoxidf.trustedoidcissuer;

import org.apache.commons.lang3.reflect.FieldUtils;
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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JdbcTrustedOidcIssuerServiceTest {

  private static final String DB_NAME = "trustedissuers_svc_test";
  private static final String DERBY_CREATE_URL = "jdbc:derby:memory:" + DB_NAME + ";create=true";
  private static final String DERBY_URL = "jdbc:derby:memory:" + DB_NAME;
  private static final String DERBY_SHUTDOWN_URL = "jdbc:derby:memory:" + DB_NAME + ";shutdown=true";

  private GatewayConfig gatewayConfig;
  private AliasService aliasService;
  private JdbcTrustedOidcIssuerService service;

  @BeforeClass
  public static void setUpClass() throws Exception {
    // Derby 10.14 does not recognize locales like en_001; force a standard locale.
    java.util.Locale.setDefault(java.util.Locale.US);
    // Create the Derby in-memory DB so DerbyDataSourceFactory can connect to it
    DriverManager.getConnection(DERBY_CREATE_URL).close();
  }

  @AfterClass
  public static void tearDownClass() {
    try {
      DriverManager.getConnection(DERBY_SHUTDOWN_URL);
    } catch (SQLException e) {
      // Derby signals a successful in-memory shutdown as SQLState 08006 / error 45000
      if (!(e.getErrorCode() == 45000 && "08006".equals(e.getSQLState()))) {
        throw new RuntimeException("Unexpected Derby shutdown error", e);
      }
    }
  }

  @Before
  public void setUp() throws Exception {
    // Clear table between tests
    try (Connection conn = DriverManager.getConnection(DERBY_URL);
         PreparedStatement ps = conn.prepareStatement("DELETE FROM TRUSTED_OIDC_ISSUERS")) {
      ps.executeUpdate();
    } catch (SQLException e) {
      // Table may not exist yet on first setUp; service.init() will create it
    }

    gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.DERBY.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("memory:" + DB_NAME).anyTimes();
    EasyMock.expect(gatewayConfig.getTrustedOidcIssuerMaxTrustedIssuers() ).andReturn(10).anyTimes();
    EasyMock.replay(gatewayConfig);

    aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(
            AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(
            AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.replay(aliasService);

    service = new JdbcTrustedOidcIssuerService();
    service.setAliasService(aliasService);
    service.init(gatewayConfig, null);
  }

  // ------------------------------------------------------------------
  // Basic CRUD and snapshot
  // ------------------------------------------------------------------

  @Test
  public void testRegisterAndIsTrusted() {
    service.register(issuer("https://issuer.example.com", false));

    assertTrue(service.isTrusted("https://issuer.example.com"));
    assertFalse(service.isTrusted("https://other.example.com"));
  }

  @Test
  public void testDeregisterClearsSnapshot() {
    service.register(issuer("https://issuer.example.com", false));
    assertTrue(service.isTrusted("https://issuer.example.com"));

    service.deregister("https://issuer.example.com");
    assertFalse(service.isTrusted("https://issuer.example.com"));
  }

  @Test
  public void testListReflectsSnapshot() {
    final TrustedOidcIssuer a = issuer("https://a.example.com", false, "clusterA", "admin");
    final TrustedOidcIssuer b = issuer("https://b.example.com", true, "clusterB", "operator");
    service.register(a);
    service.register(b);

    final List<TrustedOidcIssuer> listed = service.list();
    assertEquals(2, listed.size());
    assertIssuerInList(a, listed);
    assertIssuerInList(b, listed);
  }

  @Test
  public void testDynamicJwksFlag() {
    service.register(issuer("https://static.example.com", false));
    service.register(issuer("https://dynamic.example.com", true));

    assertFalse(service.isDynamicJwks("https://static.example.com"));
    assertTrue(service.isDynamicJwks("https://dynamic.example.com"));
    assertFalse("Unregistered issuer must return false",
        service.isDynamicJwks("https://unknown.example.com"));
  }

  /**
   * All fields must round-trip through the DB correctly, including nullable ones.
   */
  @Test
  public void testRegisterPersistsAllFields() {
    final TrustedOidcIssuer issuer = issuer("https://issuer.example.com", true, "prod-cluster", "admin");
    service.register(issuer);

    final List<TrustedOidcIssuer> listed = service.list();
    assertEquals(1, listed.size());
    assertIssuerEquals(issuer, listed.get(0));
  }

  @Test
  public void testRegisterPersistsNullableFieldsAsNull() {
    // clusterName and registeredBy may be null
    final TrustedOidcIssuer issuer = new TrustedOidcIssuer(
        "https://issuer.example.com", false, null, Instant.now(), null);
    service.register(issuer);

    final TrustedOidcIssuer fromList = service.list().get(0);
    assertEquals("https://issuer.example.com", fromList.getIssuerUrl());
    assertFalse(fromList.isDynamicJwks());
    assertNotNull("registeredAt must always be persisted", fromList.getRegisteredAt());
    assertTrue("clusterName round-trips as null", fromList.getClusterName() == null
        || fromList.getClusterName().isEmpty());
    assertTrue("registeredBy round-trips as null", fromList.getRegisteredBy() == null
        || fromList.getRegisteredBy().isEmpty());
  }

  @Test
  public void testRegistrySnapshotWarmOnInit() throws Exception {
    // Pre-populate the TRUSTED_OIDC_ISSUERS table before initializing a new service
    final String preloadedUrl = "https://preloaded.example.com";
    try (Connection conn = DriverManager.getConnection(DERBY_URL);
         PreparedStatement ps = conn.prepareStatement(
             "INSERT INTO TRUSTED_OIDC_ISSUERS (issuer_url, dynamic_jwks, registered_at) "
                 + "VALUES (?, ?, ?)")) {
      ps.setString(1, preloadedUrl);
      ps.setBoolean(2, false);
      ps.setTimestamp(3, java.sql.Timestamp.from(Instant.now()));
      ps.executeUpdate();
    }

    // New service instance: snapshot must be loaded from DB on startup
    final JdbcTrustedOidcIssuerService freshService = new JdbcTrustedOidcIssuerService();
    freshService.setAliasService(aliasService);
    freshService.init(gatewayConfig, null);

    assertTrue("Pre-populated issuer must be trusted after init", freshService.isTrusted(preloadedUrl));
  }

  @Test(expected = RuntimeException.class)
  public void testDuplicateRegistrationThrows() {
    final TrustedOidcIssuer issuer = issuer("https://issuer.example.com", false);
    service.register(issuer);
    service.register(issuer); // duplicate primary key → RuntimeException
  }

  @Test
  public void testReloadAfterMutation() {
    final String url = "https://issuer.example.com";

    service.register(issuer(url, false));
    assertTrue("Snapshot must contain issuer after register", service.isTrusted(url));
    assertEquals(1, service.list().size());

    service.deregister(url);
    assertFalse("Snapshot must not contain issuer after deregister", service.isTrusted(url));
    assertTrue(service.list().isEmpty());
  }

  @Test
  public void testMaxTrustedIssuers() throws ServiceLifecycleException {
    final GatewayConfig limitedConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(limitedConfig.getDatabaseType()).andReturn(DatabaseType.DERBY.type()).anyTimes();
    EasyMock.expect(limitedConfig.getDatabaseName()).andReturn("memory:" + DB_NAME).anyTimes();
    EasyMock.expect(limitedConfig.getTrustedOidcIssuerMaxTrustedIssuers() ).andReturn(2).anyTimes();
    EasyMock.replay(limitedConfig);

    final JdbcTrustedOidcIssuerService limitedService = new JdbcTrustedOidcIssuerService();
    limitedService.setAliasService(aliasService);
    limitedService.init(limitedConfig, null);

    limitedService.register(issuer("https://a.example.com", false));
    assertEquals("First registration must succeed", 1, limitedService.list().size());

    limitedService.register(issuer("https://b.example.com", false));
    assertEquals("Second registration must succeed", 2, limitedService.list().size());

    assertThrows(IllegalStateException.class,
            () -> limitedService.register(issuer("https://c.example.com", false)));

    assertEquals("Prior registrations must be unaffected by the rejected call",
            2, limitedService.list().size());
  }

  @Test
  public void testDeregisterNonExistentIsNoOp() {
    // deregister of unknown issuer must not throw
    service.deregister("https://nonexistent.example.com");
    assertTrue(service.list().isEmpty());
  }

  // ------------------------------------------------------------------
  // resolveJwksUri / refreshJwksUri delegation
  // ------------------------------------------------------------------

  @Test
  public void testResolveJwksUriForNonDynamicIssuerReturnsEmpty() {
    service.register(issuer("https://static.example.com", false));
    // Non-dynamic issuer: OIDCDiscoveryHelper.discoverJwksUri returns empty immediately
    // without any HTTP call (the SSRF gate inside the helper blocks it).
    assertFalse(service.resolveJwksUri("https://static.example.com").isPresent());
  }

  @Test
  public void testResolveJwksUriForUnregisteredIssuerReturnsEmpty() {
    assertFalse(service.resolveJwksUri("https://unknown.example.com").isPresent());
  }

  @Test
  public void testRefreshJwksUriForNonDynamicIsNoOp() {
    service.register(issuer("https://static.example.com", false));
    // refreshJwksUri checks isDynamicJwks first; for non-dynamic it is a no-op
    service.refreshJwksUri("https://static.example.com"); // must not throw
  }

  @Test
  public void testRefreshJwksUriForUnregisteredIsNoOp() {
    service.refreshJwksUri("https://unknown.example.com"); // must not throw
  }

  // ------------------------------------------------------------------
  // SQL exception error paths
  // ------------------------------------------------------------------

  @Test(expected = RuntimeException.class)
  public void testDeregisterSqlExceptionOnDeleteThrowsRuntimeException() throws Exception {
    final TrustedOidcIssuerDatabase mockDb = EasyMock.createMock(TrustedOidcIssuerDatabase.class);
    mockDb.delete(EasyMock.anyString());
    EasyMock.expectLastCall().andThrow(new java.sql.SQLException("delete failed"));
    EasyMock.replay(mockDb);
    FieldUtils.writeField(service, "database", mockDb, true);

    service.deregister("https://any.example.com");
  }

  @Test(expected = RuntimeException.class)
  public void testRegisterSqlExceptionOnSnapshotReloadPropagates() throws Exception {
    final TrustedOidcIssuerDatabase mockDb = EasyMock.createMock(TrustedOidcIssuerDatabase.class);
    mockDb.insert(EasyMock.anyObject(TrustedOidcIssuer.class));
    EasyMock.expectLastCall();
    EasyMock.expect(mockDb.selectAll()).andThrow(new java.sql.SQLException("selectAll failed"));
    EasyMock.replay(mockDb);
    FieldUtils.writeField(service, "database", mockDb, true);

    service.register(issuer("https://any.example.com", false));
  }

  @Test(expected = RuntimeException.class)
  public void testDeregisterSqlExceptionOnSnapshotReloadPropagates() throws Exception {
    final TrustedOidcIssuerDatabase mockDb = EasyMock.createMock(TrustedOidcIssuerDatabase.class);
    mockDb.delete(EasyMock.anyString());
    EasyMock.expectLastCall();
    EasyMock.expect(mockDb.selectAll()).andThrow(new java.sql.SQLException("selectAll failed"));
    EasyMock.replay(mockDb);
    FieldUtils.writeField(service, "database", mockDb, true);

    service.deregister("https://any.example.com");
  }

  // ------------------------------------------------------------------
  // Init guard
  // ------------------------------------------------------------------

  @Test(expected = ServiceLifecycleException.class)
  public void testInitFailsWithoutAliasService() throws ServiceLifecycleException {
    final JdbcTrustedOidcIssuerService noAliasService = new JdbcTrustedOidcIssuerService();
    // setAliasService NOT called
    noAliasService.init(gatewayConfig, null);
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private static TrustedOidcIssuer issuer(String url, boolean dynamicJwks) {
    return new TrustedOidcIssuer(url, dynamicJwks, null, Instant.now(), null);
  }

  private static TrustedOidcIssuer issuer(String url, boolean dynamicJwks,
      String clusterName, String registeredBy) {
    return new TrustedOidcIssuer(url, dynamicJwks, clusterName, Instant.now(), registeredBy);
  }

  /**
   * Asserts that all non-generated fields of {@code expected} match {@code actual}, and
   * that the generated {@code registeredAt} field is non-null.
   */
  private static void assertIssuerEquals(TrustedOidcIssuer expected, TrustedOidcIssuer actual) {
    assertEquals("issuerUrl", expected.getIssuerUrl(), actual.getIssuerUrl());
    assertEquals("dynamicJwks", expected.isDynamicJwks(), actual.isDynamicJwks());
    assertEquals("clusterName", expected.getClusterName(), actual.getClusterName());
    assertEquals("registeredBy", expected.getRegisteredBy(), actual.getRegisteredBy());
    assertNotNull("registeredAt must be persisted", actual.getRegisteredAt());
  }

  private static void assertIssuerInList(TrustedOidcIssuer expected, List<TrustedOidcIssuer> list) {
    final TrustedOidcIssuer found = list.stream()
        .filter(i -> expected.getIssuerUrl().equals(i.getIssuerUrl()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Issuer not found in list: " + expected.getIssuerUrl()));
    assertIssuerEquals(expected, found);
  }
}

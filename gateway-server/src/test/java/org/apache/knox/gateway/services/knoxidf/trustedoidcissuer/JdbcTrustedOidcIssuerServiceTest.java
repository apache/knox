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

import javax.net.ssl.SSLSocketFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JdbcTrustedOidcIssuerServiceTest {

  private static final String DB_NAME = "trustedissuers_svc_test";
  // In-memory H2; DB_CLOSE_DELAY=-1 keeps the database alive across connection open/close for the
  // whole test class (the service and the direct JDBC cleanup below each open their own connections).
  private static final String H2_URL = "jdbc:h2:mem:" + DB_NAME + ";DB_CLOSE_DELAY=-1";

  // A held-open connection guarantees the in-memory database survives even when every other
  // connection is briefly closed between tests.
  private static Connection keepAlive;

  private GatewayConfig gatewayConfig;
  private AliasService aliasService;
  private JdbcTrustedOidcIssuerService service;

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
    // Clear table between tests
    try (Connection conn = DriverManager.getConnection(H2_URL);
         PreparedStatement ps = conn.prepareStatement("DELETE FROM TRUSTED_OIDC_ISSUERS")) {
      ps.executeUpdate();
    } catch (SQLException e) {
      // Table may not exist yet on first setUp; service.init() will create it
    }

    gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("mem:" + DB_NAME + ";DB_CLOSE_DELAY=-1").anyTimes();
    EasyMock.expect(gatewayConfig.getTrustedOidcIssuerMaxTrustedIssuers() ).andReturn(10).anyTimes();
    EasyMock.expect(gatewayConfig.getTrustedOidcIssuerCaSecretMaxAgeSecs())
        .andReturn(GatewayConfig.TRUSTED_OIDC_ISSUER_CA_SECRET_MAX_AGE_SECS_DEFAULT).anyTimes();
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
    try (Connection conn = DriverManager.getConnection(H2_URL);
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
    EasyMock.expect(limitedConfig.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(limitedConfig.getDatabaseName()).andReturn("mem:" + DB_NAME + ";DB_CLOSE_DELAY=-1").anyTimes();
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
  // jwksCa persistence and getJwksTrust
  // ------------------------------------------------------------------

  @Test
  public void testRegisterPersistsJwksCa() throws Exception {
    final String pem = TestCaBundles.toPem(TestCaBundles.selfSigned("CN=ca-a"), TestCaBundles.selfSigned("CN=ca-b"));
    service.register(issuer("https://inline.example.com", true, pem));
    service.register(issuer("https://secret.example.com", true, "secret:apiserver-ca#ca.crt"));
    service.register(issuer("https://none.example.com", true, null));

    // reload from the database, not just the in-memory snapshot
    final JdbcTrustedOidcIssuerService freshService = new JdbcTrustedOidcIssuerService();
    freshService.setAliasService(aliasService);
    freshService.init(gatewayConfig, null);
    final Map<String, String> jwksCas = new HashMap<>();
    freshService.list().forEach(i -> jwksCas.put(i.getIssuerUrl(), i.getJwksCa()));

    assertEquals(pem, jwksCas.get("https://inline.example.com"));
    assertEquals("secret:apiserver-ca#ca.crt", jwksCas.get("https://secret.example.com"));
    assertTrue(jwksCas.containsKey("https://none.example.com"));
    assertNull(jwksCas.get("https://none.example.com"));
  }

  @Test
  public void testGetJwksTrustWithoutJwksCaIsEmpty() {
    service.register(issuer("https://issuer.example.com", true));
    assertFalse(service.getJwksTrust("https://issuer.example.com").isPresent());
    assertFalse(service.getJwksTrust("https://unknown.example.com").isPresent());
  }

  @Test
  public void testGetJwksTrustInlinePem() throws Exception {
    service.register(issuer("https://issuer.example.com", true,
        TestCaBundles.toPem(TestCaBundles.selfSigned("CN=ca-a"))));

    final IssuerJwksTrust trust = service.getJwksTrust("https://issuer.example.com").orElseThrow();
    assertNotNull(trust.getSslSocketFactory());
    assertSame(trust, service.getJwksTrust("https://issuer.example.com").orElseThrow());
  }

  @Test
  public void testGetJwksTrustSecretReferenceRefreshAndDeregister() throws Exception {
    final List<String> reads = new ArrayList<>();
    final AtomicReference<String> secretPem = new AtomicReference<>(TestCaBundles.toPem(TestCaBundles.selfSigned("CN=ca-a")));
    final JdbcTrustedOidcIssuerService secretService = serviceWithSecretReader(reference -> {
      reads.add(reference.toString());
      return secretPem.get();
    });
    final String url = "https://issuer.example.com";
    secretService.register(issuer(url, true, "secret:cluster-1/apiserver-ca#ca.crt"));
    assertTrue("the Secret is read lazily, not on register", reads.isEmpty());

    final IssuerJwksTrust trust = secretService.getJwksTrust(url).orElseThrow();
    final SSLSocketFactory factory = trust.getSslSocketFactory();
    assertSame(trust, secretService.getJwksTrust(url).orElseThrow());
    assertEquals(List.of("cluster-1/apiserver-ca#ca.crt"), reads);

    // POST /trusted-oidc-issuers/refresh-jwks re-reads the Secret at once
    secretPem.set(TestCaBundles.toPem(TestCaBundles.selfSigned("CN=ca-b")));
    secretService.refreshJwksUri(url);
    final IssuerJwksTrust refreshed = secretService.getJwksTrust(url).orElseThrow();
    assertNotSame(factory, refreshed.getSslSocketFactory());
    assertEquals(2, reads.size());

    secretService.deregister(url);
    assertFalse(secretService.getJwksTrust(url).isPresent());
    secretService.stop();
  }

  @Test
  public void testGetJwksTrustUnavailableSecretThrows() throws Exception {
    final JdbcTrustedOidcIssuerService secretService = serviceWithSecretReader(reference -> {
      throw new IllegalStateException("Secret " + reference.namespace() + "/" + reference.name() + " not found");
    });
    secretService.register(issuer("https://issuer.example.com", true, "secret:apiserver-ca#ca.crt"));

    final IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> secretService.getJwksTrust("https://issuer.example.com"));
    assertTrue(e.getMessage(), e.getMessage().contains("knox/apiserver-ca not found"));
    secretService.stop();
  }

  @Test
  public void testReRegisterWithNewCaGetsNewTrust() throws Exception {
    final String url = "https://issuer.example.com";
    service.register(issuer(url, true, TestCaBundles.toPem(TestCaBundles.selfSigned("CN=ca-a"))));
    final IssuerJwksTrust trust = service.getJwksTrust(url).orElseThrow();
    final SSLSocketFactory factory = trust.getSslSocketFactory();

    // an inline CA is rotated by re-registering the issuer (delete + create)
    service.deregister(url);
    service.register(issuer(url, true, TestCaBundles.toPem(TestCaBundles.selfSigned("CN=ca-b"))));
    final IssuerJwksTrust rotated = service.getJwksTrust(url).orElseThrow();
    assertNotSame(trust, rotated);
    assertNotSame(factory, rotated.getSslSocketFactory());
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
    useDatabase(service, mockDb);

    service.deregister("https://any.example.com");
  }

  @Test(expected = RuntimeException.class)
  public void testRegisterSqlExceptionOnSnapshotReloadPropagates() throws Exception {
    final TrustedOidcIssuerDatabase mockDb = EasyMock.createMock(TrustedOidcIssuerDatabase.class);
    mockDb.insert(EasyMock.anyObject(TrustedOidcIssuer.class));
    EasyMock.expectLastCall();
    EasyMock.expect(mockDb.selectAll()).andThrow(new java.sql.SQLException("selectAll failed"));
    EasyMock.replay(mockDb);
    useDatabase(service, mockDb);

    service.register(issuer("https://any.example.com", false));
  }

  @Test(expected = RuntimeException.class)
  public void testDeregisterSqlExceptionOnSnapshotReloadPropagates() throws Exception {
    final TrustedOidcIssuerDatabase mockDb = EasyMock.createMock(TrustedOidcIssuerDatabase.class);
    mockDb.delete(EasyMock.anyString());
    EasyMock.expectLastCall();
    EasyMock.expect(mockDb.selectAll()).andThrow(new java.sql.SQLException("selectAll failed"));
    EasyMock.replay(mockDb);
    useDatabase(service, mockDb);

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

  /**
   * Review finding M5: init() must re-check {@code initialized} inside the lock so a thread that
   * blocked while another was initialising does not re-initialise (overwriting the already-built
   * database/discoveryHelper). This deterministically reproduces the race window: the test thread
   * holds the init lock and marks the service initialised (as a "winning" thread would) while a
   * second init() call is blocked entering the critical section; when it proceeds, the inner recheck
   * must make it a no-op and leave the sentinel database reference untouched.
   */
  @Test
  public void testConcurrentInitDoesNotReinitialize() throws Exception {
    final ReentrantLock initLock = (ReentrantLock) FieldUtils.readField(service, "initLock", true);
    final AtomicBoolean initialized = (AtomicBoolean) FieldUtils.readField(service, "initialized", true);

    // A sentinel that the losing init() must NOT overwrite if the inner recheck is present.
    final TrustedOidcIssuerDatabase sentinel = EasyMock.createNiceMock(TrustedOidcIssuerDatabase.class);
    FieldUtils.writeField(service, "database", sentinel, true);

    // Simulate the pre-init state a second racing thread would have observed at the outer check.
    initialized.set(false);

    // Hold the lock first, then start the racing init(): it passes the outer !initialized check and
    // blocks entering the critical section until we release the lock.
    initLock.lock();
    final AtomicBoolean workerFailed = new AtomicBoolean(false);
    final Thread worker = new Thread(() -> {
      try {
        service.init(gatewayConfig, null);
      } catch (Exception e) {
        workerFailed.set(true);
      }
    });
    try {
      worker.start();
      // Wait until the worker is actually blocked in the lock queue, i.e. it entered init() while
      // initialized was still false (the exact race the inner recheck must defend against).
      final long deadline = System.currentTimeMillis() + 10_000;
      while (!initLock.hasQueuedThreads() && System.currentTimeMillis() < deadline) {
        Thread.yield();
      }
      assertTrue("Worker should be blocked entering the init critical section", initLock.hasQueuedThreads());
      // Mimic the winning thread finishing initialisation while we hold the lock.
      initialized.set(true);
    } finally {
      initLock.unlock();
    }
    worker.join(10_000);

    assertFalse("Racing init() must not have thrown", workerFailed.get());
    assertFalse("Worker thread must have finished", worker.isAlive());
    assertSame("A second init() must not rebuild the database once initialised (inner recheck)",
        sentinel, FieldUtils.readField(service, "database", true));
  }

  // ------------------------------------------------------------------
  // Registry snapshot TTL (HA eventual consistency)
  // ------------------------------------------------------------------
  // Direct JDBC writes stand in for another Knox node writing to the shared table. With a TTL of
  // 10s the effective (jittered) TTL is in [9s, 10s] and the background refresh is due after at
  // most 5s, so +6s means "refresh due, not expired" and +11s means "expired".

  private static final int TTL_SECS = 10;

  @Test
  public void testRemoteRegistrationVisibleAfterBackgroundRefresh() throws Exception {
    final AtomicLong now = new AtomicLong();
    final JdbcTrustedOidcIssuerService ttlService = ttlService(TTL_SECS, now);
    insertRow("https://remote.example.com", false, Instant.now());

    assertFalse("A fresh snapshot is served without hitting the DB",
        ttlService.isTrusted("https://remote.example.com"));

    advance(now, 6);
    ttlService.isTrusted("https://remote.example.com"); // triggers the background refresh, served stale
    assertTrue("Remote registration must be visible once the background refresh completed",
        ttlService.isTrusted("https://remote.example.com"));
  }

  @Test
  public void testRemoteDeregistrationNeverServedAfterTtl() throws Exception {
    final AtomicLong now = new AtomicLong();
    final JdbcTrustedOidcIssuerService ttlService = ttlService(TTL_SECS, now);
    ttlService.register(issuer("https://issuer.example.com", false));
    deleteRow("https://issuer.example.com");

    // No read in between, so no background refresh ran: the expired snapshot must not be served
    advance(now, 11);
    assertFalse("A revoked issuer must not be trusted once the TTL elapsed",
        ttlService.isTrusted("https://issuer.example.com"));
  }

  @Test
  public void testNonPositiveTtlFallsBackToDefault() throws Exception {
    final AtomicLong now = new AtomicLong();
    final JdbcTrustedOidcIssuerService ttlService = ttlService(0, now);
    insertRow("https://remote.example.com", false, Instant.now());

    // Default TTL is 60s: effective TTL in [54s, 60s], refresh due after at least 27s
    advance(now, 25);
    ttlService.isTrusted("https://remote.example.com");
    assertFalse(ttlService.isTrusted("https://remote.example.com"));

    advance(now, 36);
    assertTrue(ttlService.isTrusted("https://remote.example.com"));
  }

  /**
   * DB outage: the snapshot is served stale until the TTL, a failed refresh neither extends the
   * TTL nor is retried on every request, then the registry fails closed and recovers once the DB
   * is readable again.
   */
  @Test
  public void testDatabaseOutageServesStaleThenFailsClosedThenRecovers() throws Exception {
    final AtomicLong now = new AtomicLong();
    final JdbcTrustedOidcIssuerService ttlService = ttlService(TTL_SECS, now);
    ttlService.register(issuer("https://issuer.example.com", false));
    final TrustedOidcIssuerDatabase realDatabase =
        (TrustedOidcIssuerDatabase) FieldUtils.readField(ttlService, "database", true);

    final TrustedOidcIssuerDatabase failingDb = EasyMock.createMock(TrustedOidcIssuerDatabase.class);
    EasyMock.expect(failingDb.selectAll()).andThrow(new SQLException("database is down")).times(2);
    EasyMock.replay(failingDb);
    useDatabase(ttlService, failingDb);

    advance(now, 6);
    for (int i = 0; i < 5; i++) {
      assertTrue("The cached snapshot must be served while it is younger than the TTL",
          ttlService.isTrusted("https://issuer.example.com"));
    }

    advance(now, 5); // 11s after the last successful load, although the failed refresh ran at 6s
    assertFalse("The registry must fail closed once the snapshot expired",
        ttlService.isTrusted("https://issuer.example.com"));
    assertTrue(ttlService.list().isEmpty());
    assertTrue(registrySnapshotLoader(ttlService).isFailedClosed());
    // One failed background refresh at 6s plus one failed load at 11s: no per-request DB retries
    EasyMock.verify(failingDb);

    useDatabase(ttlService, realDatabase);
    advance(now, 6);
    ttlService.isTrusted("https://issuer.example.com"); // triggers the background refresh
    assertTrue("The registry must recover once the DB is readable again",
        ttlService.isTrusted("https://issuer.example.com"));
    assertFalse(registrySnapshotLoader(ttlService).isFailedClosed());
  }

  @Test
  public void testBackgroundRefreshInvalidatesDiscoveryOnlyForChangedIssuers() throws Exception {
    final AtomicLong now = new AtomicLong();
    final JdbcTrustedOidcIssuerService ttlService = ttlService(TTL_SECS, now);
    ttlService.register(issuer("https://changed.example.com", true));
    ttlService.register(issuer("https://removed.example.com", true));
    ttlService.register(issuer("https://unchanged.example.com", true));
    final RecordingDiscoveryHelper discovery = recordingDiscoveryHelper(ttlService);

    // Another node re-registers one issuer, deregisters another and registers a new one
    deleteRow("https://changed.example.com");
    insertRow("https://changed.example.com", true, Instant.now().plusSeconds(1));
    deleteRow("https://removed.example.com");
    insertRow("https://added.example.com", true, Instant.now());

    advance(now, 6);
    ttlService.isTrusted("https://added.example.com");

    assertEquals(new HashSet<>(Arrays.asList(
            "https://changed.example.com", "https://removed.example.com", "https://added.example.com")),
        new HashSet<>(discovery.invalidated));
    assertFalse(discovery.invalidatedAll.get());
  }

  @Test
  public void testLoadAfterExpiryInvalidatesAllDiscoveryEntries() throws Exception {
    final AtomicLong now = new AtomicLong();
    final JdbcTrustedOidcIssuerService ttlService = ttlService(TTL_SECS, now);
    ttlService.register(issuer("https://issuer.example.com", true));
    final RecordingDiscoveryHelper discovery = recordingDiscoveryHelper(ttlService);

    advance(now, 11);
    assertTrue(ttlService.isTrusted("https://issuer.example.com"));
    assertTrue("Without a previous snapshot to diff against, every discovery entry must be evicted",
        discovery.invalidatedAll.get());
  }

  @Test
  public void testLocalWriteInvalidatesDiscoveryForReRegisteredIssuer() throws Exception {
    service.register(issuer("https://issuer.example.com", true));
    final RecordingDiscoveryHelper discovery = recordingDiscoveryHelper(service);

    service.deregister("https://issuer.example.com");
    assertTrue(discovery.invalidated.contains("https://issuer.example.com"));

    discovery.invalidated.clear();
    service.register(issuer("https://issuer.example.com", true));
    assertTrue(discovery.invalidated.contains("https://issuer.example.com"));
  }

  @Test
  public void testRemoteReRegistrationRereadsSecretAfterBackgroundRefresh() throws Exception {
    final List<String> reads = new ArrayList<>();
    final String pem = TestCaBundles.toPem(TestCaBundles.selfSigned("CN=ca-a"));
    final AtomicLong now = new AtomicLong();
    final JdbcTrustedOidcIssuerService ttlService = ttlService(TTL_SECS, now, reference -> {
      reads.add(reference.toString());
      return pem;
    });
    final String url = "https://issuer.example.com";
    final String jwksCa = "secret:cluster-1/apiserver-ca#ca.crt";
    ttlService.register(issuer(url, true, jwksCa));
    final IssuerJwksTrust trust = ttlService.getJwksTrust(url).orElseThrow();
    assertEquals(1, reads.size());

    // Another node re-registers the issuer with the same Secret reference
    deleteRow(url);
    insertRow(url, true, Instant.now().plusSeconds(1), jwksCa);

    advance(now, 6);
    ttlService.isTrusted(url);

    assertNotSame("A re-registration on another node must discard the CA trust, as a local one does",
        trust, ttlService.getJwksTrust(url).orElseThrow());
    assertEquals(2, reads.size());
    ttlService.stop();
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private JdbcTrustedOidcIssuerService ttlService(int ttlSecs, AtomicLong now) throws ServiceLifecycleException {
    return ttlService(ttlSecs, now, null);
  }

  private JdbcTrustedOidcIssuerService ttlService(int ttlSecs, AtomicLong now,
      IssuerTrustProvider.SecretReader secretReader) throws ServiceLifecycleException {
    final GatewayConfig ttlConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(ttlConfig.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(ttlConfig.getDatabaseName()).andReturn("mem:" + DB_NAME + ";DB_CLOSE_DELAY=-1").anyTimes();
    EasyMock.expect(ttlConfig.getTrustedOidcIssuerMaxTrustedIssuers()).andReturn(10).anyTimes();
    EasyMock.expect(ttlConfig.getTrustedOidcIssuerRegistryCacheTtlSecs()).andReturn(ttlSecs).anyTimes();
    EasyMock.expect(ttlConfig.getTrustedOidcIssuerCaSecretMaxAgeSecs())
        .andReturn(GatewayConfig.TRUSTED_OIDC_ISSUER_CA_SECRET_MAX_AGE_SECS_DEFAULT).anyTimes();
    EasyMock.replay(ttlConfig);

    // Controlled time; background refreshes run on the calling thread
    final JdbcTrustedOidcIssuerService ttlService = new JdbcTrustedOidcIssuerService(now::get, Runnable::run);
    ttlService.setAliasService(aliasService);
    if (secretReader != null) {
      ttlService.setSecretReader(secretReader);
    }
    ttlService.init(ttlConfig, null);
    return ttlService;
  }

  private static void advance(AtomicLong now, long seconds) {
    now.addAndGet(TimeUnit.SECONDS.toNanos(seconds));
  }

  private static void insertRow(String url, boolean dynamicJwks, Instant registeredAt) throws SQLException {
    insertRow(url, dynamicJwks, registeredAt, null);
  }

  private static void insertRow(String url, boolean dynamicJwks, Instant registeredAt, String jwksCa)
      throws SQLException {
    try (Connection conn = DriverManager.getConnection(H2_URL);
         PreparedStatement ps = conn.prepareStatement(
             "INSERT INTO TRUSTED_OIDC_ISSUERS (issuer_url, dynamic_jwks, registered_at, jwks_ca) VALUES (?, ?, ?, ?)")) {
      ps.setString(1, url);
      ps.setBoolean(2, dynamicJwks);
      ps.setTimestamp(3, java.sql.Timestamp.from(registeredAt));
      ps.setString(4, jwksCa);
      ps.executeUpdate();
    }
  }

  private static void deleteRow(String url) throws SQLException {
    try (Connection conn = DriverManager.getConnection(H2_URL);
         PreparedStatement ps = conn.prepareStatement("DELETE FROM TRUSTED_OIDC_ISSUERS WHERE issuer_url = ?")) {
      ps.setString(1, url);
      ps.executeUpdate();
    }
  }

  private static RecordingDiscoveryHelper recordingDiscoveryHelper(JdbcTrustedOidcIssuerService target)
      throws IllegalAccessException {
    final RecordingDiscoveryHelper discovery = new RecordingDiscoveryHelper(target);
    FieldUtils.writeField(target, "discoveryHelper", discovery, true);
    FieldUtils.writeField(registrySnapshotLoader(target), "discoveryHelper", discovery, true);
    return discovery;
  }

  /** Replaces the database both for the service's writes and for its registry snapshot reads. */
  private static void useDatabase(JdbcTrustedOidcIssuerService target, TrustedOidcIssuerDatabase database)
      throws IllegalAccessException {
    FieldUtils.writeField(target, "database", database, true);
    FieldUtils.writeField(registrySnapshotLoader(target), "database", database, true);
  }

  private static RegistrySnapshotLoader registrySnapshotLoader(JdbcTrustedOidcIssuerService target)
      throws IllegalAccessException {
    return (RegistrySnapshotLoader) FieldUtils.readField(target, "registrySnapshotLoader", true);
  }

  /** Records discovery cache invalidations instead of evicting anything. */
  private static final class RecordingDiscoveryHelper extends OIDCDiscoveryHelper {
    final List<String> invalidated = new ArrayList<>();
    final AtomicBoolean invalidatedAll = new AtomicBoolean(false);

    RecordingDiscoveryHelper(TrustedOidcIssuerService trustedIssuers) {
      super(trustedIssuers, 600L, null);
    }

    @Override
    void invalidate(String issuerUrl) {
      invalidated.add(issuerUrl);
    }

    @Override
    void invalidateAll() {
      invalidatedAll.set(true);
    }
  }

  private static TrustedOidcIssuer issuer(String url, boolean dynamicJwks) {
    return new TrustedOidcIssuer(url, dynamicJwks, null, Instant.now(), null);
  }

  private static TrustedOidcIssuer issuer(String url, boolean dynamicJwks, String jwksCa) {
    return new TrustedOidcIssuer(url, dynamicJwks, null, Instant.now(), null, jwksCa);
  }

  private JdbcTrustedOidcIssuerService serviceWithSecretReader(IssuerTrustProvider.SecretReader secretReader)
      throws ServiceLifecycleException {
    final JdbcTrustedOidcIssuerService secretService = new JdbcTrustedOidcIssuerService();
    secretService.setAliasService(aliasService);
    secretService.setSecretReader(secretReader);
    secretService.init(gatewayConfig, null);
    return secretService;
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
    assertEquals("jwksCa", expected.getJwksCa(), actual.getJwksCa());
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

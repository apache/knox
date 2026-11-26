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
package org.apache.knox.gateway.services.knoxidf.flowstate;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.database.AbstractDataSourceFactory;
import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JdbcAuthzFlowStateServiceTest {

  private static final String DB_NAME = "authz_flow_state_svc_test";
  private static final String H2_URL = "jdbc:h2:mem:" + DB_NAME + ";DB_CLOSE_DELAY=-1";
  private static final int TTL_SECS = 60;

  // Keeps the in-memory database alive while the services open and close their own connections.
  private static Connection keepAlive;

  private final TestClock clock = new TestClock(1_000_000L);
  private JdbcAuthzFlowStateService service;

  @BeforeClass
  public static void setUpClass() throws Exception {
    keepAlive = DriverManager.getConnection(H2_URL);
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    try (Statement stmt = keepAlive.createStatement()) {
      stmt.execute("DROP ALL OBJECTS");
    }
    keepAlive.close();
  }

  @Before
  public void setUp() throws Exception {
    service = createService(clock);
  }

  @After
  public void tearDown() throws Exception {
    service.stop();
    try (Statement stmt = keepAlive.createStatement()) {
      stmt.execute("DELETE FROM " + AuthzFlowStateDatabase.TABLE_NAME);
    }
  }

  @Test
  public void testInitRequiresAliasService() {
    final JdbcAuthzFlowStateService noAliasService = new JdbcAuthzFlowStateService();
    assertThrows(ServiceLifecycleException.class, () -> noAliasService.init(createConfig(0), Collections.emptyMap()));
  }

  @Test
  public void testPutAndGet() {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "payload");
    assertEquals(Optional.of("payload"), service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id"));
    assertEquals(Optional.of("payload"), service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id"));
    assertFalse(service.get(AuthzFlowStateKind.FEDERATED_OP_SELECTION, "id").isPresent());
  }

  @Test
  public void testPutReplacesAndRestartsTtl() throws Exception {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "first");
    clock.advanceMillis(TimeUnit.SECONDS.toMillis(TTL_SECS - 1));
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "second");
    clock.advanceMillis(TimeUnit.SECONDS.toMillis(TTL_SECS - 1));
    assertEquals(Optional.of("second"), service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id"));
    assertEquals(1, countRows());
  }

  @Test
  public void testLargePayload() {
    final String payload = "x".repeat(64 * 1024);
    service.put(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, "id", payload);
    assertEquals(Optional.of(payload), service.get(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, "id"));
  }

  @Test
  public void testExpiredEntryIsAbsent() {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "payload");
    clock.advanceMillis(TimeUnit.SECONDS.toMillis(TTL_SECS));
    assertFalse(service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id").isPresent());
    assertFalse(service.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id").isPresent());
  }

  @Test
  public void testConsumeReturnsPayloadOnce() {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "payload");
    assertEquals(Optional.of("payload"), service.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id"));
    assertFalse(service.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id").isPresent());
    assertFalse(service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id").isPresent());
  }

  @Test
  public void testStateIsSharedBetweenInstances() throws Exception {
    final JdbcAuthzFlowStateService otherInstance = createService(clock);
    try {
      service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "payload");
      assertEquals(Optional.of("payload"), otherInstance.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id"));
      assertFalse("consumed on the other instance", service.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id").isPresent());
    } finally {
      otherInstance.stop();
    }
  }

  @Test
  public void testConcurrentConsumeAcrossInstancesSucceedsOnce() throws Exception {
    final int threads = 8;
    final List<JdbcAuthzFlowStateService> instances = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      instances.add(createService(clock));
    }
    final ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      for (int round = 0; round < 20; round++) {
        final String id = "id-" + round;
        service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, id, "payload");
        final CountDownLatch start = new CountDownLatch(1);
        final List<Callable<Optional<String>>> tasks = new ArrayList<>();
        for (JdbcAuthzFlowStateService instance : instances) {
          tasks.add(() -> {
            start.await();
            return instance.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, id);
          });
        }
        final List<Future<Optional<String>>> results = new ArrayList<>();
        for (Callable<Optional<String>> task : tasks) {
          results.add(executor.submit(task));
        }
        start.countDown();
        int winners = 0;
        for (Future<Optional<String>> result : results) {
          if (result.get().isPresent()) {
            winners++;
          }
        }
        assertEquals("round " + round, 1, winners);
      }
    } finally {
      executor.shutdownNow();
      for (JdbcAuthzFlowStateService instance : instances) {
        instance.stop();
      }
    }
  }

  @Test
  public void testRemove() {
    service.put(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, "id", "payload");
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "payload");
    service.remove(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, "id");
    assertFalse(service.get(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, "id").isPresent());
    assertTrue("only the given kind is removed", service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id").isPresent());
  }

  @Test
  public void testNullIdIsAbsent() {
    assertFalse(service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, null).isPresent());
    assertFalse(service.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, null).isPresent());
    service.remove(AuthzFlowStateKind.AUTHORIZE_REQUEST, null);
  }

  @Test
  public void testRemoveExpired() throws Exception {
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "old", "payload");
    clock.advanceMillis(TimeUnit.SECONDS.toMillis(TTL_SECS / 2));
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "new", "payload");
    clock.advanceMillis(TimeUnit.SECONDS.toMillis(TTL_SECS / 2));
    assertEquals(1, service.removeExpired());
    assertEquals(1, countRows());
    assertTrue(service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "new").isPresent());
  }

  @Test
  public void testDatabaseFailure() throws Exception {
    final String payload = "payload";
    try (Statement stmt = keepAlive.createStatement()) {
      stmt.execute("ALTER TABLE " + AuthzFlowStateDatabase.TABLE_NAME + " RENAME TO KNOXIDF_AUTHZ_FLOW_STATE_MOVED");
    }
    try {
      assertThrows(AuthzFlowStateServiceException.class, () -> service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", payload));
      // A failed read must not look like an absent entry
      assertThrows(AuthzFlowStateServiceException.class, () -> service.get(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id"));
      assertThrows(AuthzFlowStateServiceException.class, () -> service.consume(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id"));
      // Clean-up failures are only logged
      service.remove(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id");
      assertEquals(0, service.removeExpired());
    } finally {
      try (Statement stmt = keepAlive.createStatement()) {
        stmt.execute("ALTER TABLE KNOXIDF_AUTHZ_FLOW_STATE_MOVED RENAME TO " + AuthzFlowStateDatabase.TABLE_NAME);
      }
    }
  }

  @Test
  public void testSweeperRemovesExpiredEntries() throws Exception {
    final JdbcAuthzFlowStateService sweeping = createService(clock, 1);
    try {
      sweeping.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "payload");
      clock.advanceMillis(TimeUnit.SECONDS.toMillis(TTL_SECS));
      final long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
      while (countRows() > 0 && System.currentTimeMillis() < deadline) {
        Thread.sleep(100);
      }
      assertEquals(0, countRows());
    } finally {
      sweeping.stop();
    }
  }

  private static JdbcAuthzFlowStateService createService(Clock clock) throws Exception {
    // no background sweeper: the tests call removeExpired() themselves
    return createService(clock, 0);
  }

  private static JdbcAuthzFlowStateService createService(Clock clock, int cleanupIntervalSecs) throws Exception {
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.replay(aliasService);
    final JdbcAuthzFlowStateService jdbcService = new JdbcAuthzFlowStateService(clock);
    jdbcService.setAliasService(aliasService);
    jdbcService.init(createConfig(cleanupIntervalSecs), Collections.emptyMap());
    jdbcService.start();
    return jdbcService;
  }

  private static GatewayConfig createConfig(int cleanupIntervalSecs) {
    final GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(config.getDatabaseName()).andReturn("mem:" + DB_NAME + ";DB_CLOSE_DELAY=-1").anyTimes();
    EasyMock.expect(config.getKnoxIDFAuthzFlowStateTtlSecs()).andReturn(TTL_SECS).anyTimes();
    EasyMock.expect(config.getKnoxIDFAuthzFlowStateCleanupIntervalSecs()).andReturn(cleanupIntervalSecs).anyTimes();
    EasyMock.replay(config);
    return config;
  }

  private static int countRows() throws Exception {
    try (Statement stmt = keepAlive.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + AuthzFlowStateDatabase.TABLE_NAME)) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private static final class TestClock extends Clock {
    private final AtomicLong millis;

    private TestClock(long millis) {
      this.millis = new AtomicLong(millis);
    }

    private void advanceMillis(long delta) {
      millis.addAndGet(delta);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public long millis() {
      return millis.get();
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis());
    }
  }
}

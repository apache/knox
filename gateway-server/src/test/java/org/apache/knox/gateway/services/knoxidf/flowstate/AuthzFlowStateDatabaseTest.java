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

import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.database.JDBCUtils;
import org.apache.knox.gateway.services.knoxidf.flowstate.AuthzFlowStateDatabase.ConsumeStrategy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Runs the {@link AuthzFlowStateDatabase} operations on H2 with each consume strategy H2 can
 * execute. {@link ConsumeStrategy#DELETE_RETURNING} needs PostgreSQL or MariaDB.
 */
@RunWith(Parameterized.class)
public class AuthzFlowStateDatabaseTest {

  private static final String KIND = "authz_request";
  private static final long NOW = 1_000_000L;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> strategies() {
    return Arrays.asList(new Object[][] {
        {ConsumeStrategy.DELETE_FROM_OLD_TABLE},
        {ConsumeStrategy.SELECT_THEN_DELETE}
    });
  }

  private final ConsumeStrategy strategy;
  private JdbcDataSource dataSource;
  // Keeps the in-memory database alive between the unpooled connections of each operation.
  private Connection keepAlive;
  private AuthzFlowStateDatabase database;

  public AuthzFlowStateDatabaseTest(ConsumeStrategy strategy) {
    this.strategy = strategy;
  }

  @Before
  public void setUp() throws Exception {
    dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:authz_flow_state_db_" + strategy + ";DB_CLOSE_DELAY=-1");
    keepAlive = dataSource.getConnection();
    database = createDatabase();
  }

  @After
  public void tearDown() throws Exception {
    try (Statement stmt = keepAlive.createStatement()) {
      stmt.execute("DROP ALL OBJECTS");
    }
    keepAlive.close();
  }

  @Test
  public void testStrategyForDatabase() {
    assertEquals(ConsumeStrategy.DELETE_RETURNING, ConsumeStrategy.forDatabase(DatabaseType.POSTGRESQL));
    assertEquals(ConsumeStrategy.DELETE_RETURNING, ConsumeStrategy.forDatabase(DatabaseType.MARIADB));
    assertEquals(ConsumeStrategy.DELETE_FROM_OLD_TABLE, ConsumeStrategy.forDatabase(DatabaseType.H2));
    assertEquals(ConsumeStrategy.SELECT_THEN_DELETE, ConsumeStrategy.forDatabase(DatabaseType.MYSQL));
    assertEquals(ConsumeStrategy.SELECT_THEN_DELETE, ConsumeStrategy.forDatabase(DatabaseType.ORACLE));
  }

  @Test
  public void testExpiresAtIndexIsCreatedIdempotently() throws Exception {
    assertTrue(JDBCUtils.indexExists(AuthzFlowStateDatabase.TABLE_NAME,
        AuthzFlowStateDatabase.EXPIRES_AT_INDEX_NAME, dataSource));
    createDatabase();
    assertTrue(JDBCUtils.indexExists(AuthzFlowStateDatabase.TABLE_NAME,
        AuthzFlowStateDatabase.EXPIRES_AT_INDEX_NAME, dataSource));
  }

  @Test
  public void testExpiresAtIndexIsAddedToExistingTable() throws Exception {
    try (Statement stmt = keepAlive.createStatement()) {
      stmt.execute("DROP INDEX " + AuthzFlowStateDatabase.EXPIRES_AT_INDEX_NAME);
    }
    assertFalse(JDBCUtils.indexExists(AuthzFlowStateDatabase.TABLE_NAME,
        AuthzFlowStateDatabase.EXPIRES_AT_INDEX_NAME, dataSource));
    createDatabase();
    assertTrue(JDBCUtils.indexExists(AuthzFlowStateDatabase.TABLE_NAME,
        AuthzFlowStateDatabase.EXPIRES_AT_INDEX_NAME, dataSource));
  }

  @Test
  public void testUpsertInsertsAndReplaces() throws Exception {
    database.upsert(KIND, "id", "first", NOW + 10);
    database.upsert(KIND, "id", "second", NOW + 20);
    assertEquals(Optional.of("second"), database.select(KIND, "id", NOW + 15));
    assertEquals(1, countRows());
  }

  @Test
  public void testUpsertReplacesExpiredRow() throws Exception {
    database.upsert(KIND, "id", "first", NOW - 10);
    database.upsert(KIND, "id", "second", NOW + 10);
    assertEquals(Optional.of("second"), database.select(KIND, "id", NOW));
  }

  @Test
  public void testConsumeReturnsPayloadOnce() throws Exception {
    database.upsert(KIND, "id", "payload", NOW + 10);
    assertEquals(Optional.of("payload"), database.consume(KIND, "id", NOW));
    assertFalse(database.consume(KIND, "id", NOW).isPresent());
    assertEquals(0, countRows());
  }

  @Test
  public void testConsumeIgnoresExpiredAndOtherKinds() throws Exception {
    database.upsert(KIND, "expired", "payload", NOW);
    database.upsert("other_kind", "id", "payload", NOW + 10);
    assertFalse(database.consume(KIND, "expired", NOW).isPresent());
    assertFalse(database.consume(KIND, "id", NOW).isPresent());
    assertEquals(2, countRows());
  }

  @Test
  public void testConcurrentConsumeSucceedsOnce() throws Exception {
    final int threads = 8;
    final List<AuthzFlowStateDatabase> instances = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      instances.add(createDatabase());
    }
    final ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      for (int round = 0; round < 50; round++) {
        final String id = "id-" + round;
        database.upsert(KIND, id, "payload", NOW + 10);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<Optional<String>>> results = new ArrayList<>();
        for (AuthzFlowStateDatabase instance : instances) {
          results.add(executor.submit(() -> {
            start.await();
            return instance.consume(KIND, id, NOW);
          }));
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
    }
  }

  @Test
  public void testDeleteExpired() throws Exception {
    database.upsert(KIND, "expired", "payload", NOW);
    database.upsert(KIND, "live", "payload", NOW + 1);
    assertEquals(1, database.deleteExpired(NOW));
    assertEquals(Optional.of("payload"), database.select(KIND, "live", NOW));
  }

  private AuthzFlowStateDatabase createDatabase() throws Exception {
    return new AuthzFlowStateDatabase(dataSource, DatabaseType.H2, strategy);
  }

  private int countRows() throws Exception {
    try (Statement stmt = keepAlive.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + AuthzFlowStateDatabase.TABLE_NAME)) {
      rs.next();
      return rs.getInt(1);
    }
  }
}

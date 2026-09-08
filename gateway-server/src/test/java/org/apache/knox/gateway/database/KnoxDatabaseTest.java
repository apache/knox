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
package org.apache.knox.gateway.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Exercises {@link KnoxDatabase#createTablesIfNotExist(String)} and its
 * {@link KnoxDatabase#parseCreateTableStatements(String)} helper directly against an in-memory H2
 * database, using the standard KnoxIDF delegation-policy DDL as a representative
 * multi-statement, foreign-key-ordered script.
 */
public class KnoxDatabaseTest {

  public static final String USER = "sa";
  public static final String PASSWORD = "";

  private static final String DDL = AbstractDataSourceFactory.KNOXIDF_DELEGATION_POLICY_TABLES_SQL;

  // Parent-first: matches the order the statements appear in the DDL and the order they must be
  // created in to satisfy the foreign keys.
  private static final List<String> EXPECTED_TABLES = Arrays.asList(
      "DELEGATION_POLICIES",
      "DELEGATION_POLICY_USERS",
      "DELEGATION_POLICY_GROUPS",
      "DELEGATION_POLICY_RESOURCES",
      "DELEGATION_POLICY_RESOURCE_SCOPES");

  private static JdbcDataSource dataSource;
  private KnoxDatabase db;

  @BeforeClass
  public static void setUpClass() {
    dataSource = new JdbcDataSource();
    dataSource.setUser(USER);
    dataSource.setPassword(PASSWORD);
    // In-memory H2; DB_CLOSE_DELAY=-1 keeps the database alive across connection open/close for the class.
    dataSource.setUrl("jdbc:h2:mem:knoxdatabasetest;DB_CLOSE_DELAY=-1");
  }

  @Before
  public void setUp() {
    db = new KnoxDatabase(dataSource);
  }

  @After
  public void tearDown() throws Exception {
    // Child-first so the foreign keys don't block the drops; leaves a clean schema for the next test.
    final List<String> reversed = new ArrayList<>(EXPECTED_TABLES);
    java.util.Collections.reverse(reversed);
    try (Connection connection = dataSource.getConnection(USER, PASSWORD);
         Statement statement = connection.createStatement()) {
      for (String table : reversed) {
        statement.execute("DROP TABLE IF EXISTS " + table);
      }
    }
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    try (Connection connection = dataSource.getConnection(USER, PASSWORD);
         Statement statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    }
  }

  @Test
  public void shouldParseAllCreateStatementsInOrderWithoutLicenseHeader() throws Exception {
    final Map<String, String> statements = db.parseCreateTableStatements(DDL);

    assertEquals(EXPECTED_TABLES, new ArrayList<>(statements.keySet()));
    for (Map.Entry<String, String> entry : statements.entrySet()) {
      assertTrue("statement for " + entry.getKey() + " should start with CREATE TABLE",
          entry.getValue().toUpperCase(java.util.Locale.ROOT).startsWith("CREATE TABLE"));
      assertFalse("license header should have been stripped",
          entry.getValue().contains("Licensed to the Apache"));
    }
  }

  @Test
  public void shouldCreateAllTables() throws Exception {
    for (String table : EXPECTED_TABLES) {
      assertFalse(table + " should not exist before creation", JDBCUtils.tableExists(table, dataSource));
    }

    db.createTablesIfNotExist(DDL);

    for (String table : EXPECTED_TABLES) {
      assertTrue(table + " should exist after creation", JDBCUtils.tableExists(table, dataSource));
    }
  }

  @Test
  public void shouldBeIdempotentWhenAllTablesAlreadyExist() throws Exception {
    db.createTablesIfNotExist(DDL);
    // A second run must not fail even though every table is already present.
    db.createTablesIfNotExist(DDL);

    for (String table : EXPECTED_TABLES) {
      assertTrue(table + " should still exist", JDBCUtils.tableExists(table, dataSource));
    }
  }

  @Test
  public void shouldRecreateOnlyMissingTables() throws Exception {
    db.createTablesIfNotExist(DDL);

    // Drop a leaf table (nothing references it) to simulate a partially provisioned schema.
    final String leaf = "DELEGATION_POLICY_RESOURCE_SCOPES";
    try (Connection connection = dataSource.getConnection(USER, PASSWORD);
         Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE " + leaf);
    }
    assertFalse(JDBCUtils.tableExists(leaf, dataSource));

    db.createTablesIfNotExist(DDL);

    for (String table : EXPECTED_TABLES) {
      assertTrue(table + " should exist after re-provisioning", JDBCUtils.tableExists(table, dataSource));
    }
  }

  @Test
  public void shouldFailWhenDdlScriptIsMissing() {
    assertThrows(IllegalStateException.class, () -> db.parseCreateTableStatements("does-not-exist.sql"));
  }
}

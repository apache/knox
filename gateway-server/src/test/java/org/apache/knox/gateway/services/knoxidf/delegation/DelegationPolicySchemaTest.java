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

import org.apache.commons.io.IOUtils;
import org.apache.knox.gateway.database.AbstractDataSourceFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Validates that the DELEGATION_POLICIES DDL scripts parse and execute correctly
 * against in-memory databases, and that schema constraints are enforced.
 */
public class DelegationPolicySchemaTest {

  private static final String H2_DB = "delegationpolicies";
  // H2 is the OOTB embedded backend; DB_CLOSE_DELAY=-1 keeps the in-memory database alive for the class.
  private static final String H2_URL = "jdbc:h2:mem:" + H2_DB + ";DB_CLOSE_DELAY=-1";

  // Child-first so the foreign keys don't block the truncation between tests.
  private static final String[] TABLES_CHILD_FIRST = {
      "DELEGATION_POLICY_RESOURCE_SCOPES", "DELEGATION_POLICY_RESOURCES",
      "DELEGATION_POLICY_GROUPS", "DELEGATION_POLICY_USERS", "DELEGATION_POLICIES"};

  private static Connection h2Conn;

  @BeforeClass
  public static void setUp() throws Exception {
    h2Conn = DriverManager.getConnection(H2_URL);
    // H2 (the OOTB embedded backend) uses the standard DDL script; IF NOT EXISTS makes it idempotent.
    runScript(h2Conn, loadSql(AbstractDataSourceFactory.KNOXIDF_DELEGATION_POLICY_TABLES_SQL));
    // Verify Oracle DDL script is present on the classpath (execution requires an Oracle-backed test)
    loadSql(AbstractDataSourceFactory.ORACLE_KNOXIDF_DELEGATION_POLICY_TABLES_SQL);
  }

  @After
  public void clearTables() throws Exception {
    // Reset mutable state so each test starts clean regardless of JUnit method order (notably the
    // "empty after DDL" assertion in testAllFiveTablesQueryable).
    try (Statement stmt = h2Conn.createStatement()) {
      for (String table : TABLES_CHILD_FIRST) {
        stmt.execute("DELETE FROM " + table);
      }
    }
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (h2Conn != null && !h2Conn.isClosed()) {
      try (Statement stmt = h2Conn.createStatement()) {
        stmt.execute("DROP ALL OBJECTS");
      }
      h2Conn.close();
    }
  }

  @Test
  public void testAllFiveTablesQueryable() throws Exception {
    for (String table : new String[]{
        "DELEGATION_POLICIES", "DELEGATION_POLICY_USERS", "DELEGATION_POLICY_GROUPS",
        "DELEGATION_POLICY_RESOURCES", "DELEGATION_POLICY_RESOURCE_SCOPES"}) {
      try (Statement stmt = h2Conn.createStatement();
           ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
        assertTrue("Table must be queryable: " + table, rs.next());
        assertEquals("Table must be empty after DDL: " + table, 0, rs.getInt(1));
      }
    }
  }

  @Test
  public void testStandardSqlIdempotent() throws Exception {
    // Running the script twice must not throw due to IF NOT EXISTS
    runScript(h2Conn, loadSql(AbstractDataSourceFactory.KNOXIDF_DELEGATION_POLICY_TABLES_SQL));
  }

  @Test
  public void testDefaultValues() throws Exception {
    final String id = UUID.randomUUID().toString();
    try (Statement stmt = h2Conn.createStatement()) {
      stmt.execute("INSERT INTO DELEGATION_POLICIES "
          + "(registration_id, actor_authority, actor_id, created_at, updated_at) "
          + "VALUES ('" + id + "', 'oidc', 'actor-id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
      try (ResultSet rs = stmt.executeQuery(
          "SELECT status, allow_headless_exchange, token_ttl_sec FROM DELEGATION_POLICIES WHERE registration_id = '" + id + "'")) {
        assertTrue(rs.next());
        assertEquals("active", rs.getString("status"));
        assertEquals(false, rs.getBoolean("allow_headless_exchange"));
        rs.getInt("token_ttl_sec");
        assertTrue("token_ttl_sec must default to null", rs.wasNull());
      }
    }
  }

  @Test
  public void testNotNullViolationActorAuthority() throws Exception {
    expectConstraintViolation(h2Conn,
        "INSERT INTO DELEGATION_POLICIES (registration_id, actor_authority, actor_id, created_at, updated_at) "
            + "VALUES ('" + UUID.randomUUID() + "', NULL, 'actor-id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
  }

  @Test
  public void testNotNullViolationActorId() throws Exception {
    expectConstraintViolation(h2Conn,
        "INSERT INTO DELEGATION_POLICIES (registration_id, actor_authority, actor_id, created_at, updated_at) "
            + "VALUES ('" + UUID.randomUUID() + "', 'oidc', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
  }

  @Test
  public void testNotNullViolationCreatedAt() throws Exception {
    expectConstraintViolation(h2Conn,
        "INSERT INTO DELEGATION_POLICIES (registration_id, actor_authority, actor_id, created_at, updated_at) "
            + "VALUES ('" + UUID.randomUUID() + "', 'oidc', 'actor-id', NULL, CURRENT_TIMESTAMP)");
  }

  @Test
  public void testUniqueConstraintOnActorAuthorityAndId() throws Exception {
    final String id1 = UUID.randomUUID().toString();
    final String id2 = UUID.randomUUID().toString();
    try (Statement stmt = h2Conn.createStatement()) {
      stmt.execute("INSERT INTO DELEGATION_POLICIES (registration_id, actor_authority, actor_id, created_at, updated_at) "
          + "VALUES ('" + id1 + "', 'oidc', 'duplicateactor', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
      expectConstraintViolation(h2Conn,
          "INSERT INTO DELEGATION_POLICIES (registration_id, actor_authority, actor_id, created_at, updated_at) "
              + "VALUES ('" + id2 + "', 'oidc', 'duplicateactor', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }
  }

  @Test
  public void testFkViolationUsers() throws Exception {
    expectConstraintViolation(h2Conn,
        "INSERT INTO DELEGATION_POLICY_USERS (registration_id, username) VALUES ('" + UUID.randomUUID() + "', 'alice')");
  }

  @Test
  public void testFkViolationGroups() throws Exception {
    expectConstraintViolation(h2Conn,
        "INSERT INTO DELEGATION_POLICY_GROUPS (registration_id, group_name) VALUES ('" + UUID.randomUUID() + "', 'admins')");
  }

  @Test
  public void testFkViolationResources() throws Exception {
    expectConstraintViolation(h2Conn,
        "INSERT INTO DELEGATION_POLICY_RESOURCES (registration_id, resource_uri) VALUES ('" + UUID.randomUUID() + "', '/api/v1')");
  }

  @Test
  public void testFkViolationResourceScopes() throws Exception {
    expectConstraintViolation(h2Conn,
        "INSERT INTO DELEGATION_POLICY_RESOURCE_SCOPES (registration_id, resource_uri, scope) "
            + "VALUES ('" + UUID.randomUUID() + "', '/api/v1', 'read')");
  }

  private static void expectConstraintViolation(Connection conn, String sql) {
    try (Statement stmt = conn.createStatement()) {
      stmt.execute(sql);
      fail("Expected a constraint violation for: " + sql);
    } catch (SQLException expected) {
      // success
    }
  }

  private static void runScript(Connection conn, String script) throws SQLException {
    final StringBuilder stripped = new StringBuilder();
    for (String line : script.split("\n")) {
      if (!line.trim().startsWith("--")) {
        stripped.append(line).append('\n');
      }
    }
    for (String statement : stripped.toString().split(";")) {
      final String trimmed = statement.trim();
      if (!trimmed.isEmpty()) {
        try (Statement stmt = conn.createStatement()) {
          stmt.execute(trimmed);
        }
      }
    }
  }

  private static String loadSql(String fileName) throws IOException {
    try (InputStream is = DelegationPolicySchemaTest.class.getClassLoader().getResourceAsStream(fileName)) {
      assertNotNull("SQL file not found on classpath: " + fileName, is);
      return IOUtils.toString(is, StandardCharsets.UTF_8);
    }
  }
}

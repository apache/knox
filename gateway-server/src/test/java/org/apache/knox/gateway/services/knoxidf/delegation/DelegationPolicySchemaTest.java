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
 * Validates that the DELEGATION_REGISTRY DDL scripts parse and execute correctly
 * against in-memory databases, and that schema constraints are enforced.
 */
public class DelegationPolicySchemaTest {

  private static final String DERBY_DB = "delegationregistry";
  private static final String DERBY_URL = "jdbc:derby:memory:" + DERBY_DB + ";create=true";
  private static final String DERBY_SHUTDOWN_URL = "jdbc:derby:memory:" + DERBY_DB + ";shutdown=true";
  private static final String HSQL_URL = "jdbc:hsqldb:mem:delegationschema;ifexists=false";
  private static final String HSQL_USER = "SA";
  private static final String HSQL_PASSWORD = "";

  private static Connection derbyConn;
  private static Connection hsqlConn;

  @BeforeClass
  public static void setUp() throws Exception {
    java.util.Locale.setDefault(java.util.Locale.US);
    derbyConn = DriverManager.getConnection(DERBY_URL);
    hsqlConn = DriverManager.getConnection(HSQL_URL, HSQL_USER, HSQL_PASSWORD);
    // Run Derby DDL once — no IF NOT EXISTS, so run once at class level
    runScript(derbyConn, loadSql(AbstractDataSourceFactory.DERBY_KNOXIDF_DELEGATION_REGISTRY_TABLES_SQL));
    // Run standard DDL on HSQLDB
    runScript(hsqlConn, loadSql(AbstractDataSourceFactory.KNOXIDF_DELEGATION_REGISTRY_TABLES_SQL));
  }

  @AfterClass
  public static void tearDown() throws Exception {
    try (Connection conn = DriverManager.getConnection(HSQL_URL, HSQL_USER, HSQL_PASSWORD);
         Statement stmt = conn.createStatement()) {
      stmt.execute("SHUTDOWN");
    }
    if (derbyConn != null && !derbyConn.isClosed()) {
      derbyConn.close();
    }
    try {
      DriverManager.getConnection(DERBY_SHUTDOWN_URL);
    } catch (SQLException e) {
      if (!(e.getErrorCode() == 45000 && "08006".equals(e.getSQLState()))) {
        throw e;
      }
    }
  }

  @Test
  public void testDerbyAllFiveTablesQueryable() throws Exception {
    for (String table : new String[]{
        "DELEGATION_REGISTRY", "DELEGATION_REGISTRY_USERS", "DELEGATION_REGISTRY_GROUPS",
        "DELEGATION_REGISTRY_RESOURCES", "DELEGATION_REGISTRY_RESOURCE_SCOPES"}) {
      try (Statement stmt = derbyConn.createStatement();
           ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
        assertTrue("Table must be queryable: " + table, rs.next());
        assertEquals("Table must be empty after DDL: " + table, 0, rs.getInt(1));
      }
    }
  }

  @Test
  public void testHsqlAllFiveTablesQueryable() throws Exception {
    for (String table : new String[]{
        "DELEGATION_REGISTRY", "DELEGATION_REGISTRY_USERS", "DELEGATION_REGISTRY_GROUPS",
        "DELEGATION_REGISTRY_RESOURCES", "DELEGATION_REGISTRY_RESOURCE_SCOPES"}) {
      try (Statement stmt = hsqlConn.createStatement();
           ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
        assertTrue("Table must be queryable: " + table, rs.next());
        assertEquals("Table must be empty after DDL: " + table, 0, rs.getInt(1));
      }
    }
  }

  @Test
  public void testHsqlStandardSqlIdempotent() throws Exception {
    // Running the script twice must not throw due to IF NOT EXISTS
    runScript(hsqlConn, loadSql(AbstractDataSourceFactory.KNOXIDF_DELEGATION_REGISTRY_TABLES_SQL));
  }

  @Test
  public void testDefaultValues() throws Exception {
    final String id = UUID.randomUUID().toString();
    try (Statement stmt = hsqlConn.createStatement()) {
      stmt.execute("INSERT INTO DELEGATION_REGISTRY "
          + "(registration_id, actor_authority, actor_id, created_at, updated_at) "
          + "VALUES ('" + id + "', 'oidc', 'actor@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
      try (ResultSet rs = stmt.executeQuery(
          "SELECT status, allow_headless_exchange, max_token_ttl_sec FROM DELEGATION_REGISTRY WHERE registration_id = '" + id + "'")) {
        assertTrue(rs.next());
        assertEquals("active", rs.getString("status"));
        assertEquals(false, rs.getBoolean("allow_headless_exchange"));
        rs.getInt("max_token_ttl_sec");
        assertTrue("max_token_ttl_sec must default to null", rs.wasNull());
      }
    }
  }

  @Test
  public void testNotNullViolationActorAuthority() throws Exception {
    expectConstraintViolation(hsqlConn,
        "INSERT INTO DELEGATION_REGISTRY (registration_id, actor_authority, actor_id, created_at, updated_at) "
            + "VALUES ('" + UUID.randomUUID() + "', NULL, 'actor@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
  }

  @Test
  public void testNotNullViolationActorId() throws Exception {
    expectConstraintViolation(hsqlConn,
        "INSERT INTO DELEGATION_REGISTRY (registration_id, actor_authority, actor_id, created_at, updated_at) "
            + "VALUES ('" + UUID.randomUUID() + "', 'oidc', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
  }

  @Test
  public void testNotNullViolationCreatedAt() throws Exception {
    expectConstraintViolation(hsqlConn,
        "INSERT INTO DELEGATION_REGISTRY (registration_id, actor_authority, actor_id, created_at, updated_at) "
            + "VALUES ('" + UUID.randomUUID() + "', 'oidc', 'actor@example.com', NULL, CURRENT_TIMESTAMP)");
  }

  @Test
  public void testUniqueConstraintOnActorAuthorityAndId() throws Exception {
    final String id1 = UUID.randomUUID().toString();
    final String id2 = UUID.randomUUID().toString();
    try (Statement stmt = hsqlConn.createStatement()) {
      stmt.execute("INSERT INTO DELEGATION_REGISTRY (registration_id, actor_authority, actor_id, created_at, updated_at) "
          + "VALUES ('" + id1 + "', 'oidc', 'duplicateactor', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
      expectConstraintViolation(hsqlConn,
          "INSERT INTO DELEGATION_REGISTRY (registration_id, actor_authority, actor_id, created_at, updated_at) "
              + "VALUES ('" + id2 + "', 'oidc', 'duplicateactor', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }
  }

  @Test
  public void testFkViolationUsers() throws Exception {
    expectConstraintViolation(hsqlConn,
        "INSERT INTO DELEGATION_REGISTRY_USERS (registration_id, username) VALUES ('" + UUID.randomUUID() + "', 'alice')");
  }

  @Test
  public void testFkViolationGroups() throws Exception {
    expectConstraintViolation(hsqlConn,
        "INSERT INTO DELEGATION_REGISTRY_GROUPS (registration_id, group_name) VALUES ('" + UUID.randomUUID() + "', 'admins')");
  }

  @Test
  public void testFkViolationResources() throws Exception {
    expectConstraintViolation(hsqlConn,
        "INSERT INTO DELEGATION_REGISTRY_RESOURCES (registration_id, resource_uri) VALUES ('" + UUID.randomUUID() + "', '/api/v1')");
  }

  @Test
  public void testFkViolationResourceScopes() throws Exception {
    expectConstraintViolation(hsqlConn,
        "INSERT INTO DELEGATION_REGISTRY_RESOURCE_SCOPES (registration_id, resource_uri, scope) "
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

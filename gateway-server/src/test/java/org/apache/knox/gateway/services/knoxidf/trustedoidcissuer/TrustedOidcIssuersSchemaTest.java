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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Validates that the TRUSTED_OIDC_ISSUERS DDL scripts parse and execute
 * correctly against in-memory databases.
 */
public class TrustedOidcIssuersSchemaTest {

  private static final String DERBY_DB = "trustedissuers";
  private static final String DERBY_URL = "jdbc:derby:memory:" + DERBY_DB + ";create=true";
  private static final String DERBY_SHUTDOWN_URL = "jdbc:derby:memory:" + DERBY_DB + ";shutdown=true";
  private static final String HSQL_URL = "jdbc:hsqldb:mem:trustedissuersschema;ifexists=false";
  private static final String HSQL_USER = "SA";
  private static final String HSQL_PASSWORD = "";

  private static Connection derbyConn;
  private static Connection hsqlConn;

  @BeforeClass
  public static void setUp() throws SQLException {
    derbyConn = DriverManager.getConnection(DERBY_URL);
    hsqlConn = DriverManager.getConnection(HSQL_URL, HSQL_USER, HSQL_PASSWORD);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    // HSQLDB: follow JDBCTokenStateServiceTest pattern — new connection for SHUTDOWN
    try (Connection conn = DriverManager.getConnection(HSQL_URL, HSQL_USER, HSQL_PASSWORD);
         Statement stmt = conn.createStatement()) {
      stmt.execute("SHUTDOWN");
    }

    // Derby: close the shared connection before issuing shutdown
    if (derbyConn != null && !derbyConn.isClosed()) {
      derbyConn.close();
    }
    try {
      DriverManager.getConnection(DERBY_SHUTDOWN_URL);
    } catch (SQLException e) {
      // Derby signals a successful single-DB shutdown as error code 45000, state "08006"
      if (!(e.getErrorCode() == 45000 && "08006".equals(e.getSQLState()))) {
        throw e;
      }
    }
  }

  /**
   * The Derby-dialect DDL must execute without error in a Derby in-memory
   * database and leave the table queryable.
   */
  @Test
  public void testDerbyDdlCreatesTable() throws Exception {
    try (Statement stmt = derbyConn.createStatement()) {
      stmt.execute(loadSql(AbstractDataSourceFactory.DERBY_KNOXIDF_TRUSTED_OIDC_ISSUERS_TABLE_SQL));
      try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM TRUSTED_OIDC_ISSUERS")) {
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
      }
    }
  }

  /**
   * The standard SQL script uses IF NOT EXISTS. Running the script twice must
   * not throw, confirming idempotency.
   */
  @Test
  public void testStandardSqlIdempotent() throws Exception {
    String sql = loadSql(AbstractDataSourceFactory.KNOXIDF_TRUSTED_OIDC_ISSUERS_TABLE_SQL);
    try (Statement stmt = hsqlConn.createStatement()) {
      stmt.execute(sql);
      // Second execution must succeed due to IF NOT EXISTS
      stmt.execute(sql);
      try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM TRUSTED_OIDC_ISSUERS")) {
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
      }
    }
  }

  private static String loadSql(String fileName) throws IOException {
    try (InputStream is = TrustedOidcIssuersSchemaTest.class.getClassLoader().getResourceAsStream(fileName)) {
      assertNotNull("SQL file not found on classpath: " + fileName, is);
      return IOUtils.toString(is, StandardCharsets.UTF_8);
    }
  }
}

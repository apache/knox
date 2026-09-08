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

  private static final String H2_DB = "trustedissuers";
  // H2 is the OOTB embedded backend; DB_CLOSE_DELAY=-1 keeps the in-memory database alive for the class.
  private static final String H2_URL = "jdbc:h2:mem:" + H2_DB + ";DB_CLOSE_DELAY=-1";

  private static Connection h2Conn;

  @BeforeClass
  public static void setUp() throws SQLException {
    h2Conn = DriverManager.getConnection(H2_URL);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    // H2: drop all objects to release the in-memory database, then close the shared connection.
    if (h2Conn != null && !h2Conn.isClosed()) {
      try (Statement stmt = h2Conn.createStatement()) {
        stmt.execute("DROP ALL OBJECTS");
      }
      h2Conn.close();
    }
  }

  /**
   * The standard-dialect DDL must execute without error in an H2 in-memory
   * database (the OOTB embedded backend) and leave the table queryable.
   */
  @Test
  public void testH2DdlCreatesTable() throws Exception {
    try (Statement stmt = h2Conn.createStatement()) {
      stmt.execute(loadSql(AbstractDataSourceFactory.KNOXIDF_TRUSTED_OIDC_ISSUERS_TABLE_SQL));
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
    try (Statement stmt = h2Conn.createStatement()) {
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

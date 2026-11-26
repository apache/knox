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

import org.apache.commons.io.IOUtils;
import org.apache.knox.gateway.database.AbstractDataSourceFactory;
import org.apache.knox.gateway.database.DatabaseType;
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
 * Validates the KNOXIDF_AUTHZ_FLOW_STATE DDL scripts.
 */
public class AuthzFlowStateSchemaTest {

  private static final String H2_URL = "jdbc:h2:mem:authzflowstate_schema;DB_CLOSE_DELAY=-1";

  private static Connection h2Conn;

  @BeforeClass
  public static void setUp() throws SQLException {
    h2Conn = DriverManager.getConnection(H2_URL);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    try (Statement stmt = h2Conn.createStatement()) {
      stmt.execute("DROP ALL OBJECTS");
    }
    h2Conn.close();
  }

  @Test
  public void testStandardDdlCreatesTableIdempotently() throws Exception {
    final String sql = loadSql(AbstractDataSourceFactory.KNOXIDF_AUTHZ_FLOW_STATE_TABLE_SQL);
    try (Statement stmt = h2Conn.createStatement()) {
      stmt.execute(sql);
      stmt.execute(sql);
      try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM KNOXIDF_AUTHZ_FLOW_STATE")) {
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
      }
    }
  }

  @Test
  public void testOracleDdlUsesOracleTypes() throws Exception {
    final String sql = loadSql(AbstractDataSourceFactory.ORACLE_KNOXIDF_AUTHZ_FLOW_STATE_TABLE_SQL);
    assertTrue(sql, sql.matches("(?s).*\\bpayload\\s+CLOB\\b.*"));
    assertTrue(sql, sql.matches("(?s).*\\bexpires_at\\s+NUMBER\\(19\\).*"));
    assertTrue(sql, sql.matches("(?s).*PRIMARY KEY \\(id, kind\\).*"));
  }

  @Test
  public void testEveryDatabaseTypeHasDdl() throws Exception {
    for (DatabaseType databaseType : DatabaseType.values()) {
      assertNotNull(databaseType.type(), loadSql(databaseType.authzFlowStateTableSql()));
    }
  }

  private static String loadSql(String fileName) throws IOException {
    try (InputStream is = AuthzFlowStateSchemaTest.class.getClassLoader().getResourceAsStream(fileName)) {
      assertNotNull("SQL resource not found: " + fileName, is);
      return IOUtils.toString(is, StandardCharsets.UTF_8);
    }
  }
}

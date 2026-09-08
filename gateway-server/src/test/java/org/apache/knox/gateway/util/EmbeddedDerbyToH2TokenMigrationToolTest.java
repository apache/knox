/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.knox.gateway.util.EmbeddedDerbyToH2TokenMigrationTool.MigrationResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Exercises the DB-agnostic {@code copyTokens} logic using two in-memory H2 databases standing in
 * for the legacy Derby source and the H2 destination. This deliberately avoids any Derby test
 * dependency: the token schema is identical across databases, so two H2 instances
 * fully cover the copy behaviour.
 */
public class EmbeddedDerbyToH2TokenMigrationToolTest {

  private static final String CREATE_TOKENS_TABLE = "CREATE TABLE KNOX_TOKENS "
      + "(token_id VARCHAR(255) NOT NULL PRIMARY KEY, issue_time BIGINT, expiration BIGINT, max_lifetime BIGINT)";
  private static final String CREATE_METADATA_TABLE = "CREATE TABLE KNOX_TOKEN_METADATA "
      + "(token_id VARCHAR(255) NOT NULL, md_name VARCHAR(255) NOT NULL, md_value VARCHAR(4000), PRIMARY KEY(token_id, md_name))";

  private Connection source;
  private Connection destination;
  private EmbeddedDerbyToH2TokenMigrationTool tool;

  @Before
  public void setUp() throws Exception {
    source = DriverManager.getConnection("jdbc:h2:mem:migration-source;DB_CLOSE_DELAY=-1");
    destination = DriverManager.getConnection("jdbc:h2:mem:migration-dest;DB_CLOSE_DELAY=-1");
    createSchema(source);
    createSchema(destination);
    tool = new EmbeddedDerbyToH2TokenMigrationTool(null, null, new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name()));
  }

  @After
  public void tearDown() throws Exception {
    dropAll(source);
    dropAll(destination);
    source.close();
    destination.close();
  }

  @Test
  public void testMigratesTokensAndMetadataVerbatim() throws Exception {
    final String base64Passcode = "VGhpc0lzQVBhc3Njb2Rl";
    insertToken(source, "token-1", 1000L, 2000L, 5000L);
    insertMetadata(source, "token-1", "passcode", base64Passcode);
    insertMetadata(source, "token-1", "userName", "admin");
    insertToken(source, "token-2", 1100L, -1L, -1L);
    insertMetadata(source, "token-2", "userName", "bob");

    final MigrationResult result = tool.copyTokens(source, destination, true);

    assertEquals(2, result.migratedTokens);
    assertEquals(0, result.skippedTokens);
    assertEquals(3, result.migratedMetadataRows);

    final Map<String, long[]> tokens = readTokens(destination);
    assertEquals(2, tokens.size());
    assertTrue(tokens.containsKey("token-1"));
    // absolute timestamps copied verbatim (no duration re-interpretation)
    assertArrayEquals(new long[] { 1000L, 2000L, 5000L }, tokens.get("token-1"));
    assertArrayEquals(new long[] { 1100L, -1L, -1L }, tokens.get("token-2"));

    // Base64 passcode preserved byte-for-byte
    assertEquals(base64Passcode, readMetadataValue(destination, "token-1", "passcode"));
    assertEquals("admin", readMetadataValue(destination, "token-1", "userName"));
    assertEquals("bob", readMetadataValue(destination, "token-2", "userName"));
  }

  @Test
  public void testReRunIsIdempotent() throws Exception {
    insertToken(source, "token-1", 1000L, 2000L, 5000L);
    insertMetadata(source, "token-1", "passcode", "cGFzcw==");

    final MigrationResult first = tool.copyTokens(source, destination, false);
    assertEquals(1, first.migratedTokens);
    assertEquals(0, first.skippedTokens);
    assertEquals(1, first.migratedMetadataRows);

    // second run: token already present -> skipped, nothing inserted
    final MigrationResult second = tool.copyTokens(source, destination, false);
    assertEquals(0, second.migratedTokens);
    assertEquals(1, second.skippedTokens);
    assertEquals(0, second.migratedMetadataRows);

    assertEquals(1, count(destination, "KNOX_TOKENS"));
    assertEquals(1, count(destination, "KNOX_TOKEN_METADATA"));
  }

  private static void createSchema(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute(CREATE_TOKENS_TABLE);
      statement.execute(CREATE_METADATA_TABLE);
    }
  }

  private static void dropAll(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS KNOX_TOKEN_METADATA");
      statement.execute("DROP TABLE IF EXISTS KNOX_TOKENS");
    }
  }

  private static void insertToken(Connection connection, String tokenId, long issueTime, long expiration, long maxLifetime) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO KNOX_TOKENS(token_id, issue_time, expiration, max_lifetime) VALUES(?, ?, ?, ?)")) {
      statement.setString(1, tokenId);
      statement.setLong(2, issueTime);
      statement.setLong(3, expiration);
      statement.setLong(4, maxLifetime);
      statement.executeUpdate();
    }
  }

  private static void insertMetadata(Connection connection, String tokenId, String name, String value) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO KNOX_TOKEN_METADATA(token_id, md_name, md_value) VALUES(?, ?, ?)")) {
      statement.setString(1, tokenId);
      statement.setString(2, name);
      statement.setString(3, value);
      statement.executeUpdate();
    }
  }

  private static Map<String, long[]> readTokens(Connection connection) throws Exception {
    final Map<String, long[]> tokens = new LinkedHashMap<>();
    try (Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT token_id, issue_time, expiration, max_lifetime FROM KNOX_TOKENS")) {
      while (rs.next()) {
        tokens.put(rs.getString(1), new long[] { rs.getLong(2), rs.getLong(3), rs.getLong(4) });
      }
    }
    return tokens;
  }

  private static String readMetadataValue(Connection connection, String tokenId, String name) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement("SELECT md_value FROM KNOX_TOKEN_METADATA WHERE token_id = ? AND md_name = ?")) {
      statement.setString(1, tokenId);
      statement.setString(2, name);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  private static int count(Connection connection, String table) throws Exception {
    try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
      return rs.next() ? rs.getInt(1) : -1;
    }
  }
}

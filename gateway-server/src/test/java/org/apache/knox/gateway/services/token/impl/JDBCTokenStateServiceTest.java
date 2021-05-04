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
package org.apache.knox.gateway.services.token.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.derby.drda.NetworkServerControl;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.shell.jdbc.Database;
import org.apache.knox.gateway.shell.jdbc.derby.DerbyDatabase;
import org.apache.knox.gateway.util.JDBCUtils;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JDBCTokenStateServiceTest {

  private static final String GET_TOKENS_COUNT_SQL = "SELECT count(*) FROM " + TokenStateDatabase.TOKENS_TABLE_NAME;
  private static final String TRUNCATE_KNOX_TOKENS_SQL = "DELETE FROM " + TokenStateDatabase.TOKENS_TABLE_NAME;
  private static final String TRUNCATE_KNOX_TOKEN_METADATA_SQL = "DELETE FROM " + TokenStateDatabase.TOKEN_METADATA_TABLE_NAME;

  @ClassRule
  public static final TemporaryFolder testFolder = new TemporaryFolder();

  private static final String SYSTEM_PROPERTY_DERBY_STREAM_ERROR_FILE = "derby.stream.error.file";
  private static final String SAMPLE_DERBY_DATABASE_NAME = "sampleDerbyDatabase";
  private static NetworkServerControl derbyNetworkServerControl;
  private static Database derbyDatabase;
  private static JDBCTokenStateService jdbcTokenStateService;

  @SuppressWarnings("PMD.JUnit4TestShouldUseBeforeAnnotation")
  @BeforeClass
  public static void setUp() throws Exception {
    final String username = "app";
    final String password = "P4ssW0rd!";
    System.setProperty(SYSTEM_PROPERTY_DERBY_STREAM_ERROR_FILE, "/dev/null");
    derbyNetworkServerControl = new NetworkServerControl(username, password);
    derbyNetworkServerControl.start(null);
    TimeUnit.SECONDS.sleep(1); // give a bit of time for the server to start
    final Path derbyDatabaseFolder = Paths.get(testFolder.newFolder().toPath().toString(), SAMPLE_DERBY_DATABASE_NAME);
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(JDBCUtils.DERBY_DB_TYPE).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseHost()).andReturn("localhost").anyTimes();
    EasyMock.expect(gatewayConfig.getDatabasePort()).andReturn(NetworkServerControl.DEFAULT_PORTNUMBER).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn(derbyDatabaseFolder.toString()).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(JDBCUtils.DATABASE_USER_ALIAS_NAME)).andReturn(username.toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(JDBCUtils.DATABASE_PASSWORD_ALIAS_NAME)).andReturn(password.toCharArray()).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);

    derbyDatabase = prepareDerbyDatabase(derbyDatabaseFolder);

    jdbcTokenStateService = new JDBCTokenStateService();
    jdbcTokenStateService.setAliasService(aliasService);
    jdbcTokenStateService.init(gatewayConfig, null);
    assertTrue(derbyDatabase.hasTable(TokenStateDatabase.TOKENS_TABLE_NAME));
  }

  private static Database prepareDerbyDatabase(Path derbyDatabaseFolder) throws SQLException {
    final Database derbyDatabase = new DerbyDatabase(derbyDatabaseFolder.toString(), true);
    derbyDatabase.create();
    return derbyDatabase;
  }

  @SuppressWarnings("PMD.JUnit4TestShouldUseAfterAnnotation")
  @AfterClass
  public static void tearDown() throws Exception {
    if (derbyDatabase != null) {
      derbyDatabase.shutdown();
    }
    derbyNetworkServerControl.shutdown();
    System.clearProperty(SYSTEM_PROPERTY_DERBY_STREAM_ERROR_FILE);
  }

  @Test
  public void testAddToken() throws Exception {
    final String tokenId = UUID.randomUUID().toString();
    long issueTime = System.currentTimeMillis();
    long maxLifetimeDuration = 1000;
    long expiration = issueTime + maxLifetimeDuration;
    jdbcTokenStateService.addToken(tokenId, issueTime, expiration, maxLifetimeDuration);

    assertEquals(expiration, jdbcTokenStateService.getTokenExpiration(tokenId));
    assertEquals(issueTime + maxLifetimeDuration, jdbcTokenStateService.getMaxLifetime(tokenId));

    assertEquals(expiration, getLongTokenAttributeFromDatabase(tokenId, TokenStateDatabase.GET_TOKEN_EXPIRATION_SQL));
    assertEquals(issueTime + maxLifetimeDuration, getLongTokenAttributeFromDatabase(tokenId, TokenStateDatabase.GET_MAX_LIFETIME_SQL));
  }

  @Test(expected = UnknownTokenException.class)
  public void testRemoveToken() throws Exception {
    truncateDatabase();
    final String tokenId = UUID.randomUUID().toString();
    jdbcTokenStateService.addToken(tokenId, 1, 1, 1);
    assertEquals(1, getLongTokenAttributeFromDatabase(null, GET_TOKENS_COUNT_SQL));
    jdbcTokenStateService.removeToken(tokenId);
    assertEquals(0, getLongTokenAttributeFromDatabase(null, GET_TOKENS_COUNT_SQL));
    jdbcTokenStateService.getTokenExpiration(tokenId);
  }

  @Test
  public void testUpdateExpiration() throws Exception {
    final String tokenId = UUID.randomUUID().toString();
    jdbcTokenStateService.addToken(tokenId, 1, 1, 1);
    jdbcTokenStateService.updateExpiration(tokenId, 2);

    assertEquals(2, jdbcTokenStateService.getTokenExpiration(tokenId));
    assertEquals(2, getLongTokenAttributeFromDatabase(tokenId, TokenStateDatabase.GET_TOKEN_EXPIRATION_SQL));
  }

  @Test(expected = UnknownTokenException.class)
  public void testAddMetadata() throws Exception {
    final String tokenId = UUID.randomUUID().toString();
    final TokenMetadata tokenMetadata = new TokenMetadata("sampleUser", "my test comment", false);
    jdbcTokenStateService.addToken(tokenId, 1, 1, 1);
    jdbcTokenStateService.addMetadata(tokenId, tokenMetadata);

    assertEquals("sampleUser", jdbcTokenStateService.getTokenMetadata(tokenId).getUserName());
    assertEquals("my test comment", jdbcTokenStateService.getTokenMetadata(tokenId).getComment());
    assertFalse(jdbcTokenStateService.getTokenMetadata(tokenId).isEnabled());

    assertEquals("sampleUser", getStringTokenAttributeFromDatabase(tokenId, getSelectMetadataSql(TokenMetadata.USER_NAME)));
    assertEquals("my test comment", getStringTokenAttributeFromDatabase(tokenId, getSelectMetadataSql(TokenMetadata.COMMENT)));
    assertEquals("false", getStringTokenAttributeFromDatabase(tokenId, getSelectMetadataSql(TokenMetadata.ENABLED)));

    //enable the token (it was disabled)
    tokenMetadata.setEnabled(true);
    jdbcTokenStateService.addMetadata(tokenId, tokenMetadata);
    assertTrue(jdbcTokenStateService.getTokenMetadata(tokenId).isEnabled());
    assertEquals("true", getStringTokenAttributeFromDatabase(tokenId, getSelectMetadataSql(TokenMetadata.ENABLED)));

    //remove and get -> expect UnknownTokenException
    jdbcTokenStateService.removeToken(tokenId);
    jdbcTokenStateService.getTokenMetadata(tokenId);
  }

  @Test
  public void testEvictExpiredTokens() throws Exception {
    truncateDatabase();
    final int tokenCount = 1000;
    for (int i = 0; i < tokenCount; i++) {
      final String tokenId = UUID.randomUUID().toString();
      jdbcTokenStateService.addToken(tokenId, 1, 1, 1);
    }
    assertEquals(tokenCount, getLongTokenAttributeFromDatabase(null, GET_TOKENS_COUNT_SQL));
    jdbcTokenStateService.evictExpiredTokens();
    assertEquals(0, getLongTokenAttributeFromDatabase(null, GET_TOKENS_COUNT_SQL));
  }

  private long getLongTokenAttributeFromDatabase(String tokenId, String sql) throws SQLException {
    try (Connection conn = derbyDatabase.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
      if (tokenId != null) {
        stmt.setString(1, tokenId);
      }
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() ? rs.getLong(1) : 0;
      }
    }
  }

  private String getStringTokenAttributeFromDatabase(String tokenId, String sql) throws SQLException {
    try (Connection conn = derbyDatabase.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, tokenId);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  private void truncateDatabase() throws SQLException {
    try (Connection conn = derbyDatabase.getConnection(); PreparedStatement stmt = conn.prepareStatement(TRUNCATE_KNOX_TOKEN_METADATA_SQL)) {
      stmt.executeUpdate();
    }

    try (Connection conn = derbyDatabase.getConnection(); PreparedStatement stmt = conn.prepareStatement(TRUNCATE_KNOX_TOKENS_SQL)) {
      stmt.executeUpdate();
    }
  }

  private String getSelectMetadataSql(String metadataName) {
    return "SELECT md_value FROM " + TokenStateDatabase.TOKEN_METADATA_TABLE_NAME + " WHERE token_id = ? AND md_name = '" + metadataName + "'";
  }

}

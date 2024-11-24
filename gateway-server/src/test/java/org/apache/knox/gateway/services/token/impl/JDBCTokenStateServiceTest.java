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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.knox.gateway.util.JDBCUtils.HSQL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.token.KnoxToken;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;
import org.apache.knox.gateway.util.JDBCUtils;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JDBCTokenStateServiceTest {

  public static final String USERNAME = "SA";
  public static final String PASSWORD = "";
  private static final String GET_TOKENS_COUNT_SQL = "SELECT count(*) FROM " + TokenStateDatabase.TOKENS_TABLE_NAME;
  private static final String TRUNCATE_KNOX_TOKENS_SQL = "DELETE FROM " + TokenStateDatabase.TOKENS_TABLE_NAME;
  private static final String TRUNCATE_KNOX_TOKEN_METADATA_SQL = "DELETE FROM " + TokenStateDatabase.TOKEN_METADATA_TABLE_NAME;

  @ClassRule
  public static final TemporaryFolder testFolder = new TemporaryFolder();
  public static final String CONNECTION_URL = "jdbc:hsqldb:mem:knox;ifexists=false";
  public static final String DB_NAME = "knox";
  private static JDBCTokenStateService jdbcTokenStateService;
  private static TokenMAC tokenMAC;

  @SuppressWarnings("PMD.JUnit4TestShouldUseBeforeAnnotation")
  @BeforeClass
  public static void setUp() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(HSQL).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseConnectionUrl()).andReturn(CONNECTION_URL).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn(DB_NAME).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(JDBCUtils.DATABASE_USER_ALIAS_NAME)).andReturn(USERNAME.toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(JDBCUtils.DATABASE_PASSWORD_ALIAS_NAME)).andReturn(PASSWORD.toCharArray()).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    jdbcTokenStateService = new JDBCTokenStateService();
    jdbcTokenStateService.setAliasService(aliasService);
    jdbcTokenStateService.init(gatewayConfig, null);
    tokenMAC = new TokenMAC(HmacAlgorithms.HMAC_SHA_256.getName(), "sPj8FCgQhCEi6G18kBfpswxYSki33plbelGLs0hMSbk".toCharArray());
  }

  @SuppressWarnings("PMD.JUnit4TestShouldUseAfterAnnotation")
  @AfterClass
  public static void tearDown() throws Exception {
    try (Connection connection = getConnection();
         Statement statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    }
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

  @Test
  public void testAddTokenThatCanAlwaysBeRenewed() throws Exception {
    final String tokenId = UUID.randomUUID().toString();
    jdbcTokenStateService.addToken(tokenId, System.currentTimeMillis(), System.currentTimeMillis() + 30, -1);

    assertEquals(-1L, jdbcTokenStateService.getMaxLifetime(tokenId));
    assertEquals(-1L, getLongTokenAttributeFromDatabase(tokenId, TokenStateDatabase.GET_MAX_LIFETIME_SQL));
  }

  @Test
  public void testAddTokensForMultipleUsers() throws Exception {
    String user1 = "user1";
    String user2 = "user2";
    String id1 = "token1";
    String id2 = "token2";
    String id3 = "token3";
    String id4 = "token4";
    String createdBy3 = "createdBy3";

    long issueTime1 = 1;
    long expiration1 = 1;
    String comment1 = "comment1";

    long issueTime2 = 2;
    long expiration2 = 2;
    String comment2 = "comment2";

    long issueTime3 = 3;
    long expiration3 = 3;
    String comment3 = "comment3";

    long issueTime4 = 4;
    long expiration4 = -1;
    String comment4 = "comment4";

    truncateDatabase();

    saveToken(user1, id1, issueTime1, expiration1, comment1);
    saveToken(user1, id2, issueTime2, expiration2, comment2);
    saveToken(user2, id3, issueTime3, expiration3, comment3);
    saveToken(user1, id4, issueTime4, expiration4, comment4);

    List<KnoxToken> user1Tokens = new ArrayList<>(jdbcTokenStateService.getTokens(user1));
    assertEquals(3, user1Tokens.size());
    assertToken(user1Tokens.get(0), id1, expiration1, comment1, issueTime1);
    assertToken(user1Tokens.get(1), id2, expiration2, comment2, issueTime2);
    assertToken(user1Tokens.get(2), id4, expiration4, comment4, issueTime4);

    List<KnoxToken> user2Tokens = new ArrayList<>(jdbcTokenStateService.getTokens(user2));
    assertEquals(1, user2Tokens.size());
    KnoxToken token3 = user2Tokens.get(0);
    assertToken(token3, id3, expiration3, comment3, issueTime3);

    // check doAs tokens
    TokenMetadata token3Metadata = new TokenMetadata(token3.getMetadata().getUserName());
    token3Metadata.add(TokenMetadata.CREATED_BY, createdBy3);
    jdbcTokenStateService.addMetadata(id3, token3Metadata);
    List<KnoxToken> createdBy3Tokens = new ArrayList<>(jdbcTokenStateService.getDoAsTokens(createdBy3));
    assertEquals(1, createdBy3Tokens.size());
    KnoxToken createdBy3Token = createdBy3Tokens.get(0);
    assertToken(createdBy3Token, id3, expiration3, comment3, issueTime3, createdBy3);

    // check all tokens
    List<KnoxToken> allTokens = new ArrayList<>(jdbcTokenStateService.getAllTokens());
    assertEquals(4, allTokens.size());
    assertToken(allTokens.get(0), id1, expiration1, comment1, issueTime1);
    assertToken(allTokens.get(1), id2, expiration2, comment2, issueTime2);
    assertToken(allTokens.get(2), id3, expiration3, comment3, issueTime3, createdBy3);
    assertToken(allTokens.get(3), id4, expiration4, comment4, issueTime4);
  }

  @Test
  public void testGetTokenExpiration() throws UnknownTokenException {
    saveToken("tokenExpirationUser1", "token100", 123, 456, "comment");
    long expiration = jdbcTokenStateService.getTokenExpiration("token100");
    assertEquals(456, expiration);

    saveToken("tokenExpirationUser1", "token101", 789, -1, "comment");
    expiration = jdbcTokenStateService.getTokenExpiration("token101");
    assertEquals(-1L, expiration);
  }

  @Test(expected = UnknownTokenException.class)
  public void testGetUnknownTokenExpiration() throws UnknownTokenException {
    jdbcTokenStateService.getTokenExpiration("unknownToken1");
  }

  private void assertToken(KnoxToken knoxToken, String tokenId, long expiration, String comment, long issueTime) {
    assertToken(knoxToken, tokenId, expiration, comment, issueTime, null);
  }

  private void assertToken(KnoxToken knoxToken, String tokenId, long expiration, String comment, long issueTime, String createdBy) {
    SimpleDateFormat df = new SimpleDateFormat(KnoxToken.DATE_FORMAT, Locale.getDefault());
    assertEquals(tokenId, knoxToken.getTokenId());
    assertEquals(df.format(new Date(issueTime)), knoxToken.getIssueTime());
    assertEquals(expiration < 0 ? "Never" : df.format(new Date(expiration)), knoxToken.getExpiration());
    assertEquals(comment, knoxToken.getMetadata().getComment());
    if (createdBy != null) {
      assertEquals(createdBy, knoxToken.getMetadata().getCreatedBy());
    }
  }

  private void saveToken(String user, String tokenId, long issueTime, long expiration, String comment) {
    jdbcTokenStateService.addToken(tokenId, issueTime, expiration);
    jdbcTokenStateService.addMetadata(tokenId, new TokenMetadata(user, comment));
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

    // set token expiration to 3 in-memory
    // we still expect 2 because in-memory lookup should be skipped while fetching token expiration
    final Map<String, Long> tokenExpirations = new ConcurrentHashMap<>();
    tokenExpirations.put(tokenId, 3L);
    FieldUtils.writeField(jdbcTokenStateService, "tokenExpirations", tokenExpirations, true);

    assertEquals(2, jdbcTokenStateService.getTokenExpiration(tokenId));
    assertEquals(2, getLongTokenAttributeFromDatabase(tokenId, TokenStateDatabase.GET_TOKEN_EXPIRATION_SQL));
  }

  @Test(expected = UnknownTokenException.class)
  public void testAddMetadata() throws Exception {
    final String tokenId = UUID.randomUUID().toString();
    final String passcode = UUID.randomUUID().toString();
    final String passcodeMac = tokenMAC.hash(tokenId, 1, "sampleUser", passcode);
    final TokenMetadata tokenMetadata = new TokenMetadata("sampleUser", "my test comment", false);
    tokenMetadata.setPasscode(passcodeMac);
    jdbcTokenStateService.addToken(tokenId, 1, 1, 1);
    jdbcTokenStateService.addMetadata(tokenId, tokenMetadata);

    assertEquals("sampleUser", jdbcTokenStateService.getTokenMetadata(tokenId).getUserName());
    assertEquals("my test comment", jdbcTokenStateService.getTokenMetadata(tokenId).getComment());
    assertFalse(jdbcTokenStateService.getTokenMetadata(tokenId).isEnabled());
    final String storedPasscode = jdbcTokenStateService.getTokenMetadata(tokenId).getPasscode();
    assertEquals(passcodeMac, storedPasscode);

    assertEquals("sampleUser", getStringTokenAttributeFromDatabase(tokenId, getSelectMetadataSql(TokenMetadata.USER_NAME)));
    assertEquals("my test comment", getStringTokenAttributeFromDatabase(tokenId, getSelectMetadataSql(TokenMetadata.COMMENT)));
    assertEquals("false", getStringTokenAttributeFromDatabase(tokenId, getSelectMetadataSql(TokenMetadata.ENABLED)));
    final String storedPasscodeInDb = new String(Base64.decodeBase64(getStringTokenAttributeFromDatabase(tokenId, getSelectMetadataSql(TokenMetadata.PASSCODE))), UTF_8);
    assertEquals(passcodeMac, storedPasscodeInDb);

    //enable the token (it was disabled)
    tokenMetadata.setEnabled(true);
    jdbcTokenStateService.addMetadata(tokenId, tokenMetadata);

    // set token metadata back to original in the in-memory cache with disabled=false
    // we still expect an enabled token because in-memory lookup should be skipped while fetching token metadata
    final Map<String, TokenMetadata> metadataMap = new ConcurrentHashMap<>();
    metadataMap.put(tokenId, tokenMetadata);
    FieldUtils.writeField(jdbcTokenStateService, "metadataMap", metadataMap, true);

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

    //add another token that never expires
    jdbcTokenStateService.addToken(UUID.randomUUID().toString(), 1, -1, 1);

    assertEquals(tokenCount + 1, getLongTokenAttributeFromDatabase(null, GET_TOKENS_COUNT_SQL));
    jdbcTokenStateService.evictExpiredTokens();
    assertEquals(1, getLongTokenAttributeFromDatabase(null, GET_TOKENS_COUNT_SQL));  //the one that never expires should remain
  }

  private long getLongTokenAttributeFromDatabase(String tokenId, String sql) throws SQLException {
    try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
      if (tokenId != null) {
        stmt.setString(1, tokenId);
      }
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() ? rs.getLong(1) : 0;
      }
    }
  }

  private String getStringTokenAttributeFromDatabase(String tokenId, String sql) throws SQLException {
    try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, tokenId);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  private void truncateDatabase() throws SQLException {
    try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(TRUNCATE_KNOX_TOKEN_METADATA_SQL)) {
      stmt.executeUpdate();
    }

    try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(TRUNCATE_KNOX_TOKENS_SQL)) {
      stmt.executeUpdate();
    }
  }

  private static Connection getConnection() throws SQLException {
    return DriverManager.getConnection(CONNECTION_URL, USERNAME, PASSWORD);
  }

  private String getSelectMetadataSql(String metadataName) {
    return "SELECT md_value FROM " + TokenStateDatabase.TOKEN_METADATA_TABLE_NAME + " WHERE token_id = ? AND md_name = '" + metadataName + "'";
  }

}

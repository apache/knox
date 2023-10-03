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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;

import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.services.security.token.KnoxToken;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.util.JDBCUtils;

public class TokenStateDatabase {
  private static final String TOKENS_TABLE_CREATE_SQL_FILE_NAME = "createKnoxTokenDatabaseTable.sql";
  private static final String TOKEN_METADATA_TABLE_CREATE_SQL_FILE_NAME = "createKnoxTokenMetadataDatabaseTable.sql";
  static final String TOKENS_TABLE_NAME = "KNOX_TOKENS";
  static final String TOKEN_METADATA_TABLE_NAME = "KNOX_TOKEN_METADATA";
  private static final String ADD_TOKEN_SQL = "INSERT INTO " + TOKENS_TABLE_NAME + "(token_id, issue_time, expiration, max_lifetime) VALUES(?, ?, ?, ?)";
  private static final String REMOVE_TOKEN_SQL = "DELETE FROM " + TOKENS_TABLE_NAME + " WHERE token_id = ?";
  private static final String GET_EXPIRED_TOKENS_SQL = "SELECT token_id FROM " + TOKENS_TABLE_NAME + " WHERE expiration < ?";
  private static final String REMOVE_EXPIRED_TOKENS_SQL = "DELETE FROM " + TOKENS_TABLE_NAME + " WHERE expiration < ?";
  static final String GET_TOKEN_ISSUE_TIME_SQL = "SELECT issue_time FROM " + TOKENS_TABLE_NAME + " WHERE token_id = ?";
  static final String GET_TOKEN_EXPIRATION_SQL = "SELECT expiration FROM " + TOKENS_TABLE_NAME + " WHERE token_id = ?";
  private static final String UPDATE_TOKEN_EXPIRATION_SQL = "UPDATE " + TOKENS_TABLE_NAME + " SET expiration = ? WHERE token_id = ?";
  static final String GET_MAX_LIFETIME_SQL = "SELECT max_lifetime FROM " + TOKENS_TABLE_NAME + " WHERE token_id = ?";
  private static final String ADD_METADATA_SQL = "INSERT INTO " + TOKEN_METADATA_TABLE_NAME + "(token_id, md_name, md_value) VALUES(?, ?, ?)";
  private static final String UPDATE_METADATA_SQL = "UPDATE " + TOKEN_METADATA_TABLE_NAME + " SET md_value = ? WHERE token_id = ? AND md_name = ?";
  private static final String GET_METADATA_SQL = "SELECT md_name, md_value FROM " + TOKEN_METADATA_TABLE_NAME + " WHERE token_id = ?";
  private static final String GET_ALL_TOKENS_SQL = "SELECT kt.token_id, kt.issue_time, kt.expiration, kt.max_lifetime, ktm.md_name, ktm.md_value FROM " + TOKENS_TABLE_NAME
      + " kt, " + TOKEN_METADATA_TABLE_NAME + " ktm WHERE kt.token_id = ktm.token_id";
  private static final String GET_TOKENS_BY_USER_NAME_SQL = GET_ALL_TOKENS_SQL + " AND kt.token_id IN (SELECT token_id FROM " + TOKEN_METADATA_TABLE_NAME + " WHERE md_name = '" + TokenMetadata.USER_NAME + "' AND md_value = ? )"
      + " ORDER BY kt.issue_time";
  private static final String GET_TOKENS_CREATED_BY_USER_NAME_SQL = GET_ALL_TOKENS_SQL + " AND kt.token_id IN (SELECT token_id FROM " + TOKEN_METADATA_TABLE_NAME + " WHERE md_name = '" + TokenMetadata.CREATED_BY + "' AND md_value = ? )"
      + " ORDER BY kt.issue_time";

  private final DataSource dataSource;

  TokenStateDatabase(DataSource dataSource) throws Exception {
    this.dataSource = dataSource;
    createTableIfNotExists(TOKENS_TABLE_NAME, TOKENS_TABLE_CREATE_SQL_FILE_NAME);
    createTableIfNotExists(TOKEN_METADATA_TABLE_NAME, TOKEN_METADATA_TABLE_CREATE_SQL_FILE_NAME);
  }

  private void createTableIfNotExists(String tableName, String createSqlFileName) throws Exception {
    if (!JDBCUtils.isTableExists(tableName, dataSource)) {
      JDBCUtils.createTable(createSqlFileName, dataSource, TokenStateDatabase.class.getClassLoader());
    }
  }

  boolean addToken(String tokenId, long issueTime, long expiration, long maxLifetimeDuration) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement addTokenStatement = connection.prepareStatement(ADD_TOKEN_SQL)) {
      addTokenStatement.setString(1, tokenId);
      addTokenStatement.setLong(2, issueTime);
      addTokenStatement.setLong(3, expiration);
      addTokenStatement.setLong(4, issueTime + maxLifetimeDuration);
      return addTokenStatement.executeUpdate() == 1;
    }
  }

  boolean removeToken(String tokenId) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement addTokenStatement = connection.prepareStatement(REMOVE_TOKEN_SQL)) {
      addTokenStatement.setString(1, tokenId);
      return addTokenStatement.executeUpdate() == 1;
    }
  }

  long getTokenIssueTime(String tokenId) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement getTokenExpirationStatement = connection.prepareStatement(GET_TOKEN_ISSUE_TIME_SQL)) {
      getTokenExpirationStatement.setString(1, tokenId);
      try (ResultSet rs = getTokenExpirationStatement.executeQuery()) {
        return rs.next() ? rs.getLong(1) : -1;
      }
    }
  }

  long getTokenExpiration(String tokenId) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement getTokenExpirationStatement = connection.prepareStatement(GET_TOKEN_EXPIRATION_SQL)) {
      getTokenExpirationStatement.setString(1, tokenId);
      try (ResultSet rs = getTokenExpirationStatement.executeQuery()) {
        return rs.next() ? rs.getLong(1) : -1;
      }
    }
  }

  boolean updateExpiration(final String tokenId, long expiration) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement updateTokenExpirationStatement = connection.prepareStatement(UPDATE_TOKEN_EXPIRATION_SQL)) {
      updateTokenExpirationStatement.setLong(1, expiration);
      updateTokenExpirationStatement.setString(2, tokenId);
      return updateTokenExpirationStatement.executeUpdate() == 1;
    }
  }

  long getMaxLifetime(String tokenId) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement getMaxLifetimeStatement = connection.prepareStatement(GET_MAX_LIFETIME_SQL)) {
      getMaxLifetimeStatement.setString(1, tokenId);
      try (ResultSet rs = getMaxLifetimeStatement.executeQuery()) {
        return rs.next() ? rs.getLong(1) : -1;
      }
    }
  }

  Set<String> getExpiredTokenIds(long expirationLimit) throws SQLException {
    final Set<String> expiredTokenIds = new HashSet<>();
    try (Connection connection = dataSource.getConnection(); PreparedStatement getExpiredTokenIdsStatement = connection.prepareStatement(GET_EXPIRED_TOKENS_SQL)) {
      getExpiredTokenIdsStatement.setLong(1, expirationLimit);
      try (ResultSet rs = getExpiredTokenIdsStatement.executeQuery()) {
        while(rs.next()) {
          expiredTokenIds.add(rs.getString(1));
        }
        return expiredTokenIds;
      }
    }
  }

  int deleteExpiredTokens(long expirationLimit) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement deleteExpiredTokensStatement = connection.prepareStatement(REMOVE_EXPIRED_TOKENS_SQL)) {
      deleteExpiredTokensStatement.setLong(1, expirationLimit);
      return deleteExpiredTokensStatement.executeUpdate();
    }
  }

  boolean updateMetadata(String tokenId, String metadataName, String metadataValue) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement updateMetadataStatement = connection.prepareStatement(UPDATE_METADATA_SQL)) {
      updateMetadataStatement.setString(1, metadataName.equals(TokenMetadata.PASSCODE) ? Base64.encodeBase64String(metadataValue.getBytes(UTF_8)) : metadataValue);
      updateMetadataStatement.setString(2, tokenId);
      updateMetadataStatement.setString(3, metadataName);
      return updateMetadataStatement.executeUpdate() == 1;
    }
  }

  boolean addMetadata(String tokenId, String metadataName, String metadataValue) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement addMetadataStatement = connection.prepareStatement(ADD_METADATA_SQL)) {
      addMetadataStatement.setString(1, tokenId);
      addMetadataStatement.setString(2, metadataName);
      addMetadataStatement.setString(3, metadataName.equals(TokenMetadata.PASSCODE) ? Base64.encodeBase64String(metadataValue.getBytes(UTF_8)) : metadataValue);
      return addMetadataStatement.executeUpdate() == 1;
    }
  }

  TokenMetadata getTokenMetadata(String tokenId) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement getMaxLifetimeStatement = connection.prepareStatement(GET_METADATA_SQL)) {
      getMaxLifetimeStatement.setString(1, tokenId);
      try (ResultSet rs = getMaxLifetimeStatement.executeQuery()) {
        final Map<String, String> metadataMap = new HashMap<>();
        while (rs.next()) {
          String metadataName = rs.getString(1);
          metadataMap.put(metadataName, decodeMetadata(metadataName, rs.getString(2)));
        }
        return metadataMap.isEmpty() ? null : new TokenMetadata(metadataMap);
      }
    }
  }

  private static String decodeMetadata(String metadataName, String metadataValue) {
    return metadataName.equals(TokenMetadata.PASSCODE) ? new String(Base64.decodeBase64(metadataValue.getBytes(UTF_8)), UTF_8) : metadataValue;
  }

  Collection<KnoxToken> getAllTokens() throws SQLException {
    return fetchTokens(null, GET_ALL_TOKENS_SQL);
  }

  Collection<KnoxToken> getTokens(String userName) throws SQLException {
    return fetchTokens(userName, GET_TOKENS_BY_USER_NAME_SQL);
  }

  Collection<KnoxToken> getDoAsTokens(String userName) throws SQLException {
    return fetchTokens(userName, GET_TOKENS_CREATED_BY_USER_NAME_SQL);
  }

  private Collection<KnoxToken> fetchTokens(String userName, String sql) throws SQLException {
    Map<String, KnoxToken> tokenMap = new LinkedHashMap<>();
    try (Connection connection = dataSource.getConnection(); PreparedStatement getTokenIdsStatement = connection.prepareStatement(sql)) {
      if (userName != null) {
        getTokenIdsStatement.setString(1, userName);
      }
      try (ResultSet rs = getTokenIdsStatement.executeQuery()) {
        while (rs.next()) {
          String tokenId = rs.getString(1);
          long issueTime = rs.getLong(2);
          long expiration = rs.getLong(3);
          long maxLifeTime = rs.getLong(4);
          String metaName = rs.getString(5);
          String metaValue = rs.getString(6);
          KnoxToken token = tokenMap.computeIfAbsent(tokenId, id -> new KnoxToken(tokenId, issueTime, expiration, maxLifeTime));
          token.addMetadata(metaName, decodeMetadata(metaName, metaValue));
        }
        return tokenMap.values();
      }
    }
  }
}

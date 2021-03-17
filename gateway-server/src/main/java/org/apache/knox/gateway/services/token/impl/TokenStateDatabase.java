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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.knox.gateway.services.security.token.TokenMetadata;

public class TokenStateDatabase {
  private static final String TOKENS_TABLE_NAME = "KNOX_TOKENS";
  private static final String ADD_TOKEN_SQL = "INSERT INTO " + TOKENS_TABLE_NAME + "(token_id, issue_time, expiration, max_lifetime) VALUES(?, ?, ?, ?)";
  private static final String REMOVE_TOKENS_SQL_PREFIX = "DELETE FROM " + TOKENS_TABLE_NAME + " WHERE token_id IN (";
  private static final String GET_TOKEN_EXPIRATION_SQL = "SELECT expiration FROM " + TOKENS_TABLE_NAME + " WHERE token_id = ?";
  private static final String UPDATE_TOKEN_EXPIRATION_SQL = "UPDATE " + TOKENS_TABLE_NAME + " SET expiration = ? WHERE token_id = ?";
  private static final String GET_MAX_LIFETIME_SQL = "SELECT max_lifetime FROM " + TOKENS_TABLE_NAME + " WHERE token_id = ?";
  private static final String GET_ALL_TOKEN_IDS_SQL = "SELECT token_id FROM " + TOKENS_TABLE_NAME;
  private static final String ADD_METADATA_SQL = "UPDATE " + TOKENS_TABLE_NAME + " SET username = ?, comment = ? WHERE token_id = ?";
  private static final int DELETE_BATCH_SIZE = 500;

  private final DataSource dataSource;

  TokenStateDatabase(DataSource dataSource) throws Exception {
    this.dataSource = dataSource;
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

  // This needs to be done in batches as many DB vendors have limitations on the number of items within the 'IN' clause
  boolean removeTokens(Set<String> tokenIds) throws SQLException {
    int removed = 0;
    if (tokenIds.size() <= DELETE_BATCH_SIZE) {
      removed += doRemoveTokens(tokenIds);
    } else {
      Set<String> tokenIdBatch = new HashSet<>();
      for (String tokenId : tokenIds) {
        tokenIdBatch.add(tokenId);
        if (tokenIdBatch.size() == DELETE_BATCH_SIZE) {
          removed += doRemoveTokens(tokenIdBatch);
          tokenIdBatch.clear();
        }
      }
      // one more round of removal if the last batch has less items than the configured batch size
      removed += doRemoveTokens(tokenIdBatch);
    }
    return removed == tokenIds.size();
  }

  private int doRemoveTokens(Set<String> tokenIds) throws SQLException {
    final StringBuilder statementPostFixBuilder = new StringBuilder(REMOVE_TOKENS_SQL_PREFIX);
    for (int i = 0; i < tokenIds.size(); i++) {
      if (statementPostFixBuilder.length() > REMOVE_TOKENS_SQL_PREFIX.length()) {
        statementPostFixBuilder.append(", ");
      }
      statementPostFixBuilder.append('?');
    }
    statementPostFixBuilder.append(')');
    try (Connection connection = dataSource.getConnection(); PreparedStatement removeTokensStatement = connection.prepareStatement(statementPostFixBuilder.toString())) {
      int i = 0;
      for (String tokenId : tokenIds) {
        removeTokensStatement.setString(++i, tokenId);
      }
      return removeTokensStatement.executeUpdate();
    }
  }

  long getTokenExpiration(String tokenId) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement getTokenExpirationStatement = connection.prepareStatement(GET_TOKEN_EXPIRATION_SQL)) {
      getTokenExpirationStatement.setString(1, tokenId);
      try (ResultSet rs = getTokenExpirationStatement.executeQuery()) {
        return rs.next() ? rs.getLong(1) : 0;
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

  List<String> getTokenIds() throws SQLException {
    final List<String> tokenIds = new LinkedList<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement getAlltokenIdsStatement = connection.prepareStatement(GET_ALL_TOKEN_IDS_SQL);
        ResultSet rs = getAlltokenIdsStatement.executeQuery()) {
      while (rs.next()) {
        tokenIds.add(rs.getString(1));
      }
    }
    return tokenIds;
  }

  boolean addMetadata(String tokenId, TokenMetadata metadata) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement addMetadataStatement = connection.prepareStatement(ADD_METADATA_SQL)) {
      addMetadataStatement.setString(1, metadata.getUserName());
      addMetadataStatement.setString(2, metadata.getComment());
      addMetadataStatement.setString(3, tokenId);
      return addMetadataStatement.executeUpdate() == 1;
    }
  }

}

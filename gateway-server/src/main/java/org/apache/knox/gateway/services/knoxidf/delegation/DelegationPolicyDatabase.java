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
import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.database.JDBCUtils;
import org.apache.knox.gateway.database.KnoxDatabase;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * JDBC helper for the five DELEGATION_POLICIES tables.
 * All SQL uses {@link PreparedStatement} with {@code ?} parameters only.
 * Each public method manages its own {@link Connection} and, for multi-table writes,
 * its own transaction boundaries (setAutoCommit / commit / rollback).
 */
class DelegationPolicyDatabase extends KnoxDatabase {

  static final String CORE_TABLE = "DELEGATION_POLICIES";

  private static final String INSERT_REGISTRATION_SQL =
      "INSERT INTO " + CORE_TABLE
          + " (registration_id, actor_authority, actor_id, name, status, max_token_ttl_sec, "
          + "description, created_by, created_at, updated_at, allow_headless_exchange) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private static final String UPDATE_CORE_SQL =
      "UPDATE " + CORE_TABLE + " SET "
          + "actor_authority = ?, actor_id = ?, name = ?, status = ?, max_token_ttl_sec = ?, "
          + "description = ?, created_by = ?, created_at = ?, updated_at = ?, "
          + "allow_headless_exchange = ? "
          + "WHERE registration_id = ?";

  private static final String DELETE_REGISTRATION_SQL =
      "DELETE FROM " + CORE_TABLE + " WHERE registration_id = ?";

  private static final String SELECT_BY_ID_SQL =
      "SELECT registration_id, actor_authority, actor_id, name, status, max_token_ttl_sec, "
          + "description, created_by, created_at, updated_at, allow_headless_exchange "
          + "FROM " + CORE_TABLE + " WHERE registration_id = ?";

  private static final String SELECT_BY_ACTOR_SQL =
      "SELECT registration_id, actor_authority, actor_id, name, status, max_token_ttl_sec, "
          + "description, created_by, created_at, updated_at, allow_headless_exchange "
          + "FROM " + CORE_TABLE + " WHERE actor_authority = ? AND actor_id = ?";

  private static final String SELECT_ALL_BASE_SQL =
      "SELECT registration_id, actor_authority, actor_id, name, status, max_token_ttl_sec, "
          + "description, created_by, created_at, updated_at, allow_headless_exchange "
          + "FROM " + CORE_TABLE;

  // Built at construction time with limit+1 baked in as an integer literal (Derby does not
  // support ? parameters in FETCH FIRST n ROWS ONLY). Fetching one extra row lets selectAll()
  // detect truncation without a second COUNT query.
  private final int listMaxTotal;
  private final int listMaxPerAuthority;
  private final String selectAllSql;
  private final String selectAllFilteredSql;

  private static final String INSERT_USER_SQL =
      "INSERT INTO DELEGATION_POLICY_USERS (registration_id, username) VALUES (?, ?)";

  private static final String INSERT_GROUP_SQL =
      "INSERT INTO DELEGATION_POLICY_GROUPS (registration_id, group_name) VALUES (?, ?)";

  private static final String INSERT_RESOURCE_SQL =
      "INSERT INTO DELEGATION_POLICY_RESOURCES (registration_id, resource_uri) VALUES (?, ?)";

  private static final String INSERT_SCOPE_SQL =
      "INSERT INTO DELEGATION_POLICY_RESOURCE_SCOPES (registration_id, resource_uri, scope) VALUES (?, ?, ?)";

  private static final String SELECT_USERS_SQL =
      "SELECT username FROM DELEGATION_POLICY_USERS WHERE registration_id = ?";

  private static final String SELECT_GROUPS_SQL =
      "SELECT group_name FROM DELEGATION_POLICY_GROUPS WHERE registration_id = ?";

  private static final String SELECT_RESOURCES_SQL =
      "SELECT resource_uri FROM DELEGATION_POLICY_RESOURCES WHERE registration_id = ?";

  private static final String SELECT_SCOPES_SQL =
      "SELECT scope FROM DELEGATION_POLICY_RESOURCE_SCOPES WHERE registration_id = ? AND resource_uri = ?";

  private static final String DELETE_USERS_SQL =
      "DELETE FROM DELEGATION_POLICY_USERS WHERE registration_id = ?";

  private static final String DELETE_GROUPS_SQL =
      "DELETE FROM DELEGATION_POLICY_GROUPS WHERE registration_id = ?";

  private static final String DELETE_RESOURCES_SQL =
      "DELETE FROM DELEGATION_POLICY_RESOURCES WHERE registration_id = ?";

  DelegationPolicyDatabase(DataSource dataSource, String dbType, int listMaxTotal, int listMaxPerAuthority) throws Exception {
    super(dataSource);
    this.listMaxTotal = listMaxTotal;
    this.listMaxPerAuthority = listMaxPerAuthority;
    this.selectAllSql = SELECT_ALL_BASE_SQL + " FETCH FIRST " + (listMaxTotal + 1) + " ROWS ONLY";
    this.selectAllFilteredSql = SELECT_ALL_BASE_SQL + " WHERE actor_authority = ? FETCH FIRST " + (listMaxPerAuthority + 1) + " ROWS ONLY";
    final DatabaseType databaseType = DatabaseType.fromString(dbType);
    createDelegationTablesIfNotExists(databaseType.delegationPolicyTablesSql());
  }

  /**
   * Multi-statement DDL runner: checks if DELEGATION_POLICIES exists, then strips SQL line
   * comments, splits on {@code ;}, and executes each non-empty statement individually.
   * {@link JDBCUtils#createTableFromSQL} handles only single statements; delegation needs five.
   * Comment stripping must happen before the split because the ASF license header contains a
   * semicolon inside a {@code --} comment line, which would otherwise produce a spurious token.
   */
  private void createDelegationTablesIfNotExists(String sqlFileName) throws Exception {
    if (!JDBCUtils.tableExists(CORE_TABLE, dataSource)) {
      try (InputStream is = getClass().getClassLoader().getResourceAsStream(sqlFileName);
           Connection connection = dataSource.getConnection()) {
        final String script = IOUtils.toString(is, UTF_8);
        final StringBuilder stripped = new StringBuilder();
        for (String line : script.split("\n")) {
          if (!line.trim().startsWith("--")) {
            stripped.append(line).append('\n');
          }
        }
        for (String statement : stripped.toString().split(";")) {
          final String trimmed = statement.trim();
          if (!trimmed.isEmpty()) {
            try (java.sql.Statement stmt = connection.createStatement()) {
              stmt.execute(trimmed);
            }
          }
        }
      }
    }
  }

  String insertPolicy(DelegationPolicy policy) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      final boolean prevAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        final String id = insertRegistrationRow(connection, policy);
        insertChildRows(connection, id, policy);
        connection.commit();
        return id;
      } catch (SQLException e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(prevAutoCommit);
      }
    }
  }

  Optional<DelegationPolicy> selectById(String registrationId) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID_SQL)) {
      ps.setString(1, registrationId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.of(assemblePolicyFromRow(connection, rs));
        }
      }
    }
    return Optional.empty();
  }

  Optional<DelegationPolicy> selectByActor(String actorAuthority, String actorId) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement ps = connection.prepareStatement(SELECT_BY_ACTOR_SQL)) {
      ps.setString(1, actorAuthority);
      ps.setString(2, actorId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.of(assemblePolicyFromRow(connection, rs));
        }
      }
    }
    return Optional.empty();
  }

  DelegationPolicyList selectAll(String actorAuthorityFilter) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      final int limit = (actorAuthorityFilter != null) ? listMaxPerAuthority : listMaxTotal;
      final List<DelegationPolicy> rows = new ArrayList<>();
      final String sql = (actorAuthorityFilter != null) ? selectAllFilteredSql : selectAllSql;
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        if (actorAuthorityFilter != null) {
          ps.setString(1, actorAuthorityFilter);
        }
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            rows.add(assemblePolicyFromRow(connection, rs));
          }
        }
      }
      final boolean hasMore = rows.size() > limit;
      return new DelegationPolicyList(hasMore ? rows.subList(0, limit) : rows, hasMore);
    }
  }

  void updatePolicy(String registrationId, DelegationPolicy policy) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      final boolean prevAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        updateCoreRow(connection, registrationId, policy);
        replaceChildRows(connection, registrationId, policy);
        connection.commit();
      } catch (SQLException e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(prevAutoCommit);
      }
    }
  }

  void deletePolicy(String registrationId) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement ps = connection.prepareStatement(DELETE_REGISTRATION_SQL)) {
      ps.setString(1, registrationId);
      ps.executeUpdate();
    }
  }

  private String insertRegistrationRow(Connection connection, DelegationPolicy policy) throws SQLException {
    final String id = UUID.randomUUID().toString();
    try (PreparedStatement ps = connection.prepareStatement(INSERT_REGISTRATION_SQL)) {
      ps.setString(1, id);
      ps.setString(2, policy.getActorAuthority());
      ps.setString(3, policy.getActorId());
      ps.setString(4, policy.getName());
      ps.setString(5, policy.getStatus());
      if (policy.getMaxTokenTtlSec() != null) {
        ps.setInt(6, policy.getMaxTokenTtlSec());
      } else {
        ps.setNull(6, java.sql.Types.INTEGER);
      }
      ps.setString(7, policy.getDescription());
      ps.setString(8, policy.getCreatedBy());
      ps.setTimestamp(9, Timestamp.from(policy.getCreatedAt()));
      ps.setTimestamp(10, Timestamp.from(policy.getUpdatedAt()));
      ps.setBoolean(11, policy.isAllowHeadlessExchange());
      ps.executeUpdate();
    }
    return id;
  }

  private void insertChildRows(Connection connection, String registrationId, DelegationPolicy policy) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(INSERT_USER_SQL)) {
      for (String username : policy.getCanActForUsers()) {
        ps.setString(1, registrationId);
        ps.setString(2, username);
        ps.addBatch();
      }
      if (!policy.getCanActForUsers().isEmpty()) {
        ps.executeBatch();
      }
    }
    try (PreparedStatement ps = connection.prepareStatement(INSERT_GROUP_SQL)) {
      for (String groupName : policy.getCanActForGroups()) {
        ps.setString(1, registrationId);
        ps.setString(2, groupName);
        ps.addBatch();
      }
      if (!policy.getCanActForGroups().isEmpty()) {
        ps.executeBatch();
      }
    }
    try (PreparedStatement psResource = connection.prepareStatement(INSERT_RESOURCE_SQL);
         PreparedStatement psScope = connection.prepareStatement(INSERT_SCOPE_SQL)) {
      for (Map.Entry<String, Set<String>> entry : policy.getResourcePolicy().entrySet()) {
        final String resourceUri = entry.getKey();
        psResource.setString(1, registrationId);
        psResource.setString(2, resourceUri);
        psResource.addBatch();
        for (String scope : entry.getValue()) {
          psScope.setString(1, registrationId);
          psScope.setString(2, resourceUri);
          psScope.setString(3, scope);
          psScope.addBatch();
        }
      }
      if (!policy.getResourcePolicy().isEmpty()) {
        psResource.executeBatch();
      }
      if (policy.getResourcePolicy().values().stream().anyMatch(s -> !s.isEmpty())) {
        psScope.executeBatch();
      }
    }
  }

  private void updateCoreRow(Connection connection, String registrationId, DelegationPolicy policy) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(UPDATE_CORE_SQL)) {
      ps.setString(1, policy.getActorAuthority());
      ps.setString(2, policy.getActorId());
      ps.setString(3, policy.getName());
      ps.setString(4, policy.getStatus());
      if (policy.getMaxTokenTtlSec() != null) {
        ps.setInt(5, policy.getMaxTokenTtlSec());
      } else {
        ps.setNull(5, java.sql.Types.INTEGER);
      }
      ps.setString(6, policy.getDescription());
      ps.setString(7, policy.getCreatedBy());
      ps.setTimestamp(8, Timestamp.from(policy.getCreatedAt()));
      ps.setTimestamp(9, Timestamp.from(policy.getUpdatedAt()));
      ps.setBoolean(10, policy.isAllowHeadlessExchange());
      ps.setString(11, registrationId);
      ps.executeUpdate();
    }
  }

  private void deleteChildRows(Connection connection, String registrationId) throws SQLException {
    for (String sql : new String[]{DELETE_RESOURCES_SQL, DELETE_GROUPS_SQL, DELETE_USERS_SQL}) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setString(1, registrationId);
        ps.executeUpdate();
      }
    }
  }

  private void replaceChildRows(Connection connection, String registrationId, DelegationPolicy policy) throws SQLException {
    deleteChildRows(connection, registrationId);
    insertChildRows(connection, registrationId, policy);
  }

  private void deleteRegistrationRow(Connection connection, String registrationId) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(DELETE_REGISTRATION_SQL)) {
      ps.setString(1, registrationId);
      ps.executeUpdate();
    }
  }

  private DelegationPolicy assemblePolicyFromRow(Connection connection, ResultSet rs) throws SQLException {
    final String registrationId = rs.getString("registration_id");
    final String actorAuthority = rs.getString("actor_authority");
    final String actorId = rs.getString("actor_id");
    final String name = rs.getString("name");
    final String status = rs.getString("status");
    final int maxTtl = rs.getInt("max_token_ttl_sec");
    final Integer maxTokenTtlSec = rs.wasNull() ? null : maxTtl;
    final String description = rs.getString("description");
    final String createdBy = rs.getString("created_by");
    final Timestamp createdAt = rs.getTimestamp("created_at");
    final Timestamp updatedAt = rs.getTimestamp("updated_at");
    final boolean allowHeadlessExchange = rs.getBoolean("allow_headless_exchange");

    final Set<String> users = selectStrings(connection, SELECT_USERS_SQL, registrationId, "username");
    final Set<String> groups = selectStrings(connection, SELECT_GROUPS_SQL, registrationId, "group_name");
    final Map<String, Set<String>> resourcePolicy = selectResourcePolicy(connection, registrationId);

    return new DelegationPolicy(
        registrationId, actorAuthority, actorId, name, status, maxTokenTtlSec,
        description, createdBy, createdAt.toInstant(), updatedAt.toInstant(),
        allowHeadlessExchange, users, groups, resourcePolicy);
  }

  private Set<String> selectStrings(Connection connection, String sql, String registrationId, String columnName) throws SQLException {
    final Set<String> result = new HashSet<>();
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, registrationId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          result.add(rs.getString(columnName));
        }
      }
    }
    return result;
  }

  private Map<String, Set<String>> selectResourcePolicy(Connection connection, String registrationId) throws SQLException {
    final Map<String, Set<String>> resourcePolicy = new HashMap<>();
    try (PreparedStatement ps = connection.prepareStatement(SELECT_RESOURCES_SQL)) {
      ps.setString(1, registrationId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          final String resourceUri = rs.getString("resource_uri");
          final Set<String> scopes = selectScopesForResource(connection, registrationId, resourceUri);
          resourcePolicy.put(resourceUri, scopes);
        }
      }
    }
    return resourcePolicy;
  }

  private Set<String> selectScopesForResource(Connection connection, String registrationId, String resourceUri) throws SQLException {
    final Set<String> scopes = new HashSet<>();
    try (PreparedStatement ps = connection.prepareStatement(SELECT_SCOPES_SQL)) {
      ps.setString(1, registrationId);
      ps.setString(2, resourceUri);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          scopes.add(rs.getString("scope"));
        }
      }
    }
    return scopes;
  }
}

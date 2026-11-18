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

import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.database.KnoxDatabase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * JDBC helper for the five DELEGATION_POLICIES tables.
 * All SQL uses {@link PreparedStatement} with {@code ?} parameters only.
 * Each public method manages its own {@link Connection} and, for multi-table writes,
 * its own transaction boundaries (setAutoCommit / commit / rollback).
 * <p>
 * Reads never issue a query per child row. {@link #selectByActor(String, String)} and
 * {@link #selectById(String)} each read a whole policy in one statement, and
 * {@link #selectAll(String)} reads a page of policies in one statement for the core rows plus a
 * fixed four per batch of registration ids. A policy may carry an unbounded number of users,
 * groups, resources and scopes, so any per-child-row query multiplies the cost of a single read.
 */
class DelegationPolicyDatabase extends KnoxDatabase {

  static final String CORE_TABLE = "DELEGATION_POLICIES";

  /**
   * Registration ids bound into one IN list by {@link #selectAll(String)}. Oracle rejects an IN
   * list of more than 1000 expressions (ORA-01795), so a batch never exceeds that. Overridable
   * through the package-private constructor so the batching loop can be exercised without
   * registering a batch's worth of policies.
   */
  private static final int ID_CHUNK_SIZE = 1000;

  /**
   * Rows the driver is asked to fetch per round trip. Oracle's driver prefetches 10 rows by
   * default, which would split a single-statement policy read into one round trip per 10 rows.
   * PostgreSQL ignores a positive fetch size while autoCommit is on, and MySQL and H2 are
   * unaffected, so this only changes behavior on Oracle.
   */
  private static final int READ_FETCH_SIZE = 1024;

  /** Number of UNION ALL branches in the single-policy read; each one binds the predicate again. */
  private static final int POLICY_BRANCHES = 5;

  private static final String INSERT_REGISTRATION_SQL =
      "INSERT INTO " + CORE_TABLE
          + " (registration_id, actor_authority, actor_id, name, status, token_ttl_sec, "
          + "description, created_by, created_at, updated_at, allow_headless_exchange) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  // actor_authority/actor_id/created_by/created_at are deliberately absent from SET: identity and
  // creation metadata are immutable after registration. updated_at is computed by the storage layer
  // and bound below (param 6) from the app clock -- the same source INSERT uses for created_at, so
  // both columns share one clock/zone and stay comparable (a DB-side CURRENT_TIMESTAMP could read
  // earlier than the JVM-written created_at). The WHERE clause requires the identity fields to still
  // match, so an update attempting to change them affects 0 rows -- indistinguishable from (and
  // reported the same as) registrationId not existing at all.
  private static final String UPDATE_CORE_SQL =
      "UPDATE " + CORE_TABLE + " SET "
          + "name = ?, status = ?, token_ttl_sec = ?, description = ?, allow_headless_exchange = ?, "
          + "updated_at = ? "
          + "WHERE registration_id = ? AND actor_authority = ? AND actor_id = ?";

  private static final String DELETE_REGISTRATION_SQL =
      "DELETE FROM " + CORE_TABLE + " WHERE registration_id = ?";

  // The 11 core columns, aliased so the column labels of the UNION ALL below are the same labels
  // SELECT_ALL_BASE_SQL produces: standard SQL takes a union's column names from its first branch,
  // so one row reader serves both the single-policy reads and the list read.
  private static final String CORE_COLUMNS =
      "p.registration_id AS registration_id, p.actor_authority AS actor_authority, "
          + "p.actor_id AS actor_id, p.name AS name, p.status AS status, "
          + "p.token_ttl_sec AS token_ttl_sec, p.description AS description, "
          + "p.created_by AS created_by, p.created_at AS created_at, "
          + "p.updated_at AS updated_at, p.allow_headless_exchange AS allow_headless_exchange";

  // Value used for a child column that a given branch has nothing to put in. It has to be a real
  // column: Oracle rejects a bare NULL in a union column position (ORA-01790), and MySQL's CAST
  // offers no VARCHAR target, so neither "NULL" nor "CAST(NULL AS VARCHAR(n))" is portable across
  // the supported databases. The assembler ignores these positions for the row kinds that do not
  // use them.
  private static final String CHILD_FILLER = "p.registration_id";

  private static final String SELECT_BY_ID_SQL =
      buildSelectPolicySql("p.registration_id = ?");

  private static final String SELECT_BY_ACTOR_SQL =
      buildSelectPolicySql("p.actor_authority = ? AND p.actor_id = ?");

  private static final String SELECT_ALL_BASE_SQL =
      "SELECT registration_id, actor_authority, actor_id, name, status, token_ttl_sec, "
          + "description, created_by, created_at, updated_at, allow_headless_exchange "
          + "FROM " + CORE_TABLE;

  private static final String SELECT_ALL_FILTERED_SQL =
      SELECT_ALL_BASE_SQL + " WHERE actor_authority = ?";

  private final int listMaxTotal;
  private final int listMaxPerAuthority;
  private final int idChunkSize;

  private static final String INSERT_USER_SQL =
      "INSERT INTO DELEGATION_POLICY_USERS (registration_id, username) VALUES (?, ?)";

  private static final String INSERT_GROUP_SQL =
      "INSERT INTO DELEGATION_POLICY_GROUPS (registration_id, group_name) VALUES (?, ?)";

  private static final String INSERT_RESOURCE_SQL =
      "INSERT INTO DELEGATION_POLICY_RESOURCES (registration_id, resource_uri) VALUES (?, ?)";

  private static final String INSERT_SCOPE_SQL =
      "INSERT INTO DELEGATION_POLICY_RESOURCE_SCOPES (registration_id, resource_uri, scope) VALUES (?, ?, ?)";

  // Batched child reads for selectAll(). Each is completed with one ? per registration id in the
  // current batch; see inListSql(String, int).
  private static final String SELECT_USERS_IN_SQL =
      "SELECT registration_id, username FROM DELEGATION_POLICY_USERS WHERE registration_id IN (";

  private static final String SELECT_GROUPS_IN_SQL =
      "SELECT registration_id, group_name FROM DELEGATION_POLICY_GROUPS WHERE registration_id IN (";

  private static final String SELECT_RESOURCES_IN_SQL =
      "SELECT registration_id, resource_uri FROM DELEGATION_POLICY_RESOURCES WHERE registration_id IN (";

  private static final String SELECT_SCOPES_IN_SQL =
      "SELECT registration_id, resource_uri, scope FROM DELEGATION_POLICY_RESOURCE_SCOPES "
          + "WHERE registration_id IN (";

  private static final String DELETE_USERS_SQL =
      "DELETE FROM DELEGATION_POLICY_USERS WHERE registration_id = ?";

  private static final String DELETE_GROUPS_SQL =
      "DELETE FROM DELEGATION_POLICY_GROUPS WHERE registration_id = ?";

  private static final String DELETE_RESOURCES_SQL =
      "DELETE FROM DELEGATION_POLICY_RESOURCES WHERE registration_id = ?";

  DelegationPolicyDatabase(DataSource dataSource, String dbType, int listMaxTotal, int listMaxPerAuthority) throws Exception {
    this(dataSource, dbType, listMaxTotal, listMaxPerAuthority, ID_CHUNK_SIZE);
  }

  DelegationPolicyDatabase(DataSource dataSource, String dbType, int listMaxTotal,
      int listMaxPerAuthority, int idChunkSize) throws Exception {
    super(dataSource);
    this.listMaxTotal = listMaxTotal;
    this.listMaxPerAuthority = listMaxPerAuthority;
    this.idChunkSize = idChunkSize;
    final DatabaseType databaseType = DatabaseType.fromString(dbType);
    createTablesIfNotExist(databaseType.delegationPolicyTablesSql());
  }

  /**
   * Builds the one-statement read of a whole policy: the core row, then one branch per child
   * table, concatenated with UNION ALL. The branches are summed rather than joined, so the row
   * count is 1 + users + groups + resources + scopes. Joining the child tables to each other
   * instead would return their cartesian product.
   * <p>
   * Each branch seeks the core row through the primary key or the actor unique constraint and
   * reads its child rows through the leading column of that child table's primary key, so the
   * predicate is bound once per branch.
   */
  private static String buildSelectPolicySql(String predicate) {
    return "SELECT " + CORE_COLUMNS + ", 'C' AS row_kind, "
        + CHILD_FILLER + " AS child_a, " + CHILD_FILLER + " AS child_b"
        + " FROM " + CORE_TABLE + " p WHERE " + predicate
        + " UNION ALL SELECT " + CORE_COLUMNS + ", 'U', u.username, " + CHILD_FILLER
        + " FROM " + CORE_TABLE + " p JOIN DELEGATION_POLICY_USERS u"
        + " ON u.registration_id = p.registration_id WHERE " + predicate
        + " UNION ALL SELECT " + CORE_COLUMNS + ", 'G', g.group_name, " + CHILD_FILLER
        + " FROM " + CORE_TABLE + " p JOIN DELEGATION_POLICY_GROUPS g"
        + " ON g.registration_id = p.registration_id WHERE " + predicate
        + " UNION ALL SELECT " + CORE_COLUMNS + ", 'R', r.resource_uri, " + CHILD_FILLER
        + " FROM " + CORE_TABLE + " p JOIN DELEGATION_POLICY_RESOURCES r"
        + " ON r.registration_id = p.registration_id WHERE " + predicate
        + " UNION ALL SELECT " + CORE_COLUMNS + ", 'S', s.resource_uri, s.scope"
        + " FROM " + CORE_TABLE + " p JOIN DELEGATION_POLICY_RESOURCE_SCOPES s"
        + " ON s.registration_id = p.registration_id WHERE " + predicate;
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
      ps.setFetchSize(READ_FETCH_SIZE);
      bindOncePerBranch(ps, registrationId);
      try (ResultSet rs = ps.executeQuery()) {
        return assemblePolicy(rs);
      }
    }
  }

  Optional<DelegationPolicy> selectByActor(String actorAuthority, String actorId) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement ps = connection.prepareStatement(SELECT_BY_ACTOR_SQL)) {
      ps.setFetchSize(READ_FETCH_SIZE);
      bindOncePerBranch(ps, actorAuthority, actorId);
      try (ResultSet rs = ps.executeQuery()) {
        return assemblePolicy(rs);
      }
    }
  }

  /**
   * Reads a page of policies in two phases: the core rows, capped at limit+1 so truncation is
   * visible without a second COUNT query, and then the children of exactly that page, batched by
   * registration id. The statement count is 1 + 4 per batch of ids, independent of how many
   * resources the policies carry.
   */
  DelegationPolicyList selectAll(String actorAuthorityFilter) throws SQLException {
    final int limit = (actorAuthorityFilter != null) ? listMaxPerAuthority : listMaxTotal;
    final String sql = (actorAuthorityFilter != null) ? SELECT_ALL_FILTERED_SQL : SELECT_ALL_BASE_SQL;
    try (Connection connection = dataSource.getConnection()) {
      final List<CoreRow> cores = new ArrayList<>();
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        // setMaxRows rather than FETCH FIRST or LIMIT: neither of those is accepted by every
        // supported database, and the driver pushes this down the same way.
        ps.setMaxRows(limit + 1);
        // The fetch size must not exceed the row cap -- H2 rejects a larger one outright.
        ps.setFetchSize(Math.min(READ_FETCH_SIZE, limit + 1));
        if (actorAuthorityFilter != null) {
          ps.setString(1, actorAuthorityFilter);
        }
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            cores.add(readCoreRow(rs));
          }
        }
      }
      final boolean hasMore = cores.size() > limit;
      final List<CoreRow> page = hasMore ? cores.subList(0, limit) : cores;
      return new DelegationPolicyList(loadChildren(connection, page), hasMore);
    }
  }

  /**
   * @return true if a core row matched registrationId and was updated; false if no row matches --
   *     either because registrationId does not exist at all, or because it exists but with a
   *     different actorAuthority/actorId (identity mismatch). Either way, nothing is written.
   */
  boolean updatePolicy(String registrationId, DelegationPolicy policy) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      final boolean prevAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        if (updateCoreRow(connection, registrationId, policy) == 0) {
          connection.rollback();
          return false;
        }
        replaceChildRows(connection, registrationId, policy);
        connection.commit();
        return true;
      } catch (SQLException e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(prevAutoCommit);
      }
    }
  }

  /**
   * @return true if a core row matched registrationId and was deleted (child rows are removed by
   *     the database via ON DELETE CASCADE); false if registrationId does not exist, in which case
   *     nothing was deleted.
   */
  boolean deletePolicy(String registrationId) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      return deleteRegistrationRow(connection, registrationId) > 0;
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
      if (policy.getTokenTtlSec() != null) {
        ps.setInt(6, policy.getTokenTtlSec());
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

  private int updateCoreRow(Connection connection, String registrationId, DelegationPolicy policy) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(UPDATE_CORE_SQL)) {
      ps.setString(1, policy.getName());
      ps.setString(2, policy.getStatus());
      if (policy.getTokenTtlSec() != null) {
        ps.setInt(3, policy.getTokenTtlSec());
      } else {
        ps.setNull(3, java.sql.Types.INTEGER);
      }
      ps.setString(4, policy.getDescription());
      ps.setBoolean(5, policy.isAllowHeadlessExchange());
      ps.setTimestamp(6, Timestamp.from(Instant.now()));
      ps.setString(7, registrationId);
      ps.setString(8, policy.getActorAuthority());
      ps.setString(9, policy.getActorId());
      return ps.executeUpdate();
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

  private int deleteRegistrationRow(Connection connection, String registrationId) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(DELETE_REGISTRATION_SQL)) {
      ps.setString(1, registrationId);
      return ps.executeUpdate();
    }
  }

  /** Binds the read predicate once for each UNION ALL branch, in branch order. */
  private static void bindOncePerBranch(PreparedStatement ps, String... values) throws SQLException {
    int index = 1;
    for (int branch = 0; branch < POLICY_BRANCHES; branch++) {
      for (String value : values) {
        ps.setString(index++, value);
      }
    }
  }

  /**
   * Collects the rows of one {@link #buildSelectPolicySql(String)} result into a policy. The core
   * columns repeat on every row, so the first row of any kind supplies them; the row kind says
   * which child collection the row's child columns belong to.
   * <p>
   * UNION ALL does not promise the branches arrive in order, so nothing here depends on the core
   * row coming first, and a scope row is tolerated ahead of its resource row.
   *
   * @return empty when the result had no rows at all, meaning no policy matched the predicate.
   */
  private Optional<DelegationPolicy> assemblePolicy(ResultSet rs) throws SQLException {
    CoreRow core = null;
    final Set<String> users = new HashSet<>();
    final Set<String> groups = new HashSet<>();
    final Map<String, Set<String>> resourcePolicy = new HashMap<>();
    while (rs.next()) {
      if (core == null) {
        core = readCoreRow(rs);
      }
      switch (rs.getString("row_kind")) {
        case "U":
          users.add(rs.getString("child_a"));
          break;
        case "G":
          groups.add(rs.getString("child_a"));
          break;
        case "R":
          // A resource with no scopes maps to an empty set, which means every scope is allowed
          // for it. Only the key is added here; the scope branch fills the set.
          resourcePolicy.computeIfAbsent(rs.getString("child_a"), uri -> new HashSet<>());
          break;
        case "S":
          resourcePolicy.computeIfAbsent(rs.getString("child_a"), uri -> new HashSet<>())
              .add(rs.getString("child_b"));
          break;
        default:
          break;
      }
    }
    return core == null ? Optional.empty()
        : Optional.of(core.toPolicy(users, groups, resourcePolicy));
  }

  /**
   * Loads the children of an already-read page of core rows and builds the policies, preserving
   * the order the core query returned. Ids are read back in batches of {@link #idChunkSize}, four
   * queries per batch.
   */
  private List<DelegationPolicy> loadChildren(Connection connection, List<CoreRow> page) throws SQLException {
    final Map<String, Set<String>> usersById = new HashMap<>();
    final Map<String, Set<String>> groupsById = new HashMap<>();
    final Map<String, Map<String, Set<String>>> resourcesById = new HashMap<>();
    final List<String> ids = new ArrayList<>(page.size());
    for (CoreRow core : page) {
      ids.add(core.registrationId());
    }
    for (int from = 0; from < ids.size(); from += idChunkSize) {
      final List<String> batch = ids.subList(from, Math.min(from + idChunkSize, ids.size()));
      selectChildValues(connection, SELECT_USERS_IN_SQL, batch, "username", usersById);
      selectChildValues(connection, SELECT_GROUPS_IN_SQL, batch, "group_name", groupsById);
      selectChildResources(connection, batch, resourcesById);
      selectChildScopes(connection, batch, resourcesById);
    }
    final List<DelegationPolicy> policies = new ArrayList<>(page.size());
    for (CoreRow core : page) {
      final String id = core.registrationId();
      policies.add(core.toPolicy(
          usersById.getOrDefault(id, Collections.emptySet()),
          groupsById.getOrDefault(id, Collections.emptySet()),
          resourcesById.getOrDefault(id, Collections.emptyMap())));
    }
    return policies;
  }

  /** Completes a batched child read with one {@code ?} per registration id in the batch. */
  private static String inListSql(String prefix, int idCount) {
    final StringBuilder sql = new StringBuilder(prefix.length() + idCount * 2 + 1).append(prefix);
    for (int i = 0; i < idCount; i++) {
      if (i > 0) {
        sql.append(',');
      }
      sql.append('?');
    }
    return sql.append(')').toString();
  }

  private static void bindAll(PreparedStatement ps, List<String> values) throws SQLException {
    for (int i = 0; i < values.size(); i++) {
      ps.setString(i + 1, values.get(i));
    }
  }

  private void selectChildValues(Connection connection, String sqlPrefix, List<String> ids,
      String valueColumn, Map<String, Set<String>> target) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(inListSql(sqlPrefix, ids.size()))) {
      ps.setFetchSize(READ_FETCH_SIZE);
      bindAll(ps, ids);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          target.computeIfAbsent(rs.getString("registration_id"), id -> new HashSet<>())
              .add(rs.getString(valueColumn));
        }
      }
    }
  }

  private void selectChildResources(Connection connection, List<String> ids,
      Map<String, Map<String, Set<String>>> target) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(inListSql(SELECT_RESOURCES_IN_SQL, ids.size()))) {
      ps.setFetchSize(READ_FETCH_SIZE);
      bindAll(ps, ids);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          target.computeIfAbsent(rs.getString("registration_id"), id -> new HashMap<>())
              .computeIfAbsent(rs.getString("resource_uri"), uri -> new HashSet<>());
        }
      }
    }
  }

  private void selectChildScopes(Connection connection, List<String> ids,
      Map<String, Map<String, Set<String>>> target) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(inListSql(SELECT_SCOPES_IN_SQL, ids.size()))) {
      ps.setFetchSize(READ_FETCH_SIZE);
      bindAll(ps, ids);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          target.computeIfAbsent(rs.getString("registration_id"), id -> new HashMap<>())
              .computeIfAbsent(rs.getString("resource_uri"), uri -> new HashSet<>())
              .add(rs.getString("scope"));
        }
      }
    }
  }

  private static CoreRow readCoreRow(ResultSet rs) throws SQLException {
    final String registrationId = rs.getString("registration_id");
    final String actorAuthority = rs.getString("actor_authority");
    final String actorId = rs.getString("actor_id");
    final String name = rs.getString("name");
    final String status = rs.getString("status");
    final int tokenTtlRaw = rs.getInt("token_ttl_sec");
    // wasNull() reports on the column just read, so it has to be consulted before any other get.
    final Integer tokenTtlSec = rs.wasNull() ? null : tokenTtlRaw;
    final String description = rs.getString("description");
    final String createdBy = rs.getString("created_by");
    final Timestamp createdAt = rs.getTimestamp("created_at");
    final Timestamp updatedAt = rs.getTimestamp("updated_at");
    final boolean allowHeadlessExchange = rs.getBoolean("allow_headless_exchange");
    return new CoreRow(registrationId, actorAuthority, actorId, name, status, tokenTtlSec,
        description, createdBy, createdAt.toInstant(), updatedAt.toInstant(), allowHeadlessExchange);
  }

  /**
   * One policy's core row, held while its child rows are still being read. tokenTtlSec is boxed so
   * that a NULL token_ttl_sec stays null instead of becoming zero, and the timestamps are
   * converted on read because {@link DelegationPolicy} is built from {@link Instant}.
   */
  private record CoreRow(String registrationId, String actorAuthority, String actorId,
                         String name, String status, Integer tokenTtlSec, String description,
                         String createdBy, Instant createdAt, Instant updatedAt,
                         boolean allowHeadlessExchange) {

    DelegationPolicy toPolicy(Set<String> users, Set<String> groups,
        Map<String, Set<String>> resourcePolicy) {
      return new DelegationPolicy(registrationId, actorAuthority, actorId, name, status,
          tokenTtlSec, description, createdBy, createdAt, updatedAt, allowHeadlessExchange,
          users, groups, resourcePolicy);
    }
  }
}

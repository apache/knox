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

import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.database.JDBCUtils;
import org.apache.knox.gateway.database.KnoxDatabase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * JDBC helper for the {@code KNOXIDF_AUTHZ_FLOW_STATE} table. {@code expires_at} holds epoch
 * milliseconds; rows whose {@code expires_at} is not in the future are treated as absent.
 */
class AuthzFlowStateDatabase extends KnoxDatabase {

  static final String TABLE_NAME = "KNOXIDF_AUTHZ_FLOW_STATE";
  static final String EXPIRES_AT_INDEX_NAME = "KNOXIDF_AUTHZ_FLOW_STATE_EXP";

  /** How {@link #consume} reads and deletes a row. */
  enum ConsumeStrategy {
    /** {@code DELETE ... RETURNING payload}: one statement (PostgreSQL, MariaDB). */
    DELETE_RETURNING("DELETE FROM " + TABLE_NAME + " WHERE id = ? AND kind = ? AND expires_at > ? RETURNING payload"),
    /** {@code SELECT ... FROM OLD TABLE (DELETE ...)}: one statement (H2). */
    DELETE_FROM_OLD_TABLE("SELECT payload FROM OLD TABLE (DELETE FROM " + TABLE_NAME
        + " WHERE id = ? AND kind = ? AND expires_at > ?)"),
    /** SELECT, then a DELETE whose affected-row count decides the winner (MySQL, Oracle). */
    SELECT_THEN_DELETE(null);

    private final String sql;

    ConsumeStrategy(String sql) {
      this.sql = sql;
    }

    static ConsumeStrategy forDatabase(DatabaseType databaseType) {
      switch (databaseType) {
        case POSTGRESQL:
        case MARIADB:
          return DELETE_RETURNING;
        case H2:
          return DELETE_FROM_OLD_TABLE;
        default:
          // MySQL has no RETURNING; Oracle's RETURNING ... INTO needs driver-specific binds.
          return SELECT_THEN_DELETE;
      }
    }
  }

  private static final String CREATE_EXPIRES_AT_INDEX_SQL =
      "CREATE INDEX " + EXPIRES_AT_INDEX_NAME + " ON " + TABLE_NAME + " (expires_at)";
  private static final String INSERT_SQL =
      "INSERT INTO " + TABLE_NAME + " (id, kind, payload, expires_at) VALUES (?, ?, ?, ?)";
  // No expiry check: put replaces the entry, including an expired row the sweep hasn't removed yet
  private static final String UPDATE_SQL =
      "UPDATE " + TABLE_NAME + " SET payload = ?, expires_at = ? WHERE id = ? AND kind = ?";
  private static final String SELECT_SQL =
      "SELECT payload FROM " + TABLE_NAME + " WHERE id = ? AND kind = ? AND expires_at > ?";
  private static final String DELETE_UNEXPIRED_SQL =
      "DELETE FROM " + TABLE_NAME + " WHERE id = ? AND kind = ? AND expires_at > ?";
  private static final String DELETE_SQL =
      "DELETE FROM " + TABLE_NAME + " WHERE id = ? AND kind = ?";
  private static final String DELETE_EXPIRED_SQL =
      "DELETE FROM " + TABLE_NAME + " WHERE expires_at <= ?";

  private final ConsumeStrategy consumeStrategy;

  AuthzFlowStateDatabase(DataSource dataSource, String dbType) throws Exception {
    this(dataSource, DatabaseType.fromString(dbType));
  }

  private AuthzFlowStateDatabase(DataSource dataSource, DatabaseType databaseType) throws Exception {
    this(dataSource, databaseType, ConsumeStrategy.forDatabase(databaseType));
  }

  AuthzFlowStateDatabase(DataSource dataSource, DatabaseType databaseType, ConsumeStrategy consumeStrategy)
      throws Exception {
    super(dataSource);
    this.consumeStrategy = consumeStrategy;
    createTableIfNotExists(TABLE_NAME, databaseType.authzFlowStateTableSql());
    createExpiresAtIndexIfNotExists();
  }

  ConsumeStrategy consumeStrategy() {
    return consumeStrategy;
  }

  // Without this index the sweep scans the table and, on InnoDB, locks every scanned row. Not in
  // the DDL script because not every driver accepts multi-statement scripts.
  private void createExpiresAtIndexIfNotExists() throws SQLException {
    if (JDBCUtils.indexExists(TABLE_NAME, EXPIRES_AT_INDEX_NAME, dataSource)) {
      return;
    }
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      statement.execute(CREATE_EXPIRES_AT_INDEX_SQL);
    } catch (SQLException e) {
      // Another gateway instance may have created it in the meantime
      if (!JDBCUtils.indexExists(TABLE_NAME, EXPIRES_AT_INDEX_NAME, dataSource)) {
        throw e;
      }
    }
  }

  void upsert(String kind, String id, String payload, long expiresAt) throws SQLException {
    // INSERT first since ids are random; portable, unlike MERGE / ON CONFLICT. The second INSERT
    // covers a row consumed between the failed INSERT and the UPDATE.
    try (Connection connection = dataSource.getConnection()) {
      try {
        insert(connection, kind, id, payload, expiresAt);
        return;
      } catch (SQLException e) {
        if (!JDBCUtils.isUniqueConstraintViolation(e)) {
          throw e;
        }
      }
      if (update(connection, kind, id, payload, expiresAt) == 0) {
        insert(connection, kind, id, payload, expiresAt);
      }
    }
  }

  Optional<String> select(String kind, String id, long now) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      return select(connection, kind, id, now);
    }
  }

  /** Reads and deletes the row; only the caller whose DELETE removes it gets the payload. */
  Optional<String> consume(String kind, String id, long now) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      if (consumeStrategy == ConsumeStrategy.SELECT_THEN_DELETE) {
        final Optional<String> payload = select(connection, kind, id, now);
        if (!payload.isPresent()) {
          return Optional.empty();
        }
        try (PreparedStatement ps = connection.prepareStatement(DELETE_UNEXPIRED_SQL)) {
          setKey(ps, kind, id, now);
          return ps.executeUpdate() == 1 ? payload : Optional.empty();
        }
      }
      try (PreparedStatement ps = connection.prepareStatement(consumeStrategy.sql)) {
        setKey(ps, kind, id, now);
        try (ResultSet rs = ps.executeQuery()) {
          return rs.next() ? Optional.ofNullable(rs.getString("payload")) : Optional.empty();
        }
      }
    }
  }

  void delete(String kind, String id) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement ps = connection.prepareStatement(DELETE_SQL)) {
      ps.setString(1, id);
      ps.setString(2, kind);
      ps.executeUpdate();
    }
  }

  int deleteExpired(long now) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement ps = connection.prepareStatement(DELETE_EXPIRED_SQL)) {
      ps.setLong(1, now);
      return ps.executeUpdate();
    }
  }

  private static Optional<String> select(Connection connection, String kind, String id, long now) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(SELECT_SQL)) {
      setKey(ps, kind, id, now);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.ofNullable(rs.getString("payload")) : Optional.empty();
      }
    }
  }

  private static void setKey(PreparedStatement ps, String kind, String id, long now) throws SQLException {
    ps.setString(1, id);
    ps.setString(2, kind);
    ps.setLong(3, now);
  }

  private static int update(Connection connection, String kind, String id, String payload, long expiresAt)
      throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(UPDATE_SQL)) {
      ps.setString(1, payload);
      ps.setLong(2, expiresAt);
      ps.setString(3, id);
      ps.setString(4, kind);
      return ps.executeUpdate();
    }
  }

  private static void insert(Connection connection, String kind, String id, String payload, long expiresAt)
      throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {
      ps.setString(1, id);
      ps.setString(2, kind);
      ps.setString(3, payload);
      ps.setLong(4, expiresAt);
      ps.executeUpdate();
    }
  }
}

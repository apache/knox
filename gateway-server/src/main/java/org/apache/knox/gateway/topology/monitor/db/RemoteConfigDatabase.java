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
package org.apache.knox.gateway.topology.monitor.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import org.apache.knox.gateway.util.JDBCUtils;

public class RemoteConfigDatabase {
  private static final String KNOX_PROVIDERS_TABLE_CREATE_SQL_FILE_NAME = "createKnoxProvidersTable.sql";
  private static final String KNOX_DESCRIPTORS_TABLE_CREATE_SQL_FILE_NAME = "createKnoxDescriptorsTable.sql";
  private static final String KNOX_PROVIDERS_TABLE_NAME = "KNOX_PROVIDERS";
  private static final String KNOX_DESCRIPTORS_TABLE_NAME = "KNOX_DESCRIPTORS";
  private final DataSource dataSource;

  public RemoteConfigDatabase(DataSource dataSource) {
    this.dataSource = dataSource;
    ensureTablesExist();
  }

  private void ensureTablesExist() {
    try {
      createTableIfNotExists(KNOX_PROVIDERS_TABLE_NAME, KNOX_PROVIDERS_TABLE_CREATE_SQL_FILE_NAME);
      createTableIfNotExists(KNOX_DESCRIPTORS_TABLE_NAME, KNOX_DESCRIPTORS_TABLE_CREATE_SQL_FILE_NAME);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void createTableIfNotExists(String tableName, String createSqlFileName) throws Exception {
    if (!JDBCUtils.isTableExists(tableName, dataSource)) {
      JDBCUtils.createTable(createSqlFileName, dataSource, this.getClass().getClassLoader());
    }
  }

  /**
   * @return all remote providers, including the deleted ones
   */
  public List<RemoteConfig> selectProviders() {
    return selectFrom(KNOX_PROVIDERS_TABLE_NAME);
  }

  /**
   * @return all remote descriptors, including the deleted one
   */
  public List<RemoteConfig> selectDescriptors() {
    return selectFrom(KNOX_DESCRIPTORS_TABLE_NAME);
  }

  private List<RemoteConfig> selectFrom(String tableName) {
    List<RemoteConfig> result = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(
                 "SELECT name, content, last_modified_time, deleted FROM " + tableName)) {
      try (ResultSet rs = statement.executeQuery()) {
        while(rs.next()) {
          result.add(new RemoteConfig(
                  rs.getString(1),
                  rs.getString(2),
                  rs.getTimestamp(3).toInstant(),
                  rs.getBoolean(4)
          ));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    return result;
  }

  /**
   * Save provider config to DB, overwrite if exists
   */
  public boolean putProvider(String name, String content) {
    return insert(name, content, KNOX_PROVIDERS_TABLE_NAME);
  }

  /**
   * Save descriptor config to DB, overwrite if exists
   */
  public boolean putDescriptor(String name, String content) {
    return insert(name, content, KNOX_DESCRIPTORS_TABLE_NAME);
  }

  private boolean insert(String name, String content, String tableName) {
    try {
      if (exists(name, tableName)) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE " + tableName + " SET content = ?, last_modified_time = ?, deleted = ? WHERE name = ?")) {
          statement.setString(1, content);
          statement.setTimestamp(2, Timestamp.from(Instant.now()));
          statement.setBoolean(3, false);
          statement.setString(4, name);
          return statement.executeUpdate() == 1;
        }
      } else {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO " + tableName + " (name, content, last_modified_time) VALUES(?,?,?)")) {
          statement.setString(1, name);
          statement.setString(2, content);
          statement.setTimestamp(3, Timestamp.from(Instant.now()));
          return statement.executeUpdate() == 1;
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean exists(String name, String tableName) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(
                 "SELECT name FROM " + tableName + " WHERE name = ?")) {
      statement.setString(1, name);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  /**
   * Logically delete a provider
   */
  public boolean deleteProvider(String name) {
    return deleteLogical(name, KNOX_PROVIDERS_TABLE_NAME);
  }

  /**
   * Logically delete a descriptor
   */
  public boolean deleteDescriptor(String name) {
    return deleteLogical(name, KNOX_DESCRIPTORS_TABLE_NAME);
  }

  private boolean deleteLogical(String name, String tableName) {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(
                 "UPDATE " + tableName + " SET deleted = ?, last_modified_time = ? WHERE name = ?")) {
      statement.setBoolean(1, true);
      statement.setTimestamp(2, Timestamp.from(Instant.now()));
      statement.setString(3, name);
      return statement.executeUpdate() == 1;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public int cleanTables(int olderThanSeconds) {
    Instant instant = Instant.now().minusSeconds(olderThanSeconds);
    return cleanTable(KNOX_PROVIDERS_TABLE_NAME, instant)
            + cleanTable(KNOX_DESCRIPTORS_TABLE_NAME, instant);
  }

  private int cleanTable(String tableName, Instant instant) {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM " + tableName + " WHERE deleted = ? AND last_modified_time <= ?")) {
      statement.setBoolean(1, true);
      statement.setTimestamp(2, Timestamp.from(instant));
      return statement.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}

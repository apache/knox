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
package org.apache.knox.gateway.services.knoxidf.trustedoidcissuer;

import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.database.KnoxDatabase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC helper for the {@code TRUSTED_OIDC_ISSUERS} table.
 * All SQL uses {@link PreparedStatement} with {@code ?} parameters only.
 * Uses {@link ResultSet#getBoolean(String)} for the {@code dynamic_jwks} column,
 * which correctly maps both BOOLEAN (standard/Derby) and NUMBER(1) (Oracle) values.
 */
class TrustedOidcIssuerDatabase extends KnoxDatabase {

  static final String TABLE_NAME = "TRUSTED_OIDC_ISSUERS";

  private static final String INSERT_SQL =
      "INSERT INTO " + TABLE_NAME + " (issuer_url, dynamic_jwks, cluster_name, registered_at, registered_by) VALUES (?, ?, ?, ?, ?)";
  private static final String DELETE_SQL =
      "DELETE FROM " + TABLE_NAME + " WHERE issuer_url = ?";
  private static final String SELECT_ALL_SQL =
      "SELECT issuer_url, dynamic_jwks, cluster_name, registered_at, registered_by FROM " + TABLE_NAME;

  TrustedOidcIssuerDatabase(DataSource dataSource, String dbType) throws Exception {
    super(dataSource);
    final DatabaseType databaseType = DatabaseType.fromString(dbType);
    createTableIfNotExists(TABLE_NAME, databaseType.trustedOidcIssuersTableSql());
  }

  void insert(TrustedOidcIssuer issuer) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {
      ps.setString(1, issuer.getIssuerUrl());
      ps.setBoolean(2, issuer.isDynamicJwks());
      ps.setString(3, issuer.getClusterName());
      ps.setTimestamp(4, Timestamp.from(issuer.getRegisteredAt()));
      ps.setString(5, issuer.getRegisteredBy());
      ps.executeUpdate();
    }
  }

  void delete(String issuerUrl) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement ps = connection.prepareStatement(DELETE_SQL)) {
      ps.setString(1, issuerUrl);
      ps.executeUpdate();
    }
  }

  List<TrustedOidcIssuer> selectAll() throws SQLException {
    final List<TrustedOidcIssuer> result = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
         PreparedStatement ps = connection.prepareStatement(SELECT_ALL_SQL);
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        result.add(new TrustedOidcIssuer(
            rs.getString("issuer_url"),
            rs.getBoolean("dynamic_jwks"),
            rs.getString("cluster_name"),
            rs.getTimestamp("registered_at").toInstant(),
            rs.getString("registered_by")
        ));
      }
    }
    return result;
  }
}

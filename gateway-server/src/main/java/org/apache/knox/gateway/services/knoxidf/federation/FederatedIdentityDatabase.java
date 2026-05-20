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
package org.apache.knox.gateway.services.knoxidf.federation;

import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.database.KnoxDatabase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Optional;

class FederatedIdentityDatabase extends KnoxDatabase {
    private static final String FEDERATED_IDENTITY_TABLE_NAME = "federated_identity";
    private static final String FEDERATED_IDENTITY_ATTRIBUTES_TABLE_NAME = "federated_identity_attr";
    private static final String ADD_FEDERATED_IDENTITY_SQL = "INSERT INTO " + FEDERATED_IDENTITY_TABLE_NAME
            + " (id, user_id, provider, external_subject, external_issuer, created_at) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String ADD_FEDERATED_IDENTITY_ATTR_SQL = "INSERT INTO " + FEDERATED_IDENTITY_ATTRIBUTES_TABLE_NAME +
            " (identity_id, attr_key, attr_value) VALUES (?, ?, ?)";
    private static final String FETCH_FEDERATED_IDENTITY_BY_PROV_ISS_SUB_SQL = "SELECT * FROM " + FEDERATED_IDENTITY_TABLE_NAME +
            " WHERE provider = ? AND external_issuer = ? AND external_subject = ?";
    private static final String FETCH_FEDERATED_IDENTITY_SQL_BY_ID = "SELECT id, user_id, provider, external_subject, external_issuer, created_at FROM "
            + FEDERATED_IDENTITY_TABLE_NAME + " WHERE id = ?";
    private static final String FETCH_FEDERATED_IDENTITY_ATTR_SQL = "SELECT attr_key, attr_value FROM " + FEDERATED_IDENTITY_ATTRIBUTES_TABLE_NAME + " WHERE identity_id = ?";

    FederatedIdentityDatabase(DataSource dataSource, String dbType) throws Exception {
        super(dataSource);
        DatabaseType databaseType = DatabaseType.fromString(dbType);
        createTableIfNotExists(FEDERATED_IDENTITY_TABLE_NAME, databaseType.federatedIdentityTableSql());
        createTableIfNotExists(FEDERATED_IDENTITY_ATTRIBUTES_TABLE_NAME, databaseType.federatedIdentityAttrTableSql());
    }

    void addFederatedIdentity(FederatedIdentity identity) throws SQLException {
        // save core metadata first
        try (Connection connection = dataSource.getConnection(); PreparedStatement addFederatedIdentityStatement = connection.prepareStatement(ADD_FEDERATED_IDENTITY_SQL)) {
            addFederatedIdentityStatement.setString(1, identity.getId());
            addFederatedIdentityStatement.setString(2, identity.getUserId());
            addFederatedIdentityStatement.setString(3, identity.getProvider());
            addFederatedIdentityStatement.setString(4, identity.getExternalSubject());
            addFederatedIdentityStatement.setString(5, identity.getExternalIssuer());
            addFederatedIdentityStatement.setTimestamp(6, Timestamp.from(identity.getCreatedAt()));
            addFederatedIdentityStatement.executeUpdate();
        }

        // save attributes
        try (Connection connection = dataSource.getConnection(); PreparedStatement addFederatedIdentityAttrStatement = connection.prepareStatement(ADD_FEDERATED_IDENTITY_ATTR_SQL)) {
            for (var attribute : identity.getAttributes().entrySet()) {
                addFederatedIdentityAttrStatement.setString(1, identity.getId());
                addFederatedIdentityAttrStatement.setString(2, attribute.getKey());
                addFederatedIdentityAttrStatement.setString(3, attribute.getValue());
                addFederatedIdentityAttrStatement.addBatch();
            }
            addFederatedIdentityAttrStatement.executeBatch();
        }
    }


    Optional<FederatedIdentity> findByProviderAndSubject(String provider, String issuer, String subject) throws SQLException {
        FederatedIdentity federatedIdentity = null;
        try (Connection connection = dataSource.getConnection(); PreparedStatement getFederatedIdentityStatement = connection.prepareStatement(FETCH_FEDERATED_IDENTITY_BY_PROV_ISS_SUB_SQL)) {
            getFederatedIdentityStatement.setString(1, provider);
            getFederatedIdentityStatement.setString(2, issuer);
            getFederatedIdentityStatement.setString(3, subject);
            try (ResultSet rs = getFederatedIdentityStatement.executeQuery()) {
                if (rs.next()) {
                    federatedIdentity = new FederatedIdentity(
                            rs.getString("id"),
                            rs.getString("user_id"),
                            provider,
                            subject,
                            issuer,
                            rs.getTimestamp("created_at").toInstant(), new HashMap<>());
                } else {
                    return Optional.empty();
                }
            }
        }
        populateAttributes(federatedIdentity);
        return Optional.of(federatedIdentity);
    }

    Optional<FederatedIdentity> findById(String id) throws SQLException {
        FederatedIdentity federatedIdentity = null;
        try (Connection connection = dataSource.getConnection(); PreparedStatement getFederatedIdentityStatement = connection.prepareStatement(FETCH_FEDERATED_IDENTITY_SQL_BY_ID)) {
            getFederatedIdentityStatement.setString(1, id);
            try (ResultSet rs = getFederatedIdentityStatement.executeQuery()) {
                if (rs.next()) {
                    federatedIdentity = new FederatedIdentity(
                            id,
                            rs.getString("user_id"),
                            rs.getString("provider"),
                            rs.getString("external_subject"),
                            rs.getString("external_issuer"),
                            rs.getTimestamp("created_at").toInstant(), new HashMap<>());
                } else {
                    return Optional.empty();
                }
            }
        }
        populateAttributes(federatedIdentity);
        return Optional.of(federatedIdentity);
    }

    private void populateAttributes(FederatedIdentity federatedIdentity) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement getFederatedIdentityAttrStatement = connection.prepareStatement(FETCH_FEDERATED_IDENTITY_ATTR_SQL)) {
            getFederatedIdentityAttrStatement.setString(1, federatedIdentity.getId());
            try (ResultSet rs = getFederatedIdentityAttrStatement.executeQuery()) {
                while (rs.next()) {
                    federatedIdentity.getAttributes().put(rs.getString(1), rs.getString(2));
                }
            }
        }
    }
}

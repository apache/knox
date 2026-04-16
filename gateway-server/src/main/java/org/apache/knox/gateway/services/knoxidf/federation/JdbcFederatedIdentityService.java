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

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.database.DataSourceProvider;
import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.database.JDBCUtils;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.token.impl.TokenStateDatabase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

//TODO: split this class into service + FederatedIdentityDatabase classes
public class JdbcFederatedIdentityService implements FederatedIdentityService {
    private static final String FEDERATED_IDENTITY_TABLE_NAME = "federated_identity";
    private static final String FEDERATED_IDENTITY_ATTRIBUTES_TABLE_NAME = "federated_identity_attr";

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Lock initLock = new ReentrantLock(true);
    private final DataSource dataSource;

    public JdbcFederatedIdentityService(GatewayConfig config, AliasService aliasService) throws Exception {
        this.dataSource = DataSourceProvider.getDataSource(config, aliasService);
    }

    private void ensureTablesExist(DatabaseType databaseType) {
        try {
            createTableIfNotExists(FEDERATED_IDENTITY_TABLE_NAME, databaseType.federatedIdentityTableSql());
            createTableIfNotExists(FEDERATED_IDENTITY_ATTRIBUTES_TABLE_NAME, databaseType.federatedIdentityAttrTableSql());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createTableIfNotExists(String tableName, String createSqlFileName) throws Exception {
        if (!JDBCUtils.tableExists(tableName, dataSource)) {
            JDBCUtils.createTableFromSQL(createSqlFileName, dataSource, TokenStateDatabase.class.getClassLoader());
        }
    }

    @Override
    public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
        if (!initialized.get()) {
            initLock.lock();
            try {
                ensureTablesExist(DatabaseType.fromString(config.getDatabaseType()));
            } finally {
                initLock.unlock();
            }
        }
    }

    @Override
    public void start() throws ServiceLifecycleException {
    }

    @Override
    public void stop() throws ServiceLifecycleException {
    }

    @Override
    public void addFederatedIdentity(FederatedIdentity identity) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO federated_identity " +
                            "(id, user_id, provider, external_subject, external_issuer, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?)")) {

                ps.setString(1, identity.getId());
                ps.setString(2, identity.getUserId());
                ps.setString(3, identity.getProvider());
                ps.setString(4, identity.getExternalSubject());
                ps.setString(5, identity.getExternalIssuer());
                ps.setTimestamp(6, Timestamp.from(identity.getCreatedAt()));
                ps.executeUpdate();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO federated_identity_attr " +
                            "(identity_id, attr_key, attr_value) VALUES (?, ?, ?)")) {

                for (var e : identity.getAttributes().entrySet()) {
                    ps.setString(1, identity.getId());
                    ps.setString(2, e.getKey());
                    ps.setString(3, e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<FederatedIdentity> findByProviderAndSubject(
            String provider,
            String issuer,
            String subject) {

        try (Connection c = dataSource.getConnection()) {

            FederatedIdentity federatedIdentity = null;

            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM federated_identity " +
                    "WHERE provider = ? AND external_issuer = ? AND external_subject = ?")) {
                ps.setString(1, provider);
                ps.setString(2, issuer);
                ps.setString(3, subject);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    federatedIdentity = new FederatedIdentity(
                            rs.getString("id"),
                            rs.getString("user_id"),
                            provider,
                            subject,
                            issuer,
                            rs.getTimestamp("created_at").toInstant(), new HashMap<>());
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT attr_key, attr_value FROM federated_identity_attr WHERE identity_id = ?")) {

                ps.setString(1, federatedIdentity.getId());

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        federatedIdentity.getAttributes().put(
                                rs.getString(1),
                                rs.getString(2));
                    }
                }
            }

            return Optional.of(federatedIdentity);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<FederatedIdentity> findById(String id) {

        try (Connection c = dataSource.getConnection()) {

            FederatedIdentity federatedIdentity;

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, user_id, provider, external_subject, external_issuer, created_at " +
                            "FROM federated_identity WHERE id = ?")) {

                ps.setString(1, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    federatedIdentity = new FederatedIdentity(
                            rs.getString("id"),
                            rs.getString("user_id"),
                            rs.getString("provider"),
                            rs.getString("external_subject"),
                            rs.getString("external_issuer"),
                            rs.getTimestamp("created_at").toInstant(),
                            new HashMap<>());
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT attr_key, attr_value FROM federated_identity_attr WHERE identity_id = ?")) {

                ps.setString(1, federatedIdentity.getId());

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        federatedIdentity.getAttributes().put(
                                rs.getString("attr_key"),
                                rs.getString("attr_value"));
                    }
                }
            }

            return Optional.of(federatedIdentity);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load federated identity by id", e);
        }
    }

}

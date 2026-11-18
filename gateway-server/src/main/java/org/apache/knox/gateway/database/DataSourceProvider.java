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
package org.apache.knox.gateway.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds the {@link DataSource} used by the JDBC-backed gateway services.
 * <p>
 * When {@link GatewayConfig#isDatabaseConnectionPoolEnabled()} is {@code true} (the default), the
 * database-specific {@link DataSource} is wrapped in a HikariCP connection pool. Callers with the same
 * database settings get the same pooled {@link DataSource}, so all services of a gateway share one pool.
 * A pool connects once when it is created, so an unreachable database fails fast with an
 * {@link SQLException}, as it did without pooling. Pools are closed by {@link #closeConnectionPools()}
 * when the gateway stops.
 */
public class DataSourceProvider {
    private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
    private static final String POOL_NAME_PREFIX = "KnoxDatabasePool-";

    private static final Map<String, HikariDataSource> CONNECTION_POOLS = new HashMap<>();
    private static final AtomicInteger POOL_COUNTER = new AtomicInteger();

    public static DataSource getDataSource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException, SQLException {
        if (!gatewayConfig.isDatabaseConnectionPoolEnabled()) {
            return createDataSource(gatewayConfig, aliasService);
        }

        synchronized (CONNECTION_POOLS) {
            final String poolKey = getPoolKey(gatewayConfig, aliasService);
            HikariDataSource pool = CONNECTION_POOLS.get(poolKey);
            if (pool == null || pool.isClosed()) {
                pool = createConnectionPool(gatewayConfig, createDataSource(gatewayConfig, aliasService), POOL_NAME_PREFIX + POOL_COUNTER.incrementAndGet());
                CONNECTION_POOLS.put(poolKey, pool);
            }
            return pool;
        }
    }

    /**
     * Closes all connection pools created by this provider.
     */
    public static void closeConnectionPools() {
        final List<HikariDataSource> pools;
        synchronized (CONNECTION_POOLS) {
            pools = new ArrayList<>(CONNECTION_POOLS.values());
            CONNECTION_POOLS.clear();
        }
        for (HikariDataSource pool : pools) {
            pool.close();
            LOG.closedDatabaseConnectionPool(pool.getPoolName());
        }
    }

    private static DataSource createDataSource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException, SQLException {
        DatabaseType dbType = DatabaseType.fromString(gatewayConfig.getDatabaseType());

        AbstractDataSourceFactory dsFactory = switch (dbType) {
            case POSTGRESQL -> new PostgresDataSourceFactory();
            case MYSQL -> new MysqlDataSourceFactory();
            case MARIADB -> new MariaDBDataSourceFactory();
            case H2 -> new H2DataSourceFactory();
            case ORACLE -> new OracleDataSourceFactory();
        };

        return dsFactory.createDataSource(gatewayConfig, aliasService);
    }

    static HikariDataSource createConnectionPool(GatewayConfig gatewayConfig, DataSource dataSource, String poolName) throws SQLException {
        final HikariConfig poolConfig = new HikariConfig();
        poolConfig.setPoolName(poolName);
        poolConfig.setDataSource(dataSource);
        poolConfig.setMaximumPoolSize(gatewayConfig.getDatabaseConnectionPoolMaxSize());
        if (gatewayConfig.getDatabaseConnectionPoolMinIdle() >= 0) {
            poolConfig.setMinimumIdle(gatewayConfig.getDatabaseConnectionPoolMinIdle());
        }
        poolConfig.setConnectionTimeout(gatewayConfig.getDatabaseConnectionPoolConnectionTimeout());
        poolConfig.setIdleTimeout(gatewayConfig.getDatabaseConnectionPoolIdleTimeout());
        poolConfig.setMaxLifetime(gatewayConfig.getDatabaseConnectionPoolMaxLifetime());
        // Connect once while building the pool and fail if that fails. Otherwise the pool keeps retrying
        // in the background and every caller blocks for the connection timeout, e.g. a knoxcli command
        // run while the gateway holds the embedded H2 database file. A failed pool is not cached.
        poolConfig.setInitializationFailTimeout(1);

        final HikariDataSource pool;
        try {
            pool = new HikariDataSource(poolConfig);
        } catch (HikariPool.PoolInitializationException e) {
            if (e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            }
            throw new SQLException("Cannot create database connection pool " + poolName + ": " + e.getMessage(), e);
        }
        LOG.createdDatabaseConnectionPool(poolName, gatewayConfig.getDatabaseType(), pool.getMaximumPoolSize());
        return pool;
    }

    /*
     * Covers every setting the data source factories read, so callers share a pool only when they
     * would otherwise have built identical data sources. Credentials are hashed, not kept in clear.
     */
    private static String getPoolKey(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException {
        final String credentials = String.join("\n",
                Objects.toString(getAlias(aliasService, AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME), ""),
                Objects.toString(getAlias(aliasService, AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME), ""),
                Objects.toString(getAlias(aliasService, AbstractDataSourceFactory.DATABASE_TRUSTSTORE_PASSWORD_ALIAS_NAME), ""),
                Objects.toString(gatewayConfig.isDatabaseH2EncryptionEnabled() ? getAlias(aliasService, gatewayConfig.getDatabaseH2EncryptionPassphraseAlias()) : null, ""));
        return String.join("\n",
                gatewayConfig.getDatabaseType(),
                Objects.toString(gatewayConfig.getDatabaseConnectionUrl(), ""),
                Objects.toString(gatewayConfig.getDatabaseHost(), ""),
                String.valueOf(gatewayConfig.getDatabasePort()),
                Objects.toString(gatewayConfig.getDatabaseName(), ""),
                String.valueOf(gatewayConfig.isDatabaseSslEnabled()),
                String.valueOf(gatewayConfig.verifyDatabaseSslServerCertificate()),
                Objects.toString(gatewayConfig.getDatabaseSslTruststoreFileName(), ""),
                Objects.toString(gatewayConfig.getDatabaseSslTruststoreType(), ""),
                String.valueOf(gatewayConfig.isDatabaseH2EncryptionEnabled()),
                DigestUtils.sha256Hex(credentials));
    }

    private static String getAlias(AliasService aliasService, String aliasName) throws AliasServiceException {
        final char[] value = aliasService.getPasswordFromAliasForGateway(aliasName);
        return value == null ? null : new String(value);
    }
}

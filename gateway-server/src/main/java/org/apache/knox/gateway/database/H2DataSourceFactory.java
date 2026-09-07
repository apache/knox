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

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Builds a {@link DataSource} for the H2 database. In the self-provisioning embedded case the
 * connection URL points at a local file base ({@code jdbc:h2:${securityDir}/h2db/knoxdb}) that
 * {@link EmbeddedH2Database} has configured via {@code gateway.database.name}; H2 creates the file
 * on first connection, so no server or explicit database-creation step is required. Within a single
 * JVM all Knox services connecting to the same URL share one embedded database instance.
 * <p>
 * When {@link GatewayConfig#isDatabaseH2EncryptionEnabled()} is {@code true}, at-rest encryption is
 * enabled by appending {@code ;CIPHER=AES} to the URL and supplying the connection password as
 * {@code "<filePassword> <userPassword>"} (H2 splits the password on the first space into the file
 * password and the user password). The file password is the operator-provisioned passphrase resolved
 * from the credential-store alias named by {@link GatewayConfig#getDatabaseH2EncryptionPassphraseAlias()};
 * initialization fails fast if that alias is unset (no silent unencrypted fallback).
 */
public class H2DataSourceFactory extends AbstractDataSourceFactory {

    private static final String ENCRYPTED_URL_POSTFIX = ";CIPHER=AES";

    @Override
    public DataSource createDataSource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException, SQLException {
        final JdbcDataSource dataSource = new JdbcDataSource();
        String url = "jdbc:h2:" + gatewayConfig.getDatabaseName();
        final String userPassword = getDatabasePassword(aliasService);

        dataSource.setUser(getDatabaseUser(aliasService));
        if (gatewayConfig.isDatabaseH2EncryptionEnabled()) {
            final String aliasName = gatewayConfig.getDatabaseH2EncryptionPassphraseAlias();
            final String filePassword = getDatabaseAlias(aliasService, aliasName);
            if (filePassword == null || filePassword.isEmpty()) {
                throw new SQLException("H2 at-rest encryption is enabled (" + aliasName + ") but no passphrase is stored under credential-store alias '"
                        + aliasName + "'. Provision the alias or disable gateway.database.h2.encryption.enabled.");
            }
            url += ENCRYPTED_URL_POSTFIX;
            // H2 splits the password on the first space: <filePassword> <userPassword>.
            dataSource.setPassword(filePassword + " " + (userPassword == null ? "" : userPassword));
        } else {
            dataSource.setPassword(userPassword);
        }

        dataSource.setUrl(url);
        return dataSource;
    }
}

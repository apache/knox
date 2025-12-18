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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.jdbc.SslMode;
import org.postgresql.ssl.NonValidatingFactory;

import javax.sql.DataSource;
import java.sql.SQLException;

public class PostgresDataSource extends AbstractDataSource {

    @Override
    public DataSource createDataSource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException, SQLException {
        final PGSimpleDataSource postgresDataSource = new PGSimpleDataSource();
        final String dbUser = getDatabaseUser(aliasService);
        final String dbPassword = getDatabasePassword(aliasService);
        if (gatewayConfig.getDatabaseConnectionUrl() != null) {
            postgresDataSource.setUrl(gatewayConfig.getDatabaseConnectionUrl());

            // avoid nullifying already configured user/password properties in case they
            // were already set in the given JDBC URL but not saved as aliases
            if (StringUtils.isNotBlank(dbUser)) {
                postgresDataSource.setUser(dbUser);
            }
            if (StringUtils.isNotBlank(dbPassword)) {
                postgresDataSource.setPassword(dbPassword);
            }
        } else {
            postgresDataSource.setDatabaseName(gatewayConfig.getDatabaseName());
            postgresDataSource.setServerNames(new String[]{gatewayConfig.getDatabaseHost()});
            postgresDataSource.setPortNumbers(new int[]{gatewayConfig.getDatabasePort()});
            postgresDataSource.setUser(dbUser);
            postgresDataSource.setPassword(dbPassword);
        }

        configureSsl(gatewayConfig, aliasService, postgresDataSource);

        return postgresDataSource;
    }

    private void configureSsl(GatewayConfig gatewayConfig, AliasService aliasService, PGSimpleDataSource postgresDataSource) throws AliasServiceException {
        if (gatewayConfig.isDatabaseSslEnabled()) {
            postgresDataSource.setSsl(true);
            postgresDataSource.setSslMode(SslMode.VERIFY_FULL.value);
            if (gatewayConfig.verifyDatabaseSslServerCertificate()) {
                postgresDataSource.setSslRootCert(gatewayConfig.getDatabaseSslTruststoreFileName());
                postgresDataSource.setSslPassword(getDatabaseAlias(aliasService, DATABASE_TRUSTSTORE_PASSWORD_ALIAS_NAME));
            } else {
                postgresDataSource.setSslfactory(NonValidatingFactory.class.getCanonicalName());
            }
        }
    }
}

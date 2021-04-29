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
package org.apache.knox.gateway.util;

import javax.sql.DataSource;

import org.apache.derby.jdbc.ClientDataSource;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.jdbc.SslMode;
import org.postgresql.ssl.NonValidatingFactory;

public class JDBCUtils {
  public static final String POSTGRESQL_DB_TYPE = "postgresql";
  public static final String DERBY_DB_TYPE = "derbydb";
  public static final String DATABASE_USER_ALIAS_NAME = "gateway_database_user";
  public static final String DATABASE_PASSWORD_ALIAS_NAME = "gateway_database_password";
  public static final String DATABASE_TRUSTSTORE_PASSWORD_ALIAS_NAME = "gateway_database_ssl_truststore_password";

  public static DataSource getDataSource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException {
    if (POSTGRESQL_DB_TYPE.equalsIgnoreCase(gatewayConfig.getDatabaseType())) {
      return createPostgresDataSource(gatewayConfig, aliasService);
    } else if (DERBY_DB_TYPE.equalsIgnoreCase(gatewayConfig.getDatabaseType())) {
      return createDerbyDatasource(gatewayConfig, aliasService);
    }
    throw new IllegalArgumentException("Invalid database type: " + gatewayConfig.getDatabaseType());
  }

  private static DataSource createPostgresDataSource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException {
    final PGSimpleDataSource postgresDataSource = new PGSimpleDataSource();
    postgresDataSource.setDatabaseName(gatewayConfig.getDatabaseName());
    postgresDataSource.setServerNames(new String[] { gatewayConfig.getDatabaseHost() });
    postgresDataSource.setPortNumbers(new int[] { gatewayConfig.getDatabasePort() });
    postgresDataSource.setUser(getDatabaseUser(aliasService));
    postgresDataSource.setPassword(getDatabasePassword(aliasService));
    configurePostgreSQLSsl(gatewayConfig, aliasService, postgresDataSource);
    return postgresDataSource;
  }

  private static void configurePostgreSQLSsl(GatewayConfig gatewayConfig, AliasService aliasService, PGSimpleDataSource postgresDataSource) throws AliasServiceException {
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

  private static DataSource createDerbyDatasource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException {
    final ClientDataSource derbyDatasource = new ClientDataSource();
    derbyDatasource.setDatabaseName(gatewayConfig.getDatabaseName());
    derbyDatasource.setServerName(gatewayConfig.getDatabaseHost());
    derbyDatasource.setPortNumber(gatewayConfig.getDatabasePort());
    derbyDatasource.setUser(getDatabaseUser(aliasService));
    derbyDatasource.setPassword(getDatabasePassword(aliasService));
    return derbyDatasource;
  }

  private static String getDatabaseUser(AliasService aliasService) throws AliasServiceException {
    return getDatabaseAlias(aliasService, DATABASE_USER_ALIAS_NAME);
  }

  private static String getDatabasePassword(AliasService aliasService) throws AliasServiceException {
    return getDatabaseAlias(aliasService, DATABASE_PASSWORD_ALIAS_NAME);
  }

  private static String getDatabaseAlias(AliasService aliasService, String aliasName) throws AliasServiceException {
    final char[] value = aliasService.getPasswordFromAliasForGateway(aliasName);
    return value == null ? null : new String(value);
  }

}

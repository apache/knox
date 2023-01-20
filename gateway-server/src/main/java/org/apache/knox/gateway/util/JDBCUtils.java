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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import javax.sql.DataSource;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.derby.jdbc.ClientDataSource;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.hsqldb.jdbc.JDBCDataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.jdbc.SslMode;
import org.postgresql.ssl.NonValidatingFactory;

import com.mysql.cj.conf.PropertyDefinitions;
import com.mysql.cj.jdbc.MysqlDataSource;

public class JDBCUtils {
  public static final String POSTGRESQL_DB_TYPE = "postgresql";
  public static final String MYSQL_DB_TYPE = "mysql";
  public static final String DERBY_DB_TYPE = "derbydb";
  public static final String HSQL = "hsql";
  public static final String DATABASE_USER_ALIAS_NAME = "gateway_database_user";
  public static final String DATABASE_PASSWORD_ALIAS_NAME = "gateway_database_password";
  public static final String DATABASE_TRUSTSTORE_PASSWORD_ALIAS_NAME = "gateway_database_ssl_truststore_password";

  public static DataSource getDataSource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException, SQLException {
    if (POSTGRESQL_DB_TYPE.equalsIgnoreCase(gatewayConfig.getDatabaseType())) {
      return createPostgresDataSource(gatewayConfig, aliasService);
    } else if (DERBY_DB_TYPE.equalsIgnoreCase(gatewayConfig.getDatabaseType())) {
      return createDerbyDatasource(gatewayConfig, aliasService);
    } else if (HSQL.equalsIgnoreCase(gatewayConfig.getDatabaseType())) {
      return createHsqlDatasource(gatewayConfig, aliasService);
    } else if (MYSQL_DB_TYPE.equalsIgnoreCase(gatewayConfig.getDatabaseType())) {
      return createMySqlDataSource(gatewayConfig, aliasService);
    }
    throw new IllegalArgumentException("Invalid database type: " + gatewayConfig.getDatabaseType());
  }

  private static DataSource createPostgresDataSource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException {
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
      postgresDataSource.setServerNames(new String[] { gatewayConfig.getDatabaseHost() });
      postgresDataSource.setPortNumbers(new int[] { gatewayConfig.getDatabasePort() });
      postgresDataSource.setUser(dbUser);
      postgresDataSource.setPassword(dbPassword);
    }

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


  private static DataSource createHsqlDatasource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException {
    JDBCDataSource hsqlDatasource = new JDBCDataSource();
    hsqlDatasource.setUrl(gatewayConfig.getDatabaseConnectionUrl());
    hsqlDatasource.setUser(getDatabaseUser(aliasService));
    hsqlDatasource.setPassword(getDatabasePassword(aliasService));
    return hsqlDatasource;
  }

  private static DataSource createMySqlDataSource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException, SQLException {
    MysqlDataSource dataSource = new MysqlDataSource();
    if (gatewayConfig.getDatabaseConnectionUrl() != null) {
      dataSource.setUrl(gatewayConfig.getDatabaseConnectionUrl());
    } else {
      dataSource.setDatabaseName(gatewayConfig.getDatabaseName());
      dataSource.setServerName(gatewayConfig.getDatabaseHost());
      dataSource.setPortNumber(gatewayConfig.getDatabasePort());
      dataSource.setUser(getDatabaseUser(aliasService));
      dataSource.setPassword(getDatabasePassword(aliasService));
      configureMysqlSsl(gatewayConfig, aliasService, dataSource);
    }
    return dataSource;
  }

  private static void configureMysqlSsl(GatewayConfig gatewayConfig, AliasService aliasService, MysqlDataSource dataSource) throws AliasServiceException, SQLException {
    if (gatewayConfig.isDatabaseSslEnabled()) {
      dataSource.setUseSSL(true);
      if (gatewayConfig.verifyDatabaseSslServerCertificate()) {
        dataSource.setSslMode(PropertyDefinitions.SslMode.VERIFY_CA.name());
        dataSource.setVerifyServerCertificate(true);
        dataSource.setTrustCertificateKeyStoreType("JKS");
        dataSource.setTrustCertificateKeyStoreUrl("file:"+ gatewayConfig.getDatabaseSslTruststoreFileName());
        dataSource.setTrustCertificateKeyStorePassword(getDatabaseAlias(aliasService, DATABASE_TRUSTSTORE_PASSWORD_ALIAS_NAME));
      } else {
        dataSource.setVerifyServerCertificate(false);
      }
    }
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

  public static boolean isTableExists(String tableName, DataSource dataSource) throws SQLException {
    boolean exists = false;
    try (Connection connection = dataSource.getConnection()) {
      final DatabaseMetaData dbMetadata = connection.getMetaData();
      final String tableNameToCheck = dbMetadata.storesUpperCaseIdentifiers() ? tableName : tableName.toLowerCase(Locale.ROOT);
      try (ResultSet tables = dbMetadata.getTables(connection.getCatalog(), null, tableNameToCheck, null)) {
        exists = tables.next();
      }
    }
    return exists;
  }

  public static void createTable(String createSqlFileName, DataSource dataSource, ClassLoader classLoader) throws Exception {
    final InputStream is = classLoader.getResourceAsStream(createSqlFileName);
    String createTableSql = IOUtils.toString(is, UTF_8);
    try (Connection connection = dataSource.getConnection(); Statement createTableStatment = connection.createStatement();) {
      createTableStatment.execute(createTableSql);
    }
  }
}

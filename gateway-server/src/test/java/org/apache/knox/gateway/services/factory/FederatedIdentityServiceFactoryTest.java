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
package org.apache.knox.gateway.services.factory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.knox.gateway.database.AbstractDataSourceFactory;
import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.federation.H2DBFederatedIdentityService;
import org.apache.knox.gateway.services.knoxidf.federation.JdbcFederatedIdentityService;
import org.apache.knox.gateway.services.security.AliasService;
import org.easymock.EasyMock;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class FederatedIdentityServiceFactoryTest extends ServiceFactoryTest {

  private final FederatedIdentityServiceFactory serviceFactory = new FederatedIdentityServiceFactory();

  @Test
  public void testBasics() throws Exception {
    initConfig();
    super.testBasics(serviceFactory, ServiceType.MASTER_SERVICE, ServiceType.KNOXIDF_FEDERATED_IDENTITY_SERVICE);
  }

  @Test
  public void shouldReturnH2DBServiceByDefault() throws Exception {
    // Embedded H2 is the zero-config OOTB default that replaced the retired Derby backend (KNOX-3401).
    Service service = null;
    try {
      initConfig(true, DatabaseType.H2);
      service = serviceFactory.create(gatewayServices, ServiceType.KNOXIDF_FEDERATED_IDENTITY_SERVICE, gatewayConfig, options, "");
      assertTrue(service instanceof H2DBFederatedIdentityService);
    } finally {
      if (service != null) {
        service.stop();
      }
    }
  }

  @Test
  public void shouldReturnJdbcServiceWhenExplicitlyConfigured() throws Exception {
    // When the operator explicitly names the JDBC implementation, the factory must honor it and
    // return a plain JdbcFederatedIdentityService rather than the embedded-H2 default. The backing
    // DataSource is an in-memory H2 here purely as test plumbing; selecting the real external
    // backend (postgresql/mysql/oracle) from gateway.database.type is covered by DataSourceProviderTest.
    final String memDb = "federation_factory_jdbc_test";
    Service service = null;
    final Connection keepAlive = DriverManager.getConnection("jdbc:h2:mem:" + memDb + ";DB_CLOSE_DELAY=-1");
    try {
      final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
      EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn(null).anyTimes();
      EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn(null).anyTimes();
      EasyMock.replay(aliasService);
      EasyMock.expect(gatewayServices.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService).anyTimes();
      EasyMock.replay(gatewayServices);
      EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
      EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn("mem:" + memDb + ";DB_CLOSE_DELAY=-1").anyTimes();
      EasyMock.replay(gatewayConfig);

      service = serviceFactory.create(gatewayServices, ServiceType.KNOXIDF_FEDERATED_IDENTITY_SERVICE, gatewayConfig, options,
          JdbcFederatedIdentityService.class.getName());
      assertTrue(service instanceof JdbcFederatedIdentityService);
      assertFalse("Explicit JDBC impl must not fall back to the embedded-H2 default", service instanceof H2DBFederatedIdentityService);
    } finally {
      if (service != null) {
        service.stop();
      }
      try (Statement stmt = keepAlive.createStatement()) {
        stmt.execute("DROP ALL OBJECTS");
      }
      keepAlive.close();
    }
  }
}

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
package org.apache.knox.gateway.services.factory;

import org.apache.knox.gateway.database.AbstractDataSourceFactory;
import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.flowstate.InMemoryAuthzFlowStateService;
import org.apache.knox.gateway.services.knoxidf.flowstate.JdbcAuthzFlowStateService;
import org.apache.knox.gateway.services.security.AliasService;
import org.easymock.EasyMock;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AuthzFlowStateServiceFactoryTest extends ServiceFactoryTest {

  private final AuthzFlowStateServiceFactory serviceFactory = new AuthzFlowStateServiceFactory();

  @Test
  public void testBasics() throws Exception {
    initConfig();
    super.testBasics(serviceFactory, ServiceType.MASTER_SERVICE, ServiceType.KNOXIDF_AUTHZ_FLOW_STATE_SERVICE);
  }

  @Test
  public void shouldReturnInMemoryServiceByDefault() throws Exception {
    initConfig();
    final Service service = serviceFactory.create(gatewayServices, ServiceType.KNOXIDF_AUTHZ_FLOW_STATE_SERVICE, gatewayConfig, options, "");
    assertTrue(service instanceof InMemoryAuthzFlowStateService);
  }

  @Test
  public void shouldReturnInMemoryServiceByDefaultWithEmbeddedDatabase() throws Exception {
    expectDatabase(DatabaseType.H2.type(), "mem:unused");
    final Service service = serviceFactory.create(gatewayServices, ServiceType.KNOXIDF_AUTHZ_FLOW_STATE_SERVICE, gatewayConfig, options, "");
    assertTrue(service instanceof InMemoryAuthzFlowStateService);
  }

  @Test
  public void shouldReturnInMemoryServiceWhenExplicitlyConfigured() throws Exception {
    initConfig();
    final Service service = serviceFactory.create(gatewayServices, ServiceType.KNOXIDF_AUTHZ_FLOW_STATE_SERVICE, gatewayConfig, options,
        InMemoryAuthzFlowStateService.class.getName());
    assertTrue(service instanceof InMemoryAuthzFlowStateService);
  }

  @Test
  public void shouldReturnJdbcServiceWhenExplicitlyConfigured() throws Exception {
    final String memDb = "authz_flow_state_factory_test";
    final Connection keepAlive = DriverManager.getConnection("jdbc:h2:mem:" + memDb + ";DB_CLOSE_DELAY=-1");
    Service service = null;
    try {
      expectDatabase(DatabaseType.H2.type(), "mem:" + memDb + ";DB_CLOSE_DELAY=-1");
      service = serviceFactory.create(gatewayServices, ServiceType.KNOXIDF_AUTHZ_FLOW_STATE_SERVICE, gatewayConfig, options,
          JdbcAuthzFlowStateService.class.getName());
      assertTrue(service instanceof JdbcAuthzFlowStateService);
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

  @Test
  public void shouldFailWhenExplicitJdbcServiceCannotStart() throws Exception {
    expectUnreachablePostgres();
    assertThrows(ServiceLifecycleException.class, () -> serviceFactory.create(gatewayServices,
        ServiceType.KNOXIDF_AUTHZ_FLOW_STATE_SERVICE, gatewayConfig, options, JdbcAuthzFlowStateService.class.getName()));
  }

  @Test
  public void shouldFallBackToInMemoryWhenDefaultJdbcServiceCannotStart() throws Exception {
    expectUnreachablePostgres();
    final Service service = serviceFactory.create(gatewayServices, ServiceType.KNOXIDF_AUTHZ_FLOW_STATE_SERVICE, gatewayConfig, options, "");
    assertTrue(service instanceof InMemoryAuthzFlowStateService);
  }

  private void expectUnreachablePostgres() throws Exception {
    // nothing listens on port 1, so the connection is refused right away
    EasyMock.expect(gatewayConfig.getDatabaseConnectionUrl()).andReturn("jdbc:postgresql://127.0.0.1:1/knox?connectTimeout=2").anyTimes();
    expectDatabase(DatabaseType.POSTGRESQL.type(), null);
  }

  private void expectDatabase(String databaseType, String databaseName) throws Exception {
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.replay(aliasService);
    EasyMock.expect(gatewayServices.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService).anyTimes();
    EasyMock.replay(gatewayServices);
    EasyMock.expect(gatewayConfig.getDatabaseType()).andReturn(databaseType).anyTimes();
    EasyMock.expect(gatewayConfig.getDatabaseName()).andReturn(databaseName).anyTimes();
    EasyMock.expect(gatewayConfig.getKnoxIDFAuthzFlowStateTtlSecs()).andReturn(300).anyTimes();
    EasyMock.replay(gatewayConfig);
  }
}

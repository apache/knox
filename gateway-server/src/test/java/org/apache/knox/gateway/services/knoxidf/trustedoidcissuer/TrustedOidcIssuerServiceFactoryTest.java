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
package org.apache.knox.gateway.services.knoxidf.trustedoidcissuer;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.database.AbstractDataSourceFactory;
import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.factory.TrustedOidcIssuerServiceFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TrustedOidcIssuerServiceFactoryTest {

  private static final String DB_NAME = "trustedissuers_factory_test";
  private static final String DERBY_CREATE_URL = "jdbc:derby:memory:" + DB_NAME + ";create=true";
  private static final String DERBY_SHUTDOWN_URL = "jdbc:derby:memory:" + DB_NAME + ";shutdown=true";

  @BeforeClass
  public static void setUpClass() throws SQLException {
    // Derby 10.14 does not recognize locales like en_001; force a standard locale.
    java.util.Locale.setDefault(java.util.Locale.US);
    DriverManager.getConnection(DERBY_CREATE_URL).close();
  }

  @AfterClass
  public static void tearDownClass() {
    try {
      DriverManager.getConnection(DERBY_SHUTDOWN_URL);
    } catch (SQLException e) {
      if (!(e.getErrorCode() == 45000 && "08006".equals(e.getSQLState()))) {
        throw new RuntimeException("Unexpected Derby shutdown error", e);
      }
    }
  }

  // ------------------------------------------------------------------
  // Empty (no KNOXIDF) cases
  // ------------------------------------------------------------------

  /** Zero topologies → no topology service returns anything → Empty. */
  @Test
  public void testNoTopologiesReturnsEmpty() throws Exception {
    assertIsEmpty(createFactory(), buildEmptyGatewayServices(), emptyConfig());
  }

  /** Topologies exist but none contain KNOXIDF or KNOXIDF_ADMIN → Empty. */
  @Test
  public void testTopologiesWithNonKnoxIdfRolesReturnsEmpty() throws Exception {
    final GatewayServices gws = buildGatewayServicesWithTopology(withRoles("HDFS", "WEBHDFS"), null);
    assertIsEmpty(createFactory(), gws, emptyConfig());
  }

  /** TopologyService is null (not yet registered) → Empty, no NPE. */
  @Test
  public void testNullTopologyServiceReturnsEmpty() throws Exception {
    final GatewayServices gws = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gws.getService(ServiceType.TOPOLOGY_SERVICE)).andReturn(null).anyTimes();
    EasyMock.replay(gws);
    assertIsEmpty(createFactory(), gws, emptyConfig());
  }

  // ------------------------------------------------------------------
  // JDBC cases
  // ------------------------------------------------------------------

  /** A single KNOXIDF topology → JDBC. */
  @Test
  public void testKnoxIdfTopologyReturnsJdbc() throws Exception {
    final GatewayServices gws = buildGatewayServicesWithTopology(withRoles("KNOXIDF"), derbyAlias());
    assertIsJdbc(createFactory(), gws, derbyConfig());
  }

  /** A single KNOXIDF_ADMIN-only topology (no KNOXIDF) → JDBC. */
  @Test
  public void testKnoxIdfAdminOnlyTopologyReturnsJdbc() throws Exception {
    final GatewayServices gws = buildGatewayServicesWithTopology(withRoles("KNOXIDF_ADMIN"), derbyAlias());
    assertIsJdbc(createFactory(), gws, derbyConfig());
  }

  /** Both KNOXIDF and KNOXIDF_ADMIN in the same topology → JDBC. */
  @Test
  public void testBothRolesInSameTopologyReturnsJdbc() throws Exception {
    final GatewayServices gws = buildGatewayServicesWithTopology(
        withRoles("KNOXIDF", "KNOXIDF_ADMIN"), derbyAlias());
    assertIsJdbc(createFactory(), gws, derbyConfig());
  }

  /** Multiple topologies; only the second has KNOXIDF → JDBC (verifies the loop continues). */
  @Test
  public void testMultipleTopologiesOneHasKnoxIdfReturnsJdbc() throws Exception {
    final AliasService alias = derbyAlias();
    final GatewayServices gws = buildGatewayServicesWithMultipleTopologies(
        withRoles("HDFS", "WEBHDFS"), withRoles("KNOXIDF"), alias);
    assertIsJdbc(createFactory(), gws, derbyConfig());
  }

  // ------------------------------------------------------------------
  // Error handling
  // ------------------------------------------------------------------

  /**
   * When JDBC service initialization fails (e.g. bad DB type), the factory must fall back
   * to EmptyTrustedOidcIssuerService rather than propagating the exception.
   */
  @Test
  public void testJdbcInitFailureFallsBackToEmpty() throws Exception {
    final GatewayServices gws = buildGatewayServicesWithTopology(withRoles("KNOXIDF"), derbyAlias());

    final GatewayConfig brokenConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(brokenConfig.getDatabaseType()).andReturn("invalid_db_type").anyTimes();
    EasyMock.expect(brokenConfig.getServiceParameter(EasyMock.anyString(), EasyMock.anyString()))
        .andReturn("").anyTimes();
    EasyMock.replay(brokenConfig);

    assertIsEmpty(createFactory(), gws, brokenConfig);
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private static TrustedOidcIssuerServiceFactory createFactory() {
    return new TrustedOidcIssuerServiceFactory();
  }

  private static void assertIsEmpty(TrustedOidcIssuerServiceFactory factory,
      GatewayServices gws, GatewayConfig config) throws Exception {
    final org.apache.knox.gateway.services.Service result =
        factory.create(gws, ServiceType.TRUSTED_OIDC_ISSUER_SERVICE, config, Map.of());
    assertNotNull(result);
    assertTrue("Expected EmptyTrustedOidcIssuerService but got " + result.getClass().getSimpleName(),
        result instanceof EmptyTrustedOidcIssuerService);
  }

  private static void assertIsJdbc(TrustedOidcIssuerServiceFactory factory,
      GatewayServices gws, GatewayConfig config) throws Exception {
    final org.apache.knox.gateway.services.Service result =
        factory.create(gws, ServiceType.TRUSTED_OIDC_ISSUER_SERVICE, config, Map.of());
    assertNotNull(result);
    assertTrue("Expected JdbcTrustedOidcIssuerService but got " + result.getClass().getSimpleName(),
        result instanceof JdbcTrustedOidcIssuerService);
  }

  /** GatewayServices with a TopologyService returning no topologies; no AliasService needed. */
  private static GatewayServices buildEmptyGatewayServices() {
    final TopologyService topologyService = EasyMock.createNiceMock(TopologyService.class);
    EasyMock.expect(topologyService.getTopologies()).andReturn(Collections.emptyList()).anyTimes();
    EasyMock.replay(topologyService);

    final GatewayServices gws = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gws.getService(ServiceType.TOPOLOGY_SERVICE))
        .andReturn(topologyService).anyTimes();
    EasyMock.replay(gws);
    return gws;
  }

  /**
   * Builds a {@link GatewayServices} mock with one topology that has the given service roles.
   * {@code alias} may be null when no JDBC init will be attempted.
   */
  private static GatewayServices buildGatewayServicesWithTopology(
      String[] roles, AliasService alias) throws Exception {
    final Topology topology = topologyWithRoles(roles);
    return buildGatewayServices(Collections.singletonList(topology), alias);
  }

  private static GatewayServices buildGatewayServicesWithMultipleTopologies(
      String[] roles1, String[] roles2, AliasService alias) throws Exception {
    return buildGatewayServices(
        Arrays.asList(topologyWithRoles(roles1), topologyWithRoles(roles2)), alias);
  }

  private static GatewayServices buildGatewayServices(
      java.util.List<Topology> topologies, AliasService alias) throws Exception {
    final TopologyService topologyService = EasyMock.createNiceMock(TopologyService.class);
    EasyMock.expect(topologyService.getTopologies()).andReturn(topologies).anyTimes();
    EasyMock.replay(topologyService);

    final GatewayServices gws = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gws.getService(ServiceType.TOPOLOGY_SERVICE))
        .andReturn(topologyService).anyTimes();
    if (alias != null) {
      EasyMock.expect(gws.getService(ServiceType.ALIAS_SERVICE))
          .andReturn(alias).anyTimes();
    }
    EasyMock.replay(gws);
    return gws;
  }

  private static Topology topologyWithRoles(String... roles) {
    final Topology topology = EasyMock.createNiceMock(Topology.class);
    final java.util.List<org.apache.knox.gateway.topology.Service> services = new java.util.ArrayList<>();
    for (String role : roles) {
      final org.apache.knox.gateway.topology.Service svc =
          EasyMock.createNiceMock(org.apache.knox.gateway.topology.Service.class);
      EasyMock.expect(svc.getRole()).andReturn(role).anyTimes();
      EasyMock.replay(svc);
      services.add(svc);
    }
    EasyMock.expect(topology.getServices()).andReturn(services).anyTimes();
    EasyMock.replay(topology);
    return topology;
  }

  private static String[] withRoles(String... roles) {
    return roles;
  }

  private static AliasService derbyAlias() throws Exception {
    final AliasService alias = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(alias.getPasswordFromAliasForGateway(
        AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.expect(alias.getPasswordFromAliasForGateway(
        AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME)).andReturn(null).anyTimes();
    EasyMock.replay(alias);
    return alias;
  }

  private static GatewayConfig derbyConfig() {
    final GatewayConfigImpl config = new GatewayConfigImpl();
    config.set(GatewayConfigImpl.GATEWAY_DATABASE_TYPE, DatabaseType.DERBY.type());
    config.set(GatewayConfigImpl.GATEWAY_DATABASE_NAME, "memory:" + DB_NAME);
    return config;
  }

  /** Config mock that returns empty string for getServiceParameter (required for impl detection). */
  private static GatewayConfig emptyConfig() {
    final GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getServiceParameter(EasyMock.anyString(), EasyMock.anyString()))
        .andReturn("").anyTimes();
    EasyMock.replay(config);
    return config;
  }
}

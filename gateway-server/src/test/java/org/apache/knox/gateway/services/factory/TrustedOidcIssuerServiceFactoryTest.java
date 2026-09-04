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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.EmptyTrustedOidcIssuerService;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.H2DBTrustedOidcIssuerService;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.JdbcTrustedOidcIssuerService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Test;

public class TrustedOidcIssuerServiceFactoryTest {

  private final TrustedOidcIssuerServiceFactory serviceFactory = new TrustedOidcIssuerServiceFactory();
  private final Map<String, String> options = new HashMap<>();
  private File tempDir;
  private Service createdService;

  @After
  public void tearDown() throws Exception {
    if (createdService != null) {
      createdService.stop();
    }
    if (tempDir != null) {
      FileUtils.forceDelete(tempDir);
    }
  }

  // ------------------------------------------------------------------
  // Auto-implementation selection
  // ------------------------------------------------------------------

  @Test
  public void shouldChooseH2WhenNoDatabaseConfigured() {
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.expect(config.getDatabaseType()).andReturn("none").anyTimes();
    EasyMock.replay(config);
    assertEquals(H2DBTrustedOidcIssuerService.class.getName(), serviceFactory.chooseAutoImplementation(config));
  }

  @Test
  public void shouldChooseJdbcWhenExternalDatabaseConfigured() {
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.expect(config.getDatabaseType()).andReturn(DatabaseType.POSTGRESQL.type()).anyTimes();
    EasyMock.replay(config);
    assertEquals(JdbcTrustedOidcIssuerService.class.getName(), serviceFactory.chooseAutoImplementation(config));
  }

  // ------------------------------------------------------------------
  // Empty (no KNOXIDF) cases
  // ------------------------------------------------------------------

  /** Zero topologies → KnoxIDF not deployed → Empty. */
  @Test
  public void shouldSelectEmptyWhenNoTopologies() throws Exception {
    final GatewayServices gws = servicesWithTopology(/* no topologies */);
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.replay(config);
    createdService = serviceFactory.create(gws, ServiceType.TRUSTED_OIDC_ISSUER_SERVICE, config, options, "");
    assertTrue(createdService instanceof EmptyTrustedOidcIssuerService);
  }

  /** Topologies exist but none contain KNOXIDF or KNOXIDF_ADMIN → Empty. */
  @Test
  public void shouldSelectEmptyWhenNoKnoxIdfRole() throws Exception {
    final GatewayServices gws = servicesWithTopology(topologyWithRole("KNOXSSO"));
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.replay(config);
    createdService = serviceFactory.create(gws, ServiceType.TRUSTED_OIDC_ISSUER_SERVICE, config, options, "");
    assertTrue(createdService instanceof EmptyTrustedOidcIssuerService);
  }

  /** An explicit Empty implementation is honored even when KnoxIDF is deployed. */
  @Test
  public void shouldHonorExplicitEmptyImplEvenWhenKnoxIdfIsDeployed() throws Exception {
    final GatewayServices gws = servicesWithTopology(topologyWithRole("KNOXIDF"));
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.replay(config);
    createdService = serviceFactory.create(gws, ServiceType.TRUSTED_OIDC_ISSUER_SERVICE, config, options,
        EmptyTrustedOidcIssuerService.class.getName());
    assertTrue(createdService instanceof EmptyTrustedOidcIssuerService);
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private Topology topologyWithRole(String role) {
    final Topology topology = new Topology();
    topology.setName("topology-" + role);
    final org.apache.knox.gateway.topology.Service service = new org.apache.knox.gateway.topology.Service();
    service.setRole(role);
    topology.addService(service);
    return topology;
  }

  private GatewayServices servicesWithTopology(Topology... topologies) {
    final TopologyService topologyService = EasyMock.createNiceMock(TopologyService.class);
    EasyMock.expect(topologyService.getTopologies()).andReturn(Arrays.asList(topologies)).anyTimes();
    EasyMock.replay(topologyService);
    final GatewayServices gws = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gws.getService(ServiceType.TOPOLOGY_SERVICE)).andReturn(topologyService).anyTimes();
    EasyMock.replay(gws);
    return gws;
  }
}

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.federation.EmptyFederatedIdentityService;
import org.apache.knox.gateway.services.knoxidf.federation.H2DBFederatedIdentityService;
import org.apache.knox.gateway.services.knoxidf.federation.JdbcFederatedIdentityService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.test.TestUtils;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Test;

public class FederatedIdentityServiceFactoryTest {

  private final FederatedIdentityServiceFactory serviceFactory = new FederatedIdentityServiceFactory();
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

  @Test
  public void shouldChooseH2WhenNoDatabaseConfigured() {
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.expect(config.getDatabaseType()).andReturn("none").anyTimes();
    EasyMock.replay(config);
    assertEquals(H2DBFederatedIdentityService.class.getName(), serviceFactory.chooseAutoImplementation(config));
  }

  @Test
  public void shouldChooseJdbcWhenExternalDatabaseConfigured() {
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.expect(config.getDatabaseType()).andReturn(DatabaseType.POSTGRESQL.type()).anyTimes();
    EasyMock.replay(config);
    assertEquals(JdbcFederatedIdentityService.class.getName(), serviceFactory.chooseAutoImplementation(config));
  }

  @Test
  public void shouldDetectKnoxIdfFromInMemoryTopology() {
    final GatewayServices gatewayServices = servicesWithTopology(topologyWithRole("KNOXIDF"));
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.replay(config);
    assertTrue(serviceFactory.isKnoxIdfEnabledInAnyTopology(gatewayServices, config));
  }

  @Test
  public void shouldDetectKnoxIdfAdminFromInMemoryTopology() {
    final GatewayServices gatewayServices = servicesWithTopology(topologyWithRole("KNOXIDF_ADMIN"));
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.replay(config);
    assertTrue(serviceFactory.isKnoxIdfEnabledInAnyTopology(gatewayServices, config));
  }

  @Test
  public void shouldDetectKnoxIdfViaDiskScanWhenNoTopologiesLoadedYet() throws IOException {
    // Mirrors the real init-time timing: the topology monitor has not loaded topologies yet, so the
    // in-memory list is empty, but the topology XML already exists on disk.
    tempDir = TestUtils.createTempDir(this.getClass().getName());
    writeTopologyFile("knoxidf-sso.xml", "<topology><service><role>KNOXIDF</role></service></topology>");

    final GatewayServices gatewayServices = servicesWithTopology(/* no in-memory topologies */);
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.expect(config.getGatewayTopologyDir()).andReturn(tempDir.getAbsolutePath()).anyTimes();
    EasyMock.replay(config);

    assertTrue(serviceFactory.isKnoxIdfEnabledInAnyTopology(gatewayServices, config));
  }

  @Test
  public void shouldNotDetectKnoxIdfWhenAbsentFromMemoryAndDisk() throws IOException {
    tempDir = TestUtils.createTempDir(this.getClass().getName());
    writeTopologyFile("sandbox.xml", "<topology><service><role>KNOXSSO</role></service></topology>");

    final GatewayServices gatewayServices = servicesWithTopology(topologyWithRole("KNOXSSO"));
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.expect(config.getGatewayTopologyDir()).andReturn(tempDir.getAbsolutePath()).anyTimes();
    EasyMock.replay(config);

    assertFalse(serviceFactory.isKnoxIdfEnabledInAnyTopology(gatewayServices, config));
  }

  @Test
  public void shouldHonorExplicitEmptyImplEvenWhenKnoxIdfIsDeployed() throws Exception {
    final GatewayServices gatewayServices = servicesWithTopology(topologyWithRole("KNOXIDF"));
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.replay(config);

    createdService = serviceFactory.create(gatewayServices, ServiceType.KNOXIDF_FEDERATED_IDENTITY_SERVICE, config, options,
        EmptyFederatedIdentityService.class.getName());
    assertTrue(createdService instanceof EmptyFederatedIdentityService);
  }

  @Test
  public void shouldSelectEmptyWhenKnoxIdfNotDeployed() throws Exception {
    final GatewayServices gatewayServices = servicesWithTopology(/* no topologies */);
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    // No topology dir and no in-memory topology -> KnoxIDF not enabled -> Empty.
    EasyMock.replay(config);

    createdService = serviceFactory.create(gatewayServices, ServiceType.KNOXIDF_FEDERATED_IDENTITY_SERVICE, config, options, "");
    assertTrue(createdService instanceof EmptyFederatedIdentityService);
  }

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
    final GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gatewayServices.getService(ServiceType.TOPOLOGY_SERVICE)).andReturn(topologyService).anyTimes();
    EasyMock.replay(gatewayServices);
    return gatewayServices;
  }

  private void writeTopologyFile(String name, String content) throws IOException {
    Files.write(Paths.get(tempDir.getAbsolutePath(), name), content.getBytes(StandardCharsets.UTF_8));
  }
}

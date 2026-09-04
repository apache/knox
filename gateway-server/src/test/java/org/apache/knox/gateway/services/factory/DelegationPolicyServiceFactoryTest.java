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

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.delegation.EmptyDelegationPolicyService;
import org.apache.knox.gateway.services.knoxidf.delegation.H2DBDelegationPolicyService;
import org.apache.knox.gateway.services.knoxidf.delegation.JdbcDelegationPolicyService;
import org.apache.knox.gateway.services.knoxidf.delegation.PolicyCheckRequest;
import org.apache.knox.gateway.services.knoxidf.delegation.PolicyDecision;
import org.apache.knox.gateway.services.knoxidf.delegation.DelegationPolicyService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DelegationPolicyServiceFactoryTest {

  private final DelegationPolicyServiceFactory factory = new DelegationPolicyServiceFactory();
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
  // Empty (no KNOXIDF) cases
  // ------------------------------------------------------------------

  @Test
  public void shouldSelectEmptyWhenNoTopologies() throws Exception {
    final GatewayServices gws = servicesWithTopology(/* no topologies */);
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.replay(config);
    createdService = factory.create(gws, ServiceType.DELEGATION_POLICY_SERVICE, config, options, "");
    assertTrue(createdService instanceof EmptyDelegationPolicyService);
  }

  @Test
  public void shouldSelectEmptyWhenNoKnoxIdfRole() throws Exception {
    final GatewayServices gws = servicesWithTopology(topologyWithRole("KNOXSSO"));
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.replay(config);
    createdService = factory.create(gws, ServiceType.DELEGATION_POLICY_SERVICE, config, options, "");
    assertTrue(createdService instanceof EmptyDelegationPolicyService);
  }

  @Test
  public void shouldHonorExplicitEmptyImplEvenWhenKnoxIdfIsDeployed() throws Exception {
    final GatewayServices gws = servicesWithTopology(topologyWithRole("KNOXIDF"));
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.replay(config);
    createdService = factory.create(gws, ServiceType.DELEGATION_POLICY_SERVICE, config, options,
        EmptyDelegationPolicyService.class.getName());
    assertTrue(createdService instanceof EmptyDelegationPolicyService);
  }

  // ------------------------------------------------------------------
  // Auto-implementation selection
  // ------------------------------------------------------------------

  @Test
  public void shouldChooseH2WhenNoDatabaseConfigured() {
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.expect(config.getDatabaseType()).andReturn("none").anyTimes();
    EasyMock.replay(config);
    assertEquals(H2DBDelegationPolicyService.class.getName(), factory.chooseAutoImplementation(config));
  }

  @Test
  public void shouldChooseJdbcWhenExternalDatabaseConfigured() {
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.expect(config.getDatabaseType()).andReturn(DatabaseType.POSTGRESQL.type()).anyTimes();
    EasyMock.replay(config);
    assertEquals(JdbcDelegationPolicyService.class.getName(), factory.chooseAutoImplementation(config));
  }

  // ------------------------------------------------------------------
  // EmptyDelegationPolicyService behavior
  // ------------------------------------------------------------------

  @Test
  public void emptyServiceEvaluateReturnsServiceNotConfigured() throws Exception {
    final GatewayServices gws = servicesWithTopology(/* no topologies */);
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.replay(config);
    createdService = factory.create(gws, ServiceType.DELEGATION_POLICY_SERVICE, config, options, "");

    final DelegationPolicyService svc = (DelegationPolicyService) createdService;
    final PolicyDecision decision = svc.evaluate(
        new PolicyCheckRequest("oidc", "actor-id", "alice", "/api", Collections.singleton("read"), false));
    assertEquals("service_not_configured", decision.getDenyReason());
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

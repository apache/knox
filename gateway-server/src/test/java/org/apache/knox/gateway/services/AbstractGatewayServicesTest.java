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

package org.apache.knox.gateway.services;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createMockBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.knox.gateway.config.GatewayConfig;
import org.junit.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class AbstractGatewayServicesTest {


  @Test
  public void testAddStartAndStop() throws ServiceLifecycleException {
    AbstractGatewayServices gatewayServices = createMockBuilder(AbstractGatewayServices.class)
        .withConstructor("role", "name")
        .createMock();

    Map<ServiceType, Service> serviceMap = new EnumMap<>(ServiceType.class);
    TestService lastService = null;

    ServiceType[] orderedServiceTypes = {
        ServiceType.MASTER_SERVICE,
        ServiceType.KEYSTORE_SERVICE,
        ServiceType.ALIAS_SERVICE,
        ServiceType.SSL_SERVICE,
        ServiceType.TOKEN_STATE_SERVICE,
        ServiceType.TOKEN_SERVICE,
        ServiceType.SERVER_INFO_SERVICE,
        ServiceType.REMOTE_REGISTRY_CLIENT_SERVICE,
        ServiceType.CLUSTER_CONFIGURATION_MONITOR_SERVICE,
        ServiceType.TOPOLOGY_SERVICE,
        ServiceType.METRICS_SERVICE,
        ServiceType.CRYPTO_SERVICE,
        ServiceType.HOST_MAPPING_SERVICE,
        ServiceType.SERVICE_DEFINITION_REGISTRY,
        ServiceType.SERVICE_REGISTRY_SERVICE,
        ServiceType.CONCURRENT_SESSION_VERIFIER
    };

    assertNotEquals(ServiceType.values(), orderedServiceTypes);

    // Services should be started in the order they were added.. and stopped in the reverse order.
    // For testing let's use an order different than what is defined in the ServiceType enum.
    for (ServiceType serviceType : orderedServiceTypes) {
      TestService testService = new TestService();
      testService.setPreviousService(lastService);

      if (lastService != null) {
        lastService.setNextService(testService);
      }

      lastService = testService;
      serviceMap.put(serviceType, testService);

      gatewayServices.addService(serviceType, serviceMap.get(serviceType));
    }

    gatewayServices.start();
    gatewayServices.stop();

    assertEquals(EnumSet.allOf(ServiceType.class), gatewayServices.getServiceTypes());
  }

  @Test
  public void testGetRoleAndGetName() {
    AbstractGatewayServices gatewayServices = createMockBuilder(AbstractGatewayServices.class)
        .withConstructor("role", "name")
        .createMock();

    assertEquals("role", gatewayServices.getRole());
    assertEquals("name", gatewayServices.getName());
  }

  @Test
  public void testGetServiceAndGetServiceTypes() {
    AbstractGatewayServices gatewayServices = createMockBuilder(AbstractGatewayServices.class)
        .withConstructor("role", "name")
        .createMock();

    Set<ServiceType> expected = EnumSet.of(ServiceType.ALIAS_SERVICE, ServiceType.CRYPTO_SERVICE, ServiceType.MASTER_SERVICE);

    for (ServiceType serviceType : expected) {
      gatewayServices.addService(serviceType, createMock(Service.class));
    }

    assertEquals(expected, gatewayServices.getServiceTypes());
    assertNotEquals(EnumSet.allOf(ServiceType.class), gatewayServices.getServiceTypes());

    for (ServiceType serviceType : ServiceType.values()) {
      if (expected.contains(serviceType)) {
        assertNotNull(gatewayServices.getService(serviceType));
      } else {
        assertNull(gatewayServices.getService(serviceType));
      }
    }
  }

  private class TestService implements Service {
    private TestService previousService;
    private TestService nextService;
    private boolean started;

    @Override
    public void init(GatewayConfig config, Map<String, String> options) {
      assertFalse(started);
      if (previousService != null) {
        assertFalse(previousService.isStarted());
      }
      if (nextService != null) {
        assertFalse(nextService.isStarted());
      }
    }

    @Override
    public void start() {
      assertFalse(started);
      if (previousService != null) {
        assertTrue(previousService.isStarted());
      }
      if (nextService != null) {
        assertFalse(nextService.isStarted());
      }
      started = true;
    }

    @Override
    public void stop() {
      assertTrue(started);
      if (previousService != null) {
        assertTrue(previousService.isStarted());
      }
      if (nextService != null) {
        assertFalse(nextService.isStarted());
      }
      started = false;
    }

    public boolean isStarted() {
      return started;
    }

    void setPreviousService(TestService previousService) {
      this.previousService = previousService;
    }

    void setNextService(TestService nextService) {
      this.nextService = nextService;
    }
  }
}
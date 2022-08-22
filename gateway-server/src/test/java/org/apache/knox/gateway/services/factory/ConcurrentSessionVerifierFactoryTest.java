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

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.session.control.ConcurrentSessionVerifier;
import org.apache.knox.gateway.session.control.EmptyConcurrentSessionVerifier;
import org.apache.knox.gateway.session.control.InMemoryConcurrentSessionVerifier;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class ConcurrentSessionVerifierFactoryTest extends ServiceFactoryTest {
  private final ConcurrentSessionVerifierFactory serviceFactory = new ConcurrentSessionVerifierFactory();

  @Before
  public void setUp() throws Exception {
    initConfig();
  }

  @Test
  public void testBasics() throws Exception {
    super.testBasics(serviceFactory, ServiceType.MASTER_SERVICE, ServiceType.CONCURRENT_SESSION_VERIFIER);
  }

  @Test
  public void testShouldReturnEmptyConcurrentSessionVerifier() throws Exception {
    GatewayConfig configForVerifier = mockConfig(Collections.emptySet(), Collections.emptySet());

    ConcurrentSessionVerifier concurrentSessionVerifier = (ConcurrentSessionVerifier) serviceFactory.create(gatewayServices, ServiceType.CONCURRENT_SESSION_VERIFIER, configForVerifier, null, "");
    assertTrue(concurrentSessionVerifier instanceof EmptyConcurrentSessionVerifier);
    concurrentSessionVerifier = (ConcurrentSessionVerifier) serviceFactory.create(gatewayServices, ServiceType.CONCURRENT_SESSION_VERIFIER, configForVerifier, null, EmptyConcurrentSessionVerifier.class.getName());
    assertTrue(concurrentSessionVerifier instanceof EmptyConcurrentSessionVerifier);
  }

  @Test
  public void testShouldReturnInMemoryConcurrentSessionVerifier() throws Exception {
    GatewayConfig configForVerifier = mockConfig(new HashSet<>(Arrays.asList("admin")), Collections.emptySet());

    ConcurrentSessionVerifier concurrentSessionVerifier = (ConcurrentSessionVerifier) serviceFactory.create(gatewayServices, ServiceType.CONCURRENT_SESSION_VERIFIER, configForVerifier, null, InMemoryConcurrentSessionVerifier.class.getName());
    assertTrue(concurrentSessionVerifier instanceof InMemoryConcurrentSessionVerifier);

    configForVerifier = mockConfig(Collections.emptySet(), new HashSet<>(Arrays.asList("tom")));

    concurrentSessionVerifier = (ConcurrentSessionVerifier) serviceFactory.create(gatewayServices, ServiceType.CONCURRENT_SESSION_VERIFIER, configForVerifier, null, InMemoryConcurrentSessionVerifier.class.getName());
    assertTrue(concurrentSessionVerifier instanceof InMemoryConcurrentSessionVerifier);
  }

  @Test
  public void testShouldThrowException() {
    GatewayConfig configForVerifier = mockConfig(Collections.emptySet(), Collections.emptySet());

    assertThrows(ServiceLifecycleException.class, () -> {
      serviceFactory.create(gatewayServices, ServiceType.CONCURRENT_SESSION_VERIFIER, configForVerifier, null, InMemoryConcurrentSessionVerifier.class.getName());
    });
  }

  private GatewayConfig mockConfig(Set<String> privilegedUsers, Set<String> nonPrivilegedUsers) {
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getPrivilegedUsers()).andReturn(privilegedUsers).anyTimes();
    EasyMock.expect(config.getNonPrivilegedUsers()).andReturn(nonPrivilegedUsers).anyTimes();
    EasyMock.replay(config);
    return config;
  }

}

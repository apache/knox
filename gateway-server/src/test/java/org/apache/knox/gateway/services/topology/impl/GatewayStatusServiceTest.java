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
package org.apache.knox.gateway.services.topology.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;

import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.junit.Test;

public class GatewayStatusServiceTest {

  @Test
  public void testReadyStatus() throws Exception {
    GatewayStatusService statusService = new GatewayStatusService();
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    statusService.init(config, null);
    assertFalse(statusService.status());
    EasyMock.expect(config.getHealthCheckTopologies()).andReturn(new HashSet<>(Arrays.asList("t1", "t2"))).anyTimes();
    EasyMock.replay(config);
    statusService.initTopologiesToCheck();
    statusService.onTopologyReady("t1");
    assertFalse(statusService.status());
    statusService.onTopologyReady("t2");
    assertTrue(statusService.status());
  }
}
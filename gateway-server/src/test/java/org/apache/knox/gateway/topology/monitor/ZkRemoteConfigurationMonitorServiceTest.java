/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.monitor;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.easymock.EasyMock;
import org.junit.Test;

public class ZkRemoteConfigurationMonitorServiceTest {
  @Test(expected=IllegalStateException.class)
  public void testInitWithoutRequiredConfig() {
    GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getGatewayProvidersConfigDir()).andReturn("./shared-providers").anyTimes();
    EasyMock.expect(gatewayConfig.getGatewayDescriptorsDir()).andReturn("./descriptors").anyTimes();
    EasyMock.replay(gatewayConfig);

    RemoteConfigurationRegistryClientService remoteConfigurationRegistryClientService =
        EasyMock.createNiceMock(RemoteConfigurationRegistryClientService.class);

    new ZkRemoteConfigurationMonitorService(gatewayConfig, remoteConfigurationRegistryClientService);
  }
}

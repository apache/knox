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

import static org.junit.Assert.assertTrue;

import org.apache.knox.gateway.services.DefaultServerInfoService;
import org.apache.knox.gateway.services.ServerInfoService;
import org.apache.knox.gateway.services.ServiceType;
import org.junit.Before;
import org.junit.Test;

public class ServerInfoServiceFactoryTest extends ServiceFactoryTest {

  private final ServerInfoServiceFactory serviceFactory = new ServerInfoServiceFactory();

  @Before
  public void setUp() throws Exception {
    initConfig();
  }

  @Test
  public void testBasics() throws Exception {
    super.testBasics(serviceFactory, ServiceType.MASTER_SERVICE, ServiceType.SERVER_INFO_SERVICE);
  }

  @Test
  public void shouldReturnDefaultServerInfoService() throws Exception {
    final ServerInfoService serverInfoService = (ServerInfoService) serviceFactory.create(gatewayServices, ServiceType.SERVER_INFO_SERVICE, gatewayConfig, options);
    assertTrue(serverInfoService instanceof DefaultServerInfoService);
  }

}

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

import static org.apache.knox.gateway.services.ServiceType.ALIAS_SERVICE;
import static org.junit.Assert.assertTrue;

import org.apache.knox.gateway.backend.hashicorp.vault.HashicorpVaultAliasService;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.impl.DefaultAliasService;
import org.apache.knox.gateway.services.security.impl.RemoteAliasService;
import org.apache.knox.gateway.services.security.impl.ZookeeperRemoteAliasService;
import org.junit.Before;
import org.junit.Test;

public class AliasServiceFactoryTest extends ServiceFactoryTest {

  private final AliasServiceFactory serviceFactory = new AliasServiceFactory();

  @Before
  public void setUp() throws Exception {
    initConfig();
  }

  @Test
  public void testBasics() throws Exception {
    super.testBasics(serviceFactory, ServiceType.MASTER_SERVICE, ServiceType.ALIAS_SERVICE);
  }

  @Test
  public void shouldReturnDefaultAliasService() throws Exception {
    AliasService aliasService = (AliasService) serviceFactory.create(gatewayServices, ServiceType.ALIAS_SERVICE, gatewayConfig, options, DefaultAliasService.class.getName());
    assertTrue(aliasService instanceof DefaultAliasService);
    assertTrue(isMasterServiceSet(aliasService));
    assertTrue(isKeystoreServiceSet(aliasService));

    aliasService = (AliasService) serviceFactory.create(gatewayServices, ServiceType.ALIAS_SERVICE, gatewayConfig, options, "");
    assertTrue(aliasService instanceof DefaultAliasService);
    assertTrue(isMasterServiceSet(aliasService));
    assertTrue(isKeystoreServiceSet(aliasService));
  }

  @Test
  public void shouldReturnHashicorpVaultAliasService() throws Exception {
    assertTrue(serviceFactory.create(gatewayServices, ALIAS_SERVICE, gatewayConfig, options, HashicorpVaultAliasService.class.getName()) instanceof HashicorpVaultAliasService);
  }

  @Test
  public void shouldReturnRemoteAliasService() throws Exception {
    assertTrue(serviceFactory.create(gatewayServices, ALIAS_SERVICE, gatewayConfig, options, RemoteAliasService.class.getName()) instanceof RemoteAliasService);
  }

  @Test
  public void shouldReturnZookeeperAliasService() throws Exception {
    assertTrue(serviceFactory.create(gatewayServices, ALIAS_SERVICE, gatewayConfig, options, ZookeeperRemoteAliasService.class.getName()) instanceof ZookeeperRemoteAliasService);
  }

}

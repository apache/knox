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

import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.token.impl.AliasBasedTokenStateService;
import org.apache.knox.gateway.services.token.impl.DefaultTokenStateService;
import org.apache.knox.gateway.services.token.impl.DerbyDBTokenStateService;
import org.apache.knox.gateway.services.token.impl.JournalBasedTokenStateService;
import org.apache.knox.gateway.services.token.impl.ZookeeperTokenStateService;
import org.junit.Test;

public class TokenStateServiceFactoryTest extends ServiceFactoryTest {

  private final TokenStateServiceFactory serviceFactory = new TokenStateServiceFactory();

  @Test
  public void testBasics() throws Exception {
    initConfig();
    super.testBasics(serviceFactory, ServiceType.MASTER_SERVICE, ServiceType.TOKEN_STATE_SERVICE);
  }

  @Test
  public void shouldReturnDefaultTokenStateService() throws Exception {
    initConfig();
    TokenStateService tokenStateService = (TokenStateService) serviceFactory.create(gatewayServices, ServiceType.TOKEN_STATE_SERVICE, gatewayConfig, options,
        DefaultTokenStateService.class.getName());
    assertTrue(tokenStateService instanceof DefaultTokenStateService);
  }

  @Test
  public void shouldReturnDerbyDBTokenStateServiceByDefault() throws Exception {
    TokenStateService tokenStateService = null;
    try {
      initConfig(true);
      tokenStateService = (TokenStateService) serviceFactory.create(gatewayServices, ServiceType.TOKEN_STATE_SERVICE, gatewayConfig, options, "");
      assertTrue(tokenStateService instanceof DerbyDBTokenStateService);
    } finally {
      if (tokenStateService != null) {
        tokenStateService.stop();
      }
    }
  }

  @Test
  public void shouldReturnAliasBasedTokenStateService() throws Exception {
    initConfig();
    final TokenStateService tokenStateService = (TokenStateService) serviceFactory.create(gatewayServices, ServiceType.TOKEN_STATE_SERVICE, gatewayConfig,
        options, AliasBasedTokenStateService.class.getName());
    assertTrue(tokenStateService instanceof AliasBasedTokenStateService);
    assertTrue(isAliasServiceSet(tokenStateService));
  }

  @Test
  public void shouldReturnJournalTokenStateService() throws Exception {
    initConfig();
    assertTrue(serviceFactory.create(gatewayServices, ServiceType.TOKEN_STATE_SERVICE, gatewayConfig, options,
        JournalBasedTokenStateService.class.getName()) instanceof JournalBasedTokenStateService);
  }

  @Test
  public void shouldReturnZookeeperTokenStateService() throws Exception {
    initConfig();
    assertTrue(serviceFactory.create(gatewayServices, ServiceType.TOKEN_STATE_SERVICE, gatewayConfig, options,
        ZookeeperTokenStateService.class.getName()) instanceof ZookeeperTokenStateService);
  }

  @Test
  public void shouldReturnDerbyDatabaseTokenStateService() throws Exception {
    TokenStateService tokenStateService = null;
    try {
      initConfig(true);
      tokenStateService = (TokenStateService) serviceFactory.create(gatewayServices, ServiceType.TOKEN_STATE_SERVICE, gatewayConfig, options,
          DerbyDBTokenStateService.class.getName());
      assertTrue(tokenStateService instanceof DerbyDBTokenStateService);
      assertTrue(isAliasServiceSetOnParent(tokenStateService));
      assertTrue(isMasterServiceSet(tokenStateService));
    } finally {
      if (tokenStateService != null) {
        tokenStateService.stop();
      }
    }

  }
}

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
import org.apache.knox.gateway.services.token.impl.DefaultTokenAuthorityService;
import org.junit.Before;
import org.junit.Test;

public class TokenServiceFactoryTest extends ServiceFactoryTest {

  private final TokenServiceFactory serviceFactory = new TokenServiceFactory();

  @Before
  public void setUp() throws Exception {
    initConfig();
  }

  @Test
  public void testBasics() throws Exception {
    super.testBasics(serviceFactory, ServiceType.MASTER_SERVICE, ServiceType.TOKEN_SERVICE);
  }

  @Test
  public void shouldReturnDefaultTokenService() throws Exception {
    final DefaultTokenAuthorityService keystoreService = (DefaultTokenAuthorityService) serviceFactory.create(gatewayServices, ServiceType.TOKEN_SERVICE, gatewayConfig, options);
    assertTrue(keystoreService instanceof DefaultTokenAuthorityService);
    assertTrue(isKeystoreServiceSet(keystoreService));
    assertTrue(isAliasServiceSet(keystoreService));
  }

}

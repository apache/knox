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
package org.apache.knox.gateway.services.security.impl;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertArrayEquals;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.MasterService;
import org.junit.Test;

public class DefaultAliasServiceTest {

  @Test
  public void testGetHttpClientKeyPassphraseUsesKeyPassphraseAlias() throws Exception {
    GatewayConfig config = createMock(GatewayConfig.class);
    expect(config.getHttpClientKeyPassphraseAlias()).andReturn("gateway-httpclient-key-passphrase").atLeastOnce();

    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getCredentialForCluster("__gateway", "gateway-httpclient-key-passphrase"))
        .andReturn("keypass".toCharArray()).atLeastOnce();

    MasterService masterService = createMock(MasterService.class);

    replay(config, keystoreService, masterService);

    DefaultAliasService aliasService = new DefaultAliasService();
    aliasService.setKeystoreService(keystoreService);
    aliasService.setMasterService(masterService);
    aliasService.init(config, null);

    assertArrayEquals("keypass".toCharArray(), aliasService.getHttpClientKeyPassphrase());
    verify(config, keystoreService, masterService);
  }

  @Test
  public void testGetHttpClientKeyPassphraseFallsBackToKeystorePasswordThenMaster() throws Exception {
    GatewayConfig config = createMock(GatewayConfig.class);
    expect(config.getHttpClientKeyPassphraseAlias()).andReturn("gateway-httpclient-key-passphrase").atLeastOnce();
    expect(config.getHttpClientKeystorePasswordAlias()).andReturn("gateway-httpclient-keystore-password").atLeastOnce();

    KeystoreService keystoreService = createMock(KeystoreService.class);
    // Neither alias resolves -> both return null
    expect(keystoreService.getCredentialForCluster("__gateway", "gateway-httpclient-key-passphrase"))
        .andReturn(null).atLeastOnce();
    expect(keystoreService.getCredentialForCluster("__gateway", "gateway-httpclient-keystore-password"))
        .andReturn(null).atLeastOnce();

    MasterService masterService = createMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn("master".toCharArray()).atLeastOnce();

    replay(config, keystoreService, masterService);

    DefaultAliasService aliasService = new DefaultAliasService();
    aliasService.setKeystoreService(keystoreService);
    aliasService.setMasterService(masterService);
    aliasService.init(config, null);

    assertArrayEquals("master".toCharArray(), aliasService.getHttpClientKeyPassphrase());
    verify(config, keystoreService, masterService);
  }
}

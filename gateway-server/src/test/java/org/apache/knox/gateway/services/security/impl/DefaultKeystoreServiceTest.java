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
 *
 *
 */

package org.apache.knox.gateway.services.security.impl;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createMockBuilder;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.MasterService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Collections;

public class DefaultKeystoreServiceTest {
  @Rule
  public final TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void testGetTruststoreForHttpClientDefaults() throws Exception {
    final File dataDir = testFolder.newFolder();

    GatewayConfigImpl config = new GatewayConfigImpl();
    config.set("gateway.data.dir", dataDir.getAbsolutePath());

    KeyStore keystore = createNiceMock(KeyStore.class);

    DefaultKeystoreService keystoreService = createMockBuilder(DefaultKeystoreService.class)
        .addMockedMethod("getKeystoreForGateway")
        .createMock();
    expect(keystoreService.getKeystoreForGateway()).andReturn(keystore).once();

    replay(keystore, keystoreService);

    keystoreService.init(config, Collections.emptyMap());

    assertEquals(keystore, keystoreService.getTruststoreForHttpClient());

    verify(keystore, keystoreService);
  }

  @Test
  public void testGetTruststoreForHttpClientCustomTrustStore() throws Exception {
    final File dataDir = testFolder.newFolder();
    final File truststoreFile = testFolder.newFile();
    final String truststoreType = "jks";
    final String truststorePasswordAlias = "password-alias";
    final char[] truststorePassword = "truststore_password".toCharArray();

    GatewayConfigImpl config = new GatewayConfigImpl();
    config.set("gateway.data.dir", dataDir.getAbsolutePath());
    config.set("gateway.httpclient.truststore.path", truststoreFile.getAbsolutePath());
    config.set("gateway.httpclient.truststore.type", truststoreType);
    config.set("gateway.httpclient.truststore.password.alias", truststorePasswordAlias);

    KeyStore keystore = createNiceMock(KeyStore.class);

    DefaultKeystoreService keystoreService = createMockBuilder(DefaultKeystoreService.class)
        .addMockedMethod("getKeystore", File.class, String.class, char[].class)
        .addMockedMethod("getCredentialForCluster", String.class, String.class)
        .createMock();
    expect(keystoreService.getKeystore(eq(truststoreFile), eq(truststoreType), eq(truststorePassword)))
        .andReturn(keystore)
        .once();
    expect(keystoreService.getCredentialForCluster(eq(AliasService.NO_CLUSTER_NAME), eq(truststorePasswordAlias)))
        .andReturn(truststorePassword)
        .once();

    replay(keystore, keystoreService);

    keystoreService.init(config, Collections.emptyMap());

    assertEquals(keystore, keystoreService.getTruststoreForHttpClient());

    verify(keystore, keystoreService);
  }

  @Test(expected = KeystoreServiceException.class)
  public void testGetTruststoreForHttpClientMissingCustomTrustStore() throws Exception {
    final File dataDir = testFolder.newFolder();
    final String truststoreType = "jks";
    final String truststorePasswordAlias = "password-alias";
    final char[] truststorePassword = "truststore_password".toCharArray();

    GatewayConfigImpl config = new GatewayConfigImpl();
    config.set("gateway.data.dir", dataDir.getAbsolutePath());
    config.set("gateway.httpclient.truststore.path", Paths.get(dataDir.getAbsolutePath(), "missing_file.jks").toString());
    config.set("gateway.httpclient.truststore.type", truststoreType);
    config.set("gateway.httpclient.truststore.password.alias", truststorePasswordAlias);

    DefaultKeystoreService keystoreService = createMockBuilder(DefaultKeystoreService.class)
        .addMockedMethod("getKeystore", File.class, String.class, char[].class)
        .addMockedMethod("getCredentialForCluster", String.class, String.class)
        .createMock();
    expect(keystoreService.getCredentialForCluster(eq(AliasService.NO_CLUSTER_NAME), eq(truststorePasswordAlias)))
        .andReturn(truststorePassword)
        .once();

    replay(keystoreService);

    keystoreService.init(config, Collections.emptyMap());

    keystoreService.getTruststoreForHttpClient();

    verify(keystoreService);
  }

  @Test
  public void testGetTruststoreForHttpClientCustomTrustStoreMissingPasswordAlias() throws Exception {
    final File dataDir = testFolder.newFolder();
    final File truststoreFile = testFolder.newFile();
    final String truststoreType = "jks";
    final char[] masterSecret = "master_secret".toCharArray();

    GatewayConfigImpl config = new GatewayConfigImpl();
    config.set("gateway.data.dir", dataDir.getAbsolutePath());
    config.set("gateway.httpclient.truststore.path", truststoreFile.getAbsolutePath());
    config.set("gateway.httpclient.truststore.type", truststoreType);

    KeyStore keystore = createNiceMock(KeyStore.class);

    DefaultKeystoreService keystoreService = createMockBuilder(DefaultKeystoreService.class)
        .addMockedMethod("getKeystore", File.class, String.class, char[].class)
        .addMockedMethod("getCredentialForCluster", String.class, String.class)
        .withConstructor()
        .createMock();
    expect(keystoreService.getKeystore(eq(truststoreFile), eq(truststoreType), eq(masterSecret)))
        .andReturn(keystore)
        .once();
    expect(keystoreService.getCredentialForCluster(eq(AliasService.NO_CLUSTER_NAME), eq(GatewayConfig.DEFAULT_HTTP_CLIENT_TRUSTSTORE_PASSWORD_ALIAS)))
        .andReturn(null)
        .once();

    MasterService masterService = createMock(MasterService.class);
    expect(masterService.getMasterSecret()).andReturn(masterSecret);

    replay(keystore, keystoreService, masterService);

    keystoreService.init(config, Collections.emptyMap());
    keystoreService.setMasterService(masterService);

    assertEquals(keystore, keystoreService.getTruststoreForHttpClient());

    verify(keystore, keystoreService, masterService);
  }

}
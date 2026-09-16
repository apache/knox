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
package org.apache.knox.gateway.websockets;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.Assert;
import org.junit.Test;

import jakarta.websocket.ClientEndpointConfig;
import java.security.KeyStore;

public class ProxyWebSocketAdapterTest {

  private static KeyStore emptyKeyStore() throws Exception {
    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, null);
    return keyStore;
  }

  @Test
  public void testConfigureSslAppliesKeystoreAndTruststore() throws Exception {
    KeyStore identity = emptyKeyStore();
    KeyStore truststore = emptyKeyStore();
    ClientEndpointConfig clientConfig = ClientEndpointConfig.Builder.create().build();
    clientConfig.getUserProperties().put(KnoxWebSocketCreator.TRUSTSTORE_USER_PROPERTY, truststore);
    clientConfig.getUserProperties().put(KnoxWebSocketCreator.KEYSTORE_USER_PROPERTY, identity);
    clientConfig.getUserProperties().put(KnoxWebSocketCreator.KEYSTORE_KEY_PASSPHRASE_USER_PROPERTY, "secret".toCharArray());

    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
    ProxyWebSocketAdapter.configureSsl(sslContextFactory, clientConfig);

    Assert.assertSame(identity, sslContextFactory.getKeyStore());
    Assert.assertSame(truststore, sslContextFactory.getTrustStore());
  }

  @Test
  public void testConfigureSslNoKeystoreWhenAbsent() throws Exception {
    KeyStore truststore = emptyKeyStore();
    ClientEndpointConfig clientConfig = ClientEndpointConfig.Builder.create().build();
    clientConfig.getUserProperties().put(KnoxWebSocketCreator.TRUSTSTORE_USER_PROPERTY, truststore);

    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
    ProxyWebSocketAdapter.configureSsl(sslContextFactory, clientConfig);

    Assert.assertNull(sslContextFactory.getKeyStore());
    Assert.assertSame(truststore, sslContextFactory.getTrustStore());
  }

  @Test
  public void testConfigureSslKeystorePresentNullPassphrase() throws Exception {
    KeyStore identity = emptyKeyStore();
    KeyStore truststore = emptyKeyStore();
    ClientEndpointConfig clientConfig = ClientEndpointConfig.Builder.create().build();
    clientConfig.getUserProperties().put(KnoxWebSocketCreator.TRUSTSTORE_USER_PROPERTY, truststore);
    clientConfig.getUserProperties().put(KnoxWebSocketCreator.KEYSTORE_USER_PROPERTY, identity);

    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
    ProxyWebSocketAdapter.configureSsl(sslContextFactory, clientConfig);

    Assert.assertSame(identity, sslContextFactory.getKeyStore());
    Assert.assertSame(truststore, sslContextFactory.getTrustStore());
  }
}

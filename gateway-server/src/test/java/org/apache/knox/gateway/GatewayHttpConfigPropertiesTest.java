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
package org.apache.knox.gateway;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.SSLService;
import org.easymock.EasyMock;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.Test;

public class GatewayHttpConfigPropertiesTest {

  @Test
  public void testHttpProperties() throws Exception {
    doTest(false);
  }

  @Test
  public void testHttpsProperties() throws Exception {
    doTest(true);
  }

  private void doTest(boolean sslEnabled) throws Exception {
    final int requestHeaderBuffer = 512 * 1024;
    final int responseHeaderBuffer = 256 * 1024;
    final int responseBuffer = 1024 * 1024;

    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.isSSLEnabled()).andReturn(sslEnabled).anyTimes();
    EasyMock.expect(gatewayConfig.getGatewayAddress()).andReturn(Arrays.asList(new InetSocketAddress("localhost", 1234))).anyTimes();
    EasyMock.expect(gatewayConfig.getGatewayPortMappings()).andReturn(Collections.emptyMap()).anyTimes();
    EasyMock.expect(gatewayConfig.getHttpServerRequestHeaderBuffer()).andReturn(requestHeaderBuffer).anyTimes();
    EasyMock.expect(gatewayConfig.getHttpServerResponseHeaderBuffer()).andReturn(responseHeaderBuffer).anyTimes();
    EasyMock.expect(gatewayConfig.getHttpServerResponseBuffer()).andReturn(responseBuffer).anyTimes();
    EasyMock.replay(gatewayConfig);

    final GatewayServer server = new GatewayServer(gatewayConfig);

    if (sslEnabled) {
      final GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
      final SSLService sslService = EasyMock.createNiceMock(SSLService.class);
      EasyMock.expect(sslService.buildSslContextFactory(gatewayConfig)).andReturn(new SslContextFactory.Server()).anyTimes();
      EasyMock.expect(gatewayServices.getService(ServiceType.SSL_SERVICE)).andReturn(sslService).anyTimes();
      EasyMock.replay(gatewayServices, sslService);

      final Field servicesField = server.getClass().getDeclaredField("services");
      servicesField.setAccessible(true);
      servicesField.set(server, gatewayServices);
    }

    server.createJetty();

    final Field jettyField = server.getClass().getDeclaredField("jetty");
    jettyField.setAccessible(true);
    final Server jetty = (Server) jettyField.get(server);

    assertEquals(1, jetty.getConnectors().length);
    final Connector connector = jetty.getConnectors()[0];
    assertEquals(sslEnabled ? 2 : 1, connector.getConnectionFactories().size());
    final HttpConnectionFactory connectionFactory = connector.getConnectionFactory(HttpConnectionFactory.class);
    assertNotNull(connectionFactory);
    assertEquals(requestHeaderBuffer, connectionFactory.getHttpConfiguration().getRequestHeaderSize());
    assertEquals(responseHeaderBuffer, connectionFactory.getHttpConfiguration().getResponseHeaderSize());
    assertEquals(responseBuffer, connectionFactory.getHttpConfiguration().getOutputBufferSize());

    if (sslEnabled) {
      final SslConnectionFactory sslConnectionFactory = connector.getConnectionFactory(SslConnectionFactory.class);
      assertNotNull(sslConnectionFactory);
      assertEquals("SSL", sslConnectionFactory.getProtocol());
    }
  }

}

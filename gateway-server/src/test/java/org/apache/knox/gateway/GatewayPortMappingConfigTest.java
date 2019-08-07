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

import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Test the Gateway Topology Port Mapping config
 *
 */
public class GatewayPortMappingConfigTest {
  /**
   * Mock gateway config
   */
  private static GatewayConfig gatewayConfig;

  private static int eeriePort;
  private static int ontarioPort;
  private static int huronPort;

  private static int defaultPort;

  private static Server gatewayServer;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  public GatewayPortMappingConfigTest() {
    super();
  }

  @BeforeClass
  public static void init() throws Exception {

    Map<String, Integer> topologyPortMapping = new ConcurrentHashMap<>();

    // get unique ports
    eeriePort = getAvailablePort(1240, 49151);
    ontarioPort = getAvailablePort(eeriePort + 1, 49151);
    huronPort = getAvailablePort(ontarioPort + 1, 49151);

    defaultPort = getAvailablePort(huronPort + 1, 49151);

    topologyPortMapping.put("eerie", eeriePort);
    topologyPortMapping.put("ontario", ontarioPort);
    topologyPortMapping.put("huron", huronPort);

    gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getGatewayPortMappings())
        .andReturn(topologyPortMapping).anyTimes();

    EasyMock.expect(gatewayConfig.getGatewayPort()).andReturn(defaultPort)
        .anyTimes();

    EasyMock.replay(gatewayConfig);

    // Start gateway to check port conflicts
    startGatewayServer();
  }

  @AfterClass
  public static void stopServers() {
    try {
      gatewayServer.stop();
    } catch (final Exception e) {
      e.printStackTrace(System.err);
    }
  }

  /**
   * This utility method will return the next available port
   * that can be used.
   * @param min min port to check
   * @param max max port to check
   * @return Port that is available.
   */
  private static int getAvailablePort(final int min, final int max) {
    for (int i = min; i <= max; i++) {
      if (!GatewayServer.isPortInUse(i)) {
        return i;
      }
    }
    // too bad
    return -1;
  }

  /*
   * This method simply tests the configs
   */
  @Test
  public void testGatewayConfig() {
    assertThat(gatewayConfig.getGatewayPortMappings().get("eerie"),
        greaterThan(-1));
    assertThat(gatewayConfig.getGatewayPortMappings().get("ontario"),
        greaterThan(-1));
    assertThat(gatewayConfig.getGatewayPortMappings().get("huron"),
        greaterThan(-1));
  }

  /*
   * Test case where topologies "eerie" and "huron" use same ports.
   */
  @Test
  public void testCheckPortConflict()
      throws IOException, NoSuchFieldException, IllegalAccessException {
    /* Check port conflict with default port */
    exception.expect(IOException.class);
    exception.expectMessage(String.format(Locale.ROOT,
        " Port %d used by topology %s is used by other topology, ports for topologies (if defined) have to be unique. ",
        huronPort, "eerie"));

    GatewayServer gatewayServer = new GatewayServer(gatewayConfig);

    Server mockedJetty = EasyMock.createNiceMock(Server.class);

    ServerConnector mockConnector = EasyMock.createNiceMock(ServerConnector.class);
    EasyMock.expect(mockConnector.getPort()).andReturn(huronPort).anyTimes();
    EasyMock.replay(mockConnector);

    ServerConnector[] mockConnectorArray = new ServerConnector[] {mockConnector};

    EasyMock.expect(mockedJetty.getConnectors()).andReturn(mockConnectorArray).anyTimes();

    EasyMock.replay(mockedJetty);

    Field field = gatewayServer.getClass().getDeclaredField("jetty");
    field.setAccessible(true);
    field.set(gatewayServer, mockedJetty);

    gatewayServer.checkPortConflict(huronPort, "eerie", gatewayConfig);
  }

  /*
   * Test a case where gateway is already running and same port is used to start
   * another gateway.
   */
  @Test
  public void testDefaultPortInUse() throws IOException {

    exception.expect(IOException.class);
    exception
        .expectMessage(String.format(Locale.ROOT, "Port %d already in use.", defaultPort));

    final GatewayServer gatewayServer = new GatewayServer(gatewayConfig);
    gatewayServer.checkPortConflict(defaultPort, null, gatewayConfig);
  }

  private static void startGatewayServer() throws Exception {
    // use default Max threads
    gatewayServer = new Server(defaultPort);
    final ServerConnector connector = new ServerConnector(gatewayServer);
    gatewayServer.addConnector(connector);

    // workaround so we can add our handler later at runtime
    HandlerCollection handlers = new HandlerCollection(true);

    // add some initial handlers
    ContextHandler context = new ContextHandler();
    context.setContextPath("/");
    handlers.addHandler(context);

    gatewayServer.setHandler(handlers);

    // Start Server
    gatewayServer.start();
  }
}

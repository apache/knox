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

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.deploy.DeploymentFactory;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.TopologyEvent;
import org.apache.knox.gateway.topology.TopologyListener;
import org.apache.knox.test.TestUtils;
import org.easymock.EasyMock;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test how Knox holds up under multiple concurrent connections.
 *
 */
public class WebsocketMultipleConnectionTest {
  /**
   * Simulate backend websocket
   */
  private static Server backendServer;
  /**
   * URI for backend websocket server
   */
  private static URI backendServerUri;

  /**
   * Mock Gateway server
   */
  private static Server gatewayServer;

  /**
   * Mock gateway config
   */
  private static GatewayConfig gatewayConfig;

  private static GatewayServices services;

  /**
   * URI for gateway server
   */
  private static URI serverUri;

  private static File topoDir;

  /**
   * Maximum number of open connections to test.
   */
  private static int MAX_CONNECTIONS = 100;

  public WebsocketMultipleConnectionTest() {
    super();
  }

  @BeforeClass
  public static void startServers() throws Exception {

    startWebsocketServer();
    startGatewayServer();

  }

  @AfterClass
  public static void stopServers() {
    try {
      gatewayServer.stop();
      backendServer.stop();
    } catch (final Exception e) {
      e.printStackTrace(System.err);
    }

    /* Cleanup the created files */
    FileUtils.deleteQuietly(topoDir);

  }

  /**
   * Test websocket proxying through gateway.
   * 
   * @throws Exception
   */
  @Test
  public void testMultipleConnections() throws Exception {
    WebSocketContainer container = ContainerProvider.getWebSocketContainer();

    final CountDownLatch latch = new CountDownLatch(MAX_CONNECTIONS);

    Session[] sessions = new Session[MAX_CONNECTIONS];

    MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

    System.gc();
    final long heapt1 = memoryMXBean.getHeapMemoryUsage().getUsed();
    final long nonHeapt1 = memoryMXBean.getNonHeapMemoryUsage().getUsed();

    for (int i = 0; i < MAX_CONNECTIONS; i++) {

      sessions[i] = container.connectToServer(new WebsocketClient() {

        @Override
        public void onMessage(String message) {
          latch.countDown();

        }

      }, new URI(serverUri.toString() + "gateway/websocket/ws"));

    }

    for (int i = 0; i < MAX_CONNECTIONS; i++) {
      /* make sure the session is active and valid before trying to connect */
      if(sessions[i].isOpen() && sessions[i].getBasicRemote() != null) {
        sessions[i].getBasicRemote().sendText("OK");
      }
    }

    latch.await(5 * MAX_CONNECTIONS, TimeUnit.MILLISECONDS);

    System.gc();

    final long heapUsed = memoryMXBean.getHeapMemoryUsage().getUsed() - heapt1;
    final long nonHeapUsed = memoryMXBean.getNonHeapMemoryUsage().getUsed()
        - nonHeapt1;

    System.out.println("heapUsed = " + heapUsed);
    System.out.println("nonHeapUsed = " + nonHeapUsed);

    /* 90 KB per connection */
    /*
    long expected = 90 * 1024 * MAX_CONNECTIONS;
    assertThat("heap used", heapUsed, lessThan(expected));
    */
  }

  /**
   * Start Mock Websocket server that acts as backend.
   * 
   * @throws Exception
   */
  private static void startWebsocketServer() throws Exception {

    backendServer = new Server(new QueuedThreadPool(254));
    ServerConnector connector = new ServerConnector(backendServer);
    backendServer.addConnector(connector);

    final WebsocketEchoHandler handler = new WebsocketEchoHandler();

    ContextHandler context = new ContextHandler();
    context.setContextPath("/");
    context.setHandler(handler);
    backendServer.setHandler(context);

    // Start Server
    backendServer.start();

    String host = connector.getHost();
    if (host == null) {
      host = "localhost";
    }
    int port = connector.getLocalPort();
    backendServerUri = new URI(String.format(Locale.ROOT, "ws://%s:%d/ws", host, port));

  }

  /**
   * Start Gateway Server.
   * 
   * @throws Exception
   */
  private static void startGatewayServer() throws Exception {
    /* use default Max threads */
    gatewayServer = new Server(new QueuedThreadPool(254));
    final ServerConnector connector = new ServerConnector(gatewayServer);
    gatewayServer.addConnector(connector);

    /* workaround so we can add our handler later at runtime */
    HandlerCollection handlers = new HandlerCollection(true);

    /* add some initial handlers */
    ContextHandler context = new ContextHandler();
    context.setContextPath("/");
    handlers.addHandler(context);

    gatewayServer.setHandler(handlers);

    // Start Server
    gatewayServer.start();

    String host = connector.getHost();
    if (host == null) {
      host = "localhost";
    }
    int port = connector.getLocalPort();
    serverUri = new URI(String.format(Locale.ROOT, "ws://%s:%d/", host, port));

    /* Setup websocket handler */
    setupGatewayConfig(backendServerUri.toString());

    final GatewayWebsocketHandler gatewayWebsocketHandler = new GatewayWebsocketHandler(
        gatewayConfig, services);
    handlers.addHandler(gatewayWebsocketHandler);
    gatewayWebsocketHandler.start();
  }

  /**
   * Initialize the configs and components required for this test.
   * 
   * @param backend
   * @throws IOException
   */
  private static void setupGatewayConfig(final String backend)
      throws IOException {
    services = new DefaultGatewayServices();

    topoDir = createDir();
    URL serviceUrl = ClassLoader.getSystemResource("websocket-services");

    final File descriptor = new File(topoDir, "websocket.xml");
    final FileOutputStream stream = new FileOutputStream(descriptor);
    createKnoxTopology(backend).toStream(stream);
    stream.close();

    final TestTopologyListener topoListener = new TestTopologyListener();

    final Map<String, String> options = new HashMap<>();
    options.put("persist-master", "false");
    options.put("master", "password");

    gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getGatewayTopologyDir())
        .andReturn(topoDir.toString()).anyTimes();

    EasyMock.expect(gatewayConfig.getGatewayProvidersConfigDir())
            .andReturn(topoDir.getAbsolutePath() + "/shared-providers").anyTimes();

    EasyMock.expect(gatewayConfig.getGatewayDescriptorsDir())
            .andReturn(topoDir.getAbsolutePath() + "/descriptors").anyTimes();

    EasyMock.expect(gatewayConfig.getGatewayServicesDir())
        .andReturn(serviceUrl.getFile()).anyTimes();

    EasyMock.expect(gatewayConfig.getEphemeralDHKeySize()).andReturn("2048")
        .anyTimes();

    EasyMock.expect(gatewayConfig.getGatewaySecurityDir())
        .andReturn(topoDir.toString()).anyTimes();

    /* Websocket configs */
    EasyMock.expect(gatewayConfig.isWebsocketEnabled()).andReturn(true)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getWebsocketMaxTextMessageSize())
        .andReturn(GatewayConfigImpl.DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getWebsocketMaxBinaryMessageSize())
        .andReturn(GatewayConfigImpl.DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_SIZE)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getWebsocketMaxTextMessageBufferSize())
        .andReturn(
            GatewayConfigImpl.DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_BUFFER_SIZE)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getWebsocketMaxBinaryMessageBufferSize())
        .andReturn(
            GatewayConfigImpl.DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_BUFFER_SIZE)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getWebsocketInputBufferSize())
        .andReturn(GatewayConfigImpl.DEFAULT_WEBSOCKET_INPUT_BUFFER_SIZE)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getWebsocketAsyncWriteTimeout())
        .andReturn(GatewayConfigImpl.DEFAULT_WEBSOCKET_ASYNC_WRITE_TIMEOUT)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getWebsocketIdleTimeout())
        .andReturn(GatewayConfigImpl.DEFAULT_WEBSOCKET_IDLE_TIMEOUT).anyTimes();

    EasyMock.expect(gatewayConfig.getRemoteRegistryConfigurationNames())
            .andReturn(Collections.emptyList())
            .anyTimes();

    EasyMock.replay(gatewayConfig);

    try {
      services.init(gatewayConfig, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace();
    }

    DeploymentFactory.setGatewayServices(services);
    final TopologyService monitor = services
        .getService(GatewayServices.TOPOLOGY_SERVICE);
    monitor.addTopologyChangeListener(topoListener);
    monitor.reloadTopologies();

  }

  private static File createDir() throws IOException {
    return TestUtils
        .createTempDir(WebsocketEchoTest.class.getSimpleName() + "-");
  }

  private static XMLTag createKnoxTopology(final String backend) {
    XMLTag xml = XMLDoc.newDocument(true).addRoot("topology").addTag("service")
        .addTag("role").addText("WEBSOCKET").addTag("url").addText(backend)
        .gotoParent().gotoRoot();
    // System.out.println( "GATEWAY=" + xml.toString() );
    return xml;
  }

  private static class TestTopologyListener implements TopologyListener {

    public ArrayList<List<TopologyEvent>> events = new ArrayList<List<TopologyEvent>>();

    @Override
    public void handleTopologyEvent(List<TopologyEvent> events) {
      this.events.add(events);

      synchronized (this) {
        for (TopologyEvent event : events) {
          if (!event.getType().equals(TopologyEvent.Type.DELETED)) {

            /* for this test we only care about this part */
            DeploymentFactory.createDeployment(gatewayConfig,
                event.getTopology());

          }
        }

      }

    }

  }

  private static abstract class WebsocketClient extends Endpoint
      implements MessageHandler.Whole<String> {
    @Override
    public void onOpen(Session session, EndpointConfig config) {
      session.addMessageHandler(this);
    }
  }

}

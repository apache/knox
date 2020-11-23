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
import org.apache.knox.gateway.services.ServiceType;
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
import org.eclipse.jetty.websocket.server.WebSocketHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.apache.knox.gateway.config.GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS;
import static org.apache.knox.gateway.config.GatewayConfig.DEFAULT_SIGNING_KEYSTORE_TYPE;
import static org.apache.knox.gateway.config.GatewayConfig.DEFAULT_SIGNING_KEY_PASSPHRASE_ALIAS;
import static org.apache.knox.gateway.config.GatewayConfig.DEFAULT_IDENTITY_KEYSTORE_PASSWORD_ALIAS;
import static org.apache.knox.gateway.config.GatewayConfig.DEFAULT_IDENTITY_KEYSTORE_TYPE;
import static org.apache.knox.gateway.config.GatewayConfig.DEFAULT_IDENTITY_KEY_PASSPHRASE_ALIAS;

/**
 * Base class for websocoket echo tests.
 */
public class WebsocketEchoTestBase {
  private static final String TEST_KEY_ALIAS = "test-identity";

  /**
   * Simulate backend websocket
   */
  public static Server backendServer;
  /**
   * URI for backend websocket server
   */
  public static URI backendServerUri;

  /**
   * Mock Gateway server
   */
  private static Server gatewayServer;

  /**
   * Mock gateway config
   */
  public static GatewayConfig gatewayConfig;

  public static GatewayServices services;

  /**
   * URI for gateway server
   */
  public static URI serverUri;

  public static WebSocketHandler handler;

  private static File topoDir;
  private static Path dataDir;
  private static Path securityDir;
  private static Path keystoresDir;
  private static Path keystoreFile;

  public WebsocketEchoTestBase() {
    super();
  }

  public static void setUpBeforeClass() throws Exception {
    topoDir = createDir();
    dataDir = Paths.get(topoDir.getAbsolutePath(), "data").toAbsolutePath();
    securityDir = dataDir.resolve("security");
    keystoresDir = securityDir.resolve("keystores");
    keystoreFile = keystoresDir.resolve("tls.jks");
  }

  public static void startServers(String type) throws Exception {
    startServers(type, "ws");
  }

  public static void startServers(final String type, final String context) throws Exception {
    startWebsocketServer(type, context);
    startGatewayServer();
  }

  public static void tearDownAfterClass() {
    try {
      gatewayServer.stop();
      backendServer.stop();
    } catch (final Exception e) {
      e.printStackTrace(System.err);
    }

    cleanupFiles();
  }

  public static void cleanupFiles() {
    /* Cleanup the created files */
    FileUtils.deleteQuietly(topoDir);
  }

  /**
   * Start Mock Websocket server that acts as backend.
   * @throws Exception exception on websocket server start
   */
  private static void startWebsocketServer(final String type, final String contextPath) throws Exception {

    backendServer = new Server();
    ServerConnector connector = new ServerConnector(backendServer);
    backendServer.addConnector(connector);

    synchronized (WebsocketEchoTestBase.class) {
      if (handler == null) {
        handler = new WebsocketEchoHandler();
      }
    }

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
    if ("http".equals(type)) {
      backendServerUri = new URI(String.format(Locale.ROOT, "http://%s:%d/%s", host, port, contextPath));
    } else {
      backendServerUri = new URI(String.format(Locale.ROOT, "ws://%s:%d/%s", host, port, contextPath));
    }
  }

  /**
   * Start Gateway Server.
   * @throws Exception exception on server start
   */
  private static void startGatewayServer() throws Exception {
    gatewayServer = new Server();
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
   * @param backend topology to use
   * @throws IOException exception on setting up the gateway
   */
  public static void setupGatewayConfig(final String backend) throws IOException {
    services = new DefaultGatewayServices();

    URL serviceUrl = ClassLoader.getSystemResource("websocket-services");

    final File descriptor = new File(topoDir, "websocket.xml");
    try(OutputStream stream = Files.newOutputStream(descriptor.toPath())) {
      createKnoxTopology(backend).toStream(stream);
    }

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

    EasyMock.expect(gatewayConfig.getWebsocketMaxWaitBufferCount())
        .andReturn(GatewayConfigImpl.DEFAULT_WEBSOCKET_MAX_WAIT_BUFFER_COUNT).anyTimes();

    EasyMock.expect(gatewayConfig.getRemoteRegistryConfigurationNames())
            .andReturn(Collections.emptyList())
            .anyTimes();

    EasyMock.expect(gatewayConfig.getGatewayDataDir())
        .andReturn(dataDir.toString())
        .anyTimes();

    EasyMock.expect(gatewayConfig.getGatewaySecurityDir())
        .andReturn(securityDir.toString())
        .anyTimes();

    EasyMock.expect(gatewayConfig.getGatewayKeystoreDir())
        .andReturn(keystoresDir.toString())
        .anyTimes();

    EasyMock.expect(gatewayConfig.getIdentityKeystorePath())
        .andReturn(keystoreFile.toString())
        .anyTimes();

    EasyMock.expect(gatewayConfig.getIdentityKeystoreType())
        .andReturn(DEFAULT_IDENTITY_KEYSTORE_TYPE)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getIdentityKeystorePasswordAlias())
        .andReturn(DEFAULT_IDENTITY_KEYSTORE_PASSWORD_ALIAS)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getIdentityKeyAlias())
        .andReturn(TEST_KEY_ALIAS)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getIdentityKeyPassphraseAlias())
        .andReturn(DEFAULT_IDENTITY_KEY_PASSPHRASE_ALIAS)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getSigningKeystorePasswordAlias())
        .andReturn(DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getSigningKeyPassphraseAlias())
        .andReturn(DEFAULT_SIGNING_KEY_PASSPHRASE_ALIAS)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getSigningKeystorePath())
        .andReturn(keystoreFile.toString())
        .anyTimes();

    EasyMock.expect(gatewayConfig.getSigningKeystoreType())
        .andReturn(DEFAULT_SIGNING_KEYSTORE_TYPE)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getSigningKeyAlias())
        .andReturn(TEST_KEY_ALIAS)
        .anyTimes();

    EasyMock.expect(gatewayConfig.getServiceParameter(EasyMock.anyString(), EasyMock.anyString())).andReturn("").anyTimes();

    EasyMock.expect(gatewayConfig.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(gatewayConfig.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    EasyMock.replay(gatewayConfig);

    try {
      services.init(gatewayConfig, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace();
    }

    DeploymentFactory.setGatewayServices(services);
    final TopologyService monitor = services
        .getService(ServiceType.TOPOLOGY_SERVICE);
    monitor.addTopologyChangeListener(topoListener);
    monitor.reloadTopologies();
  }

  private static File createDir() throws IOException {
    return TestUtils
        .createTempDir(WebsocketEchoTest.class.getSimpleName() + "-");
  }

  private static XMLTag createKnoxTopology(final String backend) {
    return XMLDoc.newDocument(true).addRoot("topology").addTag("service")
        .addTag("role").addText("WEBSOCKET").addTag("url").addText(backend)
        .gotoParent().gotoRoot();
  }

  private static class TestTopologyListener implements TopologyListener {
    public List<List<TopologyEvent>> events = new ArrayList<>();

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
}

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

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.easymock.EasyMock;

import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Test for max message size.
 */
public class MessageFailureTest {
  private static Server backend;
  private static ServerConnector connector;
  private static URI serverUri;

  /* Proxy */
  private static Server proxy;
  private static ServerConnector proxyConnector;
  private static URI proxyUri;

  public MessageFailureTest() {
    super();
  }

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    startBackend();
    startProxy();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    /* ORDER MATTERS ! */
    proxy.stop();
    backend.stop();
  }

  /*
   * Test for a message that bigger than configured value
   */
  @Test(timeout = 8000)
  public void testMessageTooBig() throws Exception {
    final String bigMessage = RandomStringUtils.randomAscii(66001);

    WebSocketContainer container = ContainerProvider.getWebSocketContainer();

    WebsocketClient client = new WebsocketClient();
    javax.websocket.Session session = container.connectToServer(client,
        proxyUri);
    session.getBasicRemote().sendText(bigMessage);

    client.awaitClose(CloseReason.CloseCodes.TOO_BIG.getCode(), 1000,
        TimeUnit.MILLISECONDS);

    Assert.assertThat(client.close.getCloseCode().getCode(), CoreMatchers.is(CloseReason.CloseCodes.TOO_BIG.getCode()));
  }

  /**
   * Test for a message that bigger than Jetty default but smaller than limit
   * @throws Exception exception on failure
   */
  @Test(timeout = 8000)
  public void testMessageBiggerThanDefault() throws Exception {
    final String bigMessage = RandomStringUtils.randomAscii(66000);

    WebSocketContainer container = ContainerProvider.getWebSocketContainer();

    WebsocketClient client = new WebsocketClient();
    javax.websocket.Session session = container.connectToServer(client,
            proxyUri);
    session.getBasicRemote().sendText(bigMessage);

    client.awaitClose(CloseReason.CloseCodes.TOO_BIG.getCode(), 1000,
            TimeUnit.MILLISECONDS);

    Assert.assertThat(client.close.getCloseCode().getCode(), CoreMatchers.is(CloseReason.CloseCodes.TOO_BIG.getCode()));

  }

  /*
   * Test for a message within limit.
   */
  @Test(timeout = 8000)
  public void testMessageOk() throws Exception {
    final String message = "Echo";

    WebSocketContainer container = ContainerProvider.getWebSocketContainer();

    WebsocketClient client = new WebsocketClient();
    javax.websocket.Session session = container.connectToServer(client,
        proxyUri);
    session.getBasicRemote().sendText(message);

    client.messageQueue.awaitMessages(1, 1000, TimeUnit.MILLISECONDS);

    Assert.assertThat(client.messageQueue.get(0), CoreMatchers.is("Echo"));
  }

  private static void startBackend() throws Exception {
    backend = new Server();
    connector = new ServerConnector(backend);
    backend.addConnector(connector);

    /* start backend with Echo socket */
    final BigEchoSocketHandler wsHandler = new BigEchoSocketHandler(
        new EchoSocket());

    ContextHandler context = new ContextHandler();
    context.setContextPath("/");
    context.setHandler(wsHandler);
    backend.setHandler(context);

    // Start Server
    backend.start();

    String host = connector.getHost();
    if (host == null) {
      host = "localhost";
    }
    int port = connector.getLocalPort();
    serverUri = new URI(String.format(Locale.ROOT, "ws://%s:%d/", host, port));
  }

  private static void startProxy() throws Exception {
    GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    proxy = new Server();
    proxyConnector = new ServerConnector(proxy);
    proxy.addConnector(proxyConnector);

    /* start Knox with WebsocketAdapter to test */
    final BigEchoSocketHandler wsHandler = new BigEchoSocketHandler(
        new ProxyWebSocketAdapter(serverUri, Executors.newFixedThreadPool(10), gatewayConfig));

    ContextHandler context = new ContextHandler();
    context.setContextPath("/");
    context.setHandler(wsHandler);
    proxy.setHandler(context);

    // Start Server
    proxy.start();

    String host = proxyConnector.getHost();
    if (host == null) {
      host = "localhost";
    }
    int port = proxyConnector.getLocalPort();
    proxyUri = new URI(String.format(Locale.ROOT, "ws://%s:%d/", host, port));
  }
}

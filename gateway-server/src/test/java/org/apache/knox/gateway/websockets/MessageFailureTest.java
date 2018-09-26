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

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Dummy Test for max message size.
 *
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
  public static void startServer() throws Exception {
    startBackend();
    startProxy();

  }

  @AfterClass
  public static  void stopServer() throws Exception {
    /* ORDER MATTERS ! */
    proxy.stop();
    backend.stop();
    
  }


  /**
   * Test for a message that bigger than configured value
   * @throws IOException
   * @throws Exception
   */
  @Test(timeout = 8000)
  public void testMessageTooBig() throws IOException, Exception {
    final String bigMessage = "Echooooooooooooo";

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
   * Test for a message within limit.
   * @throws IOException
   * @throws Exception
   */
  @Test(timeout = 8000)
  public void testMessageOk() throws IOException, Exception {
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
    proxy = new Server();
    proxyConnector = new ServerConnector(proxy);
    proxy.addConnector(proxyConnector);

    /* start Knox with WebsocketAdapter to test */
    final BigEchoSocketHandler wsHandler = new BigEchoSocketHandler(
        new ProxyWebSocketAdapter(serverUri, Executors.newFixedThreadPool(10)));

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

/**
 * A Mock websocket handler that just Echos messages
 *
 */
class BigEchoSocketHandler extends WebSocketHandler
    implements WebSocketCreator {

  // final EchoSocket socket = new EchoSocket();
  final WebSocketAdapter socket;

  public BigEchoSocketHandler(final WebSocketAdapter socket) {
    this.socket = socket;
  }

  @Override
  public void configure(WebSocketServletFactory factory) {
    factory.getPolicy().setMaxTextMessageSize(10);
    factory.getPolicy().setMaxBinaryMessageSize(10);
    factory.setCreator(this);
  }

  @Override
  public Object createWebSocket(ServletUpgradeRequest req,
      ServletUpgradeResponse resp) {
    return socket;
  }

}

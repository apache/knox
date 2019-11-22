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

import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.websocket.api.BatchMode;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * A basic test that attempts to proxy websocket connections through Knox
 * gateway.
 * <p>
 * The way the test is set up is as follows: <br>
 * <ul>
 * <li>A Mock Websocket server is setup which simply echos the responses sent by
 * client.
 * <li>Knox Gateway is set up with websocket handler
 * {@link GatewayWebsocketHandler} that can proxy the requests.
 * <li>Appropriate Topology and service definition files are set up with the
 * address of the Websocket server.
 * <li>A mock client is setup to connect to gateway.
 * </ul>
 *
 * The test is to confirm whether the message is sent all the way to the backend
 * Websocket server through Knox and back.
 *
 * @since 0.10
 */
public class WebsocketServerInitiatedPingTest extends WebsocketEchoTestBase {

  public WebsocketServerInitiatedPingTest() {
    super();
  }

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    handler = new WebsocketServerInitiatedPingHandler();
    WebsocketEchoTestBase.setUpBeforeClass();
    WebsocketEchoTestBase.startServers("ws");
  }

  @AfterClass
  public static void tearDownAfterClass() {
    WebsocketEchoTestBase.tearDownAfterClass();
  }

  /*
   * Test websocket server initiated ping
   */
  @Test
  public void testGatewayServerInitiatedPing() throws Exception {
    WebSocketContainer container = ContainerProvider.getWebSocketContainer();

    WebsocketClient client = new WebsocketClient();
    container.connectToServer(client,
            new URI(serverUri.toString() + "gateway/websocket/123foo456bar/channels"));

    //session.getBasicRemote().sendText("Echo");
    client.messageQueue.awaitMessages(1, 10000, TimeUnit.MILLISECONDS);

    assertThat(client.messageQueue.get(0), is("PingPong"));
  }

  /**
   * A Mock websocket handler
   *
   */
  private static class WebsocketServerInitiatedPingHandler extends WebSocketHandler implements WebSocketCreator {
    private final ServerInitiatingPingSocket socket = new ServerInitiatingPingSocket();

    @Override
    public void configure(WebSocketServletFactory factory) {
      factory.getPolicy().setMaxTextMessageSize(2 * 1024 * 1024);
      factory.setCreator(this);
    }

    @Override
    public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
      return socket;
    }
  }

  /**
   * A simple socket initiating message on connect
   */
  private static class ServerInitiatingPingSocket extends WebSocketAdapter {

    @Override
    public void onWebSocketError(Throwable cause) {
      throw new RuntimeException(cause);
    }

    @Override
    public void onWebSocketConnect(Session sess) {
      super.onWebSocketConnect(sess);
      try {
        Thread.sleep(1000);
      } catch (Exception e) {
      }
      final String textMessage = "PingPong";
      final ByteBuffer binaryMessage = ByteBuffer.wrap(
                 textMessage.getBytes(StandardCharsets.UTF_8));

      try {
        RemoteEndpoint remote = getRemote();
        remote.sendPing(binaryMessage);
        if (remote.getBatchMode() == BatchMode.ON) {
          remote.flush();
        }
      } catch (IOException x) {
        throw new RuntimeIOException(x);
      }
    }
  }
}

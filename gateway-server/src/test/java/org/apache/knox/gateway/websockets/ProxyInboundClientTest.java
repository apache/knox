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
package org.apache.knox.gateway.websockets;

import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.instanceOf;

/**
 * Test {@link ProxyInboundClient} class.
 * @since 0.14.0
 */
public class ProxyInboundClientTest {

  private static Server server;
  private static URI serverUri;
  private static Handler handler;

  String recievedMessage = null;

  byte[] recievedBinaryMessage = null;


  /* create an instance */
  public ProxyInboundClientTest() {
    super();
  }

  @BeforeClass
  public static void startWSServer() throws Exception
  {
    server = new Server();
    ServerConnector connector = new ServerConnector(server);
    server.addConnector(connector);

    handler = new WebsocketEchoHandler();

    ContextHandler context = new ContextHandler();
    context.setContextPath("/");
    context.setHandler(handler);
    server.setHandler(context);

    server.start();

    String host = connector.getHost();
    if (host == null)
    {
      host = "localhost";
    }
    int port = connector.getLocalPort();
    serverUri = new URI(String.format(Locale.ROOT, "ws://%s:%d/",host,port));
  }

  @AfterClass
  public static void stopServer()
  {
    try
    {
      server.stop();
    }
    catch (Exception e)
    {
      e.printStackTrace(System.err);
    }
  }

  //@Test(timeout = 3000)
  @Test
  public void testClientInstance() throws IOException, DeploymentException {

    final String textMessage = "Echo";
    final ByteBuffer binarymessage = ByteBuffer.wrap(textMessage.getBytes(StandardCharsets.UTF_8));

    final AtomicBoolean isTestComplete = new AtomicBoolean(false);

    final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    final ProxyInboundClient client = new ProxyInboundClient( new MessageEventCallback() {

      /**
       * A generic callback, can be left un-implemented
       *
       * @param message
       */
      @Override
      public void doCallback(String message) {

      }

      /**
       * Callback when connection is established.
       *
       * @param session
       */
      @Override
      public void onConnectionOpen(Object session) {

      }

      /**
       * Callback when connection is closed.
       *
       * @param reason
       */
      @Override
      public void onConnectionClose(CloseReason reason) {
        isTestComplete.set(true);
      }

      /**
       * Callback when there is an error in connection.
       *
       * @param cause
       */
      @Override
      public void onError(Throwable cause) {
        isTestComplete.set(true);
      }

      /**
       * Callback when a text message is received.
       *
       * @param message
       * @param session
       */
      @Override
      public void onMessageText(String message, Object session) {
        recievedMessage = message;
        isTestComplete.set(true);
      }

      /**
       * Callback when a binary message is received.
       *
       * @param message
       * @param last
       * @param session
       */
      @Override
      public void onMessageBinary(byte[] message, boolean last,
          Object session) {

      }
    } );

    Assert.assertThat(client, instanceOf(javax.websocket.Endpoint.class));

    Session session = container.connectToServer(client, serverUri);

    session.getBasicRemote().sendText(textMessage);

    while(!isTestComplete.get()) {
      /* just wait for the test to finish */
    }

    Assert.assertEquals("The received text message is not the same as the sent", textMessage, recievedMessage);
  }

  @Test(timeout = 3000)
  public void testBinarymessage() throws IOException, DeploymentException {

    final String textMessage = "Echo";
    final ByteBuffer binarymessage = ByteBuffer.wrap(textMessage.getBytes(StandardCharsets.UTF_8));

    final AtomicBoolean isTestComplete = new AtomicBoolean(false);

    final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    final ProxyInboundClient client = new ProxyInboundClient( new MessageEventCallback() {

      /**
       * A generic callback, can be left un-implemented
       *
       * @param message
       */
      @Override
      public void doCallback(String message) {

      }

      /**
       * Callback when connection is established.
       *
       * @param session
       */
      @Override
      public void onConnectionOpen(Object session) {

      }

      /**
       * Callback when connection is closed.
       *
       * @param reason
       */
      @Override
      public void onConnectionClose(CloseReason reason) {
        isTestComplete.set(true);
      }

      /**
       * Callback when there is an error in connection.
       *
       * @param cause
       */
      @Override
      public void onError(Throwable cause) {
        isTestComplete.set(true);
      }

      /**
       * Callback when a text message is received.
       *
       * @param message
       * @param session
       */
      @Override
      public void onMessageText(String message, Object session) {
        recievedMessage = message;
        isTestComplete.set(true);
      }

      /**
       * Callback when a binary message is received.
       *
       * @param message
       * @param last
       * @param session
       */
      @Override
      public void onMessageBinary(byte[] message, boolean last,
          Object session) {
        recievedBinaryMessage = message;
        isTestComplete.set(true);
      }
    } );

    Assert.assertThat(client, instanceOf(javax.websocket.Endpoint.class));

    Session session = container.connectToServer(client, serverUri);

    session.getBasicRemote().sendBinary(binarymessage);

    while(!isTestComplete.get()) {
      /* just wait for the test to finish */
    }

    Assert.assertEquals("Binary message does not match", textMessage, new String(recievedBinaryMessage, StandardCharsets.UTF_8));
  }

  @Test(timeout = 3000)
  public void testTextMaxBufferLimit() throws IOException, DeploymentException {

    final String longMessage = RandomStringUtils.random(100000);

    final AtomicBoolean isTestComplete = new AtomicBoolean(false);

    final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    final ProxyInboundClient client = new ProxyInboundClient( new MessageEventCallback() {

      /**
       * A generic callback, can be left un-implemented
       *
       * @param message
       */
      @Override
      public void doCallback(String message) {

      }

      /**
       * Callback when connection is established.
       *
       * @param session
       */
      @Override
      public void onConnectionOpen(Object session) {

      }

      /**
       * Callback when connection is closed.
       *
       * @param reason
       */
      @Override
      public void onConnectionClose(CloseReason reason) {
        isTestComplete.set(true);
      }

      /**
       * Callback when there is an error in connection.
       *
       * @param cause
       */
      @Override
      public void onError(Throwable cause) {
        isTestComplete.set(true);
      }

      /**
       * Callback when a text message is received.
       *
       * @param message
       * @param session
       */
      @Override
      public void onMessageText(String message, Object session) {
        recievedMessage = message;
        isTestComplete.set(true);
      }

      /**
       * Callback when a binary message is received.
       *
       * @param message
       * @param last
       * @param session
       */
      @Override
      public void onMessageBinary(byte[] message, boolean last,
          Object session) {

      }
    } );

    Assert.assertThat(client, instanceOf(javax.websocket.Endpoint.class));

    Session session = container.connectToServer(client, serverUri);

    session.getBasicRemote().sendText(longMessage);

    while(!isTestComplete.get()) {
      /* just wait for the test to finish */
    }

    Assert.assertEquals(longMessage, recievedMessage);

  }



}

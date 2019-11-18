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

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.PongMessage;

/**
 * A Websocket client with callback which is not annotation based.
 * This handler accepts String and binary messages.
 * @since 0.14.0
 */
public class ProxyInboundClient extends Endpoint {

  /**
   * Callback to be called once we have events on our socket.
   */
  private MessageEventCallback callback;

  protected Session session;
  protected EndpointConfig config;


  public ProxyInboundClient(final MessageEventCallback callback) {
    super();
    this.callback = callback;
  }

  /**
   * Developers must implement this method to be notified when a new
   * conversation has just begun.
   *
   * @param backendSession the session that has just been activated.
   * @param config  the configuration used to configure this endpoint.
   */
  @Override
  public void onOpen(final javax.websocket.Session backendSession, final EndpointConfig config) {
    this.session = backendSession;
    this.config = config;

    /* Add message handler for binary data */
    session.addMessageHandler(new MessageHandler.Whole<byte[]>() {

      /**
       * Called when the message has been fully received.
       *
       * @param message the message data.
       */
      @Override
      public void onMessage(final byte[] message) {
        callback.onMessageBinary(message, true, session);
      }

    });

    /* Add message handler for text data */
    session.addMessageHandler(new MessageHandler.Whole<String>() {

      /**
       * Called when the message has been fully received.
       *
       * @param message the message data.
       */
      @Override
      public void onMessage(final String message) {
        callback.onMessageText(message, session);
      }

    });

    /* Add message handler for Pong Control Message */
    session.addMessageHandler(new MessageHandler.Whole<PongMessage>() {

      /**
       * Called when a ping message has been received.
       *
       * @param message the message data.
       */
      @Override
      public void onMessage(final PongMessage pongMessage) {
        callback.onMessagePong(pongMessage, session);
      }

    });

    callback.onConnectionOpen(backendSession);
  }

  @Override
  public void onClose(final javax.websocket.Session backendSession, final CloseReason closeReason) {
    callback.onConnectionClose(closeReason);
    this.session = null;
  }

  @Override
  public void onError(final javax.websocket.Session backendSession, final Throwable cause) {
    callback.onError(cause);
    this.session = null;
  }

}

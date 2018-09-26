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

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;

/**
 * A Websocket client with callback.
 * @since 0.10
 */
@ClientEndpoint
public class ProxyInboundSocket {

  /**
   * Callback to be called once we have events on our socket.
   */
  final MessageEventCallback callback;

  /**
   * Create an instance
   */
  public ProxyInboundSocket(final MessageEventCallback callback) {
    super();
    this.callback = callback;
  }

  /* Client methods */
  @OnOpen
  public void onClientOpen(final javax.websocket.Session backendSession) {

    callback.onConnectionOpen(backendSession);

  }

  @OnClose
  public void onClientClose(final CloseReason reason) {
    callback.onConnectionClose(reason);
  }

  @OnError
  public void onClientError(Throwable cause) {
    callback.onError(cause);
  }

  @OnMessage(maxMessageSize = Integer.MAX_VALUE)
  public void onBackendMessage(final String message,
      final javax.websocket.Session session) {
    callback.onMessageText(message, session);

  }

  @OnMessage(maxMessageSize = Integer.MAX_VALUE)
  public void onBackendMessageBinary(final byte[] message, final boolean last,
      final javax.websocket.Session session) {

    callback.onMessageBinary(message, last, session);

  }

}

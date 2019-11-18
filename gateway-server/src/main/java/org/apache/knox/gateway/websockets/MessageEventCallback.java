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

import javax.websocket.CloseReason;
import javax.websocket.PongMessage;

/**
 * A simple callback interface used when evens happen on the Websocket client socket.
 *
 */
public interface MessageEventCallback {

  /**
   * A generic callback, can be left un-implemented
   * @param message message
   */
  void doCallback(String message);

  /**
   * Callback when connection is established.
   * @param session session
   */
  void onConnectionOpen(Object session);

  /**
   * Callback when connection is closed.
   * @param reason Reason for the connection close
   */
  void onConnectionClose(CloseReason reason);

  /**
   * Callback when there is an error in connection.
   * @param cause cause to throw on error
   */
  void onError(Throwable cause);

  /**
   * Callback when a text message is received.
   * @param message message
   * @param session session
   */
  void onMessageText(String message, Object session);

  /**
   * Callback when a binary message is received.
   * @param message message
   * @param last last
   * @param session session
   */
  void onMessageBinary(byte[]  message, boolean last, Object session);

  /**
   * Callback when a pong control message is received.
   * @param pongMessage pong message
   * @param session session
   */
  void onMessagePong(PongMessage pongMessage, Object session);
}

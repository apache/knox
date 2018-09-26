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

/**
 * A simple callback interface used when evens happen on the Websocket client socket.
 *
 */
public interface MessageEventCallback {

  /**
   * A generic callback, can be left un-implemented
   */
  void doCallback(final String message);
  
  /**
   * Callback when connection is established.
   * @param session 
   */
  void onConnectionOpen(final Object session);
  
  /**
   * Callback when connection is closed.
   * @param reason
   */
  void onConnectionClose(final CloseReason reason);
  
  /**
   * Callback when there is an error in connection.
   * @param cause
   */
  void onError(final Throwable cause);
  
  /**
   * Callback when a text message is received.
   * @param message
   * @param session
   */
  void onMessageText(final String message, final Object session);
  
  /**
   * Callback when a binary message is received.
   * @param message
   * @param last
   * @param session
   */
  void onMessageBinary(final byte[]  message, final boolean last, final Object session);
  
}

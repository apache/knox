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

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

/**
 * Logging for Websocket
 *
 * @since 0.10
 */
@Messages(logger = "org.apache.knox.gateway.websockets")
public interface WebsocketLogMessages {

  @Message(level = MessageLevel.ERROR,
      text = "Error creating websocket connection: {0}")
  void failedCreatingWebSocket(
      @StackTrace(level = MessageLevel.ERROR) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Unable to connect to websocket server: {0}")
  void connectionFailed(@StackTrace(level = MessageLevel.ERROR) Exception e);

  @Message(level = MessageLevel.ERROR, text = "Error: {0}")
  void onError(String message);

  @Message(level = MessageLevel.ERROR, text = "Bad or malformed url: {0}")
  void badUrlError(@StackTrace(level = MessageLevel.ERROR) Exception e);

  @Message(level = MessageLevel.DEBUG,
      text = "Websocket connection to backend server {0} opened")
  void onConnectionOpen(String backend);

  @Message(level = MessageLevel.DEBUG, text = "Message: {0}")
  void logMessage(String message);

  @Message(level = MessageLevel.DEBUG,
      text = "Websocket connection to backend server {0} closed")
  void onConnectionClose(String backend);

  @Message(level = MessageLevel.DEBUG,
      text = "{0}")
  void debugLog(String message);

}

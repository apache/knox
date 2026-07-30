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
package org.apache.knox.gateway.grpc;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

/**
 * Logging for the gRPC gateway listener.
 *
 * @since 3.0.0
 */
@Messages(logger = "org.apache.knox.gateway.grpc")
public interface GrpcGatewayMessages {

  @Message(level = MessageLevel.INFO, text = "Started {0} gRPC listener on port {1}")
  void startedListener(String name, int port);

  @Message(level = MessageLevel.INFO, text = "Stopping {0} gRPC listener, draining for up to {1} ms")
  void stoppingListener(String name, long drainTimeoutMillis);

  @Message(level = MessageLevel.WARN,
      text = "The {0} gRPC listener did not drain within {1} ms; terminating in-flight calls")
  void drainTimedOut(String name, long drainTimeoutMillis);

  @Message(level = MessageLevel.INFO, text = "Stopped {0} gRPC listener")
  void stoppedListener(String name);

  @Message(level = MessageLevel.ERROR, text = "Failed to start the {0} gRPC listener")
  void failedToStartListener(String name, @StackTrace(level = MessageLevel.ERROR) Exception e);

  @Message(level = MessageLevel.WARN, text = "Rejected unauthenticated gRPC call to {0}: {1}")
  void authenticationFailed(String method, String reason);

  @Message(level = MessageLevel.WARN,
      text = "Denied gRPC call to {0} for user {1} in topology {2}: {3}")
  void authorizationFailed(String method, String user, String topology, String reason);

  @Message(level = MessageLevel.WARN, text = "Cannot route gRPC call to {0}: {1}")
  void routingFailed(String method, String reason);

  @Message(level = MessageLevel.DEBUG,
      text = "Routing gRPC call to {0} for user {1} to topology {2} backend {3}")
  void routingCall(String method, String user, String topology, String backend);

  @Message(level = MessageLevel.DEBUG, text = "Opened backend gRPC channel to {0}")
  void openedBackendChannel(String backend);

  @Message(level = MessageLevel.DEBUG, text = "Closed backend gRPC channel to {0}")
  void closedBackendChannel(String backend);

  @Message(level = MessageLevel.ERROR, text = "Failed to build TLS context for the {0} gRPC listener")
  void failedToBuildServerTls(String name, @StackTrace(level = MessageLevel.ERROR) Exception e);

  @Message(level = MessageLevel.ERROR, text = "Failed to build TLS context for backend {0}")
  void failedToBuildBackendTls(String backend, @StackTrace(level = MessageLevel.ERROR) Exception e);

  @Message(level = MessageLevel.WARN,
      text = "The {0} gRPC listener is running without TLS; bearer tokens will cross the network in clear text")
  void listenerTlsDisabled(String name);

  @Message(level = MessageLevel.WARN, text = "Could not resolve the backend token alias {0}")
  void missingBackendTokenAlias(String alias);

  @Message(level = MessageLevel.INFO,
      text = "Reloaded the {0} listener message policy: {1}")
  void reloadedPolicy(String name, String policy);

  @Message(level = MessageLevel.WARN,
      text = "The {0} listener cannot apply changes to [{1}] without a gateway restart; "
          + "the running values remain in effect")
  void restartOnlyConfigChanged(String name, String properties);

  @Message(level = MessageLevel.DEBUG, text = "{0}")
  void debugLog(String message);
}

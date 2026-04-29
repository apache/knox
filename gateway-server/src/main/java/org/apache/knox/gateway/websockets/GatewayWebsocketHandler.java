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
import org.apache.knox.gateway.services.GatewayServices;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.server.ServerWebSocketContainer;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;

import java.time.Duration;

/**
 * Websocket handler that will handle websocket connection request. This class
 * is responsible for creating a proxy socket for inbound and outbound
 * connections. This is also where the http to websocket handoff happens.
 *
 * @since 0.10
 */
public class GatewayWebsocketHandler extends Handler.Wrapper {

  final GatewayConfig config;
  final GatewayServices services;
  private WebSocketUpgradeHandler wsHandler;

  public GatewayWebsocketHandler(final GatewayConfig config,
                                 final GatewayServices services) {
    super();
    this.config = config;
    this.services = services;
  }

  @Override
  protected void doStart() throws Exception {
    Server server = getServer();
    if (server == null) {
      throw new IllegalStateException("GatewayWebsocketHandler must be attached to a Server before starting");
    }

    // 1. Get or create the global ServerWebSocketContainer (no ContextHandler needed)
    ServerWebSocketContainer container = ServerWebSocketContainer.ensure(server, null);
    configureServerWebSocketContainer(container);

    // 3. Create the UpgradeHandler.
    this.wsHandler = new WebSocketUpgradeHandler(container);

    // 4. PRESERVE THE CHAIN: This handler currently wraps PortMappingHelperHandler.
    // We must ensure the internal wsHandler also wraps it, so HTTP traffic flows downwards.
    this.wsHandler.setHandler(getHandler());

    // Start the internal handler
    this.wsHandler.start();

    super.doStart();
  }

  @Override
  protected void doStop() throws Exception {
    if (this.wsHandler != null) {
      this.wsHandler.stop();
    }
    super.doStop();
  }

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {
    // Delegate to the WebSocketUpgradeHandler.
    // If it detects a WebSocket upgrade, it triggers KnoxWebSocketCreator and returns true.
    // If it's standard HTTP traffic, it delegates to its wrapped handler (PortMappingHelperHandler).
    return wsHandler.handle(request, response, callback);
  }

  public void configureServerWebSocketContainer(ServerWebSocketContainer container) {
    container.setMaxTextMessageSize(config.getWebsocketMaxTextMessageSize());
    container.setMaxBinaryMessageSize(config.getWebsocketMaxBinaryMessageSize());
    container.setInputBufferSize(config.getWebsocketInputBufferSize());

    container.setIdleTimeout(Duration.ofMillis(config.getWebsocketIdleTimeout()));

    // 2. Map ALL incoming requests to our custom Knox routing creator
    // "regex|^/.*" acts as a catch-all interceptor.
    container.addMapping("regex|^/.*", new KnoxWebSocketCreator(config, services));

    //removed in Jetty 12 container.setMaxBinaryMessageBufferSize(config.getWebsocketMaxBinaryMessageBufferSize());
    //removed in Jetty 12 container.setMaxTextMessageBufferSize(config.getWebsocketMaxTextMessageBufferSize());

    //removed in Jetty 12 container.setAsyncWriteTimeout(config.getWebsocketAsyncWriteTimeout());
    // handled by the core HTTP connection idle timeouts and
    // one can apply it directly to the asynchronous write execution
    // (e.g., using CompletableFuture.orTimeout(duration, TimeUnit) when we call session.sendText(...)).

    // removed, idle timeout is used or specified in send() methods:
    // container.setAsyncSendTimeout(config.getWebsocketAsyncWriteTimeout());

    //same as setIdleTimeout: container.setDefaultMaxSessionIdleTimeout(config.getWebsocketIdleTimeout());

  }

}

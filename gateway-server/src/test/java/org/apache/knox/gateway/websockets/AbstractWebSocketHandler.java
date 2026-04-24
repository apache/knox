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

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.server.ServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.ServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.ServerWebSocketContainer;
import org.eclipse.jetty.websocket.server.WebSocketCreator;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;

/**
 * A base class for Jetty 12 Native WebSocket handlers that abstracts away
 * the container initialization and upgrade handler lifecycle.
 */
public abstract class AbstractWebSocketHandler extends Handler.Wrapper implements WebSocketCreator {

    private WebSocketUpgradeHandler wsHandler;

    @Override
    protected void doStart() throws Exception {
        Server server = getServer();
        if (server == null) {
            throw new IllegalStateException("WebSocketHandler must be attached to a Server before starting");
        }

        // Ensure the container exists
        ServerWebSocketContainer container = ServerWebSocketContainer.ensure(server, null);

        // Let the subclass apply policies (max sizes, timeouts, etc.)
        configure(container);

        // Use the exposed mapping spec
        container.addMapping(getMappingSpec(), this);

        // Create, wire, and start the internal UpgradeHandler
        this.wsHandler = new WebSocketUpgradeHandler(container);
        this.wsHandler.setHandler(getHandler());
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
        return wsHandler.handle(request, response, callback);
    }

    /**
     * Defines the URL mapping for this WebSocket handler.
     * Defaults to catching all requests. Subclasses can override this to specify exact paths.
     * * @return The Jetty mapping spec (e.g., "/ws/*", "regex|^/.*", etc.)
     */
    protected String getMappingSpec() {
        return "regex|^/.*";
    }

    /**
     * Configure the ServerWebSocketContainer policies.
     * @param container The Jetty 12 WebSocket container
     */
    protected abstract void configure(ServerWebSocketContainer container);

    /**
     * Return the Jetty 12 Session.Listener (the WebSocket) for the given request.
     * @param req The upgrade request
     * @param resp The upgrade response
     * @param callback The callback to signal success/failure of the upgrade
     * @return The WebSocket instance
     */
    @Override
    public abstract Object createWebSocket(ServerUpgradeRequest req, ServerUpgradeResponse resp, Callback callback);
}

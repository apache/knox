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

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutorService;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.websocket.api.BatchMode;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;

/**
 * Handles outbound/inbound Websocket connections and sessions.
 *
 * @since 0.10
 */
public class ProxyWebSocketAdapter extends WebSocketAdapter {

  private static final WebsocketLogMessages LOG = MessagesFactory
      .get(WebsocketLogMessages.class);

  /* URI for the backend */
  private final URI backend;

  /* Session between the frontend (browser) and Knox */
  private Session frontendSession;

  /* Session between the backend (outbound) and Knox */
  private javax.websocket.Session backendSession;

  private WebSocketContainer container;

  private ExecutorService pool;

  /**
   * Used to transmit headers from browser to backend server.
   * @since 0.14
   */
  private ClientEndpointConfig clientConfig;

  /**
   * Create an instance
   */
  public ProxyWebSocketAdapter(final URI backend, final ExecutorService pool) {
    this(backend, pool, null);
  }

  public ProxyWebSocketAdapter(final URI backend, final ExecutorService pool, final ClientEndpointConfig clientConfig) {
    super();
    this.backend = backend;
    this.pool = pool;
    this.clientConfig = clientConfig;
  }

  @Override
  public void onWebSocketConnect(final Session frontEndSession) {

    /*
     * Let's connect to the backend, this is where the Backend-to-frontend
     * plumbing takes place
     */
    container = ContainerProvider.getWebSocketContainer();

    final ProxyInboundClient backendSocket = new ProxyInboundClient(getMessageCallback());

    /* build the configuration */

    /* Attempt Connect */
    try {
      backendSession = container.connectToServer(backendSocket, clientConfig, backend);

      LOG.onConnectionOpen(backend.toString());

    } catch (DeploymentException e) {
      LOG.connectionFailed(e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      LOG.connectionFailed(e);
      throw new RuntimeIOException(e);
    }

    super.onWebSocketConnect(frontEndSession);
    this.frontendSession = frontEndSession;

  }

  @Override
  public void onWebSocketBinary(final byte[] payload, final int offset,
      final int length) {

    if (isNotConnected()) {
      return;
    }

    throw new UnsupportedOperationException(
        "Websocket support for binary messages is not supported at this time.");
  }

  @Override
  public void onWebSocketText(final String message) {

    if (isNotConnected()) {
      return;
    }

    LOG.logMessage("[From Frontend --->]" + message);

    /* Proxy message to backend */
    try {
      backendSession.getBasicRemote().sendText(message);

    } catch (IOException e) {
      LOG.connectionFailed(e);
    }

  }

  @Override
  public void onWebSocketClose(int statusCode, String reason) {
    super.onWebSocketClose(statusCode, reason);

    /* do the cleaning business in seperate thread so we don't block */
    pool.execute(new Runnable() {
      @Override
      public void run() {
        closeQuietly();
      }
    });

    LOG.onConnectionClose(backend.toString());

  }

  @Override
  public void onWebSocketError(final Throwable t) {
    cleanupOnError(t);
  }

  /**
   * Cleanup sessions
   */
  private void cleanupOnError(final Throwable t) {

    LOG.onError(t.toString());
    if (t.toString().contains("exceeds maximum size")) {
      if(frontendSession != null && !frontendSession.isOpen()) {
        frontendSession.close(StatusCode.MESSAGE_TOO_LARGE, t.getMessage());
      }
    }

    else {
      if(frontendSession != null && !frontendSession.isOpen()) {
        frontendSession.close(StatusCode.SERVER_ERROR, t.getMessage());
      }

      /* do the cleaning business in seperate thread so we don't block */
      pool.execute(new Runnable() {
        @Override
        public void run() {
          closeQuietly();
        }
      });

    }
  }

  private MessageEventCallback getMessageCallback() {

    return new MessageEventCallback() {

      @Override
      public void doCallback(String message) {
        /* do nothing */

      }

      @Override
      public void onConnectionOpen(Object session) {
        /* do nothing */

      }

      @Override
      public void onConnectionClose(final CloseReason reason) {
        try {
          frontendSession.close(reason.getCloseCode().getCode(),
              reason.getReasonPhrase());
        } finally {

          /* do the cleaning business in seperate thread so we don't block */
          pool.execute(new Runnable() {
            @Override
            public void run() {
              closeQuietly();
            }
          });

        }

      }

      @Override
      public void onError(Throwable cause) {
        cleanupOnError(cause);
      }

      @Override
      public void onMessageText(String message, Object session) {
        final RemoteEndpoint remote = getRemote();

        LOG.logMessage("[From Backend <---]" + message);

        /* Proxy message to frontend */
        try {
          remote.sendString(message);
          if (remote.getBatchMode() == BatchMode.ON) {
            remote.flush();
          }
        } catch (IOException e) {
          LOG.connectionFailed(e);
          throw new RuntimeIOException(e);
        }

      }

      @Override
      public void onMessageBinary(byte[] message, boolean last,
          Object session) {
        throw new UnsupportedOperationException(
            "Websocket support for binary messages is not supported at this time.");

      }

    };

  }

  private void closeQuietly() {

    try {
      if(backendSession != null && !backendSession.isOpen()) {
        backendSession.close();
      }
    } catch (IOException e) {
      LOG.connectionFailed(e);
    }

    if (container instanceof LifeCycle) {
      try {
        ((LifeCycle) container).stop();
      } catch (Exception e) {
        LOG.connectionFailed(e);
      }
    }

    if(frontendSession != null && !frontendSession.isOpen()) {
      frontendSession.close();
    }

  }

}

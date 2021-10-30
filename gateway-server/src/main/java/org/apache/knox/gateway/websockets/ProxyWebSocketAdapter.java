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
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.config.GatewayConfig;
import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.websocket.api.BatchMode;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import java.security.KeyStore;
/**
 * Handles outbound/inbound Websocket connections and sessions.
 *
 * @since 0.10
 */
public class ProxyWebSocketAdapter extends WebSocketAdapter {
  protected static final WebsocketLogMessages LOG = MessagesFactory.get(WebsocketLogMessages.class);

  /* URI for the backend */
  private final URI backend;

  /* Session between the frontend (browser) and Knox */
  private Session frontendSession;

  /* Session between the backend (outbound) and Knox */
  private javax.websocket.Session backendSession;

  private WebSocketContainer container;

  protected ExecutorService pool;

  /* Message buffer for holding data frames temporarily in memory till connection is setup.
   Keeping the max size of the buffer as 100 messages for now. */
  private List<String> messageBuffer = new ArrayList<>();
  private Lock remoteLock = new ReentrantLock();

  protected final GatewayConfig config;

  /**
   * Used to transmit headers from browser to backend server.
   * @since 0.14
   */
  private ClientEndpointConfig clientConfig;

  public ProxyWebSocketAdapter(final URI backend, final ExecutorService pool, GatewayConfig config) {
    this(backend, pool, null, config);
  }

  public ProxyWebSocketAdapter(final URI backend, final ExecutorService pool, final ClientEndpointConfig clientConfig,
                               GatewayConfig config) {
    super();
    this.backend = backend;
    this.pool = pool;
    this.clientConfig = clientConfig;
    this.config = config;
  }

  @Override
  public void onWebSocketConnect(final Session frontEndSession) {
    /*
     * Let's connect to the backend, this is where the Backend-to-frontend
     * plumbing takes place
     */
    container = ContainerProvider.getWebSocketContainer();
    container.setDefaultMaxTextMessageBufferSize(frontEndSession.getPolicy().getMaxTextMessageBufferSize());
    container.setDefaultMaxBinaryMessageBufferSize(frontEndSession.getPolicy().getMaxBinaryMessageBufferSize());
    container.setAsyncSendTimeout(frontEndSession.getPolicy().getAsyncWriteTimeout());
    container.setDefaultMaxSessionIdleTimeout(frontEndSession.getPolicy().getIdleTimeout());

    KeyStore ks = null;
    if(clientConfig != null) {
      ks = (KeyStore) clientConfig.getUserProperties().get("org.apache.knox.gateway.websockets.truststore");
    }

    /*
       Currently javax.websocket API has no provisions to configure SSL
       https://github.com/eclipse-ee4j/websocket-api/issues/210
       Until that gets fixed we'll have to resort to this.
    */
    if(container instanceof org.eclipse.jetty.websocket.jsr356.ClientContainer &&
        ((org.eclipse.jetty.websocket.jsr356.ClientContainer)container).getClient() != null &&
        ((org.eclipse.jetty.websocket.jsr356.ClientContainer)container).getClient().getSslContextFactory() != null ) {
      ((org.eclipse.jetty.websocket.jsr356.ClientContainer)container).getClient().getHttpClient().getSslContextFactory().setTrustStore(ks);
      LOG.logMessage("Truststore for websocket setup");
    }

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

    remoteLock.lock();
    super.onWebSocketConnect(frontEndSession);
    this.frontendSession = frontEndSession;

    final RemoteEndpoint remote = frontEndSession.getRemote();
    try {
      if (!messageBuffer.isEmpty()) {
        flushBufferedMessages(remote);

        if (remote.getBatchMode() == BatchMode.ON) {
          remote.flush();
        }
      } else {
        LOG.debugLog("Message buffer is empty");
      }
    } catch (IOException e) {
      LOG.connectionFailed(e);
      throw new RuntimeIOException(e);
    }
    finally
    {
      remoteLock.unlock();
    }
  }

  @Override
  public void onWebSocketBinary(final byte[] payload, final int offset, final int length) {
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
    cleanup();
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
      cleanup();
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
          cleanup();
        }
      }

      @Override
      public void onError(Throwable cause) {
        cleanupOnError(cause);
      }

      @Override
      public void onMessageText(String message, Object session) {
        LOG.logMessage("[From Backend <---]" + message);
        remoteLock.lock();
        final RemoteEndpoint remote = getRemote();
        try {
          if (remote == null) {
            LOG.debugLog("Remote endpoint is null");
            if (messageBuffer.size() >= config.getWebsocketMaxWaitBufferCount()) {
              throw new RuntimeIOException("Remote is null and message buffer is full. Cannot buffer anymore ");
            }
            LOG.debugLog("Buffering message: " + message);
            messageBuffer.add(message);
            return;
          }

          /* Proxy message to frontend */
          flushBufferedMessages(remote);

          LOG.debugLog("Sending current message [From Backend <---]: " + message);
          remote.sendString(message);
          if (remote.getBatchMode() == BatchMode.ON) {
            remote.flush();
          }
        } catch (IOException e) {
          LOG.connectionFailed(e);
          throw new RuntimeIOException(e);
        }
        finally
        {
          remoteLock.unlock();
        }
      }

      @Override
      public void onMessageBinary(byte[] message, boolean last,
          Object session) {
        throw new UnsupportedOperationException(
            "Websocket support for binary messages is not supported at this time.");

      }

      @Override
      public void onMessagePong(javax.websocket.PongMessage message, Object session) {
        LOG.logMessage("[From Backend <---]: PING");
        remoteLock.lock();
        final RemoteEndpoint remote = getRemote();
        try {
          if (remote == null) {
            LOG.debugLog("Remote endpoint is null");
            return;
          }

          /* Proxy Ping message to frontend */
          flushBufferedMessages(remote);

          LOG.logMessage("Sending current PING [From Backend <---]: ");
          remote.sendPing(message.getApplicationData());
          if (remote.getBatchMode() == BatchMode.ON) {
            remote.flush();
          }
        } catch (IOException e) {
          LOG.connectionFailed(e);
          throw new RuntimeIOException(e);
        }
        finally
        {
          remoteLock.unlock();
        }
      }

    };

  }

  @SuppressWarnings("PMD.DoNotUseThreads")
  private void cleanup() {
    /* do the cleaning business in separate thread so we don't block */
    pool.execute(new Runnable() {
      @Override
      public void run() {
        closeQuietly();
      }
    });
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

  /*
   * Function to flush buffered messages. Should be called with remoteLock held
   */
  private void flushBufferedMessages(final RemoteEndpoint remote) throws IOException {
    LOG.debugLog("Flushing old buffered messages");
    for(String obj:messageBuffer) {
      LOG.debugLog("Sending old buffered message [From Backend <---]: " + obj);
      remote.sendString(obj);
    }
    messageBuffer.clear();
  }
}

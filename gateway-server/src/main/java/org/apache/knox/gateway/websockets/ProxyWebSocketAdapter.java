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
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.ee8.websocket.javax.client.JavaxWebSocketClientContainerProvider;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;

/**
 * Handles outbound/inbound Websocket connections and sessions.
 *
 * <p>The frontend (browser to Knox) side uses the native Jetty 12
 * WebSocket API via {@link Session.Listener.AbstractAutoDemanding}. The
 * backend (Knox to downstream) side stays on JSR-356 ({@code javax.websocket}).
 *
 * @since 0.10
 */

/**
 * NOTE: Jetty 12 forbids starting a new send on a session before the previous one has completed.
 * This adapter proxies in both directions and enforces that contract in each direction:
 * <p>
 * Frontend -> backend: the frontend side stays on AbstractAutoDemanding, whose auto-demand governs
 * inbound frontend frames only. onWebSocketText forwards each frame with a *blocking* JSR-356 send
 * to the backend, so the next frontend frame is not demanded until that send returns.
 *
 * Backend -> frontend: backend messages are delivered serially by the JSR-356 client, and each is
 * written to the frontend through sendToFrontendBlocking(), which waits for the send callback before
 * the next backend frame is processed. That guarantees at most one send in flight on frontendSession
 * and restores the send failure handling the earlier Callback.NOOP sends silently discarded.
 * <p>
 */
public class ProxyWebSocketAdapter extends Session.Listener.AbstractAutoDemanding {
  protected static final WebsocketLogMessages LOG = MessagesFactory.get(WebsocketLogMessages.class);

  /** URI for the backend */
  private final URI backend;

  /**
   * Session between the frontend (browser) and Knox. volatile because it is
   * written in onWebSocketOpen and read from other threads (the JSR-356 backend
   * callbacks, the async cleanup task) without always holding {@link #remoteLock}.
   */
  @SuppressWarnings("PMD.AvoidUsingVolatile")
  private volatile Session frontendSession;

  /**
   * Ensures the teardown in {@link #closeQuietly()} is scheduled exactly once no
   * matter how many callbacks reach {@link #cleanup()} (frontend close/error,
   * backend close/error), so the sessions and container are never closed twice.
   */
  private final AtomicBoolean closed = new AtomicBoolean(false);

  /** Session between the backend (outbound) and Knox */
  private javax.websocket.Session backendSession;

  /** JSR-356 client container used to connect to the backend */
  private WebSocketContainer container;

  protected ExecutorService pool;

  /**
   * Buffer for messages arriving from the backend before the frontend session
   * is ready. Capped by {@code config.getWebsocketMaxWaitBufferCount()}.
   */
  private final List<String> messageBuffer = new ArrayList<>();

  /** Guards the buffer-vs-send transition at open time. */
  private final Lock remoteLock = new ReentrantLock();

  protected final GatewayConfig config;

  /**
   * Used to transmit headers from browser to backend server.
   * @since 0.14
   */
  private final ClientEndpointConfig clientConfig;

  public ProxyWebSocketAdapter(final URI backend, final ExecutorService pool, GatewayConfig config) {
    this(backend, pool, null, config);
  }

  public ProxyWebSocketAdapter(final URI backend,
                               final ExecutorService pool,
                               final ClientEndpointConfig clientConfig,
                               final GatewayConfig config) {
    super();
    this.backend = backend;
    this.pool = pool;
    this.clientConfig = clientConfig;
    this.config = config;
  }

  @Override
  public void onWebSocketOpen(final Session frontEndSession) {
    /*
     * Let's connect to the backend. This is where the Backend-to-Frontend
     * plumbing takes place.
     */
    container = buildBackendContainer();

    /*
     * Seed sensible defaults on the outbound (JSR-356) container from the
     * inbound session. In Jetty 12 the payload size / idle timeout knobs live
     * on the server container (not the session) and are propagated here so
     * that the backend connection honors the same limits.
     */
    container.setDefaultMaxTextMessageBufferSize(config.getWebsocketMaxTextMessageBufferSize());
    container.setDefaultMaxBinaryMessageBufferSize(config.getWebsocketMaxBinaryMessageBufferSize());
    container.setAsyncSendTimeout(config.getWebsocketAsyncWriteTimeout());
    container.setDefaultMaxSessionIdleTimeout(config.getWebsocketIdleTimeout());

    final ProxyInboundClient backendSocket = new ProxyInboundClient(getMessageCallback());

    /*
     * The backend endpoint is programmatic (no @ClientEndpoint), so the JSR-356
     * container requires an explicit ClientEndpointConfig. Use the one supplied
     * by the frontend upgrade (which carries any truststore / mTLS identity), or
     * an empty default when none was provided (e.g. plain ws:// backends).
     */
    final ClientEndpointConfig backendConfig =
        clientConfig != null ? clientConfig : ClientEndpointConfig.Builder.create().build();

    /* Attempt Connect */
    try {
      backendSession = container.connectToServer(backendSocket, backendConfig, backend);
      LOG.onConnectionOpen(backend.toString());
    } catch (DeploymentException e) {
      LOG.connectionFailed(e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      LOG.connectionFailed(e);
      throw new UncheckedIOException(e);
    }

    remoteLock.lock();
    try {
      this.frontendSession = frontEndSession;
      if (!messageBuffer.isEmpty()) {
        flushBufferedMessages();
      } else {
        LOG.debugLog("Message buffer is empty");
      }
    } finally {
      remoteLock.unlock();
    }
  }

  /**
   * Build the JSR-356 client container and, if a truststore was supplied,
   * back it with an {@link HttpClient} whose SSL context trusts those certs.
   *
   * <p>In Jetty 12 the old {@code org.eclipse.jetty.websocket.jsr356.ClientContainer}
   * cast is no longer available; the supported way to configure SSL for the
   * JSR-356 client is to hand {@link JavaxWebSocketClientContainerProvider}
   * a pre-configured {@link HttpClient}.
   */
  private WebSocketContainer buildBackendContainer() {
    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
    /*
     * A ClientEndpointConfig is only supplied when the frontend upgrade carried
     * SSL material (truststore / client identity for mTLS). When it is absent
     * there is nothing to configure from it — guard against a null clientConfig,
     * mirroring the null-safety the pre-Jetty-12 implementation had.
     */
    if (clientConfig != null) {
      configureSsl(sslContextFactory, clientConfig);
    }
    HttpClient httpClient = new HttpClient();
    httpClient.setSslContextFactory(sslContextFactory);
    LOG.logMessage("Truststore for websocket setup");
    return JavaxWebSocketClientContainerProvider.getContainer(httpClient);
  }


  /**
   * Configures the WebSocket client's SslContextFactory from the values the
   * handler placed in the ClientEndpointConfig user properties: the truststore
   * (unchanged behavior, may be null) and, when two-way SSL supplied one, the
   * client identity keystore plus its key-manager password.
   */
  static void configureSsl(final SslContextFactory.Client sslContextFactory,
                           final ClientEndpointConfig clientConfig) {
    final Map<String, Object> userProperties = clientConfig.getUserProperties();

    sslContextFactory.setTrustStore(
        (KeyStore) userProperties.get(KnoxWebSocketCreator.TRUSTSTORE_USER_PROPERTY));

    final KeyStore identityKeystore =
        (KeyStore) userProperties.get(KnoxWebSocketCreator.KEYSTORE_USER_PROPERTY);
    if (identityKeystore != null) {
      sslContextFactory.setKeyStore(identityKeystore);
      final char[] passphrase =
          (char[]) userProperties.get(KnoxWebSocketCreator.KEYSTORE_KEY_PASSPHRASE_USER_PROPERTY);
      if (passphrase != null) {
        sslContextFactory.setKeyManagerPassword(new String(passphrase));
      }
    }
  }

  @Override
  public void onWebSocketText(final String message) {
    if (frontendSession == null || !frontendSession.isOpen()) {
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
  public void onWebSocketBinary(final ByteBuffer payload, final Callback callback) {
    if (frontendSession == null || !frontendSession.isOpen()) {
      callback.succeed();
      return;
    }
    callback.fail(new UnsupportedOperationException(
    "Websocket support for binary messages is not supported at this time."));
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
   * Cleanup sessions on error.
   */
  private void cleanupOnError(final Throwable t) {
    LOG.onError(t.toString());
    if (t.toString().contains("exceeds maximum size")) {
      if (frontendSession != null && frontendSession.isOpen()) {
        frontendSession.close(StatusCode.MESSAGE_TOO_LARGE, t.getMessage(), Callback.NOOP);
      }
    } else {
      if (frontendSession != null && frontendSession.isOpen()) {
        frontendSession.close(StatusCode.SERVER_ERROR, t.getMessage(), Callback.NOOP);
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
          if (frontendSession != null && frontendSession.isOpen()) {
            frontendSession.close(reason.getCloseCode().getCode(),
            reason.getReasonPhrase(), Callback.NOOP);
          }
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
        try {
          if (frontendSession == null) {
            LOG.debugLog("Frontend session is null");
            if (messageBuffer.size() >= config.getWebsocketMaxWaitBufferCount()) {
              throw new UncheckedIOException(new IOException(
              "Frontend session is not ready and message buffer is full."));
            }
            LOG.debugLog("Buffering message: " + message);
            messageBuffer.add(message);
            return;
          }

          /* Proxy message to frontend */
          flushBufferedMessages();

          LOG.debugLog("Sending current message [From Backend <---]: " + message);
          sendToFrontendBlocking(cb -> frontendSession.sendText(message, cb));
        } finally {
          remoteLock.unlock();
        }
      }

      @Override
      public void onMessageBinary(byte[] message, boolean last, Object session) {
        throw new UnsupportedOperationException(
        "Websocket support for binary messages is not supported at this time.");
      }

      @Override
      public void onMessagePong(javax.websocket.PongMessage message, Object session) {
        LOG.logMessage("[From Backend <---]: PONG");
        remoteLock.lock();
        try {
          if (frontendSession == null) {
            LOG.debugLog("Frontend session is null");
            return;
          }

          /* Proxy Pong message to frontend */
          flushBufferedMessages();

          LOG.logMessage("Sending current PING [From Backend <---]: ");
          sendToFrontendBlocking(cb -> frontendSession.sendPing(message.getApplicationData(), cb));
        } finally {
          remoteLock.unlock();
        }
      }
    };
  }

  @SuppressWarnings("PMD.DoNotUseThreads")
  private void cleanup() {
    /*
     * Teardown can be triggered from more than one callback (frontend close,
     * frontend error, backend close, backend error). compareAndSet lets only the
     * first caller schedule closeQuietly, so the sessions and container are never
     * closed/stopped twice.
     */
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    /* do the cleaning business in a separate thread so we don't block */
    pool.execute(this::closeQuietly);
  }

  /**
   * Performs one send on the frontend session and blocks until it completes.
   *
   * <p>Jetty 12's WebSocket API forbids starting a new send before the previous
   * one has finished. Backend messages are delivered serially by the JSR-356
   * client, so waiting here for the send callback guarantees at most one send is
   * ever in flight on {@link #frontendSession} and applies backpressure to the
   * backend, the next backend frame is not processed until this send lands. On
   * failure the cause is rethrown so the JSR-356 container routes it to
   * {@link MessageEventCallback#onError} and the proxy is torn down.
   *
   * @param sendOp issues the actual send, wiring in the supplied completion callback
   */
  private void sendToFrontendBlocking(final Consumer<Callback> sendOp) {
    final CompletableFuture<Void> sent = new CompletableFuture<>();
    sendOp.accept(Callback.from(() -> sent.complete(null), sent::completeExceptionally));
    try {
      sent.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UncheckedIOException(new IOException("Interrupted while sending message to frontend", e));
    } catch (ExecutionException e) {
      final Throwable cause = e.getCause();
      throw new UncheckedIOException(cause instanceof IOException
          ? (IOException) cause
          : new IOException("Error sending message to frontend", cause));
    }
  }

  private void closeQuietly() {
    try {
      if (backendSession != null && backendSession.isOpen()) {
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

    if (frontendSession != null && frontendSession.isOpen()) {
      frontendSession.close(StatusCode.NORMAL, null, Callback.NOOP);
    }
  }

  /**
   * Flush buffered messages to the frontend session. Must be called while
   * holding {@link #remoteLock}.
   */
  private void flushBufferedMessages() {
    if (messageBuffer.isEmpty()) {
      return;
    }
    LOG.debugLog("Flushing old buffered messages");
    for (String buffered : messageBuffer) {
      LOG.debugLog("Sending old buffered message [From Backend <---]: " + buffered);
      sendToFrontendBlocking(cb -> frontendSession.sendText(buffered, cb));
    }
    messageBuffer.clear();
  }
}

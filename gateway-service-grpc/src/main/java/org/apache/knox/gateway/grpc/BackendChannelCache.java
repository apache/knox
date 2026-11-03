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

import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.TrustManagerFactory;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.KeystoreService;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

/**
 * Keeps one {@link ManagedChannel} per backend URL, shared by every call routed
 * to that backend.
 * <p>
 * gRPC channels multiplex concurrent calls over a pooled HTTP/2 connection and
 * are designed to be long-lived, so creating one per RPC would be both slower
 * and wasteful of connections. Channels go idle on their own after
 * {@code gateway.grpc.channel.idle.timeout} and reconnect transparently
 * when used again, so a cached entry for an unused backend costs nothing.
 */
public class BackendChannelCache {

  private static final GrpcGatewayMessages LOG = MessagesFactory.get(GrpcGatewayMessages.class);

  private static final String PLAINTEXT_SCHEME = "grpc";
  private static final String TLS_SCHEME = "grpcs";

  private final GrpcListenerSettings settings;
  private final GatewayServices services;
  private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

  public BackendChannelCache(GrpcListenerSettings settings, GatewayServices services) {
    this.settings = settings;
    this.services = services;
  }

  /**
   * Returns the shared channel for the given backend URL, creating it if this is
   * the first call to that backend.
   *
   * @param backendUrl a {@code grpc://host:port} or {@code grpcs://host:port} URL
   * @return the channel for that backend
   * @throws io.grpc.StatusRuntimeException if the URL is unusable or TLS cannot be set up
   */
  public ManagedChannel getChannel(String backendUrl) {
    return channels.computeIfAbsent(backendUrl, this::createChannel);
  }

  private ManagedChannel createChannel(String backendUrl) {
    final URI uri = parse(backendUrl);
    final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    final String host = uri.getHost();
    final int port = uri.getPort();

    if (host == null || port < 0) {
      throw Status.FAILED_PRECONDITION
          .withDescription("The backend URL must include a host and port: " + backendUrl)
          .asRuntimeException();
    }

    final NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port)
        .maxInboundMessageSize(settings.getMaxMessageSize())
        .idleTimeout(settings.getChannelIdleTimeoutMillis(), TimeUnit.MILLISECONDS);

    if (TLS_SCHEME.equals(scheme)) {
      builder.negotiationType(NegotiationType.TLS);
      try {
        builder.sslContext(GrpcSslContexts.forClient().trustManager(backendTrustManagers()).build());
      } catch (Exception e) {
        LOG.failedToBuildBackendTls(backendUrl, e);
        throw Status.UNAVAILABLE
            .withDescription("Cannot establish TLS to the backend")
            .withCause(e)
            .asRuntimeException();
      }
    } else if (PLAINTEXT_SCHEME.equals(scheme)) {
      builder.negotiationType(NegotiationType.PLAINTEXT);
    } else {
      throw Status.FAILED_PRECONDITION
          .withDescription("The backend URL scheme must be grpc:// or grpcs://, got: " + backendUrl)
          .asRuntimeException();
    }

    LOG.openedBackendChannel(backendUrl);
    return builder.build();
  }

  /**
   * Trust material for the backend leg: the HTTP client truststore if the
   * deployment configured one, otherwise the gateway keystore. This is the same
   * fallback the WebSocket handler applies, so a deployment that already trusts
   * its backends over {@code wss://} needs no extra configuration here.
   */
  private TrustManagerFactory backendTrustManagers() throws Exception {
    final KeystoreService keystoreService = services.getService(ServiceType.KEYSTORE_SERVICE);
    KeyStore truststore = keystoreService.getTruststoreForHttpClient();
    if (truststore == null) {
      truststore = keystoreService.getKeystoreForGateway();
    }
    final TrustManagerFactory factory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init(truststore);
    return factory;
  }

  private static URI parse(String backendUrl) {
    try {
      return new URI(backendUrl);
    } catch (URISyntaxException e) {
      throw Status.FAILED_PRECONDITION
          .withDescription("Malformed backend URL: " + backendUrl)
          .withCause(e)
          .asRuntimeException();
    }
  }

  /**
   * Shuts every cached channel down, waiting up to the given deadline in total
   * for in-flight calls to finish.
   *
   * @param timeoutMillis total time to wait for all channels to terminate
   */
  public void shutdown(long timeoutMillis) {
    final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    for (Map.Entry<String, ManagedChannel> entry : channels.entrySet()) {
      entry.getValue().shutdown();
    }
    for (Map.Entry<String, ManagedChannel> entry : channels.entrySet()) {
      final long remaining = deadline - System.nanoTime();
      try {
        if (remaining <= 0 || !entry.getValue().awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
          entry.getValue().shutdownNow();
        }
      } catch (InterruptedException e) {
        entry.getValue().shutdownNow();
        Thread.currentThread().interrupt();
      }
      LOG.closedBackendChannel(entry.getKey());
    }
    channels.clear();
  }
}

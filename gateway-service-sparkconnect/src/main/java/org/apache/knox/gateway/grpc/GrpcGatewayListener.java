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

import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.protocol.ProtocolListener;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;

import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;

/**
 * A gRPC listener: a Netty server on its own port, wired to Knox's identity,
 * token, topology and audit services.
 * <p>
 * It is a separate socket rather than a route on the gateway's existing
 * connectors because gRPC requires HTTP/2 negotiated over ALPN, and Knox's Jetty
 * connectors are HTTP/1.1 only. Beyond the transport, the servlet pipeline could
 * not carry these calls anyway: Servlet 3.1 has no trailer API, and gRPC puts
 * {@code grpc-status} — and, for Spark Connect, structured error details — in
 * trailers.
 *
 * <h2>Why this is abstract</h2>
 * Almost everything a gRPC gateway needs is protocol-agnostic: the transport,
 * TLS from the gateway identity, bearer authentication, coarse authorization,
 * topology routing, backend channel caching, auditing, graceful drain, and the
 * byte-level relay itself. Only message-body handling — identity assertion and
 * per-RPC gating — needs to know what is being proxied.
 * <p>
 * Keeping that split explicit means a generic gRPC gateway would later be a
 * configuration-and-documentation exercise rather than an engineering one. But
 * Knox does <strong>not</strong> offer one today, and this class is deliberately
 * not a way to get one: it is abstract, there is no configuration property that
 * selects a listener generically, and the only concrete subclass is the Spark
 * Connect one. A generic offering would need its own service-to-role mapping,
 * default-deny posture, and an honest account of what byte-level proxying cannot
 * enforce — none of which is in scope here.
 * <p>
 * Subclasses supply four things: what to call the listener, which Knox service
 * role backs it, the typed handlers, and which proto services may fall back to
 * byte-level relay.
 */
// volatile: the lifecycle fields are written by the thread calling start/stop and
// read by request threads, so they need visibility but not mutual exclusion.
@SuppressWarnings("PMD.AvoidUsingVolatile")
public abstract class GrpcGatewayListener implements ProtocolListener {

  private static final GrpcGatewayMessages LOG = MessagesFactory.get(GrpcGatewayMessages.class);

  private volatile Server server;
  private volatile BackendChannelCache channelCache;
  private volatile AuthorizationInterceptor authorizationInterceptor;
  private volatile GrpcListenerSettings settings;

  /**
   * The Knox service role backing this listener, e.g. {@code SPARKCONNECT}.
   * Topologies declare a service with this role, and its ACLs are keyed on it.
   *
   * @return the service role
   */
  protected abstract String getServiceRole();

  /**
   * Reads this listener's transport settings from gateway configuration.
   * Subclasses own this because they own the configuration properties; the
   * template deliberately does not read {@code GatewayConfig} for transport
   * limits itself.
   *
   * @param config the gateway configuration
   * @return the settings to build the server with
   */
  protected abstract GrpcListenerSettings createSettings(GatewayConfig config);

  /**
   * Builds the typed service definition whose handlers may inspect and rewrite
   * message bodies.
   *
   * @param channels supplies the backend channel for the call in flight
   * @param headers rewrites metadata for the backend leg
   * @return the service definition to register
   */
  protected abstract ServerServiceDefinition bindService(BackendChannelProvider channels,
                                                        HeaderRewriter headers);

  /**
   * Fully qualified proto service names whose unregistered methods may still be
   * relayed as opaque bytes. Anything not named here is answered
   * {@code UNIMPLEMENTED}, so this is a closed list rather than an opt-out.
   *
   * @return the proto service names eligible for byte-level passthrough
   */
  protected abstract Set<String> getPassthroughServiceNames();

  @Override
  public void start(GatewayConfig config, GatewayServices services) throws Exception {
    final GrpcListenerSettings listenerSettings = createSettings(config);
    this.settings = listenerSettings;

    final BackendChannelCache channels = new BackendChannelCache(listenerSettings, services);
    this.channelCache = channels;

    final BackendChannelProvider channelProvider = () -> {
      final GrpcCallContext callContext = GrpcCallContext.current();
      if (callContext == null || callContext.getBackendUrl() == null) {
        throw Status.UNAVAILABLE.withDescription("No backend resolved for this call").asRuntimeException();
      }
      return channels.getChannel(callContext.getBackendUrl());
    };

    // Built once, and validated here rather than on the first call: a bad key
    // name should stop the gateway starting, not surprise the first user.
    final Metadata.Key<String> topologyKey =
        GrpcMetadataKeys.topologyKey(listenerSettings.getTopologyMetadataKey());

    final AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);
    final HeaderRewriter headerRewriter =
        new BackendHeaderRewriter(aliasService, listenerSettings.getBackendTokenAlias(), topologyKey);

    this.authorizationInterceptor = new AuthorizationInterceptor(config, services, getServiceRole());

    // Order is load-bearing: audit wraps everything so even rejected calls are
    // recorded, then identity, then topology selection, then the ACL check that
    // depends on both having succeeded.
    final List<ServerInterceptor> interceptors = Arrays.asList(
        new AuditInterceptor(),
        new AuthenticationInterceptor(new TokenAuthenticator(config, services)),
        new RoutingInterceptor(config, services, getServiceRole(), topologyKey),
        authorizationInterceptor);

    final NettyServerBuilder builder = NettyServerBuilder.forPort(listenerSettings.getPort())
        .maxInboundMessageSize(listenerSettings.getMaxMessageSize())
        .maxConcurrentCallsPerConnection(listenerSettings.getMaxConcurrentCallsPerConnection())
        .permitKeepAliveTime(listenerSettings.getPermitKeepAliveTimeMillis(), TimeUnit.MILLISECONDS)
        .permitKeepAliveWithoutCalls(listenerSettings.isPermitKeepAliveWithoutCalls());

    if (config.isSSLEnabled()) {
      builder.sslContext(buildServerSslContext(config, services));
    } else {
      // A client that sets token= forces use_ssl=true, so this is really a test
      // and development posture; say so rather than let it pass silently.
      LOG.listenerTlsDisabled(listenerSettings.getName());
    }

    builder.addService(intercept(bindService(channelProvider, headerRewriter), interceptors));

    final ProxyCallHandler<byte[], byte[]> passthroughHandler =
        new ProxyCallHandler<>(channelProvider, MessageInterceptor.passthrough(), headerRewriter);
    builder.fallbackHandlerRegistry(new PassthroughHandlerRegistry(
        getPassthroughServiceNames(),
        InterceptorChain.intercept(passthroughHandler, interceptors)));

    try {
      this.server = builder.build().start();
    } catch (Exception e) {
      LOG.failedToStartListener(listenerSettings.getName(), e);
      channels.shutdown(0L);
      this.channelCache = null;
      throw e;
    }
    LOG.startedListener(listenerSettings.getName(), getPort());
    warnIfNoTopologyDeclaresTheRole(listenerSettings, services);
  }

  /**
   * Notes, at debug level, that the listener is running with nothing to route to.
   * <p>
   * Enabling the listener and declaring a backend are separate steps in separate
   * files, so it is possible to do the first and forget the second — but it is
   * equally possible to do the first deliberately and wait. A deployment that
   * enables the listener as a matter of course, and adds a topology only when
   * someone provisions a Spark Connect cluster, is in this state normally and
   * perhaps permanently. That is why this is debug rather than a warning: it
   * helps when someone is asking why calls are refused, without nagging every
   * deployment that is simply waiting.
   */
  private void warnIfNoTopologyDeclaresTheRole(GrpcListenerSettings listenerSettings,
                                               GatewayServices services) {
    final TopologyService topologyService = services.getService(ServiceType.TOPOLOGY_SERVICE);
    if (topologyService == null) {
      return;
    }
    for (Topology topology : topologyService.getTopologies()) {
      for (Service service : topology.getServices()) {
        if (getServiceRole().equals(service.getRole())) {
          return;
        }
      }
    }
    LOG.noTopologyDeclaresService(listenerSettings.getName(), getServiceRole());
  }

  /**
   * Applies the interceptor chain to every method of a service definition. The
   * chain is composed by hand rather than through {@code ServerInterceptors} so
   * the ordering established above is preserved exactly.
   */
  private static ServerServiceDefinition intercept(ServerServiceDefinition service,
                                                   List<ServerInterceptor> interceptors) {
    final ServerServiceDefinition.Builder builder =
        ServerServiceDefinition.builder(service.getServiceDescriptor());
    for (ServerMethodDefinition<?, ?> method : service.getMethods()) {
      builder.addMethod(wrap(method, interceptors));
    }
    return builder.build();
  }

  private static <ReqT, RespT> ServerMethodDefinition<ReqT, RespT> wrap(
      ServerMethodDefinition<ReqT, RespT> method, List<ServerInterceptor> interceptors) {
    return ServerMethodDefinition.create(
        method.getMethodDescriptor(),
        InterceptorChain.intercept(method.getServerCallHandler(), interceptors));
  }

  /**
   * Builds the server's TLS context from the gateway identity — the same key
   * material Jetty presents — so a deployment has one certificate to manage, not
   * two.
   * <p>
   * The identity is copied into a single-entry keystore before building the key
   * manager, so the configured alias is the one presented even when the gateway
   * keystore holds other entries.
   */
  private SslContext buildServerSslContext(GatewayConfig config, GatewayServices services)
      throws Exception {
    try {
      final KeystoreService keystoreService = services.getService(ServiceType.KEYSTORE_SERVICE);
      final AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);

      final String alias = config.getIdentityKeyAlias();
      final char[] passphrase = aliasService.getGatewayIdentityPassphrase();
      final KeyStore gatewayKeystore = keystoreService.getKeystoreForGateway();
      if (gatewayKeystore == null) {
        throw new IllegalStateException("The gateway identity keystore is not available");
      }

      final Key key = gatewayKeystore.getKey(alias, passphrase);
      final Certificate[] chain = gatewayKeystore.getCertificateChain(alias);
      if (!(key instanceof PrivateKey) || chain == null || chain.length == 0) {
        throw new IllegalStateException(
            "The gateway identity keystore has no usable key entry for alias " + alias);
      }

      final KeyStore identity = KeyStore.getInstance("PKCS12");
      identity.load(null, null);
      identity.setKeyEntry(alias, key, passphrase, chain);

      final KeyManagerFactory keyManagers =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagers.init(identity, passphrase);

      // GrpcSslContexts applies the ALPN and cipher requirements of the HTTP/2
      // profile gRPC mandates.
      return GrpcSslContexts.configure(SslContextBuilder.forServer(keyManagers)).build();
    } catch (Exception e) {
      LOG.failedToBuildServerTls(getName(), e);
      throw e;
    }
  }

  /**
   * Stops accepting new calls and lets in-flight ones finish, up to the
   * configured drain timeout.
   * <p>
   * Long-lived streams are severed if they outlast the drain. That is survivable
   * by design: Spark Connect clients already retry through
   * {@code ReattachExecute}, which exists precisely because a connection can drop
   * mid-query.
   */
  @Override
  public void stop() {
    final Server current = server;
    if (current == null) {
      return;
    }
    final GrpcListenerSettings listenerSettings = settings;
    final long drainTimeoutMillis =
        listenerSettings == null ? 0L : listenerSettings.getDrainTimeoutMillis();
    LOG.stoppingListener(getName(), drainTimeoutMillis);
    current.shutdown();
    try {
      if (!current.awaitTermination(drainTimeoutMillis, TimeUnit.MILLISECONDS)) {
        LOG.drainTimedOut(getName(), drainTimeoutMillis);
        current.shutdownNow();
      }
    } catch (InterruptedException e) {
      current.shutdownNow();
      Thread.currentThread().interrupt();
    } finally {
      server = null;
      final BackendChannelCache channels = channelCache;
      if (channels != null) {
        channels.shutdown(drainTimeoutMillis);
        channelCache = null;
      }
      LOG.stoppedListener(getName());
    }
  }

  /** Drops cached topology ACLs so a redeployed topology takes effect. */
  @Override
  public void reload() {
    final AuthorizationInterceptor interceptor = authorizationInterceptor;
    if (interceptor != null) {
      interceptor.invalidate();
    }
  }

  @Override
  public int getPort() {
    final Server current = server;
    return current == null ? -1 : current.getPort();
  }

  /** The settings this listener started with, or null before {@code start}. */
  protected GrpcListenerSettings getSettings() {
    return settings;
  }
}

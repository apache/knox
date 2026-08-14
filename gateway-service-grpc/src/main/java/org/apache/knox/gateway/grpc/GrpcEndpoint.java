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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
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
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;

/**
 * One gRPC listener: a Netty server on one port, with its own TLS identity, its
 * own view of what to proxy, and its own backend channels.
 * <p>
 * A gateway runs one of these per configured listener. They share the gateway's
 * services — tokens, topologies, audit — and route to the same topologies; what
 * distinguishes them is the socket and the certificate presented on it. See
 * {@link GrpcListenerSettingsFactory} for why that separation is worth having.
 */
// volatile: lifecycle and policy fields are written by the thread calling
// start/stop or delivering a configuration change, and read by request threads.
@SuppressWarnings("PMD.AvoidUsingVolatile")
public class GrpcEndpoint {

  private static final GrpcGatewayMessages LOG = MessagesFactory.get(GrpcGatewayMessages.class);

  private final GrpcListenerSettings settings;

  private volatile Server server;
  private volatile BackendChannelCache channelCache;
  private volatile AuthorizationInterceptor authorizationInterceptor;
  private volatile MethodAccessInterceptor methodAccessInterceptor;
  /**
   * Rebuilt when configuration changes. The relay reads this per message rather
   * than capturing it, so a change reaches handlers that already exist.
   */
  private volatile MessageInterceptor<byte[]> messageInterceptor = MessageInterceptor.passthrough();
  /** Read per call, so a changed default topology applies without a restart. */
  private volatile String defaultTopology;
  /** The configuration the running interceptor was built from, for change detection. */
  private volatile String identityRules;
  private volatile int identityScanLimit;

  public GrpcEndpoint(GrpcListenerSettings settings) {
    this.settings = settings;
    this.defaultTopology = settings.getDefaultTopology();
    this.identityRules = settings.getIdentityRules();
    this.identityScanLimit = settings.getIdentityScanLimit();
  }

  public String getName() {
    return settings.getName();
  }

  public GrpcListenerSettings getSettings() {
    return settings;
  }

  public int getPort() {
    final Server current = server;
    return current == null ? -1 : current.getPort();
  }

  /**
   * Binds the port and begins serving.
   *
   * @param config the gateway configuration, for the services shared across
   *        listeners: token validation, admin users, keystores
   * @param services the started gateway services
   * @throws Exception if the listener cannot start
   */
  public void start(GatewayConfig config, GatewayServices services) throws Exception {
    if (settings.getProtoServices().isEmpty()) {
      // Refusing to start beats binding a port that answers UNIMPLEMENTED to
      // everything, which would look like a working listener.
      throw new IllegalStateException("The gRPC listener '" + getName() + "' is enabled but "
          + GrpcListenerSettingsFactory.propertyName(null, "proto.services")
          + " names no proto service to proxy");
    }

    // Parsed here rather than on the first call: a malformed rule must stop the
    // gateway starting, not silently leave identity assertion switched off.
    final IdentityRewritePolicy identityPolicy = createPolicy(settings);
    this.messageInterceptor = createMessageInterceptor(identityPolicy);

    final BackendChannelCache channels = new BackendChannelCache(settings, services);
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
        GrpcMetadataKeys.topologyKey(settings.getTopologyMetadataKey());

    final AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);
    final HeaderRewriter headerRewriter =
        new BackendHeaderRewriter(aliasService, settings.getBackendTokenAlias(), topologyKey);

    final String serviceRole = settings.getServiceRole();
    this.authorizationInterceptor = new AuthorizationInterceptor(config, services, serviceRole);
    this.methodAccessInterceptor = new MethodAccessInterceptor(services, serviceRole,
        MethodAccessPolicy.of(settings.getMethodsDeny(), settings.getMethodsAllow()));

    // Order is load-bearing: audit wraps everything so even rejected calls are
    // recorded, then identity, then topology selection, then the checks that
    // depend on both having succeeded.
    final List<ServerInterceptor> interceptors = Arrays.asList(
        new AuditInterceptor(),
        new AuthenticationInterceptor(new TokenAuthenticator(config, services)),
        new RoutingInterceptor(() -> defaultTopology, services, serviceRole, topologyKey),
        authorizationInterceptor,
        // Coarse method gating needs no schema: gRPC carries the method name in
        // the request path. It runs last so a denial is attributable to a known
        // user in a known topology.
        methodAccessInterceptor);

    final NettyServerBuilder builder = NettyServerBuilder.forPort(settings.getPort())
        .maxInboundMessageSize(settings.getMaxMessageSize())
        .maxConcurrentCallsPerConnection(settings.getMaxConcurrentCallsPerConnection())
        .permitKeepAliveTime(settings.getPermitKeepAliveTimeMillis(), TimeUnit.MILLISECONDS)
        .permitKeepAliveWithoutCalls(settings.isPermitKeepAliveWithoutCalls());

    if (settings.isSslEnabled()) {
      builder.sslContext(buildServerSslContext(config, services));
    } else {
      // Clients that carry a bearer token generally require TLS anyway, so this
      // is really a test and development posture; say so rather than let it pass.
      LOG.listenerTlsDisabled(getName());
    }

    // No generated service is registered. Every call for a proxied proto service
    // reaches the same byte-level relay, and the relay consults the current
    // interceptor per message.
    final MessageInterceptor<byte[]> currentInterceptor = message -> messageInterceptor.intercept(message);
    builder.fallbackHandlerRegistry(new ProxyHandlerRegistry(
        settings.getProtoServices(),
        methodName -> currentInterceptor,
        relay -> InterceptorChain.intercept(
            new ProxyCallHandler<>(channelProvider, relay, headerRewriter), interceptors)));

    try {
      this.server = builder.build().start();
    } catch (Exception e) {
      LOG.failedToStartListener(getName(), e);
      channels.shutdown(0L);
      this.channelCache = null;
      throw e;
    }
    LOG.startedListener(getName(), getPort());
    LOG.proxyingServices(getName(), String.join(", ", settings.getProtoServices()),
        identityPolicy.toString());
    noteIfNoTopologyDeclaresTheRole(services, serviceRole);
  }

  private static IdentityRewritePolicy createPolicy(GrpcListenerSettings settings) {
    return IdentityRewritePolicy.parse(settings.getIdentityRules(), settings.getIdentityScanLimit());
  }

  /**
   * Identity assertion is optional: a protocol whose requests carry no identity
   * field gets a pure relay. Where rules are given, every request has each named
   * field replaced with the authenticated principal.
   */
  private static MessageInterceptor<byte[]> createMessageInterceptor(IdentityRewritePolicy policy) {
    return policy.isEmpty()
        ? MessageInterceptor.passthrough()
        : new IdentityAssertingInterceptor(policy);
  }

  /**
   * Notes, at debug level, that this listener is running with nothing to route to.
   * <p>
   * Enabling a listener and declaring a backend are separate steps in separate
   * files, so it is possible to do the first and forget the second — but it is
   * equally possible to do the first deliberately and wait. A deployment that
   * enables a listener as a matter of course, and adds a topology only when
   * someone provisions a backend, is in this state normally and perhaps
   * permanently. That is why this is debug rather than a warning: it helps when
   * someone is asking why calls are refused, without nagging every deployment
   * that is simply waiting.
   */
  private void noteIfNoTopologyDeclaresTheRole(GatewayServices services, String serviceRole) {
    final TopologyService topologyService = services.getService(ServiceType.TOPOLOGY_SERVICE);
    if (topologyService == null) {
      return;
    }
    for (Topology topology : topologyService.getTopologies()) {
      for (Service service : topology.getServices()) {
        if (serviceRole.equals(service.getRole())) {
          return;
        }
      }
    }
    LOG.noTopologyDeclaresService(getName(), serviceRole);
  }

  /**
   * Builds this listener's TLS context.
   * <p>
   * By default that is the gateway identity — the same key material Jetty
   * presents — so a deployment has one certificate to manage, not several. A
   * listener that configures its own keystore presents that instead, which is
   * what allows several listeners on one gateway to answer for several hostnames
   * with plain single-name certificates.
   * <p>
   * Either way the chosen entry is copied into a single-entry keystore before the
   * key manager is built, so the configured alias is the one presented even when
   * the source keystore holds others.
   */
  private SslContext buildServerSslContext(GatewayConfig config, GatewayServices services)
      throws Exception {
    try {
      final AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);
      final KeyStore source;
      final String alias;
      final char[] passphrase;

      if (settings.getSslKeystorePath() == null) {
        final KeystoreService keystoreService = services.getService(ServiceType.KEYSTORE_SERVICE);
        source = keystoreService.getKeystoreForGateway();
        if (source == null) {
          throw new IllegalStateException("The gateway identity keystore is not available");
        }
        alias = config.getIdentityKeyAlias();
        passphrase = aliasService.getGatewayIdentityPassphrase();
      } else {
        passphrase = keystorePassphrase(aliasService);
        source = loadKeystore(settings.getSslKeystorePath(), settings.getSslKeystoreType(), passphrase);
        alias = keyEntryAlias(source);
      }

      final Key key = source.getKey(alias, passphrase);
      final Certificate[] chain = source.getCertificateChain(alias);
      if (!(key instanceof PrivateKey) || chain == null || chain.length == 0) {
        throw new IllegalStateException("The keystore for gRPC listener '" + getName()
            + "' has no usable key entry for alias " + alias);
      }

      final KeyStore identity = KeyStore.getInstance("PKCS12");
      identity.load(null, null);
      identity.setKeyEntry(alias, key, passphrase, chain);

      final KeyManagerFactory keyManagers =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagers.init(identity, passphrase);

      LOG.listenerTlsIdentity(getName(),
          settings.getSslKeystorePath() == null ? "the gateway identity" : settings.getSslKeystorePath(),
          alias);

      // GrpcSslContexts applies the ALPN and cipher requirements of the HTTP/2
      // profile gRPC mandates.
      return GrpcSslContexts.configure(SslContextBuilder.forServer(keyManagers)).build();
    } catch (Exception e) {
      LOG.failedToBuildServerTls(getName(), e);
      throw e;
    }
  }

  /**
   * The keystore password, from the configured alias. Falling back to the gateway
   * identity passphrase matches what the embedded LDAP server does, and covers
   * the common case of a keystore provisioned alongside the gateway's own.
   */
  private char[] keystorePassphrase(AliasService aliasService) throws Exception {
    final String alias = settings.getSslKeystorePasswordAlias();
    if (alias == null) {
      return aliasService.getGatewayIdentityPassphrase();
    }
    final char[] password = aliasService.getPasswordFromAliasForGateway(alias);
    if (password == null || password.length == 0) {
      throw new IllegalStateException("The keystore password alias '" + alias
          + "' configured for gRPC listener '" + getName() + "' resolves to nothing");
    }
    return password;
  }

  private KeyStore loadKeystore(String path, String type, char[] passphrase) throws Exception {
    if (!Files.isReadable(Paths.get(path))) {
      throw new IllegalStateException("The keystore configured for gRPC listener '" + getName()
          + "' cannot be read: " + path);
    }
    final KeyStore keystore = KeyStore.getInstance(type);
    try (InputStream in = Files.newInputStream(Paths.get(path))) {
      keystore.load(in, passphrase);
    } catch (IOException e) {
      throw new IllegalStateException("The keystore configured for gRPC listener '" + getName()
          + "' could not be loaded; check its type and password: " + path, e);
    }
    return keystore;
  }

  /**
   * The entry to present. A keystore holding exactly one key entry needs no alias
   * configured, which is the usual shape of a per-listener keystore; anything
   * else has to say which, because picking arbitrarily would present a
   * certificate nobody chose.
   */
  private String keyEntryAlias(KeyStore keystore) throws Exception {
    final String configured = settings.getSslKeystoreAlias();
    if (configured != null) {
      if (!keystore.containsAlias(configured)) {
        throw new IllegalStateException("The keystore for gRPC listener '" + getName()
            + "' holds no entry named " + configured);
      }
      return configured;
    }
    final List<String> keyEntries = new ArrayList<>();
    final Enumeration<String> aliases = keystore.aliases();
    while (aliases.hasMoreElements()) {
      final String candidate = aliases.nextElement();
      if (keystore.isKeyEntry(candidate)) {
        keyEntries.add(candidate);
      }
    }
    if (keyEntries.size() == 1) {
      return keyEntries.get(0);
    }
    throw new IllegalStateException("The keystore for gRPC listener '" + getName() + "' holds "
        + keyEntries.size() + " key entries, so "
        + GrpcListenerSettingsFactory.propertyName(getName(), "ssl.keystore.alias")
        + " must name the one to present");
  }

  /**
   * Stops accepting new calls and lets in-flight ones finish, up to the
   * configured drain timeout.
   * <p>
   * Long-lived streams are severed if they outlast the drain. Clients of
   * streaming protocols generally recover, since such protocols usually carry
   * their own reattach or retry mechanism for exactly this case.
   */
  public void stop() {
    final Server current = server;
    if (current == null) {
      return;
    }
    final long drainTimeoutMillis = settings.getDrainTimeoutMillis();
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

  /** Drops cached per-topology policy so a redeployed topology takes effect. */
  public void reload() {
    final AuthorizationInterceptor authz = authorizationInterceptor;
    if (authz != null) {
      authz.invalidate();
    }
    final MethodAccessInterceptor methods = methodAccessInterceptor;
    if (methods != null) {
      methods.invalidate();
    }
  }

  /**
   * Applies a changed {@code gateway-reloadable.xml} to the controls that can
   * move on a running listener.
   * <p>
   * Only the identity rewrite and the default topology are refreshed. The
   * transport settings are built into the bound server and cannot change without
   * a restart, so rather than accept them silently and do nothing — which looks
   * like it worked — any attempt to change one is named in the log.
   *
   * @param updated the settings the refreshed configuration implies for this
   *        listener
   */
  public void onSettingsChanged(GrpcListenerSettings updated) {
    if (!Objects.equals(updated.getDefaultTopology(), defaultTopology)) {
      this.defaultTopology = updated.getDefaultTopology();
      LOG.reloadedPolicy(getName(), "default topology: "
          + (defaultTopology == null ? "none" : defaultTopology));
    }
    if (!Objects.equals(updated.getIdentityRules(), identityRules)
        || updated.getIdentityScanLimit() != identityScanLimit) {
      // Record the new configuration either way, so a rule that cannot be parsed
      // is reported once rather than on every refresh; correcting it changes the
      // value again and is picked up normally.
      this.identityRules = updated.getIdentityRules();
      this.identityScanLimit = updated.getIdentityScanLimit();
      try {
        final IdentityRewritePolicy policy = createPolicy(updated);
        this.messageInterceptor = createMessageInterceptor(policy);
        LOG.reloadedPolicy(getName(), "identity rules: " + policy);
      } catch (RuntimeException e) {
        // Keep the running policy. Switching identity assertion off because
        // someone mistyped a rule is the one outcome worse than ignoring the
        // edit, and throwing here would escape into the configuration refresh
        // task and stop it running again.
        LOG.invalidIdentityRules(getName(), String.valueOf(updated.getIdentityRules()), e);
      }
    }
    warnAboutRestartOnlyChanges(updated);
  }

  private void warnAboutRestartOnlyChanges(GrpcListenerSettings updated) {
    final List<String> changed = new ArrayList<>();
    if (updated.getPort() != settings.getPort()) {
      changed.add("port");
    }
    if (!Objects.equals(updated.getServiceRole(), settings.getServiceRole())) {
      changed.add("service.role");
    }
    if (!Objects.equals(updated.getProtoServices(), settings.getProtoServices())) {
      changed.add("proto.services");
    }
    if (updated.getMaxMessageSize() != settings.getMaxMessageSize()) {
      changed.add("max.message.size");
    }
    if (updated.getMaxConcurrentCallsPerConnection() != settings.getMaxConcurrentCallsPerConnection()) {
      changed.add("max.concurrent.calls.per.connection");
    }
    if (updated.getPermitKeepAliveTimeMillis() != settings.getPermitKeepAliveTimeMillis()) {
      changed.add("permit.keepalive.time");
    }
    if (updated.isPermitKeepAliveWithoutCalls() != settings.isPermitKeepAliveWithoutCalls()) {
      changed.add("permit.keepalive.without.calls");
    }
    if (updated.getChannelIdleTimeoutMillis() != settings.getChannelIdleTimeoutMillis()) {
      changed.add("channel.idle.timeout");
    }
    if (updated.getDrainTimeoutMillis() != settings.getDrainTimeoutMillis()) {
      changed.add("drain.timeout");
    }
    if (!Objects.equals(updated.getBackendTokenAlias(), settings.getBackendTokenAlias())) {
      changed.add("backend.token.alias");
    }
    if (!Objects.equals(updated.getTopologyMetadataKey(), settings.getTopologyMetadataKey())) {
      changed.add("topology.metadata.key");
    }
    if (updated.isSslEnabled() != settings.isSslEnabled()
        || !Objects.equals(updated.getSslKeystorePath(), settings.getSslKeystorePath())
        || !Objects.equals(updated.getSslKeystoreAlias(), settings.getSslKeystoreAlias())
        || !Objects.equals(updated.getSslKeystorePasswordAlias(), settings.getSslKeystorePasswordAlias())
        || !Objects.equals(updated.getSslKeystoreType(), settings.getSslKeystoreType())) {
      changed.add("ssl.*");
    }
    if (!changed.isEmpty()) {
      LOG.restartOnlyConfigChanged(getName(), String.join(", ", changed));
    }
  }

  /** Exposed so tests can drive a configuration change without binding a port. */
  MessageInterceptor<byte[]> currentMessageInterceptor() {
    return messageInterceptor;
  }
}

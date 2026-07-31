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
package org.apache.knox.gateway.sparkconnect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.GatewayConfigChangeListener;
import org.apache.knox.gateway.grpc.BackendChannelProvider;
import org.apache.knox.gateway.grpc.GrpcGatewayListener;
import org.apache.knox.gateway.grpc.GrpcGatewayMessages;
import org.apache.knox.gateway.grpc.GrpcListenerSettings;
import org.apache.knox.gateway.grpc.HeaderRewriter;
import org.apache.knox.gateway.grpc.MessageInterceptor;
import org.apache.knox.gateway.grpc.ProxyCallHandler;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import com.google.protobuf.Message;

import io.grpc.MethodDescriptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;

import org.apache.spark.connect.proto.SparkConnectServiceGrpc;

/**
 * Fronts a Spark Connect server, the one concrete listener the gRPC template
 * offers.
 * <p>
 * Spark Connect is worth fronting because its server has essentially no
 * authentication or authorization of its own — the project assumes a proxy
 * supplies them — while Knox already fronts the surfaces around it. What Knox
 * adds is authentication at the edge, an identity the client cannot forge, an
 * audit trail, and topology-based routing.
 * <p>
 * Clients need no code changes and no plugins. A vanilla connection string
 * carries everything required:
 * <pre>
 * sc://knox-host:15002/;use_ssl=true;token=&lt;knox-jwt&gt;;knox-topology=analytics
 * </pre>
 * {@code token=} becomes a standard bearer header (and forces TLS on), and any
 * parameter the client does not recognise — {@code knox-topology} here — is sent
 * as call metadata, which is what makes topology selection possible despite gRPC
 * forbidding a path in the URL.
 */
// volatile: the message-level policy is captured at start-up and read by request
// threads thereafter.
@SuppressWarnings("PMD.AvoidUsingVolatile")
public class SparkConnectListener extends GrpcGatewayListener implements GatewayConfigChangeListener {

  private static final GrpcGatewayMessages LOG = MessagesFactory.get(GrpcGatewayMessages.class);

  private static final String LISTENER_NAME = "SparkConnect";
  private static final String SERVICE_ROLE = "SPARKCONNECT";
  private static final String PROTO_SERVICE_NAME = "spark.connect.SparkConnectService";

  private static final String CONFIG_METHOD = "Config";
  private static final String ADD_ARTIFACTS_METHOD = "AddArtifacts";

  /**
   * The message-level controls, replaced wholesale when configuration changes.
   * Handlers read it per call rather than capturing a guard, which is what makes
   * a change take effect without rebuilding the gRPC service.
   */
  private volatile SparkConnectPolicy policy;

  @Override
  public String getName() {
    return LISTENER_NAME;
  }

  @Override
  public boolean isEnabled(GatewayConfig config) {
    return config.isSparkConnectEnabled();
  }

  @Override
  protected String getServiceRole() {
    return SERVICE_ROLE;
  }

  @Override
  protected GrpcListenerSettings createSettings(GatewayConfig config) {
    // Message-level policy can change later; the transport settings below cannot,
    // because they are built into the bound server.
    this.policy = SparkConnectPolicy.from(config);

    return new GrpcListenerSettings()
        .name(LISTENER_NAME)
        .port(config.getSparkConnectPort())
        .maxMessageSize(config.getSparkConnectMaxMessageSize())
        .maxConcurrentCallsPerConnection(config.getSparkConnectMaxConcurrentCallsPerConnection())
        .permitKeepAliveTimeMillis(config.getSparkConnectPermitKeepAliveTime())
        .permitKeepAliveWithoutCalls(config.isSparkConnectPermitKeepAliveWithoutCalls())
        .channelIdleTimeoutMillis(config.getSparkConnectChannelIdleTimeout())
        .drainTimeoutMillis(config.getSparkConnectDrainTimeout())
        .topologyMetadataKey(config.getSparkConnectTopologyMetadataKey())
        .backendTokenAlias(config.getSparkConnectBackendTokenAlias());
  }

  @Override
  protected Set<String> getPassthroughServiceNames() {
    // Only methods of the Spark Connect service itself may fall back to a
    // byte-level relay, and only when this build has no typed handler for them —
    // which is how a client from a newer Spark line still gets proxied.
    return Collections.singleton(PROTO_SERVICE_NAME);
  }

  /**
   * Registers a proxy handler for every method of {@code SparkConnectService}.
   * <p>
   * There is one handler implementation rather than one per RPC shape. Unary,
   * server-streaming and client-streaming calls differ only in message counts,
   * which the relay handles uniformly, so the ten-odd methods need no bespoke
   * code — just the right request interceptor each.
   */
  @Override
  protected ServerServiceDefinition bindService(BackendChannelProvider channels,
                                                HeaderRewriter headers) {
    final ServerServiceDefinition.Builder builder =
        ServerServiceDefinition.builder(SparkConnectServiceGrpc.getServiceDescriptor());
    for (MethodDescriptor<?, ?> method : SparkConnectServiceGrpc.getServiceDescriptor().getMethods()) {
      builder.addMethod(proxyMethod(method, channels, headers));
    }
    return builder.build();
  }

  /**
   * Erasure lets one relay serve every method: the generated marshallers still
   * parse each message into its concrete type, and the interceptor only touches
   * fields it looks up by name on the descriptor.
   */
  @SuppressWarnings("unchecked")
  private ServerMethodDefinition<Message, Message> proxyMethod(MethodDescriptor<?, ?> method,
                                                               BackendChannelProvider channels,
                                                               HeaderRewriter headers) {
    final MethodDescriptor<Message, Message> descriptor = (MethodDescriptor<Message, Message>) method;
    final MessageInterceptor<Message> interceptor =
        interceptorFor(bareMethodName(descriptor.getFullMethodName()));
    return ServerMethodDefinition.create(descriptor,
        new ProxyCallHandler<>(channels, interceptor, headers));
  }

  /**
   * The guards indirect through {@link #policy} on every call rather than being
   * captured here. Handlers are registered once when the server is built, so a
   * guard captured at that moment could never be replaced — which is what made
   * these settings silently restart-only before.
   */
  // Package-private so tests can assert that a policy change reaches an
  // interceptor built before the change.
  MessageInterceptor<Message> interceptorFor(String methodName) {
    if (CONFIG_METHOD.equals(methodName)) {
      return new SparkConnectMessageInterceptor(
          (message, principal) -> policy.reservedConfigGuard().check(message, principal));
    }
    if (ADD_ARTIFACTS_METHOD.equals(methodName)) {
      return new SparkConnectMessageInterceptor(
          (message, principal) -> policy.addArtifactsGuard().check(message, principal));
    }
    return new SparkConnectMessageInterceptor(null);
  }

  /**
   * Applies a changed {@code gateway-reloadable.xml} to the controls that can
   * move on a running gateway.
   * <p>
   * Only the message-level policy is refreshed. The transport settings are built
   * into the bound server and cannot change without a restart, so rather than
   * accept them silently and do nothing — which looks like it worked — any
   * attempt to change one is named in the log.
   */
  @Override
  public void onGatewayConfigChanged(GatewayConfig config) {
    final SparkConnectPolicy updated = SparkConnectPolicy.from(config);
    if (updated.differsFrom(policy)) {
      this.policy = updated;
      LOG.reloadedPolicy(LISTENER_NAME, updated.toString());
    }
    warnAboutRestartOnlyChanges(config);
  }

  private void warnAboutRestartOnlyChanges(GatewayConfig config) {
    final GrpcListenerSettings running = getSettings();
    if (running == null) {
      return;
    }
    final List<String> changed = new ArrayList<>();
    if (config.getSparkConnectPort() != running.getPort()) {
      changed.add("port");
    }
    if (config.getSparkConnectMaxMessageSize() != running.getMaxMessageSize()) {
      changed.add("max.message.size");
    }
    if (config.getSparkConnectMaxConcurrentCallsPerConnection()
        != running.getMaxConcurrentCallsPerConnection()) {
      changed.add("max.concurrent.calls.per.connection");
    }
    if (config.getSparkConnectPermitKeepAliveTime() != running.getPermitKeepAliveTimeMillis()) {
      changed.add("permit.keepalive.time");
    }
    if (config.isSparkConnectPermitKeepAliveWithoutCalls() != running.isPermitKeepAliveWithoutCalls()) {
      changed.add("permit.keepalive.without.calls");
    }
    if (config.getSparkConnectChannelIdleTimeout() != running.getChannelIdleTimeoutMillis()) {
      changed.add("channel.idle.timeout");
    }
    if (config.getSparkConnectDrainTimeout() != running.getDrainTimeoutMillis()) {
      changed.add("drain.timeout");
    }
    if (!Objects.equals(config.getSparkConnectBackendTokenAlias(), running.getBackendTokenAlias())) {
      changed.add("backend.token.alias");
    }
    if (!changed.isEmpty()) {
      LOG.restartOnlyConfigChanged(LISTENER_NAME, String.join(", ", changed));
    }
  }

  private static String bareMethodName(String fullMethodName) {
    final int separator = fullMethodName.lastIndexOf('/');
    return separator < 0 ? fullMethodName : fullMethodName.substring(separator + 1);
  }
}

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.GatewayConfigChangeListener;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.protocol.ProtocolListener;
import org.apache.knox.gateway.services.GatewayServices;

/**
 * The gateway's gRPC listeners: one or more Netty servers on their own ports,
 * wired to Knox's identity, token, topology and audit services.
 * <p>
 * They are separate sockets rather than routes on the gateway's existing
 * connectors because gRPC requires HTTP/2 negotiated over ALPN, and Knox's Jetty
 * connectors are HTTP/1.1 only. Beyond the transport, the servlet pipeline could
 * not carry these calls anyway: Servlet 3.1 has no trailer API, and gRPC puts
 * {@code grpc-status} — and often structured error details — in trailers.
 *
 * <h2>No schema, anywhere</h2>
 * This compiles against no {@code .proto} file and no generated class. Calls are
 * relayed as opaque bytes for whatever proto services a deployment names, and the
 * one thing that needs to look inside a message — replacing the caller's claimed
 * identity with the authenticated one — is done by field number on the wire.
 * Field numbers are the part of a protobuf schema that cannot change without
 * breaking every deployed client, so the gateway tracks no particular version of
 * anything.
 * <p>
 * What a deployment supplies is therefore configuration rather than code: which
 * proto services to front, which Knox service role ties them to a topology,
 * where the identity lives, and which RPCs to refuse. The protocol this was
 * built for runs through the documentation, but only ever as values.
 *
 * <h2>Why more than one</h2>
 * Each listener routes to as many topologies as its clients select, so several
 * listeners are not a way to separate policy — topology selection already does
 * that. They exist because TLS identity is per-socket: a listener each lets a
 * gateway answer for several hostnames with plain single-name certificates,
 * which is the only option where the platform PKI cannot issue multi-name ones.
 * A deployment that names no listeners runs exactly one, configured from the
 * plain {@code gateway.grpc.*} properties.
 */
public class GrpcListener implements ProtocolListener, GatewayConfigChangeListener {

  private static final GrpcGatewayMessages LOG = MessagesFactory.get(GrpcGatewayMessages.class);

  private final List<GrpcEndpoint> endpoints = new ArrayList<>();
  /** Started listeners by name, so a configuration change reaches the right one. */
  private final Map<String, GrpcEndpoint> byName = new ConcurrentHashMap<>();

  @Override
  public String getName() {
    return "gRPC";
  }

  @Override
  public boolean isEnabled(GatewayConfig config) {
    return config.isGrpcEnabled();
  }

  /**
   * Starts every configured listener.
   * <p>
   * One failing stops the gateway, and the listeners already started are stopped
   * first: a gateway that came up serving half the endpoints an operator
   * configured would be worse than one that refused to come up at all, because
   * the missing half looks like a network fault from the outside.
   */
  @Override
  public void start(GatewayConfig config, GatewayServices services) throws Exception {
    for (GrpcListenerSettings settings : GrpcListenerSettingsFactory.create(config)) {
      final GrpcEndpoint endpoint = new GrpcEndpoint(settings);
      try {
        endpoint.start(config, services);
      } catch (Exception e) {
        stop();
        throw e;
      }
      endpoints.add(endpoint);
      byName.put(settings.getName(), endpoint);
    }
  }

  @Override
  public void stop() {
    for (GrpcEndpoint endpoint : endpoints) {
      endpoint.stop();
    }
    endpoints.clear();
    byName.clear();
  }

  @Override
  public void reload() {
    for (GrpcEndpoint endpoint : endpoints) {
      endpoint.reload();
    }
  }

  /**
   * Hands each running listener the settings the refreshed configuration implies
   * for it.
   * <p>
   * Which listeners exist is fixed at startup, like whether the feature runs at
   * all: a name added or removed in a running gateway is reported rather than
   * acted on, since binding or releasing a port is exactly the kind of change an
   * operator should schedule.
   */
  @Override
  public void onGatewayConfigChanged(GatewayConfig config) {
    final List<GrpcListenerSettings> updated;
    try {
      updated = GrpcListenerSettingsFactory.create(config);
    } catch (RuntimeException e) {
      LOG.invalidListenerConfiguration(getName(), e);
      return;
    }
    final List<String> unknown = new ArrayList<>();
    for (GrpcListenerSettings settings : updated) {
      final GrpcEndpoint endpoint = byName.get(settings.getName());
      if (endpoint == null) {
        unknown.add(settings.getName());
      } else {
        endpoint.onSettingsChanged(settings);
      }
    }
    if (!unknown.isEmpty()) {
      LOG.listenerSetChanged(getName(), String.join(", ", unknown));
    }
  }

  /**
   * @return the port of the first listener, for the gateway's startup log; see
   *         {@link #getPorts()} for all of them
   */
  @Override
  public int getPort() {
    return endpoints.isEmpty() ? -1 : endpoints.get(0).getPort();
  }

  @Override
  public List<Integer> getPorts() {
    final List<Integer> ports = new ArrayList<>(endpoints.size());
    for (GrpcEndpoint endpoint : endpoints) {
      ports.add(endpoint.getPort());
    }
    return Collections.unmodifiableList(ports);
  }

  /** Exposed so tests can inspect what a given configuration would start. */
  List<GrpcEndpoint> currentEndpoints() {
    return Collections.unmodifiableList(endpoints);
  }
}

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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.registry.ServiceRegistry;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Selects the topology for a call and resolves its backend.
 * <p>
 * Knox normally routes on {@code /gateway/{topology}/{service}}, which is not
 * available here: the Spark Connect connection string forbids a path component,
 * and gRPC fixes request paths at {@code /pkg.Service/Method}. The topology
 * therefore has to come from something else a vanilla client can send. Two
 * discriminators are supported:
 * <ol>
 *   <li>a metadata entry, named by configuration and {@code knox-topology} by
 *       default, which the client supplies as an
 *       extra {@code sc://} connection-string parameter;</li>
 *   <li>the configured default topology, for the single-topology case.</li>
 * </ol>
 * Because the client chooses in case 1, this is only a routing decision, not an
 * authorization one — the coarse ACL check downstream still gates whether the
 * user may use the topology they asked for.
 * <p>
 * Backend lookup then goes through the ordinary registry
 * ({@code ServiceRegistry.lookupServiceURL}), so a Spark Connect backend is
 * declared in topology XML like any other service. The registry treats service
 * URLs as opaque strings, which is why a {@code grpc://} URL needs no special
 * handling — the same property that already lets {@code ws://} URLs through.
 */
public class RoutingInterceptor implements ServerInterceptor {

  private static final GrpcGatewayMessages LOG = MessagesFactory.get(GrpcGatewayMessages.class);

  private final GatewayConfig config;
  private final GatewayServices services;
  private final String serviceRole;
  private final Metadata.Key<String> topologyKey;

  public RoutingInterceptor(GatewayConfig config, GatewayServices services, String serviceRole,
                            Metadata.Key<String> topologyKey) {
    this.config = config;
    this.services = services;
    this.serviceRole = serviceRole;
    this.topologyKey = topologyKey;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                               Metadata headers,
                                                               ServerCallHandler<ReqT, RespT> next) {
    final String method = call.getMethodDescriptor().getFullMethodName();

    final String topology = resolveTopology(headers);
    if (StringUtils.isBlank(topology)) {
      return reject(call, method, Status.UNIMPLEMENTED,
          "no topology selected; set a " + topologyKey.name()
              + " connection parameter or configure a default topology");
    }

    final ServiceRegistry registry = services.getService(ServiceType.SERVICE_REGISTRY_SERVICE);
    final String backendUrl = registry == null ? null : registry.lookupServiceURL(topology, serviceRole);
    if (StringUtils.isBlank(backendUrl)) {
      return reject(call, method, Status.UNAVAILABLE,
          "topology " + topology + " declares no " + serviceRole + " service");
    }

    final GrpcCallContext callContext = GrpcCallContext.current();
    if (callContext != null) {
      callContext.setTopology(topology);
      callContext.setBackendUrl(backendUrl);
      LOG.routingCall(method, callContext.getPrincipal(), topology, backendUrl);
    }
    return next.startCall(call, headers);
  }

  private String resolveTopology(Metadata headers) {
    final String requested = headers.get(topologyKey);
    if (StringUtils.isNotBlank(requested)) {
      return requested.trim();
    }
    return config.getSparkConnectDefaultTopology();
  }

  private <ReqT, RespT> ServerCall.Listener<ReqT> reject(ServerCall<ReqT, RespT> call,
                                                         String method,
                                                         Status status,
                                                         String reason) {
    LOG.routingFailed(method, reason);
    call.close(status.withDescription(reason), new Metadata());
    return new ServerCall.Listener<ReqT>() { };
  }
}

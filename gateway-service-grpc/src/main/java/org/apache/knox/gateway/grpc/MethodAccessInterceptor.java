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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Topology;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Applies per-topology method allow and deny lists, after the topology has been
 * selected and the user authorized to use it.
 * <p>
 * Being keyed on the topology is the point: the same gateway can front a cluster
 * where uploading code is fine and one where it is not, and the difference is a
 * parameter in the topology that already forms the policy boundary.
 * <p>
 * Configured alongside the ACLs on the {@code AclsAuthz} provider, keyed on the
 * service role:
 * <pre>
 * &lt;param&gt;
 *   &lt;name&gt;SPARKCONNECT.methods.deny&lt;/name&gt;
 *   &lt;value&gt;AddArtifacts&lt;/value&gt;
 * &lt;/param&gt;
 * </pre>
 */
public class MethodAccessInterceptor implements ServerInterceptor {

  private static final GrpcGatewayMessages LOG = MessagesFactory.get(GrpcGatewayMessages.class);

  private static final String AUTHZ_PROVIDER_ROLE = "authorization";
  private static final String ACLS_AUTHZ_PROVIDER_NAME = "AclsAuthz";
  private static final String DENY_SUFFIX = ".methods.deny";
  private static final String ALLOW_SUFFIX = ".methods.allow";

  private final GatewayServices services;
  private final String resourceRole;
  private final MethodAccessPolicy defaultPolicy;
  /** Derived from topology configuration, so cached per topology and cleared on redeploy. */
  private final Map<String, MethodAccessPolicy> policies = new ConcurrentHashMap<>();

  public MethodAccessInterceptor(GatewayServices services, String resourceRole,
                                 MethodAccessPolicy defaultPolicy) {
    this.services = services;
    this.resourceRole = resourceRole;
    this.defaultPolicy = defaultPolicy;
  }

  /** Drops cached policies so a redeployed topology takes effect. */
  public void invalidate() {
    policies.clear();
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                               Metadata headers,
                                                               ServerCallHandler<ReqT, RespT> next) {
    final String method = call.getMethodDescriptor().getFullMethodName();
    final GrpcCallContext callContext = GrpcCallContext.current();
    final String topology = callContext == null ? null : callContext.getTopology();

    final MethodAccessPolicy policy =
        topology == null ? defaultPolicy : policies.computeIfAbsent(topology, this::buildPolicy);

    if (!policy.isPermitted(method)) {
      LOG.methodDenied(method, callContext == null ? null : callContext.getPrincipal(), topology);
      call.close(Status.PERMISSION_DENIED
          .withDescription("This RPC is not permitted in this topology"), new Metadata());
      return new ServerCall.Listener<ReqT>() { };
    }
    return next.startCall(call, headers);
  }

  private MethodAccessPolicy buildPolicy(String topologyName) {
    final TopologyService topologyService = services.getService(ServiceType.TOPOLOGY_SERVICE);
    if (topologyService == null) {
      return defaultPolicy;
    }
    for (Topology topology : topologyService.getTopologies()) {
      if (!topologyName.equals(topology.getName())) {
        continue;
      }
      final Provider provider = topology.getProvider(AUTHZ_PROVIDER_ROLE, ACLS_AUTHZ_PROVIDER_NAME);
      if (provider == null || !provider.isEnabled() || provider.getParams() == null) {
        return defaultPolicy;
      }
      final Map<String, String> params = provider.getParams();
      final String deny = param(params, resourceRole + DENY_SUFFIX);
      final String allow = param(params, resourceRole + ALLOW_SUFFIX);
      if (deny == null && allow == null) {
        return defaultPolicy;
      }
      return MethodAccessPolicy.of(deny, allow);
    }
    return defaultPolicy;
  }

  /** Provider params are lowercased on the servlet path; accept either spelling. */
  private static String param(Map<String, String> params, String name) {
    final String value = params.get(name);
    return value != null ? value : params.get(name.toLowerCase(java.util.Locale.ROOT));
  }
}

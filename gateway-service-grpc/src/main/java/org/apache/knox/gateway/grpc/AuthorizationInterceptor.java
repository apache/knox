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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.filter.InvalidACLException;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Topology;

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Applies the coarse "may this user use this service in this topology" check,
 * after authentication and before any backend connection is opened.
 * <p>
 * The servlet {@code AclsAuthz} filter cannot run here — there is no filter
 * chain on a gRPC call — so this reads the same provider configuration directly
 * and evaluates it with the same parser. A topology that declares no ACLs for
 * the role is unrestricted, as on the servlet path.
 */
public class AuthorizationInterceptor implements ServerInterceptor {

  private static final GrpcGatewayMessages LOG = MessagesFactory.get(GrpcGatewayMessages.class);

  private static final String AUTHZ_PROVIDER_ROLE = "authorization";
  private static final String ACLS_AUTHZ_PROVIDER_NAME = "AclsAuthz";

  private final GatewayConfig config;
  private final GatewayServices services;
  private final String resourceRole;
  /**
   * Authorizers are derived from topology configuration, which changes only on
   * redeploy, so they are cached per topology rather than rebuilt per RPC. The
   * cache is cleared when topologies are reloaded.
   */
  private final Map<String, AclAuthorizer> authorizers = new ConcurrentHashMap<>();

  public AuthorizationInterceptor(GatewayConfig config, GatewayServices services, String resourceRole) {
    this.config = config;
    this.services = services;
    this.resourceRole = resourceRole;
  }

  /** Drops cached ACLs so a redeployed topology takes effect. */
  public void invalidate() {
    authorizers.clear();
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                               Metadata headers,
                                                               ServerCallHandler<ReqT, RespT> next) {
    final GrpcCallContext callContext = GrpcCallContext.current();
    final String method = call.getMethodDescriptor().getFullMethodName();
    final String topology = callContext == null ? null : callContext.getTopology();
    final String user = callContext == null ? null : callContext.getPrincipal();

    if (topology == null || user == null) {
      // Routing and authentication run first; reaching here without either means
      // the chain was assembled wrongly. Deny rather than guess.
      return reject(call, method, user, topology, "call reached authorization without an identity or topology");
    }

    final AclAuthorizer authorizer;
    try {
      authorizer = authorizers.computeIfAbsent(topology, this::buildAuthorizer);
    } catch (InvalidAclConfigurationException e) {
      return reject(call, method, user, topology, e.getMessage());
    }

    if (!authorizer.isPermitted(user, callContext.getGroups(), callContext.getRemoteAddress())) {
      return reject(call, method, user, topology, "denied by the topology ACLs for " + resourceRole);
    }
    return next.startCall(call, headers);
  }

  private AclAuthorizer buildAuthorizer(String topologyName) {
    final TopologyService topologyService = services.getService(ServiceType.TOPOLOGY_SERVICE);
    Map<String, String> providerParams = null;
    if (topologyService != null) {
      for (Topology topology : topologyService.getTopologies()) {
        if (topologyName.equals(topology.getName())) {
          final Provider provider = topology.getProvider(AUTHZ_PROVIDER_ROLE, ACLS_AUTHZ_PROVIDER_NAME);
          if (provider != null && provider.isEnabled()) {
            providerParams = provider.getParams();
          }
          break;
        }
      }
    }
    try {
      return new AclAuthorizer(resourceRole, providerParams,
          config.getKnoxAdminUsers(), config.getKnoxAdminGroups());
    } catch (InvalidACLException e) {
      throw new InvalidAclConfigurationException(
          "Topology " + topologyName + " has malformed ACLs for " + resourceRole, e);
    }
  }

  static String remoteAddressOf(ServerCall<?, ?> call) {
    final SocketAddress address = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
    if (address instanceof InetSocketAddress) {
      final InetSocketAddress inet = (InetSocketAddress) address;
      return inet.getAddress() == null ? inet.getHostString() : inet.getAddress().getHostAddress();
    }
    return address == null ? null : address.toString();
  }

  private <ReqT, RespT> ServerCall.Listener<ReqT> reject(ServerCall<ReqT, RespT> call,
                                                         String method,
                                                         String user,
                                                         String topology,
                                                         String reason) {
    LOG.authorizationFailed(method, user, topology, reason);
    call.close(Status.PERMISSION_DENIED.withDescription("Not permitted to use " + resourceRole), new Metadata());
    return new ServerCall.Listener<ReqT>() { };
  }

  /** Wraps {@link InvalidACLException} so it can escape a {@code computeIfAbsent} mapping function. */
  static class InvalidAclConfigurationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    InvalidAclConfigurationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}

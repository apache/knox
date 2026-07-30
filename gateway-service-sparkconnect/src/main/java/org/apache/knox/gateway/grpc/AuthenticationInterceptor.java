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

import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Rejects any call that does not present a valid Knox bearer token.
 * <p>
 * Authentication happens before a backend channel is opened, so an
 * unauthenticated request never reaches Spark — which matters because the OSS
 * Spark Connect server has essentially no authentication of its own and assumes
 * a fronting proxy provides it.
 * <p>
 * Tokens are checked when an RPC starts and not again while it runs. A
 * multi-hour {@code ExecutePlan} is therefore not severed the moment its token
 * expires; the next RPC fails instead. Cutting off long queries at expiry would
 * punish precisely the workloads Spark Connect exists to serve, and Spark's own
 * session timeout still bounds how long a session survives.
 */
public class AuthenticationInterceptor implements ServerInterceptor {

  private static final GrpcGatewayMessages LOG = MessagesFactory.get(GrpcGatewayMessages.class);

  private final TokenAuthenticator authenticator;

  public AuthenticationInterceptor(TokenAuthenticator authenticator) {
    this.authenticator = authenticator;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                               Metadata headers,
                                                               ServerCallHandler<ReqT, RespT> next) {
    final String method = call.getMethodDescriptor().getFullMethodName();
    final String header = headers.get(GrpcMetadataKeys.AUTHORIZATION);

    if (header == null || !header.regionMatches(true, 0, GrpcMetadataKeys.BEARER_PREFIX, 0,
        GrpcMetadataKeys.BEARER_PREFIX.length())) {
      return reject(call, method, "no bearer token presented");
    }

    final String serializedToken = header.substring(GrpcMetadataKeys.BEARER_PREFIX.length()).trim();
    final AuthenticatedUser user;
    try {
      user = authenticator.authenticate(serializedToken);
    } catch (TokenAuthenticator.AuthenticationException e) {
      return reject(call, method, e.getMessage());
    } catch (RuntimeException e) {
      // Belt and braces: whatever goes wrong while examining a credential, the
      // answer is that the call is not authenticated. Letting an exception escape
      // would hand the caller UNKNOWN instead of UNAUTHENTICATED, which both
      // leaks that the input was unusual and lets a malformed token cost the
      // gateway a stack trace on every request.
      return reject(call, method, "token validation failed: " + e.getClass().getSimpleName());
    }

    final GrpcCallContext callContext = GrpcCallContext.current();
    if (callContext != null) {
      callContext.setPrincipal(user.getPrincipal());
      callContext.setGroups(user.getGroups());
    }
    return next.startCall(call, headers);
  }

  private <ReqT, RespT> ServerCall.Listener<ReqT> reject(ServerCall<ReqT, RespT> call,
                                                         String method,
                                                         String reason) {
    LOG.authenticationFailed(method, reason);
    // The description is deliberately generic: distinguishing "expired" from
    // "bad signature" tells an attacker which tokens are real.
    call.close(Status.UNAUTHENTICATED.withDescription("Invalid or missing bearer token"), new Metadata());
    return new ServerCall.Listener<ReqT>() { };
  }
}

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

import java.util.concurrent.TimeUnit;

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Creates the per-call context and writes one audit record per RPC.
 * <p>
 * This is the outermost interceptor. It runs first so that every call — including
 * ones rejected for a bad token or a failed ACL check — gets a record, and it
 * observes the outcome last, once the inner interceptors have filled in whatever
 * they resolved. The record therefore reports the principal and topology even on
 * paths where the call never reached a backend.
 * <p>
 * A shared mutable {@link GrpcCallContext} is what makes that possible: gRPC
 * context values set by an inner interceptor are not visible to an outer one, so
 * the state the inner stages establish has to live in an object this interceptor
 * created and attached before delegating.
 */
public class AuditInterceptor implements ServerInterceptor {

  private static final AuditService AUDIT_SERVICE = AuditServiceFactory.getAuditService();
  private static final Auditor AUDITOR = AuditServiceFactory.getAuditService()
      .getAuditor(AuditConstants.DEFAULT_AUDITOR_NAME,
          AuditConstants.KNOX_SERVICE_NAME,
          AuditConstants.KNOX_COMPONENT_NAME);

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                               Metadata headers,
                                                               ServerCallHandler<ReqT, RespT> next) {
    final String method = call.getMethodDescriptor().getFullMethodName();
    final GrpcCallContext callContext = new GrpcCallContext(
        method,
        call.getAuthority(),
        AuthorizationInterceptor.remoteAddressOf(call),
        System.nanoTime());

    final ServerCall<ReqT, RespT> auditedCall =
        new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
          @Override
          public void close(Status status, Metadata trailers) {
            try {
              audit(callContext, status);
            } finally {
              super.close(status, trailers);
            }
          }
        };

    final Context grpcContext = Context.current().withValue(GrpcCallContext.KEY, callContext);
    return Contexts.interceptCall(grpcContext, auditedCall, headers, next);
  }

  private void audit(GrpcCallContext callContext, Status status) {
    final long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - callContext.getStartNanos());
    final String outcome = status.isOk() ? ActionOutcome.SUCCESS : ActionOutcome.FAILURE;

    final StringBuilder message = new StringBuilder(160);
    message.append("status=").append(status.getCode())
        .append(", topology=").append(nullSafe(callContext.getTopology()))
        .append(", backend=").append(nullSafe(callContext.getBackendUrl()))
        .append(", remoteAddress=").append(nullSafe(callContext.getRemoteAddress()))
        .append(", authority=").append(nullSafe(callContext.getAuthority()))
        .append(", durationMs=").append(millis);
    // Populated only on the proto-aware path; a byte-level proxy cannot know them.
    if (callContext.getSessionId() != null) {
      message.append(", sessionId=").append(callContext.getSessionId());
    }
    if (callContext.getOperationId() != null) {
      message.append(", operationId=").append(callContext.getOperationId());
    }

    AUDIT_SERVICE.createContext();
    try {
      if (callContext.getPrincipal() != null) {
        AUDIT_SERVICE.getContext().setUsername(callContext.getPrincipal());
      }
      AUDITOR.audit(Action.ACCESS, callContext.getMethodName(), ResourceType.URI, outcome,
          message.toString());
    } finally {
      AUDIT_SERVICE.detachContext();
    }
  }

  private static String nullSafe(String value) {
    return value == null ? "-" : value;
  }
}

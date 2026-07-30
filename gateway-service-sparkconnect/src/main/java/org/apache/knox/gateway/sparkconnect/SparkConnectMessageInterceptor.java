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

import org.apache.knox.gateway.grpc.GrpcCallContext;
import org.apache.knox.gateway.grpc.MessageInterceptor;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;

import io.grpc.Status;

/**
 * Asserts the authenticated identity onto every request, and applies the
 * per-RPC gating switches.
 * <p>
 * Identity assertion is the reason this gateway parses messages at all. Spark
 * Connect trusts a <em>client-asserted</em> {@code user_context.user_id}: the
 * client simply states who it is, and the server believes it. Overwriting that
 * field with the principal Knox authenticated closes a real spoofing hole, and
 * it is something no byte-level proxy — or L4 passthrough, or generic sidecar —
 * could do.
 * <p>
 * Be precise about what it buys, though. On the server, {@code user_id} keys the
 * session cache ({@code SessionKey(userId, sessionId)}) and appears in logs and
 * events. It is <em>not</em> propagated into Spark's {@code CurrentUserContext},
 * so {@code current_user()} in SQL still reports the Spark application's own
 * user. What assertion guarantees is session isolation between users and a
 * trustworthy audit trail — not storage-level enforcement, which needs either
 * per-user backends or a server-side component that bridges this field into the
 * session.
 */
public class SparkConnectMessageInterceptor implements MessageInterceptor<Message> {

  private static final String USER_CONTEXT_FIELD = "user_context";
  private static final String SESSION_ID_FIELD = "session_id";
  private static final String OPERATION_ID_FIELD = "operation_id";
  private static final String USER_ID_FIELD = "user_id";
  private static final String USER_NAME_FIELD = "user_name";

  private final RequestGuard guard;

  /**
   * @param guard an extra check for this RPC, or null when identity assertion is
   *        all that applies
   */
  public SparkConnectMessageInterceptor(RequestGuard guard) {
    this.guard = guard;
  }

  @Override
  public Message intercept(Message message) {
    final GrpcCallContext callContext = GrpcCallContext.current();
    final String principal = callContext == null ? null : callContext.getPrincipal();
    if (principal == null) {
      // The authentication interceptor runs before any handler, so this cannot
      // happen unless the chain was assembled wrongly. Fail rather than forward a
      // request carrying whatever identity the client claimed.
      throw Status.INTERNAL
          .withDescription("No authenticated principal available for identity assertion")
          .asRuntimeException();
    }

    recordCallDetails(message, callContext);
    if (guard != null) {
      guard.check(message, principal);
    }
    return assertIdentity(message, principal);
  }

  /**
   * Copies the session and operation identifiers into the call context so audit
   * records can name the session a call belongs to. Every Spark Connect request
   * carries {@code session_id}; only some carry {@code operation_id}.
   */
  private static void recordCallDetails(Message message, GrpcCallContext callContext) {
    if (callContext == null) {
      return;
    }
    final FieldDescriptor sessionField =
        message.getDescriptorForType().findFieldByName(SESSION_ID_FIELD);
    if (sessionField != null) {
      final Object sessionId = message.getField(sessionField);
      if (sessionId instanceof String && !((String) sessionId).isEmpty()) {
        callContext.setSessionId((String) sessionId);
      }
    }
    final FieldDescriptor operationField =
        message.getDescriptorForType().findFieldByName(OPERATION_ID_FIELD);
    if (operationField != null && message.hasField(operationField)) {
      final Object operationId = message.getField(operationField);
      if (operationId instanceof String && !((String) operationId).isEmpty()) {
        callContext.setOperationId((String) operationId);
      }
    }
  }

  /**
   * Replaces the client-supplied identity with the authenticated one.
   * <p>
   * This works the same way for all twelve RPCs because every Spark Connect
   * request message carries {@code UserContext user_context = 2} in the same
   * position — so the rewrite is driven off the descriptor rather than written
   * out once per message type. Fields the gateway does not touch, including ones
   * from a newer Spark than these vendored protos describe, survive: protobuf
   * retains unknown fields across a parse and re-serialize.
   */
  @SuppressWarnings("unchecked")
  static <T extends Message> T assertIdentity(T message, String principal) {
    final FieldDescriptor userContextField =
        message.getDescriptorForType().findFieldByName(USER_CONTEXT_FIELD);
    if (userContextField == null) {
      return message;
    }

    final Message userContext = (Message) message.getField(userContextField);
    final FieldDescriptor userIdField =
        userContext.getDescriptorForType().findFieldByName(USER_ID_FIELD);
    final FieldDescriptor userNameField =
        userContext.getDescriptorForType().findFieldByName(USER_NAME_FIELD);

    final Message.Builder userContextBuilder = userContext.toBuilder();
    if (userIdField != null) {
      userContextBuilder.setField(userIdField, principal);
    }
    // The client's user_name is overwritten too: it is purely descriptive on the
    // server, but leaving a self-asserted value would put a name Knox never
    // verified into Spark's logs next to the id it did.
    if (userNameField != null) {
      userContextBuilder.setField(userNameField, principal);
    }

    return (T) message.toBuilder()
        .setField(userContextField, userContextBuilder.build())
        .build();
  }

  /** An additional per-RPC check applied before a request is forwarded. */
  @FunctionalInterface
  public interface RequestGuard {

    /**
     * @param message the request message
     * @param principal the authenticated principal
     * @throws io.grpc.StatusRuntimeException to reject the call
     */
    void check(Message message, String principal);
  }
}

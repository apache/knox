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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.knox.gateway.grpc.GrpcCallContext;

import com.google.protobuf.Message;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.apache.spark.connect.proto.AnalyzePlanRequest;
import org.apache.spark.connect.proto.ConfigRequest;
import org.apache.spark.connect.proto.ExecutePlanRequest;
import org.apache.spark.connect.proto.InterruptRequest;
import org.apache.spark.connect.proto.ReattachExecuteRequest;
import org.apache.spark.connect.proto.UserContext;

import org.junit.Test;

public class SparkConnectMessageInterceptorTest {

  private static final String PRINCIPAL = "alice";
  private static final String SPOOFED = "root";

  @Test
  public void overwritesClientSuppliedUserId() {
    final ExecutePlanRequest request = ExecutePlanRequest.newBuilder()
        .setSessionId("session-1")
        .setUserContext(UserContext.newBuilder().setUserId(SPOOFED).setUserName(SPOOFED).build())
        .build();

    final ExecutePlanRequest forwarded = (ExecutePlanRequest) intercept(request, PRINCIPAL);

    // The whole point of parsing message bodies: the client says who it is, and
    // Spark believes it, so Knox replaces the claim with the identity it verified.
    assertEquals(PRINCIPAL, forwarded.getUserContext().getUserId());
    assertEquals(PRINCIPAL, forwarded.getUserContext().getUserName());
  }

  @Test
  public void assertsIdentityWhenClientSuppliesNoUserContext() {
    final ExecutePlanRequest request = ExecutePlanRequest.newBuilder().setSessionId("session-1").build();

    final ExecutePlanRequest forwarded = (ExecutePlanRequest) intercept(request, PRINCIPAL);

    assertEquals(PRINCIPAL, forwarded.getUserContext().getUserId());
  }

  @Test
  public void assertsIdentityOnEveryRequestShape() {
    // All twelve request types carry UserContext in the same field position, which
    // is why one descriptor-driven rewrite covers the whole service rather than
    // needing a handler per RPC. Spot-check across the RPC shapes.
    final Message[] requests = {
        ExecutePlanRequest.newBuilder()
            .setUserContext(UserContext.newBuilder().setUserId(SPOOFED).build()).build(),
        AnalyzePlanRequest.newBuilder()
            .setUserContext(UserContext.newBuilder().setUserId(SPOOFED).build()).build(),
        ConfigRequest.newBuilder()
            .setUserContext(UserContext.newBuilder().setUserId(SPOOFED).build()).build(),
        InterruptRequest.newBuilder()
            .setUserContext(UserContext.newBuilder().setUserId(SPOOFED).build()).build(),
        ReattachExecuteRequest.newBuilder()
            .setUserContext(UserContext.newBuilder().setUserId(SPOOFED).build()).build(),
    };

    for (Message request : requests) {
      final Message forwarded = intercept(request, PRINCIPAL);
      final UserContext userContext = (UserContext) forwarded.getField(
          forwarded.getDescriptorForType().findFieldByName("user_context"));
      assertEquals(request.getDescriptorForType().getName() + " kept the client's user_id",
          PRINCIPAL, userContext.getUserId());
    }
  }

  @Test
  public void preservesEveryOtherField() {
    final ExecutePlanRequest request = ExecutePlanRequest.newBuilder()
        .setSessionId("session-1")
        .setOperationId("operation-1")
        .setClientType("pyspark")
        .addTags("tag-a")
        .addTags("tag-b")
        .setUserContext(UserContext.newBuilder()
            .setUserId(SPOOFED)
            .addExtensions(com.google.protobuf.Any.newBuilder().setTypeUrl("type/x").build())
            .build())
        .build();

    final ExecutePlanRequest forwarded = (ExecutePlanRequest) intercept(request, PRINCIPAL);

    assertEquals("session-1", forwarded.getSessionId());
    assertEquals("operation-1", forwarded.getOperationId());
    assertEquals("pyspark", forwarded.getClientType());
    assertEquals(request.getTagsList(), forwarded.getTagsList());
    // UserContext extensions belong to the client, not to the identity claim.
    assertEquals(1, forwarded.getUserContext().getExtensionsCount());
    assertEquals("type/x", forwarded.getUserContext().getExtensions(0).getTypeUrl());
  }

  @Test
  public void preservesFieldsUnknownToTheVendoredProtos() throws Exception {
    // A newer Spark client can send fields these protos do not describe. Protobuf
    // retains them as unknown fields across a parse and re-serialize, which is
    // what keeps proto skew from being a breaking problem.
    final ExecutePlanRequest known = ExecutePlanRequest.newBuilder()
        .setSessionId("session-1")
        .setUserContext(UserContext.newBuilder().setUserId(SPOOFED).build())
        .build();
    final com.google.protobuf.UnknownFieldSet unknown = com.google.protobuf.UnknownFieldSet.newBuilder()
        .addField(9999, com.google.protobuf.UnknownFieldSet.Field.newBuilder()
            .addVarint(42L).build())
        .build();
    final ExecutePlanRequest request = known.toBuilder().setUnknownFields(unknown).build();

    final ExecutePlanRequest forwarded = (ExecutePlanRequest) intercept(request, PRINCIPAL);

    assertEquals(PRINCIPAL, forwarded.getUserContext().getUserId());
    assertEquals(42L, forwarded.getUnknownFields().getField(9999).getVarintList().get(0).longValue());
  }

  @Test
  public void recordsSessionAndOperationForAuditing() {
    final GrpcCallContext callContext = newCallContext(PRINCIPAL);
    final ExecutePlanRequest request = ExecutePlanRequest.newBuilder()
        .setSessionId("session-7")
        .setOperationId("operation-9")
        .build();

    interceptWith(callContext, request, null);

    assertEquals("session-7", callContext.getSessionId());
    assertEquals("operation-9", callContext.getOperationId());
  }

  @Test
  public void refusesToForwardWithoutAnAuthenticatedPrincipal() {
    final GrpcCallContext callContext =
        new GrpcCallContext("m", "authority", "127.0.0.1", System.nanoTime());
    try {
      interceptWith(callContext, ExecutePlanRequest.getDefaultInstance(), null);
      fail("Expected the request to be rejected without a principal");
    } catch (StatusRuntimeException e) {
      // Forwarding here would send the client's own identity claim through
      // untouched, which is exactly the spoofing this layer exists to stop.
      assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }
  }

  @Test
  public void appliesTheConfiguredGuardBeforeRewriting() {
    final boolean[] guardRan = {false};
    final SparkConnectMessageInterceptor.RequestGuard guard = (message, principal) -> {
      guardRan[0] = true;
      assertEquals(PRINCIPAL, principal);
      // The guard sees the client's message, before identity assertion.
      assertNotNull(message);
    };

    interceptWith(newCallContext(PRINCIPAL), ExecutePlanRequest.getDefaultInstance(), guard);

    assertTrue("guard was not invoked", guardRan[0]);
  }

  private static Message intercept(Message request, String principal) {
    return interceptWith(newCallContext(principal), request, null);
  }

  private static Message interceptWith(GrpcCallContext callContext,
                                       Message request,
                                       SparkConnectMessageInterceptor.RequestGuard guard) {
    final Message[] result = new Message[1];
    // Context.run keeps StatusRuntimeException unwrapped, which the rejection
    // tests assert on directly.
    io.grpc.Context.current()
        .withValue(GrpcCallContext.KEY, callContext)
        .run(() -> result[0] = new SparkConnectMessageInterceptor(guard).intercept(request));
    return result[0];
  }

  private static GrpcCallContext newCallContext(String principal) {
    final GrpcCallContext callContext =
        new GrpcCallContext("spark.connect.SparkConnectService/ExecutePlan",
            "knox.example.com:15002", "127.0.0.1", System.nanoTime());
    callContext.setPrincipal(principal);
    return callContext;
  }
}

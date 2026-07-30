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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.knox.gateway.grpc.BackendChannelProvider;
import org.apache.knox.gateway.grpc.GrpcCallContext;
import org.apache.knox.gateway.grpc.GrpcMetadataKeys;
import org.apache.knox.gateway.grpc.HeaderRewriter;
import org.apache.knox.gateway.grpc.MessageInterceptor;
import org.apache.knox.gateway.grpc.ProxyCallHandler;

import com.google.protobuf.Message;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

import org.apache.spark.connect.proto.AnalyzePlanRequest;
import org.apache.spark.connect.proto.AnalyzePlanResponse;
import org.apache.spark.connect.proto.ExecutePlanRequest;
import org.apache.spark.connect.proto.ExecutePlanResponse;
import org.apache.spark.connect.proto.SparkConnectServiceGrpc;
import org.apache.spark.connect.proto.UserContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

/**
 * Exercises the relay against a real gRPC backend over an in-process transport:
 * a stand-in Spark Connect server, the gateway's proxy handler in front of it,
 * and a generated client stub calling through.
 * <p>
 * The properties under test are the ones a hand-rolled proxy tends to get wrong
 * — response streaming, verbatim trailers and status codes, and rejecting a
 * gated call before the backend is touched — so they are checked end to end
 * rather than against mocks.
 */
// volatile: the stub's knobs are set by the test thread and read by the gRPC
// server threads handling the call.
@SuppressWarnings("PMD.AvoidUsingVolatile")
public class SparkConnectProxyIntegrationTest {

  /** Stands in for the trailer Spark uses to carry structured error details. */
  private static final Metadata.Key<String> ERROR_DETAILS =
      Metadata.Key.of("x-spark-error-class", Metadata.ASCII_STRING_MARSHALLER);
  private static final Context.Key<Metadata> BACKEND_HEADERS = Context.key("backendHeaders");
  private static final String PRINCIPAL = "alice";

  /** A stalled relay is a plausible failure mode here, so fail rather than hang. */
  @Rule
  public final Timeout timeout = Timeout.seconds(30);

  private Server backend;
  private Server gateway;
  private ManagedChannel backendChannel;
  private ManagedChannel clientChannel;
  private StubSparkConnect stub;

  @Before
  public void setUp() throws Exception {
    final String backendName = InProcessServerBuilder.generateName();
    final String gatewayName = InProcessServerBuilder.generateName();

    stub = new StubSparkConnect();
    backend = InProcessServerBuilder.forName(backendName)
        .addService(ServerInterceptors.intercept(stub, new CaptureHeaders()))
        .build()
        .start();
    backendChannel = InProcessChannelBuilder.forName(backendName).build();

    gateway = InProcessServerBuilder.forName(gatewayName)
        .addService(proxyService(identityAsserting()))
        .build()
        .start();
    clientChannel = InProcessChannelBuilder.forName(gatewayName).build();
  }

  @After
  public void tearDown() {
    shutdown(clientChannel);
    shutdown(backendChannel);
    shutdown(gateway);
    shutdown(backend);
  }

  private static void shutdown(ManagedChannel channel) {
    if (channel != null) {
      channel.shutdownNow();
    }
  }

  private static void shutdown(Server server) {
    if (server != null) {
      server.shutdownNow();
    }
  }

  private MessageInterceptor<Message> identityAsserting() {
    return new SparkConnectMessageInterceptor(null);
  }

  /**
   * Registers a relay for every Spark Connect method, inside a context carrying
   * the principal and backend the interceptor chain would normally have
   * resolved. The chain itself is covered by its own tests.
   */
  private ServerServiceDefinition proxyService(MessageInterceptor<Message> messageInterceptor) {
    final BackendChannelProvider channels = () -> backendChannel;
    final HeaderRewriter headers = metadata -> metadata.removeAll(GrpcMetadataKeys.AUTHORIZATION);

    final ServerServiceDefinition.Builder builder =
        ServerServiceDefinition.builder(SparkConnectServiceGrpc.getServiceDescriptor());
    for (MethodDescriptor<?, ?> method : SparkConnectServiceGrpc.getServiceDescriptor().getMethods()) {
      @SuppressWarnings("unchecked")
      final MethodDescriptor<Message, Message> descriptor = (MethodDescriptor<Message, Message>) method;
      final ProxyCallHandler<Message, Message> handler =
          new ProxyCallHandler<>(channels, messageInterceptor, headers);

      builder.addMethod(ServerMethodDefinition.create(descriptor, (call, metadata) -> {
        final GrpcCallContext callContext = new GrpcCallContext(
            descriptor.getFullMethodName(), "test", "127.0.0.1", System.nanoTime());
        callContext.setPrincipal(PRINCIPAL);
        callContext.setBackendUrl("grpc://backend:15002");
        // Contexts.interceptCall, exactly as the audit interceptor uses it: it
        // attaches the context to the listener callbacks too, not just to
        // startCall. Request messages arrive in onMessage, well after startCall
        // returns, so anything that only wrapped startCall would leave the
        // handler without a principal at the moment it needs one.
        return Contexts.interceptCall(
            Context.current().withValue(GrpcCallContext.KEY, callContext),
            call, metadata, handler);
      }));
    }
    return builder.build();
  }

  @Test
  public void relaysAUnaryCall() {
    final AnalyzePlanResponse response = SparkConnectServiceGrpc.newBlockingStub(clientChannel)
        .analyzePlan(AnalyzePlanRequest.newBuilder().setSessionId("s1").build());

    assertEquals("s1", response.getSessionId());
    assertEquals(1, stub.analyzeRequests.size());
  }

  @Test
  public void assertsIdentityOnTheWayThrough() {
    SparkConnectServiceGrpc.newBlockingStub(clientChannel)
        .analyzePlan(AnalyzePlanRequest.newBuilder()
            .setSessionId("s1")
            .setUserContext(UserContext.newBuilder().setUserId("root").build())
            .build());

    // Assert on what the backend received, not on what the client sent.
    assertEquals(PRINCIPAL, stub.analyzeRequests.get(0).getUserContext().getUserId());
  }

  @Test
  public void stripsTheClientCredentialFromTheBackendLeg() {
    SparkConnectServiceGrpc.newBlockingStub(clientChannel)
        .analyzePlan(AnalyzePlanRequest.newBuilder().setSessionId("s1").build());

    // The user's token authenticates them to Knox and has no meaning past it.
    assertNotNull(stub.lastHeaders);
    assertNull(stub.lastHeaders.get(GrpcMetadataKeys.AUTHORIZATION));
  }

  @Test
  public void relaysEveryMessageOfAServerStream() {
    stub.executeResponseCount = 25;

    final Iterator<ExecutePlanResponse> responses = SparkConnectServiceGrpc
        .newBlockingStub(clientChannel)
        .executePlan(ExecutePlanRequest.newBuilder().setSessionId("s1").build());

    final List<String> ids = new ArrayList<>();
    while (responses.hasNext()) {
      ids.add(responses.next().getResponseId());
    }
    // Long server streams are the normal case for ExecutePlan, not an edge case.
    assertEquals(25, ids.size());
    assertEquals("response-0", ids.get(0));
    assertEquals("response-24", ids.get(24));
  }

  @Test
  public void relaysBackendStatusCodeVerbatim() {
    stub.failExecuteWith = Status.RESOURCE_EXHAUSTED.withDescription("query too large");

    try {
      SparkConnectServiceGrpc.newBlockingStub(clientChannel)
          .executePlan(ExecutePlanRequest.newBuilder().setSessionId("s1").build())
          .next();
      fail("Expected the backend failure to surface at the client");
    } catch (StatusRuntimeException e) {
      // Not remapped to INTERNAL or UNKNOWN: clients branch on these codes.
      assertEquals(Status.Code.RESOURCE_EXHAUSTED, e.getStatus().getCode());
      assertEquals("query too large", e.getStatus().getDescription());
    }
  }

  @Test
  public void relaysBackendTrailersVerbatim() {
    stub.failExecuteWith = Status.INTERNAL.withDescription("boom");
    stub.failureTrailerValue = "AnalysisException";

    try {
      SparkConnectServiceGrpc.newBlockingStub(clientChannel)
          .executePlan(ExecutePlanRequest.newBuilder().setSessionId("s1").build())
          .next();
      fail("Expected the backend failure to surface at the client");
    } catch (StatusRuntimeException e) {
      // Spark packs structured error details into trailers and clients read them,
      // so losing trailers breaks error reporting wholesale rather than at edges.
      assertNotNull("trailers were not relayed", e.getTrailers());
      assertEquals("AnalysisException", e.getTrailers().get(ERROR_DETAILS));
    }
  }

  @Test
  public void relaysUnimplementedForAMethodTheBackendLacks() {
    stub.failExecuteWith = Status.UNIMPLEMENTED.withDescription("not in this Spark");

    try {
      SparkConnectServiceGrpc.newBlockingStub(clientChannel)
          .executePlan(ExecutePlanRequest.newBuilder().setSessionId("s1").build())
          .next();
      fail("Expected UNIMPLEMENTED to surface");
    } catch (StatusRuntimeException e) {
      assertEquals(Status.Code.UNIMPLEMENTED, e.getStatus().getCode());
    }
  }

  @Test
  public void rejectsAGatedCallWithoutContactingTheBackend() throws Exception {
    final String name = InProcessServerBuilder.generateName();
    final MessageInterceptor<Message> denying = message -> {
      throw Status.PERMISSION_DENIED.withDescription("nope").asRuntimeException();
    };

    final Server gatingGateway = InProcessServerBuilder.forName(name)
        .addService(proxyService(denying)).build().start();
    final ManagedChannel gatingClient = InProcessChannelBuilder.forName(name).build();
    try {
      final int before = stub.analyzeRequests.size();
      try {
        SparkConnectServiceGrpc.newBlockingStub(gatingClient)
            .analyzePlan(AnalyzePlanRequest.newBuilder().setSessionId("s1").build());
        fail("Expected the gated call to be denied");
      } catch (StatusRuntimeException e) {
        assertEquals(Status.Code.PERMISSION_DENIED, e.getStatus().getCode());
      }
      // A denial that still forwarded the message would be advisory, not enforcing.
      assertEquals(before, stub.analyzeRequests.size());
    } finally {
      shutdown(gatingClient);
      shutdown(gatingGateway);
    }
  }

  /** Records the metadata the backend actually received. */
  private static final class CaptureHeaders implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
      return Contexts.interceptCall(
          Context.current().withValue(BACKEND_HEADERS, headers), call, headers, next);
    }
  }

  /** A stand-in Spark Connect server that records what it was sent. */
  private static final class StubSparkConnect
      extends SparkConnectServiceGrpc.SparkConnectServiceImplBase {

    private final List<AnalyzePlanRequest> analyzeRequests = new ArrayList<>();
    private volatile Metadata lastHeaders;
    private volatile int executeResponseCount = 1;
    private volatile Status failExecuteWith;
    private volatile String failureTrailerValue;

    @Override
    public void analyzePlan(AnalyzePlanRequest request, StreamObserver<AnalyzePlanResponse> observer) {
      analyzeRequests.add(request);
      lastHeaders = BACKEND_HEADERS.get();
      observer.onNext(AnalyzePlanResponse.newBuilder().setSessionId(request.getSessionId()).build());
      observer.onCompleted();
    }

    @Override
    public void executePlan(ExecutePlanRequest request, StreamObserver<ExecutePlanResponse> observer) {
      lastHeaders = BACKEND_HEADERS.get();
      if (failExecuteWith != null) {
        final Metadata trailers = new Metadata();
        if (failureTrailerValue != null) {
          trailers.put(ERROR_DETAILS, failureTrailerValue);
        }
        observer.onError(failExecuteWith.asRuntimeException(trailers));
        return;
      }
      for (int i = 0; i < executeResponseCount; i++) {
        observer.onNext(ExecutePlanResponse.newBuilder()
            .setSessionId(request.getSessionId())
            .setResponseId("response-" + i)
            .build());
      }
      observer.onCompleted();
    }
  }
}

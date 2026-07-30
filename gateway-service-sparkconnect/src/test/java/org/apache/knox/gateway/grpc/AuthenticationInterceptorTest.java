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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import io.grpc.Attributes;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;

import org.junit.Test;

/**
 * Authentication has to fail closed.
 * <p>
 * Validation reaches third-party JOSE code that signals some malformed
 * credentials by throwing rather than returning false — a token signed with an
 * unexpected algorithm family, for one. If that escapes, the caller gets
 * {@code UNKNOWN} instead of {@code UNAUTHENTICATED}, which answers a probe
 * differently from an ordinary rejection and costs a stack trace per request.
 */
public class AuthenticationInterceptorTest {

  private static final String BEARER = "Bearer some-token";

  @Test
  public void rejectsWhenValidationThrowsUnexpectedly() {
    final Outcome outcome = intercept(new ThrowingAuthenticator(
        new IllegalStateException("unexpected JOSE failure")));

    assertFalse("the call must not reach the handler", outcome.proceeded);
    assertEquals(Status.Code.UNAUTHENTICATED, outcome.status.getCode());
  }

  @Test
  public void rejectsWithTheSameStatusAsAnOrdinaryFailure() {
    // A probe must not be able to tell "malformed in an interesting way" from
    // "simply wrong" by the status code.
    final Status thrown = intercept(new ThrowingAuthenticator(
        new IllegalArgumentException("boom"))).status;
    final Status declined = intercept(new DecliningAuthenticator()).status;

    assertEquals(declined.getCode(), thrown.getCode());
    assertEquals(declined.getDescription(), thrown.getDescription());
  }

  @Test
  public void rejectsWhenNoTokenIsPresented() {
    final Outcome outcome = intercept(new DecliningAuthenticator(), new Metadata());

    assertFalse(outcome.proceeded);
    assertEquals(Status.Code.UNAUTHENTICATED, outcome.status.getCode());
  }

  @Test
  public void rejectsANonBearerAuthorizationHeader() {
    final Metadata headers = new Metadata();
    headers.put(GrpcMetadataKeys.AUTHORIZATION, "Basic dXNlcjpwYXNz");

    final Outcome outcome = intercept(new DecliningAuthenticator(), headers);

    assertFalse(outcome.proceeded);
    assertEquals(Status.Code.UNAUTHENTICATED, outcome.status.getCode());
  }

  @Test
  public void admitsAValidTokenAndPublishesTheIdentity() {
    final GrpcCallContext callContext =
        new GrpcCallContext("m", "authority", "127.0.0.1", System.nanoTime());
    final Outcome outcome = intercept(new AcceptingAuthenticator(), bearerHeaders(), callContext);

    assertTrue("a valid token should reach the handler", outcome.proceeded);
    assertNull(outcome.status);
    assertEquals("alice", callContext.getPrincipal());
    assertEquals(Collections.singleton("analysts"), callContext.getGroups());
  }

  private static Metadata bearerHeaders() {
    final Metadata headers = new Metadata();
    headers.put(GrpcMetadataKeys.AUTHORIZATION, BEARER);
    return headers;
  }

  private static Outcome intercept(TokenAuthenticator authenticator) {
    return intercept(authenticator, bearerHeaders());
  }

  private static Outcome intercept(TokenAuthenticator authenticator, Metadata headers) {
    return intercept(authenticator, headers,
        new GrpcCallContext("m", "authority", "127.0.0.1", System.nanoTime()));
  }

  private static Outcome intercept(TokenAuthenticator authenticator,
                                   Metadata headers,
                                   GrpcCallContext callContext) {
    final RecordingServerCall call = new RecordingServerCall();
    final Outcome outcome = new Outcome();
    final ServerCallHandler<byte[], byte[]> next = (c, h) -> {
      outcome.proceeded = true;
      return new ServerCall.Listener<byte[]>() { };
    };

    io.grpc.Context.current().withValue(GrpcCallContext.KEY, callContext)
        .run(() -> new AuthenticationInterceptor(authenticator).interceptCall(call, headers, next));

    outcome.status = call.closedWith;
    return outcome;
  }

  private static final class Outcome {
    private boolean proceeded;
    private Status status;
  }

  /** Stands in for validation blowing up inside third-party JOSE code. */
  private static final class ThrowingAuthenticator extends TokenAuthenticator {
    private final RuntimeException failure;

    ThrowingAuthenticator(RuntimeException failure) {
      super(null, null);
      this.failure = failure;
    }

    @Override
    public AuthenticatedUser authenticate(String serializedToken) {
      throw failure;
    }
  }

  /** Stands in for an ordinary "this token is not valid" outcome. */
  private static final class DecliningAuthenticator extends TokenAuthenticator {
    DecliningAuthenticator() {
      super(null, null);
    }

    @Override
    public AuthenticatedUser authenticate(String serializedToken) throws AuthenticationException {
      throw new AuthenticationException("Bearer token failed validation");
    }
  }

  private static final class AcceptingAuthenticator extends TokenAuthenticator {
    AcceptingAuthenticator() {
      super(null, null);
    }

    @Override
    public AuthenticatedUser authenticate(String serializedToken) {
      return new AuthenticatedUser("alice", Collections.singleton("analysts"));
    }
  }

  /** Captures the status a rejected call was closed with. */
  private static final class RecordingServerCall extends ServerCall<byte[], byte[]> {

    private Status closedWith;

    @Override
    public void request(int numMessages) {
    }

    @Override
    public void sendHeaders(Metadata headers) {
    }

    @Override
    public void sendMessage(byte[] message) {
    }

    @Override
    public void close(Status status, Metadata trailers) {
      this.closedWith = status;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public Attributes getAttributes() {
      return Attributes.EMPTY;
    }

    @Override
    public MethodDescriptor<byte[], byte[]> getMethodDescriptor() {
      return MethodDescriptor.<byte[], byte[]>newBuilder()
          .setType(MethodDescriptor.MethodType.UNKNOWN)
          .setFullMethodName("spark.connect.SparkConnectService/AnalyzePlan")
          .setRequestMarshaller(ByteArrayMarshaller.INSTANCE)
          .setResponseMarshaller(ByteArrayMarshaller.INSTANCE)
          .build();
    }
  }
}

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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.knox.gateway.grpc.IdentityAssertingInterceptor;
import org.apache.knox.gateway.grpc.IdentityRewritePolicy;
import org.apache.knox.gateway.grpc.ProtoWire;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.google.protobuf.UnknownFieldSet;

import org.apache.spark.connect.proto.AddArtifactsRequest;
import org.apache.spark.connect.proto.AnalyzePlanRequest;
import org.apache.spark.connect.proto.CloneSessionRequest;
import org.apache.spark.connect.proto.ConfigRequest;
import org.apache.spark.connect.proto.ExecutePlanRequest;
import org.apache.spark.connect.proto.FetchErrorDetailsRequest;
import org.apache.spark.connect.proto.GetStatusRequest;
import org.apache.spark.connect.proto.InterruptRequest;
import org.apache.spark.connect.proto.KeyValue;
import org.apache.spark.connect.proto.ReattachExecuteRequest;
import org.apache.spark.connect.proto.ReleaseExecuteRequest;
import org.apache.spark.connect.proto.ReleaseSessionRequest;
import org.apache.spark.connect.proto.UserContext;

import org.junit.Test;

/**
 * Checks the schema-free identity rewrite against generated classes.
 * <p>
 * The gateway itself compiles against no Spark Connect protos and rewrites
 * requests by field number alone. These protos exist only here, as an oracle:
 * they say what the wire bytes are supposed to mean, so the hand-written wire
 * code can be held to typed semantics. A Spark release that moved the fields
 * this depends on would fail here rather than in production.
 */
public class IdentityAssertionOracleTest {

  private static final String PRINCIPAL = "alice";
  private static final String SPOOFED = "root";

  private final IdentityAssertingInterceptor interceptor =
      new IdentityAssertingInterceptor(
          IdentityRewritePolicy.parse("2.1=principal,2.2=principal",
              IdentityRewritePolicy.DEFAULT_SCAN_LIMIT));

  private static UserContext spoofedIdentity() {
    return UserContext.newBuilder()
        .setUserId(SPOOFED)
        .setUserName(SPOOFED)
        .addExtensions(Any.newBuilder().setTypeUrl("type/keep").build())
        .build();
  }

  /**
   * Rewrites the message, reparses it with the generated class, and asserts both
   * that the identity was replaced and that nothing else changed.
   */
  private void assertRewrite(Message original) {
    final byte[] rewritten = interceptor.assertIdentity(original.toByteArray(), PRINCIPAL);

    final Message reparsed;
    try {
      reparsed = original.getParserForType().parseFrom(rewritten);
    } catch (Exception e) {
      throw new AssertionError(original.getDescriptorForType().getName()
          + " did not survive the rewrite as valid protobuf", e);
    }

    final UserContext identity = (UserContext) reparsed.getField(
        reparsed.getDescriptorForType().findFieldByName("user_context"));
    assertEquals("user_id was not asserted", PRINCIPAL, identity.getUserId());
    assertEquals("user_name was not asserted", PRINCIPAL, identity.getUserName());

    // Blank out the identity on both sides; everything remaining must be equal.
    final Message.Builder before = original.toBuilder();
    final Message.Builder after = reparsed.toBuilder();
    before.setField(before.getDescriptorForType().findFieldByName("user_context"),
        UserContext.getDefaultInstance());
    after.setField(after.getDescriptorForType().findFieldByName("user_context"),
        UserContext.getDefaultInstance());
    assertEquals("the rewrite changed something other than the identity",
        before.build(), after.build());
  }

  @Test
  public void assertsIdentityOnEveryRequestShape() {
    final UserContext spoof = spoofedIdentity();
    assertRewrite(ExecutePlanRequest.newBuilder().setSessionId("s").setUserContext(spoof).build());
    assertRewrite(AnalyzePlanRequest.newBuilder().setSessionId("s").setUserContext(spoof).build());
    assertRewrite(ConfigRequest.newBuilder().setSessionId("s").setUserContext(spoof).build());
    assertRewrite(AddArtifactsRequest.newBuilder().setSessionId("s").setUserContext(spoof).build());
    assertRewrite(InterruptRequest.newBuilder().setSessionId("s").setUserContext(spoof).build());
    assertRewrite(ReattachExecuteRequest.newBuilder().setSessionId("s").setUserContext(spoof).build());
    assertRewrite(ReleaseExecuteRequest.newBuilder().setSessionId("s").setUserContext(spoof).build());
    assertRewrite(ReleaseSessionRequest.newBuilder().setSessionId("s").setUserContext(spoof).build());
    assertRewrite(FetchErrorDetailsRequest.newBuilder().setSessionId("s").setUserContext(spoof).build());
    assertRewrite(CloneSessionRequest.newBuilder().setSessionId("s").setUserContext(spoof).build());
    assertRewrite(GetStatusRequest.newBuilder().setSessionId("s").setUserContext(spoof).build());
  }

  @Test
  public void preservesEveryOtherField() {
    assertRewrite(ExecutePlanRequest.newBuilder()
        .setSessionId("session-1")
        .setOperationId("operation-1")
        .setClientType("pyspark")
        .addTags("tag-a")
        .addTags("tag-b")
        .setUserContext(spoofedIdentity())
        .build());
  }

  @Test
  public void preservesExtensionsInsideTheIdentityContainer() {
    final ExecutePlanRequest request = ExecutePlanRequest.newBuilder()
        .setSessionId("s").setUserContext(spoofedIdentity()).build();

    final byte[] rewritten = interceptor.assertIdentity(request.toByteArray(), PRINCIPAL);
    final ExecutePlanRequest reparsed;
    try {
      reparsed = ExecutePlanRequest.parseFrom(rewritten);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    // Extensions belong to the client; only the identity fields are ours.
    assertEquals(1, reparsed.getUserContext().getExtensionsCount());
    assertEquals("type/keep", reparsed.getUserContext().getExtensions(0).getTypeUrl());
  }

  @Test
  public void synthesisesAnIdentityWhenTheClientSendsNone() {
    assertRewrite(ExecutePlanRequest.newBuilder().setSessionId("s").build());
  }

  @Test
  public void assertsOverAnEmptyIdentity() {
    assertRewrite(ExecutePlanRequest.newBuilder().setSessionId("s")
        .setUserContext(UserContext.getDefaultInstance()).build());
  }

  @Test
  public void handlesAPayloadLargeEnoughToNeedMultiByteLengths() {
    final StringBuilder big = new StringBuilder();
    for (int i = 0; i < 50000; i++) {
      big.append("xxxx");
    }
    assertRewrite(ExecutePlanRequest.newBuilder().setSessionId("s")
        .setUserContext(spoofedIdentity()).setClientType(big.toString()).build());
  }

  @Test
  public void preservesFieldsFromANewerProtocolVersion() {
    // A field this build has never heard of. It is not merely retained -- the
    // wire rewrite never decodes it, so its bytes are copied verbatim.
    final UnknownFieldSet unknown = UnknownFieldSet.newBuilder()
        .addField(4242, UnknownFieldSet.Field.newBuilder()
            .addVarint(7)
            .addLengthDelimited(ByteString.copyFromUtf8("from-the-future"))
            .build())
        .build();
    final ExecutePlanRequest request = ExecutePlanRequest.newBuilder()
        .setSessionId("s").setUserContext(spoofedIdentity()).build()
        .toBuilder().setUnknownFields(unknown).build();

    assertRewrite(request);

    final byte[] rewritten = interceptor.assertIdentity(request.toByteArray(), PRINCIPAL);
    final ExecutePlanRequest reparsed;
    try {
      reparsed = ExecutePlanRequest.parseFrom(rewritten);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    assertEquals(7L, reparsed.getUnknownFields().getField(4242).getVarintList().get(0).longValue());
    assertEquals(ByteString.copyFromUtf8("from-the-future"),
        reparsed.getUnknownFields().getField(4242).getLengthDelimitedList().get(0));
  }

  @Test
  public void leavesConfigOperationsUntouched() {
    // Config carries a nested operation the gateway knows nothing about; the
    // rewrite must leave every byte of it alone.
    final ConfigRequest request = ConfigRequest.newBuilder()
        .setSessionId("s")
        .setUserContext(spoofedIdentity())
        .setOperation(ConfigRequest.Operation.newBuilder()
            .setSet(ConfigRequest.Set.newBuilder()
                .addPairs(KeyValue.newBuilder().setKey("spark.sql.shuffle.partitions").setValue("8"))))
        .build();

    assertRewrite(request);
  }

  @Test
  public void rejectsBytesThatAreNotAProtobufMessage() {
    // Truncated mid-varint. Forwarding would send the client's own claim on
    // untouched, so this has to fail rather than pass through.
    final byte[] truncated = {(byte) 0x92, (byte) 0x80};
    try {
      interceptor.assertIdentity(truncated, PRINCIPAL);
      fail("Expected malformed input to be rejected");
    } catch (ProtoWire.MalformedMessageException e) {
      assertEquals("varint runs past the end of the message", e.getMessage());
    }
  }

  @Test
  public void isIdempotent() {
    final ExecutePlanRequest request = ExecutePlanRequest.newBuilder()
        .setSessionId("s").setUserContext(spoofedIdentity()).build();

    final byte[] once = interceptor.assertIdentity(request.toByteArray(), PRINCIPAL);
    final byte[] twice = interceptor.assertIdentity(once, PRINCIPAL);

    assertArrayEquals("rewriting an already-rewritten message should change nothing", once, twice);
  }

  @Test
  public void acceptsALargeRequestWhoseIdentityIsAtTheFront() {
    // The scan limit bounds where the identity may sit, not how big a request may
    // be. Generated serializers emit fields in ascending number order, so
    // user_context = 2 lands near the front however large the payload after it.
    final StringBuilder payload = new StringBuilder();
    for (int i = 0; i < 100000; i++) {
      payload.append("xxxxxxxxxx");
    }
    assertRewrite(ExecutePlanRequest.newBuilder()
        .setSessionId("s")
        .setUserContext(spoofedIdentity())
        .setClientType(payload.toString())
        .build());
  }

  @Test
  public void refusesAnIdentityThatSitsBeyondTheScanLimit() {
    // Everything before user_context pushes it past a deliberately tiny limit.
    final StringBuilder prefix = new StringBuilder();
    for (int i = 0; i < 200; i++) {
      prefix.append('p');
    }
    final ExecutePlanRequest request = ExecutePlanRequest.newBuilder()
        .setSessionId(prefix.toString())
        .setUserContext(spoofedIdentity())
        .build();

    try {
      interceptorWithScanLimit(64).assertIdentity(request.toByteArray(), PRINCIPAL);
      fail("Expected an identity beyond the scan limit to be refused");
    } catch (IdentityAssertingInterceptor.UnassertableMessageException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("extends past the first 64 bytes"));
    }
  }

  @Test
  public void refusesAnIdentityContainerLargerThanTheScanLimit() {
    // The container starts at the very front but runs long. Measuring the limit
    // against where a field ends is what bounds the copying the rewrite does.
    final StringBuilder padding = new StringBuilder();
    for (int i = 0; i < 4096; i++) {
      padding.append('x');
    }
    final ExecutePlanRequest request = ExecutePlanRequest.newBuilder()
        .setUserContext(UserContext.newBuilder()
            .setUserId(SPOOFED)
            .setUserName(padding.toString())
            .build())
        .build();

    try {
      interceptorWithScanLimit(1024).assertIdentity(request.toByteArray(), PRINCIPAL);
      fail("Expected an oversized identity container to be refused");
    } catch (IdentityAssertingInterceptor.UnassertableMessageException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("extends past the first 1024 bytes"));
    }
  }

  @Test
  public void rewritesEveryOccurrenceOfARepeatedIdentityContainer() {
    // Two user_context records on the wire. Protobuf merges them, so one left
    // unasserted would override the one that was.
    final byte[] doubled = concat(
        ExecutePlanRequest.newBuilder().setSessionId("s")
            .setUserContext(spoofedIdentity()).build().toByteArray(),
        ExecutePlanRequest.newBuilder()
            .setUserContext(UserContext.newBuilder().setUserId(SPOOFED).build())
            .build().toByteArray());

    final byte[] rewritten = interceptor.assertIdentity(doubled, PRINCIPAL);
    final ExecutePlanRequest reparsed;
    try {
      reparsed = ExecutePlanRequest.parseFrom(rewritten);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    assertEquals("a repeated container let a spoofed identity survive the merge",
        PRINCIPAL, reparsed.getUserContext().getUserId());
  }

  private static byte[] concat(byte[] first, byte[] second) {
    final byte[] joined = new byte[first.length + second.length];
    System.arraycopy(first, 0, joined, 0, first.length);
    System.arraycopy(second, 0, joined, first.length, second.length);
    return joined;
  }

  private static IdentityAssertingInterceptor interceptorWithScanLimit(int limit) {
    return new IdentityAssertingInterceptor(
        IdentityRewritePolicy.parse("2.1=principal,2.2=principal", limit));
  }
}

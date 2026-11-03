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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

/**
 * Rewrite behaviour with no protocol in sight.
 * <p>
 * The oracle test proves the wire code matches typed semantics for one real
 * protocol. This one covers what the rules mechanism is supposed to do in
 * general: arbitrary depth, several rules at once, synthesis of an absent path,
 * and the refusals — because none of that should need a {@code .proto} file to
 * exercise, and a protocol-shaped test would quietly re-import the assumptions
 * this design exists to drop.
 */
public class IdentityRewritePolicyTest {

  private static final String PRINCIPAL = "alice";
  private static final String SPOOFED = "root";

  private static byte[] message(byte[]... records) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] record : records) {
      out.write(record, 0, record.length);
    }
    return out.toByteArray();
  }

  private static byte[] stringField(int field, String value) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    ProtoWire.writeLengthDelimited(out, field, value.getBytes(StandardCharsets.UTF_8));
    return out.toByteArray();
  }

  private static byte[] messageField(int field, byte[] nested) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    ProtoWire.writeLengthDelimited(out, field, nested);
    return out.toByteArray();
  }

  private static byte[] varintField(int field, long value) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    ProtoWire.writeTag(out, field, ProtoWire.WIRETYPE_VARINT);
    ProtoWire.writeVarint(out, value);
    return out.toByteArray();
  }

  private static byte[] rewrite(String rules, byte[] request) {
    return rewrite(rules, IdentityRewritePolicy.DEFAULT_SCAN_LIMIT, request);
  }

  private static byte[] rewrite(String rules, int scanLimit, byte[] request) {
    return new IdentityAssertingInterceptor(IdentityRewritePolicy.parse(rules, scanLimit))
        .assertIdentity(request, PRINCIPAL);
  }

  /** Reads a string field, following a dotted path of field numbers. */
  private static String read(byte[] buffer, int... path) {
    byte[] current = buffer;
    for (int depth = 0; depth < path.length - 1; depth++) {
      current = ProtoWire.firstLengthDelimited(current, path[depth]);
      if (current == null) {
        return null;
      }
    }
    final byte[] value = ProtoWire.firstLengthDelimited(current, path[path.length - 1]);
    return value == null ? null : new String(value, StandardCharsets.UTF_8);
  }

  @Test
  public void rewritesATopLevelField() {
    // Depth one: a protocol with a flat identity field, which the container-plus-
    // id shape could not express at all.
    final byte[] rewritten = rewrite("1=principal", stringField(1, SPOOFED));
    assertEquals(PRINCIPAL, read(rewritten, 1));
  }

  @Test
  public void rewritesArbitrarilyDeepPaths() {
    final byte[] request = messageField(4, messageField(3, messageField(2, stringField(1, SPOOFED))));
    final byte[] rewritten = rewrite("4.3.2.1=principal", request);
    assertEquals(PRINCIPAL, read(rewritten, 4, 3, 2, 1));
  }

  @Test
  public void appliesSeveralRulesInOnePass() {
    final byte[] request = messageField(2, message(stringField(1, SPOOFED), stringField(2, SPOOFED)));
    final byte[] rewritten = rewrite("2.1=principal,2.2=principal", request);
    assertEquals(PRINCIPAL, read(rewritten, 2, 1));
    assertEquals(PRINCIPAL, read(rewritten, 2, 2));
  }

  @Test
  public void synthesisesTheWholeChainWhenTheIdentityIsAbsent() {
    // Nothing to overwrite, so the container, its parent and the leaf are all
    // created: the backend must never see a request whose identity Knox did not
    // put there.
    final byte[] rewritten = rewrite("4.3.1=principal", stringField(9, "unrelated"));
    assertEquals(PRINCIPAL, read(rewritten, 4, 3, 1));
    assertEquals("unrelated", read(rewritten, 9));
  }

  @Test
  public void leavesEveryOtherByteAlone() {
    final byte[] untouched = message(
        stringField(1, "session"),
        varintField(7, 4242),
        stringField(9, "trailing"));
    final byte[] request = message(untouched, messageField(2, stringField(1, SPOOFED)));

    final byte[] rewritten = rewrite("2.1=principal", request);

    assertEquals("session", read(rewritten, 1));
    assertEquals("trailing", read(rewritten, 9));
    assertArrayEquals("bytes outside the identity path were not copied verbatim",
        untouched, java.util.Arrays.copyOfRange(rewritten, 0, untouched.length));
  }

  @Test
  public void isIdempotent() {
    final byte[] request = messageField(2, message(stringField(1, SPOOFED), stringField(2, SPOOFED)));
    final byte[] once = rewrite("2.1=principal,2.2=principal", request);
    final byte[] twice = rewrite("2.1=principal,2.2=principal", once);
    assertArrayEquals(once, twice);
  }

  @Test
  public void refusesAFieldWhoseWireTypeContradictsTheRules() {
    // Field 2 is a varint here, so it is neither a value to replace nor a message
    // to descend into. Skipping it would forward the caller's own claim.
    try {
      rewrite("2.1=principal", varintField(2, 1));
      fail("Expected a wire type mismatch to be refused");
    } catch (IdentityAssertingInterceptor.UnassertableMessageException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("do not describe this message"));
    }
  }

  @Test
  public void refusesAnIdentityBeyondTheScanLimit() {
    final StringBuilder padding = new StringBuilder();
    for (int i = 0; i < 500; i++) {
      padding.append('x');
    }
    final byte[] request = message(
        stringField(1, padding.toString()),
        messageField(2, stringField(1, SPOOFED)));

    try {
      rewrite("2.1=principal", 128, request);
      fail("Expected an identity beyond the scan limit to be refused");
    } catch (IdentityAssertingInterceptor.UnassertableMessageException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("extends past the first 128 bytes"));
    }
  }

  @Test
  public void synthesisesBeyondTheScanLimitWhenNoIdentityIsPresent() {
    // Nothing was found anywhere, so there is nothing an appended identity could
    // be overridden by, and the limit has nothing to say about it.
    final StringBuilder padding = new StringBuilder();
    for (int i = 0; i < 500; i++) {
      padding.append('x');
    }
    final byte[] rewritten =
        rewrite("2.1=principal", 128, stringField(1, padding.toString()));
    assertEquals(PRINCIPAL, read(rewritten, 2, 1));
  }

  @Test
  public void anEmptyRuleSetRewritesNothing() {
    assertTrue(IdentityRewritePolicy.parse(null, 1024).isEmpty());
    assertTrue(IdentityRewritePolicy.parse("   ", 1024).isEmpty());
    final byte[] request = stringField(1, SPOOFED);
    assertArrayEquals(request, rewrite("", request));
  }

  @Test
  public void rejectsRulesThatContradictEachOther() {
    assertRejected("2=principal,2.1=principal", "writes a value to");
    assertRejected("2.1=principal,2.1=principal", "collides");
  }

  @Test
  public void rejectsMalformedRules() {
    assertRejected("2.1", "path=subject");
    assertRejected("2.1=", "must name a subject");
    assertRejected("2.1=nonsense", "Unknown identity subject");
    assertRejected("=principal", "at least one field number");
    assertRejected("2.x=principal", "only field numbers");
    assertRejected("0=principal", "between 1 and");
    assertRejected("19001=principal", "reserved by protobuf");
  }

  @Test
  public void rejectsANonPositiveScanLimit() {
    try {
      IdentityRewritePolicy.parse("2.1=principal", 0);
      fail("Expected a non-positive scan limit to be rejected");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("must be positive"));
    }
  }

  private static void assertRejected(String rules, String expectedFragment) {
    try {
      IdentityRewritePolicy.parse(rules, IdentityRewritePolicy.DEFAULT_SCAN_LIMIT);
      fail("Expected '" + rules + "' to be rejected");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().contains(expectedFragment));
    }
  }
}

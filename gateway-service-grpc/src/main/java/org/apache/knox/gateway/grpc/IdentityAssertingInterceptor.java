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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.grpc.Status;

/**
 * Replaces the caller's claimed identity with the authenticated one, working
 * directly on the wire format.
 * <p>
 * This is the whole reason the gateway looks inside messages at all. Protocols
 * in this family commonly trust a <em>client-asserted</em> identity field: the
 * client states who it is and the server believes it. Such a field typically
 * keys the server-side session cache, so leaving it alone would let one caller
 * collide with — or attach to — another's session simply by claiming their name.
 * Overwriting it is what makes sessions isolated and the audit trail meaningful.
 * <p>
 * No schema is needed to do it. The identity lives at field numbers named by an
 * {@link IdentityRewritePolicy}, and every other byte of the message is copied
 * through verbatim — including fields from a newer protocol version this build
 * has never heard of, which are not merely preserved but never even decoded.
 * <p>
 * Three cases are refused rather than forwarded: a message that cannot be
 * parsed, a message whose shape contradicts the configured rules, and a message
 * whose identity fields lie beyond the policy's scan limit. All three share a
 * reason — if the identity cannot be replaced everywhere the rules say it lives,
 * then the caller's own claim would travel on somewhere, which is precisely what
 * this exists to prevent.
 */
public class IdentityAssertingInterceptor implements MessageInterceptor<byte[]> {

  private final IdentityRewritePolicy policy;
  private final PrincipalSource principalSource;

  /** Supplies the principal for the call in flight. */
  @FunctionalInterface
  public interface PrincipalSource {
    String currentPrincipal();
  }

  public IdentityAssertingInterceptor(IdentityRewritePolicy policy, PrincipalSource principalSource) {
    this.policy = policy;
    this.principalSource = principalSource;
  }

  /** Uses the principal the authentication interceptor put in the call context. */
  public IdentityAssertingInterceptor(IdentityRewritePolicy policy) {
    this(policy, () -> {
      final GrpcCallContext callContext = GrpcCallContext.current();
      return callContext == null ? null : callContext.getPrincipal();
    });
  }

  @Override
  public byte[] intercept(byte[] message) {
    final String principal = principalSource.currentPrincipal();
    if (principal == null || principal.isEmpty()) {
      // Authentication runs before any handler, so this cannot happen unless the
      // interceptor chain was assembled wrongly. Forwarding would send the
      // client's own claim through untouched.
      throw Status.INTERNAL
          .withDescription("No authenticated principal available for identity assertion")
          .asRuntimeException();
    }
    try {
      return assertIdentity(message, principal);
    } catch (ProtoWire.MalformedMessageException e) {
      throw Status.INVALID_ARGUMENT
          .withDescription("Request message is not a well-formed protobuf message")
          .withCause(e)
          .asRuntimeException();
    } catch (UnassertableMessageException e) {
      throw Status.INVALID_ARGUMENT
          .withDescription(e.getMessage())
          .withCause(e)
          .asRuntimeException();
    }
  }

  /**
   * Returns the message with every configured identity field replaced.
   *
   * @param message the request as received
   * @param principal the authenticated principal
   * @return the request to forward
   * @throws ProtoWire.MalformedMessageException if the message cannot be parsed
   * @throws UnassertableMessageException if the message's shape contradicts the
   *         rules, or an identity field lies beyond the scan limit
   */
  public byte[] assertIdentity(byte[] message, String principal) {
    if (policy.isEmpty()) {
      return message;
    }
    return rewrite(message, policy.root(), principal, 0);
  }

  /**
   * Rewrites one message — the request itself, or a nested message a rule
   * descends into.
   *
   * @param buffer the bytes of this message
   * @param node the rules that apply at this depth
   * @param principal the authenticated principal
   * @param baseOffset where {@code buffer} begins within the request as a whole,
   *        so the scan limit is measured against the message the client sent
   *        rather than against each nested message separately
   * @return the rewritten bytes
   */
  private byte[] rewrite(byte[] buffer, IdentityRewritePolicy.Node node, String principal,
                         int baseOffset) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream(buffer.length + 32);
    final Set<Integer> rewritten = new HashSet<>();

    int pos = 0;
    while (pos < buffer.length) {
      final int recordStart = pos;
      final ProtoWire.Varint tag = ProtoWire.readVarint(buffer, pos);
      pos = tag.end();
      final int field = ProtoWire.fieldNumber(tag.value());
      final int wire = ProtoWire.wireType(tag.value());
      final int[] bounds = ProtoWire.valueBounds(buffer, pos, wire);

      final IdentityRewritePolicy.Node child = node.child(field);
      if (child == null) {
        out.write(buffer, recordStart, bounds[1] - recordStart);
      } else {
        requireWithinScanLimit(field, baseOffset + bounds[1]);
        requireLengthDelimited(field, wire, child);
        if (child.isLeaf()) {
          write(out, field, child, principal);
        } else {
          final byte[] nested = ProtoWire.slice(buffer, bounds[0], bounds[1]);
          ProtoWire.writeLengthDelimited(out, field,
              rewrite(nested, child, principal, baseOffset + bounds[0]));
        }
        // Every occurrence is rewritten, not just the first: protobuf merges
        // repeats, so one left alone could override the one we asserted.
        rewritten.add(field);
      }
      pos = bounds[1];
    }

    // A client that sent no identity at all still gets one, at whatever depth the
    // rules put it; the backend must never see a request whose identity Knox did
    // not put there. Appending is safe however large the message is, because
    // nothing was found to be overridden by — the walk above covered every byte.
    for (Map.Entry<Integer, IdentityRewritePolicy.Node> entry : node.children().entrySet()) {
      if (rewritten.contains(entry.getKey())) {
        continue;
      }
      final IdentityRewritePolicy.Node child = entry.getValue();
      if (child.isLeaf()) {
        write(out, entry.getKey(), child, principal);
      } else {
        ProtoWire.writeLengthDelimited(out, entry.getKey(),
            rewrite(new byte[0], child, principal, baseOffset));
      }
    }
    return out.toByteArray();
  }

  private static void write(ByteArrayOutputStream out, int field,
                            IdentityRewritePolicy.Node leaf, String principal) {
    ProtoWire.writeLengthDelimited(out, field,
        leaf.subject().resolve(principal).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Refuses a field that ends beyond the scan limit.
   * <p>
   * Measured against the end rather than the start, so the bound covers what the
   * rewrite has to copy: a container beginning in the first few bytes but running
   * to a hundred megabytes costs as much as one that begins late.
   */
  private void requireWithinScanLimit(int field, int endOffset) {
    if (endOffset > policy.getScanLimit()) {
      throw new UnassertableMessageException("Identity field " + field
          + " extends past the first " + policy.getScanLimit()
          + " bytes of the request, so the authenticated identity cannot be asserted over it");
    }
  }

  /**
   * Refuses a field whose wire type contradicts the rules. A rule expects a
   * string to overwrite or a message to descend into, and both are
   * length-delimited; anything else means the configuration does not describe
   * this protocol. Skipping it quietly would forward the caller's own claim.
   */
  private static void requireLengthDelimited(int field, int wire,
                                             IdentityRewritePolicy.Node node) {
    if (wire != ProtoWire.WIRETYPE_LENGTH_DELIMITED) {
      throw new UnassertableMessageException("Identity field " + field + " is a "
          + (node.isLeaf() ? "value to replace" : "message to descend into")
          + " but arrived with wire type " + wire
          + "; the configured identity rules do not describe this message");
    }
  }

  /**
   * Signals a message the configured rules cannot be applied to in full. Distinct
   * from malformed input: the bytes parse, but their shape and the configuration
   * disagree, or the identity sits further into the request than the policy
   * allows.
   */
  public static class UnassertableMessageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UnassertableMessageException(String message) {
      super(message);
    }
  }
}

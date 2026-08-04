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
import java.util.ArrayList;
import java.util.List;

/**
 * The little of the protobuf wire format the gateway needs in order to find and
 * replace a field without knowing the schema.
 * <p>
 * A protobuf message is a flat sequence of records, each introduced by a tag
 * holding a field number and a wire type. The wire type says how long the value
 * is; the field number says which field it is. Nothing else — not the field's
 * name, its declared type, nor the message it belongs to — is on the wire. That
 * is what lets a proxy rewrite one field of a message it has no schema for, and
 * copy every other byte through untouched.
 * <p>
 * Field numbers are also the one thing protobuf guarantees never changes: a
 * schema may add, rename or deprecate fields, but renumbering an existing one
 * breaks every deployed client. Depending on a field number is therefore a much
 * weaker coupling than depending on a generated class.
 */
public final class ProtoWire {

  public static final int WIRETYPE_VARINT = 0;
  public static final int WIRETYPE_FIXED64 = 1;
  public static final int WIRETYPE_LENGTH_DELIMITED = 2;
  public static final int WIRETYPE_START_GROUP = 3;
  public static final int WIRETYPE_END_GROUP = 4;
  public static final int WIRETYPE_FIXED32 = 5;

  /** A varint is at most ten bytes; more than that is malformed, not merely large. */
  private static final int MAX_VARINT_BYTES = 10;

  private ProtoWire() {
  }

  /**
   * Signals input that is not a well-formed protobuf message.
   * <p>
   * The gateway treats this as fatal for the call rather than forwarding the
   * bytes: if a message cannot be parsed then the identity field cannot be
   * replaced, and forwarding it would pass the caller's own claim through
   * unaltered — the exact substitution this layer exists to prevent.
   */
  public static class MalformedMessageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MalformedMessageException(String message) {
      super(message);
    }
  }

  /** A decoded varint: its value, and the offset just past it. */
  public static final class Varint {
    private final long value;
    private final int end;

    Varint(long value, int end) {
      this.value = value;
      this.end = end;
    }

    public long value() {
      return value;
    }

    public int end() {
      return end;
    }
  }

  /**
   * Reads a base-128 varint.
   *
   * @param buffer the message bytes
   * @param position offset of the first byte of the varint
   * @return the value and the offset just past it
   * @throws MalformedMessageException if the varint runs off the end or is over-long
   */
  public static Varint readVarint(byte[] buffer, int position) {
    long result = 0;
    int shift = 0;
    int pos = position;
    for (int read = 0; read < MAX_VARINT_BYTES; read++) {
      if (pos >= buffer.length) {
        throw new MalformedMessageException("varint runs past the end of the message");
      }
      final int b = buffer[pos++] & 0xFF;
      result |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return new Varint(result, pos);
      }
      shift += 7;
    }
    throw new MalformedMessageException("varint is longer than ten bytes");
  }

  public static void writeVarint(ByteArrayOutputStream out, long value) {
    long remaining = value;
    while ((remaining & ~0x7FL) != 0) {
      out.write((int) ((remaining & 0x7F) | 0x80));
      remaining >>>= 7;
    }
    out.write((int) remaining);
  }

  public static void writeTag(ByteArrayOutputStream out, int fieldNumber, int wireType) {
    writeVarint(out, ((long) fieldNumber << 3) | wireType);
  }

  /**
   * Writes a length-delimited field: tag, byte length, then the bytes.
   *
   * @param out destination
   * @param fieldNumber the field number to write
   * @param value the field's bytes
   */
  public static void writeLengthDelimited(ByteArrayOutputStream out, int fieldNumber, byte[] value) {
    writeTag(out, fieldNumber, WIRETYPE_LENGTH_DELIMITED);
    writeVarint(out, value.length);
    out.write(value, 0, value.length);
  }

  /**
   * Locates the value of the record beginning at {@code position}, which must be
   * just past the record's tag.
   *
   * @param buffer the message bytes
   * @param position offset just past the tag
   * @param wireType the wire type taken from the tag
   * @return offsets of the value: {@code [start, end)}
   * @throws MalformedMessageException on an unusable wire type or a length that
   *         overruns the buffer
   */
  public static int[] valueBounds(byte[] buffer, int position, int wireType) {
    switch (wireType) {
      case WIRETYPE_VARINT: {
        final Varint v = readVarint(buffer, position);
        return new int[] {position, v.end()};
      }
      case WIRETYPE_FIXED64:
        return new int[] {position, requireWithin(buffer, position + 8)};
      case WIRETYPE_LENGTH_DELIMITED: {
        final Varint length = readVarint(buffer, position);
        if (length.value() < 0 || length.value() > Integer.MAX_VALUE) {
          throw new MalformedMessageException("length-delimited field declares a negative or huge length");
        }
        final int start = length.end();
        return new int[] {start, requireWithin(buffer, start + (int) length.value())};
      }
      case WIRETYPE_FIXED32:
        return new int[] {position, requireWithin(buffer, position + 4)};
      case WIRETYPE_START_GROUP:
      case WIRETYPE_END_GROUP:
        // Groups were removed from proto3 and no protobuf schema in use here emits them.
        // Refusing is safer than skipping bytes whose extent we would have to guess.
        throw new MalformedMessageException("group wire types are not supported");
      default:
        throw new MalformedMessageException("unknown wire type " + wireType);
    }
  }

  private static int requireWithin(byte[] buffer, int end) {
    if (end < 0 || end > buffer.length) {
      throw new MalformedMessageException("field extends past the end of the message");
    }
    return end;
  }

  public static int fieldNumber(long tag) {
    return (int) (tag >>> 3);
  }

  public static int wireType(long tag) {
    return (int) (tag & 0x7);
  }

  public static byte[] slice(byte[] buffer, int from, int to) {
    final byte[] copy = new byte[to - from];
    System.arraycopy(buffer, from, copy, 0, to - from);
    return copy;
  }

  /**
   * Returns the value of the first length-delimited record with the given field
   * number, for reading into a nested message or string without a schema.
   *
   * @param buffer the message bytes
   * @param fieldNumber the field to look for
   * @return the field's bytes, or null if absent
   * @throws MalformedMessageException if the message is not well formed
   */
  public static byte[] firstLengthDelimited(byte[] buffer, int fieldNumber) {
    final List<byte[]> found = collect(buffer, fieldNumber, true);
    return found.isEmpty() ? null : found.get(0);
  }

  /**
   * Returns every length-delimited record with the given field number, in order,
   * as a repeated field is encoded.
   *
   * @param buffer the message bytes
   * @param fieldNumber the field to look for
   * @return the values, possibly empty
   * @throws MalformedMessageException if the message is not well formed
   */
  public static List<byte[]> allLengthDelimited(byte[] buffer, int fieldNumber) {
    return collect(buffer, fieldNumber, false);
  }

  private static List<byte[]> collect(byte[] buffer, int fieldNumber, boolean stopAtFirst) {
    final List<byte[]> values = new ArrayList<>();
    int pos = 0;
    while (pos < buffer.length) {
      final Varint tag = readVarint(buffer, pos);
      pos = tag.end();
      final int field = fieldNumber(tag.value());
      final int wire = wireType(tag.value());
      final int[] bounds = valueBounds(buffer, pos, wire);
      if (field == fieldNumber && wire == WIRETYPE_LENGTH_DELIMITED) {
        values.add(slice(buffer, bounds[0], bounds[1]));
        if (stopAtFirst) {
          return values;
        }
      }
      pos = bounds[1];
    }
    return values;
  }
}

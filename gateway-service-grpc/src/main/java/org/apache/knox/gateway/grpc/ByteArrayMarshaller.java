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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import io.grpc.MethodDescriptor;
import io.grpc.Status;

/**
 * Passes message bodies through as opaque bytes.
 * <p>
 * With this marshaller the gateway can relay a call whose message types it has
 * no generated classes for, which is what makes the fallback path work for
 * methods outside the vendored protos — a newer client calling an RPC added
 * after this build still gets proxied rather than rejected.
 */
public final class ByteArrayMarshaller implements MethodDescriptor.Marshaller<byte[]> {

  public static final ByteArrayMarshaller INSTANCE = new ByteArrayMarshaller();

  private ByteArrayMarshaller() {
  }

  @Override
  public InputStream stream(byte[] value) {
    return new ByteArrayInputStream(value);
  }

  @Override
  public byte[] parse(InputStream stream) {
    try {
      // grpc hands over the complete message, so a single drain is enough and the
      // inbound size limit has already been applied by the transport.
      return readAll(stream);
    } catch (IOException e) {
      throw Status.INTERNAL.withDescription("Failed to read gRPC message").withCause(e).asRuntimeException();
    }
  }

  private static byte[] readAll(InputStream stream) throws IOException {
    final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
    final byte[] chunk = new byte[8192];
    int read;
    while ((read = stream.read(chunk)) != -1) {
      buffer.write(chunk, 0, read);
    }
    return buffer.toByteArray();
  }
}

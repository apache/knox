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

import java.util.Locale;

import org.apache.knox.gateway.sparkconnect.SparkConnectMessageInterceptor.RequestGuard;

import com.google.protobuf.Message;

import io.grpc.Status;

/**
 * Refuses client writes to session-configuration keys reserved for Knox.
 * <p>
 * A deployment may publish the authenticated identity into the Spark session as
 * a configuration entry, which downstream authorization then reads. If a client
 * could overwrite that entry it could assume any identity it liked, so
 * {@code Set} and {@code Unset} on the reserved prefix are rejected outright.
 * <p>
 * This covers the structured path completely and cheaply, because the keys are
 * named fields in the {@code Config} RPC. It does not cover
 * {@code SET reserved.key=...} issued as SQL inside {@code ExecutePlan}, which
 * would need plan-text inspection and would still be best-effort. That gap is
 * the argument for the stronger server-side arrangement, where an interceptor in
 * the Spark application recomputes the identity from {@code user_context} on
 * every request: a value derived per request cannot be overwritten by a session
 * {@code SET} at all.
 */
public class ReservedConfigGuard implements RequestGuard {

  private static final String OPERATION_FIELD = "operation";
  private static final String SET_FIELD = "set";
  private static final String UNSET_FIELD = "unset";
  private static final String PAIRS_FIELD = "pairs";
  private static final String KEYS_FIELD = "keys";
  private static final String KEY_FIELD = "key";

  private final String reservedPrefix;

  public ReservedConfigGuard(String reservedPrefix) {
    this.reservedPrefix = reservedPrefix == null ? "" : reservedPrefix.toLowerCase(Locale.ROOT);
  }

  @Override
  public void check(Message request, String principal) {
    if (reservedPrefix.isEmpty()) {
      return;
    }
    final Message operation = childMessage(request, OPERATION_FIELD);
    if (operation == null) {
      return;
    }

    final Message set = childMessage(operation, SET_FIELD);
    if (set != null) {
      final com.google.protobuf.Descriptors.FieldDescriptor pairs =
          set.getDescriptorForType().findFieldByName(PAIRS_FIELD);
      if (pairs != null) {
        final int count = set.getRepeatedFieldCount(pairs);
        for (int i = 0; i < count; i++) {
          final Message pair = (Message) set.getRepeatedField(pairs, i);
          final com.google.protobuf.Descriptors.FieldDescriptor key =
              pair.getDescriptorForType().findFieldByName(KEY_FIELD);
          if (key != null) {
            reject(String.valueOf(pair.getField(key)));
          }
        }
      }
    }

    final Message unset = childMessage(operation, UNSET_FIELD);
    if (unset != null) {
      final com.google.protobuf.Descriptors.FieldDescriptor keys =
          unset.getDescriptorForType().findFieldByName(KEYS_FIELD);
      if (keys != null) {
        final int count = unset.getRepeatedFieldCount(keys);
        for (int i = 0; i < count; i++) {
          reject(String.valueOf(unset.getRepeatedField(keys, i)));
        }
      }
    }
  }

  private void reject(String key) {
    if (key != null && key.toLowerCase(Locale.ROOT).startsWith(reservedPrefix)) {
      throw Status.PERMISSION_DENIED
          .withDescription("Session configuration keys beginning with '" + reservedPrefix
              + "' are reserved by the gateway and cannot be set or unset by clients")
          .asRuntimeException();
    }
  }

  /**
   * Returns a singular message-valued field only when it is actually present, so
   * an absent branch of the {@code op_type} oneof does not read as an empty
   * {@code Set}.
   */
  private static Message childMessage(Message parent, String fieldName) {
    final com.google.protobuf.Descriptors.FieldDescriptor field =
        parent.getDescriptorForType().findFieldByName(fieldName);
    if (field == null || !parent.hasField(field)) {
      return null;
    }
    final Object value = parent.getField(field);
    return value instanceof Message ? (Message) value : null;
  }
}

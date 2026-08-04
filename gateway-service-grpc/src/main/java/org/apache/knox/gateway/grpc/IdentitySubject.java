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

import java.util.Arrays;
import java.util.Locale;

/**
 * Which attribute of the authenticated caller a rewrite rule writes.
 * <p>
 * A rule names a place in the message and a subject; this is the subject half.
 * Keeping it explicit is what stops the gateway assuming that two fields in the
 * same container mean "id" and "display name" — a convention of one protocol
 * rather than a property of protobuf.
 * <p>
 * The vocabulary is deliberately limited to what authentication actually
 * establishes. Anything a deployment wishes were assertable but that Knox does
 * not know is better refused at startup than written as an empty string.
 */
public enum IdentitySubject {

  /** The authenticated principal: the subject of the validated bearer token. */
  PRINCIPAL {
    @Override
    public String resolve(String principal) {
      return principal;
    }
  };

  /**
   * Returns the value to write for this subject.
   *
   * @param principal the authenticated principal for the call in flight
   * @return the value to write
   */
  public abstract String resolve(String principal);

  /**
   * Parses a subject name as written in configuration.
   *
   * @param value the configured name, case-insensitive
   * @return the subject
   * @throws IllegalArgumentException if the name is not one this build knows
   */
  public static IdentitySubject parse(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("A rewrite rule must name a subject, e.g. '2.1=principal'");
    }
    final String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (IdentitySubject subject : values()) {
      if (subject.name().toLowerCase(Locale.ROOT).equals(normalized)) {
        return subject;
      }
    }
    throw new IllegalArgumentException("Unknown identity subject '" + value.trim()
        + "'; supported subjects are " + Arrays.toString(values()).toLowerCase(Locale.ROOT));
  }
}

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

import java.util.Locale;

/**
 * One place in a request message where the authenticated identity is written,
 * expressed as field numbers rather than as a schema.
 * <p>
 * Written as {@code path=subject}, where the path is one or more protobuf field
 * numbers separated by dots and the subject names what to write there. Each
 * leading number is a nested message to descend into; the last is the string
 * field to replace. So {@code 1=principal} replaces a top-level field, and
 * {@code 2.1=principal} replaces a field one level down.
 * <p>
 * Naming numbers rather than compiling against generated classes is what keeps
 * the gateway free of any particular protocol version: a schema may gain fields,
 * rename them or deprecate them, but renumbering an existing field breaks every
 * deployed client, so the numbers are the stable part.
 */
public final class IdentityRewriteRule {

  /** Protobuf caps field numbers at 2^29-1. */
  private static final int MAX_FIELD_NUMBER = 536870911;
  private static final int RESERVED_FROM = 19000;
  private static final int RESERVED_TO = 19999;

  private final int[] path;
  private final IdentitySubject subject;

  private IdentityRewriteRule(int[] path, IdentitySubject subject) {
    this.path = path;
    this.subject = subject;
  }

  /**
   * Parses one rule.
   *
   * @param rule {@code path=subject}, for example {@code 2.1=principal}
   * @return the parsed rule
   * @throws IllegalArgumentException if the rule is not a dotted list of legal
   *         field numbers followed by a known subject
   */
  public static IdentityRewriteRule parse(String rule) {
    if (rule == null || rule.trim().isEmpty()) {
      throw new IllegalArgumentException("A rewrite rule must not be empty");
    }
    final String trimmed = rule.trim();
    final int separator = trimmed.indexOf('=');
    if (separator < 0) {
      throw new IllegalArgumentException(
          "A rewrite rule must be written as 'path=subject', got: " + trimmed);
    }
    final String pathPart = trimmed.substring(0, separator).trim();
    final IdentitySubject parsedSubject = IdentitySubject.parse(trimmed.substring(separator + 1));

    if (pathPart.isEmpty()) {
      throw new IllegalArgumentException(
          "A rewrite rule must name at least one field number, got: " + trimmed);
    }
    final String[] parts = pathPart.split("\\.", -1);
    final int[] parsedPath = new int[parts.length];
    for (int i = 0; i < parts.length; i++) {
      parsedPath[i] = parseFieldNumber(parts[i], trimmed);
    }
    return new IdentityRewriteRule(parsedPath, parsedSubject);
  }

  private static int parseFieldNumber(String value, String rule) {
    final int number;
    try {
      number = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "A rewrite rule path must contain only field numbers, got: " + rule, e);
    }
    if (number < 1 || number > MAX_FIELD_NUMBER) {
      throw new IllegalArgumentException(
          "Field numbers must be between 1 and " + MAX_FIELD_NUMBER + ", got: " + rule);
    }
    if (number >= RESERVED_FROM && number <= RESERVED_TO) {
      throw new IllegalArgumentException(
          "Field numbers " + RESERVED_FROM + "-" + RESERVED_TO
              + " are reserved by protobuf, got: " + rule);
    }
    return number;
  }

  /**
   * @return the field numbers to follow, outermost first; never empty
   */
  public int[] getPath() {
    return path.clone();
  }

  public IdentitySubject getSubject() {
    return subject;
  }

  @Override
  public String toString() {
    final StringBuilder text = new StringBuilder(16);
    for (int i = 0; i < path.length; i++) {
      if (i > 0) {
        text.append('.');
      }
      text.append(path[i]);
    }
    return text.append('=').append(subject.name().toLowerCase(Locale.ROOT)).toString();
  }
}

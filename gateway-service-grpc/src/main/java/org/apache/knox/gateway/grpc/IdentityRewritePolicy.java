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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The set of identity rewrite rules in force, compiled for one pass over a
 * message.
 * <p>
 * Zero rules is the ordinary case for a protocol that carries no identity: the
 * relay is then a pure pipe. Where there are rules, they are compiled into a
 * tree keyed by field number, so rules sharing a prefix — {@code 2.1} and
 * {@code 2.2}, say — descend into that container once rather than once each.
 *
 * <h2>The scan limit</h2>
 * Every field a rule touches must lie wholly within the first
 * {@link #getScanLimit()} bytes of the message. This bounds what identity
 * assertion can be made to do: rewriting a nested field means slicing it out and
 * rebuilding it, so without a limit a client could put a hundred megabytes
 * inside the identity container and make the gateway copy it several times over.
 * <p>
 * It is a rejection rather than a truncation, and that is the security-relevant
 * part. Giving up on a rule that sits beyond the limit — and synthesising a
 * fresh identity instead — would leave the caller's own claim in the message
 * behind ours, where protobuf's last-wins merge semantics would let it take
 * effect. A message we cannot fully assert over is one we must not forward.
 * <p>
 * Messages are not otherwise constrained: a large request whose identity sits at
 * the front, which is what generated serializers emit, passes regardless of its
 * total size.
 */
public final class IdentityRewritePolicy {

  /**
   * 128 KiB. Comfortably past any identity container a real protocol declares,
   * while keeping the worst-case rewrite cost of a 128 MB message the same as
   * that of a small one.
   */
  public static final int DEFAULT_SCAN_LIMIT = 131072;

  private static final IdentityRewritePolicy NONE =
      new IdentityRewritePolicy(Collections.emptyList(), DEFAULT_SCAN_LIMIT, new Node());

  private final List<IdentityRewriteRule> rules;
  private final int scanLimit;
  private final Node root;

  private IdentityRewritePolicy(List<IdentityRewriteRule> rules, int scanLimit, Node root) {
    this.rules = rules;
    this.scanLimit = scanLimit;
    this.root = root;
  }

  /** @return a policy that rewrites nothing */
  public static IdentityRewritePolicy none() {
    return NONE;
  }

  /**
   * Parses a comma-separated list of rules.
   *
   * @param configuredRules for example {@code 2.1=principal, 2.2=principal};
   *        null or empty yields a policy that rewrites nothing
   * @param scanLimit the maximum offset, in bytes, at which a rewritten field may
   *        end
   * @return the compiled policy
   * @throws IllegalArgumentException if a rule is malformed, if two rules
   *         collide, or if the scan limit is not positive
   */
  public static IdentityRewritePolicy parse(String configuredRules, int scanLimit) {
    if (configuredRules == null || configuredRules.trim().isEmpty()) {
      return NONE;
    }
    if (scanLimit < 1) {
      throw new IllegalArgumentException("The identity scan limit must be positive, got: " + scanLimit);
    }
    final List<IdentityRewriteRule> parsed = new ArrayList<>();
    final Node newRoot = new Node();
    for (String entry : configuredRules.trim().split("\\s*,\\s*")) {
      if (entry.isEmpty()) {
        continue;
      }
      final IdentityRewriteRule rule = IdentityRewriteRule.parse(entry);
      add(newRoot, rule);
      parsed.add(rule);
    }
    if (parsed.isEmpty()) {
      return NONE;
    }
    return new IdentityRewritePolicy(Collections.unmodifiableList(parsed), scanLimit, newRoot);
  }

  /**
   * Inserts a rule into the tree, refusing the two ways rules can contradict each
   * other: writing the same place twice, and writing a value at a field another
   * rule descends through.
   */
  private static void add(Node root, IdentityRewriteRule rule) {
    Node current = root;
    for (final int field : rule.getPath()) {
      if (current.subject != null) {
        throw new IllegalArgumentException("Rule " + rule
            + " descends through field " + field + ", which another rule writes a value to");
      }
      Node child = current.children.get(field);
      if (child == null) {
        child = new Node();
        current.children.put(field, child);
      }
      current = child;
    }
    if (current.subject != null || !current.children.isEmpty()) {
      throw new IllegalArgumentException("Rule " + rule + " collides with an earlier rule");
    }
    current.subject = rule.getSubject();
  }

  /** @return true if this policy rewrites nothing, so the relay is a pure pipe */
  public boolean isEmpty() {
    return rules.isEmpty();
  }

  /**
   * @return the maximum offset, in bytes, at which a rewritten field may end
   */
  public int getScanLimit() {
    return scanLimit;
  }

  public List<IdentityRewriteRule> getRules() {
    return rules;
  }

  Node root() {
    return root;
  }

  @Override
  public String toString() {
    if (rules.isEmpty()) {
      return "none";
    }
    final StringBuilder text = new StringBuilder(48);
    for (IdentityRewriteRule rule : rules) {
      if (text.length() > 0) {
        text.append(',');
      }
      text.append(rule);
    }
    return text.append(" (scan limit ").append(scanLimit).append(" bytes)").toString();
  }

  /**
   * One field number in the compiled tree. A node either writes a value
   * ({@code subject} set, a leaf) or is descended through ({@code children}
   * populated) — {@link #add} refuses anything that would be both.
   */
  static final class Node {
    /** Insertion-ordered so synthesised fields come out in the order configured. */
    private final Map<Integer, Node> children = new LinkedHashMap<>();
    private IdentitySubject subject;

    Node child(int field) {
      return children.get(field);
    }

    Map<Integer, Node> children() {
      return children;
    }

    boolean isLeaf() {
      return subject != null;
    }

    IdentitySubject subject() {
      return subject;
    }
  }
}

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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Allows or denies whole RPCs by name, with no knowledge of what they carry.
 * <p>
 * gRPC puts the method in the request path — {@code /pkg.Service/Method} — so
 * this needs no marshaller, no descriptor and no schema. It is the coarsest
 * control the gateway offers and the only message-level one that survives
 * completely intact on a byte-level proxy.
 * <p>
 * The intended use is shutting off a capability that undermines whatever the
 * backend enforces. A deployment relying on the backend's own plan-level policy
 * can deny code upload outright, because uploaded code runs inside the backend
 * process with that process's credentials and answers to no plan check. Be clear
 * about the limit, though: denying an upload RPC does not close code execution
 * where a protocol also allows inline functions inside ordinary requests. It
 * shrinks the attack surface rather than drawing a boundary.
 * <p>
 * Names may be given bare ({@code AddArtifacts}) or fully qualified
 * ({@code pkg.Service/Method}); a bare name matches that method on any service.
 */
public class MethodAccessPolicy {

  private static final MethodAccessPolicy ALLOW_ALL =
      new MethodAccessPolicy(Collections.emptySet(), Collections.emptySet());

  private final Set<String> denied;
  private final Set<String> allowed;

  private MethodAccessPolicy(Set<String> denied, Set<String> allowed) {
    this.denied = denied;
    this.allowed = allowed;
  }

  public static MethodAccessPolicy allowAll() {
    return ALLOW_ALL;
  }

  /**
   * Builds a policy from comma-separated lists.
   *
   * @param denyList methods to refuse, or null/empty for none
   * @param allowList when non-empty, the only methods permitted; anything else is
   *        refused
   * @return the policy
   */
  public static MethodAccessPolicy of(String denyList, String allowList) {
    return new MethodAccessPolicy(split(denyList), split(allowList));
  }

  private static Set<String> split(String csv) {
    if (csv == null || csv.trim().isEmpty()) {
      return Collections.emptySet();
    }
    final Set<String> values = new LinkedHashSet<>();
    for (String entry : csv.trim().split("\\s*,\\s*")) {
      if (!entry.isEmpty()) {
        values.add(entry.toLowerCase(Locale.ROOT));
      }
    }
    return Collections.unmodifiableSet(values);
  }

  /**
   * Decides whether a call may proceed.
   *
   * @param fullMethodName the gRPC method name, {@code pkg.Service/Method}
   * @return true if permitted
   */
  public boolean isPermitted(String fullMethodName) {
    if (denied.isEmpty() && allowed.isEmpty()) {
      return true;
    }
    final String full = fullMethodName == null ? "" : fullMethodName.toLowerCase(Locale.ROOT);
    final String bare = full.substring(full.lastIndexOf('/') + 1);

    if (denied.contains(full) || denied.contains(bare)) {
      return false;
    }
    // An allow list, once given, is exhaustive: anything unnamed is refused, so
    // an RPC added by a newer protocol version does not appear by default.
    return allowed.isEmpty() || allowed.contains(full) || allowed.contains(bare);
  }

  /** @return true if this policy permits everything */
  public boolean isUnrestricted() {
    return denied.isEmpty() && allowed.isEmpty();
  }

  @Override
  public String toString() {
    return "deny=" + Arrays.toString(denied.toArray())
        + ", allow=" + Arrays.toString(allowed.toArray());
  }
}

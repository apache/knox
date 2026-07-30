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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.knox.gateway.sparkconnect.SparkConnectMessageInterceptor.RequestGuard;

import com.google.protobuf.Message;

import io.grpc.Status;

/**
 * Controls who may upload artifacts through {@code AddArtifacts}.
 * <p>
 * This is defense in depth, and it is worth being clear why it cannot be more
 * than that. A shared Spark Connect server runs as one principal, and any
 * user-supplied code — an uploaded jar, or an inline Python or Scala UDF
 * embedded in a plan — executes inside that JVM with that principal's storage
 * credentials. Such code can read data directly, bypassing any plan-level policy
 * check, and could subvert an in-JVM authorization plugin. Session-scoped
 * artifact classloaders isolate sessions from each other, not from the
 * application's own privileges.
 * <p>
 * So blocking artifact upload shrinks the attack surface; it does not create a
 * boundary, because inline UDFs remain a path to the same capability. This is a
 * property of plan-level enforcement in general rather than something the
 * gateway introduces. Deployments needing a hard boundary want per-user backends
 * instead.
 */
public class AddArtifactsGuard implements RequestGuard {

  /** Every user may upload artifacts. */
  public static final String MODE_ALLOW = "ALLOW";
  /** No user may upload artifacts. */
  public static final String MODE_DENY = "DENY";
  /** Only explicitly listed users may upload artifacts. */
  public static final String MODE_ALLOW_LISTED_USERS = "ALLOW_LISTED_USERS";

  private final String mode;
  private final Set<String> allowedUsers;

  public AddArtifactsGuard(String mode, Collection<String> allowedUsers) {
    this.mode = mode == null ? MODE_ALLOW : mode.trim().toUpperCase(Locale.ROOT);
    this.allowedUsers = allowedUsers == null
        ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(allowedUsers));
  }

  /**
   * Whether this guard would reject every call, letting the caller skip
   * per-message work entirely.
   *
   * @return true if no user may upload artifacts
   */
  public boolean deniesEveryone() {
    return MODE_DENY.equals(mode);
  }

  @Override
  public void check(Message request, String principal) {
    if (MODE_ALLOW.equals(mode)) {
      return;
    }
    if (MODE_ALLOW_LISTED_USERS.equals(mode) && allowedUsers.contains(principal)) {
      return;
    }
    throw Status.PERMISSION_DENIED
        .withDescription("Uploading artifacts through Spark Connect is not permitted for this user")
        .asRuntimeException();
  }
}

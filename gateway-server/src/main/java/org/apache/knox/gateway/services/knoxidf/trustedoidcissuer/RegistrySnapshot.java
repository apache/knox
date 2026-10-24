/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.services.knoxidf.trustedoidcissuer;

import java.util.Collections;
import java.util.Map;

/**
 * An immutable snapshot of the whole {@code TRUSTED_OIDC_ISSUERS} table, keyed by issuer URL,
 * together with the (ticker) time its DB read started. {@link RegistrySnapshotExpiry} computes
 * the hard expiry from that time rather than from the time the snapshot was put in the cache.
 */
final class RegistrySnapshot {
  private final Map<String, TrustedOidcIssuer> issuers;
  private final long loadedAtNanos;
  // true for the empty placeholder cached while the DB is unreachable after expiry
  private final boolean failedClosed;

  private RegistrySnapshot(Map<String, TrustedOidcIssuer> issuers, long loadedAtNanos, boolean failedClosed) {
    this.issuers = issuers;
    this.loadedAtNanos = loadedAtNanos;
    this.failedClosed = failedClosed;
  }

  static RegistrySnapshot of(Map<String, TrustedOidcIssuer> issuers, long loadedAtNanos) {
    return new RegistrySnapshot(Collections.unmodifiableMap(issuers), loadedAtNanos, false);
  }

  /**
   * Returns the empty snapshot that is served while the registry fails closed: no issuer is trusted.
   */
  static RegistrySnapshot failedClosed(long loadedAtNanos) {
    return new RegistrySnapshot(Collections.emptyMap(), loadedAtNanos, true);
  }

  Map<String, TrustedOidcIssuer> getIssuers() {
    return issuers;
  }

  long getLoadedAtNanos() {
    return loadedAtNanos;
  }

  boolean isFailedClosed() {
    return failedClosed;
  }
}

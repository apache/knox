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

import com.github.benmanes.caffeine.cache.Expiry;

/**
 * Expires a {@link RegistrySnapshot} once it is older than the TTL, measured from its own load
 * time. A failed background reload puts the old snapshot back, so this keeps the hard expiry
 * anchored to the last successful load rather than extending it.
 */
final class RegistrySnapshotExpiry implements Expiry<String, RegistrySnapshot> {
  private final long ttlNanos;

  RegistrySnapshotExpiry(long ttlNanos) {
    this.ttlNanos = ttlNanos;
  }

  @Override
  public long expireAfterCreate(String key, RegistrySnapshot value, long currentTime) {
    return remainingNanos(value, currentTime);
  }

  @Override
  public long expireAfterUpdate(String key, RegistrySnapshot value, long currentTime, long currentDuration) {
    return remainingNanos(value, currentTime);
  }

  @Override
  public long expireAfterRead(String key, RegistrySnapshot value, long currentTime, long currentDuration) {
    return currentDuration;
  }

  private long remainingNanos(RegistrySnapshot value, long currentTime) {
    return Math.max(0L, ttlNanos - (currentTime - value.getLoadedAtNanos()));
  }
}

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
package org.apache.knox.gateway.services.knoxidf.flowstate;

import org.apache.knox.gateway.config.GatewayConfig;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps the authorization flow state in the memory of this gateway instance, so HA needs load
 * balancer session affinity. Expired entries are purged on writes, at most once per cleanup
 * interval.
 */
public class InMemoryAuthzFlowStateService implements AuthzFlowStateService {

  private final ConcurrentMap<Key, Entry> entries = new ConcurrentHashMap<>();
  private final Clock clock;
  private final AtomicLong nextCleanup = new AtomicLong();
  private long ttlMillis = TimeUnit.SECONDS.toMillis(GatewayConfig.KNOXIDF_AUTHZ_FLOW_STATE_TTL_SECS_DEFAULT);
  private long cleanupIntervalMillis = TimeUnit.SECONDS.toMillis(GatewayConfig.KNOXIDF_AUTHZ_FLOW_STATE_CLEANUP_INTERVAL_SECS_DEFAULT);

  public InMemoryAuthzFlowStateService() {
    this(Clock.systemUTC());
  }

  InMemoryAuthzFlowStateService(Clock clock) {
    this.clock = clock;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) {
    if (config != null) {
      ttlMillis = TimeUnit.SECONDS.toMillis(config.getKnoxIDFAuthzFlowStateTtlSecs());
      cleanupIntervalMillis = TimeUnit.SECONDS.toMillis(config.getKnoxIDFAuthzFlowStateCleanupIntervalSecs());
    }
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
    entries.clear();
  }

  @Override
  public void put(AuthzFlowStateKind kind, String id, String payload) {
    final long now = clock.millis();
    removeExpiredIfDue(now);
    entries.put(new Key(kind, id), new Entry(payload, now + ttlMillis));
  }

  @Override
  public Optional<String> get(AuthzFlowStateKind kind, String id) {
    if (id == null) {
      return Optional.empty();
    }
    final Entry entry = entries.get(new Key(kind, id));
    return entry == null || entry.isExpired(clock.millis()) ? Optional.empty() : Optional.of(entry.payload);
  }

  @Override
  public Optional<String> consume(AuthzFlowStateKind kind, String id) {
    if (id == null) {
      return Optional.empty();
    }
    // ConcurrentMap#remove is atomic: of several concurrent callers only one gets the entry.
    final Entry entry = entries.remove(new Key(kind, id));
    return entry == null || entry.isExpired(clock.millis()) ? Optional.empty() : Optional.of(entry.payload);
  }

  @Override
  public void remove(AuthzFlowStateKind kind, String id) {
    if (id != null) {
      entries.remove(new Key(kind, id));
    }
  }

  @Override
  public int removeExpired() {
    final long now = clock.millis();
    int removed = 0;
    for (Map.Entry<Key, Entry> entry : entries.entrySet()) {
      if (entry.getValue().isExpired(now) && entries.remove(entry.getKey(), entry.getValue())) {
        removed++;
      }
    }
    return removed;
  }

  int size() {
    return entries.size();
  }

  private void removeExpiredIfDue(long now) {
    final long due = nextCleanup.get();
    if (now >= due && nextCleanup.compareAndSet(due, now + cleanupIntervalMillis)) {
      removeExpired();
    }
  }

  private record Key(AuthzFlowStateKind kind, String id) {
  }

  private static final class Entry {
    private final String payload;
    private final long expiresAt;

    private Entry(String payload, long expiresAt) {
      this.payload = payload;
      this.expiresAt = expiresAt;
    }

    private boolean isExpired(long now) {
      return expiresAt <= now;
    }
  }
}

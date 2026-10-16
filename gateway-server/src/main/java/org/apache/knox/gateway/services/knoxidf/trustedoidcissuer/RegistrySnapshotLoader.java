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

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Ticker;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads the trusted OIDC issuer {@link RegistrySnapshot} from the DB for the registry cache of
 * {@link JdbcTrustedOidcIssuerService}, and invalidates the OIDC discovery cache and
 * {@link IssuerTrustProvider} entries that a new snapshot makes stale.
 * <p>
 * Neither {@link #load} nor {@link #reload} throws: Caffeine would log a failed refresh through
 * java.util.logging on every attempt, and a failed load must fail closed rather than fail the
 * request. {@link #rebuild} does throw, for callers that must report a DB failure.
 */
final class RegistrySnapshotLoader implements CacheLoader<String, RegistrySnapshot> {

  private static final TrustedOidcIssuerServiceMessages LOG =
      MessagesFactory.get(TrustedOidcIssuerServiceMessages.class);

  private final TrustedOidcIssuerDatabase database;
  private final OIDCDiscoveryHelper discoveryHelper;
  private final IssuerTrustProvider trustProvider;
  private final Ticker ticker;
  private final long ttlNanos;
  private final long refreshNanos;
  // Set while the registry fails closed, so that state is logged once rather than per request
  private final AtomicBoolean failedClosed = new AtomicBoolean(false);

  RegistrySnapshotLoader(TrustedOidcIssuerDatabase database, OIDCDiscoveryHelper discoveryHelper,
      IssuerTrustProvider trustProvider, Ticker ticker, long ttlNanos, long refreshNanos) {
    this.database = database;
    this.discoveryHelper = discoveryHelper;
    this.trustProvider = trustProvider;
    this.ticker = ticker;
    this.ttlNanos = ttlNanos;
    this.refreshNanos = refreshNanos;
  }

  /*
   * Synchronous load: there is no snapshot (it expired), so nothing to diff against.
   */
  @Override
  public RegistrySnapshot load(String key) {
    try {
      return rebuild(null);
    } catch (Exception e) {
      if (failedClosed.compareAndSet(false, true)) {
        LOG.registrySnapshotFailedClosed(TimeUnit.NANOSECONDS.toSeconds(refreshNanos), e.toString(), e);
      } else {
        LOG.registrySnapshotStillFailedClosed(e.toString());
      }
      return RegistrySnapshot.failedClosed(ticker.read());
    }
  }

  /*
   * Background reload: the old snapshot is served until this completes.
   */
  @Override
  public RegistrySnapshot reload(String key, RegistrySnapshot oldValue) {
    try {
      return rebuild(oldValue);
    } catch (Exception e) {
      if (oldValue.isFailedClosed()) {
        LOG.registrySnapshotStillFailedClosed(e.toString());
      } else {
        final long ageNanos = ticker.read() - oldValue.getLoadedAtNanos();
        LOG.errorRefreshingRegistrySnapshotServingStale(TimeUnit.NANOSECONDS.toSeconds(ageNanos),
            TimeUnit.NANOSECONDS.toSeconds(Math.max(0L, ttlNanos - ageNanos)), e.toString(), e);
      }
      // Caffeine records the refresh as done, so the next attempt waits another refresh interval,
      // while RegistrySnapshotExpiry keeps the hard expiry anchored to the original load time
      return oldValue;
    }
  }

  /**
   * Reads a fresh snapshot from the DB and invalidates the discovery cache and CA trust entries it
   * makes stale: those of the issuers that changed since {@code previous}, or all of them when
   * there is no previous snapshot to diff against.
   *
   * @param previous the snapshot being replaced, or {@code null} if there is none
   * @return the fresh snapshot
   * @throws SQLException if the DB cannot be read; the caches are left untouched then
   */
  RegistrySnapshot rebuild(RegistrySnapshot previous) throws SQLException {
    final RegistrySnapshot fresh = readFromDatabase();
    if (previous == null) {
      discoveryHelper.invalidateAll();
      trustProvider.invalidateAll();
    } else {
      invalidateChangedIssuerEntries(previous, fresh);
    }
    if (failedClosed.compareAndSet(true, false)) {
      LOG.registrySnapshotRecovered(fresh.getIssuers().size());
    }
    return fresh;
  }

  boolean isFailedClosed() {
    return failedClosed.get();
  }

  private RegistrySnapshot readFromDatabase() throws SQLException {
    // Taken before the query so the snapshot age never under-reports how stale the data may be
    final long readAt = ticker.read();
    final Map<String, TrustedOidcIssuer> issuers = database.selectAll().stream()
        .collect(Collectors.toMap(TrustedOidcIssuer::getIssuerUrl, Function.identity()));
    return RegistrySnapshot.of(issuers, readAt);
  }

  /**
   * Invalidates the discovery cache and CA trust entries of every issuer that was added, removed
   * or changed between the two snapshots. A delete followed by a re-registration shows up as a
   * changed {@code registeredAt}, so the old discovery result and CA bundle cannot survive it.
   */
  private void invalidateChangedIssuerEntries(RegistrySnapshot previous, RegistrySnapshot fresh) {
    final Set<String> issuerUrls = new HashSet<>(previous.getIssuers().keySet());
    issuerUrls.addAll(fresh.getIssuers().keySet());
    int invalidated = 0;
    for (final String issuerUrl : issuerUrls) {
      if (!sameRegistration(previous.getIssuers().get(issuerUrl), fresh.getIssuers().get(issuerUrl))) {
        discoveryHelper.invalidate(issuerUrl);
        trustProvider.invalidate(issuerUrl);
        invalidated++;
      }
    }
    LOG.registrySnapshotReloaded(fresh.getIssuers().size(), invalidated);
  }

  private static boolean sameRegistration(TrustedOidcIssuer a, TrustedOidcIssuer b) {
    return a != null && b != null
        && a.isDynamicJwks() == b.isDynamicJwks()
        && Objects.equals(a.getRegisteredAt(), b.getRegisteredAt())
        && Objects.equals(a.getJwksCa(), b.getJwksCa());
  }
}

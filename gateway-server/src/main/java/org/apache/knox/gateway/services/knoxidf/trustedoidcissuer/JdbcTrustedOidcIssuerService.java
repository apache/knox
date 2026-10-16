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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.database.DataSourceProvider;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JDBC-backed implementation of {@link TrustedOidcIssuerService}.
 * <p>
 * The whole {@code TRUSTED_OIDC_ISSUERS} table is held in memory as one immutable
 * {@link RegistrySnapshot}, cached under a single key in a Caffeine {@link LoadingCache} that
 * {@link RegistrySnapshotLoader} loads and {@link RegistrySnapshotExpiry} expires. Reads ({@link #isTrusted},
 * {@link #isDynamicJwks}, {@link #list}) never touch the DB while the snapshot is fresh, and an
 * unknown {@code iss} can never trigger a DB query. Writes ({@link #register},
 * {@link #deregister}) are synchronized: the DB is committed first, then the snapshot is rebuilt
 * from a fresh SELECT and put into the cache, so a node always reads its own writes.
 * <p>
 * The TLS trust of issuers registered with a {@code jwksCa} is built by an
 * {@link IssuerTrustProvider} and discarded on register, deregister and
 * {@link #refreshJwksUri}, so the next fetch re-reads it.
 * <p>
 * HA note: each Knox node maintains its own snapshot, and changes made on another node become
 * visible within {@code gateway.trusted.oidc.issuer.registry.cache.ttl.secs} (TTL):
 * <ul>
 *   <li>Once a snapshot is older than half the TTL, the next read triggers a background reload;
 *   the current snapshot is served meanwhile, so request threads never wait on the DB.</li>
 *   <li>A snapshot is never served once it is older than the TTL (a hard bound for revocations).
 *   A failed background reload keeps the snapshot's original load time, so it cannot extend
 *   that bound.</li>
 *   <li>If the snapshot has expired and the DB cannot be read, the registry fails closed: no
 *   issuer is trusted until a reload succeeds. This is logged once at ERROR per failure period,
 *   and the recovery is logged at INFO.</li>
 * </ul>
 * Each node shortens its TTL by a random amount (up to 10%) so that nodes started together do
 * not reload in lockstep. Whenever the snapshot changes, the OIDC discovery cache and
 * {@link IssuerTrustProvider} entries of the added, removed or re-registered issuers are
 * invalidated as well.
 */
public class JdbcTrustedOidcIssuerService implements TrustedOidcIssuerService {

  private static final TrustedOidcIssuerServiceMessages LOG =
      MessagesFactory.get(TrustedOidcIssuerServiceMessages.class);

  // The registry is cached as one whole-table snapshot under this single key
  private static final String SNAPSHOT_KEY = TrustedOidcIssuerDatabase.TABLE_NAME;
  private static final double MAX_TTL_JITTER_FRACTION = 0.1;

  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final Lock initLock = new ReentrantLock(true);
  private final Ticker ticker;
  private final Executor refreshExecutor;

  private RegistrySnapshotLoader registrySnapshotLoader;
  private LoadingCache<String, RegistrySnapshot> registrySnapshot;

  private AliasService aliasService;
  private TrustedOidcIssuerDatabase database;
  private OIDCDiscoveryHelper discoveryHelper;
  private IssuerTrustProvider trustProvider;
  private int maxTrustedIssuers;

  // Overridable in tests; by default Secrets are read through the in-cluster Kubernetes API.
  private IssuerTrustProvider.SecretReader secretReader;
  private Clock clock = Clock.systemUTC();

  public JdbcTrustedOidcIssuerService() {
    this(Ticker.systemTicker(), ForkJoinPool.commonPool());
  }

  /**
   * Allows tests to control time and to run background reloads on the calling thread.
   */
  JdbcTrustedOidcIssuerService(Ticker ticker, Executor refreshExecutor) {
    this.ticker = ticker;
    this.refreshExecutor = refreshExecutor;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    if (!initialized.get()) {
      initLock.lock();
      try {
        // Double-checked locking: re-test under the lock so a thread that blocked while another was
        // initialising does not re-initialise the database/discoveryHelper a second time (mirrors
        // JdbcFederatedIdentityService). Without this inner check a startup race overwrote the
        // already-built database and discoveryHelper references.
        if (!initialized.get()) {
          if (aliasService == null) {
            throw new ServiceLifecycleException("The required AliasService reference has not been set.");
          }
          try {
            this.maxTrustedIssuers = config.getTrustedOidcIssuerMaxTrustedIssuers();
            this.database = new TrustedOidcIssuerDatabase(
                DataSourceProvider.getDataSource(config, aliasService), config.getDatabaseType());
            final int connectTimeoutMs = config.getTrustedOidcIssuerDiscoveryConnectTimeoutMs();
            final int readTimeoutMs = config.getTrustedOidcIssuerDiscoveryReadTimeoutMs();
            this.discoveryHelper = new OIDCDiscoveryHelper(this, config.getTrustedOidcIssuerDiscoveryCacheTtlSecs(),
                OIDCDiscoveryHelper.buildHttpClient(connectTimeoutMs, readTimeoutMs),
                sslContext -> OIDCDiscoveryHelper.buildHttpClient(connectTimeoutMs, readTimeoutMs, sslContext));
            this.trustProvider = new IssuerTrustProvider(issuerUrl -> issuers().get(issuerUrl),
                Duration.ofSeconds(config.getTrustedOidcIssuerCaSecretMaxAgeSecs()),
                secretReader == null ? new IssuerTrustProvider.KubernetesSecretReader() : secretReader, clock);
            this.registrySnapshot = buildRegistrySnapshotCache(config.getTrustedOidcIssuerRegistryCacheTtlSecs());
            // Eager first load: an unreachable DB at startup still fails init
            reloadRegistrySnapshot();
            initialized.set(true);
          } catch (ServiceLifecycleException e) {
            throw e;
          } catch (Exception e) {
            throw new ServiceLifecycleException("Error initializing JdbcTrustedOidcIssuerService: " + e, e);
          }
        }
      } finally {
        initLock.unlock();
      }
    }
  }

  private LoadingCache<String, RegistrySnapshot> buildRegistrySnapshotCache(int configuredTtlSecs) {
    final int ttlSecs = configuredTtlSecs > 0
        ? configuredTtlSecs : GatewayConfig.TRUSTED_OIDC_ISSUER_REGISTRY_CACHE_TTL_SECS_DEFAULT;
    final long ttlMs = TimeUnit.SECONDS.toMillis(ttlSecs);
    final long jitterMs = ThreadLocalRandom.current().nextLong((long) (ttlMs * MAX_TTL_JITTER_FRACTION) + 1);
    final long ttlNanos = TimeUnit.MILLISECONDS.toNanos(ttlMs - jitterMs);
    final long refreshNanos = ttlNanos / 2;
    LOG.registrySnapshotTtl(TimeUnit.NANOSECONDS.toMillis(ttlNanos), ttlSecs, TimeUnit.NANOSECONDS.toMillis(refreshNanos));
    this.registrySnapshotLoader = new RegistrySnapshotLoader(database, discoveryHelper, trustProvider, ticker, ttlNanos, refreshNanos);
    return Caffeine.newBuilder()
        .ticker(ticker)
        .executor(refreshExecutor)
        .refreshAfterWrite(refreshNanos, TimeUnit.NANOSECONDS)
        .expireAfter(new RegistrySnapshotExpiry(ttlNanos))
        .build(registrySnapshotLoader);
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    try {
      if (trustProvider != null) {
        trustProvider.close();
      }
      if (discoveryHelper != null) {
        discoveryHelper.close();
      }
    } catch (Exception e) {
      throw new ServiceLifecycleException("Error stopping JdbcTrustedOidcIssuerService: " + e, e);
    }
  }

  public void setAliasService(AliasService aliasService) {
    this.aliasService = aliasService;
  }

  protected AliasService getAliasService() {
    return aliasService;
  }

  void setSecretReader(IssuerTrustProvider.SecretReader secretReader) {
    this.secretReader = secretReader;
  }

  void setClock(Clock clock) {
    this.clock = clock;
  }

  @Override
  public boolean isTrusted(String issuerUrl) {
    return issuers().containsKey(issuerUrl);
  }

  @Override
  public boolean isDynamicJwks(String issuerUrl) {
    final TrustedOidcIssuer entry = issuers().get(issuerUrl);
    return entry != null && entry.isDynamicJwks();
  }

  @Override
  public Optional<String> resolveJwksUri(String issuerUrl) {
    return discoveryHelper.discoverJwksUri(issuerUrl);
  }

  @Override
  public synchronized void register(TrustedOidcIssuer issuer) {
    if (issuers().size() >= maxTrustedIssuers) {
      throw new IllegalStateException(
          "Cannot register issuer: MAX_TRUSTED_ISSUERS (" + maxTrustedIssuers + ") reached");
    }
    try {
      database.insert(issuer);
    } catch (SQLException e) {
      LOG.errorRegisteringIssuer(issuer.getIssuerUrl(), e.getMessage(), e);
      throw new RuntimeException("Error registering trusted OIDC issuer: " + issuer.getIssuerUrl(), e);
    }
    reloadRegistrySnapshot();
    trustProvider.invalidate(issuer.getIssuerUrl());
    discoveryHelper.invalidate(issuer.getIssuerUrl());
  }

  @Override
  public synchronized void deregister(String issuerUrl) {
    try {
      database.delete(issuerUrl);
    } catch (SQLException e) {
      LOG.errorDeregisteringIssuer(issuerUrl, e.getMessage(), e);
      throw new RuntimeException("Error deregistering trusted OIDC issuer: " + issuerUrl, e);
    }
    reloadRegistrySnapshot();
    trustProvider.invalidate(issuerUrl);
    discoveryHelper.invalidate(issuerUrl);
  }

  @Override
  public void refreshJwksUri(String issuerUrl) {
    if (isDynamicJwks(issuerUrl)) {
      trustProvider.invalidate(issuerUrl);
      discoveryHelper.invalidate(issuerUrl);
    }
  }

  @Override
  public Optional<IssuerJwksTrust> getJwksTrust(String issuerUrl) {
    return trustProvider.get(issuerUrl);
  }

  @Override
  public List<TrustedOidcIssuer> list() {
    return new ArrayList<>(issuers().values());
  }

  /**
   * Returns the current registry snapshot, loading it synchronously if it has expired. Never
   * throws: if the DB cannot be read once the snapshot has expired, the result is empty (fail closed).
   */
  private Map<String, TrustedOidcIssuer> issuers() {
    final LoadingCache<String, RegistrySnapshot> cache = registrySnapshot;
    return cache == null ? Collections.emptyMap() : cache.get(SNAPSHOT_KEY).getIssuers();
  }

  /**
   * Rebuilds the registry snapshot from the current DB state and puts it into the cache.
   * Called on init, after register, and after deregister. Unlike a TTL-driven reload, a DB
   * failure here is propagated to the caller.
   * Synchronized on this to prevent concurrent rebuilds from interleaving with mutations.
   */
  private synchronized void reloadRegistrySnapshot() {
    final RegistrySnapshot fresh;
    try {
      fresh = registrySnapshotLoader.rebuild(registrySnapshot.asMap().get(SNAPSHOT_KEY));
    } catch (Exception e) {
      LOG.errorReloadingRegistrySnapshot(e.getMessage(), e);
      throw new RuntimeException("Error reloading trusted OIDC issuer registry snapshot", e);
    }
    registrySnapshot.put(SNAPSHOT_KEY, fresh);
  }
}

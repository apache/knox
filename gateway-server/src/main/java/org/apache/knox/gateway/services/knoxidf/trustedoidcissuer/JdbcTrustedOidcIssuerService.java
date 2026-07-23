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

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.database.DataSourceProvider;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JDBC-backed implementation of {@link TrustedOidcIssuerService}.
 * <p>
 * Maintains an in-memory registry snapshot as an {@link AtomicReference} to an immutable
 * {@link Map}. Reads ({@link #isTrusted}, {@link #isDynamicJwks}, {@link #list}) are
 * lock-free and always see a consistent snapshot. Writes ({@link #register},
 * {@link #deregister}) are synchronized: the DB is committed first, then the snapshot is
 * rebuilt from a fresh SELECT to guarantee the in-memory state cannot diverge from
 * persistent storage.
 * <p>
 * HA note: each Knox node maintains its own snapshot. A registration on node A updates
 * that node's snapshot immediately; other nodes' snapshots remain stale until restart.
 */
public class JdbcTrustedOidcIssuerService implements TrustedOidcIssuerService {

  private static final TrustedOidcIssuerServiceMessages LOG =
      MessagesFactory.get(TrustedOidcIssuerServiceMessages.class);

  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final Lock initLock = new ReentrantLock(true);

  private final AtomicReference<Map<String, TrustedOidcIssuer>> registrySnapshot =
      new AtomicReference<>(Collections.emptyMap());

  private AliasService aliasService;
  private TrustedOidcIssuerDatabase database;
  private OIDCDiscoveryHelper discoveryHelper;
  private int maxTrustedIssuers;

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    if (!initialized.get()) {
      initLock.lock();
      try {
        if (aliasService == null) {
          throw new ServiceLifecycleException("The required AliasService reference has not been set.");
        }
        try {
          this.maxTrustedIssuers = config.getTrustedOidcIssuerMaxTrustedIssuers();
          this.database = new TrustedOidcIssuerDatabase(
              DataSourceProvider.getDataSource(config, aliasService), config.getDatabaseType());
          this.discoveryHelper = new OIDCDiscoveryHelper(this, config.getTrustedOidcIssuerDiscoveryCacheTtlSecs(),
              OIDCDiscoveryHelper.buildHttpClient(config.getTrustedOidcIssuerDiscoveryConnectTimeoutMs(), config.getTrustedOidcIssuerDiscoveryReadTimeoutMs()));
          reloadRegistrySnapshot();
          initialized.set(true);
        } catch (ServiceLifecycleException e) {
          throw e;
        } catch (Exception e) {
          throw new ServiceLifecycleException("Error initializing JdbcTrustedOidcIssuerService: " + e, e);
        }
      } finally {
        initLock.unlock();
      }
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }

  public void setAliasService(AliasService aliasService) {
    this.aliasService = aliasService;
  }

  protected AliasService getAliasService() {
    return aliasService;
  }

  @Override
  public boolean isTrusted(String issuerUrl) {
    return registrySnapshot.get().containsKey(issuerUrl);
  }

  @Override
  public boolean isDynamicJwks(String issuerUrl) {
    final TrustedOidcIssuer entry = registrySnapshot.get().get(issuerUrl);
    return entry != null && entry.isDynamicJwks();
  }

  @Override
  public Optional<String> resolveJwksUri(String issuerUrl) {
    return discoveryHelper.discoverJwksUri(issuerUrl);
  }

  @Override
  public synchronized void register(TrustedOidcIssuer issuer) {
    if (registrySnapshot.get().size() >= maxTrustedIssuers) {
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
    discoveryHelper.invalidate(issuerUrl);
  }

  @Override
  public void refreshJwksUri(String issuerUrl) {
    if (isDynamicJwks(issuerUrl)) {
      discoveryHelper.invalidate(issuerUrl);
    }
  }

  @Override
  public List<TrustedOidcIssuer> list() {
    return new ArrayList<>(registrySnapshot.get().values());
  }

  /**
   * Rebuilds the registry snapshot from the current DB state.
   * Called on init, after register, and after deregister.
   * Synchronized on this to prevent concurrent rebuilds from interleaving with mutations.
   */
  private synchronized void reloadRegistrySnapshot() {
    try {
      final Map<String, TrustedOidcIssuer> fresh = database.selectAll().stream()
          .collect(Collectors.toMap(TrustedOidcIssuer::getIssuerUrl, Function.identity()));
      registrySnapshot.set(Collections.unmodifiableMap(fresh));
    } catch (Exception e) {
      LOG.errorReloadingRegistrySnapshot(e.getMessage(), e);
      throw new RuntimeException("Error reloading trusted OIDC issuer registry snapshot", e);
    }
  }
}

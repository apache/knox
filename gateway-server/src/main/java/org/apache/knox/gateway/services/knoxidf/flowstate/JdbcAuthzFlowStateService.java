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
import org.apache.knox.gateway.database.DataSourceProvider;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;

import java.sql.SQLException;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the authorization flow state in the gateway database, shared by all gateway instances
 * using it. Expired rows are ignored on read and deleted by a periodic sweep.
 */
public class JdbcAuthzFlowStateService implements AuthzFlowStateService {
  private static final AuthzFlowStateServiceMessages LOG = MessagesFactory.get(AuthzFlowStateServiceMessages.class);

  private final Clock clock;
  private AliasService aliasService; // connection username/pw are stored here
  private AuthzFlowStateDatabase database;
  private long ttlMillis;
  private long cleanupIntervalSecs;
  private ScheduledExecutorService sweeper;

  public JdbcAuthzFlowStateService() {
    this(Clock.systemUTC());
  }

  JdbcAuthzFlowStateService(Clock clock) {
    this.clock = clock;
  }

  public void setAliasService(AliasService aliasService) {
    this.aliasService = aliasService;
  }

  @Override
  public synchronized void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    if (database != null) {
      return;
    }
    if (aliasService == null) {
      throw new ServiceLifecycleException("The required AliasService reference has not been set.");
    }
    try {
      database = new AuthzFlowStateDatabase(DataSourceProvider.getDataSource(config, aliasService), config.getDatabaseType());
    } catch (Exception e) {
      throw new ServiceLifecycleException("Error while initiating JdbcAuthzFlowStateService: " + e, e);
    }
    ttlMillis = TimeUnit.SECONDS.toMillis(config.getKnoxIDFAuthzFlowStateTtlSecs());
    cleanupIntervalSecs = config.getKnoxIDFAuthzFlowStateCleanupIntervalSecs();
  }

  @Override
  public synchronized void start() throws ServiceLifecycleException {
    if (sweeper == null && cleanupIntervalSecs > 0) {
      sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "KnoxIDFAuthzFlowStateSweeper");
        thread.setDaemon(true);
        return thread;
      });
      sweeper.scheduleAtFixedRate(this::removeExpired, cleanupIntervalSecs, cleanupIntervalSecs, TimeUnit.SECONDS);
    }
  }

  @Override
  public synchronized void stop() throws ServiceLifecycleException {
    if (sweeper != null) {
      sweeper.shutdownNow();
      sweeper = null;
    }
  }

  @Override
  public void put(AuthzFlowStateKind kind, String id, String payload) {
    try {
      database.upsert(kind.id(), id, payload, clock.millis() + ttlMillis);
    } catch (SQLException e) {
      LOG.errorSavingAuthzFlowState(kind.id(), e.getMessage(), e);
      throw new AuthzFlowStateServiceException("An error occurred while saving KnoxIDF authorization flow state in the database", e);
    }
  }

  @Override
  public Optional<String> get(AuthzFlowStateKind kind, String id) {
    if (id == null) {
      return Optional.empty();
    }
    try {
      return database.select(kind.id(), id, clock.millis());
    } catch (SQLException e) {
      LOG.errorReadingAuthzFlowState(kind.id(), e.getMessage(), e);
      throw readFailure(e);
    }
  }

  @Override
  public Optional<String> consume(AuthzFlowStateKind kind, String id) {
    if (id == null) {
      return Optional.empty();
    }
    try {
      return database.consume(kind.id(), id, clock.millis());
    } catch (SQLException e) {
      LOG.errorReadingAuthzFlowState(kind.id(), e.getMessage(), e);
      throw readFailure(e);
    }
  }

  @Override
  public void remove(AuthzFlowStateKind kind, String id) {
    if (id == null) {
      return;
    }
    try {
      database.delete(kind.id(), id);
    } catch (SQLException e) {
      // The entry still expires; a failed clean-up must not fail the flow.
      LOG.errorRemovingAuthzFlowState(kind.id(), e.getMessage(), e);
    }
  }

  @Override
  public int removeExpired() {
    try {
      final int removed = database.deleteExpired(clock.millis());
      LOG.removedExpiredAuthzFlowState(removed);
      return removed;
    } catch (SQLException e) {
      LOG.errorRemovingExpiredAuthzFlowState(e.getMessage(), e);
      return 0;
    }
  }

  // Not reported as an absent entry: that would end the flow with a client error.
  private static AuthzFlowStateServiceException readFailure(SQLException e) {
    return new AuthzFlowStateServiceException("An error occurred while reading KnoxIDF authorization flow state from the database", e);
  }
}

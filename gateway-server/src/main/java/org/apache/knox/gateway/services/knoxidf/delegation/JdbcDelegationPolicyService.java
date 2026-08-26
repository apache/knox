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
package org.apache.knox.gateway.services.knoxidf.delegation;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.database.DataSourceProvider;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JDBC-backed implementation of {@link DelegationPolicyService}.
 * All mutating operations use explicit transactions: {@code setAutoCommit(false)} with
 * commit/rollback so child-table writes and core-row writes are atomic.
 */
public class JdbcDelegationPolicyService implements DelegationPolicyService {

  private static final DelegationPolicyServiceMessages LOG =
      MessagesFactory.get(DelegationPolicyServiceMessages.class);

  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final Lock initLock = new ReentrantLock(true);

  private AliasService aliasService;
  private DelegationPolicyDatabase database;
  private int configuredKnoxTokenTtlSec;

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    if (!initialized.get()) {
      initLock.lock();
      try {
        if (!initialized.get()) {
          if (aliasService == null) {
            throw new ServiceLifecycleException("The required AliasService reference has not been set.");
          }
          try {
            this.configuredKnoxTokenTtlSec = config.getDelegationServiceTokenTtlSec();
            this.database = new DelegationPolicyDatabase(
                DataSourceProvider.getDataSource(config, aliasService),
                config.getDatabaseType(),
                config.getDelegationServiceListMaxTotal(),
                config.getDelegationServiceListMaxPerAuthority());
            initialized.set(true);
          } catch (ServiceLifecycleException e) {
            throw e;
          } catch (Exception e) {
            throw new ServiceLifecycleException("Error initializing JdbcDelegationPolicyService: " + e, e);
          }
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

  @Override
  public DelegationPolicy register(DelegationPolicy policy) throws ServiceLifecycleException {
    try (Connection connection = database.getConnection()) {
      connection.setAutoCommit(false);
      try {
        final String id = database.insertRegistration(connection, policy);
        database.insertChildRows(connection, id, policy);
        connection.commit();
        connection.setAutoCommit(true);
        return database.selectById(connection, id).orElseThrow(
            () -> new ServiceLifecycleException("Failed to read back registered policy " + id));
      } catch (SQLException e) {
        rollbackQuietly(connection);
        LOG.errorRegisteringPolicy(policy.getActorAuthority(), policy.getActorId(), e.getMessage(), e);
        throw new ServiceLifecycleException(
            "Error registering delegation policy for actor (" + policy.getActorAuthority() + ", " + policy.getActorId() + "): " + e, e);
      }
    } catch (ServiceLifecycleException e) {
      throw e;
    } catch (Exception e) {
      throw new ServiceLifecycleException("Error registering delegation policy: " + e, e);
    }
  }

  @Override
  public void update(String registrationId, DelegationPolicy policy) throws ServiceLifecycleException {
    try (Connection connection = database.getConnection()) {
      connection.setAutoCommit(false);
      try {
        database.updateCoreRow(connection, registrationId, policy);
        database.replaceChildRows(connection, registrationId, policy);
        connection.commit();
      } catch (SQLException e) {
        rollbackQuietly(connection);
        LOG.errorUpdatingPolicy(registrationId, e.getMessage(), e);
        throw new ServiceLifecycleException("Error updating delegation policy " + registrationId + ": " + e, e);
      }
    } catch (ServiceLifecycleException e) {
      throw e;
    } catch (Exception e) {
      throw new ServiceLifecycleException("Error updating delegation policy " + registrationId + ": " + e, e);
    }
  }

  @Override
  public void delete(String registrationId) {
    try (Connection connection = database.getConnection()) {
      connection.setAutoCommit(false);
      try {
        database.deleteChildRows(connection, registrationId);
        database.deleteRegistration(connection, registrationId);
        connection.commit();
      } catch (SQLException e) {
        rollbackQuietly(connection);
        LOG.errorDeletingPolicy(registrationId, e.getMessage(), e);
        throw new RuntimeException("Error deleting delegation policy " + registrationId + ": " + e, e);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Error deleting delegation policy " + registrationId + ": " + e, e);
    }
  }

  @Override
  public Optional<DelegationPolicy> get(String registrationId) {
    try (Connection connection = database.getConnection()) {
      return database.selectById(connection, registrationId);
    } catch (Exception e) {
      LOG.errorReadingPolicy(registrationId, e.getMessage(), e);
      throw new RuntimeException("Error reading delegation policy " + registrationId + ": " + e, e);
    }
  }

  @Override
  public Optional<DelegationPolicy> findByActor(String actorAuthority, String actorId) {
    try (Connection connection = database.getConnection()) {
      return database.selectByActor(connection, actorAuthority, actorId);
    } catch (Exception e) {
      LOG.errorListingPolicies(e.getMessage(), e);
      throw new RuntimeException("Error looking up delegation policy for actor (" + actorAuthority + ", " + actorId + "): " + e, e);
    }
  }

  @Override
  public DelegationPolicyList list(String actorAuthorityFilter) {
    try (Connection connection = database.getConnection()) {
      return database.selectAll(connection, actorAuthorityFilter);
    } catch (Exception e) {
      LOG.errorListingPolicies(e.getMessage(), e);
      throw new RuntimeException("Error listing delegation policies: " + e, e);
    }
  }

  @Override
  public PolicyDecision evaluate(PolicyCheckRequest request) {
    // Step 1: look up registration
    final Optional<DelegationPolicy> policyOpt = findByActor(request.getActorAuthority(), request.getActorId());
    if (!policyOpt.isPresent()) {
      return deny("actor_not_registered");
    }
    final DelegationPolicy policy = policyOpt.get();

    // Step 2: headless exchange check
    if (request.isHeadlessExchange() && !policy.isAllowHeadlessExchange()) {
      return deny("headless_not_allowed");
    }

    // Step 3: subject check
    if (!policy.getCanActForUsers().contains(request.getSubjectName())) {
      // Step 4: group check (not yet implemented)
      if (!policy.getCanActForGroups().isEmpty()) {
        throw new UnsupportedOperationException("canActFor.groups evaluation not yet implemented");
      }
      return deny("subject_not_allowed");
    }

    // Step 5: resource check
    final Map<String, Set<String>> resourcePolicy = policy.getResourcePolicy();
    if (!resourcePolicy.containsKey(request.getRequestedResource())) {
      return deny("resource_not_allowed");
    }

    // Step 6: scope check
    final Set<String> scopeSet = resourcePolicy.get(request.getRequestedResource());
    if (!scopeSet.isEmpty() && !scopeSet.contains(request.getRequestedScope())) {
      return deny("scope_not_allowed");
    }

    // Step 7: effective TTL
    final int effectiveTtl;
    if (policy.getMaxTokenTtlSec() != null) {
      effectiveTtl = Math.min(policy.getMaxTokenTtlSec(), configuredKnoxTokenTtlSec);
    } else {
      effectiveTtl = configuredKnoxTokenTtlSec;
    }

    // Step 8: authorized
    return new PolicyDecision(true, null, effectiveTtl, request.getRequestedScope(), request.getRequestedResource());
  }

  private static PolicyDecision deny(String reason) {
    return new PolicyDecision(false, reason, 0, null, null);
  }

  private static void rollbackQuietly(Connection connection) {
    try {
      connection.rollback();
    } catch (SQLException ignored) {
    }
  }
}

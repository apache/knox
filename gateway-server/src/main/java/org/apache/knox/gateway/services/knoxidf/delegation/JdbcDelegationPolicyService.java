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
import org.apache.knox.gateway.database.JDBCUtils;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JDBC-backed implementation of {@link DelegationPolicyService}.
 * Transaction management is handled by {@link DelegationPolicyDatabase}; this class
 * is responsible for service lifecycle, exception translation, and evaluate() logic.
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

  protected AliasService getAliasService() {
    return aliasService;
  }

  @Override
  public DelegationPolicy register(DelegationPolicy policy) {
    final String id;
    try {
      id = database.insertPolicy(policy);
    } catch (SQLException e) {
      if (JDBCUtils.isUniqueConstraintViolation(e)) {
        throw new DelegationPolicyAlreadyExistsException(policy.getActorAuthority(), policy.getActorId(), e);
      }
      LOG.errorRegisteringPolicy(policy.getActorAuthority(), policy.getActorId(), e.getMessage(), e);
      throw new RuntimeException(
          "Error registering delegation policy for actor (" + policy.getActorAuthority() + ", " + policy.getActorId() + "): " + e, e);
    } catch (Exception e) {
      LOG.errorRegisteringPolicy(policy.getActorAuthority(), policy.getActorId(), e.getMessage(), e);
      throw new RuntimeException(
          "Error registering delegation policy for actor (" + policy.getActorAuthority() + ", " + policy.getActorId() + "): " + e, e);
    }
    try {
      return database.selectById(id).orElseThrow(
          () -> new RuntimeException("Failed to read back registered policy " + id));
    } catch (Exception e) {
      LOG.errorRegisteringPolicy(policy.getActorAuthority(), policy.getActorId(), e.getMessage(), e);
      throw new RuntimeException(
          "Error registering delegation policy for actor (" + policy.getActorAuthority() + ", " + policy.getActorId() + "): " + e, e);
    }
  }

  @Override
  public DelegationPolicy update(String registrationId, DelegationPolicy policy) {
    try {
      if (!database.updatePolicy(registrationId, policy)) {
        throw new DelegationPolicyNotFoundException(registrationId);
      }
      return database.selectById(registrationId).orElseThrow(
          () -> new RuntimeException("Failed to read back updated policy " + registrationId));
    } catch (DelegationPolicyNotFoundException e) {
      throw e;
    } catch (Exception e) {
      LOG.errorUpdatingPolicy(registrationId, e.getMessage(), e);
      throw new RuntimeException("Error updating delegation policy " + registrationId + ": " + e, e);
    }
  }

  @Override
  public RegisterOrUpdateResult registerOrUpdate(DelegationPolicy policy) {
    final Optional<DelegationPolicy> existing = findByActor(policy.getActorAuthority(), policy.getActorId());
    if (existing.isPresent()) {
      try {
        return new RegisterOrUpdateResult(update(existing.get().getRegistrationId(), policy), false);
      } catch (DelegationPolicyNotFoundException e) {
        // Deleted concurrently between our findByActor() and update() -- fall through to create.
      }
      return new RegisterOrUpdateResult(register(policy), true); // a second race propagates as-is
    } else {
      try {
        return new RegisterOrUpdateResult(register(policy), true);
      } catch (DelegationPolicyAlreadyExistsException e) {
        // Created concurrently between our findByActor() and register() -- fall through to update.
      }
      final DelegationPolicy winner = findByActor(policy.getActorAuthority(), policy.getActorId())
          .orElseThrow(() -> new IllegalStateException(
              "Actor (" + policy.getActorAuthority() + ", " + policy.getActorId()
                  + ") reported as already existing but not found on re-lookup"));
      return new RegisterOrUpdateResult(update(winner.getRegistrationId(), policy), false);
    }
  }

  @Override
  public void delete(String registrationId) {
    try {
      if (!database.deletePolicy(registrationId)) {
        throw new DelegationPolicyNotFoundException(registrationId);
      }
    } catch (DelegationPolicyNotFoundException e) {
      throw e;
    } catch (Exception e) {
      LOG.errorDeletingPolicy(registrationId, e.getMessage(), e);
      throw new RuntimeException("Error deleting delegation policy " + registrationId + ": " + e, e);
    }
  }

  @Override
  public Optional<DelegationPolicy> get(String registrationId) {
    try {
      return database.selectById(registrationId);
    } catch (Exception e) {
      LOG.errorReadingPolicy(registrationId, e.getMessage(), e);
      throw new RuntimeException("Error reading delegation policy " + registrationId + ": " + e, e);
    }
  }

  @Override
  public Optional<DelegationPolicy> findByActor(String actorAuthority, String actorId) {
    try {
      return database.selectByActor(actorAuthority, actorId);
    } catch (Exception e) {
      LOG.errorListingPolicies(e.getMessage(), e);
      throw new RuntimeException("Error looking up delegation policy for actor (" + actorAuthority + ", " + actorId + "): " + e, e);
    }
  }

  @Override
  public DelegationPolicyList list(String actorAuthorityFilter) {
    try {
      return database.selectAll(actorAuthorityFilter);
    } catch (Exception e) {
      LOG.errorListingPolicies(e.getMessage(), e);
      throw new RuntimeException("Error listing delegation policies: " + e, e);
    }
  }

  @Override
  public int getConfiguredTokenTtlSec() {
    return configuredKnoxTokenTtlSec;
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

    // Step 3: user check (remember result; group check deferred to the end)
    final boolean userCheckPassed =
        policy.getCanActForUsers().contains(request.getSubjectName());

    // Step 4: resource check
    final Map<String, Set<String>> resourcePolicy = policy.getResourcePolicy();
    if (!resourcePolicy.containsKey(request.getRequestedResource())) {
      return deny("resource_not_allowed");
    }

    // Step 5: scope check (requested scopes are optional; when non-empty, all must be in the allowed set)
    final Set<String> scopeSet = resourcePolicy.get(request.getRequestedResource());
    if (!request.getRequestedScopes().isEmpty() && !scopeSet.isEmpty()
        && !scopeSet.containsAll(request.getRequestedScopes())) {
      return deny("scope_not_allowed");
    }

    // Step 6: effective TTL — use the policy value if set, otherwise fall back to the configured default
    final int effectiveTtlSec = policy.getTokenTtlSec() != null
        ? policy.getTokenTtlSec()
        : configuredKnoxTokenTtlSec;

    // Step 7: group check (LDAP lookup — slowest, deferred past cheap checks)
    if (!userCheckPassed) {
      if (!policy.getCanActForGroups().isEmpty()) {
        throw new UnsupportedOperationException("canActFor.groups evaluation not yet implemented");
      }
      return deny("subject_not_allowed");
    }

    // Step 8: authorized
    return new PolicyDecision(null, effectiveTtlSec);
  }

  private static PolicyDecision deny(String reason) {
    return new PolicyDecision(reason, 0);
  }
}

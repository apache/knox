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
import org.apache.knox.gateway.services.ldap.KnoxLDAPService;
import org.apache.knox.gateway.services.security.AliasService;

import java.sql.SQLException;
import java.util.List;
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
  private KnoxLDAPService ldapService;
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

  /**
   * The LDAP service used to resolve the impersonated subject's group memberships for the
   * {@code canActFor.groups} check in {@link #evaluate(PolicyCheckRequest)}. May be left unset when
   * no LDAP service is available; a policy with a non-empty {@code canActFor.groups} list then cannot
   * be evaluated and {@link #evaluate(PolicyCheckRequest)} raises
   * {@link DelegationGroupLookupUnavailableException} (surfaced as a server error) rather than denying.
   */
  public void setLdapService(KnoxLDAPService ldapService) {
    this.ldapService = ldapService;
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

    // Step 1.5: a policy that is not active (e.g. revoked instead of deleted) must not authorize.
    // The registration still exists, but its authorization has been withdrawn, so the exchange is
    // denied here before any user/group/resource/scope check is considered.
    if (!policy.isActive()) {
      return deny("policy_not_active");
    }

    // Step 2: headless exchange check
    if (request.isHeadlessExchange() && !policy.isAllowHeadlessExchange()) {
      return deny("headless_not_allowed");
    }

    // Step 3: user check (remember result; group check deferred to the end)
    final boolean userCheckPassed =
        policy.getCanActForUsers().contains(request.getSubjectName());

    // Step 4/5: per-resource membership and scope check.
    //
    // Scopes can only be validated against a specific resource's allowed scope set, so a request
    // cannot ask for scopes without also requesting at least one resource. When no resources are
    // requested but at least one scope is requested, deny.
    //
    // Each requested resource is checked independently against its own allowed scope set. A
    // resource whose configured scope set is empty is unrestricted for that resource; requested
    // scopes must still be allowed by every other requested resource that has a non empty scope
    // set. Iteration order over requestedResources does not matter: the loop returns on the first
    // resource that fails either check, so when multiple requested resources would each fail for
    // a different reason, which reason is returned is unspecified.
    //
    // When no resources and no scopes are requested, this check imposes no restriction and
    // evaluate() proceeds to Step 6.
    final String scopeNotAllowedReason = "scope_not_allowed";
    final Map<String, Set<String>> resourcePolicy = policy.getResourcePolicy();
    if (request.getRequestedResources().isEmpty() && !request.getRequestedScopes().isEmpty()) {
      return deny(scopeNotAllowedReason);
    }
    for (String requestedResource : request.getRequestedResources()) {
      if (!resourcePolicy.containsKey(requestedResource)) {
        return deny("resource_not_allowed");
      }
      final Set<String> scopeSet = resourcePolicy.get(requestedResource);
      if (!request.getRequestedScopes().isEmpty() && !scopeSet.isEmpty()
          && !scopeSet.containsAll(request.getRequestedScopes())) {
        return deny(scopeNotAllowedReason);
      }
    }

    // Step 6: effective TTL. Use the policy value if set, otherwise fall back to the
    // configured default.
    final int effectiveTtlSec = policy.getTokenTtlSec() != null
        ? policy.getTokenTtlSec()
        : configuredKnoxTokenTtlSec;

    // Step 7: group check (LDAP lookup, slowest, deferred past the cheaper checks). Only reached
    // when the cheaper explicit-user check did not already authorize the subject. The lookup is on
    // the impersonated subject, mirroring the Step 3 user check (canActForUsers is matched against
    // the same subjectName): canActFor.groups means "this actor may act for users in these groups".
    if (!userCheckPassed) {
      final Set<String> allowedGroups = policy.getCanActForGroups();
      if (allowedGroups.isEmpty()) {
        // No group rule to fall back on: the subject simply is not allowed.
        return deny("subject_not_allowed");
      }
      // A group-based policy requires LDAP to resolve the subject's memberships. If LDAP is absent
      // or disabled this is an operator misconfiguration (a groups policy with the directory turned
      // off), not an authorization decision: signal it so the exchange fails with a server_error
      // directing the operator to enable LDAP, rather than silently denying with subject_not_allowed.
      if (ldapService == null || !ldapService.isEnabled()) {
        LOG.groupLookupUnavailable(request.getSubjectName());
        throw new DelegationGroupLookupUnavailableException(request.getSubjectName());
      }
      if (!subjectBelongsToAllowedGroup(request.getSubjectName(), allowedGroups)) {
        return deny("subject_not_allowed");
      }
    }

    // Step 8: authorized
    return new PolicyDecision(null, effectiveTtlSec);
  }

  /**
   * Resolve the subject's group memberships via the (present and enabled) LDAP service and test
   * them against the policy's allowed groups. Availability is the caller's precondition; this method
   * concerns itself only with the membership decision. A runtime lookup failure means the group rule
   * could not be evaluated - it is neither a match nor a non-match - so it is logged (with stack
   * trace) and re-raised as a {@link DelegationGroupLookupUnavailableException} for the caller to
   * surface as a server error, rather than being swallowed into a {@code subject_not_allowed} denial.
   */
  private boolean subjectBelongsToAllowedGroup(String subjectName, Set<String> allowedGroups) {
    try {
      final List<String> subjectGroups = ldapService.getUserGroups(subjectName);
      for (String group : subjectGroups) {
        if (allowedGroups.contains(group)) {
          return true;
        }
      }
      return false;
    } catch (Exception e) {
      LOG.errorEvaluatingGroupMembership(subjectName, e.getMessage(), e);
      throw new DelegationGroupLookupUnavailableException(subjectName, e);
    }
  }

  private static PolicyDecision deny(String reason) {
    return new PolicyDecision(reason, 0);
  }
}

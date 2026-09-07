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
import org.apache.knox.gateway.services.ServiceLifecycleException;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * No-op stub used when the KNOXIDF or KNOXIDF_ADMIN service role is not deployed.
 * Read methods return safe empty results; mutating methods throw
 * {@link UnsupportedOperationException}.
 */
public class EmptyDelegationPolicyService implements DelegationPolicyService {

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }

  @Override
  public DelegationPolicy register(DelegationPolicy policy) {
    throw new UnsupportedOperationException("DelegationPolicyService is not enabled; "
        + "deploy the KNOXIDF or KNOXIDF_ADMIN service role to activate it.");
  }

  @Override
  public DelegationPolicy update(String registrationId, DelegationPolicy policy) {
    throw new UnsupportedOperationException("DelegationPolicyService is not enabled; "
        + "deploy the KNOXIDF or KNOXIDF_ADMIN service role to activate it.");
  }

  @Override
  public RegisterOrUpdateResult registerOrUpdate(DelegationPolicy policy) {
    throw new UnsupportedOperationException("DelegationPolicyService is not enabled; "
        + "deploy the KNOXIDF or KNOXIDF_ADMIN service role to activate it.");
  }

  @Override
  public void delete(String registrationId) {
    throw new UnsupportedOperationException("DelegationPolicyService is not enabled; "
        + "deploy the KNOXIDF or KNOXIDF_ADMIN service role to activate it.");
  }

  @Override
  public Optional<DelegationPolicy> get(String registrationId) {
    return Optional.empty();
  }

  @Override
  public Optional<DelegationPolicy> findByActor(String actorAuthority, String actorId) {
    return Optional.empty();
  }

  @Override
  public DelegationPolicyList list(String actorAuthorityFilter) {
    return new DelegationPolicyList(Collections.emptyList(), false);
  }

  @Override
  public PolicyDecision evaluate(PolicyCheckRequest request) {
    return new PolicyDecision("service_not_configured", 0);
  }

  @Override
  public int getConfiguredTokenTtlSec() {
    // This stub is only in play when the service role is not deployed (so the admin API is not
    // deployed either). Return the gateway-config default so any bounds check still sees an
    // in-range value rather than 0.
    return GatewayConfig.DELEGATION_SERVICE_TOKEN_TTL_SEC_DEFAULT;
  }
}

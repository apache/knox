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

/**
 * Result of {@link DelegationPolicyService#registerOrUpdate(DelegationPolicy)}: the persisted
 * policy plus whether this call created it (true) or replaced an existing one (false).
 */
public final class RegisterOrUpdateResult {

  private final DelegationPolicy policy;
  private final boolean created;

  public RegisterOrUpdateResult(DelegationPolicy policy, boolean created) {
    this.policy = policy;
    this.created = created;
  }

  public DelegationPolicy getPolicy() {
    return policy;
  }

  public boolean isCreated() {
    return created;
  }
}

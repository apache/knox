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
 * Immutable result of {@link DelegationPolicyService#evaluate(PolicyCheckRequest)}.
 * A null {@link #getDenyReason()} means authorized; non-null means denied.
 */
public class PolicyDecision {

  private final String denyReason;
  private final int effectiveTtlSec;

  public PolicyDecision(String denyReason, int effectiveTtlSec) {
    this.denyReason = denyReason;
    this.effectiveTtlSec = effectiveTtlSec;
  }

  /** Null when authorized. Maps to the {@code error_description} of an {@code invalid_grant} response when non-null. */
  public String getDenyReason() {
    return denyReason;
  }

  /** TTL in seconds for the minted token after exchange. 0 when denied. */
  public int getEffectiveTtlSec() {
    return effectiveTtlSec;
  }
}

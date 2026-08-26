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
 * When {@link #isAuthorized()} is false, {@link #getDenyReason()} is non-null and
 * {@link #getEffectiveMaxTtlSec()} is 0.
 */
public class PolicyDecision {

  private final boolean authorized;
  private final String denyReason;
  private final int effectiveMaxTtlSec;
  private final String effectiveScope;
  private final String effectiveResource;

  public PolicyDecision(boolean authorized, String denyReason, int effectiveMaxTtlSec,
      String effectiveScope, String effectiveResource) {
    this.authorized = authorized;
    this.denyReason = denyReason;
    this.effectiveMaxTtlSec = effectiveMaxTtlSec;
    this.effectiveScope = effectiveScope;
    this.effectiveResource = effectiveResource;
  }

  public boolean isAuthorized() {
    return authorized;
  }

  /** Non-null when {@link #isAuthorized()} is false. */
  public String getDenyReason() {
    return denyReason;
  }

  /** 0 when denied. */
  public int getEffectiveMaxTtlSec() {
    return effectiveMaxTtlSec;
  }

  /** Null when denied. */
  public String getEffectiveScope() {
    return effectiveScope;
  }

  /** Null when denied. */
  public String getEffectiveResource() {
    return effectiveResource;
  }
}

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of {@link DelegationPolicyService#list(String)}.
 * {@code hasMore} is {@code true} when the result set was truncated at the configured row limit;
 * callers must surface this to the user rather than treating the list as complete.
 */
public final class DelegationPolicyList {

  private final List<DelegationPolicy> policies;
  private final boolean hasMore;

  public DelegationPolicyList(List<DelegationPolicy> policies, boolean hasMore) {
    this.policies = Collections.unmodifiableList(new ArrayList<>(policies));
    this.hasMore = hasMore;
  }

  public List<DelegationPolicy> getPolicies() {
    return policies;
  }

  public boolean hasMore() {
    return hasMore;
  }
}

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

import java.util.Collections;
import java.util.Set;

/**
 * Immutable input to {@link DelegationPolicyService#evaluate(PolicyCheckRequest)}.
 */
public class PolicyCheckRequest {

  private final String actorAuthority;
  private final String actorId;
  private final String subjectName;
  private final Set<String> requestedResources;
  private final Set<String> requestedScopes;
  private final boolean headlessExchange;

  public PolicyCheckRequest(String actorAuthority, String actorId, String subjectName,
      Set<String> requestedResources, Set<String> requestedScopes, boolean headlessExchange) {
    this.actorAuthority = actorAuthority;
    this.actorId = actorId;
    this.subjectName = subjectName;
    this.requestedResources = Set.copyOf(requestedResources != null ?
            requestedResources : Collections.emptySet());
    this.requestedScopes = Set.copyOf(requestedScopes != null ?
            requestedScopes : Collections.emptySet());
    this.headlessExchange = headlessExchange;
  }

  public String getActorAuthority() {
    return actorAuthority;
  }

  public String getActorId() {
    return actorId;
  }

  public String getSubjectName() {
    return subjectName;
  }

  public Set<String> getRequestedResources() {
    return requestedResources;
  }

  public Set<String> getRequestedScopes() {
    return requestedScopes;
  }

  public boolean isHeadlessExchange() {
    return headlessExchange;
  }
}

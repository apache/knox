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
 * Immutable input to {@link DelegationPolicyService#evaluate(PolicyCheckRequest)}.
 */
public class PolicyCheckRequest {

  private final String actorAuthority;
  private final String actorId;
  private final String subjectName;
  private final String requestedResource;
  private final String requestedScope;
  private final boolean headlessExchange;

  public PolicyCheckRequest(String actorAuthority, String actorId, String subjectName,
      String requestedResource, String requestedScope, boolean headlessExchange) {
    this.actorAuthority = actorAuthority;
    this.actorId = actorId;
    this.subjectName = subjectName;
    this.requestedResource = requestedResource;
    this.requestedScope = requestedScope;
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

  public String getRequestedResource() {
    return requestedResource;
  }

  public String getRequestedScope() {
    return requestedScope;
  }

  public boolean isHeadlessExchange() {
    return headlessExchange;
  }
}

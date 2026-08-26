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

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable representation of a stored delegation policy record.
 */
public class DelegationPolicy {

  private final String registrationId;
  private final String actorAuthority;
  private final String actorId;
  private final String name;
  private final String status;
  private final Integer maxTokenTtlSec;
  private final String description;
  private final String createdBy;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final boolean allowHeadlessExchange;
  private final Set<String> canActForUsers;
  private final Set<String> canActForGroups;
  private final Map<String, Set<String>> resourcePolicy;

  public DelegationPolicy(String registrationId, String actorAuthority, String actorId,
      String name, String status, Integer maxTokenTtlSec, String description, String createdBy,
      Instant createdAt, Instant updatedAt, boolean allowHeadlessExchange,
      Set<String> canActForUsers, Set<String> canActForGroups,
      Map<String, Set<String>> resourcePolicy) {
    this.registrationId = registrationId;
    this.actorAuthority = actorAuthority;
    this.actorId = actorId;
    this.name = name;
    this.status = status;
    this.maxTokenTtlSec = maxTokenTtlSec;
    this.description = description;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.allowHeadlessExchange = allowHeadlessExchange;
    this.canActForUsers = Collections.unmodifiableSet(new HashSet<>(canActForUsers));
    this.canActForGroups = Collections.unmodifiableSet(new HashSet<>(canActForGroups));
    Map<String, Set<String>> copy = new HashMap<>();
    for (Map.Entry<String, Set<String>> entry : resourcePolicy.entrySet()) {
      copy.put(entry.getKey(), Collections.unmodifiableSet(new HashSet<>(entry.getValue())));
    }
    this.resourcePolicy = Collections.unmodifiableMap(copy);
  }

  public String getRegistrationId() {
    return registrationId;
  }

  public String getActorAuthority() {
    return actorAuthority;
  }

  public String getActorId() {
    return actorId;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public Integer getMaxTokenTtlSec() {
    return maxTokenTtlSec;
  }

  public String getDescription() {
    return description;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public boolean isAllowHeadlessExchange() {
    return allowHeadlessExchange;
  }

  public Set<String> getCanActForUsers() {
    return canActForUsers;
  }

  public Set<String> getCanActForGroups() {
    return canActForGroups;
  }

  public Map<String, Set<String>> getResourcePolicy() {
    return resourcePolicy;
  }
}

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
package org.apache.knox.gateway.service.knoxidf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;
import java.util.Set;

/**
 * Request body shared by POST and PUT on {@link DelegationPolicyResource}.
 * registrationId/createdBy/createdAt/updatedAt are deliberately not present here: they are
 * server-managed and never read from the request body.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DelegationPolicyRequest {

  private String actorAuthority;
  private String actorId;
  private String name;
  private String status;
  private Integer tokenTtlSec;
  private String description;
  private boolean allowHeadlessExchange;
  private Set<String> canActForUsers;
  private Set<String> canActForGroups;
  private Map<String, Set<String>> resourcePolicy;

  public String getActorAuthority() {
    return actorAuthority;
  }

  public void setActorAuthority(String actorAuthority) {
    this.actorAuthority = actorAuthority;
  }

  public String getActorId() {
    return actorId;
  }

  public void setActorId(String actorId) {
    this.actorId = actorId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getTokenTtlSec() {
    return tokenTtlSec;
  }

  public void setTokenTtlSec(Integer tokenTtlSec) {
    this.tokenTtlSec = tokenTtlSec;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public boolean isAllowHeadlessExchange() {
    return allowHeadlessExchange;
  }

  public void setAllowHeadlessExchange(boolean allowHeadlessExchange) {
    this.allowHeadlessExchange = allowHeadlessExchange;
  }

  public Set<String> getCanActForUsers() {
    return canActForUsers;
  }

  public void setCanActForUsers(Set<String> canActForUsers) {
    this.canActForUsers = canActForUsers;
  }

  public Set<String> getCanActForGroups() {
    return canActForGroups;
  }

  public void setCanActForGroups(Set<String> canActForGroups) {
    this.canActForGroups = canActForGroups;
  }

  public Map<String, Set<String>> getResourcePolicy() {
    return resourcePolicy;
  }

  public void setResourcePolicy(Map<String, Set<String>> resourcePolicy) {
    this.resourcePolicy = resourcePolicy;
  }
}

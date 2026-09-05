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

import java.util.Collections;
import java.util.List;

/**
 * Response body for GET (list) on {@link DelegationPolicyResource}.
 * Mirrors {@link org.apache.knox.gateway.services.knoxidf.delegation.DelegationPolicyList}:
 * hasMore=true means the result is incomplete because the configured listing capacity was
 * exceeded. This is an operational/capacity condition on the server, not a client paging
 * mechanism -- there is no cursor or page token.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DelegationPolicyListResponse {

  private List<DelegationPolicyResponse> policies = Collections.emptyList();
  private boolean hasMore;

  public List<DelegationPolicyResponse> getPolicies() {
    return policies;
  }

  public void setPolicies(List<DelegationPolicyResponse> policies) {
    this.policies = List.copyOf(policies != null ? policies : Collections.emptyList());
  }

  public boolean isHasMore() {
    return hasMore;
  }

  public void setHasMore(boolean hasMore) {
    this.hasMore = hasMore;
  }
}

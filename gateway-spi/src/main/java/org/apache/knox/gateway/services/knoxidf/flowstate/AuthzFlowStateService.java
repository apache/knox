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
package org.apache.knox.gateway.services.knoxidf.flowstate;

import org.apache.knox.gateway.services.Service;

import java.util.Optional;

/**
 * Short-lived state of in-flight KnoxIDF authorization flows, written on one browser request and
 * read on a later one, which in HA may reach another gateway instance. Entries expire after
 * {@link org.apache.knox.gateway.config.GatewayConfig#getKnoxIDFAuthzFlowStateTtlSecs()}.
 */
public interface AuthzFlowStateService extends Service {

  /**
   * Stores the payload under the kind and id, replacing an existing entry and restarting its TTL.
   *
   * @throws AuthzFlowStateServiceException if the state cannot be stored
   */
  void put(AuthzFlowStateKind kind, String id, String payload);

  /**
   * @return the payload stored under the kind and id, or empty if there is none or it expired
   */
  Optional<String> get(AuthzFlowStateKind kind, String id);

  /**
   * Reads and removes the entry atomically: of concurrent callers on any gateway instance, at most
   * one gets the payload.
   *
   * @return the payload, or empty if there is none, it expired or another caller consumed it
   */
  Optional<String> consume(AuthzFlowStateKind kind, String id);

  /**
   * Removes the entry, if there is one.
   */
  void remove(AuthzFlowStateKind kind, String id);

  /**
   * Removes all expired entries.
   *
   * @return the number of removed entries
   */
  int removeExpired();
}

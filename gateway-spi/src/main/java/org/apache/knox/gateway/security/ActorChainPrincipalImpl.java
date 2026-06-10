/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implementation of ActorChainPrincipal that holds the RFC 8693 actor delegation chain.
 */
public class ActorChainPrincipalImpl implements ActorChainPrincipal {
  private final List<Map<String, Object>> actorChain;

  /**
   * Creates a new ActorChainPrincipal with the specified actor chain.
   *
   * @param actorChain the list of actor claim maps, ordered from most recent to oldest
   */
  public ActorChainPrincipalImpl(List<Map<String, Object>> actorChain) {
    this.actorChain = actorChain == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(actorChain));
  }

  @Override
  public List<Map<String, Object>> getActorChain() {
    return actorChain;
  }

  @Override
  public String getCurrentActor() {
    if (actorChain.isEmpty()) {
      return null;
    }
    Object sub = actorChain.get(0).get("sub");
    return sub != null ? sub.toString() : null;
  }

  @Override
  public String getOriginalDelegator() {
    if (actorChain.isEmpty()) {
      return null;
    }
    Object sub = actorChain.get(actorChain.size() - 1).get("sub");
    return sub != null ? sub.toString() : null;
  }

  @Override
  public String getName() {
    // Return the current actor's identity as the principal name
    String currentActor = getCurrentActor();
    return currentActor != null ? currentActor : "";
  }

  @Override
  public String toString() {
    return "ActorChainPrincipal[actors=" + actorChain.size() + ", current=" + getCurrentActor() + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof ActorChainPrincipal)) {
      return false;
    }

    ActorChainPrincipal that = (ActorChainPrincipal) o;
    return actorChain.equals(that.getActorChain());
  }

  @Override
  public int hashCode() {
    return actorChain.hashCode();
  }
}

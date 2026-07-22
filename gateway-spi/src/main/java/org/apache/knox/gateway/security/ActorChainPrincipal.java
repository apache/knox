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

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * A Principal that represents the chain of actors (delegation chain) from RFC 8693 token exchange.
 *
 * <p>This principal is used to represent the 'act' claim chain from a JWT, which provides
 * a means to express that delegation has occurred and identify the acting parties to whom
 * authority has been delegated.</p>
 *
 * <p>The actor chain is ordered from most recent (current actor) to oldest (original delegator).
 * Each actor in the chain is represented as a Map containing identity claims. According to
 * RFC 8693 Section 4.1:</p>
 * <ul>
 *   <li>Identity claims such as 'sub' (subject) and 'iss' (issuer) should be used to identify actors</li>
 *   <li>The combination of 'iss' and 'sub' may be necessary to uniquely identify an actor</li>
 *   <li>Non-identity claims (e.g., 'exp', 'nbf', 'aud') are NOT meaningful within 'act' claims
 *       and should not be used</li>
 * </ul>
 *
 * <p>Example chain structure:</p>
 * <pre>
 * [
 *   {"sub": "service-c", "iss": "https://issuer.example.com"},  // Most recent actor
 *   {"sub": "service-b", "iss": "https://issuer.example.com"},  // Previous actor
 *   {"sub": "service-a"}                                         // Original delegator
 * ]
 * </pre>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-4.1">RFC 8693 Section 4.1 - Actor Claim</a>
 */
public interface ActorChainPrincipal extends Principal {

  /**
   * Returns the chain of actors in order from most recent to oldest.
   *
   * <p>Each Map in the list represents an actor's claims, with at minimum a 'sub' claim.
   * The first element in the list is the most recent actor (the one who directly
   * performed the current delegation), and the last element is the original delegator.</p>
   *
   * @return an immutable list of actor claim maps, never null but may be empty
   */
  List<Map<String, Object>> getActorChain();

  /**
   * Returns the subject (identity) of the most recent actor in the chain.
   *
   * <p>This is equivalent to calling {@code getActorChain().get(0).get("sub")}
   * if the chain is not empty.</p>
   *
   * @return the subject of the most recent actor, or null if the chain is empty
   */
  String getCurrentActor();

  /**
   * Returns the subject (identity) of the original delegator (the first actor in the chain).
   *
   * <p>This is the actor who initiated the delegation chain. It is equivalent to calling
   * {@code getActorChain().get(getActorChain().size() - 1).get("sub")}
   * if the chain is not empty.</p>
   *
   * @return the subject of the original delegator, or null if the chain is empty
   */
  String getOriginalDelegator();
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.provider.federation.jwt.filter;

import org.apache.knox.gateway.services.security.token.impl.JWT;

/**
 * The (actorAuthority, actorId) pair identifying the actor of an RFC 8693 token exchange.
 *
 * <p>It is the delegation policy's unique lookup key and, because it is unique, doubles as the
 * audit record's resourceName (see {@link #resourceName()}). Delegation policies are registered
 * and looked up by this pair.</p>
 */
final class ActorIdentity {

  private static final String K8S_SERVICE_ACCOUNT_SUBJECT_PREFIX = "system:serviceaccount:";
  private static final String K8S_SA_ACTOR_AUTHORITY = "K8S_SA";
  private static final String USER_ACTOR_AUTHORITY = "USER";

  final String actorAuthority;
  final String actorId;

  private ActorIdentity(String actorAuthority, String actorId) {
    this.actorAuthority = actorAuthority;
    this.actorId = actorId;
  }

  /**
   * Derive the (actorAuthority, actorId) pair identifying the actor for a delegation policy
   * check, from the actor's validated JWT (the actor_token when one is present, or the
   * subject_token acting as actor for a headless delegation exchange). actorAuthority is a
   * fixed type tag identifying what kind of actor this is. A Kubernetes service-account subject
   * (sub of the form "system:serviceaccount:&lt;namespace&gt;:&lt;sa-name&gt;") is tagged K8S_SA,
   * with an actorId composed of its issuer, namespace, and service-account name, concatenated with
   * colon separators. Every other subject is tagged USER, with its own subject as actorId verbatim.
   * Knox managed client policies, tagged with CLIENT_ID, are deferred.
   *
   * @param actorJwt the actor's validated JWT
   * @return the derived actor identity
   */
  static ActorIdentity fromJwt(JWT actorJwt) {
    final String subject = actorJwt.getSubject();
    if (subject != null && subject.startsWith(K8S_SERVICE_ACCOUNT_SUBJECT_PREFIX)) {
      final String namespaceAndName = subject.substring(K8S_SERVICE_ACCOUNT_SUBJECT_PREFIX.length());
      return new ActorIdentity(K8S_SA_ACTOR_AUTHORITY, actorJwt.getIssuer() + ":" + namespaceAndName);
    }
    return new ActorIdentity(USER_ACTOR_AUTHORITY, subject);
  }

  /**
   * The (actorAuthority, actorId) pair is the delegation policy's unique lookup key and hence can
   * act as the audit record's unique resourceName.
   */
  String resourceName() {
    return actorAuthority + "/" + actorId;
  }
}

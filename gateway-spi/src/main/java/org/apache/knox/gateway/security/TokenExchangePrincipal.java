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

/**
 * Principal that represents an RFC 8693 token exchange operation.
 *
 * <p>This principal carries metadata about both the subject and actor tokens
 * through the authentication and identity assertion pipeline. It signals to
 * downstream layers that a token exchange has occurred and provides the
 * necessary information to establish proper impersonation semantics.</p>
 *
 * <p>In RFC 8693 token exchange:</p>
 * <ul>
 *   <li><b>Subject Token</b>: Represents the identity of the party on behalf
 *       of whom the request is being made (will become ImpersonatedPrincipal)</li>
 *   <li><b>Actor Token</b>: Represents the identity of the acting party who
 *       is authorized to act (will become PrimaryPrincipal)</li>
 * </ul>
 *
 * <p>This principal is added to the Subject during authentication/federation
 * and is used by the identity assertion layer to establish the correct
 * impersonation relationship using Subject.doAs().</p>
 *
 * <p>Example usage flow:</p>
 * <pre>
 * // In JWTFederationFilter (authentication layer):
 * JWT subjectToken = validateSubjectToken(request);
 * JWT actorToken = validateActorToken(request);
 * TokenExchangePrincipal tep = new TokenExchangePrincipalImpl(
 *     subjectToken.getSubject(), subjectToken.getIssuer(),
 *     actorToken.getSubject(), actorToken.getIssuer()
 * );
 * subject.getPrincipals().add(tep);
 *
 * // In AbstractIdentityAssertionFilter (identity assertion layer):
 * TokenExchangePrincipal tep = getTokenExchangePrincipal(subject);
 * if (tep != null) {
 *     // Set up impersonation: actor acts as subject
 *     mappedPrincipalName = tep.getSubjectPrincipalName();
 *     // This triggers existing impersonation logic
 * }
 * </pre>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-2.1">RFC 8693 Section 2.1</a>
 */
public interface TokenExchangePrincipal extends Principal {

  /**
   * Get the subject principal name (the identity on behalf of whom the request is made).
   *
   * <p>In the token exchange flow, this is extracted from the subject_token parameter.
   * This identity will typically become the {@link ImpersonatedPrincipal} after the
   * identity assertion layer processes the token exchange.</p>
   *
   * @return the subject principal name, never null
   */
  String getSubjectPrincipalName();

  /**
   * Get the issuer of the subject token.
   *
   * <p>Combined with the subject principal name, this provides a globally unique
   * identity for the subject. This is important for audit trails and security
   * decisions where the issuer context matters.</p>
   *
   * @return the subject token issuer, may be null if not available
   */
  String getSubjectIssuer();

  /**
   * Get the actor principal name (the identity of the acting party).
   *
   * <p>In the token exchange flow, this is extracted from the actor_token parameter.
   * This identity represents the authenticated party who is authorized to act on
   * behalf of the subject. This will typically be the {@link PrimaryPrincipal}.</p>
   *
   * @return the actor principal name, never null
   */
  String getActorPrincipalName();

  /**
   * Get the issuer of the actor token.
   *
   * <p>Combined with the actor principal name, this provides a globally unique
   * identity for the actor. This is important for audit trails and security
   * decisions where the issuer context matters.</p>
   *
   * @return the actor token issuer, may be null if not available
   */
  String getActorIssuer();

  /**
   * Returns the actor principal name.
   *
   * <p>This implements {@link Principal#getName()} and returns the actor's identity
   * since the actor is the authenticated party performing the token exchange.</p>
   *
   * @return the actor principal name
   */
  @Override
  default String getName() {
    return getActorPrincipalName();
  }
}

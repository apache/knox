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

import java.util.Objects;

/**
 * Implementation of TokenExchangePrincipal that holds RFC 8693 token exchange metadata.
 */
public class TokenExchangePrincipalImpl implements TokenExchangePrincipal {
  private final String subjectPrincipalName;
  private final String subjectIssuer;
  private final String actorPrincipalName;
  private final String actorIssuer;

  /**
   * Creates a new TokenExchangePrincipal with the specified subject and actor identities.
   *
   * @param subjectPrincipalName the subject principal name (required)
   * @param subjectIssuer the subject token issuer (may be null)
   * @param actorPrincipalName the actor principal name (required)
   * @param actorIssuer the actor token issuer (may be null)
   * @throws IllegalArgumentException if subjectPrincipalName or actorPrincipalName is null
   */
  public TokenExchangePrincipalImpl(String subjectPrincipalName, String subjectIssuer,
                                    String actorPrincipalName, String actorIssuer) {
    if (subjectPrincipalName == null || subjectPrincipalName.isEmpty()) {
      throw new IllegalArgumentException("Subject principal name cannot be null or empty");
    }
    if (actorPrincipalName == null || actorPrincipalName.isEmpty()) {
      throw new IllegalArgumentException("Actor principal name cannot be null or empty");
    }
    this.subjectPrincipalName = subjectPrincipalName;
    this.subjectIssuer = subjectIssuer;
    this.actorPrincipalName = actorPrincipalName;
    this.actorIssuer = actorIssuer;
  }

  @Override
  public String getSubjectPrincipalName() {
    return subjectPrincipalName;
  }

  @Override
  public String getSubjectIssuer() {
    return subjectIssuer;
  }

  @Override
  public String getActorPrincipalName() {
    return actorPrincipalName;
  }

  @Override
  public String getActorIssuer() {
    return actorIssuer;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("TokenExchangePrincipal[actor=");
    sb.append(actorPrincipalName);
    if (actorIssuer != null) {
      sb.append("@").append(actorIssuer);
    }
    sb.append(", subject=").append(subjectPrincipalName);
    if (subjectIssuer != null) {
      sb.append("@").append(subjectIssuer);
    }
    sb.append("]");
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TokenExchangePrincipal)) {
      return false;
    }
    TokenExchangePrincipal that = (TokenExchangePrincipal) o;
    return Objects.equals(subjectPrincipalName, that.getSubjectPrincipalName())
        && Objects.equals(subjectIssuer, that.getSubjectIssuer())
        && Objects.equals(actorPrincipalName, that.getActorPrincipalName())
        && Objects.equals(actorIssuer, that.getActorIssuer());
  }

  @Override
  public int hashCode() {
    return Objects.hash(subjectPrincipalName, subjectIssuer, actorPrincipalName, actorIssuer);
  }
}

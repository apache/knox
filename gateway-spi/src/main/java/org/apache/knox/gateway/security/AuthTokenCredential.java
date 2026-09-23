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

import org.apache.knox.gateway.util.Tokens;

/**
 * Carries the caller's serialized JWT through the request's security context.
 * <p>
 * This is held in {@link javax.security.auth.Subject#getPrivateCredentials()} rather than as a
 * {@link java.security.Principal}, because a bearer token is a credential and not an identity.
 * Modelling it as a principal would expose it to
 * {@link SubjectUtils#getPrimaryPrincipalName(javax.security.auth.Subject)}, which falls back to
 * the first principal it finds when no {@link PrimaryPrincipal} is present -- the raw token could
 * then surface as a user name in audit records and actor headers.
 * <p>
 * Consumers read it with {@link SubjectUtils#getAuthToken(javax.security.auth.Subject)}.
 */
public class AuthTokenCredential {
  private final String token;

  /**
   * @param token the serialized JWT (required)
   * @throws IllegalArgumentException if token is null or empty
   */
  public AuthTokenCredential(String token) {
    if (token == null || token.isEmpty()) {
      throw new IllegalArgumentException("Auth token cannot be null or empty");
    }
    this.token = token;
  }

  public String getToken() {
    return token;
  }

  /**
   * Deliberately redacted: this value reaches log lines, so it must never render the raw token.
   */
  @Override
  public String toString() {
    final String displayText = Tokens.getTokenDisplayText(token);
    return "AuthTokenCredential[" + (displayText == null ? "****" : displayText) + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AuthTokenCredential that = (AuthTokenCredential) o;
    return token.equals(that.token);
  }

  @Override
  public int hashCode() {
    return token.hashCode();
  }
}

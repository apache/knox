/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.services.token.impl;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * In-Memory authentication token state management implementation.
 */
public class DefaultTokenStateService implements TokenStateService {

  protected static final long DEFAULT_RENEWAL_INTERVAL = 24 * 60 * 60 * 1000; // 24 hours

  protected static final int MAX_RENEWALS = 7;

  protected static final long DEFAULT_MAX_LIFETIME = MAX_RENEWALS * DEFAULT_RENEWAL_INTERVAL; // 7 days

  private final Map<String, Long> tokenExpirations = new HashMap<>();

  private final Set<String> revokedTokens = new HashSet<>();

  private final Map<String, Long> maxTokenLifetimes = new HashMap<>();

  private long maxLifetimeInterval = DEFAULT_MAX_LIFETIME;


  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
//    maxLifetimeInterval = ??; // TODO: PJZ: Honor gateway configuration for this value, if specified ?
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }

  @Override
  public void addToken(final JWTToken token, final long issueTime) {
    if (token == null) {
      throw new IllegalArgumentException("Token data cannot be null.");
    }
    addToken(token.getPayload(), issueTime, token.getExpiresDate().getTime());
  }

  @Override
  public void addToken(final String token, final long issueTime, final long expiration) {
    if (!isValidIdentifier(token)) {
      throw new IllegalArgumentException("Token data cannot be null.");
    }
    synchronized (tokenExpirations) {
      tokenExpirations.put(token, expiration);
    }
    setMaxLifetime(token, issueTime);
  }

  @Override
  public long getTokenExpiration(String token) {
    long expiration;

    validateToken(token);

    synchronized (tokenExpirations) {
      expiration = tokenExpirations.get(token);
    }

    return expiration;
  }

  @Override
  public long renewToken(final JWTToken token) {
    return renewToken(token, DEFAULT_RENEWAL_INTERVAL);
  }

  @Override
  public long renewToken(final JWTToken token, final Long renewInterval) {
    if (token == null) {
      throw new IllegalArgumentException("Token data cannot be null.");
    }
    return renewToken(token.getPayload(), renewInterval);
  }

  @Override
  public long renewToken(final String token) { // Should return new expiration?
    return renewToken(token, DEFAULT_RENEWAL_INTERVAL);
  }

  @Override
  public long renewToken(final String token, final Long renewInterval) { // Should return new expiration?
    long expiration;

    validateToken(token, true);

    // Make sure the maximum lifetime has not been (and will not be) exceeded
    if (hasRemainingRenewals(token, (renewInterval != null ? renewInterval : DEFAULT_RENEWAL_INTERVAL))) {
      expiration = System.currentTimeMillis() + (renewInterval != null ? renewInterval : DEFAULT_RENEWAL_INTERVAL);
      updateExpiration(token, expiration);
    } else {
      throw new IllegalArgumentException("The renewal limit for the token has been exceeded");
    }

    return expiration;
  }

  @Override
  public void revokeToken(final JWTToken token) {
    if (token == null) {
      throw new IllegalArgumentException("Token data cannot be null.");
    }

    revokeToken(token.getPayload());
  }

  @Override
  public void revokeToken(final String token) {
    validateToken(token);
    revokedTokens.add(token);
  }

  @Override
  public boolean isExpired(final JWTToken token) {
    return isExpired(token.getPayload());
  }

  @Override
  public boolean isExpired(final String token) {
    boolean isExpired;

    isExpired = isRevoked(token); // Check if it has been revoked first
    if (!isExpired) {
      // If it has not been revoked, check its expiration
      isExpired = (getTokenExpiration(token) <= System.currentTimeMillis());
    }

    return isExpired;
  }

  protected void setMaxLifetime(final String token, final long issueTime) {
    synchronized (maxTokenLifetimes) {
      maxTokenLifetimes.put(token, issueTime + maxLifetimeInterval);
    }
  }

  /**
   * @param token
   * @return false, if the service has previously stored the specified token; Otherwise, true.
   */
  protected boolean isUnknown(final String token) {
    boolean isUnknown;

    synchronized (tokenExpirations) {
      isUnknown = !(tokenExpirations.containsKey(token));
    }

    return isUnknown;
  }

  protected void updateExpiration(final String token, long expiration) {
    synchronized (tokenExpirations) {
      tokenExpirations.replace(token, expiration);
    }
  }

  protected boolean hasRemainingRenewals(final String token, final Long renewInterval) {
    // Is the current time + 30-second buffer + the renewal interval is less than the max lifetime for the token?
    return ((System.currentTimeMillis() + 30000 + renewInterval) < getMaxLifetime(token));
  }

  protected long getMaxLifetime(final String token) {
    long result;
    synchronized (maxTokenLifetimes) {
      result = maxTokenLifetimes.getOrDefault(token, 0L);
    }
    return result;
  }

  protected boolean isRevoked(final String token) {
    return revokedTokens.contains(token);
  }

  protected long getMaxLifetimeInterval() {
    return maxLifetimeInterval;
  }

  protected boolean isValidIdentifier(final String token) {
    return token != null && !token.isEmpty();
  }

  /**
   * Validate the specified token identifier.
   *
   * @param token The token identifier to validate.
   *
   * @throws IllegalArgumentException if the specified token in invalid.
   */
  protected void validateToken(final String token) throws IllegalArgumentException {
    validateToken(token, false);
  }

  /**
   * Validate the specified token identifier.
   *
   * @param token              The token identifier to validate.
   * @param includeRevocation  true, if the revocation status of the specified token should be considered in the validation.
   *
   * @throws IllegalArgumentException if the specified token in invalid.
   */
  protected void validateToken(final String token, final boolean includeRevocation) throws IllegalArgumentException {
    if (!isValidIdentifier(token)) {
      throw new IllegalArgumentException("Token data cannot be null.");
    }

    // First, make sure the token is one we know about
    if (isUnknown(token)) {
      throw new IllegalArgumentException("Unknown token");
    }

    // Then, make sure it has not been revoked
    if (includeRevocation && isRevoked(token)) {
      throw new IllegalArgumentException("The specified token has been revoked");
    }
  }

}

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
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * In-Memory authentication token state management implementation.
 */
public class DefaultTokenStateService implements TokenStateService {

  protected static final long DEFAULT_RENEWAL_INTERVAL = 24 * 60 * 60 * 1000L; // 24 hours

  protected static final int MAX_RENEWALS = 7;

  protected static final long DEFAULT_MAX_LIFETIME = MAX_RENEWALS * DEFAULT_RENEWAL_INTERVAL; // 7 days

  protected static final TokenStateServiceMessages log = MessagesFactory.get(TokenStateServiceMessages.class);

  private final Map<String, Long> tokenExpirations = new HashMap<>();

  private final Map<String, Long> maxTokenLifetimes = new HashMap<>();

  /* token eviction interval in seconds */
  private long tokenEvictionInterval;
  /* grace period (in seconds) after which an expired token should be evicted */
  private long tokenEvictionGracePeriod;

  private final ScheduledExecutorService evictionScheduler = Executors.newScheduledThreadPool(1);


  @Override
  public void init(final GatewayConfig config, final Map<String, String> options) throws ServiceLifecycleException {
    tokenEvictionInterval = config.getKnoxTokenEvictionInterval();
    tokenEvictionGracePeriod = config.getKnoxTokenEvictionGracePeriod();
  }

  @Override
  public void start() throws ServiceLifecycleException {
    if (tokenEvictionInterval > 0) {
      /* run token eviction task at configured intervals */
      evictionScheduler.scheduleAtFixedRate(() -> evictExpiredTokens(), tokenEvictionInterval, tokenEvictionInterval, TimeUnit.SECONDS);
    }
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    evictionScheduler.shutdown();
  }

  @Override
  public long getDefaultRenewInterval() {
    return DEFAULT_RENEWAL_INTERVAL;
  }

  @Override
  public long getDefaultMaxLifetimeDuration() {
    return DEFAULT_MAX_LIFETIME;
  }

  @Override
  public void addToken(final JWTToken token, long issueTime) {
    if (token == null) {
      throw new IllegalArgumentException("Token data cannot be null.");
    }
    addToken(token.getPayload(), issueTime, token.getExpiresDate().getTime());
  }

  @Override
  public void addToken(final String token, long issueTime, long expiration) {
    addToken(token, issueTime, expiration, getDefaultMaxLifetimeDuration());
  }

  @Override
  public void addToken(final String token,
                       long         issueTime,
                       long         expiration,
                       long         maxLifetimeDuration) {
    if (!isValidIdentifier(token)) {
      throw new IllegalArgumentException("Token data cannot be null.");
    }
    synchronized (tokenExpirations) {
      tokenExpirations.put(token, expiration);
    }
    setMaxLifetime(token, issueTime, maxLifetimeDuration);
    log.addedToken(TokenUtils.getTokenDisplayText(token), getTimestampDisplay(expiration));
  }

  @Override
  public long getTokenExpiration(final String token) throws UnknownTokenException {
    long expiration;

    validateToken(token);

    synchronized (tokenExpirations) {
      expiration = tokenExpirations.get(token);
    }

    return expiration;
  }

  @Override
  public long renewToken(final JWTToken token) throws UnknownTokenException {
    return renewToken(token, DEFAULT_RENEWAL_INTERVAL);
  }

  @Override
  public long renewToken(final JWTToken token, long renewInterval) throws UnknownTokenException {
    if (token == null) {
      throw new IllegalArgumentException("Token data cannot be null.");
    }
    return renewToken(token.getPayload(), renewInterval);
  }

  @Override
  public long renewToken(final String token) throws UnknownTokenException {
    return renewToken(token, DEFAULT_RENEWAL_INTERVAL);
  }

  @Override
  public long renewToken(final String token, long renewInterval) throws UnknownTokenException {
    long expiration;

    validateToken(token);

    // Make sure the maximum lifetime has not been (and will not be) exceeded
    if (hasRemainingRenewals(token, renewInterval)) {
      expiration = System.currentTimeMillis() + renewInterval;
      updateExpiration(token, expiration);
      log.renewedToken(TokenUtils.getTokenDisplayText(token), getTimestampDisplay(expiration));
    } else {
      log.renewalLimitExceeded(token);
      throw new IllegalArgumentException("The renewal limit for the token has been exceeded");
    }

    return expiration;
  }

  @Override
  public void revokeToken(final JWTToken token) throws UnknownTokenException {
    if (token == null) {
      throw new IllegalArgumentException("Token data cannot be null.");
    }

    revokeToken(token.getPayload());
  }

  @Override
  public void revokeToken(final String token) throws UnknownTokenException {
    /* no reason to keep revoked tokens around */
    removeToken(token);
    log.revokedToken(TokenUtils.getTokenDisplayText(token));
  }

  @Override
  public boolean isExpired(final JWTToken token) throws UnknownTokenException {
    return isExpired(token.getPayload());
  }

  @Override
  public boolean isExpired(final String token) throws UnknownTokenException {
    boolean isExpired;
    isExpired = isUnknown(token); // Check if the token exist
    if (!isExpired) {
      // If it not unknown, check its expiration
      isExpired = (getTokenExpiration(token) <= System.currentTimeMillis());
    }
    return isExpired;
  }

  protected void setMaxLifetime(final String token, long issueTime, long maxLifetimeDuration) {
    synchronized (maxTokenLifetimes) {
      maxTokenLifetimes.put(token, issueTime + maxLifetimeDuration);
    }
  }

  /**
   * @param token token to check
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

  protected void removeToken(final String token) throws UnknownTokenException {
    validateToken(token);
    synchronized (tokenExpirations) {
      tokenExpirations.remove(token);
    }
    synchronized (maxTokenLifetimes) {
      maxTokenLifetimes.remove(token);
    }
    log.removedTokenState(TokenUtils.getTokenDisplayText(token));
  }

  protected boolean hasRemainingRenewals(final String token, long renewInterval) {
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

  protected boolean isValidIdentifier(final String token) {
    return token != null && !token.isEmpty();
  }

  /**
   * Validate the specified token identifier.
   *
   * @param token The token identifier to validate.
   *
   * @throws IllegalArgumentException if the specified token in invalid.
   * @throws UnknownTokenException if the specified token in valid, but not known to the service.
   */
  protected void validateToken(final String token) throws IllegalArgumentException, UnknownTokenException {
    if (!isValidIdentifier(token)) {
      throw new IllegalArgumentException("Token data cannot be null.");
    }

    // First, make sure the token is one we know about
    if (isUnknown(token)) {
      log.unknownToken(TokenUtils.getTokenDisplayText(token));
      throw new UnknownTokenException(token);
    }
  }

  protected String getTimestampDisplay(long timestamp) {
    return Instant.ofEpochMilli(timestamp).toString();
  }

  /**
   * Method that deletes expired tokens based on the token timestamp.
   */
  protected void evictExpiredTokens() {
    for (final String token : getTokens()) {
      try {
        if (needsEviction(token)) {
          log.evictToken(TokenUtils.getTokenDisplayText(token));
          removeToken(token);
        }
      } catch (final Exception e) {
        log.failedExpiredTokenEviction(TokenUtils.getTokenDisplayText(token), e);
      }
    }
  }

  /**
   * Method that checks if an expired token is ready to be evicted
   * by adding configured grace period to the expiry time.
   * @param token
   * @return
   */
  protected boolean needsEviction(final String token) throws UnknownTokenException {
    return ((getTokenExpiration(token) + TimeUnit.SECONDS.toMillis(tokenEvictionGracePeriod)) <= System.currentTimeMillis());
  }

  /**
   * Get a list of tokens
   *
   * @return
   */
  protected List<String> getTokens() {
    return tokenExpirations.keySet().stream().collect(Collectors.toList());
  }

}

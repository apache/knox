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
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
  /* should knox token fail permissively */
  protected boolean permissiveValidationEnabled;

  private final ScheduledExecutorService evictionScheduler = Executors.newScheduledThreadPool(1);


  @Override
  public void init(final GatewayConfig config, final Map<String, String> options) throws ServiceLifecycleException {
    tokenEvictionInterval = config.getKnoxTokenEvictionInterval();
    tokenEvictionGracePeriod = config.getKnoxTokenEvictionGracePeriod();
    permissiveValidationEnabled = config.isKnoxTokenPermissiveValidationEnabled();
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
      throw new IllegalArgumentException("Token cannot be null.");
    }
    addToken(TokenUtils.getTokenId(token), issueTime, token.getExpiresDate().getTime());
  }

  @Override
  public void addToken(final String tokenId, long issueTime, long expiration) {
    addToken(tokenId, issueTime, expiration, getDefaultMaxLifetimeDuration());
  }

  @Override
  public void addToken(final String tokenId,
                             long   issueTime,
                             long   expiration,
                             long   maxLifetimeDuration) {
    if (!isValidIdentifier(tokenId)) {
      throw new IllegalArgumentException("Token identifier cannot be null.");
    }
    synchronized (tokenExpirations) {
      tokenExpirations.put(tokenId, expiration);
    }
    setMaxLifetime(tokenId, issueTime, maxLifetimeDuration);
    log.addedToken(tokenId, getTimestampDisplay(expiration));
  }

  @Override
  public long getTokenExpiration(final JWT token) throws UnknownTokenException {
    long expiration = -1;

    try {
      expiration = getTokenExpiration(TokenUtils.getTokenId(token));
    } catch (UnknownTokenException e) {
      if (permissiveValidationEnabled) {
        String exp = token.getExpires();
        if (exp != null) {
          log.permissiveTokenHandling(TokenUtils.getTokenId(token), e.getMessage());
          expiration = Long.parseLong(exp);
        }
      }

      if (expiration == -1) {
        throw e;
      }
    }

    return expiration;
  }

  @Override
  public long getTokenExpiration(final String tokenId) throws UnknownTokenException {
    long expiration;

    validateToken(tokenId);

    synchronized (tokenExpirations) {
      expiration = tokenExpirations.get(tokenId);
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
      throw new IllegalArgumentException("Token cannot be null.");
    }
    return renewToken(TokenUtils.getTokenId(token), renewInterval);
  }

  @Override
  public long renewToken(final String tokenId) throws UnknownTokenException {
    return renewToken(tokenId, DEFAULT_RENEWAL_INTERVAL);
  }

  @Override
  public long renewToken(final String tokenId, long renewInterval) throws UnknownTokenException {
    long expiration;

    validateToken(tokenId);

    // Make sure the maximum lifetime has not been (and will not be) exceeded
    if (hasRemainingRenewals(tokenId, renewInterval)) {
      expiration = System.currentTimeMillis() + renewInterval;
      updateExpiration(tokenId, expiration);
      log.renewedToken(tokenId, getTimestampDisplay(expiration));
    } else {
      log.renewalLimitExceeded(tokenId);
      throw new IllegalArgumentException("The renewal limit for the token has been exceeded");
    }

    return expiration;
  }

  @Override
  public void revokeToken(final JWTToken token) throws UnknownTokenException {
    if (token == null) {
      throw new IllegalArgumentException("Token cannot be null.");
    }

    revokeToken(TokenUtils.getTokenId(token));
  }

  @Override
  public void revokeToken(final String tokenId) throws UnknownTokenException {
    /* no reason to keep revoked tokens around */
    removeToken(tokenId);
    log.revokedToken(tokenId);
  }

  @Override
  public boolean isExpired(final JWTToken token) throws UnknownTokenException {
    return getTokenExpiration(token) <= System.currentTimeMillis();
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

  protected void updateExpiration(final String tokenId, long expiration) {
    synchronized (tokenExpirations) {
      tokenExpirations.replace(tokenId, expiration);
    }
  }

  protected void removeToken(final String tokenId) throws UnknownTokenException {
    validateToken(tokenId);
    synchronized (tokenExpirations) {
      tokenExpirations.remove(tokenId);
    }
    synchronized (maxTokenLifetimes) {
      maxTokenLifetimes.remove(tokenId);
    }
    log.removedTokenState(tokenId);
  }

  protected boolean hasRemainingRenewals(final String tokenId, long renewInterval) {
    // Is the current time + 30-second buffer + the renewal interval is less than the max lifetime for the token?
    return ((System.currentTimeMillis() + 30000 + renewInterval) < getMaxLifetime(tokenId));
  }

  protected long getMaxLifetime(final String tokenId) {
    long result;
    synchronized (maxTokenLifetimes) {
      result = maxTokenLifetimes.getOrDefault(tokenId, 0L);
    }
    return result;
  }

  protected boolean isValidIdentifier(final String tokenId) {
    return tokenId != null && !tokenId.isEmpty();
  }

  /**
   * Validate the specified token identifier.
   *
   * @param tokenId The token identifier to validate.
   *
   * @throws IllegalArgumentException if the specified token in invalid.
   * @throws UnknownTokenException if the specified token in valid, but not known to the service.
   */
  protected void validateToken(final String tokenId) throws IllegalArgumentException, UnknownTokenException {
    if (!isValidIdentifier(tokenId)) {
      throw new IllegalArgumentException("Token identifier cannot be null.");
    }

    // First, make sure the token is one we know about
    if (isUnknown(tokenId)) {
      log.unknownToken(tokenId);
      throw new UnknownTokenException(tokenId);
    }
  }

  protected String getTimestampDisplay(long timestamp) {
    return Instant.ofEpochMilli(timestamp).toString();
  }

  /**
   * Method that deletes expired tokens based on the token timestamp.
   */
  protected void evictExpiredTokens() {
    for (final String tokenId : getTokens()) {
      try {
        if (needsEviction(tokenId)) {
          log.evictToken(tokenId);
          removeToken(tokenId);
        }
      } catch (final Exception e) {
        log.failedExpiredTokenEviction(tokenId, e);
      }
    }
  }

  /**
   * Method that checks if a token's state is a candidate for eviction.
   *
   * @param tokenId A unique token identifier
   * @throws UnknownTokenException Exception if token is not found.
   *
   * @return true, if the associated token state can be evicted; Otherwise, false.
   */
  protected boolean needsEviction(final String tokenId) throws UnknownTokenException {
    long maxLifetime = getMaxLifetime(tokenId);
    if (maxLifetime <= 0) {
      throw new UnknownTokenException(tokenId);
    }
    return ((maxLifetime + TimeUnit.SECONDS.toMillis(tokenEvictionGracePeriod)) <= System.currentTimeMillis());
  }

  /**
   * Get a list of tokens
   *
   * @return List of tokens
   */
  protected List<String> getTokens() {
    return new ArrayList<>(tokenExpirations.keySet());
  }
}

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
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A TokenStateService implementation based on the AliasService.
 */
public class AliasBasedTokenStateService extends DefaultTokenStateService {

  static final String TOKEN_MAX_LIFETIME_POSTFIX = "--max";

  private AliasService aliasService;

  private long statePersistenceInterval = TimeUnit.SECONDS.toSeconds(15);

  private ScheduledExecutorService statePersistenceScheduler;

  private final List<TokenState> unpersistedState = new ArrayList<>();

  public void setAliasService(AliasService aliasService) {
    this.aliasService = aliasService;
  }

  @Override
  public void init(final GatewayConfig config, final Map<String, String> options) throws ServiceLifecycleException {
    super.init(config, options);
    if (aliasService == null) {
      throw new ServiceLifecycleException("The required AliasService reference has not been set.");
    }
    statePersistenceInterval = config.getKnoxTokenStateAliasPersistenceInterval();
    if (statePersistenceInterval > 0) {
      statePersistenceScheduler = Executors.newScheduledThreadPool(1);
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
    super.start();
    if (statePersistenceScheduler != null) {
      // Run token eviction task at configured interval
      statePersistenceScheduler.scheduleAtFixedRate(this::persistTokenState,
                                                    statePersistenceInterval,
                                                    statePersistenceInterval,
                                                    TimeUnit.SECONDS);
    }
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    super.stop();
    if (statePersistenceScheduler != null) {
      statePersistenceScheduler.shutdown();
    }
  }

  protected void persistTokenState() {
    Set<String> tokenIds = new HashSet<>(); // Collect the tokenIds for logging

    List<TokenState> processing;
    synchronized (unpersistedState) {
      // Move unpersisted state to temp collection
      processing = new ArrayList<>(unpersistedState);
      unpersistedState.clear();
    }

    // Create a set of aliases based on the unpersisted TokenState objects
    Map<String, String> aliases = new HashMap<>();
    for (TokenState state : processing) {
      tokenIds.add(state.getTokenId());
      aliases.put(state.getAlias(), state.getAliasValue());
      log.creatingTokenStateAliases(state.getTokenId());
    }

    // Write aliases in a batch
    if (!aliases.isEmpty()) {
      log.creatingTokenStateAliases();

      try {
        aliasService.addAliasesForCluster(AliasService.NO_CLUSTER_NAME, aliases);
        for (String tokenId : tokenIds) {
          log.createdTokenStateAliases(tokenId);
        }
      } catch (AliasServiceException e) {
        log.failedToCreateTokenStateAliases(e);
        synchronized (unpersistedState) {
          unpersistedState.addAll(processing); // Restore the unpersisted state objects so they can be attempted later
        }
      }
    }
  }

  @Override
  public void addToken(final String tokenId,
                             long   issueTime,
                             long   expiration,
                             long   maxLifetimeDuration) {
    super.addToken(tokenId, issueTime, expiration, maxLifetimeDuration);

    synchronized (unpersistedState) {
      unpersistedState.add(new TokenExpiration(tokenId, expiration));
    }
  }

  @Override
  protected void setMaxLifetime(final String tokenId, long issueTime, long maxLifetimeDuration) {
    super.setMaxLifetime(tokenId, issueTime, maxLifetimeDuration);
    synchronized (unpersistedState) {
      unpersistedState.add(new TokenMaxLifetime(tokenId, issueTime, maxLifetimeDuration));
    }
  }

  @Override
  protected long getMaxLifetime(final String tokenId) {
    long result = super.getMaxLifetime(tokenId);

    // If there is no result from the in-memory collection, proceed to check the alias service
    if (result < 1L) {
      try {
        char[] maxLifetimeStr =
                aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME,
                        tokenId + TOKEN_MAX_LIFETIME_POSTFIX);
        if (maxLifetimeStr != null) {
          result = Long.parseLong(new String(maxLifetimeStr));
        }
      } catch (AliasServiceException e) {
        log.errorAccessingTokenState(tokenId, e);
      }
    }
    return result;
  }

  @Override
  public long getTokenExpiration(String tokenId, boolean validate) throws UnknownTokenException {
    long expiration = 0;

    if (!validate) {
      // If validation is not required, then check the in-memory collection first
      try {
        expiration = super.getTokenExpiration(tokenId, validate);
        return expiration;
      } catch (UnknownTokenException e) {
        // It's not in memory
      }
    }

    // If validating, or there is no associated record in the in-memory collection, proceed to check the alias service

    if (validate) {
      validateToken(tokenId);
    }

    try {
      char[] expStr = aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, tokenId);
      if (expStr != null) {
        expiration = Long.parseLong(new String(expStr));
        // Update the in-memory record
        super.updateExpiration(tokenId, expiration);
      }
    } catch (Exception e) {
      log.errorAccessingTokenState(tokenId, e);
    }

    return expiration;
  }

  @Override
  protected boolean isUnknown(final String tokenId) {
    boolean isUnknown = super.isUnknown(tokenId);

    // If it's not in the cache, then check the underlying alias
    if (isUnknown) {
      try {
        isUnknown = (aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, tokenId) == null);
      } catch (AliasServiceException e) {
        log.errorAccessingTokenState(tokenId, e);
      }
    }
    return isUnknown;
  }

  @Override
  protected void removeToken(final String tokenId) throws UnknownTokenException {
    removeTokens(Collections.singleton(tokenId));
  }

  @Override
  protected void removeTokens(Set<String> tokenIds) throws UnknownTokenException {
    // Add the max lifetime aliases to the list of aliases to remove
    Set<String> aliasesToRemove = new HashSet<>(tokenIds);
    for (String tokenId : tokenIds) {
      aliasesToRemove.add(tokenId + TOKEN_MAX_LIFETIME_POSTFIX);
      log.removingTokenStateAliases(tokenId);
    }

    if (!aliasesToRemove.isEmpty()) {
      log.removingTokenStateAliases();
      try {
        aliasService.removeAliasesForCluster(AliasService.NO_CLUSTER_NAME, aliasesToRemove);
        for (String tokenId : tokenIds) {
          log.removedTokenStateAliases(tokenId);
        }
      } catch (AliasServiceException e) {
        log.failedToRemoveTokenStateAliases(e);
      }
    }

    super.removeTokens(tokenIds);
  }

  @Override
  protected void updateExpiration(final String tokenId, long expiration) {
    super.updateExpiration(tokenId, expiration);
    try {
      aliasService.removeAliasForCluster(AliasService.NO_CLUSTER_NAME, tokenId);
      aliasService.addAliasForCluster(AliasService.NO_CLUSTER_NAME, tokenId, String.valueOf(expiration));
    } catch (AliasServiceException e) {
      log.failedToUpdateTokenExpiration(tokenId, e);
    }
  }

  @Override
  protected List<String> getTokens() {
    List<String> tokenIds = null;

    try {
      List<String> allAliases = aliasService.getAliasesForCluster(AliasService.NO_CLUSTER_NAME);

      // Filter for the token state aliases, and extract the token ID
      tokenIds = allAliases.stream()
                           .filter(a -> a.contains(TOKEN_MAX_LIFETIME_POSTFIX))
                           .map(a -> a.substring(0, a.indexOf(TOKEN_MAX_LIFETIME_POSTFIX)))
                           .collect(Collectors.toList());
    } catch (AliasServiceException e) {
      log.errorAccessingTokenState(e);
    }

    return (tokenIds != null ? tokenIds : Collections.emptyList());
  }

  private interface TokenState {
    String getTokenId();
    String getAlias();
    String getAliasValue();
  }

  private static final class TokenMaxLifetime implements TokenState {
    private String tokenId;
    private long   issueTime;
    private long   maxLifetime;

    TokenMaxLifetime(String tokenId, long issueTime, long maxLifetime) {
      this.tokenId     = tokenId;
      this.issueTime   = issueTime;
      this.maxLifetime = maxLifetime;
    }

    @Override
    public String getTokenId() {
      return tokenId;
    }

    @Override
    public String getAlias() {
      return tokenId + TOKEN_MAX_LIFETIME_POSTFIX;
    }

    @Override
    public String getAliasValue() {
      return String.valueOf(issueTime + maxLifetime);
    }
  }

  private static final class TokenExpiration implements TokenState {
    private String tokenId;
    private long   expiration;

    TokenExpiration(String tokenId, long expiration) {
      this.tokenId    = tokenId;
      this.expiration = expiration;
    }

    @Override
    public String getTokenId() {
      return tokenId;
    }

    @Override
    public String getAlias() {
      return tokenId;
    }

    @Override
    public String getAliasValue() {
      return String.valueOf(expiration);
    }
  }

}

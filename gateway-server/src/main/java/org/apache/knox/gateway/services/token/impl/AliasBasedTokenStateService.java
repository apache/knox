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
import org.apache.knox.gateway.services.token.state.JournalEntry;
import org.apache.knox.gateway.services.token.state.TokenStateJournal;
import org.apache.knox.gateway.services.token.impl.state.TokenStateJournalFactory;

import java.io.IOException;
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

  private TokenStateJournal journal;

  public void setAliasService(AliasService aliasService) {
    this.aliasService = aliasService;
  }

  @Override
  public void init(final GatewayConfig config, final Map<String, String> options) throws ServiceLifecycleException {
    super.init(config, options);
    if (aliasService == null) {
      throw new ServiceLifecycleException("The required AliasService reference has not been set.");
    }

    try {
      // Initialize the token state journal
      journal = TokenStateJournalFactory.create(config);

      // Load any persisted journal entries, and add them to the unpersisted state collection
      List<JournalEntry> entries = journal.get();
      for (JournalEntry entry : entries) {
        String id = entry.getTokenId();
        try {
          long issueTime   = Long.parseLong(entry.getIssueTime());
          long expiration  = Long.parseLong(entry.getExpiration());
          long maxLifetime = Long.parseLong(entry.getMaxLifetime());

          // Add the token state to memory
          super.addToken(id, issueTime, expiration, maxLifetime);

          synchronized (unpersistedState) {
            // The max lifetime entry is added by way of the call to super.addToken(),
            // so only need to add the expiration entry here.
            unpersistedState.add(new TokenExpiration(id, expiration));
          }
        } catch (Exception e) {
          log.failedToLoadJournalEntry(id, e);
        }
      }
    } catch (IOException e) {
      throw new ServiceLifecycleException("Failed to load persisted state from the token state journal", e);
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
      // Run token persistence task at configured interval
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

    // Make an attempt to persist any unpersisted token state before shutting down
    persistTokenState();
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
    }

    for (String tokenId: tokenIds) {
      log.creatingTokenStateAliases(tokenId);
    }

    // Write aliases in a batch
    if (!aliases.isEmpty()) {
      log.creatingTokenStateAliases();

      try {
        aliasService.addAliasesForCluster(AliasService.NO_CLUSTER_NAME, aliases);
        for (String tokenId : tokenIds) {
          log.createdTokenStateAliases(tokenId);
          // After the aliases have been successfully persisted, remove their associated state from the journal
          try {
            journal.remove(tokenId);
          } catch (IOException e) {
            log.failedToRemoveJournalEntry(tokenId, e);
          }
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

    try {
      journal.add(tokenId, issueTime, expiration, maxLifetimeDuration);
    } catch (IOException e) {
      log.failedToAddJournalEntry(tokenId, e);
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
    // Check the in-memory collection first, to avoid costly keystore access when possible
    try {
      // If the token identifier is valid, and the associated state is available from the in-memory cache, then
      // return the expiration from there.
      return super.getTokenExpiration(tokenId, validate);
    } catch (UnknownTokenException e) {
      // It's not in memory
    }

    if (validate) {
      validateToken(tokenId);
    }

    // If there is no associated state in the in-memory cache, proceed to check the alias service
    long expiration = 0;
    try {
      char[] expStr = aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, tokenId);
      if (expStr == null) {
        throw new UnknownTokenException(tokenId);
      }
      expiration = Long.parseLong(new String(expStr));
      // Update the in-memory cache to avoid subsequent keystore look-ups for the same state
      super.updateExpiration(tokenId, expiration);
    } catch (UnknownTokenException e) {
      throw e;
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

    // If any of the token IDs is represented among the unpersisted state, remove the associated state
    synchronized (unpersistedState) {
      List<TokenState> unpersistedToRemove = new ArrayList<>();
      for (TokenState state : unpersistedState) {
        if (tokenIds.contains(state.getTokenId())) {
          unpersistedToRemove.add(state);
        }
      }
      for (TokenState state : unpersistedToRemove) {
        unpersistedState.remove(state);
      }
    }

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

  interface TokenState {
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

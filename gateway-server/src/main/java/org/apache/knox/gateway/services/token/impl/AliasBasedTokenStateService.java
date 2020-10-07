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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.impl.DefaultKeystoreService;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.token.TokenStateServiceStatistics;
import org.apache.knox.gateway.services.token.impl.state.TokenStateJournalFactory;
import org.apache.knox.gateway.services.token.state.JournalEntry;
import org.apache.knox.gateway.services.token.state.TokenStateJournal;

/**
 * A TokenStateService implementation based on the AliasService.
 */
public class AliasBasedTokenStateService extends DefaultTokenStateService {

  static final String TOKEN_MAX_LIFETIME_POSTFIX = "--max";

  private AliasService aliasService;

  private long statePersistenceInterval = TimeUnit.SECONDS.toSeconds(15);

  private ScheduledExecutorService statePersistenceScheduler;

  private final List<TokenState> unpersistedState = new ArrayList<>();

  private final AtomicBoolean readyForEviction = new AtomicBoolean(false);

  private TokenStateJournal journal;

  private Path gatewayCredentialsFilePath;

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

    if (tokenStateServiceStatistics != null) {
      this.gatewayCredentialsFilePath = Paths.get(config.getGatewayKeystoreDir()).resolve(AliasService.NO_CLUSTER_NAME + DefaultKeystoreService.CREDENTIALS_SUFFIX + config.getCredentialStoreType().toLowerCase(Locale.ROOT));
      tokenStateServiceStatistics.setGatewayCredentialsFileSize(this.gatewayCredentialsFilePath.toFile().length());
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

    // Loading ALL entries from __gateway-credentials.jceks could be VERY time-consuming (it took a bit more than 19 minutes to load 12k aliases
    // during my tests).
    // Therefore, it's safer to do it in a background thread than just make the service start hang until it's finished
    final ExecutorService gatewayCredentialsLoader = Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder().namingPattern("GatewayCredentialsLoader").build());
    gatewayCredentialsLoader.execute(this::loadGatewayCredentialsOnStartup);
  }

  private void loadGatewayCredentialsOnStartup() {
    try {
      log.loadingGatewayCredentialsOnStartup();
      final long start = System.currentTimeMillis();
      final Map<String, char[]> passwordAliasMap = aliasService.getPasswordsForGateway();
      String alias, tokenId;
      long expiration, maxLifeTime;
      int count = 0;
      for (Map.Entry<String, char[]> passwordAliasMapEntry : passwordAliasMap.entrySet()) {
        alias = passwordAliasMapEntry.getKey();
        if (alias.endsWith(TOKEN_MAX_LIFETIME_POSTFIX)) {
          // This token state service implementation persists two aliases in __gateway-credentials.jceks (see persistTokenState below):
          // - an alias which maps a token ID to its expiration time
          // - another alias with '--max' postfix which maps the maximum lifetime of the token identified by the 1st alias
          // Given this, we should check aliases ending with '--max' and calculate the token ID from this alias.
          // If all aliases were blindly processed we would end-up handling aliases that were not persisted via this token state service
          // implementation -> facing error(s) when trying to parse the expiration/maxLifeTime values and irrelevant data would be loaded in the
          // in-memory collections in the parent class
          tokenId = alias.substring(0, alias.indexOf(TOKEN_MAX_LIFETIME_POSTFIX));
          expiration = convertCharArrayToLong(passwordAliasMap.get(tokenId));
          maxLifeTime = convertCharArrayToLong(passwordAliasMapEntry.getValue());
          super.updateExpiration(tokenId, expiration);
          super.setMaxLifetime(tokenId, maxLifeTime);
          count++;
        }
      }
      log.loadedGatewayCredentialsOnStartup(count * 2, System.currentTimeMillis() - start);  //count is multiplied by two: tokenId + tokenId--max
    } catch (AliasServiceException e) {
      log.errorWhileLoadingGatewayCredentialsOnStartup(e.getMessage(), e);
    } finally {
      readyForEviction.set(true);
    }
  }

  @Override
  protected boolean readyForEviction() {
    return readyForEviction.get();
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
        if (tokenStateServiceStatistics != null) {
          tokenStateServiceStatistics.interactKeystore(TokenStateServiceStatistics.KeystoreInteraction.SAVE_ALIAS);
          tokenStateServiceStatistics.setGatewayCredentialsFileSize(this.gatewayCredentialsFilePath.toFile().length());
        }
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
        char[] maxLifetimeStr = getPasswordUsingAliasService(tokenId + TOKEN_MAX_LIFETIME_POSTFIX);
        if (maxLifetimeStr != null) {
          result = convertCharArrayToLong(maxLifetimeStr);
        }
      } catch (AliasServiceException e) {
        log.errorAccessingTokenState(tokenId, e);
      }
    }
    return result;
  }

  private char[] getPasswordUsingAliasService(String tokenId) throws AliasServiceException {
    char[] password = aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, tokenId);
    if (tokenStateServiceStatistics != null) {
      tokenStateServiceStatistics.interactKeystore(TokenStateServiceStatistics.KeystoreInteraction.GET_PASSWORD);
    }
    return password;
  }

  private long convertCharArrayToLong(char[] charArray) {
    return Long.parseLong(new String(charArray));
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
      char[] expStr = getPasswordUsingAliasService(tokenId);
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
        isUnknown = (getPasswordUsingAliasService(tokenId) == null);
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
      unpersistedState.removeAll(unpersistedToRemove);
    }

    // Add the max lifetime aliases to the list of aliases to remove
    Set<String> aliasesToRemove = new HashSet<>(tokenIds);
    for (String tokenId : tokenIds) {
      aliasesToRemove.add(tokenId + TOKEN_MAX_LIFETIME_POSTFIX);
    }

    if (!aliasesToRemove.isEmpty()) {
      log.removingTokenStateAliases();
      try {
        aliasService.removeAliasesForCluster(AliasService.NO_CLUSTER_NAME, aliasesToRemove);
        if (tokenStateServiceStatistics != null) {
          tokenStateServiceStatistics.interactKeystore(TokenStateServiceStatistics.KeystoreInteraction.REMOVE_ALIAS);
          tokenStateServiceStatistics.setGatewayCredentialsFileSize(this.gatewayCredentialsFilePath.toFile().length());
        }
        log.removedTokenStateAliases(String.join(", ", tokenIds));
      } catch (AliasServiceException e) {
        log.failedToRemoveTokenStateAliases(e);
      }
    }

    super.removeTokens(tokenIds);
  }

  @Override
  protected void updateExpiration(final String tokenId, long expiration) {
    //Update in-memory
    super.updateExpiration(tokenId, expiration);

    //Update the in-memory representation of unpersisted states that will be processed by the state persistence thread
    synchronized (unpersistedState) {
      final Optional<TokenState> tokenStateToRemove = unpersistedState.stream().filter(tokenState -> tokenState.getTokenId().equals(tokenId)).findFirst();
      if (tokenStateToRemove.isPresent()) {
        unpersistedState.remove(tokenStateToRemove.get());
      }
      unpersistedState.add(new TokenExpiration(tokenId, expiration));
    }
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

    @Override
    public int hashCode() {
      return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
      return EqualsBuilder.reflectionEquals(this, obj);
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

    @Override
    public int hashCode() {
      return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
      return EqualsBuilder.reflectionEquals(this, obj);
    }
  }

}

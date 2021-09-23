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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.token.TokenStateServiceStatistics;
import org.apache.knox.gateway.services.token.impl.state.TokenStateJournalFactory;
import org.apache.knox.gateway.services.token.state.JournalEntry;
import org.apache.knox.gateway.services.token.state.TokenStateJournal;
import org.apache.knox.gateway.util.ExecutorServiceUtils;
import org.apache.knox.gateway.util.Tokens;

/**
 * A TokenStateService implementation based on the AliasService.
 */
public class AliasBasedTokenStateService extends DefaultTokenStateService implements TokenStatePeristerMonitorListener {

  static final String TOKEN_ALIAS_SUFFIX_DELIM   = "--";
  static final String TOKEN_ISSUE_TIME_POSTFIX   = TOKEN_ALIAS_SUFFIX_DELIM + "iss";
  static final String TOKEN_MAX_LIFETIME_POSTFIX = TOKEN_ALIAS_SUFFIX_DELIM + "max";
  static final String TOKEN_META_POSTFIX         = TOKEN_ALIAS_SUFFIX_DELIM + "meta";

  protected AliasService aliasService;

  protected long statePersistenceInterval = TimeUnit.SECONDS.toSeconds(15);

  private ScheduledExecutorService statePersistenceScheduler;

  private final Set<TokenState> unpersistedState = new HashSet<>();

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
          log.failedToLoadJournalEntry(Tokens.getTokenIDDisplayText(id), e);
        }
      }
    } catch (IOException e) {
      throw new ServiceLifecycleException("Failed to load persisted state from the token state journal", e);
    }

    statePersistenceInterval = config.getKnoxTokenStateAliasPersistenceInterval();

    if (tokenStateServiceStatistics != null) {
      this.gatewayCredentialsFilePath = Paths.get(config.getGatewayKeystoreDir()).resolve(AliasService.NO_CLUSTER_NAME + DefaultKeystoreService.CREDENTIALS_SUFFIX + config.getCredentialStoreType().toLowerCase(Locale.ROOT));
      tokenStateServiceStatistics.setGatewayCredentialsFileSize(this.gatewayCredentialsFilePath.toFile().length());
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
    super.start();
    if (statePersistenceInterval > 0) {
      //first schedule event; only happen if the feature is not disabled via persistence interval settings
      scheduleTokenStatePersistence();
    }

    // Loading ALL entries from __gateway-credentials.jceks could be VERY time-consuming (it took a bit more than 19 minutes to load 12k aliases
    // during my tests).
    // Therefore, it's safer to do it in a background thread than just make the service start hang until it's finished
    final ExecutorService gatewayCredentialsLoader = Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder().namingPattern("PersistenceStoreLoader").build());
    gatewayCredentialsLoader.execute(this::loadTokenAliasesFromPersistenceStore);
  }

  protected void loadTokenAliasesFromPersistenceStore() {
    try {
      log.loadingTokenAliasesFromPersistenceStore();
      final long start = System.currentTimeMillis();
      final Map<String, char[]> passwordAliasMap = aliasService.getPasswordsForGateway();
      String alias, tokenId;
      long expiration, maxLifeTime;
      int count = 0;
      for (Map.Entry<String, char[]> passwordAliasMapEntry : passwordAliasMap.entrySet()) {
        alias = passwordAliasMapEntry.getKey();
        if (alias.endsWith(TOKEN_MAX_LIFETIME_POSTFIX)) {
          // This token state service implementation persists 4 aliases in __gateway-credentials.jceks (see persistTokenState below):
          // - an alias which maps a token ID to its expiration time
          // - an alias with '--max' postfix which maps the maximum lifetime of the token identified by the 1st alias
          // - an alias with '--iss' postfix which maps the issue time of the token
          // - an alias with '-meta' postfix which maps an arbitrary metadata of the token
          // Given this, we should check aliases ending with '--max' and calculate the token ID from this alias.
          // If all aliases were blindly processed we would end-up handling aliases that were not persisted via this token state service
          // implementation -> facing error(s) when trying to parse the expiration/maxLifeTime values and irrelevant data would be loaded in the
          // in-memory collections in the parent class
          tokenId = alias.substring(0, alias.indexOf(TOKEN_MAX_LIFETIME_POSTFIX));
          expiration = convertCharArrayToLong(passwordAliasMap.get(tokenId));
          maxLifeTime = convertCharArrayToLong(passwordAliasMapEntry.getValue());
          super.updateExpiration(tokenId, expiration);
          super.setMaxLifetime(tokenId, maxLifeTime);
          count+=2;
        } else if (alias.endsWith(TOKEN_META_POSTFIX)) {
          tokenId = alias.substring(0, alias.indexOf(TOKEN_META_POSTFIX));
          super.addMetadata(tokenId, TokenMetadata.fromJSON(new String(passwordAliasMapEntry.getValue())));
        } else if (alias.endsWith(TOKEN_ISSUE_TIME_POSTFIX)) {
          tokenId = alias.substring(0, alias.indexOf(TOKEN_ISSUE_TIME_POSTFIX));
          setIssueTimeInMemory(tokenId, convertCharArrayToLong(passwordAliasMapEntry.getValue()));
        }

        // log some progress (it's very useful in case a huge amount of token related aliases in __gateway-credentials.jceks)
        if (count % 100 == 0) {
          log.loadedTokenAliasesFromPersistenceStore(count, System.currentTimeMillis() - start);
        }
      }
      log.loadedTokenAliasesFromPersistenceStore(count * 2, System.currentTimeMillis() - start);  //count is multiplied by two: tokenId + tokenId--max
    } catch (AliasServiceException e) {
      log.errorWhileLoadingTokenAliasesFromPersistenceStore(e.getMessage(), e);
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

  private void scheduleTokenStatePersistence() {
    if (statePersistenceScheduler != null) {
      ExecutorServiceUtils.shutdownAndAwaitTermination(statePersistenceScheduler, 10, TimeUnit.SECONDS);
    }
    statePersistenceScheduler = Executors.newSingleThreadScheduledExecutor(new BasicThreadFactory.Builder().namingPattern("TokenStatePerister-%d").build());
    final ScheduledFuture<?> persistTokenStateTask = statePersistenceScheduler.scheduleAtFixedRate(this::persistTokenState, statePersistenceInterval, statePersistenceInterval, TimeUnit.SECONDS);
    log.runningTokenStateAliasePersisterTask(statePersistenceInterval, TimeUnit.SECONDS.toString());
    final TokenStatePersisterMonitor taskMonitor = new TokenStatePersisterMonitor(persistTokenStateTask, this);
    taskMonitor.startMonitor();
  }

  @Override
  public void onTokenStatePeristerTaskError(Throwable error) {
    scheduleTokenStatePersistence();
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
      log.creatingTokenStateAliases(Tokens.getTokenIDDisplayText(tokenId));
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
          log.createdTokenStateAliases(Tokens.getTokenIDDisplayText(tokenId));
          // After the aliases have been successfully persisted, remove their associated state from the journal
          try {
            journal.remove(tokenId);
          } catch (IOException e) {
            log.failedToRemoveJournalEntry(Tokens.getTokenIDDisplayText(tokenId), e);
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
      journal.add(tokenId, issueTime, expiration, maxLifetimeDuration, null);
    } catch (IOException e) {
      log.failedToAddJournalEntry(Tokens.getTokenIDDisplayText(tokenId), e);
    }
  }

  @Override
  protected void setIssueTime(String tokenId, long issueTime) {
    synchronized (unpersistedState) {
      unpersistedState.add(new TokenIssueTime(tokenId, issueTime));
    }
    setIssueTimeInMemory(tokenId, issueTime);
  }

  protected void setIssueTimeInMemory(String tokenId, long issueTime) {
    super.setIssueTime(tokenId, issueTime);
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
        log.errorAccessingTokenState(Tokens.getTokenIDDisplayText(tokenId), e);
      }
    }
    return result;
  }

  protected char[] getPasswordUsingAliasService(String alias) throws AliasServiceException {
    char[] password = aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, alias);
    if (tokenStateServiceStatistics != null) {
      tokenStateServiceStatistics.interactKeystore(TokenStateServiceStatistics.KeystoreInteraction.GET_PASSWORD);
    }
    return password;
  }

  protected long convertCharArrayToLong(char[] charArray) {
    return Long.parseLong(new String(charArray));
  }

  @Override
  public long getTokenIssueTime(String tokenId) throws UnknownTokenException {
    // Check the in-memory collection first, to avoid costly keystore access when possible
    try {
      // check the in-memory cache first
      return super.getTokenIssueTime(tokenId);
    } catch (UnknownTokenException e) {
      // It's not in memory
    }

    // If there is no associated state in the in-memory cache, proceed to check the alias service
    long issueTime = 0;
    try {
      char[] issueTimeStr = getPasswordUsingAliasService(tokenId + TOKEN_ISSUE_TIME_POSTFIX);
      if (issueTimeStr == null) {
        throw new UnknownTokenException(tokenId);
      }
      issueTime = convertCharArrayToLong(issueTimeStr);
      // Update the in-memory cache to avoid subsequent keystore look-ups for the same state
      setIssueTimeInMemory(tokenId, issueTime);
    } catch (UnknownTokenException e) {
      throw e;
    } catch (Exception e) {
      log.errorAccessingTokenState(Tokens.getTokenIDDisplayText(tokenId), e);
    }

    return issueTime;
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
      log.errorAccessingTokenState(Tokens.getTokenIDDisplayText(tokenId), e);
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
        log.errorAccessingTokenState(Tokens.getTokenIDDisplayText(tokenId), e);
      }
    }
    return isUnknown;
  }

  @Override
  protected void removeTokens(Set<String> tokenIds) {

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

    // Add the max lifetime, metadata and issue time aliases to the list of aliases to remove
    Set<String> aliasesToRemove = new HashSet<>(tokenIds);
    for (String tokenId : tokenIds) {
      aliasesToRemove.add(tokenId + TOKEN_MAX_LIFETIME_POSTFIX);
      aliasesToRemove.add(tokenId + TOKEN_META_POSTFIX);
      aliasesToRemove.add(tokenId + TOKEN_ISSUE_TIME_POSTFIX);
    }

    if (!aliasesToRemove.isEmpty()) {
      log.removingTokenStateAliases();
      try {
        aliasService.removeAliasesForCluster(AliasService.NO_CLUSTER_NAME, aliasesToRemove);
        if (tokenStateServiceStatistics != null) {
          tokenStateServiceStatistics.interactKeystore(TokenStateServiceStatistics.KeystoreInteraction.REMOVE_ALIAS);
          tokenStateServiceStatistics.setGatewayCredentialsFileSize(this.gatewayCredentialsFilePath.toFile().length());
        }
        log.removedTokenStateAliases(String.join(", ", Tokens.getDisplayableTokenIDsText(tokenIds)));
      } catch (AliasServiceException e) {
        log.failedToRemoveTokenStateAliases(e);
      }
    }

    removeTokensFromMemory(tokenIds);
  }

  protected void removeTokensFromMemory(Set<String> tokenIds) {
    super.removeTokens(tokenIds);
  }

  @Override
  protected void updateExpiration(final String tokenId, long expiration) {
    //Update in-memory
    updateExpirationInMemory(tokenId, expiration);

    //Update the in-memory representation of unpersisted states that will be processed by the state persistence thread
    synchronized (unpersistedState) {
      unpersistedState.add(new TokenExpiration(tokenId, expiration));
    }
  }

  protected void updateExpirationInMemory(final String tokenId, long expiration) {
    super.updateExpiration(tokenId, expiration);
  }

  @Override
  public void addMetadata(String tokenId, TokenMetadata metadata) {
    addMetadataInMemory(tokenId, metadata);
    try {
      final JournalEntry entry = journal.get(tokenId);
      if (entry != null) {
        journal.add(entry.getTokenId(), Long.parseLong(entry.getIssueTime()), Long.parseLong(entry.getExpiration()), Long.parseLong(entry.getMaxLifetime()), metadata);
      }
    } catch (IOException e) {
      log.failedToAddJournalEntry(Tokens.getTokenIDDisplayText(tokenId), e);
    }

    synchronized (unpersistedState) {
      unpersistedState.add(new TokenMetadataState(tokenId, metadata));
    }
  }

  protected void addMetadataInMemory(String tokenId, TokenMetadata metadata) {
    super.addMetadata(tokenId, metadata);
  }

  @Override
  public TokenMetadata getTokenMetadata(String tokenId) throws UnknownTokenException {
    TokenMetadata tokenMetadata = null;
    try {
      tokenMetadata = super.getTokenMetadata(tokenId);
    } catch (UnknownTokenException e) {
      // This is expected if the metadata is not yet part of the in-memory record. In this case, the metadata will
      // be retrieved from the alias store.
    }

    if (tokenMetadata == null) {
      try {
        final char[] tokenMetadataAliasValue = getPasswordUsingAliasService(tokenId + TOKEN_META_POSTFIX);
        if (tokenMetadataAliasValue != null) {
          tokenMetadata = TokenMetadata.fromJSON(new String(tokenMetadataAliasValue));
        } else {
          throw new UnknownTokenException(tokenId);
        }
      } catch (AliasServiceException e) {
        log.errorAccessingTokenState(Tokens.getTokenIDDisplayText(tokenId), e);
      }
    }
    return tokenMetadata;
  }

  enum TokenStateType {
    EXP(1), MAX(2), META(3), ISS(4);

    private final int id;

    TokenStateType(int id) {
      this.id = id;
    }
  }

  interface TokenState {
    String getTokenId();
    String getAlias();
    String getAliasValue();
    TokenStateType getType();
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
    public TokenStateType getType() {
      return TokenStateType.MAX;
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder().append(tokenId).append(getType().id).toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (obj == this) {
        return true;
      }
      if (obj.getClass() != getClass()) {
        return false;
      }
      final TokenMaxLifetime rhs = (TokenMaxLifetime) obj;
      return new EqualsBuilder().append(this.tokenId, rhs.tokenId).append(this.getType().id, rhs.getType().id).isEquals();
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
    public TokenStateType getType() {
      return TokenStateType.EXP;
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder().append(tokenId).append(getType().id).toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (obj == this) {
        return true;
      }
      if (obj.getClass() != getClass()) {
        return false;
      }
      final TokenExpiration rhs = (TokenExpiration) obj;
      return new EqualsBuilder().append(this.tokenId, rhs.tokenId).append(this.getType().id, rhs.getType().id).isEquals();
    }
  }

  private static final class TokenIssueTime implements TokenState {
    private String tokenId;
    private long   issueTime;

    TokenIssueTime(String tokenId, long issueTime) {
      this.tokenId    = tokenId;
      this.issueTime = issueTime;
    }

    @Override
    public String getTokenId() {
      return tokenId;
    }

    @Override
    public String getAlias() {
      return tokenId + TOKEN_ISSUE_TIME_POSTFIX;
    }

    @Override
    public String getAliasValue() {
      return String.valueOf(issueTime);
    }

    @Override
    public TokenStateType getType() {
      return TokenStateType.ISS;
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder().append(tokenId).append(getType().id).toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (obj == this) {
        return true;
      }
      if (obj.getClass() != getClass()) {
        return false;
      }
      final TokenIssueTime rhs = (TokenIssueTime) obj;
      return new EqualsBuilder().append(this.tokenId, rhs.tokenId).append(this.getType().id, rhs.getType().id).isEquals();
    }
  }

  private static final class TokenMetadataState implements TokenState {

    private final String tokenId;
    private final TokenMetadata metadata;

    TokenMetadataState(String tokenId, TokenMetadata metadata) {
      this.tokenId = tokenId;
      this.metadata = metadata;
    }

    @Override
    public String getTokenId() {
      return tokenId;
    }

    @Override
    public String getAlias() {
      return tokenId + TOKEN_META_POSTFIX;
    }

    @Override
    public String getAliasValue() {
      return metadata.toJSON();
    }

    @Override
    public TokenStateType getType() {
      return TokenStateType.META;
    }
  }

}

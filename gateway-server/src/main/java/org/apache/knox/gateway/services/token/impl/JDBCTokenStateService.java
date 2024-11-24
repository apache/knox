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
package org.apache.knox.gateway.services.token.impl;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.token.KnoxToken;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenMigrationTarget;
import org.apache.knox.gateway.services.security.token.TokenStateServiceException;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.util.JDBCUtils;
import org.apache.knox.gateway.util.TokenMigrationTool;
import org.apache.knox.gateway.util.Tokens;

public class JDBCTokenStateService extends AbstractPersistentTokenStateService implements TokenMigrationTarget {
  private AliasService aliasService; // connection username/pw and passcode HMAC secret are stored here
  private TokenStateDatabase tokenDatabase;
  private AtomicBoolean initialized = new AtomicBoolean(false);
  private Lock initLock = new ReentrantLock(true);
  private Lock addMetadataLock = new ReentrantLock(true);

  private boolean skipTokenMigration;
  private boolean archiveMigratedTokens;
  private boolean migrateExpiredTokens;
  private boolean verboseTokenMigration;
  private int tokenMigrationProgressCount;

  public void setAliasService(AliasService aliasService) {
    this.aliasService = aliasService;
  }

  protected AliasService getAliasService() {
    return aliasService;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    if (!initialized.get()) {
      initLock.lock();
      try {
        super.init(config, options);
        if (aliasService == null) {
          throw new ServiceLifecycleException("The required AliasService reference has not been set.");
        }
        try {
          this.tokenDatabase = new TokenStateDatabase(JDBCUtils.getDataSource(config, aliasService));
          initialized.set(true);
        } catch (Exception e) {
          throw new ServiceLifecycleException("Error while initiating JDBCTokenStateService: " + e, e);
        }

        this.skipTokenMigration = config.skipTokenMigration();
        this.archiveMigratedTokens = config.archiveMigratedTokens();
        this.migrateExpiredTokens = config.migrateExpiredTokens();
        this.verboseTokenMigration = config.printVerboseTokenMigrationMessages();
        this.tokenMigrationProgressCount = config.getTokenMigrationProgressCount();
      } finally {
        initLock.unlock();
      }
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
    super.start();
    if (skipTokenMigration) {
      log.skipTokenMigration();
    } else {
      final TokenMigrationTool tokenMigrationTool = new TokenMigrationTool(aliasService, this, null);
      tokenMigrationTool.setArchiveMigratedTokens(archiveMigratedTokens);
      tokenMigrationTool.setProgressCount(tokenMigrationProgressCount);
      tokenMigrationTool.setVerbose(verboseTokenMigration);
      tokenMigrationTool.setMigrateExpiredTokens(migrateExpiredTokens);
      tokenMigrationTool.migrateTokensFromGatewayCredentialStore();
    }
  }

  @Override
  public void addToken(String tokenId, long issueTime, long expiration, long maxLifetimeDuration) {
    try {
      final boolean added = tokenDatabase.addToken(tokenId, issueTime, expiration, maxLifetimeDuration);
      if (added) {
        log.savedTokenInDatabase(Tokens.getTokenIDDisplayText(tokenId));

        // add in-memory
        super.addToken(tokenId, issueTime, expiration, maxLifetimeDuration);
      } else {
        log.failedToSaveTokenInDatabase(Tokens.getTokenIDDisplayText(tokenId));
        throw new TokenStateServiceException("Failed to save token " + Tokens.getTokenIDDisplayText(tokenId) + " in the database");
      }
    } catch (SQLException e) {
      log.errorSavingTokenInDatabase(Tokens.getTokenIDDisplayText(tokenId), e.getMessage(), e);
      throw new TokenStateServiceException("An error occurred while saving token " + Tokens.getTokenIDDisplayText(tokenId) + " in the database", e);
    }
  }

  @Override
  public long getTokenIssueTime(String tokenId) throws UnknownTokenException {
    try {
      // check the in-memory cache first
      return super.getTokenIssueTime(tokenId);
    } catch (UnknownTokenException e) {
      // It's not in memory
    }

    long issueTime = 0;
    try {
      issueTime = tokenDatabase.getTokenIssueTime(tokenId);
      if (issueTime > 0) {
        log.fetchedIssueTimeFromDatabase(Tokens.getTokenIDDisplayText(tokenId), issueTime);

        // Update the in-memory cache to avoid subsequent DB look-ups for the same state
        super.setIssueTime(tokenId, issueTime);
      } else {
        throw new UnknownTokenException(tokenId);
      }
    } catch (SQLException e) {
      log.errorFetchingIssueTimeFromDatabase(Tokens.getTokenIDDisplayText(tokenId), e.getMessage(), e);
    }
    return issueTime;
  }

  @Override
  public long getTokenExpiration(String tokenId, boolean validate) throws UnknownTokenException {
    // To support HA, there is no in-memory lookup here; we should go directly to the DB
    // See KNOX-2658 for more details.

    if (validate) {
      validateToken(tokenId);
    }

    try {
      final Long expiration = tokenDatabase.getTokenExpiration(tokenId);
      if (expiration != null) {
        log.fetchedExpirationFromDatabase(Tokens.getTokenIDDisplayText(tokenId), expiration);

        // Update the in-memory cache to avoid subsequent DB look-ups for the same state
        super.updateExpiration(tokenId, expiration);
        return expiration;
      } else {
        throw new UnknownTokenException(tokenId);
      }
    } catch (SQLException e) {
      log.errorFetchingExpirationFromDatabase(Tokens.getTokenIDDisplayText(tokenId), e.getMessage(), e);
      throw new TokenStateServiceException("An error occurred while fetching expiration for " + Tokens.getTokenIDDisplayText(tokenId) + " from the database", e);
    }
  }

  @Override
  protected void updateExpiration(String tokenId, long expiration) {
    try {
      final boolean updated = tokenDatabase.updateExpiration(tokenId, expiration);
      if (updated) {
        log.updatedExpirationInDatabase(Tokens.getTokenIDDisplayText(tokenId), expiration);

        // Update in-memory
        super.updateExpiration(tokenId, expiration);
      } else {
        log.failedToUpdateExpirationInDatabase(Tokens.getTokenIDDisplayText(tokenId), expiration);
        throw new TokenStateServiceException("Failed to updated expiration for " + Tokens.getTokenIDDisplayText(tokenId) + " in the database");
      }
    } catch (SQLException e) {
      log.errorUpdatingExpirationInDatabase(Tokens.getTokenIDDisplayText(tokenId), e.getMessage(), e);
      throw new TokenStateServiceException("An error occurred while updating expiration for " + Tokens.getTokenIDDisplayText(tokenId) + " in the database", e);
    }
  }

  @Override
  protected long getMaxLifetime(String tokenId) {
    long maxLifetime = super.getMaxLifetime(tokenId);  // returns 0, if not found in memory

    // If there is no result from the in-memory collection, proceed to check the Database
    if (maxLifetime == 0L) {
      try {
        maxLifetime = tokenDatabase.getMaxLifetime(tokenId);
        log.fetchedMaxLifetimeFromDatabase(Tokens.getTokenIDDisplayText(tokenId), maxLifetime);
      } catch (SQLException e) {
        log.errorFetchingMaxLifetimeFromDatabase(Tokens.getTokenIDDisplayText(tokenId), e.getMessage(), e);
      }
    }
    return maxLifetime;
  }

  @Override
  protected boolean isUnknown(String tokenId) {
    boolean isUnknown = super.isUnknown(tokenId);

    // If it's not in the cache, then check in the Database
    if (isUnknown) {
      try {
        isUnknown = tokenDatabase.getMaxLifetime(tokenId) < 0;
      } catch (SQLException e) {
        log.errorFetchingMaxLifetimeFromDatabase(Tokens.getTokenIDDisplayText(tokenId), e.getMessage(), e);
      }
    }
    return isUnknown;
  }

  @Override
  protected void removeToken(String tokenId) throws UnknownTokenException {
    try {
      final boolean removed = tokenDatabase.removeToken(tokenId);
      if (removed) {
        super.removeTokens(Collections.singleton(tokenId));
        log.removedTokenFromDatabase(Tokens.getTokenIDDisplayText(tokenId));
      } else {
        throw new UnknownTokenException(tokenId);
      }
    } catch (SQLException e) {
      log.errorRemovingTokenFromDatabase(Tokens.getTokenIDDisplayText(tokenId), e.getMessage(), e);
    }
  }

  @Override
  protected void evictExpiredTokens() {
    try {
      final long expirationLimit = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(tokenEvictionGracePeriod);
      final Set<String> expiredTokenIds = tokenDatabase.getExpiredTokenIds(expirationLimit);
      if (!expiredTokenIds.isEmpty()) {
        log.removingExpiredTokensFromDatabase(expiredTokenIds.size(),
            String.join(", ", expiredTokenIds.stream().map(tokenId -> Tokens.getTokenIDDisplayText(tokenId)).collect(Collectors.toSet())));
        final int numOfExpiredTokens = tokenDatabase.deleteExpiredTokens(expirationLimit);
        log.removedTokensFromDatabase(numOfExpiredTokens);

        // remove from in-memory collections
        super.removeTokens(expiredTokenIds);
      }
    } catch (SQLException e) {
      log.errorRemovingTokensFromDatabase(e.getMessage(), e);
    }
  }

  @Override
  public void addMetadata(String tokenId, TokenMetadata metadata) {
    try {
      boolean added = saveMetadataMapInDatabase(tokenId, metadata.getMetadataMap());

      if (added) {
        log.updatedMetadataInDatabase(Tokens.getTokenIDDisplayText(tokenId));

        // Update in-memory
        super.addMetadata(tokenId, metadata);
      } else {
        log.failedToUpdateMetadataInDatabase(Tokens.getTokenIDDisplayText(tokenId));
        throw new TokenStateServiceException("Failed to update metadata for " + Tokens.getTokenIDDisplayText(tokenId) + " in the database");
      }
    } catch (SQLException e) {
      log.errorUpdatingMetadataInDatabase(Tokens.getTokenIDDisplayText(tokenId), e.getMessage(), e);
      throw new TokenStateServiceException("An error occurred while updating metadata for " + Tokens.getTokenIDDisplayText(tokenId) + " in the database", e);
    }
  }

  private boolean saveMetadataMapInDatabase(String tokenId, Map<String, String> metadataMap) throws SQLException {
    addMetadataLock.lock();
    try {
      boolean saved = false;
      for (Map.Entry<String, String> metadataMapEntry : metadataMap.entrySet()) {
        if (StringUtils.isNotBlank(metadataMapEntry.getValue())) {
          if (upsertTokenMetadata(tokenId, metadataMapEntry.getKey(), metadataMapEntry.getValue())) {
            saved = true;
          }
        }
      }
      return saved;
    } finally {
      addMetadataLock.unlock();
    }
  }

  private boolean upsertTokenMetadata(String tokenId, String metadataName, String metadataValue) throws SQLException {
    if (!tokenDatabase.updateMetadata(tokenId, metadataName, metadataValue)) {
      return tokenDatabase.addMetadata(tokenId, metadataName, metadataValue);
    } else {
      return true; //successfully updated
    }
  }

  @Override
  public TokenMetadata getTokenMetadata(String tokenId) throws UnknownTokenException {
    // To support HA, there is no in-memory lookup here; we should go directly to the DB.
    // See KNOX-2658 for more details.

    TokenMetadata tokenMetadata = null;

    try {
      tokenMetadata = tokenDatabase.getTokenMetadata(tokenId);

      if (tokenMetadata != null) {
        log.fetchedMetadataFromDatabase(Tokens.getTokenIDDisplayText(tokenId));
        // Update the in-memory cache to avoid subsequent DB look-ups for the same state
        super.addMetadata(tokenId, tokenMetadata);
      } else {
        throw new UnknownTokenException(tokenId);
      }
    } catch (SQLException e) {
      log.errorFetchingMetadataFromDatabase(Tokens.getTokenIDDisplayText(tokenId), e.getMessage(), e);
    }
    return tokenMetadata;
  }

  @Override
  public Collection<KnoxToken> getAllTokens() {
    try {
      return tokenDatabase.getAllTokens();
    } catch (SQLException e) {
      log.errorFetchingAllTokensFromDatabase(e.getMessage(), e);
      return Collections.emptyList();
    }
  }

  @Override
  public Collection<KnoxToken> getTokens(String userName) {
    try {
      return tokenDatabase.getTokens(userName);
    } catch (SQLException e) {
      log.errorFetchingTokensForUserFromDatabase(userName, e.getMessage(), e);
      return Collections.emptyList();
    }
  }

  @Override
  public Collection<KnoxToken> getDoAsTokens(String createdBy) {
    try {
      return tokenDatabase.getDoAsTokens(createdBy);
    } catch (SQLException e) {
      log.errorFetchingDoAsTokensForUserFromDatabase(createdBy, e.getMessage(), e);
      return Collections.emptyList();
    }
  }
}

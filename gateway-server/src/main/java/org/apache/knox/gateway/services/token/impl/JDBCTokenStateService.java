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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.util.JDBCUtils;
import org.apache.knox.gateway.util.Tokens;

public class JDBCTokenStateService extends DefaultTokenStateService {
  private AliasService aliasService; // connection username/pw is stored here
  private TokenStateDatabase tokenDatabase;

  public void setAliasService(AliasService aliasService) {
    this.aliasService = aliasService;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    super.init(config, options);
    if (aliasService == null) {
      throw new ServiceLifecycleException("The required AliasService reference has not been set.");
    }
    try {
      this.tokenDatabase = new TokenStateDatabase(JDBCUtils.getDataSource(config, aliasService));
    } catch (Exception e) {
      throw new ServiceLifecycleException("Error while initiating JDBCTokenStateService: " + e, e);
    }
  }

  @Override
  public void addToken(String tokenId, long issueTime, long expiration, long maxLifetimeDuration) {
    super.addToken(tokenId, issueTime, expiration, maxLifetimeDuration);
    try {
      final boolean added = tokenDatabase.addToken(tokenId, issueTime, expiration, maxLifetimeDuration);
      if (added) {
        log.savedTokenInDatabase(Tokens.getTokenIDDisplayText(tokenId));
      } else {
        log.failedToSaveTokenInDatabase(Tokens.getTokenIDDisplayText(tokenId));
      }
    } catch (SQLException e) {
      log.errorSavingTokenInDatabase(Tokens.getTokenIDDisplayText(tokenId), e.getMessage(), e);
    }
  }

  @Override
  public long getTokenExpiration(String tokenId, boolean validate) throws UnknownTokenException {
    try {
      // check the in-memory cache, then
      return super.getTokenExpiration(tokenId, validate);
    } catch (UnknownTokenException e) {
      // It's not in memory
    }

    long expiration = 0;
    try {
      expiration = tokenDatabase.getTokenExpiration(tokenId);
      log.fetchedExpirationFromDatabase(Tokens.getTokenIDDisplayText(tokenId), expiration);
    } catch (SQLException e) {
      log.errorFetchingExpirationFromDatabase(Tokens.getTokenIDDisplayText(tokenId), e.getMessage(), e);
    }
    return expiration;
  }

  @Override
  protected void updateExpiration(String tokenId, long expiration) {
    // Update in-memory
    super.updateExpiration(tokenId, expiration);

    try {
      final boolean updated = tokenDatabase.updateExpiration(tokenId, expiration);
      if (updated) {
        log.updatedExpirationInDatabase(Tokens.getTokenIDDisplayText(tokenId), expiration);
      } else {
        log.failedToUpdateExpirationInDatabase(Tokens.getTokenIDDisplayText(tokenId), expiration);
      }
    } catch (SQLException e) {
      log.errorUpdatingExpirationInDatabase(Tokens.getTokenIDDisplayText(tokenId), e.getMessage(), e);
    }
  }

  @Override
  protected long getMaxLifetime(String tokenId) {
    long maxLifetime = super.getMaxLifetime(tokenId);

    // If there is no result from the in-memory collection, proceed to check the Database
    if (maxLifetime < 1L) {
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
  protected void evictExpiredTokens() {
    try {
      int numOfExpiredTokens = tokenDatabase.deleteExpiredTokens(TimeUnit.SECONDS.toMillis(tokenEvictionGracePeriod));
      log.removedTokensFromDatabase(numOfExpiredTokens);
    } catch (SQLException e) {
      log.errorRemovingTokensFromDatabase(e.getMessage(), e);
    }

    //remove from in-memory collections
    super.evictExpiredTokens();
  }

  @Override
  public void addMetadata(String tokenId, TokenMetadata metadata) {
    // Update in-memory
    super.addMetadata(tokenId, metadata);

    try {
      final boolean added = tokenDatabase.addMetadata(tokenId, metadata);
      if (added) {
        log.updatedMetadataInDatabase(Tokens.getTokenIDDisplayText(tokenId));
      } else {
        log.failedToUpdateMetadataInDatabase(Tokens.getTokenIDDisplayText(tokenId));
      }
    } catch (SQLException e) {
      log.errorUpdatingMetadataInDatabase(Tokens.getTokenIDDisplayText(tokenId), e.getMessage(), e);
    }
  }
}

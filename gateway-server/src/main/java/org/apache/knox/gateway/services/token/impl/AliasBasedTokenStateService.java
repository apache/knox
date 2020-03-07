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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A TokenStateService implementation based on the AliasService.
 */
public class AliasBasedTokenStateService extends DefaultTokenStateService {

  private AliasService aliasService;
  private static final String TOKEN_MAX_LIFETIME_POSTFIX = "--max";

  public void setAliasService(AliasService aliasService) {
    this.aliasService = aliasService;
  }

  @Override
  public void init(final GatewayConfig config, final Map<String, String> options) throws ServiceLifecycleException {
    super.init(config, options);
    if (aliasService == null) {
      throw new ServiceLifecycleException("The required AliasService reference has not been set.");
    }
  }

  @Override
  public void addToken(final String tokenId,
                             long   issueTime,
                             long   expiration,
                             long   maxLifetimeDuration) {
    isValidIdentifier(tokenId);
    try {
      aliasService.addAliasForCluster(AliasService.NO_CLUSTER_NAME, tokenId, String.valueOf(expiration));
      setMaxLifetime(tokenId, issueTime, maxLifetimeDuration);
      log.addedToken(tokenId, getTimestampDisplay(expiration));
    } catch (AliasServiceException e) {
      log.failedToSaveTokenState(tokenId, e);
    }
  }

  @Override
  protected void setMaxLifetime(final String tokenId, long issueTime, long maxLifetimeDuration) {
    try {
      aliasService.addAliasForCluster(AliasService.NO_CLUSTER_NAME,
                                      tokenId + TOKEN_MAX_LIFETIME_POSTFIX,
                                      String.valueOf(issueTime + maxLifetimeDuration));
    } catch (AliasServiceException e) {
      log.failedToSaveTokenState(tokenId, e);
    }
  }

  @Override
  protected long getMaxLifetime(final String tokenId) {
    long result = 0;
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
    return result;
  }

  @Override
  public long getTokenExpiration(final String tokenId) throws UnknownTokenException {
    long expiration = 0;

    validateToken(tokenId);

    try {
      char[] expStr = aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, tokenId);
      if (expStr != null) {
        expiration = Long.parseLong(new String(expStr));
      }
    } catch (Exception e) {
      log.errorAccessingTokenState(tokenId, e);
    }

    return expiration;
  }

  @Override
  protected boolean isUnknown(final String tokenId) {
    boolean isUnknown = false;
    try {
      isUnknown = (aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, tokenId) == null);
    } catch (AliasServiceException e) {
      log.errorAccessingTokenState(tokenId, e);
    }
    return isUnknown;
  }

  @Override
  protected void removeToken(final String tokenId) throws UnknownTokenException {
    validateToken(tokenId);

    try {
      aliasService.removeAliasForCluster(AliasService.NO_CLUSTER_NAME, tokenId);
      aliasService.removeAliasForCluster(AliasService.NO_CLUSTER_NAME, tokenId + TOKEN_MAX_LIFETIME_POSTFIX);
      log.removedTokenState(tokenId);
    } catch (AliasServiceException e) {
      log.failedToRemoveTokenState(tokenId, e);
    }
  }

  @Override
  protected void updateExpiration(final String tokenId, long expiration) {
    try {
      aliasService.removeAliasForCluster(AliasService.NO_CLUSTER_NAME, tokenId);
      aliasService.addAliasForCluster(AliasService.NO_CLUSTER_NAME, tokenId, String.valueOf(expiration));
    } catch (AliasServiceException e) {
      log.failedToUpdateTokenExpiration(tokenId, e);
    }
  }

  @Override
  protected List<String> getTokens() {
    List<String> allAliases = new ArrayList<>();
    try {
      allAliases = aliasService.getAliasesForCluster(AliasService.NO_CLUSTER_NAME);
      /* only get the aliases that represent tokens and extract the current list of tokens */
      allAliases = allAliases.stream().filter(a -> a.contains(TOKEN_MAX_LIFETIME_POSTFIX)).map(a -> a.substring(0, a.indexOf(TOKEN_MAX_LIFETIME_POSTFIX)))
          .collect(Collectors.toList());
    } catch (AliasServiceException e) {
      log.errorEvictingTokens(e);
    }
    return allAliases;
  }
}

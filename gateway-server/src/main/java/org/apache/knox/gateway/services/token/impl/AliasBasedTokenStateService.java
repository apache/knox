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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.TokenUtils;
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
  public void addToken(final String token,
                       long         issueTime,
                       long         expiration,
                       long         maxLifetimeDuration) {
    isValidIdentifier(token);
    try {
      aliasService.addAliasForCluster(AliasService.NO_CLUSTER_NAME, token, String.valueOf(expiration));
      setMaxLifetime(token, issueTime, maxLifetimeDuration);
      log.addedToken(TokenUtils.getTokenDisplayText(token), getTimestampDisplay(expiration));
    } catch (AliasServiceException e) {
      log.failedToSaveTokenState(TokenUtils.getTokenDisplayText(token), e);
    }
  }

  @Override
  protected void setMaxLifetime(final String token, long issueTime, long maxLifetimeDuration) {
    try {
      aliasService.addAliasForCluster(AliasService.NO_CLUSTER_NAME,
                                      token + TOKEN_MAX_LIFETIME_POSTFIX,
                                      String.valueOf(issueTime + maxLifetimeDuration));
    } catch (AliasServiceException e) {
      log.failedToSaveTokenState(TokenUtils.getTokenDisplayText(token), e);
    }
  }

  @Override
  protected long getMaxLifetime(final String token) {
    long result = 0;
    try {
      char[] maxLifetimeStr =
                      aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, token + TOKEN_MAX_LIFETIME_POSTFIX);
      if (maxLifetimeStr != null) {
        result = Long.parseLong(new String(maxLifetimeStr));
      }
    } catch (AliasServiceException e) {
      log.errorAccessingTokenState(TokenUtils.getTokenDisplayText(token), e);
    }
    return result;
  }

  @Override
  public long getTokenExpiration(final String token) throws UnknownTokenException {
    long expiration = 0;
    try {
      validateToken(token);
    } catch (final UnknownTokenException e) {
      /* if token permissiveness is enabled we check JWT token expiration when the token state is unknown */
      if (permissiveFailureEnabled && StringUtils
          .containsIgnoreCase(e.toString(), "Unknown token")) {
        return getJWTTokenExpiration(token);
      } else {
        throw e;
      }
    }
    try {
      char[] expStr = aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, token);
      if (expStr != null) {
        expiration = Long.parseLong(new String(expStr));
      }
    } catch (Exception e) {
      log.errorAccessingTokenState(TokenUtils.getTokenDisplayText(token), e);
    }

    return expiration;
  }

  @Override
  public void revokeToken(final String token) throws UnknownTokenException {
    /* no reason to keep revoked tokens around */
    removeToken(token);
    log.revokedToken(TokenUtils.getTokenDisplayText(token));
  }

  @Override
  protected boolean isUnknown(final String token) {
    boolean isUnknown = false;
    try {
      isUnknown = (aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, token) == null);
    } catch (AliasServiceException e) {
      log.errorAccessingTokenState(TokenUtils.getTokenDisplayText(token), e);
    }
    return isUnknown;
  }

  @Override
  protected void removeToken(final String token) throws UnknownTokenException {
    validateToken(token);

    try {
      aliasService.removeAliasForCluster(AliasService.NO_CLUSTER_NAME, token);
      aliasService.removeAliasForCluster(AliasService.NO_CLUSTER_NAME,token + TOKEN_MAX_LIFETIME_POSTFIX);
      log.removedTokenState(TokenUtils.getTokenDisplayText(token));
    } catch (AliasServiceException e) {
      log.failedToRemoveTokenState(TokenUtils.getTokenDisplayText(token), e);
    }
  }

  @Override
  protected void updateExpiration(final String token, long expiration) {
    if (isUnknown(token)) {
      log.unknownToken(TokenUtils.getTokenDisplayText(token));
      throw new IllegalArgumentException("Unknown token.");
    }

    try {
      aliasService.removeAliasForCluster(AliasService.NO_CLUSTER_NAME, token);
      aliasService.addAliasForCluster(AliasService.NO_CLUSTER_NAME, token, String.valueOf(expiration));
    } catch (AliasServiceException e) {
      log.failedToUpdateTokenExpiration(TokenUtils.getTokenDisplayText(token), e);
    }
  }

  @Override
  protected List<String> getTokens() {
    List<String> allAliases = new ArrayList();
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

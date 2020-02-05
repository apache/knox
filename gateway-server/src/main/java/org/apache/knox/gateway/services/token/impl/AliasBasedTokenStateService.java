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

import java.util.Map;

/**
 * A TokenStateService implementation based on the AliasService.
 */
public class AliasBasedTokenStateService extends DefaultTokenStateService {

  private AliasService aliasService;

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
      log.addedToken(getTokenDisplayText(token));
    } catch (AliasServiceException e) {
      log.failedToSaveTokenState(e);
    }
  }

  @Override
  protected void setMaxLifetime(final String token, long issueTime, long maxLifetimeDuration) {
    try {
      aliasService.addAliasForCluster(AliasService.NO_CLUSTER_NAME,
                                      token + "--max",
                                      String.valueOf(issueTime + maxLifetimeDuration));
    } catch (AliasServiceException e) {
      log.failedToSaveTokenState(e);
    }
  }

  @Override
  protected long getMaxLifetime(final String token) {
    long result = 0;
    try {
      char[] maxLifetimeStr =
                      aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, token + "--max");
      if (maxLifetimeStr != null) {
        result = Long.parseLong(new String(maxLifetimeStr));
      }
    } catch (AliasServiceException e) {
      log.errorAccessingTokenState(e);
    }
    return result;
  }

  @Override
  public long getTokenExpiration(final String token) {
    long expiration = 0;

    validateToken(token);

    try {
      char[] expStr = aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, token);
      if (expStr != null) {
        expiration = Long.parseLong(new String(expStr));
      }
    } catch (Exception e) {
      log.errorAccessingTokenState(e);
    }

    return expiration;
  }

  @Override
  public void revokeToken(final String token) {
    /* no reason to keep revoked tokens around */
    removeToken(token);
    log.revokedToken(getTokenDisplayText(token));
  }

  @Override
  protected boolean isUnknown(final String token) {
    boolean isUnknown = false;
    try {
      isUnknown = (aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, token) == null);
    } catch (AliasServiceException e) {
      log.errorAccessingTokenState(e);
    }
    return isUnknown;
  }

  @Override
  protected void removeToken(final String token) {
    validateToken(token);

    try {
      aliasService.removeAliasForCluster(AliasService.NO_CLUSTER_NAME, token);
      aliasService.removeAliasForCluster(AliasService.NO_CLUSTER_NAME,token + "--max");
    } catch (AliasServiceException e) {
      log.failedToUpdateTokenExpiration(e);
    }

  }

  @Override
  protected void updateExpiration(final String token, long expiration) {
    if (isUnknown(token)) {
      log.unknownToken(getTokenDisplayText(token));
      throw new IllegalArgumentException("Unknown token.");
    }

    try {
      aliasService.removeAliasForCluster(AliasService.NO_CLUSTER_NAME, token);
      aliasService.addAliasForCluster(AliasService.NO_CLUSTER_NAME, token, String.valueOf(expiration));
    } catch (AliasServiceException e) {
      log.failedToUpdateTokenExpiration(e);
    }
  }
}

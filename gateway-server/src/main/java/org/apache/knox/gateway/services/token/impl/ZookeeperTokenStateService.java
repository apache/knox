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

import static org.apache.knox.gateway.services.ServiceType.ALIAS_SERVICE;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.factory.AliasServiceFactory;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.impl.ZookeeperRemoteAliasService;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.token.RemoteTokenStateChangeListener;
import org.apache.knox.gateway.util.Tokens;

/**
 * A Zookeeper Token State Service is actually an Alias based TSS where the 'alias service' happens to be the 'zookeeper' implementation.
 * This means the only important thing that should be overridden here is the init method where the underlying alias service is configured
 * properly.
 */
public class ZookeeperTokenStateService extends AliasBasedTokenStateService implements RemoteTokenStateChangeListener {

  private final GatewayServices gatewayServices;
  private final AliasServiceFactory aliasServiceFactory;

  public ZookeeperTokenStateService(GatewayServices gatewayServices) {
    this(gatewayServices, new AliasServiceFactory());
  }

  public ZookeeperTokenStateService(GatewayServices gatewayServices, AliasServiceFactory aliasServiceFactory) {
    this.gatewayServices = gatewayServices;
    this.aliasServiceFactory = aliasServiceFactory;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    final ZookeeperRemoteAliasService zookeeperAliasService = (ZookeeperRemoteAliasService) aliasServiceFactory.create(gatewayServices, ALIAS_SERVICE, config, options,
        ZookeeperRemoteAliasService.class.getName());
    options.put(ZookeeperRemoteAliasService.OPTION_NAME_SHOULD_CREATE_TOKENS_SUB_NODE, "true");
    options.put(ZookeeperRemoteAliasService.OPTION_NAME_SHOULD_USE_LOCAL_ALIAS, "false");
    zookeeperAliasService.registerRemoteTokenStateChangeListener(this);
    zookeeperAliasService.init(config, options);
    super.setAliasService(zookeeperAliasService);
    super.init(config, options);
    options.remove(ZookeeperRemoteAliasService.OPTION_NAME_SHOULD_CREATE_TOKENS_SUB_NODE);
    options.remove(ZookeeperRemoteAliasService.OPTION_NAME_SHOULD_USE_LOCAL_ALIAS);
  }

  @Override
  protected void loadTokenAliasesFromPersistenceStore() {
    // NOP : registering 'knox/security/topology' child entry listener in ZKRemoteAliasService ends-up reading existing ZK nodes
    // and with the help of RemoteTokenStateChangeListener notifications in-memory collections will be populated
    // without loading them here directly
  }

  @Override
  protected boolean readyForEviction() {
    return true;
  }

  @Override
  protected char[] getPasswordUsingAliasService(String alias) throws AliasServiceException {
    char[] password = super.getPasswordUsingAliasService(alias);

    if (password == null) {
      password = retry(alias);
    }
    return password;
  }

  /*
   * In HA scenarios, it might happen, that node1 generated a token but the state
   * persister thread saves that token in ZK a bit later. If there is a subsequent
   * call to this token on another node - e.g. node2 - before it's persisted in ZK
   * the token would be considered unknown. (see CDPD-22225)
   *
   * To avoid this issue, the ZK token state service should retry to fetch the
   * token from ZK in every second until the token is found or the number of
   * retries exceeded the configured persistence interval
   */
  private char[] retry(String alias) throws AliasServiceException {
    char[] password = null;
    final Instant timeLimit = Instant.now().plusSeconds(statePersistenceInterval).plusSeconds(1); // an addition of 1 second as grace period

    while (password == null && timeLimit.isAfter(Instant.now())) {
      try {
        TimeUnit.SECONDS.sleep(1);
        log.retryZkFetchAlias(getDisplayableAliasText(alias));
        password = super.getPasswordUsingAliasService(alias);
      } catch (InterruptedException e) {
        log.failedRetryZkFetchAlias(getDisplayableAliasText(alias), e.getMessage(), e);
      }
    }
    return password;
  }

  @Override
  public void onChanged(String alias, String updatedState) {
    processAlias(alias, updatedState);
    log.onRemoteTokenStateChanged(getDisplayableAliasText(alias));
  }

  @Override
  public void onRemoved(String alias) {
    final String tokenId = getTokenIdFromAlias(alias);
    removeTokensFromMemory(Collections.singleton(tokenId));
    log.onRemoteTokenStateRemoval(getDisplayableAliasText(alias));
  }

  private void processAlias(String alias, String value) {
    if (!ZookeeperRemoteAliasService.TOKENS_SUB_NODE_NAME.equals(alias)) {
      try {
        final String tokenId = getTokenIdFromAlias(alias);
        if (alias.endsWith(TOKEN_MAX_LIFETIME_POSTFIX)) {
          final long maxLifeTime = Long.parseLong(value);
          setMaxLifetime(tokenId, maxLifeTime);
        } else if (alias.endsWith(TOKEN_META_POSTFIX)) {
          addMetadataInMemory(tokenId, TokenMetadata.fromJSON(value));
        } else if (alias.endsWith(TOKEN_ISSUE_TIME_POSTFIX)) {
          setIssueTimeInMemory(tokenId, Long.parseLong(value));
        } else {
          final long expiration = Long.parseLong(value);
          updateExpirationInMemory(tokenId, expiration);
        }
      } catch (Throwable e) {
        log.errorWhileProcessingTokenAlias(getDisplayableAliasText(alias), e.getMessage(), e);
      }
    }
  }

  private String getTokenIdFromAlias(final String alias) {
    return alias.contains(TOKEN_ALIAS_SUFFIX_DELIM) ? alias.substring(0, alias.indexOf(TOKEN_ALIAS_SUFFIX_DELIM)) : alias;
  }

  private String getDisplayableAliasText(final String alias) {
    String tokenId = getTokenIdFromAlias(alias);
    String suffix = alias.length() > tokenId.length() ? alias.substring(tokenId.length()) : "";
    return Tokens.getTokenIDDisplayText(tokenId) + suffix;
  }
}

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
package org.apache.knox.gateway.util;

import static org.apache.knox.gateway.services.token.impl.AliasBasedTokenStateService.TOKEN_ISSUE_TIME_POSTFIX;
import static org.apache.knox.gateway.services.token.impl.AliasBasedTokenStateService.TOKEN_MAX_LIFETIME_POSTFIX;
import static org.apache.knox.gateway.services.token.impl.AliasBasedTokenStateService.TOKEN_META_POSTFIX;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.token.impl.TokenStateServiceMessages;

public class TokenMigrationTool {

  private static final TokenStateServiceMessages LOG = MessagesFactory.get(TokenStateServiceMessages.class);

  private final AliasService aliasService;
  private final TokenStateService tokenStateService;
  private final PrintStream out;

  private int progressCount = 10;
  private boolean archiveMigratedTokens;
  private boolean migrateExpiredTokens;
  private boolean verbose;

  public TokenMigrationTool(AliasService aliasService, TokenStateService tokenStateService, PrintStream out) {
    this.aliasService = aliasService;
    this.tokenStateService = tokenStateService;
    this.out = out;
  }

  public void setProgressCount(int progressCount) {
    this.progressCount = progressCount;
  }

  public void setArchiveMigratedTokens(boolean archiveMigratedTokens) {
    this.archiveMigratedTokens = archiveMigratedTokens;
  }

  public void setMigrateExpiredTokens(boolean migrateExpiredTokens) {
    this.migrateExpiredTokens = migrateExpiredTokens;
  }

  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  public void migrateTokensFromGatewayCredentialStore() {
    try {
      final Map<String, TokenData> tokenDataMap = new ConcurrentHashMap<>();
      final long start = System.currentTimeMillis();
      String logMessage = "Loading token aliases from the __gateway credential store. This could take a while.";
      log(logMessage);
      final Map<String, char[]> passwordAliasMap = aliasService.getPasswordsForGateway();
      log("Token aliases loaded in " + (System.currentTimeMillis() - start) + " milliseconds");
      String alias;
      for (Map.Entry<String, char[]> passwordAliasMapEntry : passwordAliasMap.entrySet()) {
        alias = passwordAliasMapEntry.getKey();
        processAlias(passwordAliasMap, passwordAliasMapEntry, alias, tokenDataMap);
      }

      final long migrationStart = System.currentTimeMillis();
      final AtomicInteger count = new AtomicInteger(0);
      tokenDataMap.entrySet().forEach(entry -> {
        int loggedCount = 0;
        saveTokenIfComplete(tokenStateService, entry.getKey(), entry.getValue());
        count.incrementAndGet();
        // log some progress (it's very useful in case a huge amount of token related
        // aliases in __gateway-credentials.jceks)
        if (count.intValue() > 0 && (count.intValue() % progressCount == 0) && loggedCount != count.intValue()) {
          loggedCount = count.intValue();
          logProgress(count.intValue(), System.currentTimeMillis() - migrationStart);
        }
      });

      logProgress(count.intValue(), System.currentTimeMillis() - migrationStart);

      archiveTokens(tokenDataMap);

      removeTokenAliasesFromGatewayCredentialStore(tokenDataMap);
    } catch (AliasServiceException e) {
      throw new RuntimeException("Error while migrating tokens from __gateway credential store: " + e.getMessage(), e);
    }
  }

  private void log(String message) {
    LOG.info(message);
    if (out != null) {
      out.println(message);
    }
  }

  private void logProgress(int count, long duration) {
    log(String.format(Locale.getDefault(), "Processed %d tokens in %d milliseconds", count, duration));
  }

  /*
   *
   * The AliasBasedTSS implementation persists 4 aliases in  __gateway-credentials.jceks:
   *  - an alias which maps a token ID to its expiration time
   *  - an alias with '--max' postfix which maps the maximum lifetime of the token identified by the 1st alias
   *  - an alias with '--iss' postfix which maps the issue time of the token
   *  - an alias with '-meta' postfix which maps an arbitrary metadata of the token
   *
   */
  private void processAlias(final Map<String, char[]> passwordAliasMap, Map.Entry<String, char[]> passwordAliasMapEntry, String alias,
      Map<String, TokenData> tokenDataMap) {
    String tokenId = null;
    long expiration, maxLifeTime;
    if (alias.endsWith(TOKEN_MAX_LIFETIME_POSTFIX)) {
      tokenId = alias.substring(0, alias.indexOf(TOKEN_MAX_LIFETIME_POSTFIX));
      tokenDataMap.putIfAbsent(tokenId, new TokenData());
      expiration = convertCharArrayToLong(passwordAliasMap.get(tokenId));
      maxLifeTime = convertCharArrayToLong(passwordAliasMapEntry.getValue());
      tokenDataMap.get(tokenId).expiration = expiration;
      tokenDataMap.get(tokenId).maxLifeTime = maxLifeTime;
    } else if (alias.endsWith(TOKEN_META_POSTFIX)) {
      tokenId = alias.substring(0, alias.indexOf(TOKEN_META_POSTFIX));
      tokenDataMap.putIfAbsent(tokenId, new TokenData());
      tokenDataMap.get(tokenId).metadata = TokenMetadata.fromJSON(new String(passwordAliasMapEntry.getValue()));
    } else if (alias.endsWith(TOKEN_ISSUE_TIME_POSTFIX)) {
      tokenId = alias.substring(0, alias.indexOf(TOKEN_ISSUE_TIME_POSTFIX));
      tokenDataMap.putIfAbsent(tokenId, new TokenData());
      tokenDataMap.get(tokenId).issueTime = convertCharArrayToLong(passwordAliasMapEntry.getValue());
    }
  }

  private long convertCharArrayToLong(char[] charArray) {
    return Long.parseLong(new String(charArray));
  }

  private void saveTokenIfComplete(TokenStateService tokenStateService, String tokenId, TokenData tokenData) {
    if (tokenId != null && tokenData.isComplete() && !tokenData.isProcessed()) {
      if (migrateToken(tokenData)) {
        tokenStateService.addToken(tokenId, tokenData.issueTime, tokenData.expiration, tokenData.maxLifeTime);
        tokenStateService.addMetadata(tokenId, tokenData.metadata);
        if (verbose) {
          log("Migrated token " + Tokens.getTokenIDDisplayText(tokenId) + " into the configured TokenStateService backend.");
        }
      } else {
        if (verbose) {
          log("Skipping the migration of expired token with ID = " + Tokens.getTokenIDDisplayText(tokenId));
        }
      }
    }
    tokenData.processed = true;
  }

  private boolean migrateToken(TokenData tokenData) {
    return tokenData.isExpired() ? migrateExpiredTokens : true;
  }

  private void archiveTokens(Map<String, TokenData> tokenDataMap) throws AliasServiceException {
    if (archiveMigratedTokens) {
      final String cluster = "__tokens";
      log("Archiving token aliases in the " + cluster + " credential store...");
      final long start = System.currentTimeMillis();
      final Map<String, String> tokenAliasesToArchive = new HashMap<>();
      tokenDataMap.entrySet().forEach(tokenDataMapEntry -> {
        String tokenId = tokenDataMapEntry.getKey();
        tokenDataMapEntry.getValue();
        tokenAliasesToArchive.put(tokenId, String.valueOf(tokenDataMapEntry.getValue().expiration));
        tokenAliasesToArchive.put(tokenId + TOKEN_MAX_LIFETIME_POSTFIX, String.valueOf(tokenDataMapEntry.getValue().maxLifeTime));
        tokenAliasesToArchive.put(tokenId + TOKEN_ISSUE_TIME_POSTFIX, String.valueOf(tokenDataMapEntry.getValue().issueTime));
        tokenAliasesToArchive.put(tokenId + TOKEN_META_POSTFIX, tokenDataMapEntry.getValue().metadata.toJSON());
      });
      aliasService.addAliasesForCluster(cluster, tokenAliasesToArchive);
      log("Archived token related aliases in the " + cluster + " credential store in " + (System.currentTimeMillis() - start) + " millsieconds ");
    }
  }

  private void removeTokenAliasesFromGatewayCredentialStore(Map<String, TokenData> tokenDataMap) throws AliasServiceException {
    log("Removing token aliases from the __gateway credential store...");
    final long start = System.currentTimeMillis();
    final Set<String> tokenAliases = new HashSet<>();
    tokenDataMap.entrySet().forEach(tokenDataMapEntry -> {
      String tokenId = tokenDataMapEntry.getKey();
      tokenAliases.addAll(Arrays.asList(tokenId, tokenId + TOKEN_MAX_LIFETIME_POSTFIX, tokenId + TOKEN_ISSUE_TIME_POSTFIX, tokenId + TOKEN_META_POSTFIX));
    });
    aliasService.removeAliasesForCluster(AliasService.NO_CLUSTER_NAME, tokenAliases);
    log("Removed token related aliases from the __gateway credential store in " + (System.currentTimeMillis() - start) + " milliseconds");
  }

  private class TokenData {
    boolean processed;
    long issueTime = -1;
    long maxLifeTime = -1;
    long expiration = -2; // can be set to '-1' meaning it never expires
    TokenMetadata metadata;

    boolean isComplete() {
      return issueTime != -1 && maxLifeTime != -1 && expiration != -2 && metadata != null;
    }

    boolean isProcessed() {
      return processed;
    }

    boolean isExpired() {
      return expiration == -1 ? false : expiration < System.currentTimeMillis();
    }
  }

}

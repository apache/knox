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
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.services.token.state.JournalEntry;
import org.apache.knox.gateway.services.token.state.TokenStateJournal;
import org.apache.knox.gateway.services.token.impl.state.TokenStateJournalFactory;
import org.easymock.EasyMock;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AliasBasedTokenStateServiceTest extends DefaultTokenStateServiceTest {

  private Long tokenStatePersistenceInterval = TimeUnit.SECONDS.toMillis(15);

  @Override
  protected long getTokenStatePersistenceInterval() {
    return (tokenStatePersistenceInterval != null) ? tokenStatePersistenceInterval : super.getTokenStatePersistenceInterval();
  }

  /**
   * KNOX-2375
   */
  @Test
  public void testBulkTokenStateEviction() throws Exception {
    final long evictionInterval = TimeUnit.SECONDS.toMillis(3);
    final long maxTokenLifetime = evictionInterval * 3;

    final Set<JWTToken> testTokens = new HashSet<>();
    for (int i = 0; i < 10 ; i++) {
      testTokens.add(createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60)));
    }

    List<String> testTokenStateAliases = new ArrayList<>();
    for (JWTToken token : testTokens) {
      String tokenId = token.getClaim(JWTToken.KNOX_ID_CLAIM);
      testTokenStateAliases.add(tokenId);
      testTokenStateAliases.add(tokenId + AliasBasedTokenStateService.TOKEN_MAX_LIFETIME_POSTFIX);
    }

    // Create a mock AliasService so we can verify that the expected bulk removal method is invoked when the token state
    // reaper runs.
    AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForCluster(anyString(), anyString()))
            .andReturn(String.valueOf(System.currentTimeMillis()).toCharArray())
            .anyTimes();
    EasyMock.expect(aliasService.getAliasesForCluster(AliasService.NO_CLUSTER_NAME)).andReturn(testTokenStateAliases).anyTimes();
    // Expecting the bulk alias removal method to be invoked only once, rather than the individual alias removal method
    // invoked twice for every expired token.
    aliasService.removeAliasesForCluster(anyString(), anyObject());
    EasyMock.expectLastCall().andVoid().once();

    EasyMock.replay(aliasService);

    AliasBasedTokenStateService tss = new AliasBasedTokenStateService();
    tss.setAliasService(aliasService);
    initTokenStateService(tss);

    try {
      tss.start();

      // Add the expired tokens
      for (JWTToken token : testTokens) {
        tss.addToken(token.getClaim(JWTToken.KNOX_ID_CLAIM),
                     System.currentTimeMillis(),
                     token.getExpiresDate().getTime(),
                     maxTokenLifetime);
        assertTrue("Expected the token to have expired.", tss.isExpired(token));
      }

      // Sleep to allow the eviction evaluation to be performed
      Thread.sleep(evictionInterval + (evictionInterval / 2));
    } finally {
      tss.stop();
    }

    // Verify that the expected method was invoked
    EasyMock.verify(aliasService);
  }

  @Test
  public void testAddAndRemoveTokenIncludesCache() throws Exception {
    final int TOKEN_COUNT = 10;

    final Set<JWTToken> testTokens = new HashSet<>();
    for (int i = 0; i < TOKEN_COUNT ; i++) {
      testTokens.add(createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60)));
    }

    List<String> testTokenStateAliases = new ArrayList<>();
    for (JWTToken token : testTokens) {
      String tokenId = token.getClaim(JWTToken.KNOX_ID_CLAIM);
      testTokenStateAliases.add(tokenId);
      testTokenStateAliases.add(tokenId + AliasBasedTokenStateService.TOKEN_MAX_LIFETIME_POSTFIX);
    }

    // Create a mock AliasService so we can verify that the expected bulk removal method is invoked (and that the
    // individual removal method is NOT invoked) when the token state reaper runs.
    AliasService aliasService = EasyMock.createMock(AliasService.class);
    EasyMock.expect(aliasService.getAliasesForCluster(AliasService.NO_CLUSTER_NAME)).andReturn(testTokenStateAliases).anyTimes();
    // Expecting the bulk alias removal method to be invoked only once, rather than the individual alias removal method
    // invoked twice for every expired token.
    aliasService.removeAliasesForCluster(anyString(), anyObject());
    EasyMock.expectLastCall().andVoid().once();

    EasyMock.replay(aliasService);

    AliasBasedTokenStateService tss = new AliasBasedTokenStateService();
    tss.setAliasService(aliasService);
    initTokenStateService(tss);

    Map<String, Long> tokenExpirations = getTokenExpirationsField(tss);
    Map<String, Long> maxTokenLifetimes = getMaxTokenLifetimesField(tss);

    final long evictionInterval = TimeUnit.SECONDS.toMillis(3);
    final long maxTokenLifetime = evictionInterval * 3;

    try {
      tss.start();

      // Add the expired tokens
      for (JWTToken token : testTokens) {
        tss.addToken(token.getClaim(JWTToken.KNOX_ID_CLAIM),
                     System.currentTimeMillis(),
                     token.getExpiresDate().getTime(),
                     maxTokenLifetime);
      }

      assertEquals("Expected the tokens to have been added in the base class cache.", 10, tokenExpirations.size());
      assertEquals("Expected the tokens lifetimes to have been added in the base class cache.",
                   10,
                   maxTokenLifetimes.size());

      // Sleep to allow the eviction evaluation to be performed
      Thread.sleep(evictionInterval + (evictionInterval / 4));

    } finally {
      tss.stop();
    }

    // Verify that the expected methods were invoked
    EasyMock.verify(aliasService);

    assertEquals("Expected the tokens to have been removed from the base class cache as a result of eviction.",
                 0,
                 tokenExpirations.size());
    assertEquals("Expected the tokens lifetimes to have been removed from the base class cache as a result of eviction.",
                 0,
                 maxTokenLifetimes.size());
  }

  /**
   * Verify that the token state reaper includes token state which has not been cached, so it's not left in the keystore
   * forever.
   */
  @Test
  public void testTokenEvictionIncludesUncachedAliases() throws Exception {
    final long evictionInterval = TimeUnit.SECONDS.toMillis(3);
    final long maxTokenLifetime = evictionInterval * 3;

    final Set<JWTToken> testTokens = new HashSet<>();
    for (int i = 0; i < 10 ; i++) {
      testTokens.add(createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60)));
    }

    List<String> testTokenStateAliases = new ArrayList<>();
    for (JWTToken token : testTokens) {
      testTokenStateAliases.add(token.getClaim(JWTToken.KNOX_ID_CLAIM));
      testTokenStateAliases.add(token.getClaim(JWTToken.KNOX_ID_CLAIM) + AliasBasedTokenStateService.TOKEN_MAX_LIFETIME_POSTFIX);
    }

    // Add aliases for an uncached test token
    final JWTToken uncachedToken = createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60));
    final String uncachedTokenId = uncachedToken.getClaim(JWTToken.KNOX_ID_CLAIM);
    testTokenStateAliases.add(uncachedTokenId);
    testTokenStateAliases.add(uncachedTokenId + AliasBasedTokenStateService.TOKEN_MAX_LIFETIME_POSTFIX);
    final long uncachedTokenExpiration = System.currentTimeMillis();
    System.out.println("Uncached token ID: " + uncachedTokenId);

    final Set<String> expectedTokensToEvict = new HashSet<>();
    expectedTokensToEvict.addAll(testTokenStateAliases);
    expectedTokensToEvict.add(uncachedTokenId);
    expectedTokensToEvict.add(uncachedTokenId + AliasBasedTokenStateService.TOKEN_MAX_LIFETIME_POSTFIX);

    // Create a mock AliasService so we can verify that the expected bulk removal method is invoked (and that the
    // individual removal method is NOT invoked) when the token state reaper runs.
    AliasService aliasService = EasyMock.createMock(AliasService.class);
    EasyMock.expect(aliasService.getAliasesForCluster(AliasService.NO_CLUSTER_NAME)).andReturn(testTokenStateAliases).anyTimes();
    // Expecting the bulk alias removal method to be invoked only once, rather than the individual alias removal method
    // invoked twice for every expired token.
    aliasService.removeAliasesForCluster(anyString(), EasyMock.eq(expectedTokensToEvict));
    EasyMock.expectLastCall().andVoid().once();
    aliasService.getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, uncachedTokenId);
    EasyMock.expectLastCall().andReturn(String.valueOf(uncachedTokenExpiration).toCharArray()).once();

    EasyMock.replay(aliasService);

    AliasBasedTokenStateService tss = new AliasBasedTokenStateService();
    tss.setAliasService(aliasService);
    initTokenStateService(tss);

    Map<String, Long> tokenExpirations = getTokenExpirationsField(tss);
    Map<String, Long> maxTokenLifetimes = getMaxTokenLifetimesField(tss);

    try {
      tss.start();

      // Add the expired tokens
      for (JWTToken token : testTokens) {
        tss.addToken(token.getClaim(JWTToken.KNOX_ID_CLAIM),
                     System.currentTimeMillis(),
                     token.getExpiresDate().getTime(),
                     maxTokenLifetime);
      }

      assertEquals("Expected the tokens to have been added in the base class cache.", 10, tokenExpirations.size());
      assertEquals("Expected the tokens lifetimes to have been added in the base class cache.",
                   10,
                   maxTokenLifetimes.size());

      // Sleep to allow the eviction evaluation to be performed, but only one iteration
      Thread.sleep(evictionInterval + (evictionInterval / 4));
    } finally {
      tss.stop();
    }

    // Verify that the expected methods were invoked
    EasyMock.verify(aliasService);

    assertEquals("Expected the tokens to have been removed from the base class cache as a result of eviction.",
                 0,
                 tokenExpirations.size());
    assertEquals("Expected the tokens lifetimes to have been removed from the base class cache as a result of eviction.",
                 0,
                 maxTokenLifetimes.size());
  }

  @Test
  public void testGetMaxLifetimeUsesCache() throws Exception {
    AliasService aliasService = EasyMock.createMock(AliasService.class);
    aliasService.addAliasesForCluster(anyString(), anyObject());
    EasyMock.expectLastCall().once(); // Expecting this during shutdown

    EasyMock.replay(aliasService);

    AliasBasedTokenStateService tss = new AliasBasedTokenStateService();
    tss.setAliasService(aliasService);
    initTokenStateService(tss);

    Map<String, Long> maxTokenLifetimes = getMaxTokenLifetimesField(tss);

    final long evictionInterval = TimeUnit.SECONDS.toMillis(3);
    final long maxTokenLifetime = evictionInterval * 3;

    final Set<JWTToken> testTokens = new HashSet<>();
    for (int i = 0; i < 10 ; i++) {
      testTokens.add(createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60)));
    }

    try {
      tss.start();

      // Add the expired tokens
      for (JWTToken token : testTokens) {
        tss.addToken(token.getClaim(JWTToken.KNOX_ID_CLAIM),
                System.currentTimeMillis(),
                token.getExpiresDate().getTime(),
                maxTokenLifetime);

      }

      assertEquals("Expected the tokens lifetimes to have been added in the base class cache.",
                   10,
                   maxTokenLifetimes.size());

      // Set the cache values to be different from the underlying alias value
      final long updatedMaxLifetime = evictionInterval * 5;
      for (Map.Entry<String, Long> entry : maxTokenLifetimes.entrySet()) {
        entry.setValue(updatedMaxLifetime);
      }

      // Verify that we get the cache value back
      for (String tokenId : maxTokenLifetimes.keySet()) {
        assertEquals("Expected the cached max lifetime, rather than the alias value",
                     updatedMaxLifetime,
                     tss.getMaxLifetime(tokenId));
      }
    } finally {
      tss.stop();
    }

    // Verify that the expected methods were invoked
    EasyMock.verify(aliasService);
  }

  @Test
  public void testUpdateExpirationUsesCache() throws Exception {
    AliasService aliasService = EasyMock.createMock(AliasService.class);
    aliasService.addAliasForCluster(anyString(), anyString(), anyString());
    EasyMock.expectLastCall().andVoid().atLeastOnce();
    aliasService.removeAliasForCluster(anyString(), anyObject());
    EasyMock.expectLastCall().andVoid().atLeastOnce();
    aliasService.addAliasesForCluster(anyString(), anyObject());
    EasyMock.expectLastCall().andVoid().once(); // Expecting this during shutdown

    EasyMock.replay(aliasService);

    AliasBasedTokenStateService tss = new AliasBasedTokenStateService();
    tss.setAliasService(aliasService);
    initTokenStateService(tss);

    Map<String, Long> tokenExpirations = getTokenExpirationsField(tss);

    final long evictionInterval = TimeUnit.SECONDS.toMillis(3);
    final long maxTokenLifetime = evictionInterval * 3;

    final Set<JWTToken> testTokens = new HashSet<>();
    for (int i = 0; i < 10 ; i++) {
      testTokens.add(createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60)));
    }

    try {
      tss.start();

      // Add the expired tokens
      for (JWTToken token : testTokens) {
        tss.addToken(token.getClaim(JWTToken.KNOX_ID_CLAIM),
                     System.currentTimeMillis(),
                     token.getExpiresDate().getTime(),
                     maxTokenLifetime);
      }

      assertEquals("Expected the tokens expirations to have been added in the base class cache.",
                   10,
                   tokenExpirations.size());

      // Set the cache values to be different from the underlying alias value
      final long updatedExpiration = System.currentTimeMillis();
      for (String tokenId : tokenExpirations.keySet()) {
        tss.updateExpiration(tokenId, updatedExpiration);
      }

      // Invoking with true/false validation flags as it should not affect if values are coming from the cache
      int count = 0;
      for (String tokenId : tokenExpirations.keySet()) {
        assertEquals("Expected the cached expiration to have been updated.",
                     updatedExpiration,
                     tss.getTokenExpiration(tokenId, count++ % 2 == 0));
      }

    } finally {
      tss.stop();
    }

    // Verify that the expected methods were invoked
    EasyMock.verify(aliasService);
  }

  @Test
  public void testTokenStateJournaling() throws Exception {
    AliasService aliasService = EasyMock.createMock(AliasService.class);
    aliasService.getAliasesForCluster(anyString());
    EasyMock.expectLastCall().andReturn(Collections.emptyList()).anyTimes();
    aliasService.addAliasesForCluster(anyString(), anyObject());
    EasyMock.expectLastCall().once();
    EasyMock.replay(aliasService);

    tokenStatePersistenceInterval = 1L; // Override the persistence interval for this test

    AliasBasedTokenStateService tss = new AliasBasedTokenStateService();
    tss.setAliasService(aliasService);
    initTokenStateService(tss);

    Field maxTokenLifetimesField = tss.getClass().getSuperclass().getDeclaredField("maxTokenLifetimes");
    maxTokenLifetimesField.setAccessible(true);
    Map<String, Long> maxTokenLifetimes = (Map<String, Long>) maxTokenLifetimesField.get(tss);

    Path journalDir = Paths.get(getGatewaySecurityDir(), "token-state");

    final long evictionInterval = TimeUnit.SECONDS.toMillis(3);
    final long maxTokenLifetime = evictionInterval * 3;

    final List<String> tokenIds = new ArrayList<>();
    final Set<JWTToken> testTokens = new HashSet<>();
    for (int i = 0; i < 10 ; i++) {
      JWTToken token = createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60));
      testTokens.add(token);
      tokenIds.add(token.getClaim(JWTToken.KNOX_ID_CLAIM));
    }

    try {
      tss.start();

      // Add the expired tokens
      for (JWTToken token : testTokens) {
        tss.addToken(token.getClaim(JWTToken.KNOX_ID_CLAIM),
                     System.currentTimeMillis(),
                     token.getExpiresDate().getTime(),
                     maxTokenLifetime);
      }

      assertEquals("Expected the tokens lifetimes to have been added in the base class cache.",
                   10,
                   maxTokenLifetimes.size());

      // Check for the expected number of files corresponding to journal entries
      List<Path> listing = Files.list(journalDir).collect(Collectors.toList());
      assertFalse(listing.isEmpty());
      assertEquals(10, listing.size());

      // Validate the journal entry file names
      for (Path p : listing) {
        Path filename = p.getFileName();
        String filenameString = filename.toString();
        assertTrue(filenameString.endsWith(".ts"));
        String tokenId = filenameString.substring(0, filenameString.length() - 3);
        assertTrue(tokenIds.contains(tokenId));
      }

      // Sleep to allow the persistence to be performed
      Thread.sleep(TimeUnit.SECONDS.toMillis(tokenStatePersistenceInterval) * 2);

    } finally {
      tss.stop();
      tokenStatePersistenceInterval = null;
    }

    // Verify that the expected methods were invoked
    EasyMock.verify(aliasService);

    // Verify that the journal entries were removed when the aliases were created
    List<Path> listing = Files.list(journalDir).collect(Collectors.toList());
    assertTrue(listing.isEmpty());
  }

  @Test
  public void testLoadTokenStateJournalDuringInit() throws Exception {
    final int TOKEN_COUNT = 10;

    AliasService aliasService = EasyMock.createMock(AliasService.class);
    aliasService.getAliasesForCluster(anyString());
    EasyMock.expectLastCall().andReturn(Collections.emptyList()).anyTimes();
    EasyMock.replay(aliasService);

    // Create some test tokens
    final Set<JWTToken> testTokens = new HashSet<>();
    for (int i = 0; i < TOKEN_COUNT ; i++) {
      JWTToken token = createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60));
      testTokens.add(token);
    }

    // Persist the token state journal entries before initializing the TokenStateService
    TokenStateJournal journal = TokenStateJournalFactory.create(createMockGatewayConfig(false));
    for (JWTToken token : testTokens) {
      journal.add(token.getClaim(JWTToken.KNOX_ID_CLAIM),
                  System.currentTimeMillis(),
                  token.getExpiresDate().getTime(),
                  System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24));
    }

    AliasBasedTokenStateService tss = new AliasBasedTokenStateService();
    tss.setAliasService(aliasService);

    // Initialize the service, and presumably load the previously-persisted journal entries
    initTokenStateService(tss);

    Map<String, Long> tokenExpirations = getTokenExpirationsField(tss);
    Map<String, Long> maxTokenLifetimes = getMaxTokenLifetimesField(tss);

    List<AliasBasedTokenStateService.TokenState> unpersistedState = getUnpersistedStateField(tss);

    assertEquals("Expected the tokens expirations to have been added in the base class cache.",
                 TOKEN_COUNT,
                 tokenExpirations.size());

    assertEquals("Expected the tokens lifetimes to have been added in the base class cache.",
                 TOKEN_COUNT,
                 maxTokenLifetimes.size());

    assertEquals("Expected the unpersisted state to have been added.",
                 (TOKEN_COUNT * 2), // Two TokenState entries per token (expiration, max lifetime)
                 unpersistedState.size());

    // Verify that the expected methods were invoked
    EasyMock.verify(aliasService);
  }

  @Test
  public void testLoadTokenStateJournalDuringInitWithInvalidEntries() throws Exception {
    final int TOKEN_COUNT = 5;

    AliasService aliasService = EasyMock.createMock(AliasService.class);
    aliasService.getAliasesForCluster(anyString());
    EasyMock.expectLastCall().andReturn(Collections.emptyList()).anyTimes();
    EasyMock.replay(aliasService);

    // Create some test tokens
    final Set<JWTToken> testTokens = new HashSet<>();
    for (int i = 0; i < TOKEN_COUNT ; i++) {
      JWTToken token = createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60));
      testTokens.add(token);
    }

    // Persist the token state journal entries before initializing the TokenStateService
    TokenStateJournal journal = TokenStateJournalFactory.create(createMockGatewayConfig(false));
    for (JWTToken token : testTokens) {
      journal.add(token.getClaim(JWTToken.KNOX_ID_CLAIM),
                  System.currentTimeMillis(),
                  token.getExpiresDate().getTime(),
                  System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24));
    }

    // Add an entry with an invalid token identifier
    journal.add("   ",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                System.currentTimeMillis());

    // Add an entry with an invalid issue time
    journal.add(new TestJournalEntry(UUID.randomUUID().toString(),
                "invalidLongValue",
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(System.currentTimeMillis())));

    // Add an entry with an invalid expiration time
    journal.add(new TestJournalEntry(UUID.randomUUID().toString(),
                String.valueOf(System.currentTimeMillis()),
                "invalidLongValue",
                String.valueOf(System.currentTimeMillis())));

    // Add an entry with an invalid max lifetime
    journal.add(new TestJournalEntry(UUID.randomUUID().toString(),
                                     String.valueOf(System.currentTimeMillis()),
                                     String.valueOf(System.currentTimeMillis()),
                                     "invalidLongValue"));

    AliasBasedTokenStateService tss = new AliasBasedTokenStateService();
    tss.setAliasService(aliasService);

    // Initialize the service, and presumably load the previously-persisted journal entries
    initTokenStateService(tss);

    Map<String, Long> tokenExpirations = getTokenExpirationsField(tss);
    Map<String, Long> maxTokenLifetimes = getMaxTokenLifetimesField(tss);

    List<AliasBasedTokenStateService.TokenState> unpersistedState = getUnpersistedStateField(tss);

    assertEquals("Expected the tokens expirations to have been added in the base class cache.",
                 TOKEN_COUNT,
                 tokenExpirations.size());

    assertEquals("Expected the tokens lifetimes to have been added in the base class cache.",
                 TOKEN_COUNT,
                 maxTokenLifetimes.size());

    assertEquals("Expected the unpersisted state to have been added.",
                 (TOKEN_COUNT * 2), // Two TokenState entries per token (expiration, max lifetime)
                 unpersistedState.size());

    // Verify that the expected methods were invoked
    EasyMock.verify(aliasService);
  }

  @Override
  protected TokenStateService createTokenStateService() throws Exception {
    AliasBasedTokenStateService tss = new AliasBasedTokenStateService();
    tss.setAliasService(new TestAliasService());
    initTokenStateService(tss);
    return tss;
  }

  /**
   * A dumbed-down AliasService implementation for testing purposes only.
   */
  private static final class TestAliasService implements AliasService {

    private final Map<String, Map<String, String>> clusterAliases= new HashMap<>();


    @Override
    public List<String> getAliasesForCluster(String clusterName) throws AliasServiceException {
      List<String> aliases = new ArrayList<>();

      if (clusterAliases.containsKey(clusterName)) {
          aliases.addAll(clusterAliases.get(clusterName).keySet());
      }
      return aliases;
    }

    @Override
    public void addAliasForCluster(String clusterName, String alias, String value) throws AliasServiceException {
      Map<String, String> aliases = null;
      if (clusterAliases.containsKey(clusterName)) {
        aliases = clusterAliases.get(clusterName);
      } else {
        aliases = new HashMap<>();
        clusterAliases.put(clusterName, aliases);
      }
      aliases.put(alias, value);
    }

    @Override
    public void addAliasesForCluster(String clusterName, Map<String, String> credentials) throws AliasServiceException {
      for (Map.Entry<String, String> credential : credentials.entrySet()) {
        addAliasForCluster(clusterName, credential.getKey(), credential.getValue());
      }
    }

    @Override
    public void removeAliasForCluster(String clusterName, String alias) throws AliasServiceException {
      if (clusterAliases.containsKey(clusterName)) {
        clusterAliases.get(clusterName).remove(alias);
      }
    }

    @Override
    public void removeAliasesForCluster(String clusterName, Set<String> aliases) throws AliasServiceException {
      for (String alias : aliases) {
        removeAliasForCluster(clusterName, alias);
      }
    }

    @Override
    public char[] getPasswordFromAliasForCluster(String clusterName, String alias) throws AliasServiceException {
      char[] value = null;
      if (clusterAliases.containsKey(clusterName)) {
        String valString = clusterAliases.get(clusterName).get(alias);
        if (valString != null) {
          value = valString.toCharArray();
        }
      }
      return value;
    }

    @Override
    public char[] getPasswordFromAliasForCluster(String clusterName, String alias, boolean generate) throws AliasServiceException {
      return new char[0];
    }

    @Override
    public void generateAliasForCluster(String clusterName, String alias) throws AliasServiceException {
    }

    @Override
    public char[] getPasswordFromAliasForGateway(String alias) throws AliasServiceException {
      return getPasswordFromAliasForCluster(AliasService.NO_CLUSTER_NAME, alias);
    }

    @Override
    public char[] getGatewayIdentityPassphrase() throws AliasServiceException {
      return new char[0];
    }

    @Override
    public char[] getGatewayIdentityKeystorePassword() throws AliasServiceException {
      return new char[0];
    }

    @Override
    public char[] getSigningKeyPassphrase() throws AliasServiceException {
      return new char[0];
    }

    @Override
    public char[] getSigningKeystorePassword() throws AliasServiceException {
      return new char[0];
    }

    @Override
    public void generateAliasForGateway(String alias) throws AliasServiceException {
    }

    @Override
    public Certificate getCertificateForGateway(String alias) throws AliasServiceException {
      return null;
    }

    @Override
    public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    }

    @Override
    public void start() throws ServiceLifecycleException {
    }

    @Override
    public void stop() throws ServiceLifecycleException {
    }
  }

  @Override
  protected void addToken(TokenStateService tss, String tokenId, long issueTime, long expiration, long maxLifetime) {
    super.addToken(tss, tokenId, issueTime, expiration, maxLifetime);

    // Persist any unpersisted token state aliases
    triggerAliasPersistence(tss);
  }

  @Override
  protected void addToken(TokenStateService tss, JWTToken token, long issueTime) {
    super.addToken(tss, token, issueTime);

    // Persist any unpersisted token state aliases
    triggerAliasPersistence(tss);
  }

  private void triggerAliasPersistence(TokenStateService tss) {
    if (tss instanceof AliasBasedTokenStateService) {
      try {
        Method m = tss.getClass().getDeclaredMethod("persistTokenState");
        m.setAccessible(true);
        m.invoke(tss);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private static Map<String, Long> getTokenExpirationsField(TokenStateService tss) throws Exception {
    Field tokenExpirationsField = tss.getClass().getSuperclass().getDeclaredField("tokenExpirations");
    tokenExpirationsField.setAccessible(true);
    return (Map<String, Long>) tokenExpirationsField.get(tss);
  }

  private static Map<String, Long> getMaxTokenLifetimesField(TokenStateService tss) throws Exception {
    Field maxTokenLifetimesField = tss.getClass().getSuperclass().getDeclaredField("maxTokenLifetimes");
    maxTokenLifetimesField.setAccessible(true);
    return (Map<String, Long>) maxTokenLifetimesField.get(tss);
  }

  private static List<AliasBasedTokenStateService.TokenState> getUnpersistedStateField(TokenStateService tss)
          throws Exception {
    Field unpersistedStateField = tss.getClass().getDeclaredField("unpersistedState");
    unpersistedStateField.setAccessible(true);
    return (List<AliasBasedTokenStateService.TokenState>) unpersistedStateField.get(tss);

  }

  private static class TestJournalEntry implements JournalEntry {

    private String tokenId;
    private String issueTime;
    private String expiration;
    private String maxLifetime;

    TestJournalEntry(String tokenId, String issueTime, String expiration, String maxLifetime) {
      this.tokenId     = tokenId;
      this.issueTime   = issueTime;
      this.expiration  = expiration;
      this.maxLifetime = maxLifetime;
    }

    @Override
    public String getTokenId() {
      return tokenId;
    }

    @Override
    public String getIssueTime() {
      return issueTime;
    }

    @Override
    public String getExpiration() {
      return expiration;
    }

    @Override
    public String getMaxLifetime() {
      return maxLifetime;
    }

    @Override
    public String toString() {
      return tokenId + "," + issueTime + "," + expiration + "," + maxLifetime;
    }
  }
}

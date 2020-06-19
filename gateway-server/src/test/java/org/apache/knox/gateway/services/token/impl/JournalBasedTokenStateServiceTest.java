/*
 *
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
 *
 */
package org.apache.knox.gateway.services.token.impl;

import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.services.token.impl.state.TokenStateJournalFactory;
import org.apache.knox.gateway.services.token.state.TokenStateJournal;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JournalBasedTokenStateServiceTest extends DefaultTokenStateServiceTest {

    @Override
    protected TokenStateService createTokenStateService() throws Exception {
        TokenStateService tss =  new JournalBasedTokenStateService();
        initTokenStateService(tss);
        return tss;
    }


    @Test
    public void testBulkTokenStateEviction() throws Exception {
        final int TOKEN_COUNT = 5;
        final long evictionInterval = TimeUnit.SECONDS.toMillis(3);
        final long maxTokenLifetime = evictionInterval * 3;

        final Set<JWTToken> testTokens = new HashSet<>();
        for (int i = 0; i < TOKEN_COUNT ; i++) {
            testTokens.add(createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60)));
        }

        TokenStateService tss = createTokenStateService();

        TokenStateJournal journal = getJournalField(tss);

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

            assertEquals(TOKEN_COUNT, journal.get().size());

            // Sleep to allow the eviction evaluation to be performed
            Thread.sleep(evictionInterval + (evictionInterval / 2));
        } finally {
            tss.stop();
        }

        assertEquals(0, journal.get().size());
    }

    @Test
    public void testAddAndRemoveTokenIncludesCache() throws Exception {
        final int TOKEN_COUNT = 5;

        final Set<JWTToken> testTokens = new HashSet<>();
        for (int i = 0; i < TOKEN_COUNT ; i++) {
            testTokens.add(createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60)));
        }

        TokenStateService tss = createTokenStateService();

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

            assertEquals("Expected the tokens to have been added in the base class cache.",
                         TOKEN_COUNT,
                         tokenExpirations.size());
            assertEquals("Expected the tokens lifetimes to have been added in the base class cache.",
                         TOKEN_COUNT,
                         maxTokenLifetimes.size());

            // Sleep to allow the eviction evaluation to be performed
            Thread.sleep(evictionInterval + (evictionInterval / 4));

        } finally {
            tss.stop();
        }

        assertEquals("Expected the tokens to have been removed from the base class cache as a result of eviction.",
                     0,
                     tokenExpirations.size());
        assertEquals("Expected the tokens lifetimes to have been removed from the base class cache as a result of eviction.",
                     0,
                     maxTokenLifetimes.size());
    }

    /**
     * Verify that the token state reaper includes previously-persisted token state, so it's not left in the file
     * system forever.
     */
    @Test
    public void testTokenEvictionIncludesPreviouslyPersistedJournalEntries() throws Exception {
        final int TOKEN_COUNT = 5;
        final long evictionInterval = TimeUnit.SECONDS.toMillis(3);
        final long maxTokenLifetime = evictionInterval * 3;

        final Set<JWTToken> testTokens = new HashSet<>();
        for (int i = 0; i < TOKEN_COUNT ; i++) {
            testTokens.add(createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60)));
        }

        TokenStateJournal testJournal =
                    TokenStateJournalFactory.create(createMockGatewayConfig(false,
                                                                            getGatewaySecurityDir(),
                                                                            getTokenStatePersistenceInterval()));

        // Add a journal entry prior to initializing the TokenStateService
        final JWTToken uncachedToken = createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60));
        final String uncachedTokenId = uncachedToken.getClaim(JWTToken.KNOX_ID_CLAIM);
        testJournal.add(uncachedTokenId,
                        System.currentTimeMillis(),
                        uncachedToken.getExpiresDate().getTime(),
                        maxTokenLifetime);
        assertEquals("Expected the uncached journal entry", 1, testJournal.get().size());

        // Create and initialize the TokenStateService
        TokenStateService tss = createTokenStateService();
        TokenStateJournal journal = getJournalField(tss);

        Map<String, Long> tokenExpirations = getTokenExpirationsField(tss);
        Map<String, Long> maxTokenLifetimes = getMaxTokenLifetimesField(tss);

        assertEquals("Expected the previously-persisted journal entry to have been loaded into the cache.",
                     1,
                     tokenExpirations.size());
        assertEquals("Expected the previously-persisted journal entry to have been loaded into the cache.",
                     1,
                     maxTokenLifetimes.size());

        try {
            tss.start();

            // Add the expired tokens
            for (JWTToken token : testTokens) {
                tss.addToken(token.getClaim(JWTToken.KNOX_ID_CLAIM),
                             System.currentTimeMillis(),
                             token.getExpiresDate().getTime(),
                             maxTokenLifetime);
            }

            assertEquals("Expected the tokens to have been added in the base class cache.",
                         TOKEN_COUNT + 1,
                         tokenExpirations.size());
            assertEquals("Expected the tokens lifetimes to have been added in the base class cache.",
                         TOKEN_COUNT + 1,
                         maxTokenLifetimes.size());
            assertEquals("Expected the uncached journal entry in addition to the cached tokens",
                         TOKEN_COUNT + 1,
                         journal.get().size());


            // Sleep to allow the eviction evaluation to be performed, but only one iteration
            Thread.sleep(evictionInterval + (evictionInterval / 4));
        } finally {
            tss.stop();
        }

        assertEquals("Expected the tokens to have been removed from the base class cache as a result of eviction.",
                     0,
                     tokenExpirations.size());
        assertEquals("Expected the tokens lifetimes to have been removed from the base class cache as a result of eviction.",
                     0,
                     maxTokenLifetimes.size());
        assertEquals("Expected the journal entries to have been removed as a result of the eviction",
                     0,
                     journal.get().size());
    }

    @Test
    public void testGetMaxLifetimeUsesCache() throws Exception {
        final int TOKEN_COUNT = 10;
        TokenStateService tss = createTokenStateService();

        Map<String, Long> maxTokenLifetimes = getMaxTokenLifetimesField(tss);

        final long evictionInterval = TimeUnit.SECONDS.toMillis(3);
        final long maxTokenLifetime = evictionInterval * 3;

        final Set<JWTToken> testTokens = new HashSet<>();
        for (int i = 0; i < TOKEN_COUNT ; i++) {
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
                         TOKEN_COUNT,
                         maxTokenLifetimes.size());

            // Set the cache values to be different from the underlying journal entry value
            final long updatedMaxLifetime = evictionInterval * 5;
            for (Map.Entry<String, Long> entry : maxTokenLifetimes.entrySet()) {
                entry.setValue(updatedMaxLifetime);
            }

            // Verify that we get the cache value back
            for (String tokenId : maxTokenLifetimes.keySet()) {
                assertEquals("Expected the cached max lifetime, rather than the journal entry value",
                             updatedMaxLifetime,
                             ((JournalBasedTokenStateService) tss).getMaxLifetime(tokenId));
            }
        } finally {
            tss.stop();
        }
    }

    @Test
    public void testUpdateExpirationUsesCache() throws Exception {
        final int TOKEN_COUNT = 10;
        TokenStateService tss = createTokenStateService();

        Map<String, Long> tokenExpirations = getTokenExpirationsField(tss);

        final long evictionInterval = TimeUnit.SECONDS.toMillis(3);
        final long maxTokenLifetime = evictionInterval * 3;

        final Set<JWTToken> testTokens = new HashSet<>();
        for (int i = 0; i < TOKEN_COUNT ; i++) {
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
                         TOKEN_COUNT,
                         tokenExpirations.size());

            // Set the cache values to be different from the underlying journal entry value
            final long updatedExpiration = System.currentTimeMillis();
            for (String tokenId : tokenExpirations.keySet()) {
                ((JournalBasedTokenStateService) tss).updateExpiration(tokenId, updatedExpiration);
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
    }

    private static TokenStateJournal getJournalField(TokenStateService tss) throws Exception {
        Field journalField = JournalBasedTokenStateService.class.getDeclaredField("journal");
        journalField.setAccessible(true);
        return (TokenStateJournal) journalField.get(tss);
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
}

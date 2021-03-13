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
package org.apache.knox.gateway.services.token.impl.state;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.services.token.state.JournalEntry;
import org.apache.knox.gateway.services.token.state.TokenStateJournal;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public abstract class AbstractFileTokenStateJournalTest {

    @Rule
    public final TemporaryFolder testFolder = new TemporaryFolder();

    abstract TokenStateJournal createTokenStateJournal(GatewayConfig config) throws IOException;

    protected JournalEntry createTestJournalEntry(final String tokenId,
                                                        long issueTime,
                                                        long expiration,
                                                        long maxLifetime) {
        return new FileTokenStateJournal.FileJournalEntry(tokenId, issueTime, expiration, maxLifetime);
    }

    protected GatewayConfig getGatewayConfig() throws IOException {
        final Path dataDir = testFolder.newFolder().toPath();
        System.out.println("dataDir : " + dataDir.toString());
        Files.createDirectories(dataDir.resolve("security")); // Make sure the security directory exists

        GatewayConfigImpl config = new GatewayConfigImpl();
        config.set("gateway.data.dir", dataDir.toString());
        return config;
    }

    @Test
    public void testSingleTokenRoundTrip() throws Exception {
        GatewayConfig config = getGatewayConfig();

        TokenStateJournal journal = createTokenStateJournal(config);

        final String tokenId = String.valueOf(UUID.randomUUID());

        // Verify that the token state has not yet been journaled
        assertNull(journal.get(tokenId));

        long issueTime = System.currentTimeMillis();
        long expiration = issueTime + TimeUnit.MINUTES.toMillis(5);
        long maxLifetime = issueTime + (5 * TimeUnit.MINUTES.toMillis(5));
        journal.add(tokenId, issueTime, expiration, maxLifetime, null);

        // Get the token state from the journal, and validate its contents
        JournalEntry entry = journal.get(tokenId);
        assertNotNull(entry);
        assertEquals(tokenId, entry.getTokenId());
        assertEquals(issueTime, Long.parseLong(entry.getIssueTime()));
        assertEquals(expiration, Long.parseLong(entry.getExpiration()));
        assertEquals(maxLifetime, Long.parseLong(entry.getMaxLifetime()));

        journal.remove(tokenId);

        // Verify that the token state can no longer be gotten from the journal
        assertNull(journal.get(tokenId));
    }

    @Test
    public void testUpdateTokenState() throws Exception {
        GatewayConfig config = getGatewayConfig();

        TokenStateJournal journal = createTokenStateJournal(config);

        final String tokenId = String.valueOf(UUID.randomUUID());

        // Verify that the token state has not yet been journaled
        assertNull(journal.get(tokenId));

        long issueTime = System.currentTimeMillis();
        long expiration = issueTime + TimeUnit.MINUTES.toMillis(5);
        long maxLifetime = issueTime + (5 * TimeUnit.MINUTES.toMillis(5));
        journal.add(tokenId, issueTime, expiration, maxLifetime, null);

        // Get the token state from the journal, and validate its contents
        JournalEntry entry = journal.get(tokenId);
        assertNotNull(entry);
        assertEquals(tokenId, entry.getTokenId());
        assertEquals(issueTime, Long.parseLong(entry.getIssueTime()));
        assertEquals(expiration, Long.parseLong(entry.getExpiration()));
        assertEquals(maxLifetime, Long.parseLong(entry.getMaxLifetime()));

        long updatedExpiration = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
        journal.add(tokenId, issueTime, updatedExpiration, maxLifetime, null);

        // Get and validate the updated token state
        entry = journal.get(tokenId);
        assertNotNull(entry);
        assertEquals(tokenId, entry.getTokenId());
        assertEquals(issueTime, Long.parseLong(entry.getIssueTime()));
        assertEquals(updatedExpiration, Long.parseLong(entry.getExpiration()));
        assertEquals(maxLifetime, Long.parseLong(entry.getMaxLifetime()));

        // Verify that the token state can no longer be gotten from the journal
        journal.remove(tokenId);
        assertNull(journal.get(tokenId));
    }

    @Test
    public void testSingleJournalEntryRoundTrip() throws Exception {
        GatewayConfig config = getGatewayConfig();

        TokenStateJournal journal = createTokenStateJournal(config);

        final String tokenId = String.valueOf(UUID.randomUUID());

        // Verify that the token state has not yet been journaled
        assertNull(journal.get(tokenId));

        long issueTime = System.currentTimeMillis();
        long expiration = issueTime + TimeUnit.MINUTES.toMillis(5);
        long maxLifetime = issueTime + (5 * TimeUnit.MINUTES.toMillis(5));
        JournalEntry original = createTestJournalEntry(tokenId, issueTime, expiration, maxLifetime);
        journal.add(original);

        // Get the token state from the journal, and validate its contents
        JournalEntry entry = journal.get(tokenId);
        assertNotNull(entry);
        assertEquals(original.getTokenId(), entry.getTokenId());
        assertEquals(original.getIssueTime(), entry.getIssueTime());
        assertEquals(original.getExpiration(), entry.getExpiration());
        assertEquals(original.getMaxLifetime(), entry.getMaxLifetime());

        journal.remove(entry);

        // Verify that the token state can no longer be gotten from the journal
        assertNull(journal.get(tokenId));
    }

    @Test
    public void testMultipleTokensRoundTrip() throws Exception {
        GatewayConfig config = getGatewayConfig();

        TokenStateJournal journal = createTokenStateJournal(config);

        final List<String> tokenIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tokenIds.add(String.valueOf(UUID.randomUUID()));
        }

        Map<String, JournalEntry> journalEntries = new HashMap<>();

        // Verify that the token state has not yet been journaled, and create a JournalEntry for it
        for (String tokenId : tokenIds) {
            assertNull(journal.get(tokenId));

            long issueTime = System.currentTimeMillis();
            long expiration = issueTime + TimeUnit.MINUTES.toMillis(5);
            long maxLifetime = issueTime + (5 * TimeUnit.MINUTES.toMillis(5));
            journalEntries.put(tokenId, createTestJournalEntry(tokenId, issueTime, expiration, maxLifetime));
        }

        for (JournalEntry entry : journalEntries.values()) {
            journal.add(entry);
        }

        for (Map.Entry<String, JournalEntry> journalEntry : journalEntries.entrySet()) {
            final String tokenId = journalEntry.getKey();
            // Get the token state from the journal, and validate its contents
            JournalEntry entry = journal.get(tokenId);
            assertNotNull(entry);

            JournalEntry original = journalEntry.getValue();
            assertEquals(original.getTokenId(), entry.getTokenId());
            assertEquals(original.getIssueTime(), entry.getIssueTime());
            assertEquals(original.getExpiration(), entry.getExpiration());
            assertEquals(original.getMaxLifetime(), entry.getMaxLifetime());
        }

        // Test loading of persisted token state
        List<JournalEntry> loadedEntries = journal.get();
        assertNotNull(loadedEntries);
        assertFalse(loadedEntries.isEmpty());
        assertEquals(10, loadedEntries.size());
        for (JournalEntry loaded : loadedEntries) {
            JournalEntry original = journalEntries.get(loaded.getTokenId());
            assertNotNull(original);
            assertEquals(original.getTokenId(), loaded.getTokenId());
            assertEquals(original.getIssueTime(), loaded.getIssueTime());
            assertEquals(original.getExpiration(), loaded.getExpiration());
            assertEquals(original.getMaxLifetime(), loaded.getMaxLifetime());
        }

        for (String tokenId : tokenIds) {
            journal.remove(tokenId);
            // Verify that the token state can no longer be gotten from the journal
            assertNull(journal.get(tokenId));
        }
    }

    @Test
    public void testGetUnknownToken() throws Exception {
        GatewayConfig config = getGatewayConfig();
        TokenStateJournal journal = createTokenStateJournal(config);
        assertNull(journal.get(UUID.randomUUID().toString()));
    }
}

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

import org.apache.knox.gateway.services.token.state.JournalEntry;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class FileTokenStateJournalTest {

    @Test
    public void testParseJournalEntry() {
        final String tokenId     = UUID.randomUUID().toString();
        final Long   issueTime   = System.currentTimeMillis();
        final Long   expiration  = issueTime + TimeUnit.HOURS.toMillis(1);
        final Long   maxLifetime = TimeUnit.HOURS.toMillis(7);

        doTestParseJournalEntry(tokenId, issueTime, expiration, maxLifetime);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseJournalEntry_MissingMaxLifetime() {
        final String tokenId     = UUID.randomUUID().toString();
        final Long   issueTime   = System.currentTimeMillis();
        final Long   expiration  = issueTime + TimeUnit.HOURS.toMillis(1);
        final Long   maxLifetime = null;

        doTestParseJournalEntry(tokenId, issueTime, expiration, maxLifetime);
    }

    @Test
    public void testParseJournalEntry_MissingIssueTime() {
        final String tokenId     = UUID.randomUUID().toString();
        final Long   issueTime   = System.currentTimeMillis();
        final Long   expiration  = issueTime + TimeUnit.HOURS.toMillis(1);
        final Long   maxLifetime = TimeUnit.HOURS.toMillis(7);

        doTestParseJournalEntry(tokenId, null, expiration, maxLifetime);
    }

    @Test
    public void testParseJournalEntry_MissingIssueAndExpirationTimes() {
        final String tokenId = UUID.randomUUID().toString();
        final Long   maxLifetime = TimeUnit.HOURS.toMillis(7);

        doTestParseJournalEntry(tokenId, null, null, maxLifetime);
    }

    @Test
    public void testParseJournalEntry_OnlyMaxLifetime() {
        final Long maxLifetime = TimeUnit.HOURS.toMillis(7);

        doTestParseJournalEntry(null, null, null, maxLifetime);
    }

    @Test
    public void testParseJournalEntry_AllMissing() {
        doTestParseJournalEntry(null, null, null, " ");
    }

    private void doTestParseJournalEntry(final String tokenId,
                                         final Long   issueTime,
                                         final Long   expiration,
                                         final Long   maxLifetime) {
        doTestParseJournalEntry(tokenId,
                                (issueTime != null ? issueTime.toString() : null),
                                (expiration != null ? expiration.toString() : null),
                                (maxLifetime != null ? maxLifetime.toString() : null));
    }

    private void doTestParseJournalEntry(final String tokenId,
                                         final String issueTime,
                                         final String expiration,
                                         final String maxLifetime) {
        StringBuilder entryStringBuilder =
            new StringBuilder(tokenId != null ? tokenId : "").append(',')
                                                             .append(issueTime != null ? issueTime : "")
                                                             .append(',')
                                                             .append(expiration != null ? expiration : "")
                                                             .append(',')
                                                             .append(maxLifetime != null ? maxLifetime : "");

        JournalEntry entry = FileTokenStateJournal.FileJournalEntry.parse(entryStringBuilder.toString());
        assertNotNull(entry);
        if (tokenId != null && !tokenId.trim().isEmpty()) {
            assertEquals(tokenId, entry.getTokenId());
        } else {
            assertNull(entry.getTokenId());
        }

        if (issueTime != null && !issueTime.trim().isEmpty()) {
            assertEquals(issueTime, entry.getIssueTime());
        } else {
            assertNull(entry.getIssueTime());
        }

        if (expiration != null && !expiration.trim().isEmpty()) {
            assertEquals(expiration, entry.getExpiration());
        } else {
            assertNull(entry.getExpiration());
        }

        if (maxLifetime != null && !maxLifetime.trim().isEmpty()) {
            assertEquals(maxLifetime, entry.getMaxLifetime());
        } else {
            assertNull(entry.getMaxLifetime());
        }
    }


}

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.services.token.state.JournalEntry;
import org.junit.Test;

public class FileTokenStateJournalTest {

    @Test
    public void testParseJournalEntry() {
        final String tokenId     = UUID.randomUUID().toString();
        final Long   issueTime   = System.currentTimeMillis();
        final Long   expiration  = issueTime + TimeUnit.HOURS.toMillis(1);
        final Long   maxLifetime = TimeUnit.HOURS.toMillis(7);

        doTestParseJournalEntry(tokenId, issueTime, expiration, maxLifetime);
    }

    @Test
    public void testParseJournalEntry_MissingMaxLifetime() {
        final String tokenId     = UUID.randomUUID().toString();
        final Long   issueTime   = System.currentTimeMillis();
        final Long   expiration  = issueTime + TimeUnit.HOURS.toMillis(1);
        final Long   maxLifetime = null;

        doTestParseJournalEntry(tokenId, issueTime, expiration, maxLifetime, Boolean.TRUE, "user", null);
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
    public void tesParseTokenMetadata() throws Exception {
      doTestParseJournalEntry("", "", "", "", "", "userName", "");
      doTestParseJournalEntry("", "", "", "", "", "", "comment");
      doTestParseJournalEntry("", "", "", "", "false", "", "");
    }

    @Test
    public void testParseJournalEntry_AllMissing() {
        doTestParseJournalEntry(null, null, null, " ", null, null, null);
    }

    private void doTestParseJournalEntry(final String tokenId, final Long issueTime, final Long expiration, final Long maxLifetime) {
      doTestParseJournalEntry(tokenId, issueTime, expiration, maxLifetime, null, null, null);
    }

    private void doTestParseJournalEntry(final String tokenId,
                                         final Long   issueTime,
                                         final Long   expiration,
                                         final Long   maxLifetime,
                                         final Boolean enabled,
                                         final String userName,
                                         final String comment) {
        doTestParseJournalEntry(tokenId,
                                (issueTime != null ? issueTime.toString() : null),
                                (expiration != null ? expiration.toString() : null),
                                (maxLifetime != null ? maxLifetime.toString() : null),
                                (enabled != null ? enabled.toString() : null),
                                userName, comment);
    }

    private void doTestParseJournalEntry(final String tokenId,
                                         final String issueTime,
                                         final String expiration,
                                         final String maxLifetime,
                                         final String enabled,
                                         final String userName,
                                         final String comment) {
        StringBuilder entryStringBuilder =
            new StringBuilder(tokenId != null ? tokenId : "").append(',')
                                                             .append(issueTime != null ? issueTime : "")
                                                             .append(',')
                                                             .append(expiration != null ? expiration : "")
                                                             .append(',')
                                                             .append(maxLifetime != null ? maxLifetime : "")
                                                             .append(",").append(enabled != null ? enabled : "")
                                                             .append(",").append(userName == null ? "" : userName)
                                                             .append(",").append(comment == null ? "" : comment);

        JournalEntry entry = FileTokenStateJournal.FileJournalEntry.parse(entryStringBuilder.toString());
        assertNotNull(entry);
        assertJournalEntryField(tokenId, entry.getTokenId());
        assertJournalEntryField(issueTime, entry.getIssueTime());
        assertJournalEntryField(expiration, entry.getExpiration());
        assertJournalEntryField(maxLifetime, entry.getMaxLifetime());
        assertJournalEntryField(StringUtils.isBlank(enabled) ? "false" : enabled, String.valueOf(entry.getTokenMetadata().isEnabled()));
        assertJournalEntryField(userName, entry.getTokenMetadata().getUserName());
        assertJournalEntryField(comment, entry.getTokenMetadata().getComment());
    }

    private void assertJournalEntryField(String received, String parsed) {
      if (received != null && !received.trim().isEmpty()) {
        assertEquals(received, parsed);
      } else {
        assertNull(parsed);
      }
    }


}

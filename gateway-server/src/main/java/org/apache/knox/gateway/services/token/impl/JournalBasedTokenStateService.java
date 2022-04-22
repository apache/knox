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

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.token.impl.state.TokenStateJournalFactory;
import org.apache.knox.gateway.services.token.state.JournalEntry;
import org.apache.knox.gateway.services.token.state.TokenStateJournal;
import org.apache.knox.gateway.util.Tokens;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JournalBasedTokenStateService extends AbstractPersistentTokenStateService {

    private TokenStateJournal journal;

    @Override
    public void init(final GatewayConfig config, final Map<String, String> options) throws ServiceLifecycleException {
        super.init(config, options);

        try {
            // Initialize the token state journal
            journal = TokenStateJournalFactory.create(config);

            // Load any persisted journal entries, and add them to the in-memory collection
            List<JournalEntry> entries = journal.get();
            for (JournalEntry entry : entries) {
                String id = entry.getTokenId();
                try {
                    long issueTime   = Long.parseLong(entry.getIssueTime());
                    long expiration  = Long.parseLong(entry.getExpiration());
                    long maxLifetime = Long.parseLong(entry.getMaxLifetime());

                    // Add the token state to memory
                    super.addToken(id, issueTime, expiration, maxLifetime);

                } catch (Exception e) {
                    log.failedToLoadJournalEntry(Tokens.getTokenIDDisplayText(id), e);
                }
            }
        } catch (IOException e) {
            throw new ServiceLifecycleException("Failed to load persisted state from the token state journal", e);
        }
    }

    @Override
    public void addToken(final String tokenId, long issueTime, long expiration, long maxLifetimeDuration) {
        super.addToken(tokenId, issueTime, expiration, maxLifetimeDuration);

        try {
            journal.add(tokenId, issueTime, expiration, maxLifetimeDuration, null);
        } catch (IOException e) {
            log.failedToAddJournalEntry(Tokens.getTokenIDDisplayText(tokenId), e);
        }
    }

    @Override
    public long getTokenIssueTime(String tokenId) throws UnknownTokenException {
      try {
        // Check the in-memory collection first, to avoid file access when possible
        return super.getTokenIssueTime(tokenId);
      } catch (UnknownTokenException e) {
        // It's not in memory
      }

      validateToken(tokenId);

      // If there is no associated state in the in-memory cache, proceed to check the journal
      long issueTime = 0;
      try {
        JournalEntry entry = journal.get(tokenId);
        if (entry == null) {
          throw new UnknownTokenException(tokenId);
        }

        issueTime = Long.parseLong(entry.getIssueTime());
      } catch (IOException e) {
        log.failedToLoadJournalEntry(e);
      }

      return issueTime;
    }

    @Override
    public long getTokenExpiration(final String tokenId, boolean validate) throws UnknownTokenException {
        // Check the in-memory collection first, to avoid file access when possible
        try {
            // If the token identifier is valid, and the associated state is available from the in-memory cache, then
            // return the expiration from there.
            return super.getTokenExpiration(tokenId, validate);
        } catch (UnknownTokenException e) {
            // It's not in memory
        }

        if (validate) {
            validateToken(tokenId);
        }

        // If there is no associated state in the in-memory cache, proceed to check the journal
        long expiration = 0;
        try {
            JournalEntry entry = journal.get(tokenId);
            if (entry == null) {
                throw new UnknownTokenException(tokenId);
            }

            expiration = Long.parseLong(entry.getExpiration());
            super.addToken(tokenId,
                           Long.parseLong(entry.getIssueTime()),
                           expiration,
                           Long.parseLong(entry.getMaxLifetime()));
        } catch (IOException e) {
            log.failedToLoadJournalEntry(e);
        }

        return expiration;
    }

    @Override
    protected long getMaxLifetime(final String tokenId) {
        long result = super.getMaxLifetime(tokenId);

        // If there is no result from the in-memory collection, proceed to check the journal
        if (result < 1L) {
            try {
                JournalEntry entry = journal.get(tokenId);
                if (entry == null) {
                    throw new UnknownTokenException(tokenId);
                }
                result = Long.parseLong(entry.getMaxLifetime());
                super.setMaxLifetime(tokenId, Long.parseLong(entry.getIssueTime()), result);
            } catch (Exception e) {
                log.failedToLoadJournalEntry(e);
            }
        }
        return result;
    }

    @Override
    protected void removeTokens(final Set<String> tokenIds) {
        super.removeTokens(tokenIds);
        try {
            journal.remove(tokenIds);
        } catch (IOException e) {
            log.failedToRemoveJournalEntries(e);
        }
    }

    @Override
    protected void updateExpiration(final String tokenId, long expiration) {
        super.updateExpiration(tokenId, expiration);
        try {
            JournalEntry entry = journal.get(tokenId);
            if (entry == null) {
                log.journalEntryNotFound(Tokens.getTokenIDDisplayText(tokenId));
            } else {
                // Adding will overwrite the existing journal entry, thus updating it with the new expiration
                journal.add(entry.getTokenId(),
                            Long.parseLong(entry.getIssueTime()),
                            expiration,
                            Long.parseLong(entry.getMaxLifetime()),
                            entry.getTokenMetadata());
            }
        } catch (IOException e) {
            log.errorAccessingTokenState(e);
        }
    }

    @Override
    protected boolean isUnknown(final String tokenId) {
        JournalEntry entry = null;
        try {
            entry = journal.get(tokenId);
        } catch (IOException e) {
            log.errorAccessingTokenState(e);
        }

        return (entry == null);
    }

  @Override
  public void addMetadata(String tokenId, TokenMetadata metadata) {
    super.addMetadata(tokenId, metadata);
    try {
      JournalEntry entry = journal.get(tokenId);
      if (entry == null) {
        log.journalEntryNotFound(Tokens.getTokenIDDisplayText(tokenId));
      } else {
        journal.add(entry.getTokenId(), Long.parseLong(entry.getIssueTime()), Long.parseLong(entry.getExpiration()), Long.parseLong(entry.getMaxLifetime()), metadata);
      }
    } catch (IOException e) {
      log.errorAccessingTokenState(e);
    }
  }
}

/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements. See the NOTICE file distributed with this
 *  * work for additional information regarding copyright ownership. The ASF
 *  * licenses this file to you under the Apache License, Version 2.0 (the
 *  * "License"); you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations under
 *  * the License.
 *
 */
package org.apache.knox.gateway.services.token.state;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.knox.gateway.services.security.token.TokenMetadata;

/**
 *
 */
public interface TokenStateJournal {

    /**
     * Persist the token state to the journal.
     *
     * @param tokenId     The unique token identifier
     * @param issueTime   The issue timestamp
     * @param expiration  The expiration time
     * @param maxLifetime The maximum allowed lifetime
     * @param tokenMetafata The associated token metadata
     *
     * @throws IOException exception on error
     */
    void add(String tokenId, long issueTime, long expiration, long maxLifetime, TokenMetadata tokenMetadata)
        throws IOException;

    /**
     * Persist the token state to the journal.
     *
     * @param entry The entry to persist
     *
     * @throws IOException exception on error
     */
    void add(JournalEntry entry) throws IOException;

    /**
     * Persist the token state to the journal.
     *
     * @param entries The entries to persist
     *
     * @throws IOException exception on error
     */
    void add(List<JournalEntry> entries) throws IOException;

    /**
     * Get the journaled state for the specified token identifier.
     *
     * @param tokenId The unique token identifier.
     *
     * @throws IOException exception on error
     *
     * @return A JournalEntry with the specified token's journaled state.
     */
    JournalEntry get(String tokenId) throws IOException;

    /**
     * Get all the the journaled tokens' state.
     *
     * @throws IOException exception on error
     *
     * @return A List of JournalEntry objects.
     */
    List<JournalEntry> get() throws IOException;

    /**
     * Remove the token state for the specified token from the journal
     *
     * @param tokenId The unique token identifier
     *
     * @throws IOException exception on error
     */
    void remove(String tokenId) throws IOException;

    /**
     * Remove the token state for the specified tokens from the journal
     *
     * @param tokenIds A set of unique token identifiers
     *
     * @throws IOException exception on error
     */
    void remove(Collection<String> tokenIds) throws IOException;

    /**
     * Remove the token state for the specified journal entry
     *
     * @param entry A JournalEntry for the token for which the state should be removed
     *
     * @throws IOException exception on error
     */
    void remove(JournalEntry entry) throws IOException;

}

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
import org.apache.knox.gateway.services.token.state.JournalEntry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A TokenStateJournal implementation that manages separate files for token state.
 */
class MultiFileTokenStateJournal extends FileTokenStateJournal {

    // File extension for journal entry files
    static final String ENTRY_FILE_EXT = ".ts";

    // Filter used when listing all journal entry files in the journal directory
    static final String ENTRY_FILE_EXT_FILTER = "*" + ENTRY_FILE_EXT;

    MultiFileTokenStateJournal(GatewayConfig config) throws IOException {
        super(config);
    }

    @Override
    public void add(final String tokenId, long issueTime, long expiration, long maxLifetime) throws IOException {
        add(Collections.singletonList(new FileJournalEntry(tokenId, issueTime, expiration, maxLifetime)));
    }

    @Override
    public void add(final List<JournalEntry> entries) throws IOException {
        // Persist each journal entry as an individual file in the journal directory
        for (JournalEntry entry : entries) {
            final Path entryFile = journalDir.resolve(entry.getTokenId() + ENTRY_FILE_EXT);
            log.persistingJournalEntry(entryFile.toString());
            try (FileChannel fileChannel = FileChannel.open(entryFile, StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                fileChannel.lock();
                try (OutputStream out = Channels.newOutputStream(fileChannel)) {
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
                    writer.write(entry.toString());
                    writer.newLine();
                    writer.flush();
                }
                log.addedJournalEntry(entry.getTokenId());
            } catch (IOException e){
                log.failedToPersistJournalEntry(entry.getTokenId(), e);
                throw e;
            }
        }
    }

    @Override
    public JournalEntry get(final String tokenId) throws IOException {
        JournalEntry result = null;

        Path entryFilePath = journalDir.resolve(tokenId + ENTRY_FILE_EXT);
        if (Files.exists(entryFilePath)) {
            try (FileChannel fileChannel = FileChannel.open(entryFilePath, StandardOpenOption.READ)) {
                fileChannel.lock(0L, Long.MAX_VALUE, true);
                List<FileJournalEntry> entries = loadJournal(fileChannel);
                if (entries.isEmpty()) {
                    log.journalEntryNotFound(tokenId);
                } else {
                    result = entries.get(0);
                }
            }
        } else {
            log.journalEntryNotFound(tokenId);
        }

        return result;
    }

    @Override
    public void remove(final Collection<String> tokenIds) throws IOException {
        // Remove the journal entry files corresponding to the specified token identifiers
        for (String tokenId : tokenIds) {
            Path entryFilePath = journalDir.resolve(tokenId + ENTRY_FILE_EXT);
            if (Files.exists(entryFilePath)) {
                Files.delete(entryFilePath);
                log.removedJournalEntry(tokenId);
            }
        }
    }

    @Override
    protected List<JournalEntry> loadJournal() throws IOException {
        List<JournalEntry> entries = new ArrayList<>();

        // List all the journal entry files in the directory, and create journal entries for them
        if (Files.exists(journalDir)) {
            log.loadingPersistedJournalEntries();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(journalDir, ENTRY_FILE_EXT_FILTER)) {
                for (Path entryFilePath : stream ) {
                    try (FileChannel fileChannel = FileChannel.open(entryFilePath, StandardOpenOption.READ)) {
                        fileChannel.lock(0L, Long.MAX_VALUE, true);
                        entries.addAll(loadJournal(fileChannel));
                        if (entries.isEmpty()) {
                            log.emptyJournalEntry(entryFilePath.toString());
                        } else {
                            // Should only be a single entry for this implementation
                            log.loadedPersistedJournalEntry(entries.get(0).getTokenId());
                        }
                    }
                }
            }
        }

        return entries;
    }
}

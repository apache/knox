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
package org.apache.knox.gateway.services.ldap;

import org.apache.directory.api.ldap.model.cursor.ListCursor;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursorImpl;
import org.apache.directory.server.core.api.interceptor.BaseInterceptor;
import org.apache.directory.server.core.api.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.backend.LdapBackend;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interceptor for LDAP operations to proxy user searches to backend when not found locally
 */
public class GroupLookupInterceptor extends BaseInterceptor {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);
    private DirectoryService directoryService;
    private LdapBackend backend;
    private static final Pattern UID_PATTERN = Pattern.compile(".*\\(uid=([^)]+)\\).*");
    private static final Pattern CN_PATTERN = Pattern.compile(".*\\(cn=([^)]+)\\).*");

    public GroupLookupInterceptor(DirectoryService directoryService, LdapBackend backend) {
        this.directoryService = directoryService;
        this.backend = backend;
    }

    @Override
    public EntryFilteringCursor search(SearchOperationContext ctx) throws LdapException {
        String filter = ctx.getFilter() != null ? ctx.getFilter().toString() : "";
        String baseDn = ctx.getDn() != null ? ctx.getDn().toString() : "";

        LOG.ldapSearch(baseDn, filter);

        // First try the normal search
        EntryFilteringCursor originalResults;
        try {
            originalResults = next(ctx);
        } catch (Exception e) {
            throw new LdapException(e);
        }

        // Check if this is a user search and if we got no results, try the backend
        if (isUserSearch(filter)) {
            String username = extractUser(filter);

            // Check if we have any results from local search
            List<Entry> entries = new ArrayList<>();
            try {
                while (originalResults.next()) {
                    entries.add(originalResults.get());
                }
                originalResults.close();
            } catch (Exception e) {
                // If we get an error or no results, try the backend
            }

            // If no local results, try backend
            if (entries.isEmpty() && username != null) {
                try {
                    SchemaManager schemaManager = directoryService.getSchemaManager();

                    if (username.contains("*")) {
                        // Wildcard search - use searchUsers
                        LOG.ldapSearch(baseDn, "wildcard user search: " + username);
                        List<Entry> backendEntries = backend.searchUsers(username, schemaManager);

                        // Return backend results directly without caching to avoid deadlock
                        // (caching during an active search can cause ApacheDS locking issues)
                        entries.addAll(backendEntries);
                    } else {
                        // Specific user lookup
                        LOG.ldapUserLoaded(username);
                        Entry backendEntry = backend.getUser(username, schemaManager);

                        if (backendEntry != null) {
                            // Return backend result directly without caching
                            entries.add(backendEntry);
                        }
                    }
                } catch (Exception e) {
                    LOG.ldapServiceStopFailed(e);
                }
            }

            // Return cursor with our results - use a simple approach
            return new EntryFilteringCursorImpl(new ListCursor<>(entries), ctx, directoryService.getSchemaManager());
        }

        return originalResults;
    }

    @Override
    public void bind(BindOperationContext ctx) {
        // Allow anonymous bind or simple bind
        LOG.ldapBind(ctx.getDn() != null ? ctx.getDn().toString() : "anonymous");
        try {
            next(ctx);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isUserSearch(String filter) {
        return UID_PATTERN.matcher(filter).matches() || CN_PATTERN.matcher(filter).matches();
    }

    private String extractUser(String filter) {
        Matcher uidMatcher = UID_PATTERN.matcher(filter);
        if (uidMatcher.matches()) {
            return uidMatcher.group(1);
        }

        Matcher cnMatcher = CN_PATTERN.matcher(filter);
        if (cnMatcher.matches()) {
            return cnMatcher.group(1);
        }

        return null;
    }
}


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
package org.apache.knox.gateway.services.ldap.interceptor;

import org.apache.directory.api.ldap.model.constants.AuthenticationLevel;
import org.apache.directory.api.ldap.model.cursor.ListCursor;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.server.core.api.CoreSession;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.LdapPrincipal;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursorImpl;
import org.apache.directory.server.core.api.interceptor.BaseInterceptor;
import org.apache.directory.server.core.api.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.api.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LdapMessages;
import org.apache.knox.gateway.services.ldap.LdapUtils;
import org.apache.knox.gateway.services.ldap.backend.BackendFactory;
import org.apache.knox.gateway.services.ldap.backend.LdapBackend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interceptor for LDAP operations to proxy user searches to backends when not found locally
 */
public class UserSearchInterceptor extends BaseInterceptor {

    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);
    private static final Pattern UID_PATTERN = Pattern.compile(".*\\(uid=([^)]+)\\).*");
    private static final Pattern CN_PATTERN = Pattern.compile(".*\\(cn=([^)]+)\\).*");
    private static final Pattern SAMAACCOUNTNAME_PATTERN = Pattern.compile(".*\\(sAMAccountName=([^)]+)\\).*");

    private final LdapBackend backend;

    public UserSearchInterceptor(String name, Map<String, String> config) throws Exception {
        super(name);
        backend = BackendFactory.createBackend(name, config);
    }

    public LdapBackend getBackend() {
        return backend;
    }

    @Override
    public void init(DirectoryService directoryService) throws LdapException {
        super.init(directoryService);
    }

    @Override
    public Entry lookup(LookupOperationContext ctx) throws LdapException {
        Entry entry = next(ctx);
        if (entry == null) {
            String username = LdapUtils.extractUsernameFromDn(ctx.getDn());
            if (username != null) {
                try {
                    entry = backend.getUser(username, directoryService.getSchemaManager());
                } catch (Exception e) {
                    LOG.ldapServiceStopFailed(e);
                }
            }
        }
        return entry;
    }

    @Override
    public EntryFilteringCursor search(SearchOperationContext ctx) throws LdapException {
        String filter = ctx.getFilter() != null ? ctx.getFilter().toString() : "";
        String baseDn = ctx.getDn() != null ? ctx.getDn().toString() : "";

        LOG.ldapSearch(baseDn, filter);

        // First execute the next interceptor in the chain
        EntryFilteringCursor originalResults;
        originalResults = next(ctx);

        // Check if this is a user search and call the backends
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
                // If we get an error or no results, try the backends
            }

            if (username != null) {
                try {
                    if (username.contains("*")) {
                        // Wildcard search - use searchUsers
                        LOG.ldapSearch(baseDn, "wildcard user search: " + username);
                        // Return backend results directly without caching to avoid deadlock
                        // (caching during an active search can cause ApacheDS locking issues)
                        entries.addAll(backend.searchUsers(username, schemaManager));
                    } else {
                        // if no results, perform single-user search
                        if (entries.isEmpty()) {
                            // Specific user lookup
                            Entry backendEntry = backend.getUser(username, schemaManager);
                            LOG.ldapUserLoaded(username);

                            if (backendEntry != null) {
                                // Return backend result directly without caching
                                entries.add(backendEntry);
                                LOG.ldapUserEntry(backendEntry.toString());
                            } else {
                                LOG.ldapUserNull(username);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.ldapSearchFailed(baseDn, filter, e);
                }
            }

            // Return cursor with our results - use a simple approach
            return new EntryFilteringCursorImpl(new ListCursor<>(entries), ctx, schemaManager);
        }

        return originalResults;
    }

    @Override
    public void bind(BindOperationContext ctx) throws LdapException {
        LOG.ldapBind(ctx.getDn() != null ? ctx.getDn().toString() : "anonymous");

        // Try backend first for non-system users
        if (ctx.getDn() != null && !ctx.getDn().toString().endsWith("ou=system")) {
            byte[] credentials = ctx.getCredentials();
            if (credentials != null) {
                String password = new String(credentials, java.nio.charset.StandardCharsets.UTF_8);
                if (backend.authenticate(ctx.getDn(), password)) {
                    // Create session for the authenticated user and set it in context
                    // This is required by LdapServer to avoid NullPointerException
                    LdapPrincipal principal = new LdapPrincipal(directoryService.getSchemaManager(), ctx.getDn(), AuthenticationLevel.SIMPLE);
                    CoreSession session = directoryService.getSession(principal);
                    ctx.setSession(session);
                    return; // Successfully authenticated via backend, bypass local check
                }
            }
        }

        try {
            next(ctx);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isUserSearch(String filter) {
        return UID_PATTERN.matcher(filter).matches()
                || CN_PATTERN.matcher(filter).matches()
                || SAMAACCOUNTNAME_PATTERN.matcher(filter).matches();
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

        Matcher samaaccountnameMatcher = SAMAACCOUNTNAME_PATTERN.matcher(filter);
        if (samaaccountnameMatcher.matches()) {
            return samaaccountnameMatcher.group(1);
        }

        return null;
    }
}


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

import static java.util.Locale.ROOT;

/**
 * Interceptor for LDAP operations to proxy user searches to backends when not found locally
 */
public class UserSearchInterceptor extends BaseInterceptor {

    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    private final LdapBackend backend;

    public UserSearchInterceptor(String name, Map<String, String> config) throws Exception {
        this(name, BackendFactory.createBackend(name, config));
    }

    protected UserSearchInterceptor(String name, LdapBackend backend) {
        super(name);
        this.backend = backend;
    }

    public LdapBackend getBackend() {
        return backend;
    }

    @Override
    public Entry lookup(LookupOperationContext ctx) throws LdapException {
        Entry entry = next(ctx);
        if (entry == null) {
            String username = LdapUtils.extractUsernameFromDn(ctx.getDn());
            if (username != null) {
                try {
                    entry = backend.getUser(username, schemaManager);
                } catch (Exception e) {
                    LOG.ldapLookupFailed(ctx.getDn().toString(),e);
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

        List<Entry> entries = new ArrayList<>();

        // First execute the next interceptor in the chain
        try (EntryFilteringCursor originalResults = next(ctx)){
            while (originalResults.next()) {
                entries.add(originalResults.get());
            }
        } catch (Exception e) {
            // If we get an error or no results, try the backends
        }

        // Only forward to the backend when the search base is under the backend's namespace.
        // System/operational searches (ou=schema, cn=config, root-DSE) must not be forwarded.
        if (isUnderBackendBaseDn(baseDn)) {
            try {
                entries.addAll(backend.search(baseDn, ctx.getScope(), filter, schemaManager));
            } catch (Exception e) {
                LOG.ldapSearchFailed(baseDn, filter, e);
            }
        }

        // Return cursor with our results - use a simple approach
        return new EntryFilteringCursorImpl(new ListCursor<>(entries), ctx, schemaManager);
    }

    private boolean isUnderBackendBaseDn(String searchBase) {
        final String backendBase = backend.getBaseDn();
        if (searchBase == null || searchBase.isEmpty() || backendBase == null || backendBase.isEmpty()) {
            return false;
        }
        return searchBase.toLowerCase(ROOT).endsWith(backendBase.toLowerCase(ROOT));
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
}


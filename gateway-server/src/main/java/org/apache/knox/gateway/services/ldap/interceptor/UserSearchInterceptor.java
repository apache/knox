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
import org.apache.directory.api.ldap.model.constants.SchemaConstants;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.ListCursor;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapNoSuchObjectException;
import org.apache.directory.api.ldap.model.exception.LdapOperationException;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.ObjectClassNode;
import org.apache.directory.api.ldap.model.message.ResultCodeEnum;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.AttributeType;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Interceptor for LDAP operations to proxy user searches to backends when not found locally
 */
public class UserSearchInterceptor extends BaseInterceptor {

    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    private final LdapBackend backend;

    // The in-memory bind user (gateway.ldap.bind.user) is authenticated locally by the
    // InMemoryBindAuthenticator, so its bind must never be forwarded to the backend.
    // Set once, before the directory service starts.
    private Dn localBindDn;

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

    public Dn getLocalBindDn() {
        return localBindDn;
    }

    public void setLocalBindDn(Dn localBindDn) {
        this.localBindDn = localBindDn;
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
                    throw new LdapOperationException(ResultCodeEnum.OTHER, "Lookup request to backend " + getName() + " failed.", e);
                }
            }
        }
        return entry;
    }

    @Override
    public EntryFilteringCursor search(SearchOperationContext ctx) throws LdapException {

        String filter = filterString(ctx.getFilter());
        String baseDn = ctx.getDn() != null ? ctx.getDn().toString() : "";
        String[] returningAttributes = getReturningAttributes(ctx);

        LOG.ldapSearch(baseDn, filter, Arrays.toString(returningAttributes));

        // Only forward to the backend when the search base is under the backend's namespace.
        // System/operational searches (ou=schema, cn=config, root-DSE) must not be forwarded.
        final boolean backendSearchBase = backend.isSupportedSearchBase(baseDn);

        List<Entry> entries = new ArrayList<>();
        LdapNoSuchObjectException notFoundLocally = null;

        // First execute the next interceptor in the chain
        try (EntryFilteringCursor originalResults = next(ctx)){
            while (originalResults.next()) {
                entries.add(originalResults.get());
            }
        } catch (LdapNoSuchObjectException e) {
            // Entries served by the backend (e.g. uid=alice,ou=people,<baseDn>) do not exist in the
            // local partition, so a search rooted at one of them fails there; let the backend answer it.
            if (!backendSearchBase) {
                throw e;
            }
            LOG.ldapSearchBaseNotFoundLocally(baseDn, getName());
            notFoundLocally = e;
        } catch (CursorException e ) {
            throw new LdapException(e.getMessage(), e);
        } catch (IOException e) {
            // swallow error closing cursor
        }

        if (backendSearchBase) {
            try {
                entries.addAll(backend.search(baseDn, ctx.getScope(), filter, schemaManager, returningAttributes));
            } catch (LdapNoSuchObjectException e) {
                // The search base does not exist in the backend; it is an error only if it does not exist locally either
                if (notFoundLocally != null) {
                    throw notFoundLocally;
                }
                LOG.ldapSearchBaseNotFoundInBackend(baseDn, getName());
            } catch (Exception e) {
                LOG.ldapSearchFailed(getName(), baseDn, filter, e);
                throw new LdapOperationException(ResultCodeEnum.OTHER, "Search request to backend " + getName() + " failed.", e);
            }
        }

        // Return cursor with our results - use a simple approach
        return new EntryFilteringCursorImpl(new ListCursor<>(entries), ctx, schemaManager);
    }

    private String[] getReturningAttributes(SearchOperationContext ctx) {
        if (ctx.isNoAttributes()) {
            return SchemaConstants.NO_ATTRIBUTE_ARRAY;
        }
        List<String> returningAttributes = new ArrayList<>();
        if (ctx.getReturningAttributes() != null) {
            Arrays.stream(ctx.getReturningAttributesString()).forEach(oid -> {
                AttributeType attributeType = schemaManager.getAttributeType(oid);
                if (attributeType != null) {
                    // prefer to use the attribute name when calling backend
                    returningAttributes.add(attributeType.getName());
                } else {
                    // use oid if attribute name not found
                    returningAttributes.add(oid);
                }
            });
        }
        if (ctx.isAllUserAttributes()) {
            returningAttributes.add("*");
        }
        if (ctx.isAllOperationalAttributes()) {
            returningAttributes.add("+");
        }
        return returningAttributes.toArray(new String[0]);
    }

    @Override
    public void bind(BindOperationContext ctx) throws LdapException {
        final Dn bindDn = ctx.getDn();
        LOG.ldapBind(bindDn != null && !bindDn.isEmpty() ? bindDn.toString() : "anonymous");

        if (isLocalBindUser(bindDn)) {
            // Skip the backend: a remote bind would fail anyway and expose the gateway bind credential to it
            LOG.ldapLocalBindUser(bindDn.toString(), getName());
        } else if (bindDn != null && !bindDn.isEmpty() && !bindDn.toString().endsWith("ou=system")) {
            // Try backend first for non-system users
            byte[] credentials = ctx.getCredentials();
            if (credentials != null && credentials.length > 0) {
                String password = new String(credentials, java.nio.charset.StandardCharsets.UTF_8);
                if (backend.authenticate(bindDn, password)) {
                    // Create session for the authenticated user and set it in context
                    // This is required by LdapServer to avoid NullPointerException
                    LdapPrincipal principal = new LdapPrincipal(directoryService.getSchemaManager(), bindDn, AuthenticationLevel.SIMPLE);
                    CoreSession session = directoryService.getSession(principal);
                    ctx.setSession(session);
                    return; // Successfully authenticated via backend, bypass local check
                }
            }
        }

        next(ctx);
    }

    private static String filterString(ExprNode filter) {
        if (filter == null) {
            return "";
        }
        // ApacheDS replaces (objectClass=*) with an ObjectClassNode, which does not print as a filter ("All")
        return filter instanceof ObjectClassNode ? "(objectClass=*)" : filter.toString();
    }

    private boolean isLocalBindUser(Dn bindDn) {
        return localBindDn != null && localBindDn.equals(bindDn);
    }
}

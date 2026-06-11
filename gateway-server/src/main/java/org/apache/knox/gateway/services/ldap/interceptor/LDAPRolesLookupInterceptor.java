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

import org.apache.commons.lang3.StringUtils;
import org.apache.directory.api.ldap.model.cursor.ListCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapInvalidDnException;
import org.apache.directory.api.ldap.model.message.Control;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursorImpl;
import org.apache.directory.server.core.api.interceptor.BaseInterceptor;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LDAPRolesLookupService;
import org.apache.knox.gateway.services.ldap.LdapMessages;
import org.apache.knox.gateway.services.ldap.LdapUtils;
import org.apache.knox.gateway.services.ldap.control.RolesLookupBypassControl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Interceptor that replaces group names in memberOf attributes with role names
 * if LDAP roles lookup is enabled.
 */
public class LDAPRolesLookupInterceptor extends BaseInterceptor {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    private final LDAPRolesLookupService rolesLookupService;
    private final String rolesLookupBypassControlOid;

    public LDAPRolesLookupInterceptor(LDAPRolesLookupService rolesLookupService, String rolesLookupBypassControlOid) {
        this.rolesLookupService = rolesLookupService;
        this.rolesLookupBypassControlOid = rolesLookupBypassControlOid;
    }

    @Override
    public EntryFilteringCursor search(SearchOperationContext ctx) throws LdapException {
        if (StringUtils.isNotBlank(rolesLookupBypassControlOid)) {
            if (ctx.hasRequestControl(rolesLookupBypassControlOid)) {
                Control control = ctx.getRequestControl(rolesLookupBypassControlOid);
                if (control instanceof RolesLookupBypassControl) {
                    RolesLookupBypassControl rolesLookupBypassControl = (RolesLookupBypassControl) control;
                    if (rolesLookupBypassControl.isBypassRolesLookup()) {
                        return next(ctx);
                    }
                }
            }
        }

        final List<Entry> entries = new ArrayList<>();
        try (EntryFilteringCursor cursor = next(ctx)) {
            while (cursor.next()) {
                entries.add(cursor.get());
            }
        } catch (Exception e) {
            LOG.ldapRolesLookupFailed(LdapUtils.extractGroupName(ctx.getDn()), e);
            throw new LdapException(e);
        }

        if (!entries.isEmpty()) {
            for (Entry entry : entries) {
                try {
                    final String username = LdapUtils.extractUsernameFromEntry(entry, "uid", "cn");
                    final Set<String> groups = fetchGroups(entry);
                    final Collection<String> roles = rolesLookupService.lookupRoles(username, groups);
                    modifyEntry(entry, roles);
                } catch (Exception e) {
                    LOG.ldapRolesLookupFailed("Error while updating entry with roles lookup results", e);
                    throw new LdapException(e);
                }
            }
        }

        return new EntryFilteringCursorImpl(new ListCursor<>(entries), ctx, ctx.getSession().getDirectoryService().getSchemaManager());
    }

    private Set<String> fetchGroups(final Entry entry) {
        final Set<String> groups = new HashSet<>();
        final Attribute memberOf = entry.get("memberOf");
        if (memberOf != null) {
            for (Value value : memberOf) {
                try {
                    Dn groupDn = new Dn(value.getString());
                    String groupName = LdapUtils.extractGroupName(groupDn);
                    if (groupName != null) {
                        groups.add(groupName);
                    }
                } catch (LdapInvalidDnException ignore) {
                }
            }
        }
        return groups;
    }

    Entry modifyEntry(Entry entry, Collection<String> roles) throws LdapException {
        if (entry != null) {
            final Attribute memberOfAttr = entry.get("memberOf");
            final List<Dn> groupDns = new ArrayList<>();
            if (memberOfAttr != null) {
                for (Value value : memberOfAttr) {
                    groupDns.add(new Dn(value.getString()));
                }
            }

            // Only modify if there are existing attributes to wipe or new roles to add
            if (memberOfAttr != null || (roles != null && !roles.isEmpty())) {
                updateMemberOfAttributes(entry, roles, groupDns);
            }
        }
        return entry;
    }

    private void updateMemberOfAttributes(Entry entry, Collection<String> roles, List<Dn> groupDns) throws LdapException {
        // Always wipe the old attributes if role lookup is active
        entry.removeAttributes("memberOf");

        if (!roles.isEmpty()) {
            Dn templateDn = groupDns.isEmpty() ? null : groupDns.get(0);
            for (String role : roles) {
                addRoleAttribute(entry, role, templateDn);
            }
        }
    }

    private void addRoleAttribute(Entry entry, String role, Dn templateDn) throws LdapException {
        if (templateDn != null) {
            // Create a new DN by replacing the CN of the template DN
            List<Rdn> rdns = new ArrayList<>(templateDn.getRdns());
            if (!rdns.isEmpty() && rdns.get(0).getType().equalsIgnoreCase("cn")) {
                rdns.set(0, new Rdn("cn", role));
                Dn roleDn = new Dn(rdns.toArray(new Rdn[0]));
                entry.add("memberOf", roleDn.getName());
                return;
            }
        }
        entry.add("memberOf", "cn=" + role);
    }

}

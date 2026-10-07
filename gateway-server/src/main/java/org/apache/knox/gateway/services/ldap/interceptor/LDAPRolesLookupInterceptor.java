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

import org.apache.directory.api.ldap.model.cursor.ListCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultAttribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapInvalidDnException;
import org.apache.directory.api.ldap.model.message.Control;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.api.ldap.model.schema.AttributeType;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursorImpl;
import org.apache.directory.server.core.api.interceptor.BaseInterceptor;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LDAPRolesLookupService;
import org.apache.knox.gateway.services.ldap.LdapMessages;
import org.apache.knox.gateway.services.ldap.LdapUtils;
import org.apache.knox.gateway.services.ldap.control.RolesLookupBypassControl;
import org.apache.knox.gateway.services.ldap.model.constants.SchemaConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interceptor that replaces group names in memberOf attributes with role names
 * if LDAP roles lookup is enabled.
 */
public class LDAPRolesLookupInterceptor extends BaseInterceptor {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    /**
     * Attributes the interceptor must be able to read to do its job, regardless of what the client
     * requested. Each entry is added to the backend search when the client omitted it, and stripped
     * back out afterwards. The list is owned by the interceptor rather than being configurable: an
     * attribute only belongs here if code in this class actually consumes it (today, uid keys the role
     * lookup), so the augment set and the code that reads it stay in lockstep. To have the lookup use a
     * new attribute (e.g. objectClass to distinguish user vs group entries), add it here and the code
     * that consumes it.
     */
    private static final List<String> REQUIRED_ATTRIBUTES = Arrays.asList("uid", "objectClass");

    private final LDAPRolesLookupService rolesLookupService;

    public LDAPRolesLookupInterceptor(LDAPRolesLookupService rolesLookupService) {
        this.rolesLookupService = rolesLookupService;
    }

    @Override
    public EntryFilteringCursor search(SearchOperationContext ctx) throws LdapException {
        if (ctx.hasRequestControl(SchemaConstants.ROLES_LOOKUP_BYPASS_CONTROL_OID)) {
            Control control = ctx.getRequestControl(SchemaConstants.ROLES_LOOKUP_BYPASS_CONTROL_OID);
            if (control instanceof RolesLookupBypassControl) {
                RolesLookupBypassControl rolesLookupBypassControl = (RolesLookupBypassControl) control;
                if (rolesLookupBypassControl.isBypassRolesLookup()) {
                    return next(ctx);
                }
            }
        }

        final SchemaManager schemaManager = ctx.getSession().getDirectoryService().getSchemaManager();

        // The role lookup must be keyed on the stable user id, which lives in the uid attribute
        // (for Active Directory the backend maps sAMAccountName onto uid). The DN's RDN is not a
        // reliable source: an AD DN is cn=<display name>,... rather than uid=... When the LDAP
        // client (e.g. Hadoop LdapGroupsMapping) does not request the attributes the lookup needs,
        // they are trimmed from the returned entries and the lookup would fall back to the cn display
        // name, resolving the wrong roles. Force the required attributes to be returned for the lookup,
        // then strip out the ones the client did not ask for so the response only carries what was
        // requested.
        final List<String> augmentedAttributes = attributesToAugment(ctx, schemaManager);
        final String[] originalReturningAttributes = ctx.getReturningAttributesString();
        if (!augmentedAttributes.isEmpty()) {
            ctx.setReturningAttributes(withAttributes(originalReturningAttributes, augmentedAttributes));
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

        final List<Entry> roleEntries = new ArrayList<>();
        final List<Entry> resultEntries = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            try {
                if (LdapUtils.isGroupEntry(entry)) {
                    roleEntries.addAll(translateGroupEntry(entry));
                } else {
                    final String username = LdapUtils.extractUsernameFromEntry(entry, "uid", "cn");
                    final Set<String> groups = fetchGroups(entry);
                    final Collection<String> roles = rolesLookupService.lookupRoles(username, groups);
                    modifyEntry(entry, roles);
                    for (String attribute : augmentedAttributes) {
                        // These attributes were added only to resolve the lookup; remove them so the response
                        // honors the client's original request.
                        entry.removeAttributes(attribute);
                    }
                    resultEntries.add(entry);
                }
            } catch (Exception e) {
                LOG.ldapRolesLookupFailed(entry.getDn().getName(), e);
                throw new LdapException(e);
            }
        }
        resultEntries.addAll(deduplicate(roleEntries));

        if (!augmentedAttributes.isEmpty()) {
            ctx.setReturningAttributes(originalReturningAttributes == null ? new String[0] : originalReturningAttributes);
        }

        return new EntryFilteringCursorImpl(new ListCursor<>(resultEntries), ctx, schemaManager);
    }

    private Collection<? extends Entry> deduplicate(List<Entry> roleEntries) throws LdapException {
        Map<Dn, Entry> dedup = new HashMap<>();
        for (Entry entry : roleEntries) {
            Dn dn = entry.getDn();
            if (!dedup.containsKey(dn)) {
                dedup.put(dn, entry);
            } else {
                addMembersToGroupEntry(dedup.get(dn), entry);
            }
        }
        return dedup.values();
    }

    /**
     * Adds member and uniqueMember attributes from source entry into destination entry.
     * @param destination the destination entry
     * @param source the source entry
     * @throws LdapException
     */
    private void addMembersToGroupEntry(Entry destination, Entry source) throws LdapException {
        Attribute sourceMembers = source.get("member");
        Attribute sourceUniqueMembers = source.get("uniqueMember");

        Attribute destinationMembers = destination.get("member");
        if ((sourceMembers != null || sourceUniqueMembers != null) && destinationMembers == null) {
            // create member Attribute for destination Entry
            destinationMembers = new DefaultAttribute(schemaManager.getAttributeType("member"));
            destination.add(destinationMembers);
        }

        if (sourceMembers != null) {
            for (Value value : sourceMembers) {
                destinationMembers.add(value.getString());
            }
        }
        if (sourceUniqueMembers != null) {
            for (Value value : sourceUniqueMembers) {
                destinationMembers.add(value.getString());
            }
        }
    }

    /**
     * Computes which of the {@link #REQUIRED_ATTRIBUTES} the client did not already request and that
     * must therefore be added to the search so the role lookup can read them. Returns the attributes to
     * add, or an empty list when the request already covers them (e.g. a {@code *} all-user-attributes
     * request, or an explicit list that names them). The per-attribute {@code contains} check accounts
     * for the {@code *}/{@code +}/{@code 1.1} flags against each attribute's usage.
     */
    private List<String> attributesToAugment(SearchOperationContext ctx, SchemaManager schemaManager) throws LdapException {
        final List<String> toAugment = new ArrayList<>();
        for (String attributeName : REQUIRED_ATTRIBUTES) {
            final AttributeType attributeType = schemaManager.lookupAttributeTypeRegistry(attributeName);
            if (!ctx.contains(schemaManager, attributeType)) {
                toAugment.add(attributeName);
            }
        }
        return toAugment;
    }

    private String[] withAttributes(String[] attributes, List<String> additional) {
        final List<String> combined = new ArrayList<>();
        if (attributes != null) {
            combined.addAll(Arrays.asList(attributes));
        }
        combined.addAll(additional);
        return combined.toArray(new String[0]);
    }

    /**
     * Translates a group entry into zero or more entries representing the roles the group
     * maps to, renaming each entry's DN (and cn) to the role name. Groups with no role mapping
     * are dropped from the result set.
     */
    List<Entry> translateGroupEntry(Entry entry) throws Exception {
        final String groupName = LdapUtils.extractGroupName(entry.getDn());
        if (groupName == null) {
            return List.of();
        }
        final Collection<String> roles = rolesLookupService.lookupRoles(null, Set.of(groupName));
        final List<Entry> translatedEntries = new ArrayList<>();
        for (String role : roles) {
            final Dn roleDn = renameCnRdn(entry.getDn(), role);
            if (roleDn != null) {
                final Entry roleEntry = new DefaultEntry(schemaManager, roleDn);
                roleEntry.add("cn", role);
                roleEntry.add("objectClass", "groupOfNames");
                addMembersToGroupEntry(roleEntry, entry);
                translatedEntries.add(roleEntry);
            }
        }
        return translatedEntries;
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
        final Dn roleDn = templateDn == null ? null : renameCnRdn(templateDn, role);
        entry.add("memberOf", roleDn != null ? roleDn.getName() : "cn=" + role);
    }

    /**
     * Builds a new DN by replacing the leading "cn" RDN of the given DN with the given value.
     * Returns null if the DN's leading RDN is not "cn", since there is no safe way to rename it.
     */
    private static Dn renameCnRdn(Dn dn, String newCnValue) throws LdapException {
        final List<Rdn> rdns = new ArrayList<>(dn.getRdns());
        if (rdns.isEmpty() || !rdns.get(0).getType().equalsIgnoreCase("cn")) {
            return null;
        }
        rdns.set(0, new Rdn("cn", newCnValue));
        return new Dn(rdns.toArray(new Rdn[0]));
    }
}

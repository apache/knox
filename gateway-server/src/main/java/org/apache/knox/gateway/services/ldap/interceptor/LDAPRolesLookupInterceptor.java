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

import com.google.common.annotations.VisibleForTesting;
import org.apache.directory.api.ldap.model.cursor.Cursor;
import org.apache.directory.api.ldap.model.cursor.ListCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultAttribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapInvalidDnException;
import org.apache.directory.api.ldap.model.filter.AndNode;
import org.apache.directory.api.ldap.model.filter.EqualityNode;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.ObjectClassNode;
import org.apache.directory.api.ldap.model.filter.OrNode;
import org.apache.directory.api.ldap.model.filter.PresenceNode;
import org.apache.directory.api.ldap.model.message.Control;
import org.apache.directory.api.ldap.model.message.SearchRequest;
import org.apache.directory.api.ldap.model.message.SearchRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.api.ldap.model.schema.AttributeType;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursorImpl;
import org.apache.directory.server.core.api.interceptor.BaseInterceptor;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LDAPRolesLookupService;
import org.apache.knox.gateway.services.ldap.LdapMessages;
import org.apache.knox.gateway.services.ldap.LdapUtils;
import org.apache.knox.gateway.services.ldap.RoleAssignees;
import org.apache.knox.gateway.services.ldap.control.RolesLookupBypassControl;
import org.apache.knox.gateway.services.ldap.control.RolesLookupBypassControlImpl;
import org.apache.knox.gateway.services.ldap.model.constants.SchemaConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * If true, the user id and the group names are lowercased before they are sent to the roles lookup. This lets
     * a case-sensitive roles backend match users and groups whose directory names (e.g. AD sAMAccountName or group
     * cn) differ in case from the ids the roles were assigned to.
     */
    public static final String LOWERCASE_IDS = "lowercaseIds";

    private final LDAPRolesLookupService rolesLookupService;
    private final String baseDn;
    private final String proxyGroupSearchBase;
    private final String proxyUserSearchBase;
    private final boolean lowercaseIds;

    /**
     * Track the number of roles. This value is used to decide whether the list or lookup
     * strategy will be used when populating users and groups with their roles.
     * -1 indicates the number of roles is unknown.
     */
    @SuppressWarnings("PMD.AvoidUsingVolatile")
    private volatile int numRoles = -1;

    public LDAPRolesLookupInterceptor(LDAPRolesLookupService rolesLookupService, Map<String, String> config) throws Exception{
        this.rolesLookupService = rolesLookupService;
        this.baseDn = config.get("baseDn");
        this.proxyGroupSearchBase = "ou=groups," + this.baseDn;
        this.proxyUserSearchBase = "ou=people," + this.baseDn;
        this.lowercaseIds = Boolean.parseBoolean(config.get(LOWERCASE_IDS));
    }

    @Override
    public void init(DirectoryService directoryService) throws LdapException {
        super.init(directoryService);
        try {
            // attempt to seed numRoles
            listRoles();
        }  catch (Exception ignore) {
        }
    }

    /**
     * Seeds the number of roles last seen so tests can pin which strategy {@link #handleSearch} picks
     * between {@link #populateRolesThroughList} and {@link #populateRolesThroughLookup}. In production
     * this is maintained from {@link LDAPRolesLookupService#listRoles()} inside the list path.
     */
    @VisibleForTesting
    void setNumRoles(int numRoles) {
        this.numRoles = numRoles;
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

        if (isAllGroupsSearch(ctx)) {
            return handleAllGroupsSearch(ctx);
        } else {
            return handleSearch(ctx);
        }
    }

    private boolean isAllGroupsSearch(SearchOperationContext ctx) throws LdapException {
        Dn proxyGroupSearchBaseDn = new Dn(schemaManager, proxyGroupSearchBase);
        Dn searchDn = ctx.getDn();
        ExprNode rawSearchFilter = ctx.getFilter();
        ExprNode searchFilter = removeReferral(rawSearchFilter);

        if (searchDn == null) {
            return false;
        }

        if (ctx.getScope() == SearchScope.OBJECT || !isAllGroupsFilter(searchFilter)) {
            return false;
        }

        // Search for all groups in the proxy groups base
        if (proxyGroupSearchBaseDn.equals(searchDn) && isAllGroupsFilter(searchFilter)) {
            return true;
        }

        // the root with an explicit group filter
        return searchDn.isAncestorOf(proxyGroupSearchBaseDn)
                && isAllGroupsFilterForAncestor(searchFilter);
    }

    private ExprNode removeReferral(ExprNode rawSearchFilter) {
        if (rawSearchFilter instanceof OrNode) {
            OrNode orNode = (OrNode) rawSearchFilter;
            if (orNode.getChildren().size() == 2) {
                List<ExprNode> children = orNode.getChildren();
                if (isReferralObjectClassFilter(children.get(0))) {
                    return children.get(1);
                }
                if (isReferralObjectClassFilter(children.get(1))) {
                    return children.get(0);
                }
            }
        }
        return rawSearchFilter;
    }

    private boolean isGroupObjectClassFilter(ExprNode filter) {
        if (filter instanceof EqualityNode) {
            EqualityNode<?> eqNode = (EqualityNode<?>) filter;
            if ("objectClass".equalsIgnoreCase(eqNode.getAttribute())) {
                String value = eqNode.getValue().toString();
                return "groupOfNames".equalsIgnoreCase(value) ||
                        "groupOfUniqueNames".equalsIgnoreCase(value);
            }
        }
        return false;
    }

    private boolean isReferralObjectClassFilter(ExprNode filter) {
        if (filter instanceof EqualityNode) {
            EqualityNode<?> eqNode = (EqualityNode<?>) filter;
            if ("objectClass".equalsIgnoreCase(eqNode.getAttribute())) {
                String value = eqNode.getValue().toString();
                return "referral".equalsIgnoreCase(value);
            }
        }
        return false;
    }

    private boolean isAllGroupsFilterForAncestor(ExprNode filter) {
        // Case 1: Simple objectClass evaluation (e.g., objectClass=groupOfNames)
        if (isGroupObjectClassFilter(filter)) {
            return true;
        }

        // Case 2: Compound filters.
        if (filter instanceof AndNode) {
            List<ExprNode> children = ((AndNode) filter).getChildren();
            // An AND is an all groups filter only if one child is and the rest of the children
            // are supersets of all groups. e.g., (&(objectClass=groupOfNames)(cn=*))
            return !children.isEmpty() &&
                    children.stream().anyMatch(this::isGroupObjectClassFilter) &&
                    children.stream().allMatch(this::isAllGroupsFilter);
        }
        if (filter instanceof OrNode) {
            // An OR is an all groups filter only if every child is.
            return ((OrNode) filter).getChildren().stream().allMatch(this::isGroupObjectClassFilter);
        }

        return false;
    }

    private boolean isAllGroupsFilter(ExprNode filter) {
        // Case 1: Simple objectClass evaluation (e.g., objectClass=groupOfNames)
        if (isGroupObjectClassFilter(filter)) {
            return true;
        }

        // Case 2a: Wildcard match on all common names and object classes (e.g., cn=*, objectClass=*)
        if (filter instanceof PresenceNode) {
            PresenceNode presNode = (PresenceNode) filter;
            return "cn".equalsIgnoreCase(presNode.getAttribute()) ||
                    "objectClass".equalsIgnoreCase(presNode.getAttribute());
        }
        // Case 2b: Wildcard match on object class (e.g., objectClass=*)
        if (filter instanceof ObjectClassNode) {
            return true;
        }

        // Case 3: Compound filters. An AND is an all groups filter only if every child is, an OR
        // if any child is, e.g. (&(objectClass=groupOfNames)(cn=*)) or (|(uid=alice)(cn=*)).
        // A NOT is never an all groups filter: negating a condition never positively selects groups.
        if (filter instanceof AndNode) {
            List<ExprNode> children = ((AndNode) filter).getChildren();
            return !children.isEmpty() && children.stream().allMatch(this::isAllGroupsFilter);
        }
        if (filter instanceof OrNode) {
            return ((OrNode) filter).getChildren().stream().anyMatch(this::isAllGroupsFilter);
        }

        return false;
    }

    private EntryFilteringCursor handleAllGroupsSearch(SearchOperationContext ctx) throws LdapException {
        List<Entry> entries = new ArrayList<>();

        final AttributeType memberAttributeType = schemaManager.lookupAttributeTypeRegistry("member");
        if (!ctx.contains(schemaManager, memberAttributeType)) {
            // short-circuit the LDAP search to return all roles
            Collection<String> roles = listRoles();
            for (String role : roles) {
                Dn roleDn = new Dn(schemaManager, createGroupDnFromName(role));
                Entry entry = createRoleEntry(roleDn);
                entries.add(entry);
            }
        } else {
            Map<String, RoleAssignees> roleAssignees = getAllRoleAssignees();

            // Collect each assigned group's member DNs (the LDAP proxy backend has already flattened
            // nested groups into their members).
            Set<String> groupsToResolve = roleAssignees.values().stream()
                    .map(RoleAssignees::getGroups)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());
            Map<String, Set<Dn>> groupToMemberDns = collectGroupMemberDns(ctx, groupsToResolve);

            // A single user-base search resolves the directly-assigned users (by uid) and, at the same
            // time, re-resolves which group member DNs are users - one search in place of two.
            Set<String> directUserIds = roleAssignees.values().stream()
                    .map(RoleAssignees::getUsers)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());
            Set<Dn> candidateMemberDns = new LinkedHashSet<>();
            groupToMemberDns.values().forEach(candidateMemberDns::addAll);
            // A DN's normalized form is reused across resolveUsers and retainUserMembers; memoize it so
            // each distinct member DN is rebuilt through the schema only once per request.
            final Map<Dn, String> normCache = new HashMap<>();
            UserResolution users = resolveUsers(ctx, directUserIds, candidateMemberDns, normCache);

            // Keep only the group members that resolved to a user entry.
            Map<String, Set<Dn>> groupMembers = retainUserMembers(groupToMemberDns, users.userMemberNorms(), normCache);

            populateRoleMembers(entries, roleAssignees, groupMembers, users.directUserDns());
        }

        return new EntryFilteringCursorImpl(new ListCursor<>(entries), ctx, schemaManager);
    }

    /**
     * Issues a sub-search against the backend as a separate LDAP operation that carries the
     * {@link RolesLookupBypassControl}. Running it as its own request (rather than mutating and forwarding
     * the incoming context) leaves the caller's search context untouched — so its returning-attribute
     * selection is not disturbed — and the bypass control makes this interceptor pass the backend entries
     * through unchanged on re-entry instead of re-mapping them to roles.
     */
    private List<Entry> searchBackend(SearchOperationContext ctx, String base, ExprNode filter, String... attributes) throws LdapException {
        final SearchRequest searchRequest = new SearchRequestImpl();
        searchRequest.setBase(new Dn(schemaManager, base));
        searchRequest.setScope(SearchScope.SUBTREE);
        searchRequest.setFilter(filter);
        if (attributes != null && attributes.length > 0) {
            searchRequest.addAttributes(attributes);
        }
        final RolesLookupBypassControl bypassControl = new RolesLookupBypassControlImpl();
        bypassControl.setBypassRolesLookup(true);
        searchRequest.addControl(bypassControl);

        final List<Entry> results = new ArrayList<>();
        try (Cursor<Entry> cursor = ctx.getSession().search(searchRequest)) {
            while (cursor.next()) {
                results.add(cursor.get());
            }
        } catch (LdapException e) {
            throw e;
        } catch (Exception e) {
            throw new LdapException(e.getMessage(), e);
        }
        return results;
    }

    /**
     * Resolves, in one search of the proxy user base, the DNs of the directly-assigned users (matched by
     * uid) and which of the candidate group-member DNs are user entries (matched by their leading RDN),
     * all confirmed by objectClass. Returns the directly-assigned users keyed on their lowercased uid,
     * and the normalized DNs of the member candidates that are users. Matching members by normalized DN
     * avoids treating an entry that merely shares an RDN in another subtree as a member. Keying users on
     * the lowercased uid lets the assignee lookup match even when the roles store holds a different case
     * than the directory (uid matches case-insensitively).
     */
    private UserResolution resolveUsers(SearchOperationContext ctx, Set<String> directUserIds, Set<Dn> candidateMemberDns, Map<Dn, String> normCache) throws LdapException {
        if ((directUserIds == null || directUserIds.isEmpty()) && candidateMemberDns.isEmpty()) {
            return new UserResolution(Map.of(), Set.of());
        }
        final OrNode idNode = new OrNode();
        final AttributeType uidAttributeType = schemaManager.getAttributeType("uid");
        if (directUserIds != null) {
            for (String userId : directUserIds) {
                EqualityNode uidEquality = new EqualityNode<>(uidAttributeType, new Value(userId));
                idNode.addNode(uidEquality);
            }
        }
        for (Dn candidate : candidateMemberDns) {
            final Rdn rdn = candidate.getRdn();
            final AttributeType rdnType = schemaManager.getAttributeType(rdn.getType());
            if (rdnType != null) {
                EqualityNode rdnEquality = new EqualityNode<>(rdnType, new Value(rdn.getValue()));
                idNode.addNode(rdnEquality);
            }
        }
        if (idNode.getChildren().isEmpty()) {
            // No resolvable ids - e.g. only group assignees whose member RDN types are unknown to the
            // schema. An empty OR would make the filter invalid, and there is nothing to resolve anyway.
            return new UserResolution(Map.of(), Set.of());
        }
        final AndNode filter = new AndNode();
        final AttributeType objectClassAttributeType = schemaManager.getAttributeType("objectClass");
        EqualityNode userObjectClass = new EqualityNode<>(objectClassAttributeType, new Value("inetOrgPerson"));
        filter.addNode(userObjectClass);
        filter.addNode(idNode);

        final Set<String> candidateNorms = new HashSet<>();
        for (Dn candidate : candidateMemberDns) {
            candidateNorms.add(normalizedName(candidate, normCache));
        }
        final Map<String, Dn> directUserDns = new HashMap<>();
        final Set<String> userMemberNorms = new HashSet<>();
        for (Entry entry : searchBackend(ctx, proxyUserSearchBase, filter, "uid", "objectClass")) {
            if (!LdapUtils.isUserEntry(entry)) {
                continue;
            }
            final Attribute uidAttr = entry.get("uid");
            if (uidAttr != null) {
                directUserDns.put(uidAttr.getString().toLowerCase(Locale.ROOT), entry.getDn());
            }
            final String norm = normalizedName(entry.getDn(), normCache);
            if (candidateNorms.contains(norm)) {
                userMemberNorms.add(norm);
            }
        }
        return new UserResolution(directUserDns, userMemberNorms);
    }

    /** The result of {@link #resolveUsers}: directly-assigned users by uid, and member DNs that are users. */
    private record UserResolution(Map<String, Dn> directUserDns, Set<String> userMemberNorms) { }

    /**
     * Normalizes a DN by rebuilding it through the schema before taking its normalized form, so two DNs
     * for the same entry compare equal regardless of whether the source DN was already schema-normalized.
     * A member DN built here from a group's attribute value is schema-applied, but a user entry returned
     * by the proxy backend may carry a DN that is not, so comparing their raw {@code getNormName()} forms
     * would never match; routing both through this method normalizes them consistently.
     */
    private String normalizedName(Dn dn) {
        try {
            return new Dn(schemaManager, dn.getName()).getNormName();
        } catch (LdapInvalidDnException e) {
            return dn.getName().toLowerCase(Locale.ROOT);
        }
    }

    /** {@link #normalizedName(Dn)} memoized per request, so each distinct DN is normalized only once. */
    private String normalizedName(Dn dn, Map<Dn, String> cache) {
        return cache.computeIfAbsent(dn, this::normalizedName);
    }

    /**
     * Collects each of the given groups' member DNs (member and uniqueMember values). The LDAP proxy
     * backend expands nested groups, so a nested group's users surface here too; which of these members
     * are users is decided later, by re-resolving them against the backend (see {@link #resolveUsers}).
     * The group search matches both groupOfNames and groupOfUniqueNames and is forwarded past this
     * interceptor so the backend groups are returned as-is, not translated to roles. Keyed on the
     * lowercased group name so a mixed-case directory cn still matches the assignee.
     */
    private Map<String, Set<Dn>> collectGroupMemberDns(SearchOperationContext ctx, Set<String> groupsToResolve) throws LdapException {
        if (groupsToResolve == null || groupsToResolve.isEmpty()) {
            return Map.of();
        }
        final Map<String, Set<Dn>> groupToMemberDns = new LinkedHashMap<>();
        for (Entry entry : searchBackend(ctx, proxyGroupSearchBase, groupSearchFilter(groupsToResolve), "objectClass", "member", "uniqueMember")) {
            if (!LdapUtils.isGroupEntry(entry)) {
                continue;
            }
            final String groupName = LdapUtils.extractGroupName(entry.getDn());
            if (groupName == null) {
                continue;
            }
            final Set<Dn> memberDns = groupToMemberDns.computeIfAbsent(groupName.toLowerCase(Locale.ROOT), k -> new LinkedHashSet<>());
            for (String attributeName : new String[] {"member", "uniqueMember"}) {
                final Attribute attribute = entry.get(attributeName);
                if (attribute != null) {
                    for (Value value : attribute) {
                        try {
                            memberDns.add(new Dn(schemaManager, value.getString()));
                        } catch (LdapInvalidDnException ignore) {
                            // skip values that are not valid DNs
                        }
                    }
                }
            }
        }
        return groupToMemberDns;
    }

    /** Filters each group's member DNs to those whose normalized DN resolved to a user entry. */
    private Map<String, Set<Dn>> retainUserMembers(Map<String, Set<Dn>> groupToMemberDns, Set<String> userMemberNorms, Map<Dn, String> normCache) {
        final Map<String, Set<Dn>> groupMembers = new LinkedHashMap<>();
        for (Map.Entry<String, Set<Dn>> group : groupToMemberDns.entrySet()) {
            final Set<Dn> users = new LinkedHashSet<>();
            for (Dn memberDn : group.getValue()) {
                if (userMemberNorms.contains(normalizedName(memberDn, normCache))) {
                    users.add(memberDn);
                }
            }
            groupMembers.put(group.getKey(), users);
        }
        return groupMembers;
    }

    /** Builds a filter matching the given groups by cn, accepting either standard group object class. */
    private ExprNode groupSearchFilter(Set<String> groupsToResolve) {
        final AndNode filter = new AndNode();
        final AttributeType objectClassAttributeType = schemaManager.getAttributeType("objectClass");
        final OrNode objectClassNode = new OrNode();
        for (String groupObjectClass : new String[] {"groupOfNames", "groupOfUniqueNames"}) {
            EqualityNode objectClassEquality = new EqualityNode<>(objectClassAttributeType, new Value(groupObjectClass));
            objectClassNode.addNode(objectClassEquality);
        }
        filter.addNode(objectClassNode);
        final OrNode cnNode = new OrNode();
        final AttributeType cnAttributeType = schemaManager.getAttributeType("cn");
        for (String groupName : groupsToResolve) {
            EqualityNode cnEquality = new EqualityNode<>(cnAttributeType, new Value(groupName));
            cnNode.addNode(cnEquality);
        }
        filter.addNode(cnNode);
        return filter;
    }

    /**
     * Builds one role entry per role and sets its {@code member} attribute to the DNs of the role's
     * members: the users directly assigned to the role plus the user members of the groups assigned to
     * the role, each resolved to its directory DN. Roles with no resolvable members are still returned,
     * without a member attribute. The created entries are appended to {@code entries}.
     */
    private void populateRoleMembers(List<Entry> entries, Map<String, RoleAssignees> roleAssignees,
                                     Map<String, Set<Dn>> groupMembers, Map<String, Dn> userDns)
            throws LdapException {
        for (Map.Entry<String, RoleAssignees> roleEntry : roleAssignees.entrySet()) {
            final String roleName = roleEntry.getKey();
            final RoleAssignees assignees = roleEntry.getValue();
            final Dn roleDn = new Dn(schemaManager, createGroupDnFromName(roleName));
            final Entry entry = createRoleEntry(roleDn);

            final Set<Dn> memberDns = new LinkedHashSet<>();
            if (assignees != null) {
                if (assignees.getUsers() != null) {
                    for (String user : assignees.getUsers()) {
                        addUserDn(memberDns, userDns, user);
                    }
                }
                if (assignees.getGroups() != null) {
                    for (String group : assignees.getGroups()) {
                        memberDns.addAll(groupMembers.getOrDefault(group.toLowerCase(Locale.ROOT), Set.of()));
                    }
                }
            }
            for (Dn memberDn : memberDns) {
                entry.add("member", memberDn.getName());
            }
            entries.add(entry);
        }
    }

    /** Adds the DN of the user with the given id (matched case-insensitively) to the member set. */
    private void addUserDn(Set<Dn> memberDns, Map<String, Dn> userDns, String userId) {
        final Dn userDn = userId == null ? null : userDns.get(userId.toLowerCase(Locale.ROOT));
        if (userDn != null) {
            memberDns.add(userDn);
        }
    }

    private Collection<String> listRoles() throws LdapException {
        try {
            Collection<String> roles = rolesLookupService.listRoles();
            if (roles != null && !roles.isEmpty()) {
                numRoles = roles.size();
            }
            return roles;
        } catch (Exception e) {
            throw new LdapException("Failed to list roles from roles service.", e);
        }
    }

    private Map<String, RoleAssignees> getAllRoleAssignees() throws LdapException {
        try {
            Map<String, RoleAssignees> roleAssignees = rolesLookupService.getAllRoleAssignees();
            if (roleAssignees == null) {
                // The service returns null when roles lookup is disabled (listRoles() returns an empty
                // list in that case); normalize to an empty map so callers never dereference null.
                return Map.of();
            }
            if (!roleAssignees.isEmpty()) {
                numRoles = roleAssignees.size();
            }
            return roleAssignees;
        } catch (Exception e) {
            LOG.ldapRolesGetAllRoleAssignmentsFailed(e);
            throw new LdapException("Failed to retrieve all role assignments from roles service.", e);
        }
    }

    private EntryFilteringCursor handleSearch(SearchOperationContext ctx) throws LdapException {
        // Replace the role dn with the member dns if the search filter contains a "memberOf" node.
        ExprNode filter = ctx.getFilter();
        ReverseRoleLookupMappingVisitor reverseRoleLookupMappingVisitor =
                new ReverseRoleLookupMappingVisitor(schemaManager, rolesLookupService, this::createGroupDnFromName);
        try {
            Object updatedFilter = filter.accept(reverseRoleLookupMappingVisitor);
            if (updatedFilter != null && updatedFilter instanceof ExprNode) {
                ctx.setFilter((ExprNode) updatedFilter);
            }
        } catch (Exception e) {
            LOG.ldapRolesReverseLookupFilterMappingFailed(filter.toString(), e);
            throw new LdapException("Failed to update search filter with reverse role lookup.", e);
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

        int numUserAndGroupEntries = 0;
        final List<Entry> entries = new ArrayList<>();
        try (EntryFilteringCursor cursor = next(ctx)) {
            while (cursor.next()) {
                Entry entry = cursor.get();
                if (LdapUtils.isUserEntry(entry) || LdapUtils.isGroupEntry(entry)) {
                    numUserAndGroupEntries++;
                }
                entries.add(cursor.get());
            }
        } catch (Exception e) {
            LOG.ldapRolesLookupFailed(LdapUtils.extractGroupName(ctx.getDn()), e);
            throw new LdapException(e);
        }

        // Populating the roles through lookup results in one lookup request per entry.
        // It is more efficient to list all the roles when the number of entries is large.
        List<Entry> resultEntries;
        if (preferListStrategy(numUserAndGroupEntries)) {
            try {
                resultEntries = populateRolesThroughList(entries, augmentedAttributes);
            } catch (LdapException e) {
                // if listing roles fails then fall back on populating roles through lookup
                resultEntries = populateRolesThroughLookup(entries, augmentedAttributes);
            }
        } else {
            resultEntries = populateRolesThroughLookup(entries, augmentedAttributes);
        }

        if (!augmentedAttributes.isEmpty()) {
            ctx.setReturningAttributes(originalReturningAttributes == null ? new String[0] : originalReturningAttributes);
        }

        return new EntryFilteringCursorImpl(new ListCursor<>(resultEntries), ctx, schemaManager);
    }

    private boolean preferListStrategy(int numUsersAndGroupEntries) {
        // numRoles < 0 indicates that list has not been run and the number of roles is unknown
        // If numRoles is known, then prefer the list strategy if
        return numRoles >= 0 && numUsersAndGroupEntries > numRoles;
    }

    private List<Entry> populateRolesThroughList(List<Entry> entries, List<String> augmentedAttributes) throws LdapException {
        // Map of user id to the assigned roles
        final Map<String, List<String>> userRoleAssignments = new HashMap<>();
        // Map of group id to the assigned roles
        final Map<String, List<String>> groupRoleAssignments = new HashMap<>();
        final Map<String, RoleAssignees> roleAssignees = getAllRoleAssignees();
        for (Map.Entry<String, RoleAssignees> entry : roleAssignees.entrySet()) {
            String roleName = entry.getKey();
            RoleAssignees assignees = entry.getValue();
            if (assignees != null) {
                if (assignees.getUsers() != null && !assignees.getUsers().isEmpty()) {
                    for (String userName : assignees.getUsers()) {
                        if (!userRoleAssignments.containsKey(userName)) {
                            ArrayList<String> roles = new ArrayList<>();
                            roles.add(roleName);
                            userRoleAssignments.put(userName, roles);
                        } else {
                            userRoleAssignments.get(userName).add(roleName);
                        }

                    }
                }
                if (assignees.getGroups() != null && !assignees.getGroups().isEmpty()) {
                    for (String groupName : assignees.getGroups()) {
                        if (!groupRoleAssignments.containsKey(groupName)) {
                            ArrayList<String> roles = new ArrayList<>();
                            roles.add(roleName);
                            groupRoleAssignments.put(groupName, roles);
                        } else {
                            groupRoleAssignments.get(groupName).add(roleName);
                        }
                    }
                }
            }
        }

        final List<Entry> roleEntries = new ArrayList<>();
        final List<Entry> resultEntries = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            try {
                if (LdapUtils.isGroupEntry(entry)) {
                    roleEntries.addAll(translateGroupEntry(entry, groupRoleAssignments));
                } else {
                    final String username = toRolesLookupId(LdapUtils.extractUsernameFromEntry(entry, "uid", "cn"));
                    final List<String> roles = new ArrayList<>();
                    if (userRoleAssignments.containsKey(username)) {
                        roles.addAll(userRoleAssignments.get(username));
                    }
                    for (String groupName : fetchGroups(entry)) {
                        final List<String> groupRoles = groupRoleAssignments.get(groupName);
                        if (groupRoles != null) {
                            roles.addAll(groupRoles);
                        }
                    }

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
        return resultEntries;
    }

    private List<Entry> populateRolesThroughLookup(List<Entry> entries, List<String> augmentedAttributes) throws LdapException {
        final List<Entry> roleEntries = new ArrayList<>();
        final List<Entry> resultEntries = new ArrayList<>(entries.size());

        for (Entry entry : entries) {
            try {
                if (LdapUtils.isGroupEntry(entry)) {
                    roleEntries.addAll(translateGroupEntry(entry));
                } else {
                    final String username = toRolesLookupId(LdapUtils.extractUsernameFromEntry(entry, "uid", "cn"));
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
        return resultEntries;
    }

    private String toRolesLookupId(String id) {
        return lowercaseIds && id != null ? id.toLowerCase(Locale.ROOT) : id;
    }

    private String createGroupDnFromName(String groupName) {
        return String.format(Locale.ROOT, "cn=%s,%s", groupName, proxyGroupSearchBase);
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
        final Collection<String> roles = rolesLookupService.lookupRoles(null, Set.of(toRolesLookupId(groupName)));
        return createRoleEntries(entry, roles);

    }

    List<Entry> translateGroupEntry(Entry entry, Map<String, List<String>> groupRoleAssignments) throws Exception {
        final String groupName = LdapUtils.extractGroupName(entry.getDn());
        if (groupName == null) {
            return List.of();
        }
        final List<String> roles = groupRoleAssignments.get(toRolesLookupId(groupName));
        if (roles == null) {
            return List.of();
        }
        return createRoleEntries(entry, roles);
    }

    private List<Entry> createRoleEntries(Entry entry, Collection<String> roles) throws Exception {
        final List<Entry> translatedEntries = new ArrayList<>(roles.size());
        for (String role : roles) {
            final Dn roleDn = renameCnRdn(entry.getDn(), role);
            if (roleDn != null) {
                final Entry roleEntry = createRoleEntry(roleDn);
                addMembersToGroupEntry(roleEntry, entry);
                translatedEntries.add(roleEntry);
            }
        }
        return translatedEntries;
    }

    private Entry createRoleEntry(Dn roleDn) throws LdapException {
        final Entry roleEntry = new DefaultEntry(schemaManager, roleDn);
        roleEntry.add(roleDn.getRdn().getType(), roleDn.getRdn().getValue());
        roleEntry.add("objectClass", "groupOfNames");
        return roleEntry;
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
                        groups.add(toRolesLookupId(groupName));
                    }
                } catch (LdapInvalidDnException ignore) {
                }
            }
        }
        return groups;
    }

    Entry modifyEntry(Entry entry, Collection<String> roles) throws LdapException {
        // Only modify if there are existing attributes to wipe or new roles to add
        if (entry != null && (entry.get("memberOf") != null || (roles != null && !roles.isEmpty()))) {
            updateMemberOfAttributes(entry, roles);
        }
        return entry;
    }

    private void updateMemberOfAttributes(Entry entry, Collection<String> roles) throws LdapException {
        // Always wipe the old attributes if role lookup is active
        entry.removeAttributes("memberOf");

        // Roles are written with the same DN the role listing and the reverse role lookup use,
        // so a memberOf value names a role entry served by this server. No roles (null) leaves memberOf wiped.
        if (roles != null) {
            for (String role : roles) {
                entry.add("memberOf", createGroupDnFromName(role));
            }
        }
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

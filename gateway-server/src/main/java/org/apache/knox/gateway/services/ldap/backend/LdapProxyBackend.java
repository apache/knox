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
package org.apache.knox.gateway.services.ldap.backend;

import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.ldap.client.api.DefaultLdapConnectionFactory;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.directory.ldap.client.api.LdapConnectionPool;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.directory.ldap.client.api.ValidatingPoolableLdapConnectionFactory;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LdapMessages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LDAP backend that proxies to an external LDAP server.
 * Can use central LDAP configuration or backend-specific configuration.
 */
public class LdapProxyBackend implements LdapBackend {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    static final String TYPE = "ldap";

    private String name;
    private String ldapUrl;
    private String bindDn;
    private String bindPassword;
    private String userSearchBase;
    private String groupSearchBase;
    private String proxyBaseDn;  // Base DN for proxy entries (e.g., dc=proxy,dc=com)
    private String remoteBaseDn;  // Base DN for remote server searches (e.g., dc=hadoop,dc=apache,dc=org)
    private int port;
    private String host;

    // Configurable attributes for AD/LDAP compatibility
    private String userIdentifierAttribute = "uid"; // uid for LDAP, sAMAccountName for AD
    private String userSearchFilter = "({userIdAttr}={username})"; // Will be populated with userIdentifierAttribute
    private String groupMemberAttribute = "memberUid"; // member for AD, memberUid for POSIX
    private boolean useMemberOf; // Use memberOf attribute for group lookup (efficient for AD)
    private boolean recursiveGroupResolution;
    private int recursiveGroupResolutionMaxDepth;

    private List<String> proxyEntryAttributeTypes = List.of(
            // "uid" will always be filled
            "cn",
            "dn",
            "mail",
            "description");
    private final String proxyEntryGroupMembershipAttributeType = "memberOf";

    // Connection pool for efficient connection reuse
    private LdapConnectionPool connectionPool;

    public LdapProxyBackend(String name, Map<String, String> config) {
        this.name = name;
        // Proxy base DN is for entries created in the proxy LDAP server
        proxyBaseDn = config.get("baseDn");
        if (proxyBaseDn == null || proxyBaseDn.isEmpty()) {
            throw new IllegalArgumentException("baseDn is required for LDAP proxy backend");
        }

        // Remote base DN is for searching the remote LDAP server
        remoteBaseDn = config.get("remoteBaseDn");
        if (remoteBaseDn == null || remoteBaseDn.isEmpty()) {
            throw new IllegalArgumentException("remoteBaseDn is required for LDAP proxy backend - this is the base DN of the remote LDAP server");
        }

        // Support both url and host/port configuration
        ldapUrl = config.get("url");
        if (ldapUrl != null && !ldapUrl.isEmpty()) {
            // Parse URL to extract host and port
            parseLdapUrl(ldapUrl);
        } else {
            host = config.get("host");
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("Either 'url' or 'host' is required for LDAP proxy backend");
            }
            String portStr = config.get("port");
            if (portStr == null || portStr.isEmpty()) {
                throw new IllegalArgumentException("'port' is required when using 'host' configuration");
            }
            port = Integer.parseInt(portStr);
            ldapUrl = "ldap://" + host + ":" + port;
        }

        // Support both naming conventions: bindDn/bindPassword and systemUsername/systemPassword
        bindDn = config.get("bindDn");
        if (bindDn == null || bindDn.isEmpty()) {
            bindDn = config.get("systemUsername");
        }

        bindPassword = config.get("bindPassword");
        if (bindPassword == null || bindPassword.isEmpty()) {
            bindPassword = config.get("systemPassword");
        }

        // Search bases use the remote server's base DN
        userSearchBase = config.getOrDefault("userSearchBase", "ou=people," + remoteBaseDn);
        groupSearchBase = config.getOrDefault("groupSearchBase", "ou=groups," + remoteBaseDn);

        // Configure attribute mappings for AD/LDAP compatibility
        userIdentifierAttribute = config.getOrDefault("userIdentifierAttribute", "uid");
        groupMemberAttribute = config.getOrDefault("groupMemberAttribute", "memberUid");
        useMemberOf = Boolean.parseBoolean(config.getOrDefault("useMemberOf", "false"));
        recursiveGroupResolution = Boolean.parseBoolean(config.getOrDefault("recursiveGroupResolution", "false"));
        recursiveGroupResolutionMaxDepth = Integer.parseInt(config.getOrDefault("recursiveGroupResolutionMaxDepth", "3"));

        // Build search filter template
        userSearchFilter = "(" + userIdentifierAttribute + "={username})";

        LOG.ldapBackendLoading(getName(), "Proxying " + proxyBaseDn + " to " + ldapUrl + " (" + remoteBaseDn + ") with " +
                              userIdentifierAttribute + " attribute" +
                              (useMemberOf ? " using memberOf lookups" : " using group searches") +
                              (recursiveGroupResolution ? " with recursive group resolution (max depth: " + recursiveGroupResolutionMaxDepth + ")" : ""));

        // Initialize connection pool
        initializeConnectionPool(config);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getBaseDn() {
        return remoteBaseDn;
    }

    /**
     * Initializes the LDAP connection pool with configurable parameters.
     * Uses a validating pool to ensure connections remain healthy.
     *
     * @param config Configuration map that may contain pool settings
     */
    private void initializeConnectionPool(Map<String, String> config) {
        // Configure connection settings
        LdapConnectionConfig connectionConfig = new LdapConnectionConfig();
        connectionConfig.setLdapHost(host);
        connectionConfig.setLdapPort(port);

        if (bindDn != null && !bindDn.isEmpty()) {
            connectionConfig.setName(bindDn);
            connectionConfig.setCredentials(bindPassword);
        }

        // Connection pool configuration (with sensible defaults)
        int maxActive = Integer.parseInt(config.getOrDefault("poolMaxActive", "8"));

        // Create connection factory
        DefaultLdapConnectionFactory factory = new DefaultLdapConnectionFactory(connectionConfig);

        // Create validating poolable connection factory to test connections
        ValidatingPoolableLdapConnectionFactory poolFactory = new ValidatingPoolableLdapConnectionFactory(factory);

        // Create the pool with max size
        connectionPool = new LdapConnectionPool(poolFactory);
        connectionPool.setMaxTotal(maxActive);
        connectionPool.setTestOnBorrow(true);

        LOG.ldapBackendLoading(getType(), "Initialized connection pool with maxActive=" + maxActive);
    }

    private void parseLdapUrl(String url) {
        // Simple URL parsing for ldap://host:port
        if (url.startsWith("ldap://")) {
            String hostPort = url.substring(7);
            int colonIdx = hostPort.indexOf(':');
            if (colonIdx > 0) {
                host = hostPort.substring(0, colonIdx);
                try {
                    port = Integer.parseInt(hostPort.substring(colonIdx + 1));
                } catch (NumberFormatException e) {
                    port = 389;
                }
            } else {
                host = hostPort;
                port = 389;
            }
        } else if (url.startsWith("ldaps://")) {
            String hostPort = url.substring(8);
            int colonIdx = hostPort.indexOf(':');
            if (colonIdx > 0) {
                host = hostPort.substring(0, colonIdx);
                try {
                    port = Integer.parseInt(hostPort.substring(colonIdx + 1));
                } catch (NumberFormatException e) {
                    port = 636;
                }
            } else {
                host = hostPort;
                port = 636;
            }
        }
    }

    /**
     * Gets a connection from the connection pool.
     * Connections obtained from this method should be released back to the pool
     * using releaseConnection() when done.
     *
     * @return An LDAP connection from the pool
     * @throws Exception if unable to get a connection from the pool
     */
    private LdapConnection getConnection() throws Exception {
        return connectionPool.getConnection();
    }

    /**
     * Releases a connection back to the pool.
     * This method should be called in a finally block to ensure connections are returned.
     *
     * @param connection The connection to release back to the pool
     */
    private void releaseConnection(LdapConnection connection) {
        if (connection != null) {
            try {
                connectionPool.releaseConnection(connection);
            } catch (Exception e) {
                LOG.ldapServiceStopFailed(e);
            }
        }
    }

    /**
     * Closes the connection pool and releases all resources.
     * Should be called when the backend is being shut down.
     */
    public void close() {
        if (connectionPool != null) {
            try {
                connectionPool.close();
            } catch (Exception e) {
                LOG.ldapServiceStopFailed(e);
            }
        }
    }

    @Override
    public boolean authenticate(Dn userDn, String password) {
        final String userDnText = userDn.toString(); //at this point we are sure it's not NULL
        // Create a temporary connection for authentication (bind)
        final LdapConnectionConfig authConfig = new LdapConnectionConfig();
        authConfig.setLdapHost(host);
        authConfig.setLdapPort(port);
        authConfig.setName(userDnText);
        authConfig.setCredentials(password);
        try (LdapConnection connection =  new LdapNetworkConnection(authConfig)){
            connection.bind();
            LOG.ldapAuthSucceeded(userDnText);
            return true;
        } catch (Exception e) {
            LOG.ldapAuthFailed(userDnText, e);
            return false;
        }
    }

    @Override
    public Entry getUser(String username, SchemaManager schemaManager) throws Exception {
        LdapConnection connection = null;
        try {
            connection = getConnection();
            // Search for user using configurable attribute
            String filter = userSearchFilter.replace("{username}", username);
            try (EntryCursor cursor = connection.search(userSearchBase, filter, SearchScope.SUBTREE, "*")) {
                if (cursor.next()) {
                    Entry sourceEntry = cursor.get();
                    return createProxyEntry(sourceEntry, username, connection, schemaManager, createResolvedParentsCache());
                }
            }
            return null;
        } finally {
            releaseConnection(connection);
        }
    }

    @Override
    public List<String> getUserGroups(String username) throws Exception {
        LdapConnection connection = null;
        try {
            connection = getConnection();
            List<Entry> groups = getUserGroupsEntries(connection, username, createResolvedParentsCache());
            return getCnsFromEntries(groups);
        } finally {
            releaseConnection(connection);
        }
    }

    private List<Entry> getUserGroupsEntries(LdapConnection connection, String username, Map<String, Set<Entry>> resolvedParentsCache) throws Exception {
        List<Entry> groups = new ArrayList<>();
        if (useMemberOf) {
            // Use memberOf attribute for efficient AD lookups
            List<String> groupDns = getUserGroupsViaMemberOfInternal(connection, username);
            for (String groupDn : groupDns) {
                if (recursiveGroupResolution) {
                    // We only need the Entry if we are doing recursive group resolution
                    try {
                        Entry groupEntry = connection.lookup(groupDn, "cn");
                        if (groupEntry != null) {
                            groups.add(groupEntry);
                        } else {
                            groups.add(createSkeletonGroupEntry(groupDn));
                        }
                    } catch (LdapException e) {
                        groups.add(createSkeletonGroupEntry(groupDn));
                    }
                } else {
                    // Optimized path: just use skeleton entry to extract CN from DN
                    groups.add(createSkeletonGroupEntry(groupDn));
                }
            }
        } else {
            // Use traditional group search approach
            String filter = userSearchFilter.replace("{username}", username);
            try (EntryCursor cursor = connection.search(userSearchBase, filter, SearchScope.SUBTREE, "dn")) {
                if (cursor.next()) {
                    String userDn = cursor.get().getDn().toString();
                    groups = getUserGroupsInternal(connection, userDn, username);
                }
            }
        }

        if (recursiveGroupResolution && !groups.isEmpty()) {
            groups = resolveGroupsRecursive(connection, groups, username, resolvedParentsCache);
        }
        return groups;
    }

    private List<String> getUserGroupsViaMemberOfInternal(LdapConnection connection, String username) throws LdapException, CursorException, IOException {
        final List<String> groupDns = new ArrayList<>();
        // Search for user and retrieve memberOf attribute
        final String filter = userSearchFilter.replace("{username}", username);
        try (EntryCursor cursor = connection.search(userSearchBase, filter, SearchScope.SUBTREE, "memberOf")) {
            if (cursor.next()) {
                final Attribute memberOfAttr = cursor.get().get("memberOf");
                if (memberOfAttr != null) {
                    for (Value value : memberOfAttr) {
                        groupDns.add(value.getString());
                    }
                }
            }
        }
        return groupDns;
    }

    /**
     * Creates a skeleton group entry from a DN when the actual group entry cannot be
     * found in the backend. This ensures backward compatibility with the original
     * implementation which extracted group names directly from memberOf DNs even
     * if the referenced group entries did not exist.
     *
     * @param groupDn The Distinguished Name of the group
     * @return A skeleton Entry containing the DN and CN (if extractable), or null if creation fails
     */
    private Entry createSkeletonGroupEntry(String groupDn) {
        try {
            Entry entry = new DefaultEntry(groupDn);
            String groupName = extractGroupNameFromDn(groupDn);
            if (groupName != null) {
                entry.add("cn", groupName);
            }
            LOG.ldapSkeletonGroupEntryCreated(groupDn);
            return entry;
        } catch (LdapException e) {
            LOG.ldapSearch(groupDn, "failed to create skeleton entry");
            return null;
        }
    }

    private List<Entry> resolveGroupsRecursive(LdapConnection connection, List<Entry> initialGroups, String username,
                                               Map<String, Set<Entry>> resolvedParentsCache) throws LdapException, CursorException, IOException {
        LOG.ldapRecursiveGroupSearchConfig(recursiveGroupResolution, recursiveGroupResolutionMaxDepth);

        Set<String> allGroupDns = new HashSet<>();
        List<Entry> allGroups = new ArrayList<>();
        List<Entry> currentLevelGroups = new ArrayList<>(initialGroups);

        for (Entry group : initialGroups) {
            allGroupDns.add(group.getDn().getNormName());
            allGroups.add(group);
        }

        logRecursiveSearchProgress(username, initialGroups, 0);

        int depth = 1;
        while (!currentLevelGroups.isEmpty() && depth < recursiveGroupResolutionMaxDepth) {
            List<Entry> nextLevelGroups = new ArrayList<>();
            List<Entry> groupsToSearch = new ArrayList<>();

            // Check cache first
            populateFromCache(resolvedParentsCache, currentLevelGroups, allGroupDns, allGroups, nextLevelGroups, groupsToSearch);

            if (!groupsToSearch.isEmpty()) {
                StringBuilder filterBuilder = new StringBuilder("(|");
                for (Entry group : groupsToSearch) {
                    String dn = group.getDn().getName();
                    filterBuilder.append("(member=").append(dn).append(")(uniqueMember=").append(dn).append(")");
                }
                filterBuilder.append(")");

                try (EntryCursor cursor = connection.search(groupSearchBase, filterBuilder.toString(), SearchScope.SUBTREE, "cn", "member", "uniqueMember")) {
                    while (cursor.next()) {
                        Entry parentGroup = cursor.get();
                        String parentDn = parentGroup.getDn().getNormName();

                        // Update cache for all groups found in this search
                        updateCache(resolvedParentsCache, groupsToSearch, parentGroup);

                        if (!allGroupDns.contains(parentDn)) {
                            allGroupDns.add(parentDn);
                            allGroups.add(parentGroup);
                            nextLevelGroups.add(parentGroup);
                        } else {
                            LOG.ldapRecursiveGroupSearchCycleDetected(username, parentDn);
                        }
                    }
                }

                // If some groups had no parents, we still need to mark them in cache to avoid re-searching
                for (Entry child : groupsToSearch) {
                    resolvedParentsCache.putIfAbsent(child.getDn().getNormName(), new HashSet<>());
                }
            }

            logRecursiveSearchProgress(username, nextLevelGroups, depth);
            currentLevelGroups = nextLevelGroups;
            depth++;

            if (depth == recursiveGroupResolutionMaxDepth && !currentLevelGroups.isEmpty()) {
                LOG.ldapRecursiveGroupSearchMaxDepthReached(username, recursiveGroupResolutionMaxDepth);
            }
        }

        LOG.ldapRecursiveGroupSearchFinished(username, allGroups.size());
        return allGroups;
    }

    private void populateFromCache(Map<String, Set<Entry>> resolvedParentsCache, List<Entry> currentLevelGroups, Set<String> allGroupDns, List<Entry> allGroups, List<Entry> nextLevelGroups, List<Entry> groupsToSearch) {
        for (Entry group : currentLevelGroups) {
            Set<Entry> parents = resolvedParentsCache.get(group.getDn().getNormName());
            if (parents != null) {
                LOG.ldapRecursiveGroupSearchCacheHit(group.getDn().getName(), parents.size());
                parents.forEach(parent -> {
                    String parentDn = parent.getDn().getNormName();
                    if (!allGroupDns.contains(parentDn)) {
                        allGroupDns.add(parentDn);
                        allGroups.add(parent);
                        nextLevelGroups.add(parent);
                    }
                });
            } else {
                groupsToSearch.add(group);
            }
        }
    }

    private void updateCache(Map<String, Set<Entry>> resolvedParentsCache, List<Entry> groupsToSearch, Entry parentGroup) {
        for (Entry child : groupsToSearch) {
            if (isMember(parentGroup, child.getDn())) {
                LOG.ldapRecursiveGroupSearchCacheAdd(child.getDn().getName(), parentGroup.getDn().getName());
                resolvedParentsCache.computeIfAbsent(child.getDn().getNormName(), k -> new HashSet<>()).add(parentGroup);
            }
        }
    }

    private boolean isMember(Entry group, Dn memberDn) {
        return checkMemberAttribute(group, "member", memberDn) || checkMemberAttribute(group, "uniqueMember", memberDn);
    }

    private boolean checkMemberAttribute(Entry group, String attributeName, Dn memberDn) {
        final Attribute attr = group.get(attributeName);
        if (attr != null) {
            for (Value value : attr) {
                try {
                    if (memberDn.equals(new Dn(value.getString()))) {
                        return true;
                    }
                } catch (LdapException e) {
                    // Ignore invalid DNs in member attribute
                }
            }
        }
        return false;
    }

    private void logRecursiveSearchProgress(String username, List<Entry> groups, int depth) {
        LOG.ldapRecursiveGroupSearchProgress(username, groups.size(),
                String.join(",", groups.stream().map(e -> e.getDn().getRdn().getValue()).collect(Collectors.joining())),
                depth);
    }

    private String extractGroupNameFromDn(String groupDn) {
        // Extract CN from DN like "CN=Domain Admins,CN=Users,DC=company,DC=com"
        if (groupDn.toLowerCase(Locale.ROOT).startsWith("cn=")) {
            int commaIdx = groupDn.indexOf(',');
            if (commaIdx > 0) {
                return groupDn.substring(3, commaIdx);
            }
        }
        return null;
    }

    private List<Entry> getUserGroupsInternal(LdapConnection connection, String userDn, String username) throws LdapException, CursorException, IOException {
        List<Entry> groups = new ArrayList<>();

        // Search for groups where user is a member - build filter based on configuration
        String filter;
        if ("member".equals(groupMemberAttribute)) {
            // AD style - uses full DN
            filter = "(|" +
                    "(member=" + userDn + ")" +
                    "(uniqueMember=" + userDn + ")" +
                    ")";
        } else {
            // POSIX style - uses username
            filter = "(|" +
                    "(memberUid=" + username + ")" +
                    "(member=" + userDn + ")" +
                    "(uniqueMember=" + userDn + ")" +
                    ")";
        }

        try (EntryCursor cursor = connection.search(groupSearchBase, filter, SearchScope.SUBTREE, "cn")) {
            while (cursor.next()) {
                groups.add(cursor.get());
            }
        }

        return groups;
    }

    private List<String> getCnsFromEntries(Collection<Entry> entries) throws LdapException {
        List<String> cns = new ArrayList<>();
        for (Entry entry : entries) {
            Attribute cnAttr = entry.get("cn");
            if (cnAttr != null) {
                cns.add(cnAttr.getString());
            }
        }
        return cns;
    }

    @Override
    public List<Entry> searchUsers(String filter, SchemaManager schemaManager) throws Exception {
        List<Entry> results = new ArrayList<>();
        LdapConnection connection = null;
        Map<String, Set<Entry>> resolvedParentsCache = createResolvedParentsCache();

        try {
            connection = getConnection();
            String ldapFilter = "(" + userIdentifierAttribute + "=" + filter.trim() + ")";
            try (EntryCursor cursor = connection.search(userSearchBase, ldapFilter, SearchScope.SUBTREE, "*")) {
                while (cursor.next()) {
                    Entry sourceEntry = cursor.get();
                    Attribute idAttr = sourceEntry.get(userIdentifierAttribute);
                    if (idAttr != null) {
                        String username = idAttr.getString();
                        Entry entry = createProxyEntry(sourceEntry, username, connection, schemaManager, resolvedParentsCache);
                        results.add(entry);
                    }
                }
            }
            return results;
        } finally {
            releaseConnection(connection);
        }
    }

    /**
     * Factory method for the resolved parents cache.
     * Overridden in tests to verify caching behavior.
     */
    protected Map<String, Set<Entry>> createResolvedParentsCache() {
        return new HashMap<>();
    }

    /**
     * Creates a proxy entry from a backend source entry with all required attributes.
     * This method standardizes the conversion of backend LDAP entries to proxy entries,
     * preserving the backend DN and copying all standard user attributes.
     *
     * @param sourceEntry The entry from the backend LDAP server
     * @param username The username for the entry
     * @param connection The LDAP connection for fetching group information
     * @param schemaManager The schema manager for creating entries
     * @param resolvedParentsCache Cache for direct parent groups to avoid redundant searches
     * @return A new Entry with backend DN and all copied attributes
     * @throws Exception if entry creation or attribute copying fails
     */
    private Entry createProxyEntry(Entry sourceEntry, String username, LdapConnection connection, SchemaManager schemaManager, Map<String, Set<Entry>> resolvedParentsCache) throws Exception {
        // Standard proxy approach: return entry with backend DN unchanged
        // This preserves DN integrity for bind operations and DN references
        Entry entry = new DefaultEntry(schemaManager);
        entry.setDn(sourceEntry.getDn());

        // Copy all attributes as-is from backend
        copyAttribute(sourceEntry, entry, "objectClass");
        copyAttribute(sourceEntry, entry, userIdentifierAttribute);

        // Map identifier attribute to uid for consistency if needed
        if (!"uid".equals(userIdentifierAttribute)) {
            Attribute idAttr = sourceEntry.get(userIdentifierAttribute);
            if (idAttr != null) {
                entry.add("uid", idAttr.getString());
            }
        }

        for (String attributeType : proxyEntryAttributeTypes) {
            copyAttribute(sourceEntry, entry, attributeType);
        }

        if (useMemberOf && !recursiveGroupResolution) {
            copyAttribute(sourceEntry, entry, proxyEntryGroupMembershipAttributeType);
        } else {
            List<Entry> groups = getUserGroupsEntries(connection, username, resolvedParentsCache);
            for (Entry groupEntry : groups) {
                entry.add(proxyEntryGroupMembershipAttributeType, groupEntry.getDn().getName());
            }
        }

        return entry;
    }

    private void copyAttribute(Entry source, Entry target, String attributeName) throws LdapException {
        final Attribute attribute = source.get(attributeName);
        if (attribute != null) {
            // Copy all values of the attribute (important for multi-valued attributes like objectClass)
            for (Value value : attribute) {
                try {
                    target.add(attributeName, value.getString());
                } catch (LdapException e) {
                    LOG.ldapAttributeCopyError(e);
                    throw e;
                }
            }
        }
    }
}

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
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.ldap.client.api.DefaultLdapConnectionFactory;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.directory.ldap.client.api.LdapConnectionPool;
import org.apache.directory.ldap.client.api.ValidatingPoolableLdapConnectionFactory;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LdapMessages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LDAP backend that proxies to an external LDAP server.
 * Can use central LDAP configuration or backend-specific configuration.
 */
public class LdapProxyBackend implements LdapBackend {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

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
    private int groupMaxDepth;

    private List<String> proxyEntryAttributeTypes = List.of(
            // "uid" will always be filled
            "cn",
            "dn",
            "mail",
            "description");
    private final String proxyEntryGroupMembershipAttributeType = "memberOf";

    // Connection pool for efficient connection reuse
    private LdapConnectionPool connectionPool;

    @Override
    public String getName() {
        return "ldap";
    }

    @Override
    public void initialize(Map<String, String> config) throws Exception {
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
        groupMaxDepth = Integer.parseInt(config.getOrDefault("groupMaxDepth", "10"));

        // Build search filter template
        userSearchFilter = "(" + userIdentifierAttribute + "={username})";

        LOG.ldapBackendLoading(getName(), "Proxying " + proxyBaseDn + " to " + ldapUrl + " (" + remoteBaseDn + ") with " +
                              userIdentifierAttribute + " attribute" +
                              (useMemberOf ? (recursiveGroupResolution ? " using recursive memberOf lookups" : " using memberOf lookups") :
                                             (recursiveGroupResolution ? " using recursive group searches" : " using group searches")));

        // Initialize connection pool
        initializeConnectionPool(config);
    }

    /**
     * Initializes the LDAP connection pool with configurable parameters.
     * Uses a validating pool to ensure connections remain healthy.
     *
     * @param config Configuration map that may contain pool settings
     * @throws Exception if connection pool initialization fails
     */
    private void initializeConnectionPool(Map<String, String> config) throws Exception {
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

        LOG.ldapBackendLoading(getName(), "Initialized connection pool with maxActive=" + maxActive);
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
    public Entry getUser(String username, SchemaManager schemaManager) throws Exception {
        LdapConnection connection = null;
        try {
            connection = getConnection();
            // 1. Try search in user base
            String filter = userSearchFilter.replace("{username}", username);
            EntryCursor cursor = connection.search(userSearchBase, filter, SearchScope.SUBTREE, "*", "+");

            if (cursor.next()) {
                Entry sourceEntry = cursor.get();
                Entry entry = createProxyEntry(sourceEntry, username, connection, schemaManager);
                cursor.close();
                return entry;
            }
            cursor.close();

            // 2. Try search in group base if not found in user base
            String groupFilter = "(cn=" + username + ")";
            cursor = connection.search(groupSearchBase, groupFilter, SearchScope.SUBTREE, "*", "+");
            if (cursor.next()) {
                Entry sourceEntry = cursor.get();
                Entry entry = createProxyEntry(sourceEntry, username, connection, schemaManager);
                cursor.close();
                return entry;
            }
            cursor.close();

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
            Set<String> allGroupDns = new HashSet<>();
            if (useMemberOf) {
                allGroupDns.addAll(getUserGroupDnsViaMemberOf(connection, username));
                if (recursiveGroupResolution && !allGroupDns.isEmpty()) {
                    resolveRecursiveGroupDnsViaMemberOf(connection, allGroupDns, 0);
                }
            } else {
                String filter = userSearchFilter.replace("{username}", username);
                EntryCursor cursor = connection.search(userSearchBase, filter, SearchScope.SUBTREE, "dn");
                if (cursor.next()) {
                    String userDn = cursor.get().getDn().toString();
                    cursor.close();
                    Set<String> visited = new HashSet<>();
                    visited.add(userDn);
                    List<Entry> initialGroups = getUserGroupsInternal(connection, userDn, username);
                    for (Entry group : initialGroups) {
                        allGroupDns.add(group.getDn().toString());
                    }
                    if (recursiveGroupResolution && !allGroupDns.isEmpty()) {
                        resolveRecursiveGroupDnsInternal(connection, allGroupDns, visited, 0);
                    }
                } else {
                    cursor.close();
                }
            }

            List<String> groupNames = new ArrayList<>();
            for (String dn : allGroupDns) {
                String name = extractGroupNameFromDn(dn);
                if (name != null) {
                    groupNames.add(name);
                }
            }
            return groupNames;
        } finally {
            releaseConnection(connection);
        }
    }

    private List<String> getUserGroupDnsViaMemberOf(LdapConnection connection, String username) throws LdapException, CursorException, IOException {
        List<String> dns = new ArrayList<>();
        String filter = userSearchFilter.replace("{username}", username);
        EntryCursor cursor = connection.search(userSearchBase, filter, SearchScope.SUBTREE, "memberOf", "+");
        if (cursor.next()) {
            Entry userEntry = cursor.get();
            Attribute memberOfAttr = userEntry.get("memberOf");
            if (memberOfAttr != null) {
                for (org.apache.directory.api.ldap.model.entry.Value value : memberOfAttr) {
                    dns.add(value.getString());
                }
            }
        }
        cursor.close();
        return dns;
    }

    private void resolveRecursiveGroupDnsViaMemberOf(LdapConnection connection, Set<String> allGroupDns, int depth) throws LdapException, CursorException, IOException {
        if (depth >= groupMaxDepth) {
            return;
        }

        Set<String> nextLevelDns = new HashSet<>();
        for (String dn : allGroupDns) {
            EntryCursor cursor = connection.search(dn, "(objectClass=*)", SearchScope.OBJECT, "memberOf", "+");
            if (cursor.next()) {
                Attribute memberOfAttr = cursor.get().get("memberOf");
                if (memberOfAttr != null) {
                    for (org.apache.directory.api.ldap.model.entry.Value value : memberOfAttr) {
                        String parentDn = value.getString();
                        if (!allGroupDns.contains(parentDn)) {
                            nextLevelDns.add(parentDn);
                        }
                    }
                }
            }
            cursor.close();
        }

        if (!nextLevelDns.isEmpty()) {
            allGroupDns.addAll(nextLevelDns);
            resolveRecursiveGroupDnsViaMemberOf(connection, allGroupDns, depth + 1);
        }
    }

    private void resolveRecursiveGroupDnsInternal(LdapConnection connection, Set<String> allGroupDns, Set<String> visited, int depth) throws LdapException, CursorException, IOException {
        if (depth >= groupMaxDepth) {
            return;
        }

        Set<String> nextLevelDns = new HashSet<>();
        for (String dn : new HashSet<>(allGroupDns)) {
            if (visited.contains(dn)) {
                continue;
            }
            visited.add(dn);

            List<Entry> parents = getUserGroupsInternal(connection, dn, null);
            for (Entry parent : parents) {
                String parentDn = parent.getDn().toString();
                if (!allGroupDns.contains(parentDn)) {
                    nextLevelDns.add(parentDn);
                }
            }
        }

        if (!nextLevelDns.isEmpty()) {
            allGroupDns.addAll(nextLevelDns);
            resolveRecursiveGroupDnsInternal(connection, allGroupDns, visited, depth + 1);
        }
    }

    private String extractGroupNameFromDn(String groupDn) {
        if (groupDn.toLowerCase(Locale.ROOT).startsWith("cn=")) {
            int commaIdx = groupDn.indexOf(',');
            if (commaIdx > 0) {
                return groupDn.substring(3, commaIdx);
            }
            return groupDn.substring(3);
        }
        return null;
    }

    private List<Entry> getUserGroupsInternal(LdapConnection connection, String userDn, String username) throws LdapException, CursorException, IOException {
        List<Entry> groups = new ArrayList<>();
        String filter;
        if ("member".equals(groupMemberAttribute)) {
            filter = "(|(member=" + userDn + ")(uniqueMember=" + userDn + "))";
        } else {
            filter = "(|(memberUid=" + (username != null ? username : "") + ")(member=" + userDn + ")(uniqueMember=" + userDn + "))";
        }

        EntryCursor cursor = connection.search(groupSearchBase, filter, SearchScope.SUBTREE, "cn");
        while (cursor.next()) {
            groups.add(cursor.get());
        }
        cursor.close();
        return groups;
    }

    @Override
    public List<Entry> searchUsers(String filter, SchemaManager schemaManager) throws Exception {
        List<Entry> results = new ArrayList<>();
        LdapConnection connection = null;
        try {
            connection = getConnection();
            String ldapFilter = "(" + userIdentifierAttribute + "=" + filter.trim() + ")";
            EntryCursor cursor = connection.search(userSearchBase, ldapFilter, SearchScope.SUBTREE, "*", "+");
            while (cursor.next()) {
                Entry sourceEntry = cursor.get();
                Attribute idAttr = sourceEntry.get(userIdentifierAttribute);
                if (idAttr != null) {
                    Entry entry = createProxyEntry(sourceEntry, idAttr.getString(), connection, schemaManager);
                    results.add(entry);
                }
            }
            cursor.close();
            return results;
        } finally {
            releaseConnection(connection);
        }
    }

    private Entry createProxyEntry(Entry sourceEntry, String username, LdapConnection connection, SchemaManager schemaManager) throws Exception {
        Entry entry = new DefaultEntry(schemaManager);
        entry.setDn(sourceEntry.getDn());
        copyAttribute(sourceEntry, entry, "objectClass");
        copyAttribute(sourceEntry, entry, userIdentifierAttribute);
        if (!"uid".equals(userIdentifierAttribute)) {
            Attribute idAttr = sourceEntry.get(userIdentifierAttribute);
            if (idAttr != null) {
                entry.add("uid", idAttr.getString());
            }
        }
        for (String attributeType : proxyEntryAttributeTypes) {
            copyAttribute(sourceEntry, entry, attributeType);
        }

        Set<String> allGroupDns = new HashSet<>();
        if (useMemberOf) {
            Attribute memberOfAttr = sourceEntry.get("memberOf");
            if (memberOfAttr != null) {
                for (org.apache.directory.api.ldap.model.entry.Value value : memberOfAttr) {
                    allGroupDns.add(value.getString());
                }
            }
            if (recursiveGroupResolution && !allGroupDns.isEmpty()) {
                resolveRecursiveGroupDnsViaMemberOf(connection, allGroupDns, 0);
            }
        } else {
            List<Entry> initialGroups = getUserGroupsInternal(connection, sourceEntry.getDn().toString(), username);
            for (Entry group : initialGroups) {
                allGroupDns.add(group.getDn().toString());
            }
            if (recursiveGroupResolution && !allGroupDns.isEmpty()) {
                Set<String> visited = new HashSet<>();
                visited.add(sourceEntry.getDn().toString());
                resolveRecursiveGroupDnsInternal(connection, allGroupDns, visited, 0);
            }
        }

        for (String dn : allGroupDns) {
            entry.add(proxyEntryGroupMembershipAttributeType, dn);
        }

        return entry;
    }

    private void copyAttribute(Entry source, Entry target, String attributeName) throws LdapException {
        Attribute attr = source.get(attributeName);
        if (attr != null) {
            for (org.apache.directory.api.ldap.model.entry.Value value : attr) {
                target.add(attributeName, value.getString());
            }
        }
    }
}

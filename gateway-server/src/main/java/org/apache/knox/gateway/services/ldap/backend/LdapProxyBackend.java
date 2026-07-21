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

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
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
    private int port;
    private String host;

    private RemoteSchemaConverter remoteSchemaConverter;

    // Proxy configuration
    private String proxyBaseDn;  // Base DN for proxy entries (e.g., dc=proxy,dc=com)
    private String proxyUserSearchBase;
    private String proxyGroupSearchBase;

    // Backend configuration
    private String remoteBaseDn;  // Base DN for remote server searches (e.g., dc=hadoop,dc=apache,dc=org)
    private String remoteUserSearchBase;
    private String remoteGroupSearchBase;

    // Configurable attributes for AD/LDAP compatibility
    private String remoteUserIdentifierAttribute = "uid"; // uid for LDAP, sAMAccountName for AD
    private String userSearchFilter = "({userIdAttr}={username})"; // Will be populated with userIdentifierAttribute
    private String remoteGroupMemberAttribute = "memberUid"; // member for AD, memberUid for POSIX
    private String remoteUserObjectClass = "inetOrgPerson"; // user for AD, inetOrgPerson otherwise
    private String remoteGroupObjectClass = "groupofnames"; // group for AD, groupofnames otherwise
    private boolean useMemberOf; // Use memberOf attribute for group lookup (efficient for AD)
    private boolean recursiveGroupResolution;
    private int recursiveGroupResolutionMaxDepth;

    private final String proxyEntryGroupMembershipAttributeType = "memberOf";

    // Secure transport configuration for the connection to the remote LDAP server
    private boolean useSsl;                  // LDAPS (implicit TLS)
    private boolean trustAllCertificates;    // skip certificate validation (test/dev only)
    private String trustStorePath;
    private String trustStoreType;
    private String trustStorePassword;
    private String sslProtocol;
    private String[] enabledProtocols;
    private String[] enabledCipherSuites;
    // LDAP response timeout in milliseconds; negative means use the client default.
    private long connectionTimeoutMillis = -1;

    // Connection pool for efficient connection reuse
    private LdapConnectionPool connectionPool;

    public LdapProxyBackend(String name, Map<String, String> config) {
        this.name = name;

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

        // Proxy base DN is for entries created in the proxy LDAP server
        proxyBaseDn = config.get("baseDn");
        if (proxyBaseDn == null || proxyBaseDn.isEmpty()) {
            throw new IllegalArgumentException("baseDn is required for LDAP proxy backend");
        }
        // Search bases for the proxy server
        proxyUserSearchBase = "ou=people," + proxyBaseDn;
        proxyGroupSearchBase = "ou=groups," + proxyBaseDn;

        // Remote base DN is for searching the remote LDAP server
        remoteBaseDn = config.get("remoteBaseDn");
        if (remoteBaseDn == null || remoteBaseDn.isEmpty()) {
            throw new IllegalArgumentException("remoteBaseDn is required for LDAP proxy backend - this is the base DN of the remote LDAP server");
        }
        // Search bases for the remote server
        remoteUserSearchBase = config.getOrDefault("userSearchBase", "ou=people," + remoteBaseDn);
        remoteGroupSearchBase = config.getOrDefault("groupSearchBase", "ou=groups," + remoteBaseDn);
        // Configure attribute mappings for AD/LDAP compatibility
        remoteUserIdentifierAttribute = config.getOrDefault("userIdentifierAttribute", "uid");
        remoteGroupMemberAttribute = config.getOrDefault("groupMemberAttribute", "memberUid");
        remoteUserObjectClass = config.getOrDefault("userObjectClass", "inetOrgPerson");
        remoteGroupObjectClass = config.getOrDefault("groupObjectClass", "groupOfNames");

        remoteSchemaConverter = new RemoteSchemaConverter(proxyBaseDn,
                proxyUserSearchBase,
                proxyGroupSearchBase,
                remoteBaseDn,
                remoteUserSearchBase,
                remoteGroupSearchBase,
                remoteUserIdentifierAttribute,
                remoteUserObjectClass,
                remoteGroupObjectClass);

        // Configure group lookup
        useMemberOf = Boolean.parseBoolean(config.getOrDefault("useMemberOf", "false"));
        recursiveGroupResolution = Boolean.parseBoolean(config.getOrDefault("recursiveGroupResolution", "false"));
        recursiveGroupResolutionMaxDepth = Integer.parseInt(config.getOrDefault("recursiveGroupResolutionMaxDepth", "3"));

        // Configure secure transport (LDAPS) to the remote server. An ldaps:// URL enables it
        // by default; an explicit useSsl setting always wins.
        final boolean ldapsFromUrl = ldapUrl != null && ldapUrl.toLowerCase(Locale.ROOT).startsWith("ldaps://");
        useSsl = Boolean.parseBoolean(config.getOrDefault("useSsl", String.valueOf(ldapsFromUrl)));
        trustAllCertificates = Boolean.parseBoolean(config.getOrDefault("trustAllCertificates", "false"));
        trustStorePath = config.get("trustStore");
        trustStoreType = config.getOrDefault("trustStoreType", KeyStore.getDefaultType());
        trustStorePassword = config.get("trustStorePassword");
        sslProtocol = config.get("sslProtocol");
        enabledProtocols = splitToArray(config.get("enabledProtocols"));
        enabledCipherSuites = splitToArray(config.get("enabledCipherSuites"));
        final String timeout = config.get("connectionTimeout");
        if (timeout != null && !timeout.trim().isEmpty()) {
            connectionTimeoutMillis = Long.parseLong(timeout.trim());
        }

        // Build search filter template
        userSearchFilter = "(" + remoteUserIdentifierAttribute + "={username})";

        LOG.ldapBackendLoading(getName(), "Proxying " + proxyBaseDn + " to " + ldapUrl + " (" + remoteBaseDn + ") with " +
                remoteUserIdentifierAttribute + " attribute" +
                              (useMemberOf ? " using memberOf lookups" : " using group searches") +
                              (recursiveGroupResolution ? " with recursive group resolution (max depth: " + recursiveGroupResolutionMaxDepth + ")" : "") +
                              (useSsl ? " over LDAPS" : "") +
                              ((useSsl && trustAllCertificates) ? " (certificate validation disabled)" : ""));

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

    @Override
    public String getProxyBaseDn() {
        return proxyBaseDn;
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
        applySslConfiguration(connectionConfig);
        applyTimeout(connectionConfig);

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
     * Apply the configured LDAPS settings to a connection configuration. This is shared by the
     * pooled search connections and the short-lived authentication (bind) connection so both
     * talk to the remote server over the same secure transport.
     */
    private void applySslConfiguration(LdapConnectionConfig connectionConfig) {
        if (!useSsl) {
            return;
        }
        connectionConfig.setUseSsl(true);
        connectionConfig.setTrustManagers(buildTrustManagers());
        if (sslProtocol != null && !sslProtocol.isEmpty()) {
            connectionConfig.setSslProtocol(sslProtocol);
        }
        if (enabledProtocols != null) {
            connectionConfig.setEnabledProtocols(enabledProtocols);
        }
        if (enabledCipherSuites != null) {
            connectionConfig.setEnabledCipherSuites(enabledCipherSuites);
        }
    }

    /** Apply the configured LDAP response timeout, when set, to a connection configuration. */
    private void applyTimeout(LdapConnectionConfig connectionConfig) {
        if (connectionTimeoutMillis >= 0) {
            connectionConfig.setTimeout(connectionTimeoutMillis);
        }
    }

    /**
     * Build the trust managers used to validate the remote server's certificate:
     * <ul>
     *   <li>{@code trustAllCertificates=true} disables validation (test/dev only);</li>
     *   <li>a configured {@code trustStore} is loaded and used as the trust anchor;</li>
     *   <li>otherwise the JVM default trust store is used.</li>
     * </ul>
     */
    private TrustManager[] buildTrustManagers() {
        try {
            if (trustAllCertificates) {
                return new TrustManager[] { trustAllTrustManager() };
            }
            KeyStore trustStore = null;
            if (trustStorePath != null && !trustStorePath.isEmpty()) {
                trustStore = KeyStore.getInstance(trustStoreType);
                final char[] password = trustStorePassword == null ? null : trustStorePassword.toCharArray();
                try (InputStream is = Files.newInputStream(Paths.get(trustStorePath))) {
                    trustStore.load(is, password);
                }
            }
            // A null KeyStore makes the factory fall back to the JVM default trust store.
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            return tmf.getTrustManagers();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure trust material for LDAP backend " + getName(), e);
        }
    }

    /** A trust manager that accepts any certificate; only used when trustAllCertificates is set. */
    private static X509TrustManager trustAllTrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) { }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) { }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    private static String[] splitToArray(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toArray(String[]::new);
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

        // if userDN is using proxy base DN then convert to remote base dn
        final String remoteUserDnText = remoteSchemaConverter.convertProxyDnToRemoteDn(userDnText);

        // Create a temporary connection for authentication (bind)
        final LdapConnectionConfig authConfig = new LdapConnectionConfig();
        authConfig.setLdapHost(host);
        authConfig.setLdapPort(port);
        applySslConfiguration(authConfig);
        applyTimeout(authConfig);
        authConfig.setName(remoteUserDnText);
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
            try (EntryCursor cursor = connection.search(remoteUserSearchBase, filter, SearchScope.SUBTREE, "*")) {
                if (cursor.next()) {
                    Entry sourceEntry = cursor.get();
                    addGroupMemberships(sourceEntry, connection, createEntryCache(), createResolvedParentsCache());
                    return remoteSchemaConverter.convertRemoteEntryToProxyEntry(sourceEntry, schemaManager);

                }
            }
            return null;
        } finally {
            releaseConnection(connection);
        }
    }

    @Override
    public List<String> getUserGroups(String username, SchemaManager schemaManager) throws Exception {
        Entry user = getUser(username, schemaManager);
        if (user == null) {
            return List.of();
        }

        LdapConnection connection = null;
        try {
            connection = getConnection();
            List<Entry> groups = getUserGroupsEntries(connection, user, createEntryCache(), createResolvedParentsCache());
            List<String> cns = getCnsFromEntries(groups);
            return cns;
        } finally {
            releaseConnection(connection);
        }
    }

    @Override
    public List<Entry> searchUsers(String filter, SchemaManager schemaManager) throws Exception {
        List<Entry> results = new ArrayList<>();
        LdapConnection connection = null;
        Map<String, Entry> entryCache = createEntryCache();
        Map<String, Set<String>> resolvedParentsCache = createResolvedParentsCache();

        try {
            connection = getConnection();
            String ldapFilter = "(" + remoteUserIdentifierAttribute + "=" + filter.trim() + ")";
            try (EntryCursor cursor = connection.search(remoteUserSearchBase, ldapFilter, SearchScope.SUBTREE, "*")) {
                while (cursor.next()) {
                    Entry sourceEntry = cursor.get();
                    addGroupMemberships(sourceEntry, connection, entryCache, resolvedParentsCache);
                    results.add(remoteSchemaConverter.convertRemoteEntryToProxyEntry(sourceEntry, schemaManager));
                }
            }
            return results;
        } finally {
            releaseConnection(connection);
        }
    }

    @Override
    public List<Entry> search(String searchBase, SearchScope searchScope, String filter, SchemaManager schemaManager) throws Exception {
        LdapConnection connection = null;
        String remoteSearchBase = remoteSchemaConverter.convertProxyDnToRemoteDn(searchBase);
        String remoteFilter = remoteSchemaConverter.convertProxyFilterToRemoteFilter(filter, schemaManager);
        Map<String, Entry> entryCache = createEntryCache();
        Map<String, Set<String>> resolvedParentsCache = createResolvedParentsCache();

        try {
            connection = getConnection();
            List<Entry> results = new ArrayList<>();
            try (EntryCursor cursor = connection.search(remoteSearchBase, remoteFilter, searchScope, "*")) {
                while (cursor.next()) {
                    Entry entry = cursor.get();
                    addGroupMemberships(entry, connection, entryCache, resolvedParentsCache);
                    results.add(remoteSchemaConverter.convertRemoteEntryToProxyEntry(entry, schemaManager));
                }
            } catch (LdapException e) {
                LOG.ldapSearchFailed(remoteSearchBase, remoteFilter, e);
            }
            return results;
        } finally {
            releaseConnection(connection);
        }
    }

    private void addGroupMemberships(Entry entry, LdapConnection connection, Map<String, Entry> entryCache, Map<String, Set<String>> resolvedParentsCache) throws Exception {
        // The memberOf attribute is already populated on the entry. Further work is only needed
        // when using recursive group resolution or non using memberOf to find groups
        if (recursiveGroupResolution || !useMemberOf) {
            List<Entry> groups = getUserGroupsEntries(connection, entry, entryCache, resolvedParentsCache);
            for (Entry groupEntry : groups) {
                entry.add(proxyEntryGroupMembershipAttributeType, groupEntry.getDn().getName());
            }
        }
    }

    private List<Entry> getUserGroupsEntries(LdapConnection connection, Entry user, Map<String, Entry> entryCache, Map<String, Set<String>> resolvedParentsCache) throws Exception {
        List<Entry> groups = new ArrayList<>();
        if (useMemberOf) {
            // Use memberOf attribute for efficient AD lookups
            List<String> groupDns = getGroupsViaMemberOf(connection, user, entryCache, resolvedParentsCache);
            for (String groupDn : groupDns) {
                // every dn returned by getUserGroupsViaMemberOf must have been seen and saved in the entryCache
                Entry group = entryCache.get(groupDn);
                if (group != null) {
                    groups.add(group);
                } else {
                    LOG.ldapRecursiveGroupSearchExpectedGroupNotInCache(groupDn);
                }
            }
        } else {
            // Use traditional group search approach
            Dn userDn = user.getDn();
            groups = getUserGroupsInternal(connection, userDn);
            if (recursiveGroupResolution && !groups.isEmpty()) {
                groups = resolveGroupsRecursive(connection, groups, user.getDn().getName(), entryCache, resolvedParentsCache);
            }
        }

        return groups;
    }

    private List<String> getGroupsViaMemberOf(LdapConnection connection, Entry entry, Map<String, Entry> entryCache, Map<String, Set<String>> resolvedParentsCache) throws LdapException, CursorException, IOException {
        final List<String> groupDns = new ArrayList<>();
        // Use the memberOf attribute of the given entry
        final Attribute memberOfAttr = entry.get("memberOf");
        if (memberOfAttr != null) {
            for (Value value : memberOfAttr) {
                groupDns.add(value.getString());
            }
        }
        if (recursiveGroupResolution) {
            groupDns.addAll(getGroupsViaMemberOfRecursive(connection, entry.getDn().getName(), groupDns, entryCache, resolvedParentsCache));
        } else {
            // populate cache with skeletons
            groupDns.forEach(groupDn -> entryCache.put(groupDn, createSkeletonGroupEntry(groupDn)));
        }
        return groupDns;
    }

    private Set<String> getGroupsViaMemberOfRecursive(LdapConnection connection, String entryName, List<String> initialGroupDns, Map<String, Entry> entryCache, Map<String, Set<String>> resolvedParentsCache) {
        Set<String> accumulatedGroupDns = new HashSet<>();
        List<String> currentLevelGroupsDns = initialGroupDns;
        int depth = 0;
        while (!currentLevelGroupsDns.isEmpty() && depth < recursiveGroupResolutionMaxDepth) {
            logRecursiveSearchProgressViaMemberOf(entryName, currentLevelGroupsDns, depth);
            List<String> nextLevelGroupDns = new ArrayList<>();
            for (String groupDn : currentLevelGroupsDns) {
                Set<String> parents;
                if (resolvedParentsCache.containsKey(groupDn)) {
                    parents = resolvedParentsCache.get(groupDn);
                } else {
                    parents = populateParentCacheViaMemberOf(connection, groupDn, entryCache, resolvedParentsCache);
                }
                for (String parentDn : parents) {
                    if (!accumulatedGroupDns.contains(parentDn)) {
                        accumulatedGroupDns.add(parentDn);
                        nextLevelGroupDns.add(parentDn);
                    } else {
                        LOG.ldapRecursiveGroupSearchCycleDetected(entryName, parentDn);
                    }
                }
            }
            currentLevelGroupsDns = nextLevelGroupDns;
            depth++;

            if (depth == recursiveGroupResolutionMaxDepth && !currentLevelGroupsDns.isEmpty()) {
                LOG.ldapRecursiveGroupSearchMaxDepthReached(entryName, recursiveGroupResolutionMaxDepth);
            }
        }
        return accumulatedGroupDns;
    }

    private Set<String> populateParentCacheViaMemberOf(LdapConnection connection, String groupDn, Map<String, Entry> entryCache, Map<String, Set<String>> resolvedParentsCache) {
        Set<String> parents = new HashSet<>();
        Entry groupEntry;
        if (entryCache.containsKey(groupDn)) {
            groupEntry = entryCache.get((groupDn));
        } else {
            try {
                // Request "cn" alongside "memberOf" so the cached entry can be resolved to a
                // group name later (e.g. by getUserGroups); a memberOf-only lookup omits it.
                groupEntry = connection.lookup(groupDn, "cn", "memberOf");
            } catch (LdapException e) {
                groupEntry = null;
            }
            if (groupEntry == null) {
                // Entry not found or lookup failed — synthesise a skeleton so cn is still known.
                groupEntry = createSkeletonGroupEntry(groupDn);
            }
            entryCache.put(groupDn, groupEntry);
        }
        Attribute memberOf = groupEntry == null ? null : groupEntry.get("memberOf");
        if (memberOf != null) {
            for (Value value : memberOf) {
                parents.add(value.getNormalized());
            }
        }
        resolvedParentsCache.put(groupDn, parents);
        return parents;
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

    private List<Entry> resolveGroupsRecursive(LdapConnection connection, List<Entry> initialGroups, String entryName,
                                               Map<String, Entry> entryCache, Map<String, Set<String>> resolvedParentsCache) throws LdapException, CursorException, IOException {
        LOG.ldapRecursiveGroupSearchConfig(recursiveGroupResolution, recursiveGroupResolutionMaxDepth);

        Set<String> allGroupDns = new HashSet<>();
        List<Entry> allGroups = new ArrayList<>();
        List<Entry> currentLevelGroups = new ArrayList<>(initialGroups);

        for (Entry group : initialGroups) {
            allGroupDns.add(group.getDn().getNormName());
            allGroups.add(group);
        }

        logRecursiveSearchProgress(entryName, initialGroups, 0);

        int depth = 1;
        while (!currentLevelGroups.isEmpty() && depth < recursiveGroupResolutionMaxDepth) {
            List<Entry> nextLevelGroups = new ArrayList<>();
            List<Entry> groupsToSearch = new ArrayList<>();

            // Check cache first
            populateFromCache(entryCache, resolvedParentsCache, currentLevelGroups, allGroupDns, allGroups, nextLevelGroups, groupsToSearch);

            if (!groupsToSearch.isEmpty()) {
                List<Dn> groupDns = new ArrayList<>();
                for (Entry group : groupsToSearch) {
                    groupDns.add(group.getDn());
                }
                String filter = buildMultipleGroupMemberFilter(groupDns.toArray(new Dn[0]));

                try (EntryCursor cursor = connection.search(remoteGroupSearchBase, filter, SearchScope.SUBTREE, "cn", "memberUid", "member", "uniqueMember")) {
                    while (cursor.next()) {
                        Entry parentGroup = cursor.get();
                        String parentDn = parentGroup.getDn().getNormName();

                        // Update cache for all groups found in this search
                        updateCache(entryCache, resolvedParentsCache, groupsToSearch, parentGroup);

                        if (!allGroupDns.contains(parentDn)) {
                            allGroupDns.add(parentDn);
                            allGroups.add(parentGroup);
                            nextLevelGroups.add(parentGroup);
                        } else {
                            LOG.ldapRecursiveGroupSearchCycleDetected(entryName, parentDn);
                        }
                    }
                }

                // If some groups had no parents, we still need to mark them in cache to avoid re-searching
                for (Entry child : groupsToSearch) {
                    resolvedParentsCache.putIfAbsent(child.getDn().getNormName(), new HashSet<>());
                }
            }

            logRecursiveSearchProgress(entryName, nextLevelGroups, depth);
            currentLevelGroups = nextLevelGroups;
            depth++;

            if (depth == recursiveGroupResolutionMaxDepth && !currentLevelGroups.isEmpty()) {
                LOG.ldapRecursiveGroupSearchMaxDepthReached(entryName, recursiveGroupResolutionMaxDepth);
            }
        }

        LOG.ldapRecursiveGroupSearchFinished(entryName, allGroups.size());
        return allGroups;
    }

    private void populateFromCache(Map<String, Entry> entryCache, Map<String, Set<String>> resolvedParentsCache, List<Entry> currentLevelGroups, Set<String> allGroupDns, List<Entry> allGroups, List<Entry> nextLevelGroups, List<Entry> groupsToSearch) {
        for (Entry group : currentLevelGroups) {
            Set<String> parents = resolvedParentsCache.get(group.getDn().getNormName());
            if (parents != null) {
                LOG.ldapRecursiveGroupSearchCacheHit(group.getDn().getName(), parents.size());
                parents.forEach(parentDn -> {
                    Entry parent = entryCache.get(parentDn);
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

    private void updateCache(Map<String, Entry> entryCache, Map<String, Set<String>> resolvedParentsCache, List<Entry> groupsToSearch, Entry parentGroup) {
        String parentDn = parentGroup.getDn().getNormName();
        entryCache.putIfAbsent(parentDn, parentGroup);
        for (Entry child : groupsToSearch) {
            if (isMember(parentGroup, child.getDn())) {
                LOG.ldapRecursiveGroupSearchCacheAdd(child.getDn().getName(), parentGroup.getDn().getName());
                resolvedParentsCache.computeIfAbsent(child.getDn().getNormName(), k -> new HashSet<>()).add(parentDn);
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

    private void logRecursiveSearchProgressViaMemberOf(String entryName, List<String> groups, int depth) {
        LOG.ldapRecursiveGroupSearchProgress(entryName, groups.size(),
                groups.stream().collect(Collectors.joining(",")),
                depth);
    }

    private void logRecursiveSearchProgress(String entryName, List<Entry> groups, int depth) {
        LOG.ldapRecursiveGroupSearchProgress(entryName, groups.size(),
                groups.stream().map(e -> e.getDn().getRdn().getValue()).collect(Collectors.joining(",")),
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

    private List<Entry> getUserGroupsInternal(LdapConnection connection, Dn... dns) throws LdapException, CursorException, IOException {
        List<Entry> groups = new ArrayList<>();
        if (dns.length == 0) {
            return groups;
        }

        String filter = buildMultipleGroupMemberFilter(dns);

        try (EntryCursor cursor = connection.search(remoteGroupSearchBase, filter, SearchScope.SUBTREE, "cn")) {
            while (cursor.next()) {
                groups.add(cursor.get());
            }
        }

        return groups;
    }

    private String buildMultipleGroupMemberFilter(Dn... dns) {
        StringBuilder filterBuilder = new StringBuilder();
        filterBuilder.append("(|");
        for (Dn dn : dns) {
            String dnString = dn.toString();
            String rdnString = dn.getRdn().getValue();
            // Search for groups where user is a member - build filter based on configuration
            if ("member".equals(remoteGroupMemberAttribute)) {
                // AD style - uses full DN
                filterBuilder.append("(member=");
                filterBuilder.append(dnString);
                filterBuilder.append(")(uniqueMember=");
                filterBuilder.append(dnString);
                filterBuilder.append(")");
            } else {
                // POSIX style - uses username
                filterBuilder.append("(memberUid=");
                filterBuilder.append(rdnString);
                filterBuilder.append(")(member=");
                filterBuilder.append(dnString);
                filterBuilder.append(")(uniqueMember=");
                filterBuilder.append(dnString);
                filterBuilder.append(")");
            }
        }
        filterBuilder.append(")");
        return filterBuilder.toString();
    }

    private List<String> getCnsFromEntries(Collection<Entry> entries) throws LdapException {
        List<String> cns = new ArrayList<>();
        for (Entry entry : entries) {
            Attribute cnAttr = entry.get("cn");
            if (cnAttr != null) {
                cns.add(cnAttr.getString());
            } else if (entry.getDn() != null && entry.getDn().getRdn() != null) {
                // Fall back to the CN carried in the DN when the entry was fetched without the
                // cn attribute, so resolved groups are not silently dropped from the result.
                cns.add(entry.getDn().getRdn().getValue());
            }
        }
        return cns;
    }

    protected Map<String, Entry> createEntryCache() {
        return new HashMap<>();
    }

    /**
     * Factory method for the resolved parents cache.
     * Overridden in tests to verify caching behavior.
     */
    protected Map<String, Set<String>> createResolvedParentsCache() {
        return new HashMap<>();
    }
}

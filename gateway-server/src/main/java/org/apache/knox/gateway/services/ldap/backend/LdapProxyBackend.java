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

import static java.util.Locale.ROOT;

import org.apache.commons.lang3.StringUtils;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.cursor.SearchCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapInvalidDnException;
import org.apache.directory.api.ldap.model.exception.LdapOperationException;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.FilterParser;
import org.apache.directory.api.ldap.model.message.LdapResult;
import org.apache.directory.api.ldap.model.message.Response;
import org.apache.directory.api.ldap.model.message.ResultCodeEnum;
import org.apache.directory.api.ldap.model.message.SearchRequest;
import org.apache.directory.api.ldap.model.message.SearchRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchResultDone;
import org.apache.directory.api.ldap.model.message.SearchResultEntry;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.message.controls.PagedResults;
import org.apache.directory.api.ldap.model.message.controls.PagedResultsImpl;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    private static final String[] ALL_ATTRIBUTES = new String[]{"*"};
    private static final String[] ALL_ATTRIBUTES_AND_MEMBEROF = new String[]{"*", "memberOf"};

    private static final String DEFAULT_MAX_FILTER_LENGTH = "0"; // unlimited

    static final String TYPE = "ldap";

    private final String name;
    private String bindDn;
    private String bindPassword;
    private int port;
    private String host;

    private final RemoteSchemaConverter remoteSchemaConverter;

    // Proxy configuration
    private final String proxyBaseDn;  // Base DN for proxy entries (e.g., dc=proxy,dc=com)
    private final String proxyUserSearchBase;
    private final String proxyGroupSearchBase;
    private final boolean dnMappingEnabled;

    // Backend configuration
    private final String remoteBaseDn;  // Base DN for remote server searches (e.g., dc=hadoop,dc=apache,dc=org)
    private final String remoteUserSearchBase;
    private final String remoteGroupSearchBase;

    // Configurable attributes for AD/LDAP compatibility
    private String remoteUserIdentifierAttribute = "uid"; // uid for LDAP, sAMAccountName for AD
    private String userSearchFilter = "({userIdAttr}={username})"; // Will be populated with userIdentifierAttribute
    private String remoteGroupMemberAttribute = "memberUid"; // member for AD, memberUid for POSIX
    private String remoteUserObjectClass = "inetOrgPerson"; // user for AD, inetOrgPerson otherwise
    private String remoteGroupObjectClass = "groupofnames"; // group for AD, groupofnames otherwise
    private final boolean useMemberOf; // Use memberOf attribute for group lookup (efficient for AD)
    private final boolean recursiveGroupResolution;
    private final int recursiveGroupResolutionMaxDepth;
    private final int pageSize;
    private final int maxResultSetSize;
    private final int maxFilterLength;

    private final String proxyEntryGroupMembershipAttributeType = "memberOf";

    // Secure transport configuration for the connection to the remote LDAP server
    private final boolean useSsl;                  // LDAPS (implicit TLS)
    private final boolean trustAllCertificates;    // skip certificate validation (test/dev only)
    private final String trustStorePath;
    private final String trustStoreType;
    private final String trustStorePassword;
    private final String sslProtocol;
    private final String[] enabledProtocols;
    private final String[] enabledCipherSuites;
    // LDAP response timeout in milliseconds; negative means use the client default.
    private long connectionTimeoutMillis = -1;

    // Connection pool for efficient connection reuse
    private LdapConnectionPool connectionPool;

    public LdapProxyBackend(String name, Map<String, String> config) {
        this.name = name;

        // Support both url and host/port configuration
        String ldapUrl = config.get("url");
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

        dnMappingEnabled = Boolean.parseBoolean(config.get("dnMappingEnabled"));

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
                remoteGroupObjectClass,
                dnMappingEnabled);

        // Configure group lookup
        useMemberOf = Boolean.parseBoolean(config.getOrDefault("useMemberOf", "false"));
        recursiveGroupResolution = Boolean.parseBoolean(config.getOrDefault("recursiveGroupResolution", "false"));
        recursiveGroupResolutionMaxDepth = Integer.parseInt(config.getOrDefault("recursiveGroupResolutionMaxDepth", "3"));

        // Configure search parameters
        pageSize = Integer.parseInt(config.getOrDefault("pageSize", "1000"));
        maxResultSetSize = Integer.parseInt(config.getOrDefault("maxResultSetSize", "0")); // 0 means unlimited
        LOG.ldapInterceptorConfiguring(name, "maxResultSetSize", Integer.toString(maxResultSetSize));
        maxFilterLength = Integer.parseInt(config.getOrDefault("maxFilterLength", DEFAULT_MAX_FILTER_LENGTH)); // 0 means unlimited
        LOG.ldapInterceptorConfiguring(name, "maxFilterLength", Integer.toString(maxFilterLength));

        // Configure secure transport (LDAPS) to the remote server. A ldaps:// URL enables it
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
    public boolean isSupportedSearchBase(String searchBase) {
        // System/operational searches (ou=schema, cn=config, root-DSE) must not be forwarded.
        // Only support user and group searches against either the proxy or remote base Dn.
        if (searchBase == null) {
            return false;
        }
        try {
            Dn searchBaseDn = new Dn(searchBase);
            return searchBaseDn.isDescendantOf(proxyBaseDn) ||
                    searchBaseDn.isDescendantOf(remoteBaseDn);
        } catch (LdapInvalidDnException e) {
            return false;
        }
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

        // Don't allow anonymous or unauthenticated bind
        if (StringUtils.isBlank(userDnText) || StringUtils.isBlank(password)) {
            return false;
        }

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
            LOG.ldapAuthSucceeded(getName(), userDnText);
            return true;
        } catch (Exception e) {
            LOG.ldapAuthFailed(getName(), userDnText, e);
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
            Entry sourceEntry = null;
            try (EntryCursor cursor = connection.search(remoteUserSearchBase, filter, SearchScope.SUBTREE, "*")) {
                if (cursor.next()) {
                    sourceEntry = cursor.get();
                }
            }
            if (sourceEntry != null) {
                addGroupMemberships(sourceEntry, connection, createEntryCache(), createResolvedParentsCache());
                return remoteSchemaConverter.convertRemoteEntryToProxyEntry(sourceEntry, schemaManager);
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

        List<String> groups = new ArrayList<>();
        Attribute groupsAttribute = user.get(proxyEntryGroupMembershipAttributeType);
        if (groupsAttribute != null) {
            for (Value value : groupsAttribute) {
                groups.add(new Dn(value.getString()).getRdn().getValue());
            }
        }
        return groups;
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
            List<Entry> searchResults = performPagedSearch(connection, remoteUserSearchBase, ldapFilter, SearchScope.SUBTREE, "*");
            for (Entry sourceEntry : searchResults) {
                addGroupMemberships(sourceEntry, connection, entryCache, resolvedParentsCache);
                results.add(remoteSchemaConverter.convertRemoteEntryToProxyEntry(sourceEntry, schemaManager));
            }
            return results;
        } finally {
            releaseConnection(connection);
        }
    }

    @Override
    public List<Entry> search(String searchBase, SearchScope searchScope, String filter, SchemaManager schemaManager) throws Exception {
        // Filter likely has already been annotated by other interceptors.
        // Clean the filter by removing any modifications or annotations
        String rawFilter = filter.replaceAll(":\\[.*?\\]", "").replaceAll("=\\s+", "=");
        ExprNode filterNode = FilterParser.parse(schemaManager, rawFilter);

        // NOTE: mapping memberOf must occur before schema conversion
        filterNode = mapMemberOfExpression(filterNode, schemaManager);

        LdapConnection connection = null;
        String remoteSearchBase = remoteSchemaConverter.convertProxyDnToRemoteDn(searchBase);
        ExprNode remoteFilter = remoteSchemaConverter.convertProxyFilterToRemoteFilter(filterNode, schemaManager);
        Map<String, Entry> entryCache = createEntryCache();
        Map<String, Set<String>> resolvedParentsCache = createResolvedParentsCache();

        try {
            connection = getConnection();
            List<Entry> results = new ArrayList<>();
            String[] attributes = useMemberOf ? ALL_ATTRIBUTES_AND_MEMBEROF : ALL_ATTRIBUTES;
            List<Entry> searchResults = performSplitSearch(connection, remoteSearchBase, splitFilter(remoteFilter.toString()), searchScope, attributes);
            for (Entry entry : searchResults) {
                addGroupMemberships(entry, connection, entryCache, resolvedParentsCache);
                results.add(remoteSchemaConverter.convertRemoteEntryToProxyEntry(entry, schemaManager));
            }
            return results;
        } finally {
            releaseConnection(connection);
        }
    }

    private ExprNode mapMemberOfExpression(ExprNode filter, SchemaManager schemaManager) throws Exception {
        if (useMemberOf || !filter.toString().toLowerCase(ROOT).contains("memberof")) {
            return filter;
        }
        // Convert filter if it contains "memberOf" but the backend doesn't support "memberOf"
        MemberOfMappingVisitor visitor = new MemberOfMappingVisitor(schemaManager, this::lookupGroup, dn -> isUser(dn, schemaManager));
        // Baseline in the same form as the visitor output so an unchanged visit exits on the first pass
        String current = filter.toString();
        int depth = recursiveGroupResolution ? recursiveGroupResolutionMaxDepth : 1;
        for (int i = 0; i < depth; ++i) {
            Object visitedNode;
            try {
                visitedNode = filter.accept(visitor);
            } catch (Exception e) {
                throw new LdapException("Failed to resolve group members of " + current, e);
            }
            if (!(visitedNode instanceof ExprNode)) {
                throw new LdapException("Unable to transform memberOf expressions in filter: " + filter);
            }
            filter = (ExprNode) visitedNode;
            String updated = filter.toString();
            if (updated.equals(current)) {
                break;
            }
            current = updated;
        }
        // TODO remove any remaining memberOf clauses?
        return filter;
    }

    private Entry lookupGroup(String dn, SchemaManager schemaManager) throws Exception {
        String remoteDn = remoteSchemaConverter.convertProxyDnToRemoteDn(dn);
        LdapConnection connection = null;
        try {
            connection = getConnection();
            Entry entry = connection.lookup(remoteDn);
            if  (entry != null) {
                return remoteSchemaConverter.convertRemoteEntryToProxyEntry(entry, schemaManager);
            } else {
                return null;
            }
        } finally {
            releaseConnection(connection);
        }
    }

    private boolean isUser(String userDnString, SchemaManager schemaManager) {
        if (userDnString == null) {
            return false;
        }
        try {
            Dn userDn = new Dn(schemaManager, userDnString);
            return userDn.isDescendantOf(proxyUserSearchBase) ||
                    userDn.isDescendantOf(remoteUserSearchBase);
        } catch (LdapInvalidDnException e) {
            return false;
        }
    }

    private void addGroupMemberships(Entry entry, LdapConnection connection, Map<String, Entry> entryCache, Map<String, Set<String>> resolvedParentsCache) throws LdapException, CursorException, IOException {
        // The memberOf attribute is already populated on the entry. Further work is only needed
        // when using recursive group resolution or non using memberOf to find groups
        if (recursiveGroupResolution || !useMemberOf) {
            List<Entry> groups = getUserGroupsEntries(connection, entry, entryCache, resolvedParentsCache);
            for (Entry groupEntry : groups) {
                entry.add(proxyEntryGroupMembershipAttributeType, groupEntry.getDn().getName());
            }
        }
    }

    private List<Entry> getUserGroupsEntries(LdapConnection connection, Entry user, Map<String, Entry> entryCache, Map<String, Set<String>> resolvedParentsCache) throws LdapException, CursorException, IOException {
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

    private List<String> getGroupsViaMemberOf(LdapConnection connection, Entry entry, Map<String, Entry> entryCache, Map<String, Set<String>> resolvedParentsCache) {
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
                // Entry not found or lookup failed — synthesize a skeleton so cn is still known.
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
                List<String> filters = buildMultipleGroupMemberFilters(groupDns.toArray(new Dn[0]));

                List<Entry> searchResults = performSplitSearch(connection, remoteGroupSearchBase, filters, SearchScope.SUBTREE, "cn", "memberUid", "member", "uniqueMember");
                for (Entry parentGroup : searchResults) {
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
                String.join(",", groups),
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

        groups.addAll(performSplitSearch(connection, remoteGroupSearchBase, buildMultipleGroupMemberFilters(dns), SearchScope.SUBTREE, "cn"));

        return groups;
    }

    /**
     * Splits a filter that exceeds maxFilterLength into several filters whose combined results are
     * equivalent to the original filter. A filter that can't be split is returned as is.
     */
    private List<String> splitFilter(String filter) throws LdapException {
        if (maxFilterLength <= 0 || filter.length() <= maxFilterLength) {
            return List.of(filter);
        }
        try {
            // Remote attributes may be unknown to the local schema so parse without a schema manager
            List<ExprNode> pieces = FilterSplitter.split(FilterParser.parse(filter), maxFilterLength);
            LOG.ldapFilterSplit(filter.length(), maxFilterLength, pieces.size());
            return pieces.stream().map(ExprNode::toString).collect(Collectors.toList());
        } catch (java.text.ParseException e) {
            throw new LdapException("Unable to split oversized filter: " + e.getMessage(), e);
        }
    }

    /**
     * Executes each filter as a separate paged search and combines the results, removing entries
     * returned by more than one query. Stops early once maxResultSetSize entries have been collected.
     */
    protected List<Entry> performSplitSearch(LdapConnection connection, String baseDn, List<String> filters, SearchScope scope, String... attributes) throws LdapException, CursorException, IOException {
        if (filters.size() == 1) {
            return performPagedSearch(connection, baseDn, filters.get(0), scope, attributes);
        }
        Map<String, Entry> combined = new LinkedHashMap<>();
        for (String filter : filters) {
            for (Entry entry : performPagedSearch(connection, baseDn, filter, scope, attributes)) {
                combined.putIfAbsent(entry.getDn().getNormName(), entry);
            }
            if (maxResultSetSize != 0 && combined.size() >= maxResultSetSize) {
                break;
            }
        }
        return new ArrayList<>(combined.values());
    }

    protected List<Entry> performPagedSearch(LdapConnection connection, String baseDn, String filter, SearchScope scope, String... attributes ) throws LdapException, CursorException, IOException {
        List<Entry> results = new ArrayList<>();

        // 1. Setup basic search parameters
        SearchRequest searchRequest = new SearchRequestImpl();
        searchRequest.setBase(new Dn(baseDn));
        searchRequest.setFilter(filter);
        searchRequest.setScope(scope);
        searchRequest.addAttributes(attributes);

        // 2. Initialize the PagedResults control
        PagedResults pagedControl = new PagedResultsImpl();
        pagedControl.setSize(pageSize);
        searchRequest.addControl(pagedControl);

        byte[] cookie = null;

        // 3. Loop until no more pages remain
        int pageNumber = 1;
        do {
            // Update cookie for the subsequent pages
            if (cookie != null) {
                pagedControl.setCookie(cookie);
            }

            try (SearchCursor cursor = connection.search(searchRequest)) {
                LOG.ldapPagedSearch(baseDn, filter, pageSize, pageNumber);
                while (cursor.next()) {
                    Response response = cursor.get();

                    // Process matching entries
                    if (response instanceof SearchResultEntry) {
                        Entry entry = ((SearchResultEntry) response).getEntry();
                        results.add(entry);
                    }
                }
                if (cursor.isDone()) {
                    SearchResultDone done = cursor.getSearchResultDone();

                    if (done != null) {
                        LdapResult ldapResult = done.getLdapResult();
                        if (ldapResult.getResultCode() != ResultCodeEnum.SUCCESS) {
                            throw new LdapOperationException(ldapResult.getResultCode(), ldapResult.getDiagnosticMessage());
                        }
                        PagedResults responseControl = (PagedResults) done.getControl(PagedResults.OID);

                        if (responseControl != null) {
                            cookie = responseControl.getCookie();
                        } else {
                            cookie = null;
                        }
                    } else {
                        throw new LdapException("The LDAP search operation failed to complete fully.");
                    }
                }
                pageNumber++;
            }
        } while (cookie != null && cookie.length > 0 &&
                (maxResultSetSize == 0 || results.size() < maxResultSetSize));

        if (maxResultSetSize != 0 && results.size() >= maxResultSetSize) {
            LOG.ldapPagedSearchExceededMaxResultSetSize(results.size(), maxResultSetSize);
        } else {
            LOG.ldapPagedSearchCompleted(baseDn, filter, results.size());
        }

        return results;
    }

    /**
     * Builds the filters that find the groups the given DNs are members of. The DNs are spread over
     * as many filters as needed to keep each filter within maxFilterLength.
     */
    private List<String> buildMultipleGroupMemberFilters(Dn... dns) {
        List<String> filters = new ArrayList<>();
        StringBuilder filterBuilder = new StringBuilder("(|");
        boolean empty = true;
        for (Dn dn : dns) {
            String clause = buildGroupMemberClause(dn);
            // +1 for the closing parenthesis
            if (!empty && maxFilterLength > 0 && filterBuilder.length() + clause.length() + 1 > maxFilterLength) {
                filters.add(filterBuilder.append(")").toString());
                filterBuilder = new StringBuilder("(|");
            }
            filterBuilder.append(clause);
            empty = false;
        }
        filters.add(filterBuilder.append(")").toString());
        return filters;
    }

    private String buildGroupMemberClause(Dn dn) {
        String dnString = dn.toString();
        // Search for groups where user is a member - build filter based on configuration
        if ("member".equals(remoteGroupMemberAttribute)) {
            // AD style - uses full DN
            return "(member=" + dnString + ")(uniqueMember=" + dnString + ")";
        }
        // POSIX style - uses username
        return "(memberUid=" + dn.getRdn().getValue() + ")(member=" + dnString + ")(uniqueMember=" + dnString + ")";
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

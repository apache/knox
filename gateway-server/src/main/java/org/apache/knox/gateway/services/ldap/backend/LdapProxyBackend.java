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
import org.apache.directory.api.ldap.model.exception.LdapNoSuchObjectException;
import org.apache.directory.api.ldap.model.exception.LdapOperationException;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.FilterEncoder;
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
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.api.ldap.model.schema.AttributeType;
import org.apache.directory.api.ldap.model.schema.LdapSyntax;
import org.apache.directory.api.ldap.model.schema.MatchingRule;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.ldap.model.schema.UsageEnum;
import org.apache.directory.api.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.ldap.client.api.DefaultLdapConnectionFactory;
import org.apache.directory.ldap.client.api.DefaultSchemaLoader;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * LDAP backend that proxies to an external LDAP server.
 * Can use central LDAP configuration or backend-specific configuration.
 */
public class LdapProxyBackend implements LdapBackend {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    private static final String[] ALL_ATTRIBUTES = new String[]{"*"};
    private static final String MEMBER_OF = "memberOf";
    private static final String OBJECT_CLASS = "objectClass";

    // Generic syntax used when importing a remote attribute whose declared syntax is not in the
    // bundled schema. Octet string accepts any value, so the attribute's values still pass through.
    private static final String OCTET_STRING_SYNTAX_OID = "1.3.6.1.4.1.1466.115.121.1.40";
    // Generic equality matching rule used when a remote attribute declares no equality (or one the
    // bundled schema cannot resolve). octetStringMatch pairs with the octet string syntax fallback and
    // compares raw bytes, so the attribute can still be used in equality assertions.
    private static final String OCTET_STRING_MATCH_OID = "2.5.13.17";
    // Generic ordering matching rule used when a remote attribute declares no ordering (or one the
    // bundled schema cannot resolve). Without it a strict schema rejects a range (>=, <=) filter on the
    // attribute at parse time (ERR_13301_NO_ORDERING_MR_FOR_AT), which blocks AD delta-sync clients that
    // filter with e.g. (uSNChanged>=N). The real comparison runs on the remote server's own schema.
    private static final String OCTET_STRING_ORDERING_MATCH_OID = "2.5.13.18";
    // Generic equality matching rule used as the fallback for a human-readable attribute that declares no
    // resolvable equality. A string attribute must keep a string matching rule: octetStringMatch compares
    // raw bytes and throws a ClassCastException when applied to a String value (e.g. during the
    // deduplication contains() check in RemoteSchemaConverter.copyAttribute).
    private static final String CASE_IGNORE_MATCH_OID = "2.5.13.2";

    private static final String DEFAULT_MAX_FILTER_LENGTH = "0"; // unlimited

    static final String TYPE = "ldap";

    private final String name;
    private String bindDn;
    private String bindPassword;
    private int port;
    private String host;

    private final RemoteSchemaConverter remoteSchemaConverter;
    // Operator-configured remote<->proxy attribute name mapping (e.g. whenChanged<->modifyTimestamp).
    // Applied when converting remote entries, forwarding requested attributes, and rewriting filters.
    private final AttributeNameMapper attributeNameMapper;

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

    // Secure transport configuration for the connection to the remote LDAP server
    private final boolean useSsl;                  // LDAPS (implicit TLS)
    private final boolean trustAllCertificates;    // skip certificate validation (test/dev only)
    private final String trustStorePath;
    private final String trustStoreType;
    private final String trustStorePassword;
    private final String sslProtocol;
    private final String[] enabledProtocols;
    private final String[] enabledCipherSuites;
    // Operator-declared attributes to import from the remote server's schema so the proxy can
    // recognise and request them (e.g. operational attributes like whenChanged). Only these are
    // imported; the remote schema is never trusted wholesale. May be null when none are configured.
    private final String[] extraAttributes;
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

        // Parse the operator-configured attribute name mapping (remoteName:proxyName pairs) before
        // building the converter so it can rename attributes on remote entries and in filters.
        attributeNameMapper = AttributeNameMapper.fromConfig(config.get("attributeMappings"));

        remoteSchemaConverter = new RemoteSchemaConverter(proxyBaseDn,
                proxyUserSearchBase,
                proxyGroupSearchBase,
                remoteBaseDn,
                remoteUserSearchBase,
                remoteGroupSearchBase,
                remoteUserIdentifierAttribute,
                remoteUserObjectClass,
                remoteGroupObjectClass,
                dnMappingEnabled,
                attributeNameMapper);

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
        extraAttributes = splitToArray(config.get("extraAttributes"));
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
            Dn searchBaseDn = new Dn(searchBase.toLowerCase(ROOT));
            return searchBaseDn.isDescendantOf(proxyBaseDn.toLowerCase(ROOT)) ||
                    searchBaseDn.isDescendantOf(remoteBaseDn.toLowerCase(ROOT));
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
     * Imports the operator-declared {@code extraAttributes} from the remote LDAP server's schema
     * into the shared schema manager, so the proxy can recognise and request them (e.g. operational
     * attributes like whenChanged). Only the configured attributes are imported — the remote schema
     * is never trusted wholesale — and each imported definition comes from the remote server, so its
     * syntax and usage are authoritative. Attribute types already registered (by OID) are left
     * untouched, so the hardcoded definitions in {@code SchemaManagerFactory} always win. A configured
     * attribute that the remote server does not publish is logged as an error and skipped.
     * <p>
     * Any failure (the remote server being unreachable, not publishing a schema, or a single
     * attribute type that cannot be resolved against the bundled syntaxes/matching rules) is
     * logged and skipped rather than propagated, so a backend's schema being unavailable never
     * prevents the embedded LDAP service from starting.
     */
    @Override
    public void loadRemoteSchema(SchemaManager schemaManager) {
        if (extraAttributes == null || extraAttributes.length == 0) {
            // Nothing declared to import; do not even contact the remote server.
            return;
        }
        LdapConnection connection = null;
        try {
            connection = getConnection();
            // Quirks mode (the second argument) relaxes the schema parser so non-RFC-4512 schemas
            // load instead of failing. Active Directory in particular publishes attribute type and
            // object class descriptions with non-numeric OIDs and missing elements that the strict
            // parser rejects with ERR_13787_OID_EXPECTED.
            final DefaultSchemaLoader remoteLoader = new DefaultSchemaLoader(connection, true);
            final SchemaManager remoteSchemaManager = new DefaultSchemaManager(remoteLoader);
            // Relaxed loading so an incomplete or non-standard remote schema yields what it can
            // instead of failing wholesale.
            remoteSchemaManager.loadAllEnabledRelaxed();
            importConfiguredAttributeTypes(schemaManager, remoteSchemaManager);
        } catch (Exception e) {
            LOG.ldapRemoteSchemaLoadFailed(getName(), e);
        } finally {
            releaseConnection(connection);
        }
    }

    /**
     * Imports each configured {@code extraAttributes} entry from {@code remoteSchemaManager} into
     * {@code schemaManager}, using the remote server's own definition. Attributes the remote server
     * does not publish are logged and skipped. Separated from the connection handling in
     * {@link #loadRemoteSchema(SchemaManager)} so the import can be exercised without a live server.
     */
    void importConfiguredAttributeTypes(SchemaManager schemaManager, SchemaManager remoteSchemaManager) {
        if (extraAttributes == null) {
            return;
        }
        int imported = 0;
        for (String attributeName : extraAttributes) {
            final AttributeType remoteType = remoteSchemaManager.getAttributeType(attributeName);
            if (remoteType == null || remoteType.getOid() == null) {
                // Configured but not published by the remote server: almost always a typo or an
                // attribute that does not exist on this directory. Surface it rather than ignore it.
                LOG.ldapConfiguredAttributeNotInRemoteSchema(getName(), attributeName);
                continue;
            }
            if (registerRemoteAttributeType(schemaManager, remoteType)) {
                imported++;
            }
        }
        LOG.ldapRemoteSchemaLoaded(getName(), imported);
    }

    /**
     * Registers a single remote attribute type into the target schema manager, unless an attribute
     * type with the same OID is already present. The definition is rebuilt as a standalone attribute
     * type whose syntax and matching rules are expressed by OID so that the target manager resolves
     * them against its own (bundled) registries; its superior chain is flattened for the same reason.
     *
     * @return true if a new attribute type was added, false if it already existed or could not be added
     */
    boolean registerRemoteAttributeType(SchemaManager schemaManager, AttributeType remoteType) {
        final String oid = remoteType.getOid();
        if (oid == null || schemaManager.getAttributeTypeRegistry().contains(oid)) {
            return false;
        }
        try {
            final AttributeType attributeType = toStandaloneAttributeType(schemaManager, remoteType);
            final boolean addedToManager = schemaManager.add(attributeType);
            if (!addedToManager) {
                // add() records the reason (e.g. an unresolved syntax) in the manager's error list
                // rather than throwing; surface it and move on so one bad definition is skipped.
                LOG.ldapRemoteAttributeTypeSkipped(getName(), remoteType.getName(), oid, String.valueOf(schemaManager.getErrors()));
                return false;
            }
            return true;
        } catch (LdapException e) {
            LOG.ldapRemoteAttributeTypeError(getName(), remoteType.getName(), oid, e);
            return false;
        }
    }

    /**
     * Builds a standalone copy of a remote attribute type that the strict, bundled schema manager
     * can register. Remote servers — Active Directory in particular — define attributes with syntaxes,
     * matching rules and usage/modifiability combinations the bundled schema does not accept, so the
     * copy is normalised:
     * <ul>
     *   <li>A syntax not present in the bundled schema (e.g. the AD LargeInteger syntax
     *       {@code 1.2.840.113556.1.4.906}) falls back to octet string, which accepts any value, so the
     *       attribute's values still pass through unchanged — this proxy does not evaluate them.</li>
     *   <li>Matching rules the bundled schema cannot resolve are dropped rather than failing the add.</li>
     *   <li>A non-user-modifiable attribute is forced to operational usage, because a strict schema
     *       rejects a non-modifiable USER-APPLICATIONS attribute (RFC 4512, ERR_13763).</li>
     * </ul>
     * The schema name is set to "other" to match the convention used for the hardcoded attribute types.
     */
    private AttributeType toStandaloneAttributeType(SchemaManager schemaManager, AttributeType remoteType) {
        final AttributeType attributeType = new AttributeType(remoteType.getOid());
        final List<String> names = remoteType.getNames();
        if (names != null && !names.isEmpty()) {
            attributeType.setNames(names.toArray(new String[0]));
        }
        attributeType.setSchemaName("other");
        attributeType.setDescription(remoteType.getDescription());
        attributeType.setSingleValued(remoteType.isSingleValued());
        attributeType.setCollective(remoteType.isCollective());
        attributeType.setUserModifiable(remoteType.isUserModifiable());
        attributeType.setUsage(normalizedUsage(remoteType));
        final String syntaxOid = resolvedSyntaxOid(schemaManager, remoteType);
        // The matching-rule fallbacks must match the value representation of the resolved syntax: a
        // binary (octet string) syntax takes the octet-string rules (byte comparison), a human-readable
        // syntax takes a string rule. Pairing an octet-string rule with a human-readable value throws a
        // ClassCastException when the value is compared (see CASE_IGNORE_MATCH_OID).
        final boolean octetStringSyntax = OCTET_STRING_SYNTAX_OID.equals(syntaxOid);
        attributeType.setSyntaxOid(syntaxOid);
        attributeType.setEqualityOid(resolvedEqualityOid(schemaManager, remoteType, octetStringSyntax));
        attributeType.setOrderingOid(resolvedOrderingOid(schemaManager, remoteType, octetStringSyntax));
        attributeType.setSubstringOid(resolvedMatchingRuleOid(schemaManager, remoteType.getSubstring(), remoteType.getSubstringOid()));
        return attributeType;
    }

    /**
     * A non-user-modifiable attribute must be operational in a strict schema; some servers (notably
     * Active Directory) publish such attributes as USER-APPLICATIONS, which would be rejected.
     */
    private static UsageEnum normalizedUsage(AttributeType remoteType) {
        final UsageEnum usage = remoteType.getUsage();
        if (!remoteType.isUserModifiable() && usage == UsageEnum.USER_APPLICATIONS) {
            return UsageEnum.DIRECTORY_OPERATION;
        }
        return usage;
    }

    /**
     * Returns the remote attribute's syntax OID when the bundled schema knows it, otherwise octet
     * string so the attribute still registers and its values pass through untouched.
     */
    private String resolvedSyntaxOid(SchemaManager schemaManager, AttributeType remoteType) {
        final LdapSyntax syntax = remoteType.getSyntax();
        final String syntaxOid = syntax != null ? syntax.getOid() : remoteType.getSyntaxOid();
        if (syntaxOid != null && schemaManager.getLdapSyntaxRegistry().contains(syntaxOid)) {
            return syntaxOid;
        }
        LOG.ldapRemoteAttributeSyntaxFallback(getName(), remoteType.getName(), String.valueOf(syntaxOid));
        return OCTET_STRING_SYNTAX_OID;
    }

    /**
     * Returns the matching rule OID only when the bundled schema can resolve it; an unknown matching
     * rule is dropped (returns null) rather than failing the whole attribute registration.
     */
    private static String resolvedMatchingRuleOid(SchemaManager schemaManager, MatchingRule matchingRule, String declaredOid) {
        final String oid = matchingRule != null ? matchingRule.getOid() : declaredOid;
        return oid != null && schemaManager.getMatchingRuleRegistry().contains(oid) ? oid : null;
    }

    /**
     * Returns the equality matching rule OID to register for the attribute. For a binary (octet string)
     * syntax the octet-string rule is forced: the values are opaque bytes, and a string rule — including
     * any rule the remote declared for the attribute's original, now-dropped syntax — would compare them
     * incorrectly (or throw). For a human-readable syntax the remote's declared rule is kept when the
     * bundled schema resolves it, otherwise it falls back to caseIgnoreMatch rather than leaving the
     * attribute without an equality rule, so the imported attribute remains usable in equality
     * assertions. (A strict schema cannot evaluate an equality filter against an attribute that has
     * none.) The fallback is never an octet-string rule on a human-readable value, which would throw a
     * ClassCastException when the value is compared.
     */
    private String resolvedEqualityOid(SchemaManager schemaManager, AttributeType remoteType, boolean octetStringSyntax) {
        if (octetStringSyntax) {
            return OCTET_STRING_MATCH_OID;
        }
        final String resolved = resolvedMatchingRuleOid(schemaManager, remoteType.getEquality(), remoteType.getEqualityOid());
        if (resolved != null) {
            return resolved;
        }
        LOG.ldapRemoteAttributeEqualityFallback(getName(), remoteType.getName(), String.valueOf(remoteType.getEqualityOid()));
        return CASE_IGNORE_MATCH_OID;
    }

    /**
     * Returns the ordering matching rule OID to register for the attribute. For a binary (octet string)
     * syntax it supplies octetStringOrderingMatch so the attribute still supports range (>=, <=)
     * assertions: Active Directory delta/incremental-sync clients issue range filters such as
     * {@code (uSNChanged>=N)}, and without an ordering rule a strict schema rejects the filter at parse
     * time with ERR_13301_NO_ORDERING_MR_FOR_AT. The octet-string rule compares raw bytes, matching the
     * octet string syntax; the backend forwards the real comparison to the remote server, which applies
     * its own schema. For a human-readable syntax the remote's declared ordering rule is kept when the
     * bundled schema resolves it, otherwise none is invented (a string ordering rule is not synthesised
     * here). The octet-string fallback is used only when the bundled schema actually defines the rule, so
     * an environment without it keeps the drop-to-null behaviour rather than failing.
     */
    private String resolvedOrderingOid(SchemaManager schemaManager, AttributeType remoteType, boolean octetStringSyntax) {
        if (octetStringSyntax) {
            if (!schemaManager.getMatchingRuleRegistry().contains(OCTET_STRING_ORDERING_MATCH_OID)) {
                return null;
            }
            LOG.ldapRemoteAttributeOrderingFallback(getName(), remoteType.getName(), String.valueOf(remoteType.getOrderingOid()));
            return OCTET_STRING_ORDERING_MATCH_OID;
        }
        return resolvedMatchingRuleOid(schemaManager, remoteType.getOrdering(), remoteType.getOrderingOid());
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
        Attribute groupsAttribute = user.get(MEMBER_OF);
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
    public List<Entry> search(String searchBase, SearchScope searchScope, String filter, SchemaManager schemaManager,
                              String[] attributes, long sizeLimit, int timeLimit) throws Exception {
        // Null or empty requested attributes mean "all user attributes"; normalize to "*" so the
        // remote search always has an explicit attribute list and the memberOf handling below sees a consistent value.
        if (attributes == null || attributes.length == 0) {
            attributes = ALL_ATTRIBUTES;
        }
        boolean memberRequested = isAttributeRequested(attributes, "member", schemaManager)
                || isAttributeRequested(attributes, "uniqueMember", schemaManager);
        boolean memberOfRequested = isAttributeRequested(attributes, MEMBER_OF, schemaManager);
        String[] remoteAttributes = toRemoteAttributes(attributes, memberOfRequested && useMemberOf);

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
            List<Entry> searchResults;
            try {
                searchResults = performSplitSearch(connection, remoteSearchBase, splitFilter(remoteFilter.toString()), searchScope, sizeLimit, timeLimit, remoteAttributes);
            } catch (LdapOperationException e) {
                if (e.getResultCode() == ResultCodeEnum.NO_SUCH_OBJECT) {
                    // Tell the caller that the search base itself does not exist in the remote directory
                    throw new LdapNoSuchObjectException("No such entry in backend " + name + ": " + searchBase + " (" + e.getMessage() + ")");
                }
                throw e;
            }
            Map<String, List<String>> nestedGroupMembersCache = new HashMap<>();
            for (Entry entry : searchResults) {
                if (memberOfRequested) {
                    addGroupMemberships(entry, connection, entryCache, resolvedParentsCache);
                }
                if (memberRequested && recursiveGroupResolution) {
                    addNestedGroupMembers(entry, connection, schemaManager, nestedGroupMembersCache);
                }
                results.add(remoteSchemaConverter.convertRemoteEntryToProxyEntry(entry, schemaManager));
            }
            return results;
        } finally {
            releaseConnection(connection);
        }
    }

    private String[] toRemoteAttributes(String[] attributes, boolean addMemberOf) {
        // Translate the client-supplied (proxy-facing) names to their remote names first, so the
        // proxy's own required remote attributes appended below are never themselves remapped. A
        // mapping whose proxy name happened to collide with e.g. memberOf would otherwise rewrite
        // the attribute the proxy adds for its own use and break group resolution.
        final Set<String> remote = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        remote.addAll(Arrays.asList(mapRequestedAttributesToRemote(attributes)));

        // The LDAP proxy treats memberOf as a user attribute. MemberOf is an operational attribute on some LDAP servers.
        // Explicitly add memberOf to the attribute list for remote searches if it is a requested attribute.
        if (addMemberOf) {
            remote.add(MEMBER_OF);
        }

        // Add required attributes used by the LDAP proxy for entity conversions
        if (!remote.contains("*")) {
            remote.add(OBJECT_CLASS);
            remote.add(remoteUserIdentifierAttribute);
        }

        return remote.toArray(new String[0]);
    }

    /**
     * Translates operator-mapped proxy attribute names in a requested-attribute list to the remote
     * server's names, so the remote search asks for the name the server actually knows (e.g. a
     * client request for {@code modifyTimestamp} is forwarded as {@code whenChanged}). Unmapped
     * names (including {@code "*"}) pass through unchanged, and the array is only copied when a
     * mapping is actually configured.
     *
     * @param attributes the requested attributes, in proxy-facing names
     * @return the attributes translated to remote names
     */
    String[] mapRequestedAttributesToRemote(String[] attributes) {
        if (attributes == null || attributeNameMapper.isEmpty()) {
            return attributes;
        }
        final String[] remoteAttributes = new String[attributes.length];
        for (int i = 0; i < attributes.length; i++) {
            remoteAttributes[i] = attributeNameMapper.toRemoteName(attributes[i]);
        }
        return remoteAttributes;
    }

    /**
     * Returns true if the requested attributes include the given attribute or "*". Requested
     * attributes may be names or OIDs, so they are compared by attribute type OID via the schema.
     * Null or empty attributes indicates all attributes (i.e., "*")
     */
    private boolean isAttributeRequested(String[] requestedAttributes, String attribute, SchemaManager schemaManager) {
        if (requestedAttributes == null || requestedAttributes.length == 0) {
            return true;
        }
        final AttributeType attributeType = schemaManager.getAttributeType(attribute);
        for (String requested : requestedAttributes) {
            if ("*".equals(requested) || attribute.equalsIgnoreCase(requested)) {
                return true;
            }
            if (attributeType != null && requested != null) {
                final AttributeType requestedType = schemaManager.getAttributeType(requested);
                if (requestedType != null && attributeType.getOid().equals(requestedType.getOid())) {
                    return true;
                }
            }
        }
        return false;
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
                entry.add(MEMBER_OF, groupEntry.getDn().getName());
            }
        }
    }

    /**
     * When recursive group resolution is enabled, expands a group entry's member list to include the
     * members of the nested groups it references, transitively. Unlike the memberOf path, retrieving a
     * group returns its member attribute as the backend stores it (direct members only), so the nesting
     * is resolved here: a member that is itself a group has its members merged in, up to
     * {@code recursiveGroupResolutionMaxDepth} levels, guarding against cycles.
     * <p>
     * Which members are groups to recurse into is decided authoritatively by object class rather than by
     * DN location: each level issues one batched search that matches the level's candidate DNs and
     * restricts to the group object classes, so the server returns only those candidates that are
     * actually groups. This stays correct when users and groups share a container (a group DN that is a
     * descendant of the user search base would otherwise be mistaken for a user and not expanded) while
     * only fetching the groups, not every member. The entry is still a raw remote entry, so its member
     * values - and the DNs searched - are remote DNs that are rewritten to proxy DNs later during schema
     * conversion. Entries without members (e.g. users) are unchanged.
     */
    private void addNestedGroupMembers(Entry entry, LdapConnection connection, SchemaManager schemaManager,
                                       Map<String, List<String>> nestedGroupMembersCache) throws LdapException, CursorException, IOException {
        final List<String> directMembers = collectGroupMemberDns(entry);
        if (directMembers.isEmpty()) {
            return;
        }
        // Seed the cycle guards with the group's own DN so a nested group that references it back does not
        // re-add it (which would make the group a member of itself) or cause a redundant re-expansion.
        final String ownNormDn = normalizeDn(entry.getDn().getName(), schemaManager);
        final Set<String> knownMembers = new HashSet<>();
        knownMembers.add(ownNormDn);
        final Set<String> visitedGroups = new HashSet<>();
        visitedGroups.add(ownNormDn);
        // Merge nested members into whichever membership attribute the entry already uses, so a
        // groupOfUniqueNames keeps one uniqueMember list instead of also gaining a stray member attribute.
        final String memberAttribute = entry.get("member") != null ? "member" : "uniqueMember";
        // Candidate member DNs whose type is not yet known; the batched group search below classifies them.
        List<String> currentCandidates = new ArrayList<>();
        for (String memberDn : directMembers) {
            if (knownMembers.add(normalizeDn(memberDn, schemaManager))) {
                currentCandidates.add(memberDn);
            }
        }
        for (int depth = 0; !currentCandidates.isEmpty() && depth < recursiveGroupResolutionMaxDepth; depth++) {
            final List<String> toExpand = new ArrayList<>();
            for (String memberDn : currentCandidates) {
                if (visitedGroups.add(normalizeDn(memberDn, schemaManager))) {
                    toExpand.add(memberDn);
                }
            }
            if (toExpand.isEmpty()) {
                break;
            }
            final List<String> nextCandidates = new ArrayList<>();
            for (List<String> members : expandGroups(connection, toExpand, schemaManager, nestedGroupMembersCache).values()) {
                for (String memberDn : members) {
                    if (knownMembers.add(normalizeDn(memberDn, schemaManager))) {
                        entry.add(memberAttribute, memberDn);
                        nextCandidates.add(memberDn);
                    }
                }
            }
            currentCandidates = nextCandidates;
        }
    }

    /**
     * Resolves the direct member/uniqueMember DNs of the given candidate DNs that are groups. A single
     * batched search (split if needed to honor maxFilterLength) matches the candidates by RDN and
     * restricts to the group object classes, so candidates that are not groups (e.g. users) are filtered
     * out by the server and never fetched. Results are matched back to the requested DNs exactly to guard
     * against an RDN collision elsewhere in the subtree, and cached per search (including an empty result
     * for a candidate that is not a group) so a group reached by several paths is read only once.
     *
     * @return a map from each resolved group's normalized DN to its member DNs
     */
    private Map<String, List<String>> expandGroups(LdapConnection connection, List<String> candidateDns, SchemaManager schemaManager,
                                                   Map<String, List<String>> cache) throws LdapException, CursorException, IOException {
        final Map<String, List<String>> resolved = new LinkedHashMap<>();
        final Map<String, String> toSearch = new LinkedHashMap<>(); // normalized DN -> original DN
        for (String dn : candidateDns) {
            final String norm = normalizeDn(dn, schemaManager);
            final List<String> cached = cache.get(norm);
            if (cached != null) {
                resolved.put(norm, cached);
            } else {
                toSearch.putIfAbsent(norm, dn);
            }
        }
        if (toSearch.isEmpty()) {
            return resolved;
        }
        // Seed an empty result for every searched DN so a candidate that is not a group is not re-searched.
        toSearch.keySet().forEach(norm -> cache.put(norm, List.of()));

        final String filter = buildGroupByDnFilter(toSearch.values());
        if (filter == null) {
            return resolved;
        }
        final List<Entry> groups = performSplitSearch(connection, remoteGroupSearchBase, splitFilter(filter),
                SearchScope.SUBTREE, "member", "uniqueMember");
        for (Entry group : groups) {
            final String norm = normalizeDn(group.getDn().getName(), schemaManager);
            if (!toSearch.containsKey(norm)) {
                continue; // an RDN match for an entry outside the requested set
            }
            final List<String> members = collectGroupMemberDns(group);
            cache.put(norm, members);
            resolved.put(norm, members);
        }
        return resolved;
    }

    /**
     * Builds the filter that fetches the given candidate DNs that are groups, matching each candidate by
     * its RDN and restricting to the group object classes so non-group candidates are excluded by the
     * server. Returns {@code null} when none of the candidates yields a usable RDN clause.
     */
    private String buildGroupByDnFilter(Collection<String> candidateDns) {
        final StringBuilder clauses = new StringBuilder();
        for (String dn : candidateDns) {
            final String clause = rdnClause(dn);
            if (clause != null) {
                clauses.append(clause);
            }
        }
        if (clauses.length() == 0) {
            return null;
        }
        return "(&" + groupObjectClassFilter() + "(|" + clauses + "))";
    }

    /** A filter clause matching an entry by its RDN, or {@code null} when the DN cannot be parsed. */
    private String rdnClause(String dn) {
        try {
            // Parse without a schema manager: the value is a remote DN whose attributes the local schema may not know.
            final Rdn rdn = new Dn(dn).getRdn();
            return "(" + rdn.getType() + "=" + FilterEncoder.encodeFilterValue(rdn.getValue()) + ")";
        } catch (LdapException e) {
            return null;
        }
    }

    /**
     * An object-class filter matching group entries: the configured group object class plus the standard
     * groupOfNames and groupOfUniqueNames, de-duplicated case-insensitively.
     */
    private String groupObjectClassFilter() {
        final Set<String> seen = new LinkedHashSet<>();
        final StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String objectClass : new String[] {remoteGroupObjectClass, "groupOfNames", "groupOfUniqueNames"}) {
            if (objectClass != null && seen.add(objectClass.toLowerCase(ROOT))) {
                sb.append("(objectClass=").append(objectClass).append(")");
                count++;
            }
        }
        return count == 1 ? sb.toString() : "(|" + sb + ")";
    }

    /** Collects an entry's member and uniqueMember values; empty for an entry that is not a group. */
    private List<String> collectGroupMemberDns(Entry entry) {
        final List<String> members = new ArrayList<>();
        for (String attributeName : new String[] {"member", "uniqueMember"}) {
            final Attribute attribute = entry.get(attributeName);
            if (attribute != null) {
                for (Value value : attribute) {
                    members.add(value.getString());
                }
            }
        }
        return members;
    }

    /** A whitespace- and case-normalized key for a DN, for de-duplicating members and detecting cycles. */
    private String normalizeDn(String dn, SchemaManager schemaManager) {
        try {
            return new Dn(schemaManager, dn).getNormName();
        } catch (LdapException e) {
            // The proxy schema may not know a remote RDN attribute (e.g. sAMAccountName in a DN). Fall
            // back to a schema-less DN, which still normalizes structure (whitespace, RDN order, type
            // case) so two spellings of the same DN compare equal; only if that also fails lowercase the
            // raw string.
            try {
                return new Dn(dn).getNormName();
            } catch (LdapException e2) {
                return dn.toLowerCase(Locale.ROOT);
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
            LOG.ldapSkeletonGroupEntryError(groupDn, e);
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
        return performSplitSearch(connection, baseDn, filters, scope, 0L, 0, attributes);
    }

    /**
     * {@link #performSplitSearch(LdapConnection, String, List, SearchScope, String...)} that also honors a
     * per-request size and time limit. The effective number of entries returned is capped by the smaller of
     * the configured {@code maxResultSetSize} and {@code sizeLimit} (either {@code 0} meaning unlimited), and
     * {@code timeLimit} (seconds, {@code 0} meaning unlimited) bounds each underlying search.
     */
    protected List<Entry> performSplitSearch(LdapConnection connection, String baseDn, List<String> filters, SearchScope scope, long sizeLimit, int timeLimit, String... attributes) throws LdapException, CursorException, IOException {
        final long effectiveMax = effectiveSizeLimit(sizeLimit);
        if (filters.size() == 1) {
            return performPagedSearch(connection, baseDn, filters.get(0), scope, sizeLimit, timeLimit, attributes);
        }
        Map<String, Entry> combined = new LinkedHashMap<>();
        for (String filter : filters) {
            // Only ask each successive request for the entries still needed: reduce the size limit by
            // the number of (deduplicated) entries already collected. A size limit of 0 stays unlimited.
            final long requestSizeLimit = sizeLimit <= 0 ? 0 : sizeLimit - combined.size();
            if (sizeLimit > 0 && requestSizeLimit <= 0) {
                break;
            }
            for (Entry entry : performPagedSearch(connection, baseDn, filter, scope, requestSizeLimit, timeLimit, attributes)) {
                combined.putIfAbsent(entry.getDn().getNormName(), entry);
            }
            if (effectiveMax != 0 && combined.size() >= effectiveMax) {
                break;
            }
        }
        if (effectiveMax == 0) {
            return new ArrayList<>(combined.values());
        } else {
            return combined.values().stream()
                    .limit(effectiveMax)
                    .collect(Collectors.toList());
        }
    }

    protected List<Entry> performPagedSearch(LdapConnection connection, String baseDn, String filter, SearchScope scope, String... attributes ) throws LdapException, CursorException, IOException {
        return performPagedSearch(connection, baseDn, filter, scope, 0L, 0, attributes);
    }

    /**
     * {@link #performPagedSearch(LdapConnection, String, String, SearchScope, String...)} that also honors a
     * per-request size and time limit. The number of entries returned is capped by the smaller of the
     * configured {@code maxResultSetSize} and {@code sizeLimit} (either {@code 0} meaning unlimited), and the
     * {@code timeLimit} (seconds, {@code 0} meaning unlimited) is passed to the remote server on each page.
     */
    protected List<Entry> performPagedSearch(LdapConnection connection, String baseDn, String filter, SearchScope scope, long sizeLimit, int timeLimit, String... attributes ) throws LdapException, CursorException, IOException {
        final long effectiveMax = effectiveSizeLimit(sizeLimit);
        List<Entry> results = new ArrayList<>();

        // 1. Setup basic search parameters
        SearchRequest searchRequest = new SearchRequestImpl();
        searchRequest.setBase(new Dn(baseDn));
        searchRequest.setFilter(filter);
        searchRequest.setScope(scope);
        searchRequest.addAttributes(attributes);
        // Bound the search on the remote server by time. The size limit is enforced client-side (below)
        // so paging is not interrupted by a server-side sizeLimitExceeded while results are still arriving.
        if (timeLimit > 0) {
            searchRequest.setTimeLimit(timeLimit);
        }

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
                (effectiveMax == 0 || results.size() < effectiveMax));

        if (effectiveMax != 0 && results.size() >= effectiveMax) {
            LOG.ldapPagedSearchExceededMaxResultSetSize(results.size(), (int) Math.min(effectiveMax, Integer.MAX_VALUE));
            return results.stream()
                    .limit(effectiveMax)
                    .collect(Collectors.toList());
        } else {
            LOG.ldapPagedSearchCompleted(baseDn, filter, results.size());
            return results;
        }
    }

    /**
     * Combines the configured {@code maxResultSetSize} with a per-request {@code sizeLimit} into a single
     * cap on the number of entries returned. Either value being {@code 0} (or negative) means "no limit" for
     * that source; when both impose a limit the smaller one wins. Returns {@code 0} when neither limits.
     */
    private long effectiveSizeLimit(long sizeLimit) {
        final long request = sizeLimit > 0 ? sizeLimit : 0;
        final long configured = maxResultSetSize > 0 ? maxResultSetSize : 0;
        if (request == 0) {
            return configured;
        }
        if (configured == 0) {
            return request;
        }
        return Math.min(request, configured);
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

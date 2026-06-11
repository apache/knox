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
package org.apache.knox.gateway.services.ldap;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.apache.directory.api.asn1.util.Oid;
import org.apache.directory.api.ldap.codec.api.LdapApiService;
import org.apache.directory.api.ldap.codec.api.LdapApiServiceFactory;
import org.apache.directory.api.ldap.model.cursor.Cursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.message.SearchRequest;
import org.apache.directory.api.ldap.model.message.SearchRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.DnFactory;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.interceptor.Interceptor;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.knox.gateway.config.ConfigurationException;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.control.RolesLookupBypassControlFactory;
import org.apache.knox.gateway.services.ldap.interceptor.InterceptorFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Manages the ApacheDS LDAP server instance with pluggable backends
 */
public class KnoxLDAPServerManager {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    @VisibleForTesting
    DirectoryService directoryService;
    private LdapServer ldapServer;
    private GatewayConfig gatewayConfig;
    private List<Interceptor> interceptors;
    private File workDir;
    private int port;
    private String baseDn;
    // Collection of DNs for the proxied backend LDAP servers
    private Set<String> baseDns;
    private String rolesLookupBypassControlOid;

    /**
     * Initialize the LDAP server with the given configuration
     *
     * @param config Gateway configuration
     */
    public void initialize(GatewayConfig config) throws Exception {
        this.gatewayConfig = config;

        // Prepare work directory for LDAP data
        File gatewayDataDir = new File(config.getGatewayDataDir());
        this.workDir = new File(gatewayDataDir, "ldap-server");

        // Get configuration
        this.port = config.getLDAPPort();
        this.baseDn = config.getLDAPBaseDN();

        // Get OID for roles lookup bypass control
        rolesLookupBypassControlOid = gatewayConfig.getLdapRolesLookupBypassControlOid();
        if (StringUtils.isNotBlank(rolesLookupBypassControlOid)) {
            if (!Oid.isOid(rolesLookupBypassControlOid)) {
                throw new ConfigurationException("Roles Lookup Bypass Control OID is not valid");
            }
        }

        createInterceptors(config);

        // Clean up previous run if it didn't shut down cleanly
        File lockFile = new File(workDir, "run/instance.lock");
        if (lockFile.exists()) {
            LOG.ldapCleaningLockFile(lockFile.getAbsolutePath());
            lockFile.delete();
        }

        workDir.mkdirs();
    }

    private void createInterceptors(GatewayConfig config) throws Exception {
        List<String> interceptorNames = config.getLDAPInterceptorNames();
        List<Interceptor> interceptors = new ArrayList<>(interceptorNames.size());
        for (String interceptorName : interceptorNames) {
            // Get backend-specific configuration using prefixed properties
            final Map<String, String> interceptorConfig = config.getLDAPInterceptorConfig(interceptorName);

            // Add common configuration
            interceptorConfig.put("baseDn", baseDn);

            // Add common LDAP Proxy configurations to backends
            if ("backend".equalsIgnoreCase(interceptorConfig.get("interceptorType"))) {
                interceptorConfig.put("recursiveGroupResolution", String.valueOf(config.isLDAPRecursiveGroupResolutionEnabled()));
                interceptorConfig.put("recursiveGroupResolutionMaxDepth", String.valueOf(config.getLDAPRecursiveGroupResolutionMaxDepth()));
                if ("file".equalsIgnoreCase(interceptorConfig.get("backendType")) && !interceptorConfig.containsKey("dataFile")) {
                    // Add legacy dataFile property for backwards compatibility with file backend
                    interceptorConfig.put("dataFile", config.getLDAPBackendDataFile());
                }
            }

            interceptors.add(InterceptorFactory.createInterceptor(config, interceptorName, interceptorConfig));
        }
        this.interceptors = interceptors;
    }

    /**
     * Start the LDAP server
     */
    public void start() throws Exception {
        LOG.ldapServiceStarting(port, baseDn);

        // Initialize DirectoryService
        directoryService = new DefaultDirectoryService();
        directoryService.setInstanceLayout(new InstanceLayout(workDir));

        // Add RolesLookupBypassControlFactory
        if (StringUtils.isNotBlank(rolesLookupBypassControlOid)) {
            LdapApiService apiService = directoryService.getLdapCodecService();
            if (apiService == null) {
                apiService = LdapApiServiceFactory.getSingleton();
            }
            RolesLookupBypassControlFactory rolesLookupBypassControlFactory = new RolesLookupBypassControlFactory(apiService, rolesLookupBypassControlOid);
            apiService.registerRequestControl(rolesLookupBypassControlFactory);
        }

        // Create SchemaManager
        SchemaManager schemaManager = SchemaManagerFactory.createSchemaManager();
        directoryService.setSchemaManager(schemaManager);

        // Initialize schema partition
        LdifPartition schemaPartition = new LdifPartition(schemaManager, directoryService.getDnFactory());
        schemaPartition.setPartitionPath(new File(workDir, "schema").toURI());
        SchemaPartition schemaLdifPartition = new SchemaPartition(schemaManager);
        schemaLdifPartition.setWrappedPartition(schemaPartition);
        directoryService.setSchemaPartition(schemaLdifPartition);

        // Create system partition (required)
        JdbmPartition systemPartition = new JdbmPartition(schemaManager, directoryService.getDnFactory());
        systemPartition.setId("system");
        systemPartition.setSuffixDn(new Dn(schemaManager, "ou=system"));
        systemPartition.setPartitionPath(new File(workDir, "system").toURI());
        directoryService.setSystemPartition(systemPartition);

        // Create our custom partition for proxy base DN
        JdbmPartition partition = new JdbmPartition(schemaManager, directoryService.getDnFactory());
        partition.setId("proxy");
        partition.setSuffixDn(new Dn(schemaManager, baseDn));
        partition.setPartitionPath(new File(workDir, "proxy").toURI());
        directoryService.addPartition(partition);

        baseDns = new HashSet<>();
        baseDns.add(baseDn);
        addRemotePartitions();

        addInterceptors();

        // Allow anonymous access
        directoryService.setAllowAnonymousAccess(true);

        // Start the service
        directoryService.startup();

        // Add base entries to the partitions
        createBaseEntries(baseDns, schemaManager);

        // Create LDAP server on configured port
        ldapServer = new LdapServer();
        ldapServer.setTransports(new TcpTransport(port));
        ldapServer.setDirectoryService(directoryService);

        ldapServer.start();

        LOG.ldapServiceStarted(port);
    }

    private void addRemotePartitions() throws LdapException {
        SchemaManager schemaManager = directoryService.getSchemaManager();
        DnFactory dnFactory = directoryService.getDnFactory();
        List<String> interceptorNames = gatewayConfig.getLDAPInterceptorNames();
        Map<String, Integer> idCountMap = new HashMap<>();
        for (String interceptorName : interceptorNames) {
            // Get backend-specific configuration using prefixed properties
            Map<String, String> interceptorConfig = gatewayConfig.getLDAPInterceptorConfig(interceptorName);

            String remoteBaseDn = interceptorConfig.get("remoteBaseDn");
            if (StringUtils.isNotBlank(remoteBaseDn)) {
                if (!baseDns.contains(remoteBaseDn)) {
                    //create partition
                    String id = interceptorName.replaceAll("\\s+", "");
                    if (idCountMap.containsKey(id)) {
                        int count = idCountMap.get(id);
                        idCountMap.put(id, count + 1);
                        // add suffix to ensure unique id
                        id = id + count;
                    } else {
                        idCountMap.put(id, 0);
                    }
                    JdbmPartition remotePartition = new JdbmPartition(schemaManager, dnFactory);
                    remotePartition.setId(id);
                    remotePartition.setSuffixDn(new Dn(schemaManager, remoteBaseDn));
                    remotePartition.setPartitionPath(new File(workDir, id).toURI());
                    directoryService.addPartition(remotePartition);
                    baseDns.add(remoteBaseDn);
                }
            }
        }
    }

    private void addInterceptors() throws LdapException {
        // Find location of AuthenticationInterceptor.
        // We need to insert interceptors before AuthenticationInterceptor to intercept bind requests
        final List<Interceptor> dsInterceptors = new ArrayList<>(directoryService.getInterceptors());
        final int authIdx = fetchAuthenticationInterceptorIndex(dsInterceptors);

        // Add our configured interceptors for group lookups and bind proxying
        for (Interceptor interceptor : interceptors) {
            if (authIdx != -1) {
                dsInterceptors.add(authIdx, interceptor);
            } else {
                dsInterceptors.add(interceptor);
            }
        }
        directoryService.setInterceptors(dsInterceptors);
    }

    private int fetchAuthenticationInterceptorIndex(final List<Interceptor> dsInterceptors) {
        return IntStream.range(0, dsInterceptors.size())
                .filter(i -> "authenticationInterceptor".equalsIgnoreCase(dsInterceptors.get(i).getName()))
                .findFirst()
                .orElse(-1);
    }

    /**
     * Stop the LDAP server
     */
    public void stop() throws Exception {
        LOG.ldapServiceStopping(port);

        if (ldapServer != null) {
            try {
                ldapServer.stop();
                ldapServer = null;
            } catch (Exception e) {
                LOG.ldapServiceStopFailed(e);
            }
        }

        if (directoryService != null) {
            try {
                directoryService.shutdown();
                directoryService = null;
            } catch (Exception e) {
                LOG.ldapServiceStopFailed(e);
            }
        }

        LOG.ldapServiceStopped();
    }

    private void createBaseEntries(Collection<String> baseDns, SchemaManager schemaManager) throws Exception {
        // Create base entries for proxy base DN and remote base DNs
        for (String baseDn : baseDns) {
            createBaseEntriesForDn(schemaManager, baseDn);
        }
    }

    private void createBaseEntriesForDn(SchemaManager schemaManager, String dn) throws Exception {
        Dn baseDnName = new Dn(schemaManager, dn);
        if (!directoryService.getAdminSession().exists(baseDnName)) {
            Entry baseDnEntry = new DefaultEntry(schemaManager);
            baseDnEntry.setDn(baseDnName);
            baseDnEntry.add("objectClass", "top", "domain");
            // Extract dc value from baseDn (e.g., "dc=proxy,dc=com" -> "proxy")
            String dcValue = dn.split(",")[0].split("=")[1];
            baseDnEntry.add("dc", dcValue);
            directoryService.getAdminSession().add(baseDnEntry);
        }

        Dn usersOuDn = new Dn(schemaManager, "ou=people," + dn);
        if (!directoryService.getAdminSession().exists(usersOuDn)) {
            Entry usersOu = new DefaultEntry(schemaManager);
            usersOu.setDn(usersOuDn);
            usersOu.add("objectClass", "top", "organizationalUnit");
            usersOu.add("ou", "people");
            directoryService.getAdminSession().add(usersOu);
        }

        Dn groupsOuDn = new Dn(schemaManager, "ou=groups," + dn);
        if (!directoryService.getAdminSession().exists(groupsOuDn)) {
            Entry groupsOu = new DefaultEntry(schemaManager);
            groupsOu.setDn(groupsOuDn);
            groupsOu.add("objectClass", "top", "organizationalUnit");
            groupsOu.add("ou", "groups");
            directoryService.getAdminSession().add(groupsOu);
        }
    }

    public int getPort() {
        return port;
    }

    public String getBaseDn() {
        return baseDn;
    }

    /**
     * Get groups for a user from the configured backend
     * @param username The username
     * @return List of group names
     */
    public List<String> getUserGroups(String username) throws Exception {
        SearchRequest searchRequest = new SearchRequestImpl();
        searchRequest.setBase(new Dn(directoryService.getSchemaManager(), baseDn));
        searchRequest.setScope(SearchScope.SUBTREE);
        searchRequest.setFilter("(uid=" + username + ")");
        searchRequest.addAttributes("*");

        List<String> groups = new ArrayList<>();
        try (Cursor<Entry> cursor = directoryService.getAdminSession().search(searchRequest)) {
            if (cursor.next()) {
                Entry entry = cursor.get();
                Attribute memberOf = entry.get("memberOf");
                if (memberOf != null) {
                    for (Value value : memberOf) {
                        String groupDn = value.getString();
                        if (groupDn.toLowerCase(Locale.ROOT).startsWith("cn=")) {
                            int commaIdx = groupDn.indexOf(',');
                            if (commaIdx > 0) {
                                groups.add(groupDn.substring(3, commaIdx));
                            }
                        }

                    }
                }
            }
        }
        return groups;
    }

    /**
     * Check if the LDAP server is currently running.
     *
     * @return true if the server is running, false otherwise
     */
    public boolean isRunning() {
        return ldapServer != null && ldapServer.isStarted();
    }

}

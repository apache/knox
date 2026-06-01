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

import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.interceptor.Interceptor;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.backend.BackendFactory;
import org.apache.knox.gateway.services.ldap.backend.LdapBackend;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages the ApacheDS LDAP server instance with pluggable backends
 */
public class KnoxLDAPServerManager {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    private DirectoryService directoryService;
    private LdapServer ldapServer;
    private LdapBackend backend;
    private File workDir;
    private int port;
    private String baseDn;
    private String remoteBaseDn;

    /**
     * Initialize the LDAP server with the given configuration
     *
     * @param config Gateway configuration
     */
    public void initialize(GatewayConfig config) throws Exception {
        // Prepare work directory for LDAP data
        File gatewayDataDir = new File(config.getGatewayDataDir());
        this.workDir = new File(gatewayDataDir, "ldap-server");

        // Get configuration
        this.port = config.getLDAPPort();
        this.baseDn = config.getLDAPBaseDN();
        String backendType = config.getLDAPBackendType();

        // Get backend-specific configuration using prefixed properties
        Map<String, String> backendConfig = config.getLDAPBackendConfig(backendType);

        // Add common configuration
        backendConfig.put("baseDn", baseDn);

        // Add legacy dataFile property for backwards compatibility with file backend
        if ("file".equalsIgnoreCase(backendType) && !backendConfig.containsKey("dataFile")) {
            backendConfig.put("dataFile", config.getLDAPBackendDataFile());
        }

        backendConfig.put("recursiveGroupResolution", String.valueOf(config.isLDAPRecursiveGroupResolutionEnabled()));
        backendConfig.put("recursiveGroupResolutionMaxDepth", String.valueOf(config.getLDAPRecursiveGroupResolutionMaxDepth()));

        // For proxy backends, extract remoteBaseDn if present
        this.remoteBaseDn = backendConfig.get("remoteBaseDn");

        // Initialize backend
        backend = BackendFactory.createBackend(backendType, backendConfig);

        // Clean up previous run if it didn't shut down cleanly
        File lockFile = new File(workDir, "run/instance.lock");
        if (lockFile.exists()) {
            LOG.ldapCleaningLockFile(lockFile.getAbsolutePath());
            lockFile.delete();
        }

        workDir.mkdirs();
    }

    /**
     * Start the LDAP server
     */
    public void start() throws Exception {
        LOG.ldapServiceStarting(port, baseDn);

        // Initialize DirectoryService
        directoryService = new DefaultDirectoryService();
        directoryService.setInstanceLayout(new InstanceLayout(workDir));

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

        // Create partition for remote base DN if different from proxy base DN
        // This allows backend entries with remote DNs to be returned in search results
        if (remoteBaseDn != null && !remoteBaseDn.equals(baseDn)) {
            JdbmPartition remotePartition = new JdbmPartition(schemaManager, directoryService.getDnFactory());
            remotePartition.setId("remote");
            remotePartition.setSuffixDn(new Dn(schemaManager, remoteBaseDn));
            remotePartition.setPartitionPath(new File(workDir, "remote").toURI());
            directoryService.addPartition(remotePartition);
        }

        addGroupLookupInterceptor();

        // Allow anonymous access
        directoryService.setAllowAnonymousAccess(true);

        // Start the service
        directoryService.startup();

        // Add base entries to the partition
        createBaseEntries(schemaManager);

        // Create LDAP server on configured port
        ldapServer = new LdapServer();
        ldapServer.setTransports(new TcpTransport(port));
        ldapServer.setDirectoryService(directoryService);

        ldapServer.start();

        LOG.ldapServiceStarted(port);
    }

    private void addGroupLookupInterceptor() {
        // Add our interceptor for group lookups and bind proxying
        // We need to insert it before AuthenticationInterceptor to intercept bind requests
        final List<Interceptor> interceptors = new ArrayList<>(directoryService.getInterceptors());
        int authIdx = -1;
        for (int i = 0; i < interceptors.size(); i++) {
            if (interceptors.get(i).getName().equalsIgnoreCase("authenticationInterceptor")) {
                authIdx = i;
                break;
            }
        }

        final GroupLookupInterceptor interceptor = new GroupLookupInterceptor(directoryService, backend);
        if (authIdx != -1) {
            interceptors.add(authIdx, interceptor);
        } else {
            interceptors.add(interceptor);
        }
        directoryService.setInterceptors(interceptors);
    }

    /**
     * Stop the LDAP server
     */
    public void stop() throws Exception {
        LOG.ldapServiceStopping(port);

        if (ldapServer != null) {
            try {
                ldapServer.stop();
            } catch (Exception e) {
                LOG.ldapServiceStopFailed(e);
            }
        }

        if (directoryService != null) {
            try {
                directoryService.shutdown();
            } catch (Exception e) {
                LOG.ldapServiceStopFailed(e);
            }
        }

        LOG.ldapServiceStopped();
    }

    private void createBaseEntries(SchemaManager schemaManager) throws Exception {
        // Create base entries for proxy base DN
        createBaseEntriesForDn(schemaManager, baseDn);

        // Create base entries for remote base DN if different
        if (remoteBaseDn != null && !remoteBaseDn.equals(baseDn)) {
            createBaseEntriesForDn(schemaManager, remoteBaseDn);
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
        return backend.getUserGroups(username);
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

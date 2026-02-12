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
import org.apache.directory.api.ldap.schema.loader.JarLdifSchemaLoader;
import org.apache.directory.api.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.backend.BackendFactory;
import org.apache.knox.gateway.services.ldap.backend.LdapBackend;

import java.io.File;
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
     * @param workDir Directory for LDAP data storage
     * @param port Port for LDAP server to listen on
     * @param baseDn Base DN for LDAP entries in the proxy server
     * @param backendType Type of backend to use
     * @param backendConfig Backend-specific configuration
     * @param remoteBaseDn Base DN of the remote LDAP server (for proxy backends)
     */
    public void initialize(File workDir, int port, String baseDn, String backendType, Map<String, String> backendConfig, String remoteBaseDn) throws Exception {
        this.workDir = workDir;
        this.port = port;
        this.baseDn = baseDn;
        this.remoteBaseDn = remoteBaseDn;

        // Initialize backend
        backendConfig.put("baseDn", baseDn);
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

        // Create and load schema manager manually
        JarLdifSchemaLoader loader = new JarLdifSchemaLoader();
        SchemaManager schemaManager = new DefaultSchemaManager(loader);
        schemaManager.loadAllEnabled();
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

        // Add our interceptor for group lookups
        directoryService.addLast(new GroupLookupInterceptor(directoryService, backend));

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
     * Check if the LDAP server is currently running.
     *
     * @return true if the server is running, false otherwise
     */
    public boolean isRunning() {
        return ldapServer != null && ldapServer.isStarted();
    }

}

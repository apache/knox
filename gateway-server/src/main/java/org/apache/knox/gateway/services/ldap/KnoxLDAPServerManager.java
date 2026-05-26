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
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.backend.LdapBackend;
import org.apache.knox.gateway.services.ldap.interceptor.UserSearchInterceptor;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the ApacheDS LDAP server instance with pluggable backends
 */
public class KnoxLDAPServerManager {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    @VisibleForTesting
    DirectoryService directoryService;
    private LdapServer ldapServer;
    private List<Interceptor> interceptors;
    private File workDir;
    private int port;
    private String baseDn;

    /**
     * Initialize the LDAP server with the given configuration
     *
     * @param workDir Directory for LDAP data storage
     * @param port Port for LDAP server to listen on
     * @param baseDn Base DN for LDAP entries in the proxy server
     * @param interceptors List of LDAP interceptors
     */
    public void initialize(File workDir, int port, String baseDn, List<Interceptor> interceptors) throws Exception {
        this.workDir = workDir;
        this.port = port;
        this.baseDn = baseDn;
        this.interceptors = interceptors;

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

        // Add our interceptor for user search
        // Create partition for remote base DN if different from proxy base DN
        // This allows backend entries with remote DNs to be returned in search results
        Set<String> baseDns = new HashSet<>();
        baseDns.add(baseDn);
        for (Interceptor interceptor : interceptors) {
            if (interceptor instanceof UserSearchInterceptor) {
                LdapBackend backend = ((UserSearchInterceptor) interceptor).getBackend();
                String remoteBaseDn = backend.getBaseDn();
                if (!baseDns.contains(remoteBaseDn)) {
                    //create partition
                    String id = backend.getName().replaceAll("\\s+", "");
                    JdbmPartition remotePartition = new JdbmPartition(schemaManager, directoryService.getDnFactory());
                    remotePartition.setId(id);
                    remotePartition.setSuffixDn(new Dn(schemaManager, remoteBaseDn));
                    remotePartition.setPartitionPath(new File(workDir, id).toURI());
                    directoryService.addPartition(remotePartition);
                    baseDns.add(remoteBaseDn);
                }            }
            directoryService.addLast(interceptor);
        }

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
     * Check if the LDAP server is currently running.
     *
     * @return true if the server is running, false otherwise
     */
    public boolean isRunning() {
        return ldapServer != null && ldapServer.isStarted();
    }

}

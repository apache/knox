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

import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.server.core.api.interceptor.Interceptor;
import org.apache.knox.gateway.services.ldap.interceptor.InterceptorFactory;
import org.apache.knox.gateway.services.ldap.interceptor.UserSearchInterceptor;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import java.io.File;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Unit tests for KnoxLDAPServerManager.
 */
public class KnoxLDAPServerManagerTest {

    private KnoxLDAPServerManager serverManager;
    private File tempWorkDir;
    private File tempLdapFile;
    private int port;

    @Before
    public void setUp() throws Exception {
        serverManager = new KnoxLDAPServerManager();

        // Create temporary work directory
        tempWorkDir = File.createTempFile("knox-ldap-work", "");
        tempWorkDir.delete();
        tempWorkDir.mkdirs();
        tempWorkDir.deleteOnExit();

        // Create temporary LDAP data file
        tempLdapFile = File.createTempFile("ldap-test", ".json");
        tempLdapFile.deleteOnExit();

        try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(tempLdapFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write("{\"users\":[{\"dn\":\"uid=admin,ou=people,dc=test,dc=com\",\"uid\":\"admin\",\"cn\":\"Administrator\",\"userPassword\":\"admin-password\"}],\"groups\":[]}");
        }

        // pick an unused port
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (serverManager != null) {
            try {
                serverManager.stop();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        cleanupTempFiles();
    }

    @Test
    public void testInitializeWithFileBackend() throws Exception {
        Map<String, String> backendConfig = createFileBackendInterceptorConfig();
        Interceptor interceptor = InterceptorFactory.createInterceptor("testbackend", backendConfig);

        serverManager.initialize(tempWorkDir, port, "dc=test,dc=com", List.of(interceptor));

        assertEquals("Port should be set correctly", port, serverManager.getPort());
        assertEquals("Base DN should be set correctly", "dc=test,dc=com", serverManager.getBaseDn());
        assertFalse("Should not be running after initialize", serverManager.isRunning());
    }

    @Test
    public void testInitializeWithLdapProxyBackend() throws Exception {
        Map<String, String> backendConfig = createLdapBackendInterceptorConfig();
        Interceptor interceptor = InterceptorFactory.createInterceptor("testbackend", backendConfig);

        serverManager.initialize(tempWorkDir, port, "dc=test,dc=com", List.of(interceptor));

        assertEquals("Port should be set correctly", port, serverManager.getPort());
        assertEquals("Base DN should be set correctly", "dc=test,dc=com", serverManager.getBaseDn());
        assertFalse("Should not be running after initialize", serverManager.isRunning());
    }

    @Test
    public void testInitializeWithMultipleBackends() throws Exception {
        Map<String, String> fileBackendConfig = createFileBackendInterceptorConfig();
        Interceptor fileInterceptor = InterceptorFactory.createInterceptor("filebackend", fileBackendConfig);

        Map<String, String> ldapBackendConfig = createLdapBackendInterceptorConfig();
        Interceptor ldapInterceptor = InterceptorFactory.createInterceptor("ldapbackend", fileBackendConfig);

        serverManager.initialize(tempWorkDir, port, "dc=test,dc=com", List.of(fileInterceptor, ldapInterceptor));

        assertEquals("Port should be set correctly", port, serverManager.getPort());
        assertEquals("Base DN should be set correctly", "dc=test,dc=com", serverManager.getBaseDn());
        assertFalse("Should not be running after initialize", serverManager.isRunning());
    }

    @Test
    public void testLockFileCleanup() throws Exception {
        // Create a lock file to simulate previous unclean shutdown
        File runDir = new File(tempWorkDir, "run");
        runDir.mkdirs();
        File lockFile = new File(runDir, "instance.lock");
        lockFile.createNewFile();
        assertTrue("Lock file should exist before initialization", lockFile.exists());

        Map<String, String> backendConfig = createFileBackendInterceptorConfig();
        Interceptor interceptor = InterceptorFactory.createInterceptor("testbackend", backendConfig);
        serverManager.initialize(tempWorkDir, port, "dc=test,dc=com", List.of(interceptor));

        assertFalse("Lock file should be cleaned up during initialization", lockFile.exists());
    }

    @Test
    public void testGettersBeforeInitialization() {
        assertEquals("Port should be 0 before initialization", 0, serverManager.getPort());
        assertEquals("Base DN should be null before initialization", null, serverManager.getBaseDn());
        assertFalse("Should not be running before initialization", serverManager.isRunning());
    }

    @Test
    public void testStopBeforeStart() throws Exception {
        Map<String, String> backendConfig = createFileBackendInterceptorConfig();
        Interceptor interceptor = InterceptorFactory.createInterceptor("testbackend", backendConfig);
        serverManager.initialize(tempWorkDir, port, "dc=test,dc=com", List.of(interceptor));

        // Should not throw exception when stopping before starting
        serverManager.stop();
    }

    @Test
    public void testMultipleStopCalls() throws Exception {
        Map<String, String> backendConfig = createFileBackendInterceptorConfig();
        Interceptor interceptor = InterceptorFactory.createInterceptor("testbackend", backendConfig);
        serverManager.initialize(tempWorkDir, port, "dc=test,dc=com", List.of(interceptor));

        // Multiple stop calls should not throw exceptions
        serverManager.stop();
        serverManager.stop();
        serverManager.stop();
    }

    @Test(expected = Exception.class)
    public void testStartWithoutInitialize() throws Exception {
        // Should throw exception when starting without initialization
        serverManager.start();
    }

    @Test
    public void testStartWithFileBackend() throws Exception {
        // Ensure that the partitions are created
        Map<String, String> backendConfig = createFileBackendInterceptorConfig();
        UserSearchInterceptor userSearchInterceptor = (UserSearchInterceptor) InterceptorFactory.createInterceptor("testbackend", backendConfig);
        serverManager.initialize(tempWorkDir, port, "dc=test,dc=com", List.of(userSearchInterceptor));

        serverManager.start();

        // LdapNoSuchObjectException will be thrown if expected partition does not exist
        assertEquals("File backend dn should match configuration",
                backendConfig.get("baseDn"), userSearchInterceptor.getBackend().getBaseDn());
        serverManager.directoryService.getPartitionNexus().getPartition(
                new Dn(serverManager.directoryService.getSchemaManager(), userSearchInterceptor.getBackend().getBaseDn()));

        Interceptor interceptor = serverManager.directoryService.getInterceptor("testbackend");
        assertNotNull("Interceptor should not be null", interceptor);
        assertEquals("Interceptor should match created UserSearchInterceptor", userSearchInterceptor, interceptor);
    }

    @Test
    public void testStartWithLdapProxyBackend() throws Exception {
        // Ensure that the partitions are created and backends registered with the UserSearchInterceptor

        Map<String, String> backendConfig = createLdapBackendInterceptorConfig();
        UserSearchInterceptor userSearchInterceptor = (UserSearchInterceptor) InterceptorFactory.createInterceptor("testbackend", backendConfig);
        serverManager.initialize(tempWorkDir, port, "dc=test,dc=com", List.of(userSearchInterceptor));
        serverManager.start();

        // LdapNoSuchObjectException will be thrown if expected partition does not exist
        assertEquals("Ldap Proxy backend dn should match configuration",
                backendConfig.get("remoteBaseDn"), userSearchInterceptor.getBackend().getBaseDn());
        serverManager.directoryService.getPartitionNexus().getPartition(
                new Dn(serverManager.directoryService.getSchemaManager(), userSearchInterceptor.getBackend().getBaseDn()));

        Interceptor interceptor = serverManager.directoryService.getInterceptor("testbackend");
        assertNotNull("Interceptor should not be null", interceptor);
        assertEquals("Interceptor should match created UserSearchInterceptor", userSearchInterceptor, interceptor);
    }

    @Test
    public void testStartWithMultipleBackends() throws Exception {
        // Ensure that the partitions are created and backends registered with the UserSearchInterceptor

        Map<String, String> fileBackendConfig = createFileBackendInterceptorConfig();
        UserSearchInterceptor fileInterceptor = (UserSearchInterceptor) InterceptorFactory.createInterceptor("filebackend", fileBackendConfig);

        Map<String, String> ldapBackendConfig = createLdapBackendInterceptorConfig();
        UserSearchInterceptor ldapInterceptor = (UserSearchInterceptor) InterceptorFactory.createInterceptor("ldapbackend", ldapBackendConfig);

        serverManager.initialize(tempWorkDir, port, "dc=test,dc=com", List.of(fileInterceptor, ldapInterceptor));
        serverManager.start();

        // LdapNoSuchObjectException will be thrown if expected partition does not exist
        assertEquals("File backend dn should match configuration",
                fileBackendConfig.get("baseDn"), fileInterceptor.getBackend().getBaseDn());
        serverManager.directoryService.getPartitionNexus().getPartition(
                new Dn(serverManager.directoryService.getSchemaManager(), fileInterceptor.getBackend().getBaseDn()));
        assertEquals("Ldap Proxy backend dn should match configuration",
                ldapBackendConfig.get("remoteBaseDn"), ldapInterceptor.getBackend().getBaseDn());
        serverManager.directoryService.getPartitionNexus().getPartition(
                new Dn(serverManager.directoryService.getSchemaManager(), ldapInterceptor.getBackend().getBaseDn()));

        Interceptor configuredFileInterceptor = serverManager.directoryService.getInterceptor("filebackend");
        assertNotNull("Interceptor should not be null", fileInterceptor);
        assertEquals("Interceptor should match created UserSearchInterceptor", fileInterceptor, configuredFileInterceptor);

        Interceptor configuredLdapInterceptor = serverManager.directoryService.getInterceptor("ldapbackend");
        assertNotNull("Interceptor should not be null", ldapInterceptor);
        assertEquals("Interceptor should match created UserSearchInterceptor", ldapInterceptor, configuredLdapInterceptor);
    }

    private Map<String, String> createFileBackendInterceptorConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("interceptorType", "backend");
        config.put("backendType", "file");
        config.put("baseDn", "dc=file,dc=com");
        config.put("dataFile", tempLdapFile.getAbsolutePath());
        return config;
    }

    private Map<String, String> createLdapBackendInterceptorConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("interceptorType", "backend");
        config.put("backendType", "ldap");
        config.put("baseDn", "dc=proxy,dc=com");
        config.put("url", "ldap://localhost:33389");
        config.put("remoteBaseDn", "dc=hadoop,dc=apache,dc=org");
        config.put("systemUsername", "cn=admin,dc=hadoop,dc=apache,dc=org");
        config.put("systemPassword", "admin-password");
        return config;
    }

    private void cleanupTempFiles() {
        if (tempLdapFile != null && tempLdapFile.exists()) {
            tempLdapFile.delete();
        }
        if (tempWorkDir != null && tempWorkDir.exists()) {
            // Clean up work directory recursively
            deleteRecursively(tempWorkDir);
        }
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
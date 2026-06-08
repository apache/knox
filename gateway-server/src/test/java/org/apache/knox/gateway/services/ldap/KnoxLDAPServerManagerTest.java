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

import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.knox.gateway.services.ldap.interceptor.UserSearchInterceptor;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import java.io.File;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
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
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPPort()).andReturn(port).anyTimes();
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=test,dc=com").anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("filebackend")).anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        Map<String, String> backendConfig = createFileBackendInterceptorConfig();
        expect(mockConfig.getLDAPInterceptorConfig("filebackend")).andReturn(backendConfig).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);

        assertEquals("Port should be set correctly", port, serverManager.getPort());
        assertEquals("Base DN should be set correctly", "dc=test,dc=com", serverManager.getBaseDn());
        assertFalse("Should not be running after initialize", serverManager.isRunning());
    }

    @Test
    public void testInitializeWithLdapBackend() throws Exception {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPPort()).andReturn(port).anyTimes();
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=test,dc=com").anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("ldapbackend")).anyTimes();
        Map<String, String> backendConfig = createLdapBackendInterceptorConfig();
        expect(mockConfig.getLDAPInterceptorConfig("ldapbackend")).andReturn(backendConfig).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);

        assertEquals("Port should be set correctly", port, serverManager.getPort());
        assertEquals("Base DN should be set correctly", "dc=test,dc=com", serverManager.getBaseDn());
        assertFalse("Should not be running after initialize", serverManager.isRunning());
    }

    @Test(expected = Exception.class)
    public void testInitializeWithInvalidInterceptorType() throws Exception {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("invalid")).anyTimes();
        Map<String, String> backendConfig = new HashMap<>();
        backendConfig.put("interceptorType", "badinterceptor");
        expect(mockConfig.getLDAPInterceptorConfig("invalid")).andReturn(new HashMap<>()).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);
    }

    @Test
    public void testInitializeWithMultipleBackends() throws Exception {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPPort()).andReturn(port).anyTimes();
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=test,dc=com").anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("filebackend", "ldapbackend")).anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        Map<String, String> fileBackendConfig = createFileBackendInterceptorConfig();
        expect(mockConfig.getLDAPInterceptorConfig("filebackend")).andReturn(fileBackendConfig).anyTimes();
        Map<String, String> ldapBackendConfig = createLdapBackendInterceptorConfig();
        expect(mockConfig.getLDAPInterceptorConfig("ldapbackend")).andReturn(ldapBackendConfig).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);

        assertEquals("Port should be set correctly", port, serverManager.getPort());
        assertEquals("Base DN should be set correctly", "dc=test,dc=com", serverManager.getBaseDn());
        assertFalse("Should not be running after initialize", serverManager.isRunning());
    }

    @Test
    public void testLockFileCleanup() throws Exception {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("filebackend")).anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        Map<String, String> backendConfig = createFileBackendInterceptorConfig();
        expect(mockConfig.getLDAPInterceptorConfig("filebackend")).andReturn(backendConfig).anyTimes();
        replay(mockConfig);

        // The manager will use tempWorkDir.getParent()/ldap-server as workDir
        File actualWorkDir = new File(tempWorkDir.getParent(), "ldap-server");
        actualWorkDir.mkdirs();

        // Create a lock file to simulate previous unclean shutdown
        File runDir = new File(actualWorkDir, "run");
        runDir.mkdirs();
        File lockFile = new File(runDir, "instance.lock");
        lockFile.createNewFile();
        assertTrue("Lock file should exist before initialization", lockFile.exists());

        serverManager.initialize(mockConfig);

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
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("filebackend")).anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        Map<String, String> backendConfig = createFileBackendInterceptorConfig();
        expect(mockConfig.getLDAPInterceptorConfig("filebackend")).andReturn(backendConfig).anyTimes();

        replay(mockConfig);

        serverManager.initialize(mockConfig);

        // Should not throw exception when stopping before starting
        serverManager.stop();
    }

    @Test
    public void testMultipleStopCalls() throws Exception {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("filebackend")).anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        Map<String, String> backendConfig = createFileBackendInterceptorConfig();
        expect(mockConfig.getLDAPInterceptorConfig("filebackend")).andReturn(backendConfig).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);

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
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPPort()).andReturn(port).anyTimes();
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=test,dc=com").anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("filebackend")).anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        Map<String, String> backendConfig = createFileBackendInterceptorConfig();
        expect(mockConfig.getLDAPInterceptorConfig("filebackend")).andReturn(backendConfig).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);

        serverManager.start();

        UserSearchInterceptor interceptor = (UserSearchInterceptor) serverManager.directoryService.getInterceptor("filebackend");
        assertNotNull("Interceptor should not be null", interceptor);
        assertEquals("File backend dn should match configuration",
                backendConfig.get("baseDn"), interceptor.getBackend().getBaseDn());
        // LdapNoSuchObjectException will be thrown if expected partition does not exist
        serverManager.directoryService.getPartitionNexus().getPartition(
                new Dn(serverManager.directoryService.getSchemaManager(), backendConfig.get("baseDn")));
    }

    @Test
    public void testStartWithLdapProxyBackend() throws Exception {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPPort()).andReturn(port).anyTimes();
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=proxy,dc=com").anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("ldapbackend")).anyTimes();
        Map<String, String> backendConfig = createLdapBackendInterceptorConfig();
        expect(mockConfig.getLDAPInterceptorConfig("ldapbackend")).andReturn(backendConfig).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);

        serverManager.start();

        // Ensure that the partitions are created and backends registered with the UserSearchInterceptor
        UserSearchInterceptor interceptor = (UserSearchInterceptor) serverManager.directoryService.getInterceptor("ldapbackend");
        assertNotNull("Interceptor should not be null", interceptor);
        assertEquals("LDAP backend dn should match configuration",
                backendConfig.get("remoteBaseDn"), interceptor.getBackend().getBaseDn());
        // LdapNoSuchObjectException will be thrown if expected partition does not exist
        serverManager.directoryService.getPartitionNexus().getPartition(
                new Dn(serverManager.directoryService.getSchemaManager(), backendConfig.get("baseDn")));
    }

    @Test
    public void testStartWithMultipleBackends() throws Exception {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPPort()).andReturn(port).anyTimes();
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=test,dc=com").anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("filebackend", "ldapbackend")).anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        Map<String, String> fileBackendConfig = createFileBackendInterceptorConfig();
        expect(mockConfig.getLDAPInterceptorConfig("filebackend")).andReturn(fileBackendConfig).anyTimes();
        Map<String, String> ldapBackendConfig = createLdapBackendInterceptorConfig();
        expect(mockConfig.getLDAPInterceptorConfig("ldapbackend")).andReturn(ldapBackendConfig).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);

        serverManager.start();

        // Ensure that the partitions are created and backends registered with the file backend interceptor
        UserSearchInterceptor fileInterceptor = (UserSearchInterceptor) serverManager.directoryService.getInterceptor("filebackend");
        assertNotNull("Interceptor should not be null", fileInterceptor);
        assertEquals("File backend dn should match configuration",
                fileBackendConfig.get("baseDn"), fileInterceptor.getBackend().getBaseDn());
        // LdapNoSuchObjectException will be thrown if expected partition does not exist
        serverManager.directoryService.getPartitionNexus().getPartition(
                new Dn(serverManager.directoryService.getSchemaManager(), fileBackendConfig.get("baseDn")));

        // Ensure that the partitions are created and backends registered with the ldap backend interceptor
        UserSearchInterceptor ldapInterceptor = (UserSearchInterceptor) serverManager.directoryService.getInterceptor("ldapbackend");
        assertNotNull("Interceptor should not be null", ldapInterceptor);
        assertEquals("LDAP backend dn should match configuration",
                ldapBackendConfig.get("remoteBaseDn"), ldapInterceptor.getBackend().getBaseDn());
        // LdapNoSuchObjectException will be thrown if expected partition does not exist
        serverManager.directoryService.getPartitionNexus().getPartition(
                new Dn(serverManager.directoryService.getSchemaManager(), ldapBackendConfig.get("baseDn")));
    }

    @Test
    public void testGetUserGroups() {

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
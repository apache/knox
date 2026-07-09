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

import org.apache.directory.api.ldap.codec.api.ControlFactory;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.exception.LdapAuthenticationException;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.message.Control;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.directory.server.core.api.interceptor.Interceptor;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.ldap.control.RolesLookupBypassControlFactory;
import org.apache.knox.gateway.services.ldap.model.constants.SchemaConstants;
import org.easymock.EasyMock;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.knox.gateway.services.ldap.interceptor.UserSearchInterceptor;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import java.io.File;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private static final String BIND_DN = "uid=knox,ou=system";
    private static final String BIND_PASSWORD = "knox-password";

    private KnoxLDAPServerManager serverManager;
    private File tempWorkDir;
    private File tempLdapFile;
    private int port;

    @Before
    public void setUp() throws Exception {
        // By default no bind password is stored in the credential store, so the server
        // runs with anonymous access (the historical behavior). Bind-enforcing tests
        // rebuild the manager via useBindPassword(...).
        serverManager = new KnoxLDAPServerManager(aliasServiceReturning(null));

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
                new Dn(serverManager.directoryService.getSchemaManager(), interceptor.getBackend().getBaseDn()));
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
                new Dn(serverManager.directoryService.getSchemaManager(), interceptor.getBackend().getBaseDn()));
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

        // assert that interceptors are found in reverse order
        List<String> expectedInterceptorOrder = new ArrayList<>();
        expectedInterceptorOrder.addAll(mockConfig.getLDAPInterceptorNames());
        Collections.reverse(expectedInterceptorOrder);
        List<String> interceptorNames = serverManager.directoryService.getInterceptors().stream()
                .map(Interceptor::getName)
                .filter(name -> expectedInterceptorOrder.contains(name))
                .collect(Collectors.toList());
        assertEquals("Interceptors should be added to directory service in the order specified by the config",
                expectedInterceptorOrder, interceptorNames);

        // Ensure that the partitions are created and backends registered with the file backend interceptor
        UserSearchInterceptor fileInterceptor = (UserSearchInterceptor) serverManager.directoryService.getInterceptor("filebackend");
        assertNotNull("Interceptor should not be null", fileInterceptor);
        assertEquals("File backend dn should match configuration",
                fileBackendConfig.get("baseDn"), fileInterceptor.getBackend().getBaseDn());
        // LdapNoSuchObjectException will be thrown if expected partition does not exist
        serverManager.directoryService.getPartitionNexus().getPartition(
                new Dn(serverManager.directoryService.getSchemaManager(), fileInterceptor.getBackend().getBaseDn()));

        // Ensure that the partitions are created and backends registered with the ldap backend interceptor
        UserSearchInterceptor ldapInterceptor = (UserSearchInterceptor) serverManager.directoryService.getInterceptor("ldapbackend");
        assertNotNull("Interceptor should not be null", ldapInterceptor);
        assertEquals("LDAP backend dn should match configuration",
                ldapBackendConfig.get("remoteBaseDn"), ldapInterceptor.getBackend().getBaseDn());
        // LdapNoSuchObjectException will be thrown if expected partition does not exist
        serverManager.directoryService.getPartitionNexus().getPartition(
                new Dn(serverManager.directoryService.getSchemaManager(), ldapInterceptor.getBackend().getBaseDn()));
    }

    @Test
    public void testStartWithMultipleBackendsIdCollision() throws Exception {
        // Partitions are created using the interceptor name as an id. Whitespace is removed from the
        // id, but this could result in id collisions and failure to create the partitions. This test
        // checks that partitions will still be created for the DNs even if the interceptor names
        // collide.
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPPort()).andReturn(port).anyTimes();
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=test,dc=com").anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("ldapbackend", "ldap backend")).anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        Map<String, String> ldapBackendConfig = createLdapBackendInterceptorConfig();
        expect(mockConfig.getLDAPInterceptorConfig("ldapbackend")).andReturn(ldapBackendConfig).anyTimes();
        Map<String, String> ldapBackendConfig2 = createLdapBackendInterceptorConfig();
        ldapBackendConfig2.put("remoteBaseDn", "dc=ldapbackend,dc=example,dc=org");
        expect(mockConfig.getLDAPInterceptorConfig("ldap backend")).andReturn(ldapBackendConfig2).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);

        serverManager.start();

        // Ensure that the partitions are created and backends registered with the ldap backend interceptor
        UserSearchInterceptor ldapInterceptor = (UserSearchInterceptor) serverManager.directoryService.getInterceptor("ldapbackend");
        assertNotNull("Interceptor should not be null", ldapInterceptor);
        assertEquals("LDAP backend dn should match configuration",
                ldapBackendConfig.get("remoteBaseDn"), ldapInterceptor.getBackend().getBaseDn());
        // LdapNoSuchObjectException will be thrown if expected partition does not exist
        serverManager.directoryService.getPartitionNexus().getPartition(
                new Dn(serverManager.directoryService.getSchemaManager(), ldapInterceptor.getBackend().getBaseDn()));

        // Ensure that the partitions are created and backends registered with the ldap backend interceptor
        UserSearchInterceptor ldapInterceptor2 = (UserSearchInterceptor) serverManager.directoryService.getInterceptor("ldap backend");
        assertNotNull("Interceptor should not be null", ldapInterceptor2);
        assertEquals("LDAP backend dn should match configuration",
                ldapBackendConfig2.get("remoteBaseDn"), ldapInterceptor2.getBackend().getBaseDn());
        // LdapNoSuchObjectException will be thrown if expected partition does not exist
        serverManager.directoryService.getPartitionNexus().getPartition(
                new Dn(serverManager.directoryService.getSchemaManager(), ldapInterceptor2.getBackend().getBaseDn()));
    }

    @Test
    public void testStartRegistersRolesLookupBypassControl() throws Exception {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPPort()).andReturn(port).anyTimes();
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=test,dc=com").anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of()).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);

        serverManager.start();

        Map<String, ControlFactory<? extends Control>> controlFactoryMap = serverManager.directoryService.getLdapCodecService().getRequestControlFactories();
        assertTrue(controlFactoryMap.containsKey(SchemaConstants.ROLES_LOOKUP_BYPASS_CONTROL_OID));
        assertTrue(controlFactoryMap.get(SchemaConstants.ROLES_LOOKUP_BYPASS_CONTROL_OID) instanceof RolesLookupBypassControlFactory);
    }

    @Test(expected = LdapException.class)
    public void testBindRequiredRejectsAnonymous() throws Exception {
        useBindPassword(BIND_PASSWORD);
        serverManager.initialize(createBindEnabledConfig());
        serverManager.start();

        // Anonymous access is disabled, so an anonymous bind must be rejected.
        try (LdapConnection connection = new LdapNetworkConnection("localhost", port)) {
            connection.bind();
        }
    }

    @Test
    public void testBindWithConfiguredCredentialsSucceeds() throws Exception {
        useBindPassword(BIND_PASSWORD);
        serverManager.initialize(createBindEnabledConfig());
        serverManager.start();

        try (LdapConnection connection = new LdapNetworkConnection("localhost", port)) {
            connection.bind(BIND_DN, BIND_PASSWORD);
            assertTrue("Connection should be authenticated", connection.isAuthenticated());
            // An authenticated client should be able to search.
            try (EntryCursor cursor = connection.search("dc=test,dc=com", "(objectClass=*)", SearchScope.SUBTREE)) {
                assertTrue("Authenticated search should return at least one entry", cursor.next());
            }
        }
    }

    @Test(expected = LdapAuthenticationException.class)
    public void testWrongBindPasswordRejected() throws Exception {
        useBindPassword(BIND_PASSWORD);
        serverManager.initialize(createBindEnabledConfig());
        serverManager.start();

        try (LdapConnection connection = new LdapNetworkConnection("localhost", port)) {
            connection.bind(BIND_DN, "wrong-password");
        }
    }

    @Test
    public void testAnonymousStillAllowedWhenUnconfigured() throws Exception {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPPort()).andReturn(port).anyTimes();
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=test,dc=com").anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("filebackend")).anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        expect(mockConfig.getLDAPInterceptorConfig("filebackend")).andReturn(createFileBackendInterceptorConfig()).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);
        serverManager.start();

        // No bind credentials configured -> anonymous access remains allowed (backward compatible).
        try (LdapConnection connection = new LdapNetworkConnection("localhost", port)) {
            connection.bind();
            try (EntryCursor cursor = connection.search("dc=test,dc=com", "(objectClass=*)", SearchScope.SUBTREE)) {
                assertTrue("Anonymous search should return at least one entry", cursor.next());
            }
        }
    }

    private GatewayConfig createBindEnabledConfig() {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPPort()).andReturn(port).anyTimes();
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=test,dc=com").anyTimes();
        expect(mockConfig.getLDAPBindUser()).andReturn(BIND_DN).anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("filebackend")).anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        expect(mockConfig.getLDAPInterceptorConfig("filebackend")).andReturn(createFileBackendInterceptorConfig()).anyTimes();
        replay(mockConfig);
        return mockConfig;
    }

    /** Rebuild the server manager so its credential store resolves the bind password to the given value. */
    private void useBindPassword(String password) throws Exception {
        serverManager = new KnoxLDAPServerManager(aliasServiceReturning(password));
    }

    /** Create an AliasService whose gateway password lookups return the given value (null => alias not set). */
    private AliasService aliasServiceReturning(String password) throws Exception {
        AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
        expect(aliasService.getPasswordFromAliasForGateway(EasyMock.anyString()))
                .andReturn(password == null ? null : password.toCharArray()).anyTimes();
        replay(aliasService);
        return aliasService;
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

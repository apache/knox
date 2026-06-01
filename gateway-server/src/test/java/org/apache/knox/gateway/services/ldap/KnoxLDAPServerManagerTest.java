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
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Unit tests for KnoxLDAPServerManager.
 */
public class KnoxLDAPServerManagerTest {

    private KnoxLDAPServerManager serverManager;
    private File tempWorkDir;
    private File tempLdapFile;

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
        expect(mockConfig.getLDAPPort()).andReturn(3890).anyTimes();
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=test,dc=com").anyTimes();
        expect(mockConfig.getLDAPBackendType()).andReturn("file").anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        expect(mockConfig.getLDAPBackendConfig("file")).andReturn(new HashMap<>()).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);

        assertEquals("Port should be set correctly", 3890, serverManager.getPort());
        assertEquals("Base DN should be set correctly", "dc=test,dc=com", serverManager.getBaseDn());
        assertFalse("Should not be running after initialize", serverManager.isRunning());
    }

    @Test
    public void testInitializeWithLdapBackend() throws Exception {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPPort()).andReturn(3891).anyTimes();
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=proxy,dc=com").anyTimes();
        expect(mockConfig.getLDAPBackendType()).andReturn("ldap").anyTimes();

        Map<String, String> backendConfig = new HashMap<>();
        backendConfig.put("url", "ldap://localhost:33389");
        backendConfig.put("remoteBaseDn", "dc=hadoop,dc=apache,dc=org");
        backendConfig.put("systemUsername", "cn=admin,dc=hadoop,dc=apache,dc=org");
        backendConfig.put("systemPassword", "admin-password");

        expect(mockConfig.getLDAPBackendConfig("ldap")).andReturn(backendConfig).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);

        assertEquals("Port should be set correctly", 3891, serverManager.getPort());
        assertEquals("Base DN should be set correctly", "dc=proxy,dc=com", serverManager.getBaseDn());
        assertFalse("Should not be running after initialize", serverManager.isRunning());
    }

    @Test(expected = Exception.class)
    public void testInitializeWithInvalidBackendType() throws Exception {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPBackendType()).andReturn("invalid").anyTimes();
        expect(mockConfig.getLDAPBackendConfig("invalid")).andReturn(new HashMap<>()).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);
    }

    @Test
    public void testLockFileCleanup() throws Exception {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPBackendType()).andReturn("file").anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        expect(mockConfig.getLDAPBackendConfig("file")).andReturn(new HashMap<>()).anyTimes();
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
        expect(mockConfig.getLDAPBackendType()).andReturn("file").anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        expect(mockConfig.getLDAPBackendConfig("file")).andReturn(new HashMap<>()).anyTimes();
        replay(mockConfig);

        serverManager.initialize(mockConfig);

        // Should not throw exception when stopping before starting
        serverManager.stop();
    }

    @Test
    public void testMultipleStopCalls() throws Exception {
        GatewayConfig mockConfig = EasyMock.createNiceMock(GatewayConfig.class);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempWorkDir.getParent()).anyTimes();
        expect(mockConfig.getLDAPBackendType()).andReturn("file").anyTimes();
        expect(mockConfig.getLDAPBackendDataFile()).andReturn(tempLdapFile.getAbsolutePath()).anyTimes();
        expect(mockConfig.getLDAPBackendConfig("file")).andReturn(new HashMap<>()).anyTimes();
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
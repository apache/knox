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

import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
        Map<String, String> backendConfig = createFileBackendConfig();

        serverManager.initialize(tempWorkDir, 3890, "dc=test,dc=com", "file", backendConfig, null);

        assertEquals("Port should be set correctly", 3890, serverManager.getPort());
        assertEquals("Base DN should be set correctly", "dc=test,dc=com", serverManager.getBaseDn());
        assertFalse("Should not be running after initialize", serverManager.isRunning());
    }

    @Test
    public void testInitializeWithLdapBackend() throws Exception {
        Map<String, String> backendConfig = createLdapBackendConfig();

        serverManager.initialize(tempWorkDir, 3891, "dc=proxy,dc=com", "ldap", backendConfig, "dc=hadoop,dc=apache,dc=org");

        assertEquals("Port should be set correctly", 3891, serverManager.getPort());
        assertEquals("Base DN should be set correctly", "dc=proxy,dc=com", serverManager.getBaseDn());
        assertFalse("Should not be running after initialize", serverManager.isRunning());
    }

    @Test(expected = Exception.class)
    public void testInitializeWithInvalidBackendType() throws Exception {
        Map<String, String> backendConfig = new HashMap<>();
        backendConfig.put("baseDn", "dc=test,dc=com");

        serverManager.initialize(tempWorkDir, 3890, "dc=test,dc=com", "invalid", backendConfig, null);
    }

    @Test
    public void testLockFileCleanup() throws Exception {
        // Create a lock file to simulate previous unclean shutdown
        File runDir = new File(tempWorkDir, "run");
        runDir.mkdirs();
        File lockFile = new File(runDir, "instance.lock");
        lockFile.createNewFile();
        assertTrue("Lock file should exist before initialization", lockFile.exists());

        Map<String, String> backendConfig = createFileBackendConfig();
        serverManager.initialize(tempWorkDir, 3890, "dc=test,dc=com", "file", backendConfig, null);

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
        Map<String, String> backendConfig = createFileBackendConfig();
        serverManager.initialize(tempWorkDir, 3890, "dc=test,dc=com", "file", backendConfig, null);

        // Should not throw exception when stopping before starting
        serverManager.stop();
    }

    @Test
    public void testMultipleStopCalls() throws Exception {
        Map<String, String> backendConfig = createFileBackendConfig();
        serverManager.initialize(tempWorkDir, 3890, "dc=test,dc=com", "file", backendConfig, null);

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

    private Map<String, String> createFileBackendConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("baseDn", "dc=test,dc=com");
        config.put("dataFile", tempLdapFile.getAbsolutePath());
        return config;
    }

    private Map<String, String> createLdapBackendConfig() {
        Map<String, String> config = new HashMap<>();
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
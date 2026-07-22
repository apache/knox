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

import org.junit.Test;
import org.junit.Before;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests to verify ServiceLoader discovery of LDAP backends.
 */
public class BackendFactoryTest {

    private Map<String, String> config;

    @Before
    public void setUp() throws Exception {
        config = new HashMap<>();
        config.put("baseDn", "dc=test,dc=com");

        // Create a temporary data file for FileBackend tests
        java.io.File tempFile = java.io.File.createTempFile("ldap-test", ".json");
        tempFile.deleteOnExit();

        // Write minimal valid JSON
        try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(tempFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write("{\"users\":[],\"groups\":[]}\n");
        }

        config.put("dataFile", tempFile.getAbsolutePath());
    }

    @Test
    public void testServiceLoaderDiscovery() {
        ServiceLoader<LdapBackendFactory> loader = ServiceLoader.load(LdapBackendFactory.class);

        // Should discover at least the built-in backends
        boolean foundFileBackend = false;
        boolean foundLdapBackend = false;

        for (LdapBackendFactory factory : loader) {
            String backendType = factory.getType();
            if ("file".equals(backendType)) {
                foundFileBackend = true;
                assertTrue("File backend should be FileBackend instance", factory instanceof FileBackendFactory);
            } else if ("ldap".equals(backendType)) {
                foundLdapBackend = true;
                assertTrue("LDAP backend should be LdapProxyBackend instance", factory instanceof LdapProxyBackendFactory);
            }
        }

        assertTrue("ServiceLoader should discover file backend", foundFileBackend);
        assertTrue("ServiceLoader should discover ldap backend", foundLdapBackend);
    }

    @Test
    public void testCreateFileBackend() throws Exception {
        config.put("backendType", "file");
        LdapBackend fileBackend = BackendFactory.createBackend("testbackend", config);

        assertNotNull("File backend should be created", fileBackend);
        assertTrue("Should create FileBackend instance", fileBackend instanceof FileBackend);
        assertEquals("Backend type should be 'file'", "file", fileBackend.getType());
        assertEquals("Backend name should be 'testbackend'", "testbackend", fileBackend.getName());
    }

    @Test
    public void testCreateLdapBackend() throws Exception {
        config.put("backendType", "ldap");
        config.put("url", "ldap://localhost:389");
        config.put("remoteBaseDn", "dc=hadoop,dc=apache,dc=org");

        LdapBackend ldapBackend = BackendFactory.createBackend("testbackend", config);

        assertNotNull("LDAP backend should be created", ldapBackend);
        assertTrue("Should create LdapProxyBackend instance", ldapBackend instanceof LdapProxyBackend);
        assertEquals("Backend type should be 'ldap'", "ldap", ldapBackend.getType());
        assertEquals("Backend name should be 'testbackend'", "testbackend", ldapBackend.getName());
    }

    @Test
    public void testCaseInsensitiveFileBackend() throws Exception {
        // Test uppercase
        config.put("backendType", "FILE");
        LdapBackend upperCaseBackend = BackendFactory.createBackend("UPPER", config);
        assertTrue("Should create FileBackend with uppercase type", upperCaseBackend instanceof FileBackend);

        // Test mixed case
        config.put("backendType", "File");
        LdapBackend mixedCaseBackend = BackendFactory.createBackend("Mixed", config);
        assertTrue("Should create FileBackend with mixed case type", mixedCaseBackend instanceof FileBackend);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownBackendThrowsException() throws Exception {
        config.put("backendType", "unknown");
        BackendFactory.createBackend("testbackend", config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullBackendNameThrowsException() throws Exception {
        config.put("backendType", null);
        BackendFactory.createBackend("testbackend", config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyBackendNameThrowsException() throws Exception {
        config.put("backendType", "");
        BackendFactory.createBackend("testbackend", config);
    }
}
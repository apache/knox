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
        try (java.io.FileWriter writer = new java.io.FileWriter(tempFile, java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write("{\"users\":[],\"groups\":[]}\n");
        }

        config.put("dataFile", tempFile.getAbsolutePath());
    }

    @Test
    public void testServiceLoaderDiscovery() {
        ServiceLoader<LdapBackend> loader = ServiceLoader.load(LdapBackend.class);

        // Should discover at least the built-in backends
        boolean foundFileBackend = false;
        boolean foundLdapBackend = false;

        for (LdapBackend backend : loader) {
            String backendName = backend.getName();
            if ("file".equals(backendName)) {
                foundFileBackend = true;
                assertTrue("File backend should be FileBackend instance", backend instanceof FileBackend);
            } else if ("ldap".equals(backendName)) {
                foundLdapBackend = true;
                assertTrue("LDAP backend should be LdapProxyBackend instance", backend instanceof LdapProxyBackend);
            }
        }

        assertTrue("ServiceLoader should discover file backend", foundFileBackend);
        assertTrue("ServiceLoader should discover ldap backend", foundLdapBackend);
    }

    @Test
    public void testCreateFileBackend() throws Exception {
        LdapBackend fileBackend = BackendFactory.createBackend("file", config);

        assertNotNull("File backend should be created", fileBackend);
        assertTrue("Should create FileBackend instance", fileBackend instanceof FileBackend);
        assertEquals("Backend name should be 'file'", "file", fileBackend.getName());
    }

    @Test
    public void testCreateLdapBackend() throws Exception {
        config.put("url", "ldap://localhost:389");
        config.put("remoteBaseDn", "dc=hadoop,dc=apache,dc=org");

        LdapBackend ldapBackend = BackendFactory.createBackend("ldap", config);

        assertNotNull("LDAP backend should be created", ldapBackend);
        assertTrue("Should create LdapProxyBackend instance", ldapBackend instanceof LdapProxyBackend);
        assertEquals("Backend name should be 'ldap'", "ldap", ldapBackend.getName());
    }

    @Test
    public void testCaseInsensitiveBackendNames() throws Exception {
        // Test uppercase
        LdapBackend upperCaseBackend = BackendFactory.createBackend("FILE", config);
        assertTrue("Should create FileBackend with uppercase name", upperCaseBackend instanceof FileBackend);

        // Test mixed case
        LdapBackend mixedCaseBackend = BackendFactory.createBackend("File", config);
        assertTrue("Should create FileBackend with mixed case name", mixedCaseBackend instanceof FileBackend);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownBackendThrowsException() throws Exception {
        BackendFactory.createBackend("unknown", config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullBackendNameThrowsException() throws Exception {
        BackendFactory.createBackend(null, config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyBackendNameThrowsException() throws Exception {
        BackendFactory.createBackend("", config);
    }
}
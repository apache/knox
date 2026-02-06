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
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * Unit tests for KnoxLDAPService.
 */
public class KnoxLDAPServiceTest {

    private KnoxLDAPService ldapService;
    private GatewayConfig mockConfig;
    private File tempDataDir;
    private File tempLdapFile;

    @Before
    public void setUp() throws Exception {
        ldapService = new KnoxLDAPService();
        mockConfig = createMock(GatewayConfig.class);

        // Create temporary directories and files
        tempDataDir = File.createTempFile("knox-ldap-test", "");
        tempDataDir.delete();
        tempDataDir.mkdirs();
        tempDataDir.deleteOnExit();

        tempLdapFile = new File(tempDataDir, "ldap-users.json");
        try (java.io.FileWriter writer = new java.io.FileWriter(tempLdapFile, java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write("{\"users\":[],\"groups\":[]}");
        }
        tempLdapFile.deleteOnExit();
    }

    @After
    public void tearDown() throws Exception {
        if (ldapService != null) {
            try {
                ldapService.stop();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        if (tempLdapFile != null && tempLdapFile.exists()) {
            tempLdapFile.delete();
        }
        if (tempDataDir != null && tempDataDir.exists()) {
            tempDataDir.delete();
        }
    }

    @Test
    public void testInitWithLdapDisabled() throws Exception {
        expect(mockConfig.isLDAPEnabled()).andReturn(false);
        replay(mockConfig);

        ldapService.init(mockConfig, new HashMap<>());

        assertFalse("LDAP service should not be enabled", ldapService.isEnabled());
        assertEquals("LDAP port should be -1 when disabled", -1, ldapService.getLdapPort());

        verify(mockConfig);
    }

    @Test
    public void testInitWithLdapEnabledFileBackend() throws Exception {
        setupMockConfigForFileBackend();
        replay(mockConfig);

        ldapService.init(mockConfig, new HashMap<>());

        assertTrue("LDAP service should be enabled", ldapService.isEnabled());
        assertEquals("Base DN should match config", "dc=test,dc=com", ldapService.getBaseDn());

        verify(mockConfig);
    }

    @Test
    public void testInitWithLdapEnabledLdapBackend() throws Exception {
        setupMockConfigForLdapBackend();
        replay(mockConfig);

        ldapService.init(mockConfig, new HashMap<>());

        assertTrue("LDAP service should be enabled", ldapService.isEnabled());
        assertEquals("Base DN should match config", "dc=proxy,dc=com", ldapService.getBaseDn());

        verify(mockConfig);
    }

    @Test(expected = ServiceLifecycleException.class)
    public void testInitWithInvalidBackendType() throws Exception {
        expect(mockConfig.isLDAPEnabled()).andReturn(true);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempDataDir.getAbsolutePath());
        expect(mockConfig.getLDAPPort()).andReturn(3890);
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=test,dc=com");
        expect(mockConfig.getLDAPBackendType()).andReturn("invalid");
        expect(mockConfig.getLDAPBackendConfig("invalid")).andReturn(new HashMap<>());
        replay(mockConfig);

        ldapService.init(mockConfig, new HashMap<>());
    }

    @Test
    public void testStartWhenDisabled() throws Exception {
        expect(mockConfig.isLDAPEnabled()).andReturn(false);
        replay(mockConfig);

        ldapService.init(mockConfig, new HashMap<>());

        // Should not throw exception
        ldapService.start();

        verify(mockConfig);
    }

    @Test
    public void testStopWhenDisabled() throws Exception {
        expect(mockConfig.isLDAPEnabled()).andReturn(false);
        replay(mockConfig);

        ldapService.init(mockConfig, new HashMap<>());

        // Should not throw exception
        ldapService.stop();

        verify(mockConfig);
    }

    @Test
    public void testGettersWhenNotInitialized() {
        assertEquals("LDAP port should be -1 when not initialized", -1, ldapService.getLdapPort());
        assertEquals("Base DN should be null when not initialized", null, ldapService.getBaseDn());
        assertFalse("Should not be enabled when not initialized", ldapService.isEnabled());
    }

    private void setupMockConfigForFileBackend() {
        expect(mockConfig.isLDAPEnabled()).andReturn(true);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempDataDir.getAbsolutePath());
        expect(mockConfig.getLDAPPort()).andReturn(3890);
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=test,dc=com");
        expect(mockConfig.getLDAPBackendType()).andReturn("file");

        Map<String, String> fileBackendConfig = new HashMap<>();
        fileBackendConfig.put("dataFile", tempLdapFile.getAbsolutePath());
        expect(mockConfig.getLDAPBackendConfig("file")).andReturn(fileBackendConfig);
    }

    private void setupMockConfigForLdapBackend() {
        expect(mockConfig.isLDAPEnabled()).andReturn(true);
        expect(mockConfig.getGatewayDataDir()).andReturn(tempDataDir.getAbsolutePath());
        expect(mockConfig.getLDAPPort()).andReturn(3890);
        expect(mockConfig.getLDAPBaseDN()).andReturn("dc=proxy,dc=com");
        expect(mockConfig.getLDAPBackendType()).andReturn("ldap");

        Map<String, String> ldapBackendConfig = new HashMap<>();
        ldapBackendConfig.put("url", "ldap://localhost:33389");
        ldapBackendConfig.put("remoteBaseDn", "dc=hadoop,dc=apache,dc=org");
        ldapBackendConfig.put("systemUsername", "cn=admin,dc=hadoop,dc=apache,dc=org");
        ldapBackendConfig.put("systemPassword", "admin-password");
        expect(mockConfig.getLDAPBackendConfig("ldap")).andReturn(ldapBackendConfig);
    }
}
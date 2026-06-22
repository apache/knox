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
import org.apache.knox.gateway.services.security.AliasService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        // No bind password stored in the credential store -> anonymous access (default behavior).
        final AliasService aliasService = createNiceMock(AliasService.class);
        replay(aliasService);
        ldapService.setAliasService(aliasService);
        mockConfig = createMock(GatewayConfig.class);

        // Create temporary directories and files
        tempDataDir = File.createTempFile("knox-ldap-test", "");
        tempDataDir.delete();
        tempDataDir.mkdirs();
        tempDataDir.deleteOnExit();

        tempLdapFile = new File(tempDataDir, "ldap-users.json");
        try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(tempLdapFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
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
        setupMockConfig("file");

        ldapService.init(mockConfig, new HashMap<>());

        assertTrue("LDAP service should be enabled", ldapService.isEnabled());
        assertEquals("Base DN should match config", "dc=test,dc=com", ldapService.getBaseDn());

        verify(mockConfig);
    }

    @Test
    public void testInitWithLdapEnabledLdapBackend() throws Exception {
        setupMockConfig("ldap");

        ldapService.init(mockConfig, new HashMap<>());

        assertTrue("LDAP service should be enabled", ldapService.isEnabled());
        assertEquals("Base DN should match config", "dc=proxy,dc=com", ldapService.getBaseDn());

        verify(mockConfig);
    }

    @Test(expected = ServiceLifecycleException.class)
    public void testInitWithInvalidBackendType() throws Exception {
        setupMockConfig("invalid");

        ldapService.init(mockConfig, new HashMap<>());
    }

    @Test
    public void testStartAndStopWhenDisabled() throws Exception {
        expect(mockConfig.isLDAPEnabled()).andReturn(false);
        replay(mockConfig);

        ldapService.init(mockConfig, new HashMap<>());

        // Should not throw exception
        ldapService.start();

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

    @Test
    public void testOnGatewayConfigChanged() throws Exception {
        setupMockConfig("file");

        ldapService.init(mockConfig, new HashMap<>());
        assertEquals("Initial port should be 3890", 3890, ldapService.getLdapPort());

        // Test reload with new port
        ldapService.onGatewayConfigChanged(mockConfig);

        assertEquals("Port should be updated to 3891 after reload", 3891, ldapService.getLdapPort());

        verify(mockConfig);
    }

    private void setupMockConfig(String backendType) throws Exception {
        expect(mockConfig.isLDAPEnabled()).andReturn(true).atLeastOnce();
        expect(mockConfig.isLDAPRecursiveGroupResolutionEnabled()).andReturn(false).atLeastOnce();
        expect(mockConfig.getLDAPRecursiveGroupResolutionMaxDepth()).andReturn(0).atLeastOnce();
        expect(mockConfig.getGatewayDataDir()).andReturn(tempDataDir.getAbsolutePath()).atLeastOnce();
        expect(mockConfig.getLDAPPort()).andReturn(3890).times(1).andReturn(3891).anyTimes();
        expect(mockConfig.getLDAPBaseDN()).andReturn("file".equals(backendType) ? "dc=test,dc=com" : "dc=proxy,dc=com").atLeastOnce();
        expect(mockConfig.getLDAPBindUser()).andReturn(null).anyTimes();
        expect(mockConfig.getLDAPInterceptorNames()).andReturn(List.of("testbackend")).atLeastOnce();
        expect(mockConfig.getLDAPInterceptorConfig("testbackend")).andReturn(buildBackendConfig(backendType)).atLeastOnce();
        replay(mockConfig);
    }

    private Map<String, String> buildBackendConfig(String backendType) {
        final Map<String, String> backendConfig = new HashMap<>();
        backendConfig.put("interceptorType", "backend");
        backendConfig.put("backendType", backendType);
        if ("ldap".equals(backendType)) {
            backendConfig.put("url", "ldap://localhost:33389");
            backendConfig.put("remoteBaseDn", "dc=hadoop,dc=apache,dc=org");
            backendConfig.put("systemUsername", "cn=admin,dc=hadoop,dc=apache,dc=org");
            backendConfig.put("systemPassword", "admin-password");
        } else if ("file".equals(backendType)) {
            backendConfig.put("dataFile", tempLdapFile.getAbsolutePath());
        }
        return backendConfig;
    }
}

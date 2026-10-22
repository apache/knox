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
package org.apache.knox.gateway.services.ldap.roles;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ldap.RoleAssignees;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DefaultLDAPRolesLookupServiceTest {
    private File rolesFile;

    @Before
    public void setUp() throws Exception {
        rolesFile = File.createTempFile("roles-service", ".json");
        FileUtils.writeStringToFile(rolesFile, """
                [
                  { "id": "alice", "type": "user", "roles": [ {"scope": "platform", "name": "admin"} ] },
                  { "id": "engineering", "type": "group", "roles": [ {"scope": "platform", "name": "admin"}, {"name": "viewer"} ] }
                ]""", StandardCharsets.UTF_8);
    }

    @After
    public void tearDown() {
        rolesFile.delete();
    }

    private static GatewayConfig config(String strategy, String filePath) {
        GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(config.getLdapRolesLookupStrategy()).andReturn(strategy).anyTimes();
        EasyMock.expect(config.getLdapRolesLookupFilePath()).andReturn(filePath).anyTimes();
        EasyMock.replay(config);
        return config;
    }

    private DefaultLDAPRolesLookupService enabledService() throws Exception {
        DefaultLDAPRolesLookupService service = new DefaultLDAPRolesLookupService();
        service.init(config("file", rolesFile.getAbsolutePath()), Map.of());
        return service;
    }

    @Test
    public void testServiceIsDisabledWithoutStrategy() throws Exception {
        DefaultLDAPRolesLookupService service = new DefaultLDAPRolesLookupService();
        service.init(config(null, null), Map.of());

        assertFalse(service.enabled());
        assertTrue(service.lookupRoles("alice", List.of("engineering")).isEmpty());
        assertTrue(service.listRoles().isEmpty());
        assertNull(service.lookupRoleAssignees("platform:admin"));
    }

    @Test
    public void testInitWithInvalidConfigurationFails() {
        DefaultLDAPRolesLookupService service = new DefaultLDAPRolesLookupService();
        assertThrows(ServiceLifecycleException.class, () -> service.init(config("file", ""), Map.of()));
        assertThrows(ServiceLifecycleException.class, () -> service.init(config("bogus", null), Map.of()));
    }

    @Test
    public void testEnabledServiceDelegatesLookupRoles() throws Exception {
        Collection<String> roles = enabledService().lookupRoles("alice", List.of("engineering"));

        assertEquals(Set.of("platform:admin", "viewer"), new HashSet<>(roles));
    }

    @Test
    public void testEnabledServiceListsRoles() throws Exception {
        DefaultLDAPRolesLookupService service = enabledService();

        assertTrue(service.enabled());
        assertEquals(Set.of("platform:admin", "viewer"), new HashSet<>(service.listRoles()));
    }

    @Test
    public void testEnabledServiceLooksUpRoleAssignees() throws Exception {
        RoleAssignees assignees = enabledService().lookupRoleAssignees("platform:admin");

        assertEquals(List.of("alice"), assignees.getUsers());
        assertEquals(List.of("engineering"), assignees.getGroups());
    }

    @Test
    public void testConfigChangeEnablesLookup() throws Exception {
        DefaultLDAPRolesLookupService service = new DefaultLDAPRolesLookupService();
        service.init(config(null, null), Map.of());
        assertFalse(service.enabled());

        service.onGatewayConfigChanged(config("file", rolesFile.getAbsolutePath()));

        assertTrue(service.enabled());
        assertEquals(Set.of("platform:admin", "viewer"), new HashSet<>(service.lookupRoles("alice", List.of("engineering"))));
    }

    @Test
    public void testFailedConfigChangeKeepsPreviousConfiguration() throws Exception {
        DefaultLDAPRolesLookupService service = enabledService();

        service.onGatewayConfigChanged(config("bogus", null));

        assertTrue(service.enabled());
        assertEquals(Set.of("platform:admin", "viewer"), new HashSet<>(service.lookupRoles("alice", List.of("engineering"))));
    }
}

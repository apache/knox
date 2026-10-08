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
import org.apache.knox.gateway.services.ldap.RoleAssignees;
import org.apache.knox.gateway.services.ldap.roles.file.FileBasedLdapRolesReverseLookup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileBasedLdapRolesReverseLookupTest {
    private File testFile;

    @Before
    public void setUp() throws Exception {
        testFile = File.createTempFile("roles-mapping-reverse", ".json");
        String json = """
                [
                  { "id": "alice", "type": "user",
                    "roles": [ {"scope": "platform", "name": "awc-admin"}, {"name": "viewer"} ] },
                  { "id": "bob", "type": "USER",
                    "roles": [ {"name": "viewer"} ] },
                  { "id": "engineering", "type": "group",
                    "roles": [ {"scope": "ml-workspace-abc", "name": "viewer"}, {"scope": "platform", "name": "awc-admin"} ] },
                  { "id": "mystery", "type": "robot",
                    "roles": [ {"scope": "platform", "name": "awc-admin"} ] }
                ]""";
        FileUtils.writeStringToFile(testFile, json, StandardCharsets.UTF_8);
    }

    @After
    public void tearDown() {
        if (testFile != null) {
            testFile.delete();
        }
    }

    private FileBasedLdapRolesReverseLookup lookup() {
        return new FileBasedLdapRolesReverseLookup(testFile.getAbsolutePath());
    }

    @Test
    public void testListRolesReturnsDistinctDisplayValues() throws Exception {
        Collection<String> roles = lookup().listRoles();
        assertEquals(Set.of("platform:awc-admin", "viewer", "ml-workspace-abc:viewer"), new HashSet<>(roles));
        assertEquals(3, roles.size());
    }

    @Test
    public void testLookupRoleAssigneesSplitsUsersAndGroups() throws Exception {
        RoleAssignees assignees = lookup().lookupRoleAssignees("platform:awc-admin");
        assertEquals(Set.of("alice"), new HashSet<>(assignees.getUsers()));
        assertEquals(Set.of("engineering"), new HashSet<>(assignees.getGroups()));
    }

    @Test
    public void testLookupRoleAssigneesIsCaseInsensitiveAndTypeInsensitive() throws Exception {
        RoleAssignees assignees = lookup().lookupRoleAssignees("VIEWER");
        assertEquals(Set.of("alice", "bob"), new HashSet<>(assignees.getUsers()));
        assertTrue(assignees.getGroups().isEmpty());
    }

    @Test
    public void testLookupRoleAssigneesDoesNotMatchOtherScope() throws Exception {
        RoleAssignees assignees = lookup().lookupRoleAssignees("ml-workspace-abc:viewer");
        assertTrue(assignees.getUsers().isEmpty());
        assertEquals(Set.of("engineering"), new HashSet<>(assignees.getGroups()));
    }

    @Test
    public void testLookupUnknownRoleReturnsEmptyAssignees() throws Exception {
        RoleAssignees assignees = lookup().lookupRoleAssignees("nope");
        assertTrue(assignees.getUsers().isEmpty());
        assertTrue(assignees.getGroups().isEmpty());
    }

    @Test(expected = RoleException.class)
    public void testListRolesWithMissingFileFails() throws Exception {
        new FileBasedLdapRolesReverseLookup("non-existent-file.json").listRoles();
    }

    @Test(expected = RoleException.class)
    public void testLookupWithMissingFileFails() throws Exception {
        new FileBasedLdapRolesReverseLookup("non-existent-file.json").lookupRoleAssignees("viewer");
    }

    @Test(expected = RoleException.class)
    public void testMalformedJsonFails() throws Exception {
        FileUtils.writeStringToFile(testFile, "{ not json", StandardCharsets.UTF_8);
        lookup().listRoles();
    }
}

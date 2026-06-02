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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileBasedLdapRolesLookupTest {
    private File testFile;

    @Before
    public void setUp() throws Exception {
        testFile = File.createTempFile("roles-mapping", ".json");
        String json = """
                [
                  {
                    "id": "alice",
                    "type": "user",
                    "roles": [ {"scope": "platform", "name": "awc-admin"} ]
                  },
                  {
                    "id": "bob",
                    "type": "user",
                    "roles": [ {"name": "viewer"} ]
                  },
                  {
                    "id": "engineering",
                    "type": "group",
                    "roles": [ {"scope": "ml-workspace-abc", "name": "viewer"} ]
                  },
                  {
                    "id": "admins",
                    "type": "group",
                    "roles": [ {"scope": "platform", "name": "super-user"} ]
                  }
                ]""";
        FileUtils.writeStringToFile(testFile, json, StandardCharsets.UTF_8);
    }

    @After
    public void tearDown() throws Exception {
        if (testFile != null && testFile.exists()) {
            testFile.delete();
        }
    }

    @Test
    public void testUserLookup() throws Exception {
        FileBasedLdapRolesLookup lookup = new FileBasedLdapRolesLookup(testFile.getAbsolutePath());
        Collection<String> roles = lookup.lookupRoles("alice", List.of("other-group"));
        assertEquals(1, roles.size());
        assertTrue(roles.contains("platform:awc-admin"));
    }

    @Test
    public void testGroupLookup() throws Exception {
        FileBasedLdapRolesLookup lookup = new FileBasedLdapRolesLookup(testFile.getAbsolutePath());
        Collection<String> roles = lookup.lookupRoles("unknown-user", List.of("engineering"));
        assertEquals(1, roles.size());
        assertTrue(roles.contains("ml-workspace-abc:viewer"));
    }

    @Test
    public void testUserAndGroupLookup() throws Exception {
        FileBasedLdapRolesLookup lookup = new FileBasedLdapRolesLookup(testFile.getAbsolutePath());
        Collection<String> roles = lookup.lookupRoles("alice", List.of("engineering", "admins"));
        assertEquals(3, roles.size());
        assertTrue(roles.contains("platform:awc-admin"));
        assertTrue(roles.contains("ml-workspace-abc:viewer"));
        assertTrue(roles.contains("platform:super-user"));
    }

    @Test
    public void testNoScope() throws Exception {
        FileBasedLdapRolesLookup lookup = new FileBasedLdapRolesLookup(testFile.getAbsolutePath());
        Collection<String> roles = lookup.lookupRoles("bob", null);
        assertEquals(1, roles.size());
        assertTrue(roles.contains("viewer"));
    }

    @Test(expected=RoleLookupException.class)
    public void testFileNotFound() throws Exception {
        new FileBasedLdapRolesLookup("non-existent-file.json").lookupRoles("alice", null);
    }
}

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

import org.apache.directory.api.ldap.model.exception.LdapNoSuchObjectException;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class FileBackendTest {
    private static final String PEOPLE = "ou=people,dc=proxy,dc=com";
    private static final String ALICE = "uid=alice," + PEOPLE;

    private SchemaManager schemaManager;
    private FileBackend backend;

    @Before
    public void setUp() throws Exception {
        File dataFile = File.createTempFile("ldap-users", ".json");
        dataFile.deleteOnExit();
        Files.writeString(dataFile.toPath(), "{\"users\":["
                + "{\"username\":\"alice\",\"cn\":\"Alice\",\"sn\":\"Smith\"},"
                + "{\"username\":\"bob\",\"cn\":\"Bob\",\"sn\":\"Jones\"}]}", StandardCharsets.UTF_8);
        Map<String, String> config = new HashMap<>();
        config.put("dataFile", dataFile.getAbsolutePath());
        config.put("baseDn", "dc=proxy,dc=com");
        backend = new FileBackend("file", config);
        schemaManager = SchemaManagerFactory.createSchemaManager();
    }

    @Test
    public void testSearchUnderPeopleMatchesUsersByFilter() throws Exception {
        assertEquals(List.of(ALICE), search(PEOPLE, SearchScope.SUBTREE, "(uid=alice)"));
        assertEquals(List.of(ALICE), search(PEOPLE, SearchScope.ONELEVEL, "(uid=alice)"));
    }

    @Test
    public void testBaseObjectSearchOnPeopleReturnsNoUser() throws Exception {
        assertEquals(List.of(), search(PEOPLE, SearchScope.OBJECT, "(uid=alice)"));
    }

    @Test
    public void testSearchOnUserEntryEvaluatesFilterOnThatUser() throws Exception {
        assertEquals(List.of(ALICE), search(ALICE, SearchScope.OBJECT, "(objectClass=*)"));
        assertEquals(List.of(ALICE), search(ALICE, SearchScope.SUBTREE, "(objectClass=*)"));
        assertEquals(List.of(ALICE), search(ALICE, SearchScope.OBJECT, "(uid=ALICE)"));
        assertEquals(List.of(ALICE), search(ALICE, SearchScope.OBJECT, "(&(objectClass=Person)(cn=al*e)(!(sn=Jones)))"));
        assertEquals(List.of(ALICE), search(ALICE, SearchScope.OBJECT, "(|(objectClass=groupOfNames)(sn=smith))"));
        assertEquals(List.of(), search(ALICE, SearchScope.OBJECT, "(uid=bob)"));
        assertEquals(List.of(), search(ALICE, SearchScope.SUBTREE, "(uid=bob)"));
        assertEquals(List.of(), search(ALICE, SearchScope.OBJECT, "(objectClass=groupOfNames)"));
        assertEquals(List.of(), search(ALICE, SearchScope.OBJECT, "(mail=*)"));
    }

    @Test
    public void testSearchOnUserEntryMatchesUidCaseInsensitively() throws Exception {
        assertEquals(List.of(ALICE), search("uid=Alice," + PEOPLE, SearchScope.OBJECT, "(objectClass=*)"));
    }

    @Test
    public void testOneLevelSearchOnUserEntryReturnsNothing() throws Exception {
        assertEquals(List.of(), search(ALICE, SearchScope.ONELEVEL, "(objectClass=*)"));
    }

    @Test
    public void testSearchOnUnknownEntryThrowsNoSuchObject() {
        for (String base : List.of("uid=ghost," + PEOPLE, "cn=alice," + PEOPLE, "cn=child," + ALICE)) {
            for (SearchScope scope : SearchScope.values()) {
                assertThrows(base + " " + scope, LdapNoSuchObjectException.class, () -> search(base, scope, "(objectClass=*)"));
            }
        }
    }

    private List<String> search(String base, SearchScope scope, String filter) throws Exception {
        return backend.search(base, scope, filter, schemaManager, null).stream()
                .map(entry -> entry.getDn().getName())
                .collect(Collectors.toList());
    }
}

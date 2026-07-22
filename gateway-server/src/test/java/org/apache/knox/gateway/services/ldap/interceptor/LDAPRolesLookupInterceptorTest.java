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
package org.apache.knox.gateway.services.ldap.interceptor;

import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.knox.gateway.security.ldap.SimpleDirectoryService;
import org.apache.knox.gateway.services.ldap.LDAPRolesLookupService;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.apache.knox.gateway.services.ldap.control.RolesLookupBypassControl;
import org.apache.knox.gateway.services.ldap.control.RolesLookupBypassControlImpl;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LDAPRolesLookupInterceptorTest {
    private SchemaManager schemaManager;

    @Before
    public void setUp() throws Exception {
        schemaManager = SchemaManagerFactory.createSchemaManager();
    }

    @Test
    public void testModifyEntryWithRoles() throws Exception {
        final Entry userEntry = createUserEntry("alice", "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org");
        final Collection<String> roles = Arrays.asList("roleA", "roleG");

        final Entry modifiedEntry = createInterceptor().modifyEntry(userEntry, roles);

        assertMemberOf(modifiedEntry,
                "cn=roleA,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=roleG,ou=groups,dc=hadoop,dc=apache,dc=org");
    }

    @Test
    public void testModifyEntryWithNoRoles() throws Exception {
        final Entry userEntry = createUserEntry("bob", "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org");
        final Collection<String> roles = Collections.emptyList();

        final Entry modifiedEntry = createInterceptor().modifyEntry(userEntry, roles);

        assertNull("memberOf attribute should be removed when no roles are found", modifiedEntry.get("memberOf"));
    }

    @Test
    public void testModifyEntryNoMemberOfNoRoles() throws Exception {
        final Entry userEntry = createUserEntry("charlie");
        final Collection<String> roles = Collections.emptyList();

        final Entry modifiedEntry = createInterceptor().modifyEntry(userEntry, roles);

        assertEquals(userEntry, modifiedEntry);
        assertNull(modifiedEntry.get("memberOf"));
    }

    @Test
    public void testRolesLookupNoBypass() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);

        final Collection<String> roles = Arrays.asList("roleA", "roleG");
        expect(mockRolesService.lookupRoles(anyString(), anyObject()))
                .andReturn(roles)
                .atLeastOnce();
        replay(mockRolesService);

        TestContext testContext = createTestContext(false, mockRolesService);

        // Set up test to with group and role mapping
        final Entry userEntry = createUserEntry("alice", "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org");
        testContext.nextInterceptor.setEntries(List.of(userEntry));

        final EntryFilteringCursor entries = testContext.interceptor.search(testContext.ctx);

        assertTrue(entries.next());
        Entry modifiedEntry = entries.get();
        assertMemberOf(modifiedEntry,
                "cn=roleA,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=roleG,ou=groups,dc=hadoop,dc=apache,dc=org");
        assertFalse(entries.next());
    }

    @Test
    public void testRolesLookupWithBypass() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);

        TestContext testContext = createTestContext(true, mockRolesService);

        // Set up test to with group and role mapping
        final Entry userEntry = createUserEntry("alice", "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org");
        testContext.nextInterceptor.setEntries(List.of(userEntry));

        final EntryFilteringCursor entries = testContext.interceptor.search(testContext.ctx);

        assertTrue(entries.next());
        Entry modifiedEntry = entries.get();
        assertMemberOf(modifiedEntry, "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org");
        assertFalse(entries.next());
    }

    private TestContext createTestContext(boolean bypass, LDAPRolesLookupService rolesService) throws Exception {
        DirectoryService directoryService = new SimpleDirectoryService();
        directoryService.setShutdownHookEnabled(false);
        directoryService.setSchemaManager(SchemaManagerFactory.createSchemaManager());

        LDAPRolesLookupInterceptor interceptor =
                new LDAPRolesLookupInterceptor(rolesService);
        interceptor.init(directoryService);
        directoryService.addLast(interceptor);

        ConfigurableEntriesTestInterceptor nextInterceptor =
                new ConfigurableEntriesTestInterceptor("NEXT");
        nextInterceptor.init(directoryService);
        directoryService.addLast(nextInterceptor);

        SearchOperationContext ctx =
                new SearchOperationContext(directoryService.getSession());
        ctx.setInterceptors(List.of(interceptor.getName(), "NEXT"));

        RolesLookupBypassControl control =
                new RolesLookupBypassControlImpl();
        control.setBypassRolesLookup(bypass);
        ctx.addRequestControl(control);

        return new TestContext(interceptor, nextInterceptor, ctx);
    }

    private LDAPRolesLookupService createMockRolesService() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);
        replay(mockRolesService);
        return mockRolesService;
    }

    private LDAPRolesLookupInterceptor createInterceptor() throws Exception {
        return new LDAPRolesLookupInterceptor(createMockRolesService());
    }

    private Entry createUserEntry(final String username, final String... memberOfDns) throws Exception {
        final Entry entry = new DefaultEntry(schemaManager);
        entry.add("uid", username);
        for (final String dn : memberOfDns) {
            entry.add("memberOf", dn);
        }
        return entry;
    }

    private void assertMemberOf(final Entry entry, final String... expectedDns) {
        final Attribute memberOf = entry.get("memberOf");
        assertEquals("Unexpected number of memberOf attributes", expectedDns.length, memberOf.size());
        for (final String expected : expectedDns) {
            assertTrue("Missing expected role DN: " + expected, memberOf.contains(expected));
        }
    }

    private record TestContext(
            LDAPRolesLookupInterceptor interceptor,
            ConfigurableEntriesTestInterceptor nextInterceptor,
            SearchOperationContext ctx) {
    }
}

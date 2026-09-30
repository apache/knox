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
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.knox.gateway.security.ldap.SimpleDirectoryService;
import org.apache.knox.gateway.services.ldap.LDAPRolesLookupService;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.apache.knox.gateway.services.ldap.control.RolesLookupBypassControl;
import org.apache.knox.gateway.services.ldap.control.RolesLookupBypassControlImpl;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
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
    public void testModifyEntryWithRolesButNoExistingMemberOf() throws Exception {
        final Entry userEntry = createUserEntry("dave");
        final Collection<String> roles = List.of("roleA");

        final Entry modifiedEntry = createInterceptor().modifyEntry(userEntry, roles);

        // With no existing memberOf DN to model the new value on, the role is added as a
        // literal "cn=<role>" rather than a full DN.
        assertMemberOf(modifiedEntry, "cn=roleA");
    }

    @Test
    public void testSearchWithNoBypassControlPresent() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);
        final Collection<String> roles = List.of("roleA");
        expect(mockRolesService.lookupRoles(anyString(), anyObject()))
                .andReturn(roles)
                .atLeastOnce();
        replay(mockRolesService);

        final TestContext testContext = createTestContextWithoutBypassControl(mockRolesService);

        final Entry userEntry = createUserEntry("alice", "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org");
        testContext.nextInterceptor.setEntries(List.of(userEntry));

        final EntryFilteringCursor entries = testContext.interceptor.search(testContext.ctx);

        assertTrue(entries.next());
        assertMemberOf(entries.get(), "cn=roleA,ou=groups,dc=hadoop,dc=apache,dc=org");
        assertFalse(entries.next());
    }

    @Test
    public void testSearchWrapsCursorIterationFailure() throws Exception {
        final TestContext testContext = createTestContext(false, createMockRolesService());
        // The production code logs the failure via LdapUtils.extractGroupName(ctx.getDn()),
        // which requires a non-null Dn; a real search context always carries the search base.
        testContext.ctx.setDn(new Dn("ou=groups,dc=hadoop,dc=apache,dc=org"));
        LdapException expectedException = new LdapException("backend failure");
        testContext.nextInterceptor.setLdapException(expectedException);

        LdapException exception = assertThrows(LdapException.class,
                () -> testContext.interceptor.search(testContext.ctx));
        // the underlying cursor failure should be wrapped, not propagated raw
        assertEquals(expectedException, exception.getCause());
    }

    @Test
    public void testSearchWrapsRolesLookupFailure() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);
        RuntimeException expectedException = new RuntimeException("roles lookup failed");
        expect(mockRolesService.lookupRoles(anyString(), anyObject()))
                .andThrow(expectedException);
        replay(mockRolesService);

        final TestContext testContext = createTestContext(false, mockRolesService);
        testContext.nextInterceptor.setEntries(List.of(createUserEntry("alice")));

        LdapException exception = assertThrows(LdapException.class,
                () -> testContext.interceptor.search(testContext.ctx));
        // the underlying cursor failure should be wrapped, not propagated raw
        assertEquals(expectedException, exception.getCause());
    }

    @Test
    public void testSearchIgnoresMalformedMemberOfDnWhenFetchingGroups() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);
        // A strict (non-nice) mock: an unexpected call, e.g. with the malformed DN's raw
        // value treated as a group name, would fail this test immediately.
        expect(mockRolesService.lookupRoles("alice", Set.of("group1")))
                .andReturn(List.of("roleA"));
        replay(mockRolesService);

        final TestContext testContext = createTestContext(false, mockRolesService);

        final Entry userEntry = createUserEntry("alice",
                "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org",
                "this is not a valid dn");
        testContext.nextInterceptor.setEntries(List.of(userEntry));

        try (EntryFilteringCursor entries = testContext.interceptor.search(testContext.ctx)) {
            assertTrue(entries.next());
            assertMemberOf(entries.get(), "cn=roleA,ou=groups,dc=hadoop,dc=apache,dc=org");
            assertFalse(entries.next());
        }
        verify(mockRolesService);
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

        try (EntryFilteringCursor entries = testContext.interceptor.search(testContext.ctx)) {
            assertTrue(entries.next());
            Entry modifiedEntry = entries.get();
            assertMemberOf(modifiedEntry,
                    "cn=roleA,ou=groups,dc=hadoop,dc=apache,dc=org",
                    "cn=roleG,ou=groups,dc=hadoop,dc=apache,dc=org");
            assertFalse(entries.next());
        }
    }

    @Test
    public void testRolesLookupWithBypass() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);

        TestContext testContext = createTestContext(true, mockRolesService);

        // Set up test to with group and role mapping
        final Entry userEntry = createUserEntry("alice", "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org");
        testContext.nextInterceptor.setEntries(List.of(userEntry));

        try (EntryFilteringCursor entries = testContext.interceptor.search(testContext.ctx)) {
            assertTrue(entries.next());
            Entry modifiedEntry = entries.get();
            assertMemberOf(modifiedEntry, "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org");
            assertFalse(entries.next());
        }
    }

    @Test
    public void testRolesLookupKeysOnUidAttributeAndStripsItWhenNotRequested() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);

        final Capture<String> usernameCapture = EasyMock.newCapture();
        expect(mockRolesService.lookupRoles(EasyMock.capture(usernameCapture), anyObject()))
                .andReturn(Arrays.asList("roleA"))
                .atLeastOnce();
        replay(mockRolesService);

        final TestContext testContext = createTestContext(false, mockRolesService);
        // The client (e.g. Hadoop LdapGroupsMapping) requested a limited set that does not include
        // uid. The interceptor forces uid to be returned so it can be resolved, then strips it out.
        testContext.ctx.setAllUserAttributes(false);
        testContext.ctx.setReturningAttributes("memberOf");

        // uid is the stable id; cn is a display name (as it would be for an AD-backed entry).
        final Entry userEntry =
                new DefaultEntry(schemaManager, "cn=Alice Display Name,ou=people,dc=hadoop,dc=apache,dc=org");
        userEntry.add("uid", "alice");
        userEntry.add("cn", "Alice Display Name");
        userEntry.add("memberOf", "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org");
        testContext.nextInterceptor.setEntries(List.of(userEntry));

        final EntryFilteringCursor result = testContext.interceptor.search(testContext.ctx);

        assertTrue(result.next());
        final Entry servedEntry = result.get();
        assertEquals("Role lookup must be keyed on the uid attribute, not the cn display name",
                "alice", usernameCapture.getValue());
        assertNull("uid must be stripped from the entry when the client did not request it",
                servedEntry.get("uid"));
    }

    @Test
    public void testRolesLookupRetainsUidWhenClientRequestedIt() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);

        final Capture<String> usernameCapture = EasyMock.newCapture();
        expect(mockRolesService.lookupRoles(EasyMock.capture(usernameCapture), anyObject()))
                .andReturn(Arrays.asList("roleA"))
                .atLeastOnce();
        replay(mockRolesService);

        final TestContext testContext = createTestContext(false, mockRolesService);
        // The client explicitly asked for uid, so it must survive in the response.
        testContext.ctx.setAllUserAttributes(false);
        testContext.ctx.setReturningAttributes("uid", "memberOf");

        final Entry userEntry =
                new DefaultEntry(schemaManager, "cn=Alice Display Name,ou=people,dc=hadoop,dc=apache,dc=org");
        userEntry.add("uid", "alice");
        userEntry.add("memberOf", "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org");
        testContext.nextInterceptor.setEntries(List.of(userEntry));

        final EntryFilteringCursor result = testContext.interceptor.search(testContext.ctx);

        assertTrue(result.next());
        final Entry servedEntry = result.get();
        assertEquals("alice", usernameCapture.getValue());
        assertEquals("uid must be preserved when the client requested it",
                "alice", servedEntry.get("uid").getString());
    }

    @Test
    public void testRolesLookupRetainsUidWhenAllUserAttributesRequested() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);
        expect(mockRolesService.lookupRoles(anyString(), anyObject()))
                .andReturn(Arrays.asList("roleA"))
                .atLeastOnce();
        replay(mockRolesService);

        final TestContext testContext = createTestContext(false, mockRolesService);
        // A '*' (all user attributes) request already returns uid, so no augmentation/stripping.
        testContext.ctx.setAllUserAttributes(true);

        final Entry userEntry =
                new DefaultEntry(schemaManager, "cn=Alice Display Name,ou=people,dc=hadoop,dc=apache,dc=org");
        userEntry.add("uid", "alice");
        userEntry.add("memberOf", "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org");
        testContext.nextInterceptor.setEntries(List.of(userEntry));

        final EntryFilteringCursor result = testContext.interceptor.search(testContext.ctx);

        assertTrue(result.next());
        final Entry servedEntry = result.get();
        assertEquals("uid must be preserved when all user attributes are requested",
                "alice", servedEntry.get("uid").getString());
    }

    @Test
    public void testTranslateGroupEntryWithSingleRole() throws Exception {
        final Entry groupEntry = createGroupEntry("cn=engineering,ou=groups,dc=hadoop,dc=apache,dc=org");

        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);
        expect(mockRolesService.lookupRoles(null, Set.of("engineering")))
                .andReturn(List.of("viewer"));
        replay(mockRolesService);

        final List<Entry> translated =
                new LDAPRolesLookupInterceptor(mockRolesService).translateGroupEntry(groupEntry);

        assertEquals(1, translated.size());
        final Entry roleEntry = translated.get(0);
        assertEquals("cn=viewer,ou=groups,dc=hadoop,dc=apache,dc=org", roleEntry.getDn().getName());
        assertEquals("viewer", roleEntry.get("cn").getString());
    }

    @Test
    public void testTranslateGroupEntryWithMultipleRoles() throws Exception {
        final Entry groupEntry = createGroupEntry("cn=engineering,ou=groups,dc=hadoop,dc=apache,dc=org");

        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);
        expect(mockRolesService.lookupRoles(null, Set.of("engineering")))
                .andReturn(Arrays.asList("viewer", "editor"));
        replay(mockRolesService);

        final List<Entry> translated =
                new LDAPRolesLookupInterceptor(mockRolesService).translateGroupEntry(groupEntry);

        final Set<String> resultDns = new HashSet<>();
        for (final Entry roleEntry : translated) {
            resultDns.add(roleEntry.getDn().getName());
        }
        assertEquals(2, translated.size());
        assertTrue(resultDns.contains("cn=viewer,ou=groups,dc=hadoop,dc=apache,dc=org"));
        assertTrue(resultDns.contains("cn=editor,ou=groups,dc=hadoop,dc=apache,dc=org"));
    }

    @Test
    public void testTranslateGroupEntryWithNoRoleMapping() throws Exception {
        final Entry groupEntry = createGroupEntry("cn=unmapped,ou=groups,dc=hadoop,dc=apache,dc=org");

        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);
        expect(mockRolesService.lookupRoles(null, Set.of("unmapped")))
                .andReturn(Collections.emptyList());
        replay(mockRolesService);

        final List<Entry> translated =
                new LDAPRolesLookupInterceptor(mockRolesService).translateGroupEntry(groupEntry);

        assertTrue("Group with no role mapping should be dropped", translated.isEmpty());
    }

    @Test
    public void testSearchTranslatesUserAndGroupEntries() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);
        expect(mockRolesService.lookupRoles("alice", Set.of("group1")))
                .andReturn(List.of("roleA"));
        expect(mockRolesService.lookupRoles(null, Set.of("engineering")))
                .andReturn(List.of("viewer"));
        expect(mockRolesService.lookupRoles(null, Set.of("unmapped")))
                .andReturn(Collections.emptyList());
        replay(mockRolesService);

        final TestContext testContext = createTestContext(false, mockRolesService);

        final Entry userEntry = createUserEntry("alice", "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org");
        final Entry mappedGroupEntry = createGroupEntry("cn=engineering,ou=groups,dc=hadoop,dc=apache,dc=org");
        final Entry unmappedGroupEntry = createGroupEntry("cn=unmapped,ou=groups,dc=hadoop,dc=apache,dc=org");
        testContext.nextInterceptor.setEntries(List.of(userEntry, mappedGroupEntry, unmappedGroupEntry));

        final EntryFilteringCursor entries = testContext.interceptor.search(testContext.ctx);

        final List<Entry> results = new ArrayList<>();
        while (entries.next()) {
            results.add(entries.get());
        }

        assertEquals("Unmapped group should be dropped, leaving the user entry and one role entry",
                2, results.size());

        Entry resultUserEntry = null;
        Entry resultRoleEntry = null;
        for (final Entry entry : results) {
            if (entry.get("uid") != null) {
                resultUserEntry = entry;
            } else {
                resultRoleEntry = entry;
            }
        }

        assertMemberOf(resultUserEntry, "cn=roleA,ou=groups,dc=hadoop,dc=apache,dc=org");
        assertEquals("cn=viewer,ou=groups,dc=hadoop,dc=apache,dc=org", resultRoleEntry.getDn().getName());
        assertEquals("viewer", resultRoleEntry.get("cn").getString());
    }

    @Test
    public void testSearchDeduplicatesGroupsMappingToSameRole() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);
        expect(mockRolesService.lookupRoles(null, Set.of("engineering")))
                .andReturn(List.of("viewer"));
        expect(mockRolesService.lookupRoles(null, Set.of("support")))
                .andReturn(List.of("viewer"));
        replay(mockRolesService);

        final TestContext testContext = createTestContext(false, mockRolesService);

        final Entry engineeringGroup = createGroupEntry("cn=engineering,ou=groups,dc=hadoop,dc=apache,dc=org",
                "uid=alice,ou=people,dc=hadoop,dc=apache,dc=org",
                "uid=carol,ou=people,dc=hadoop,dc=apache,dc=org");
        final Entry supportGroup = createGroupEntry("cn=support,ou=groups,dc=hadoop,dc=apache,dc=org",
                "uid=alice,ou=people,dc=hadoop,dc=apache,dc=org",
                "uid=bob,ou=people,dc=hadoop,dc=apache,dc=org");
        testContext.nextInterceptor.setEntries(List.of(engineeringGroup, supportGroup));

        final EntryFilteringCursor entries = testContext.interceptor.search(testContext.ctx);

        final List<Entry> results = new ArrayList<>();
        while (entries.next()) {
            results.add(entries.get());
        }

        assertEquals("Groups mapping to the same role should be combined into a single entry",
                1, results.size());

        final Entry roleEntry = results.get(0);
        assertEquals("cn=viewer,ou=groups,dc=hadoop,dc=apache,dc=org", roleEntry.getDn().getName());
        final Attribute member = roleEntry.get("member");
        assertEquals("Duplicate member (alice) should only appear once", 3, member.size());
        assertTrue(member.contains("uid=alice,ou=people,dc=hadoop,dc=apache,dc=org"));
        assertTrue(member.contains("uid=bob,ou=people,dc=hadoop,dc=apache,dc=org"));
        assertTrue(member.contains("uid=carol,ou=people,dc=hadoop,dc=apache,dc=org"));
    }

    @Test
    public void testSearchDeduplicatesGroupsWhenOnlyOneHasMembers() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);
        expect(mockRolesService.lookupRoles(null, Set.of("engineering")))
                .andReturn(List.of("viewer"));
        expect(mockRolesService.lookupRoles(null, Set.of("support")))
                .andReturn(List.of("viewer"));
        replay(mockRolesService);

        final TestContext testContext = createTestContext(false, mockRolesService);

        // No members on the first group, so the merged entry should end up with only the
        // second group's members rather than failing on a null "member" attribute.
        final Entry engineeringGroup = createGroupEntry("cn=engineering,ou=groups,dc=hadoop,dc=apache,dc=org");
        final Entry supportGroup = createGroupEntry("cn=support,ou=groups,dc=hadoop,dc=apache,dc=org",
                "uid=bob,ou=people,dc=hadoop,dc=apache,dc=org");
        testContext.nextInterceptor.setEntries(List.of(engineeringGroup, supportGroup));

        final EntryFilteringCursor entries = testContext.interceptor.search(testContext.ctx);

        final List<Entry> results = new ArrayList<>();
        while (entries.next()) {
            results.add(entries.get());
        }

        assertEquals(1, results.size());
        final Attribute member = results.get(0).get("member");
        assertEquals(1, member.size());
        assertTrue(member.contains("uid=bob,ou=people,dc=hadoop,dc=apache,dc=org"));
    }

    @Test
    public void testSearchMergesUniqueMemberAttributeIntoDeduplicatedEntry() throws Exception {
        final LDAPRolesLookupService mockRolesService = EasyMock.createMock(LDAPRolesLookupService.class);
        expect(mockRolesService.lookupRoles(null, Set.of("engineering")))
                .andReturn(List.of("viewer"));
        expect(mockRolesService.lookupRoles(null, Set.of("support")))
                .andReturn(List.of("viewer"));
        replay(mockRolesService);

        final TestContext testContext = createTestContext(false, mockRolesService);

        final Entry engineeringGroup = createGroupEntry("cn=engineering,ou=groups,dc=hadoop,dc=apache,dc=org",
                "uid=alice,ou=people,dc=hadoop,dc=apache,dc=org");
        final Entry supportGroup = createUniqueMemberGroupEntry("cn=support,ou=groups,dc=hadoop,dc=apache,dc=org",
                "uid=bob,ou=people,dc=hadoop,dc=apache,dc=org");
        testContext.nextInterceptor.setEntries(List.of(engineeringGroup, supportGroup));

        final EntryFilteringCursor entries = testContext.interceptor.search(testContext.ctx);

        final List<Entry> results = new ArrayList<>();
        while (entries.next()) {
            results.add(entries.get());
        }

        assertEquals("Groups mapping to the same role should be combined into a single entry",
                1, results.size());

        final Attribute member = results.get(0).get("member");
        assertEquals("uniqueMember values should be merged into the destination's member attribute",
                2, member.size());
        assertTrue(member.contains("uid=alice,ou=people,dc=hadoop,dc=apache,dc=org"));
        assertTrue(member.contains("uid=bob,ou=people,dc=hadoop,dc=apache,dc=org"));
    }

    private TestContext createTestContext(boolean bypass, LDAPRolesLookupService rolesService) throws Exception {
        RolesLookupBypassControl control = new RolesLookupBypassControlImpl();
        control.setBypassRolesLookup(bypass);
        return createTestContext(control, rolesService);
    }

    private TestContext createTestContextWithoutBypassControl(LDAPRolesLookupService rolesService) throws Exception {
        return createTestContext(null, rolesService);
    }

    private TestContext createTestContext(RolesLookupBypassControl control, LDAPRolesLookupService rolesService) throws Exception {
        DirectoryService directoryService = new SimpleDirectoryService();
        directoryService.setShutdownHookEnabled(false);
        directoryService.setSchemaManager(schemaManager);

        LDAPRolesLookupInterceptor interceptor =
                new LDAPRolesLookupInterceptor(rolesService);
        interceptor.init(directoryService);
        directoryService.addLast(interceptor);

        ConfigurableSearchTestInterceptor nextInterceptor =
                new ConfigurableSearchTestInterceptor("NEXT");
        nextInterceptor.init(directoryService);
        directoryService.addLast(nextInterceptor);

        SearchOperationContext ctx =
                new SearchOperationContext(directoryService.getSession());
        ctx.setInterceptors(List.of("NEXT"));

        if (control != null) {
            ctx.addRequestControl(control);
        }

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

    private Entry createGroupEntry(final String dn, final String... members) throws Exception {
        final Entry entry = new DefaultEntry(schemaManager, dn);
        entry.add("objectClass", "groupOfNames");
        entry.add("cn", entry.getDn().getRdn().getValue());
        for (final String member : members) {
            entry.add("member", member);
        }
        return entry;
    }

    private Entry createUniqueMemberGroupEntry(final String dn, final String... uniqueMembers) throws Exception {
        final Entry entry = new DefaultEntry(schemaManager, dn);
        entry.add("objectClass", "groupOfUniqueNames");
        entry.add("cn", entry.getDn().getRdn().getValue());
        for (final String uniqueMember : uniqueMembers) {
            entry.add("uniqueMember", uniqueMember);
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
            ConfigurableSearchTestInterceptor nextInterceptor,
            SearchOperationContext ctx) {
    }
}

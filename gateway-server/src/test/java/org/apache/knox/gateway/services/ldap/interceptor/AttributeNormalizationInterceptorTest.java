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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.knox.gateway.security.ldap.SimpleDirectoryService;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AttributeNormalizationInterceptorTest {
    private static final String TEST_INTERCEPTOR = "TEST";
    private static final String NEXT_INTERCEPTOR = "NEXT";
    private static final String USER_DN = "uid=JSmith,ou=people,dc=example,dc=com";
    private static final String GROUP_DN = "cn=Engineering,ou=groups,dc=example,dc=com";

    private AttributeNormalizationInterceptor interceptor;

    private DirectoryService directoryService;
    private SchemaManager schemaManager;
    private ConfigurableSearchTestInterceptor nextSearchInterceptor;
    private ConfigurableLookupTestInterceptor nextLookupInterceptor;

    @Before
    public void setUp() throws Exception {
        directoryService = new SimpleDirectoryService();
        directoryService.setShutdownHookEnabled(false);
        schemaManager = SchemaManagerFactory.createSchemaManager();
        directoryService.setSchemaManager(schemaManager);

        interceptor = new AttributeNormalizationInterceptor(TEST_INTERCEPTOR);
        interceptor.init(directoryService);
        directoryService.addLast(interceptor);

        nextSearchInterceptor = new ConfigurableSearchTestInterceptor(NEXT_INTERCEPTOR);
        nextSearchInterceptor.init(directoryService);
        directoryService.addLast(nextSearchInterceptor);
    }

    @After
    public void tearDown() throws Exception {
        directoryService.shutdown();
    }

    private SearchOperationContext searchContext() throws Exception {
        SearchOperationContext ctx = new SearchOperationContext(directoryService.getSession());
        ctx.setInterceptors(List.of(NEXT_INTERCEPTOR));
        return ctx;
    }

    private Entry userEntry() throws Exception {
        Entry entry = new DefaultEntry(schemaManager, USER_DN);
        entry.add("objectClass", "inetOrgPerson");
        entry.add("uid", "JSmith");
        entry.add("cn", "John SMITH");
        entry.add("sn", "Smith");
        entry.add("mail", "John.Smith@Example.COM", "JS@Example.COM");
        entry.add("memberOf", "cn=Engineering,ou=groups,dc=example,dc=com");
        return entry;
    }

    // Attribute.contains() applies the schema's case-insensitive matching, so compare raw strings.
    private static Set<String> rawValues(Attribute attribute) throws Exception {
        Set<String> values = new HashSet<>();
        for (Value value : attribute) {
            values.add(value.getString());
        }
        return values;
    }

    @Test
    public void testEmptyCursor() throws Exception {
        nextSearchInterceptor.setEntries(List.of());

        try (EntryFilteringCursor results = interceptor.search(searchContext())) {
            assertFalse("Results should be empty", results.next());
        }
        assertTrue("Cursor must be closed", nextSearchInterceptor.getCursor().isClosed());
    }

    @Test
    public void testCaseInsensitiveValuesAreLowercased() throws Exception {
        nextSearchInterceptor.setEntries(List.of(userEntry()));

        try (EntryFilteringCursor results = interceptor.search(searchContext())) {
            assertTrue(results.next());
            Entry entry = results.get();
            assertEquals("jsmith", entry.get("uid").getString());
            assertEquals("john smith", entry.get("cn").getString());
            assertEquals("smith", entry.get("sn").getString());
            Attribute mail = entry.get("mail");
            assertEquals(2, mail.size());
            assertTrue(mail.contains("john.smith@example.com"));
            assertTrue(mail.contains("js@example.com"));
            assertFalse("No more entries expected", results.next());
        }
        assertTrue("Cursor must be closed", nextSearchInterceptor.getCursor().isClosed());
    }

    @Test
    public void testAttributeIdIsPreserved() throws Exception {
        nextSearchInterceptor.setEntries(List.of(userEntry()));

        try (EntryFilteringCursor results = interceptor.search(searchContext())) {
            assertTrue(results.next());
            assertEquals("uid", results.get().get("uid").getUpId());
            assertEquals("memberOf", results.get().get("memberOf").getUpId());
        }
    }

    @Test
    public void testDnValuedAttributesAreLowercased() throws Exception {
        Entry user = userEntry();
        user.put("memberOf", "cn=Engineering,ou=Groups,dc=Example,dc=com", "CN=Ops,OU=Groups,DC=example,DC=com");
        nextSearchInterceptor.setEntries(List.of(user));

        try (EntryFilteringCursor results = interceptor.search(searchContext())) {
            assertTrue(results.next());
            Attribute memberOf = results.get().get("memberOf");
            assertEquals(2, memberOf.size());
            assertEquals("memberOf", memberOf.getUpId());
            assertEquals(
                    Set.of("cn=engineering,ou=groups,dc=example,dc=com", "cn=ops,ou=groups,dc=example,dc=com"),
                    rawValues(memberOf));
        }
    }

    @Test
    public void testUniqueMemberIsLowercasedPreservingUid() throws Exception {
        Entry group = new DefaultEntry(schemaManager, GROUP_DN);
        group.add("objectClass", "groupOfUniqueNames");
        group.add("cn", "Engineering");
        group.add("uniqueMember", "UID=JSmith,OU=People,DC=Example,DC=com", "uid=Other,ou=People,dc=example,dc=com#'0101'B");
        nextSearchInterceptor.setEntries(List.of(group));

        try (EntryFilteringCursor results = interceptor.search(searchContext())) {
            assertTrue(results.next());
            Attribute uniqueMember = results.get().get("uniqueMember");
            assertEquals("uniqueMember", uniqueMember.getUpId());
            assertEquals(
                    Set.of("uid=jsmith,ou=people,dc=example,dc=com", "uid=other,ou=people,dc=example,dc=com#'0101'B"),
                    rawValues(uniqueMember));
        }
    }

    @Test
    public void testObjectClassIsLowercased() throws Exception {
        Entry user = userEntry();
        user.add("objectClass", "organizationalPerson");
        nextSearchInterceptor.setEntries(List.of(user));

        try (EntryFilteringCursor results = interceptor.search(searchContext())) {
            assertTrue(results.next());
            Attribute objectClass = results.get().get("objectClass");
            assertEquals("objectClass", objectClass.getUpId());
            assertEquals(Set.of("inetorgperson", "organizationalperson"), rawValues(objectClass));
        }
    }

    @Test
    public void testEntryDnIsLowercased() throws Exception {
        nextSearchInterceptor.setEntries(List.of(userEntry()));

        try (EntryFilteringCursor results = interceptor.search(searchContext())) {
            assertTrue(results.next());
            assertEquals(USER_DN.toLowerCase(Locale.ROOT), results.get().getDn().getName());
        }
    }

    @Test
    public void testAllEntriesAreNormalized() throws Exception {
        Entry group = new DefaultEntry(schemaManager, GROUP_DN);
        group.add("objectClass", "groupOfNames");
        group.add("cn", "Engineering");
        group.add("member", USER_DN);
        nextSearchInterceptor.setEntries(List.of(userEntry(), group));

        try (EntryFilteringCursor results = interceptor.search(searchContext())) {
            assertTrue(results.next());
            assertEquals("jsmith", results.get().get("uid").getString());
            assertTrue(results.next());
            Entry groupResult = results.get();
            assertEquals("engineering", groupResult.get("cn").getString());
            assertEquals(USER_DN.toLowerCase(Locale.ROOT), groupResult.get("member").getString());
            assertFalse("No more entries expected", results.next());
        }
    }

    @Test
    public void testNormalizeIsIdempotent() throws Exception {
        Entry entry = userEntry();
        interceptor.normalize(entry);
        interceptor.normalize(entry);

        assertEquals("jsmith", entry.get("uid").getString());
        assertEquals(1, entry.get("uid").size());
    }

    @Test
    public void testSearchDoesNotDrainCursor() throws Exception {
        Entry first = userEntry();
        Entry second = userEntry();
        nextSearchInterceptor.setEntries(List.of(first, second));

        try (EntryFilteringCursor results = interceptor.search(searchContext())) {
            assertEquals("Nothing is normalized before the cursor is read", "JSmith", first.get("uid").getString());
            assertFalse("Wrapped cursor must not be closed by search()", nextSearchInterceptor.getCursor().isClosed());

            assertTrue(results.next());
            assertEquals("jsmith", results.get().get("uid").getString());
            assertEquals("Entries not yet read stay untouched", "JSmith", second.get("uid").getString());

            assertTrue(results.next());
            assertEquals("jsmith", results.get().get("uid").getString());
        }
        assertTrue("Closing the wrapper must close the wrapped cursor", nextSearchInterceptor.getCursor().isClosed());
    }

    @Test
    public void testRepeatedGetReturnsNormalizedEntry() throws Exception {
        nextSearchInterceptor.setEntries(List.of(userEntry()));

        try (EntryFilteringCursor results = interceptor.search(searchContext())) {
            assertTrue(results.next());
            assertEquals("jsmith", results.get().get("uid").getString());
            assertEquals("jsmith", results.get().get("uid").getString());
            assertEquals(1, results.get().get("uid").size());
        }
    }

    @Test
    public void testPreviousIsNormalized() throws Exception {
        nextSearchInterceptor.setEntries(List.of(userEntry(), userEntry()));

        try (EntryFilteringCursor results = interceptor.search(searchContext())) {
            assertTrue(results.last());
            assertEquals("jsmith", results.get().get("uid").getString());
            assertTrue(results.previous());
            assertEquals("jsmith", results.get().get("uid").getString());
        }
    }

    @Test
    public void testNormalizingFilterIsAddedToReturnedCursor() throws Exception {
        nextSearchInterceptor.setEntries(List.of(userEntry()));

        try (EntryFilteringCursor results = interceptor.search(searchContext())) {
            assertSame("The inner cursor is returned, not copied", nextSearchInterceptor.getCursor(), results);
            assertEquals(1, results.getEntryFilters().size());
        }
    }

    @Test
    public void testLookupIsNormalized() throws Exception {
        nextLookupInterceptor = new ConfigurableLookupTestInterceptor(NEXT_INTERCEPTOR + "_LOOKUP");
        nextLookupInterceptor.init(directoryService);
        directoryService.addLast(nextLookupInterceptor);
        nextLookupInterceptor.setEntry(userEntry());

        LookupOperationContext ctx = new LookupOperationContext(directoryService.getSession(), new Dn(schemaManager, USER_DN));
        ctx.setInterceptors(List.of(NEXT_INTERCEPTOR + "_LOOKUP"));

        Entry entry = interceptor.lookup(ctx);
        assertEquals("jsmith", entry.get("uid").getString());
        assertEquals("cn=engineering,ou=groups,dc=example,dc=com", entry.get("memberOf").getString());
        assertEquals(USER_DN.toLowerCase(Locale.ROOT), entry.getDn().getName());
    }

    @Test
    public void testLookupOfMissingEntryReturnsNull() throws Exception {
        nextLookupInterceptor = new ConfigurableLookupTestInterceptor(NEXT_INTERCEPTOR + "_LOOKUP");
        nextLookupInterceptor.init(directoryService);
        directoryService.addLast(nextLookupInterceptor);
        nextLookupInterceptor.setEntry(null);

        LookupOperationContext ctx = new LookupOperationContext(directoryService.getSession(), new Dn(schemaManager, USER_DN));
        ctx.setInterceptors(List.of(NEXT_INTERCEPTOR + "_LOOKUP"));

        assertNull(interceptor.lookup(ctx));
    }
}

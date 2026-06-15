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
import static org.junit.Assert.assertTrue;

import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.CoreSession;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.knox.gateway.security.ldap.SimpleDirectoryService;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Unit tests for DuplicateUserFilteringInterceptor.
 */
public class DuplicateUserFilteringInterceptorTest {

    private static final String TEST_INTERCEPTOR = "TEST";
    private static final String NEXT_INTERCEPTOR = "NEXT";

    private DuplicateUserFilteringInterceptor interceptor;

    private DirectoryService directoryService;
    private SchemaManager schemaManager;
    private ConfigurableEntriesTestInterceptor nextInterceptor;
    private SearchOperationContext ctx;
    private CoreSession session;

    @Before
    public void setUp() throws Exception {
        directoryService = new SimpleDirectoryService();
        directoryService.setShutdownHookEnabled(false);
        schemaManager = SchemaManagerFactory.createSchemaManager();
        directoryService.setSchemaManager(schemaManager);

        interceptor = new DuplicateUserFilteringInterceptor(TEST_INTERCEPTOR);
        interceptor.init(directoryService);
        directoryService.addLast(interceptor);

        nextInterceptor = new ConfigurableEntriesTestInterceptor(NEXT_INTERCEPTOR);
        nextInterceptor.init(directoryService);
        directoryService.addLast(nextInterceptor);

        session = directoryService.getSession();

        ctx = new SearchOperationContext(session);
        ctx.setInterceptors(List.of(TEST_INTERCEPTOR, NEXT_INTERCEPTOR));

    }

    @After
    public void tearDown() throws Exception {
        directoryService.shutdown();
    }

    @Test
    public void testEmptyCursor() throws Exception {
        nextInterceptor.setEntries(List.of());

        try (EntryFilteringCursor results = interceptor.search(ctx)) {
            assertFalse("Results should be empty", results.next());
        }

        EntryFilteringCursor nextInterceptorCursor = nextInterceptor.getCursor();
        assertTrue("Cursor must be closed", nextInterceptorCursor.isClosed());
    }

    @Test
    public void testNoDuplicateEntries() throws Exception {
        Entry entry1 = new DefaultEntry(schemaManager);
        entry1.add("uid", "user1");
        Entry entry2 = new DefaultEntry(schemaManager);
        entry2.add("uid", "user2");
        nextInterceptor.setEntries(List.of(entry1, entry2));

        try (EntryFilteringCursor results = interceptor.search(ctx)) {
            assertNextEntryUid(results, "user1");
            assertNextEntryUid(results, "user2");
            assertFalse("No more entries expected", results.next());
        }

        EntryFilteringCursor nextInterceptorCursor = nextInterceptor.getCursor();
        assertTrue("Cursor must be closed", nextInterceptorCursor.isClosed());
    }

    @Test
    public void testDuplicateEntries() throws Exception {
        Entry entry1 = new DefaultEntry(schemaManager);
        entry1.add("uid", "user1");
        Entry entry2 = new DefaultEntry(schemaManager);
        entry2.add("uid", "user1");
        nextInterceptor.setEntries(List.of(entry1, entry2));

        try (EntryFilteringCursor results = interceptor.search(ctx)) {
            assertNextEntryUid(results, "user1");
            assertFalse("No more entries expected", results.next());
        }

        EntryFilteringCursor nextInterceptorCursor = nextInterceptor.getCursor();
        assertTrue("Cursor must be closed", nextInterceptorCursor.isClosed());
    }

    private void assertNextEntryUid(EntryFilteringCursor cursor, String uid) throws Exception {
        assertTrue("Cursor should have another entry", cursor.next());
        Entry entry = cursor.get();
        Attribute uidAttr = entry.get("uid");
        assertEquals("Attribute should have only one value", 1, uidAttr.size());
        Value value = uidAttr.get();
        assertEquals("Uid should match " + uid, uid, value.getString());
    }
}
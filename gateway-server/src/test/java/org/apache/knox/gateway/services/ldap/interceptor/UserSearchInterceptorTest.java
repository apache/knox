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

import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.knox.gateway.security.ldap.SimpleDirectoryService;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.apache.knox.gateway.services.ldap.backend.LdapBackend;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link UserSearchInterceptor}, focused on when the interceptor forwards a
 * search to its backend.
 */
public class UserSearchInterceptorTest {

    private static final String TEST_INTERCEPTOR = "TEST";
    private static final String NEXT_INTERCEPTOR = "NEXT";

    private static final String PROXY_BASE_DN = "dc=proxy,dc=com";
    private static final String REMOTE_BASE_DN = "dc=example,dc=com";

    private DirectoryService directoryService;
    private SchemaManager schemaManager;
    private ConfigurableEntriesTestInterceptor nextInterceptor;
    private RecordingBackend backend;
    private UserSearchInterceptor interceptor;

    @Before
    public void setUp() throws Exception {
        directoryService = new SimpleDirectoryService();
        directoryService.setShutdownHookEnabled(false);
        schemaManager = SchemaManagerFactory.createSchemaManager();
        directoryService.setSchemaManager(schemaManager);

        // Proxy namespace (dc=proxy,dc=com) intentionally differs from the remote namespace
        // (dc=example,dc=com), which is the configuration that exposed the original bug.
        backend = new RecordingBackend(PROXY_BASE_DN, REMOTE_BASE_DN);

        interceptor = new UserSearchInterceptor(TEST_INTERCEPTOR, backend);
        interceptor.init(directoryService);
        directoryService.addLast(interceptor);

        nextInterceptor = new ConfigurableEntriesTestInterceptor(NEXT_INTERCEPTOR);
        nextInterceptor.init(directoryService);
        directoryService.addLast(nextInterceptor);
        // The local partition never resolves the proxied users, mirroring production.
        nextInterceptor.setEntries(List.of());
    }

    @After
    public void tearDown() throws Exception {
        directoryService.shutdown();
    }

    private SearchOperationContext newSearchContext(String baseDn) throws Exception {
        SearchOperationContext ctx = new SearchOperationContext(directoryService.getSession());
        // The interceptor list holds only the interceptors that next() should traverse
        // downstream of the one under test. Including the interceptor under test here would
        // make its next() call re-enter itself (currentInterceptor starts at index 0).
        ctx.setInterceptors(new ArrayList<>(List.of(NEXT_INTERCEPTOR)));
        ctx.setDn(new Dn(schemaManager, baseDn));
        ctx.setScope(SearchScope.SUBTREE);
        return ctx;
    }

    /**
     * Regression test for the case where the proxy base DN differs from the remote base DN.
     * A search issued in the proxy namespace must still be forwarded to the backend.
     */
    @Test
    public void testForwardsProxyNamespaceSearchWhenBaseDnsDiffer() throws Exception {
        Entry user = new DefaultEntry(schemaManager);
        user.add("uid", "admin");
        backend.setSearchResult(List.of(user));

        List<String> uids = new ArrayList<>();
        try (EntryFilteringCursor results = interceptor.search(newSearchContext(PROXY_BASE_DN))) {
            while (results.next()) {
                uids.add(results.get().get("uid").getString());
            }
        }

        assertEquals("The backend entry should be returned exactly once", List.of("admin"), uids);
        assertEquals("Backend should have been queried with the proxy-namespace base",
                PROXY_BASE_DN, backend.getLastSearchBase());
    }

    /**
     * The embedded server also registers the remote base DN as a partition, so a search in the
     * remote namespace must be forwarded as well.
     */
    @Test
    public void testForwardsRemoteNamespaceSearch() throws Exception {
        backend.setSearchResult(List.of());

        try (EntryFilteringCursor results = interceptor.search(newSearchContext(REMOTE_BASE_DN))) {
            assertFalse(results.next());
        }

        assertEquals(REMOTE_BASE_DN, backend.getLastSearchBase());
    }

    /**
     * A sub-tree search below the proxy namespace (e.g. ou=people) is forwarded.
     */
    @Test
    public void testForwardsSearchUnderProxyBaseDn() throws Exception {
        final String subtree = "ou=people," + PROXY_BASE_DN;
        backend.setSearchResult(List.of());

        try (EntryFilteringCursor results = interceptor.search(newSearchContext(subtree))) {
            assertFalse(results.next());
        }

        assertEquals(subtree, backend.getLastSearchBase());
    }

    /**
     * Operational / system searches (schema, config, ...) are outside both namespaces and must
     * never be forwarded to the backend.
     */
    @Test
    public void testDoesNotForwardOperationalSearch() throws Exception {
        try (EntryFilteringCursor results = interceptor.search(newSearchContext("ou=schema"))) {
            assertFalse(results.next());
        }

        assertNull("Backend must not be queried for operational searches", backend.getLastSearchBase());
    }

    /**
     * A minimal backend that records the search base it was invoked with and returns a
     * configurable result set.
     */
    private static final class RecordingBackend implements LdapBackend {
        private final String proxyBaseDn;
        private final String remoteBaseDn;
        private List<Entry> searchResult = new ArrayList<>();
        private String lastSearchBase;

        RecordingBackend(String proxyBaseDn, String remoteBaseDn) {
            this.proxyBaseDn = proxyBaseDn;
            this.remoteBaseDn = remoteBaseDn;
        }

        void setSearchResult(List<Entry> entries) {
            this.searchResult = entries;
        }

        String getLastSearchBase() {
            return lastSearchBase;
        }

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        public String getType() {
            return "recording";
        }

        @Override
        public String getBaseDn() {
            return remoteBaseDn;
        }

        @Override
        public String getProxyBaseDn() {
            return proxyBaseDn;
        }

        @Override
        public Entry getUser(String username, SchemaManager schemaManager) {
            return null;
        }

        @Override
        public List<String> getUserGroups(String username, SchemaManager schemaManager) {
            return List.of();
        }

        @Override
        public List<Entry> searchUsers(String filter, SchemaManager schemaManager) {
            return List.of();
        }

        @Override
        public List<Entry> search(String searchBase, SearchScope searchScope, String filter, SchemaManager schemaManager) {
            this.lastSearchBase = searchBase;
            return searchResult;
        }

        @Override
        public boolean authenticate(Dn userDn, String password) {
            return false;
        }
    }
}

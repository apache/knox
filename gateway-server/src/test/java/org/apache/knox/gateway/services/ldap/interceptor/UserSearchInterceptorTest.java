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
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapOperationException;
import org.apache.directory.api.ldap.model.filter.FilterParser;
import org.apache.directory.api.ldap.model.message.ResultCodeEnum;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.api.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.knox.gateway.security.ldap.SimpleDirectoryService;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.apache.knox.gateway.services.ldap.backend.LdapBackend;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UserSearchInterceptorTest {
    private static final String TEST_INTERCEPTOR = "TEST";
    private static final String NEXT_INTERCEPTOR = "NEXT";

    private UserSearchInterceptor interceptor;
    private LdapBackend mockLdapBackend;

    private DirectoryService directoryService;
    private SchemaManager schemaManager;
    private ConfigurableBindTestInterceptor nextBindInterceptor;
    private ConfigurableLookupTestInterceptor nextLookupInterceptor;
    private ConfigurableSearchTestInterceptor nextSearchInterceptor;

    @Before
    public void setUp() throws Exception {
        mockLdapBackend = mock(LdapBackend.class);

        directoryService = new SimpleDirectoryService();
        directoryService.setShutdownHookEnabled(false);
        schemaManager = SchemaManagerFactory.createSchemaManager();
        directoryService.setSchemaManager(schemaManager);

        interceptor = new UserSearchInterceptor(TEST_INTERCEPTOR, mockLdapBackend);
        interceptor.init(directoryService);
        directoryService.addLast(interceptor);
    }

    @After
    public void tearDown() throws Exception {
        directoryService.shutdown();
    }

    private BindOperationContext setupBindContext() throws Exception {
        nextBindInterceptor = new ConfigurableBindTestInterceptor(NEXT_INTERCEPTOR);
        nextBindInterceptor.init(directoryService);
        directoryService.addLast(nextBindInterceptor);

        BindOperationContext bindCtx = new BindOperationContext(directoryService.getSession());
        bindCtx.setInterceptors(List.of(NEXT_INTERCEPTOR));
        return bindCtx;
    }

    @Test
    public void testBind() throws Exception {
        Dn bindDn = new Dn(schemaManager, "uid=admin,ou=people,dc=proxy,dc=com");
        String bindPassword = "password";
        expect(mockLdapBackend.authenticate(bindDn, bindPassword)).andReturn(true);
        replay(mockLdapBackend);
        BindOperationContext ctx = setupBindContext();
        ctx.setDn(bindDn);
        ctx.setCredentials(bindPassword.getBytes(StandardCharsets.UTF_8));

        interceptor.bind(ctx);

        verify(mockLdapBackend);
        assertEquals(0, nextBindInterceptor.getBindCount());
    }

    @Test
    public void testBindFallsBackOnSystem() throws Exception {
        Dn bindDn = new Dn(schemaManager, "uid=admin,ou=people,dc=proxy,dc=com");
        String bindPassword = "password";
        expect(mockLdapBackend.authenticate(bindDn, bindPassword)).andReturn(false);
        replay(mockLdapBackend);
        BindOperationContext ctx = setupBindContext();
        ctx.setDn(bindDn);
        ctx.setCredentials(bindPassword.getBytes(StandardCharsets.UTF_8));

        interceptor.bind(ctx);

        verify(mockLdapBackend);
        assertEquals(1, nextBindInterceptor.getBindCount());
    }

    @Test
    public void testBindAnonymous() throws Exception {
        // nothing to replay because interceptor should not call ldap backend for anonymous users
        replay(mockLdapBackend);
        BindOperationContext ctx = setupBindContext();

        interceptor.bind(ctx);

        verify(mockLdapBackend);
        assertEquals(1, nextBindInterceptor.getBindCount());
    }

    @Test(expected = LdapException.class)
    public void testBindAnonymousNotAllowed() throws Exception {
        BindOperationContext ctx = setupBindContext();
        LdapException bindException = new LdapException("Anonymous bind not allowed");
        nextBindInterceptor.setBindException(bindException);

        interceptor.bind(ctx);
    }

    @Test
    public void testBindSystemUser() throws Exception {
        Dn bindDn = new Dn(schemaManager, "uid=admin,ou=system");
        String bindPassword = "password";
        // nothing to replay because interceptor should not call ldap backend for system users
        replay(mockLdapBackend);
        BindOperationContext ctx = setupBindContext();
        ctx.setDn(bindDn);
        ctx.setCredentials(bindPassword.getBytes(StandardCharsets.UTF_8));

        interceptor.bind(ctx);

        verify(mockLdapBackend);
        assertEquals(1, nextBindInterceptor.getBindCount());
    }

    @Test(expected = LdapException.class)
    public void testBindSystemUserNotAllowed() throws Exception {
        Dn bindDn = new Dn("uid=admin,ou=system");
        String bindPassword = "password";
        BindOperationContext ctx = setupBindContext();
        LdapException bindException = new LdapException("bind failed");
        nextBindInterceptor.setBindException(bindException);
        ctx.setDn(bindDn);
        ctx.setCredentials(bindPassword.getBytes(StandardCharsets.UTF_8));

        interceptor.bind(ctx);
    }

    private LookupOperationContext setupLookupContext() throws Exception {
        nextLookupInterceptor = new ConfigurableLookupTestInterceptor(NEXT_INTERCEPTOR);
        nextLookupInterceptor.init(directoryService);
        directoryService.addLast(nextLookupInterceptor);

        LookupOperationContext lookupCtx = new LookupOperationContext(directoryService.getSession());
        lookupCtx.setInterceptors(List.of(NEXT_INTERCEPTOR));
        return lookupCtx;
    }

    @Test
    public void testLookup() throws Exception {
        Dn lookupDn = new Dn(schemaManager, "uid=admin,ou=people,dc=proxy,dc=com");
        Entry entry = new DefaultEntry(lookupDn);
        expect(mockLdapBackend.getUser("admin", schemaManager)).andReturn(entry);
        replay(mockLdapBackend);
        LookupOperationContext ctx = setupLookupContext();
        ctx.setDn(lookupDn);

        Entry result = interceptor.lookup(ctx);

        verify(mockLdapBackend);
        assertEquals(entry, result);
    }

    @Test
    public void testLookupNotFound() throws Exception {
        Dn lookupDn = new Dn(schemaManager, "uid=admin,ou=people,dc=proxy,dc=com");
        expect(mockLdapBackend.getUser("admin", schemaManager)).andReturn(null);
        replay(mockLdapBackend);
        LookupOperationContext ctx = setupLookupContext();
        ctx.setDn(lookupDn);

        Entry result = interceptor.lookup(ctx);

        verify(mockLdapBackend);
        assertNull(result);
    }

    @Test
    public void testLookupNextInterceptorFound() throws Exception {
        Dn lookupDn = new Dn(schemaManager, "uid=admin,ou=people,dc=proxy,dc=com");
        Entry entry = new DefaultEntry(lookupDn);
        expect(mockLdapBackend.getUser(anyString(), anyObject()))
                .andThrow(new AssertionError("getUser should not be called if entry returned by next interceptor"))
                .anyTimes();
        replay(mockLdapBackend);
        LookupOperationContext ctx = setupLookupContext();
        ctx.setDn(lookupDn);
        nextLookupInterceptor.setEntry(entry);

        Entry result = interceptor.lookup(ctx);

        verify(mockLdapBackend);
        assertEquals(entry, result);
    }

    @Test
    public void testLookupNextInterceptorThrows() throws Exception {
        Dn lookupDn = new Dn(schemaManager, "uid=admin,ou=people,dc=proxy,dc=com");
        expect(mockLdapBackend.getUser(anyString(), anyObject()))
                .andThrow(new AssertionError("getUser should not be called if entry returned by next interceptor"))
                .anyTimes();
        replay(mockLdapBackend);
        LookupOperationContext ctx = setupLookupContext();
        ctx.setDn(lookupDn);
        LdapException expectedException = new LdapException("lookup failed");
        nextLookupInterceptor.setLdapException(expectedException);

        LdapException resultException = assertThrows(LdapException.class,() -> interceptor.lookup(ctx));

        verify(mockLdapBackend);
        assertEquals(expectedException, resultException);
    }

    @Test
    public void testLookupBackendThrows() throws Exception {
        Dn lookupDn = new Dn(schemaManager, "uid=admin,ou=people,dc=proxy,dc=com");
        LdapException expectedException = new LdapException("lookup failed");
        expect(mockLdapBackend.getUser(anyString(), anyObject())).andThrow(expectedException);
        replay(mockLdapBackend);
        LookupOperationContext ctx = setupLookupContext();
        ctx.setDn(lookupDn);

        LdapOperationException resultException = assertThrows(LdapOperationException.class,() -> interceptor.lookup(ctx));

        verify(mockLdapBackend);
        assertEquals(ResultCodeEnum.OTHER, resultException.getResultCode());
        assertEquals("Lookup request to backend TEST failed.", resultException.getMessage());
        assertEquals(expectedException, resultException.getCause());
    }

    private SearchOperationContext setupSearchContext() throws Exception {
        nextSearchInterceptor = new ConfigurableSearchTestInterceptor(NEXT_INTERCEPTOR);
        nextSearchInterceptor.init(directoryService);
        directoryService.addLast(nextSearchInterceptor);

        SearchOperationContext searchCtx = new SearchOperationContext(directoryService.getSession());
        searchCtx.setInterceptors(List.of(NEXT_INTERCEPTOR));
        return searchCtx;
    }

    @Test
    public void testSearch() throws Exception {
        Dn baseDn = new Dn(schemaManager, "ou=people,dc=proxy,dc=com");
        String filter = "(objectClass=*)";

        Set<String> expectedDns = new HashSet<>();
        List<Entry> nextEntries = createEntryList(baseDn, 5);
        for (Entry entry : nextEntries) {
            expectedDns.add(entry.getDn().toString());
        }
        List<Entry> backendEntries = createEntryList(baseDn, 5);
        for (Entry entry : backendEntries) {
            expectedDns.add(entry.getDn().toString());
        }

        expect(mockLdapBackend.isSupportedSearchBase(baseDn.toString())).andReturn(true);
        expect(mockLdapBackend.search(baseDn.toString(), SearchScope.SUBTREE, filter, schemaManager)).andReturn(backendEntries);
        replay(mockLdapBackend);
        SearchOperationContext ctx = setupSearchContext();
        ctx.setDn(baseDn);
        ctx.setScope(SearchScope.SUBTREE);
        ctx.setFilter(FilterParser.parse(filter));
        nextSearchInterceptor.setEntries(nextEntries);

        Set<String> resultDns = new HashSet<>();
        try (EntryFilteringCursor result = interceptor.search(ctx)) {
            for (Entry entry : result) {
                resultDns.add(entry.getDn().toString());
            }

        }
        verify(mockLdapBackend);
        assertEquals(expectedDns, resultDns);
        EntryFilteringCursor nextInterceptorCursor = nextSearchInterceptor.getCursor();
        assertTrue("Next cursor must be closed", nextInterceptorCursor.isClosed());
    }

    @Test
    public void testSearchBaseDnDoesntMatchBackend() throws Exception {
        Dn baseDn = new Dn(schemaManager, "ou=people,dc=proxy,dc=com");
        String filter = "(objectClass=*)";

        Set<String> expectedDns = new HashSet<>();
        List<Entry> nextEntries = createEntryList(baseDn, 5);
        for (Entry entry : nextEntries) {
            expectedDns.add(entry.getDn().toString());
        }

        expect(mockLdapBackend.isSupportedSearchBase(baseDn.toString())).andReturn(false);
        expect(mockLdapBackend.search(anyString(), anyObject(SearchScope.class), anyString(), anyObject(SchemaManager.class)))
                .andThrow(new AssertionError("search should not be called if base dn does not match backend"))
                .anyTimes();
        replay(mockLdapBackend);
        SearchOperationContext ctx = setupSearchContext();
        ctx.setDn(baseDn);
        ctx.setScope(SearchScope.SUBTREE);
        ctx.setFilter(FilterParser.parse(filter));
        nextSearchInterceptor.setEntries(nextEntries);

        Set<String> resultDns = new HashSet<>();
        try (EntryFilteringCursor result = interceptor.search(ctx)) {
            for (Entry entry : result) {
                resultDns.add(entry.getDn().toString());
            }

        }
        verify(mockLdapBackend);
        assertEquals(expectedDns, resultDns);
        EntryFilteringCursor nextInterceptorCursor = nextSearchInterceptor.getCursor();
        assertTrue("Next cursor must be closed", nextInterceptorCursor.isClosed());
    }

    @Test
    public void testSearchNoNextEntries() throws Exception {
        Dn baseDn = new Dn(schemaManager, "ou=people,dc=proxy,dc=com");
        String filter = "(objectClass=*)";

        Set<String> expectedDns = new HashSet<>();
        List<Entry> backendEntries = createEntryList(baseDn, 5);
        for (Entry entry : backendEntries) {
            expectedDns.add(entry.getDn().toString());
        }

        expect(mockLdapBackend.isSupportedSearchBase(baseDn.toString())).andReturn(true);
        expect(mockLdapBackend.search(baseDn.toString(), SearchScope.SUBTREE, filter, schemaManager)).andReturn(backendEntries);
        replay(mockLdapBackend);
        SearchOperationContext ctx = setupSearchContext();
        ctx.setDn(baseDn);
        ctx.setScope(SearchScope.SUBTREE);
        ctx.setFilter(FilterParser.parse(filter));
        nextSearchInterceptor.setEntries(List.of());

        Set<String> resultDns = new HashSet<>();
        try (EntryFilteringCursor result = interceptor.search(ctx)) {
            for (Entry entry : result) {
                resultDns.add(entry.getDn().toString());
            }

        }
        verify(mockLdapBackend);
        assertEquals(expectedDns, resultDns);
        EntryFilteringCursor nextInterceptorCursor = nextSearchInterceptor.getCursor();
        assertTrue("Next cursor must be closed", nextInterceptorCursor.isClosed());
    }

    @Test
    public void testSearchNextInterceptorThrows() throws Exception {
        Dn baseDn = new Dn(schemaManager, "ou=people,dc=proxy,dc=com");
        String filter = "(objectClass=*)";

        LdapException expectedException = new LdapException("Expected exception");
        SearchOperationContext ctx = setupSearchContext();
        ctx.setDn(baseDn);
        ctx.setScope(SearchScope.SUBTREE);
        ctx.setFilter(FilterParser.parse(filter));
        nextSearchInterceptor.setLdapException(expectedException);

        LdapException resultException = assertThrows(LdapException.class, () -> interceptor.search(ctx));
        assertEquals(expectedException, resultException);
    }

    @Test
    public void testSearchBackendThrows() throws Exception {
        Dn baseDn = new Dn(schemaManager, "ou=people,dc=proxy,dc=com");
        String filter = "(objectClass=*)";

        List<Entry> nextEntries = createEntryList(baseDn, 5);
        LdapException expectedException = new LdapException("Expected exception");
        expect(mockLdapBackend.isSupportedSearchBase(baseDn.toString())).andReturn(true);
        expect(mockLdapBackend.search(baseDn.toString(), SearchScope.SUBTREE, filter, schemaManager)).andThrow(expectedException);
        replay(mockLdapBackend);
        SearchOperationContext ctx = setupSearchContext();
        ctx.setDn(baseDn);
        ctx.setScope(SearchScope.SUBTREE);
        ctx.setFilter(FilterParser.parse(filter));
        nextSearchInterceptor.setEntries(nextEntries);

        LdapOperationException resultException = assertThrows(LdapOperationException.class, () -> interceptor.search(ctx));
        assertEquals(ResultCodeEnum.OTHER, resultException.getResultCode());
        assertEquals("Search request to backend TEST failed.", resultException.getMessage());
        assertEquals(expectedException, resultException.getCause());
        EntryFilteringCursor nextInterceptorCursor = nextSearchInterceptor.getCursor();
        assertTrue("Next cursor must be closed", nextInterceptorCursor.isClosed());
    }

    private List<Entry> createEntryList(Dn baseDn, int numEntries) throws Exception {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < numEntries; i++) {
            entries.add(createEntry(baseDn));
        }
        return entries;
    }

    private Entry createEntry(Dn baseDn) throws Exception {
        Dn userDn = baseDn.add("uid=" + UUID.randomUUID().toString());
        return new DefaultEntry(userDn);
    }
}

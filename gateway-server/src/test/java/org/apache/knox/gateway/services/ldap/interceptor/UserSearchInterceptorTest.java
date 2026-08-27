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

import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.interceptor.context.BindOperationContext;
import org.apache.knox.gateway.security.ldap.SimpleDirectoryService;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.apache.knox.gateway.services.ldap.backend.LdapBackend;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

public class UserSearchInterceptorTest {
    private static final String TEST_INTERCEPTOR = "TEST";
    private static final String NEXT_INTERCEPTOR = "NEXT";

    private UserSearchInterceptor interceptor;
    private LdapBackend mockLdapBackend;

    private DirectoryService directoryService;
    private SchemaManager schemaManager;
    private ConfigurableBindTestInterceptor nextBindInterceptor;

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
}

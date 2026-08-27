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
package org.apache.knox.gateway.services.ldap.authn;

import org.apache.directory.api.ldap.model.exception.LdapAuthenticationException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.LdapPrincipal;
import org.apache.directory.server.core.api.interceptor.context.BindOperationContext;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class InMemoryBindAuthenticatorTest {
    private static final String BIND_DN = "uid=bind-user,dc=test,dc=com";
    private static final String BIND_PASSWORD = "test-password";

    Dn bindDn;
    SchemaManager schemaManager;
    DirectoryService directoryService;
    InMemoryBindAuthenticator authenticator;

    @Before
    public void setUp() throws Exception {
        directoryService = new DefaultDirectoryService();
        schemaManager = SchemaManagerFactory.createSchemaManager();
        directoryService.setSchemaManager(schemaManager);

        bindDn = new Dn(schemaManager, BIND_DN);
        authenticator = new InMemoryBindAuthenticator(bindDn,  BIND_PASSWORD);
        authenticator.init(directoryService);
    }

    @Test
    public void testAuthenticate() throws Exception {
        BindOperationContext context = mock(BindOperationContext.class);
        expect(context.getDn()).andReturn(bindDn).once();
        expect(context.getCredentials()).andReturn(BIND_PASSWORD.getBytes(StandardCharsets.UTF_8)).once();
        replay(context);

        LdapPrincipal principal = authenticator.authenticate(context);
        assertNotNull(principal);
        assertEquals(bindDn, principal.getDn());
    }

    @Test
    public void testAuthenticateBadDn() throws Exception {
        Dn badDn = new Dn(schemaManager, "uid=bad-user,dc=test,dc=com");
        BindOperationContext context = mock(BindOperationContext.class);
        expect(context.getDn()).andReturn(badDn).anyTimes();
        replay(context);

        LdapPrincipal principal = authenticator.authenticate(context);
        assertNull(principal);
    }

    @Test(expected = LdapAuthenticationException.class)
    public void testAuthenticateBadPassword() throws Exception {
        BindOperationContext context = mock(BindOperationContext.class);
        expect(context.getDn()).andReturn(bindDn).once();
        expect(context.getCredentials()).andReturn("bad-password".getBytes(StandardCharsets.UTF_8)).once();
        replay(context);

        authenticator.authenticate(context);
    }
}
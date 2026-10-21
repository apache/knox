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

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ldap.roles.file.FileBasedLdapRolesLookup;
import org.apache.knox.gateway.services.ldap.roles.rest.RestApiLdapRolesLookup;
import org.easymock.EasyMock;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LdapRolesLookupFactoryTest {

    private static GatewayConfig config(String strategy, String filePath, String endpoint) {
        GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(config.getLdapRolesLookupStrategy()).andReturn(strategy).anyTimes();
        EasyMock.expect(config.getLdapRolesLookupFilePath()).andReturn(filePath).anyTimes();
        EasyMock.expect(config.getLdapRolesLookupRestApiEndpoint()).andReturn(endpoint).anyTimes();
        EasyMock.replay(config);
        return config;
    }

    @Test
    public void testNoStrategyReturnsNull() throws Exception {
        assertNull(LdapRolesLookupFactory.create(config(null, null, null)));
    }

    @Test
    public void testFileStrategy() throws Exception {
        assertTrue(LdapRolesLookupFactory.create(config("file", "/tmp/roles.json", null)) instanceof FileBasedLdapRolesLookup);
    }

    @Test(expected = RoleException.class)
    public void testFileStrategyWithoutPathFails() throws Exception {
        LdapRolesLookupFactory.create(config("file", null, null));
    }

    @Test
    public void testRestStrategy() throws Exception {
        assertTrue(LdapRolesLookupFactory.create(config("REST", null, "http://localhost/lookup")) instanceof RestApiLdapRolesLookup);
    }

    @Test(expected = RoleException.class)
    public void testRestStrategyWithoutEndpointFails() throws Exception {
        LdapRolesLookupFactory.create(config("rest", null, ""));
    }

    @Test(expected = RoleException.class)
    public void testUnknownStrategyFails() throws Exception {
        LdapRolesLookupFactory.create(config("bogus", null, null));
    }
}

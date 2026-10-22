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
import org.apache.knox.gateway.services.ldap.roles.file.FileBasedLdapRolesReverseLookup;
import org.apache.knox.gateway.services.ldap.roles.rest.RestApiLdapRolesReverseLookup;
import org.easymock.EasyMock;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LdapRolesReverseLookupFactoryTest {

    private static GatewayConfig config(String strategy, String filePath, String listEndpoint, String getEndpoint) {
        GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(config.getLdapRolesLookupStrategy()).andReturn(strategy).anyTimes();
        EasyMock.expect(config.getLdapRolesLookupFilePath()).andReturn(filePath).anyTimes();
        EasyMock.expect(config.getLdapRolesListRestApiEndpoint()).andReturn(listEndpoint).anyTimes();
        EasyMock.expect(config.getLdapRolesGetRestApiEndpoint()).andReturn(getEndpoint).anyTimes();
        EasyMock.replay(config);
        return config;
    }

    @Test
    public void testNoStrategyReturnsNull() throws Exception {
        assertNull(LdapRolesReverseLookupFactory.create(config(null, null, null, null)));
        assertNull(LdapRolesReverseLookupFactory.create(config("  ", null, null, null)));
    }

    @Test
    public void testFileStrategy() throws Exception {
        LdapRolesReverseLookup lookup = LdapRolesReverseLookupFactory.create(config("FILE", "/tmp/roles.json", null, null));
        assertTrue(lookup instanceof FileBasedLdapRolesReverseLookup);
    }

    @Test(expected = RoleException.class)
    public void testFileStrategyWithoutPathFails() throws Exception {
        LdapRolesReverseLookupFactory.create(config("file", "", null, null));
    }

    @Test
    public void testRestStrategy() throws Exception {
        LdapRolesReverseLookup lookup = LdapRolesReverseLookupFactory.create(
                config("rest", null, "http://localhost/roles", "http://localhost/roles/get"));
        assertTrue(lookup instanceof RestApiLdapRolesReverseLookup);
    }

    @Test(expected = RoleException.class)
    public void testRestStrategyWithoutListEndpointFails() throws Exception {
        LdapRolesReverseLookupFactory.create(config("rest", null, null, "http://localhost/roles/get"));
    }

    @Test(expected = RoleException.class)
    public void testRestStrategyWithoutGetEndpointFails() throws Exception {
        LdapRolesReverseLookupFactory.create(config("rest", null, "http://localhost/roles", ""));
    }

    @Test(expected = RoleException.class)
    public void testUnknownStrategyFails() throws Exception {
        LdapRolesReverseLookupFactory.create(config("carrier-pigeon", null, null, null));
    }
}

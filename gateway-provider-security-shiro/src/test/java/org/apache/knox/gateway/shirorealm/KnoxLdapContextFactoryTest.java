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
package org.apache.knox.gateway.shirorealm;

import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KnoxLdapContextFactoryTest {

    private AliasService aliasService;

    @Before
    public void setup() {
        aliasService = EasyMock.createNiceMock(AliasService.class);
    }

    /**
     * Test-specific subclass to avoid using GatewayServer static method
     */
    private class TestKnoxLdapContextFactory extends KnoxLdapContextFactory {
        @Override
        protected AliasService getAliasService() {
            return aliasService;
        }
    }

    @Test
    public void testSetSystemPasswordBlank() {
        final KnoxLdapContextFactory factory = new TestKnoxLdapContextFactory();
        factory.setSystemPassword("");
        assertNull(factory.getSystemPassword());

        factory.setSystemPassword(null);
        assertNull(factory.getSystemPassword());
    }

    @Test
    public void testSetSystemPasswordIsNotAlias() {
        final KnoxLdapContextFactory factory = new TestKnoxLdapContextFactory();
        final String password = "password";
        factory.setSystemPassword(password);
        assertEquals(password, factory.getSystemPassword());
    }

    @Test
    public void testSetSystemPasswordIsAliasFoundInClusterCredentialStore() throws Exception {
        testAliasFoundInCredentialStore(PasswordLocation.CLUSTER);
    }

    @Test
    public void testSetSystemPasswordIsAliasFoundInGatewayCredentialStore() throws Exception {
        testAliasFoundInCredentialStore(PasswordLocation.GATEWAY);
    }

    @Test
    public void testSetSystemPasswordNotFound() throws Exception {
        testAliasFoundInCredentialStore(PasswordLocation.NOWHERE);
    }

    private enum PasswordLocation {
        CLUSTER, GATEWAY, NOWHERE;
    }

    private void testAliasFoundInCredentialStore(final PasswordLocation location) throws AliasServiceException {
        final String clusterName = "test-cluster";
        final KnoxLdapContextFactory factory = new TestKnoxLdapContextFactory();
        factory.setClusterName(clusterName);
        final String systemPasswordAlias = "systemPasswordAlias";
        final String systemPassword = AliasService.ALIAS_PREFIX + systemPasswordAlias + "}";
        final char[] resolvedPassword = "resolved-password".toCharArray();

        EasyMock.expect(aliasService.isAlias(systemPassword)).andReturn(true).anyTimes();
        EasyMock.expect(aliasService.extractAlias(systemPassword)).andReturn(systemPasswordAlias).anyTimes();
        EasyMock.expect(aliasService.getPasswordFromAliasForCluster(clusterName, systemPasswordAlias)).andReturn(location == PasswordLocation.CLUSTER ? resolvedPassword : null);
        if (location != PasswordLocation.CLUSTER) {
            EasyMock.expect(aliasService.getPasswordFromAliasForGateway(systemPasswordAlias)).andReturn(location == PasswordLocation.GATEWAY ? resolvedPassword : null);
        }
        EasyMock.replay(aliasService);

        factory.setSystemPassword(systemPassword);
        if (location == PasswordLocation.NOWHERE) {
            assertEquals("", factory.getSystemPassword());
        } else {
            assertEquals(new String(resolvedPassword), factory.getSystemPassword());
        }

        EasyMock.verify(aliasService);
    }
}

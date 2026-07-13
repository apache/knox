/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.services.ldap.interceptor;

import org.apache.directory.server.core.api.interceptor.Interceptor;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ldap.LDAPRolesLookupService;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LDAPRolesLookupInterceptorFactoryTest {

    @Test
    public void testCreateWithEnabledService() throws Exception {
        LDAPRolesLookupService mockService = EasyMock.createMock(LDAPRolesLookupService.class);
        EasyMock.expect(mockService.enabled()).andReturn(true).anyTimes();
        EasyMock.replay(mockService);

        LDAPRolesLookupInterceptorFactory factory = new LDAPRolesLookupInterceptorFactory() {
            @Override
            protected LDAPRolesLookupService getLDAPRolesLookupService(GatewayServices gatewayServices) {
                return mockService;
            }
        };

        GatewayConfig mockConfig = EasyMock.createMock(GatewayConfig.class);
        EasyMock.replay(mockConfig);

        Interceptor interceptor = factory.create(mockConfig, null, "test", Collections.emptyMap());
        assertNotNull(interceptor);
        assertTrue(interceptor instanceof LDAPRolesLookupInterceptor);
    }

    @Test(expected = ServiceLifecycleException.class)
    public void testCreateWithDisabledService() throws Exception {
        LDAPRolesLookupService mockService = EasyMock.createMock(LDAPRolesLookupService.class);
        EasyMock.expect(mockService.enabled()).andReturn(false).anyTimes();
        EasyMock.replay(mockService);

        LDAPRolesLookupInterceptorFactory factory = new LDAPRolesLookupInterceptorFactory() {
            @Override
            protected LDAPRolesLookupService getLDAPRolesLookupService(GatewayServices gatewayServices) {
                return mockService;
            }
        };

        GatewayConfig mockConfig = EasyMock.createMock(GatewayConfig.class);
        EasyMock.replay(mockConfig);

        factory.create(mockConfig, null, "test", Collections.emptyMap());
    }

    @Test(expected = ServiceLifecycleException.class)
    public void testCreateWithNullService() throws Exception {
        LDAPRolesLookupInterceptorFactory factory = new LDAPRolesLookupInterceptorFactory() {
            @Override
            protected LDAPRolesLookupService getLDAPRolesLookupService(org.apache.knox.gateway.services.GatewayServices gatewayServices) {
                return null;
            }
        };

        GatewayConfig mockConfig = EasyMock.createMock(GatewayConfig.class);
        EasyMock.replay(mockConfig);

        factory.create(mockConfig, null, "test", Collections.emptyMap());
    }
}

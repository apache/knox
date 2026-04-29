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
package org.apache.knox.gateway.topology.discovery.cm;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.knox.gateway.util.TruststorePasswordSetter;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyStore;
import java.util.Collections;
import java.util.Properties;

public class ApiClientFactoryTest {

    Properties originalProps;

    @Before
    public void setUp() {
        originalProps = System.getProperties();
    }

    @After
    public void tearDown() {
        System.setProperties(originalProps);
    }

    @Test
    public void testSystemPropertySetWhenAliasExists() throws AliasServiceException {
        testGetApiClient(true, "changeit");
    }

    @Test
    public void testSystemPropertyNotSetWhenAliasNotExists() throws AliasServiceException {
        testGetApiClient(false, null);
    }

    @Test
    public void testSystemPropertySetWhenAliasEmpty() throws AliasServiceException {
        testGetApiClient(true, "");
    }

    private void testGetApiClient(final boolean shouldSetSystemProperty, String trustStorePassword) throws AliasServiceException {
        final RecordingProperties testProps = new RecordingProperties(originalProps);
        System.setProperties(testProps);

        final GatewayConfig gatewayConfig = EasyMock.createMock(GatewayConfig.class);
        EasyMock.expect(gatewayConfig.getClouderaManagerServiceDiscoveryApiVersion()).andReturn("57").anyTimes();
        EasyMock.expect(gatewayConfig.getClouderaManagerServiceDiscoveryConnectTimeoutMillis()).andReturn(1L).anyTimes();
        EasyMock.expect(gatewayConfig.getClouderaManagerServiceDiscoveryReadTimeoutMillis()).andReturn(1L).anyTimes();
        EasyMock.expect(gatewayConfig.getClouderaManagerServiceDiscoveryWriteTimeoutMillis()).andReturn(1L).anyTimes();
        EasyMock.expect(gatewayConfig.getClouderaManagerClientSSLCiphers()).andReturn(Collections.emptyList()).anyTimes();
        EasyMock.expect(gatewayConfig.getClouderaManagerClientSSLProtocols()).andReturn(Collections.emptySet()).anyTimes();
        final ServiceDiscoveryConfig serviceDiscoveryConfig = EasyMock.createMock(ServiceDiscoveryConfig.class);
        EasyMock.expect(serviceDiscoveryConfig.getAddress()).andReturn("myCmHost").anyTimes();
        EasyMock.expect(serviceDiscoveryConfig.getUser()).andReturn("myCmUser").anyTimes();
        EasyMock.expect(serviceDiscoveryConfig.getPasswordAlias()).andReturn("myCmPasswordAlias").anyTimes();
        final AliasService aliasService = EasyMock.createMock(AliasService.class);
        if (shouldSetSystemProperty) {
            EasyMock.expect(aliasService.getPasswordFromAliasForGateway(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_ALIAS)).andReturn(trustStorePassword.toCharArray()).anyTimes();
        } else {
            EasyMock.expect(aliasService.getPasswordFromAliasForGateway(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_ALIAS)).andReturn(null).anyTimes();
        }
        EasyMock.expect(aliasService.getPasswordFromAliasForGateway("myCmPasswordAlias")).andReturn("myCmPassword".toCharArray()).anyTimes();
        final KeyStore trustStore = EasyMock.createMock(KeyStore.class);

        EasyMock.replay(aliasService, gatewayConfig, serviceDiscoveryConfig, trustStore);
        ApiClientFactory.getApiClient(gatewayConfig, serviceDiscoveryConfig, aliasService, trustStore);

        if (shouldSetSystemProperty && StringUtils.isNotBlank(trustStorePassword)) {
            Assert.assertEquals(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY, testProps.lastSetKey);
            Assert.assertEquals(trustStorePassword, testProps.lastSetValue);
            Assert.assertEquals(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY, testProps.lastRemovedKey);
        } else {
            Assert.assertNull(testProps.lastSetKey);
            Assert.assertNull(testProps.lastSetValue);
            Assert.assertEquals(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY, testProps.lastRemovedKey);
        }
    }

    static class RecordingProperties extends Properties {
        private final Properties delegate;

        String lastSetKey;
        String lastSetValue;
        String lastRemovedKey;

        RecordingProperties(Properties delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized Object setProperty(String key, String value) {
            lastSetKey = key;
            lastSetValue = value;
            return delegate.setProperty(key, value);
        }

        @Override
        public synchronized Object remove(Object key) {
            lastRemovedKey = (String) key;
            return delegate.remove(key);
        }

        @Override
        public String getProperty(String key) {
            return delegate.getProperty(key);
        }
    }
}

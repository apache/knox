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
package org.apache.knox.gateway.pac4j.filter;

import org.apache.knox.gateway.pac4j.session.KnoxSessionStore;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.CryptoService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.MasterService;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.pac4j.config.client.PropertiesConstants.SAML_IDENTITY_PROVIDER_METADATA_PATH;
import static org.pac4j.config.client.PropertiesConstants.SAML_KEYSTORE_PATH;

public class Pac4jDispatcherFilterTest {

    private static final String TEST_CLUSTER_NAME = "test-cluster";
    private static final String TEST_CALLBACK_URL = "https://localhost:8443/gateway/knoxsso/api/v1/websso";

    private static class TestMocks {
        ServletContext context;
        GatewayServices services;
        CryptoService cryptoService;
        AliasService aliasService;
        KeystoreService keystoreService;
        MasterService masterService;
        FilterConfig filterConfig;
        KeyStore ks;
    }

    private TestMocks createMocks() throws Exception {
        TestMocks mocks = new TestMocks();
        mocks.ks = KeyStore.getInstance("JKS");
        mocks.context = EasyMock.createNiceMock(ServletContext.class);
        mocks.services = EasyMock.createNiceMock(GatewayServices.class);
        mocks.cryptoService = EasyMock.createNiceMock(CryptoService.class);
        mocks.aliasService = EasyMock.createNiceMock(AliasService.class);
        mocks.keystoreService = EasyMock.createNiceMock(KeystoreService.class);
        mocks.masterService = EasyMock.createNiceMock(MasterService.class);
        mocks.filterConfig = EasyMock.createNiceMock(FilterConfig.class);
        return mocks;
    }

    private void setupCommonExpectations(TestMocks mocks, List<String> additionalParams) throws Exception {
        List<String> params = new ArrayList<>();
        params.add(Pac4jDispatcherFilter.PAC4J_CALLBACK_URL);
        params.add("clientName");
        params.add(SAML_KEYSTORE_PATH);
        params.add(SAML_IDENTITY_PROVIDER_METADATA_PATH);

        params.addAll(additionalParams);

        EasyMock.expect(mocks.keystoreService.getKeystoreForGateway()).andReturn(mocks.ks).anyTimes();
        EasyMock.expect(mocks.masterService.getMasterSecret()).andReturn("apacheknox".toCharArray()).anyTimes();

        EasyMock.expect(mocks.context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(mocks.services).anyTimes();
        EasyMock.expect(mocks.context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(TEST_CLUSTER_NAME).anyTimes();
        EasyMock.expect(mocks.services.getService(ServiceType.CRYPTO_SERVICE)).andReturn(mocks.cryptoService).anyTimes();
        EasyMock.expect(mocks.services.getService(ServiceType.ALIAS_SERVICE)).andReturn(mocks.aliasService).anyTimes();
        EasyMock.expect(mocks.services.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(mocks.keystoreService).anyTimes();
        EasyMock.expect(mocks.services.getService(ServiceType.MASTER_SERVICE)).andReturn(mocks.masterService).anyTimes();
        EasyMock.expect(mocks.filterConfig.getServletContext()).andReturn(mocks.context).anyTimes();
        EasyMock.expect(mocks.filterConfig.getInitParameterNames()).andReturn(Collections.enumeration(params)).anyTimes();
        EasyMock.expect(mocks.filterConfig.getInitParameter(Pac4jDispatcherFilter.PAC4J_CALLBACK_URL)).andReturn(TEST_CALLBACK_URL).anyTimes();
        EasyMock.expect(mocks.filterConfig.getInitParameter(SAML_KEYSTORE_PATH)).andReturn("/var/keystore").anyTimes();
        EasyMock.expect(mocks.filterConfig.getInitParameter(SAML_IDENTITY_PROVIDER_METADATA_PATH)).andReturn("/tmp/sp-metadata.xml").anyTimes();
        EasyMock.expect(mocks.filterConfig.getInitParameter("clientName")).andReturn("SAML2Client").anyTimes();
        EasyMock.expect(mocks.aliasService.getPasswordFromAliasForCluster(TEST_CLUSTER_NAME, KnoxSessionStore.PAC4J_PASSWORD, true))
                .andReturn(KnoxSessionStore.PAC4J_PASSWORD.toCharArray()).anyTimes();
    }

    private void verifyCookiemaxAge(FilterConfig filterConfig, String expectedCookieMaxAge) throws Exception {
        Pac4jDispatcherFilter filter = new Pac4jDispatcherFilter();
        filter.init(filterConfig);

        java.lang.reflect.Field configField = filter.getClass().getDeclaredField("sessionStoreConfigs");
        configField.setAccessible(true);
        Map<String, String> sessionStoreConfigs = (Map<String, String>) configField.get(filter);
        Assert.assertEquals(expectedCookieMaxAge, sessionStoreConfigs.get(Pac4jDispatcherFilter.PAC4J_COOKIE_MAX_AGE));
    }


    /**
     * Test that verifies a custom value for PAC4J_COOKIE_MAX_AGE is properly set when provided in the configuration
     */
    @Test
    public void testCustomCookieMaxAge() throws Exception {
        final String expectedCookieMaxAge = "1800";
        TestMocks mocks = createMocks();
        List<String> additionalParams = new ArrayList<>();
        additionalParams.add(Pac4jDispatcherFilter.PAC4J_COOKIE_MAX_AGE);
        setupCommonExpectations(mocks, additionalParams);
        EasyMock.expect(mocks.filterConfig.getInitParameter(Pac4jDispatcherFilter.PAC4J_COOKIE_MAX_AGE)).andReturn(expectedCookieMaxAge).anyTimes();

        EasyMock.replay(mocks.context, mocks.services, mocks.cryptoService, mocks.aliasService, mocks.keystoreService, mocks.masterService, mocks.filterConfig);

        verifyCookiemaxAge(mocks.filterConfig, expectedCookieMaxAge);

        // Verify all mock interactions
        EasyMock.verify(mocks.context, mocks.services, mocks.cryptoService, mocks.aliasService, mocks.keystoreService, mocks.masterService, mocks.filterConfig);
    }

    /**
     * Test that verifies the default value of PAC4J_COOKIE_MAX_AGE is set (-1) when no value is provided in the configuration
     */
    @Test
    public void testDefaultCookieMaxAge() throws Exception {
        final String expectedCookieMaxAge = "-1";
        List<String> params = new ArrayList<>();
        params.add(Pac4jDispatcherFilter.PAC4J_CALLBACK_URL);
        params.add("clientName");
        params.add(SAML_KEYSTORE_PATH);
        params.add(SAML_IDENTITY_PROVIDER_METADATA_PATH);

        TestMocks mocks = createMocks();
        setupCommonExpectations(mocks, Collections.EMPTY_LIST);

        EasyMock.replay(mocks.context, mocks.services, mocks.cryptoService, mocks.aliasService, mocks.keystoreService, mocks.masterService, mocks.filterConfig);

        verifyCookiemaxAge(mocks.filterConfig, expectedCookieMaxAge);

        // Verify all mock interactions
        EasyMock.verify(mocks.context, mocks.services, mocks.cryptoService, mocks.aliasService, mocks.keystoreService, mocks.masterService, mocks.filterConfig);
    }
}

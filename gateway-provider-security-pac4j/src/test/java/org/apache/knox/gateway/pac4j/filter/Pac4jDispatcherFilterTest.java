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


    /**
     * Test that verifies a custom value for PAC4J_COOKIE_MAX_AGE is properly set when provided in the configuration
     */
    @Test
    public void testCustomCookieMaxAge() throws Exception {

        String COOKIE_MAX_AGE = "1800";
        List<String> params = new ArrayList<>();
        params.add(Pac4jDispatcherFilter.PAC4J_CALLBACK_URL);
        params.add(Pac4jDispatcherFilter.PAC4J_COOKIE_MAX_AGE);
        params.add("clientName");
        params.add(SAML_KEYSTORE_PATH);
        params.add(SAML_IDENTITY_PROVIDER_METADATA_PATH);

        KeyStore ks = KeyStore.getInstance("JKS");

        ServletContext context = EasyMock.createNiceMock(ServletContext.class);
        GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
        CryptoService cryptoService = EasyMock.createNiceMock(CryptoService.class);
        AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
        KeystoreService keystoreService = EasyMock.createNiceMock(KeystoreService.class);
        MasterService masterService = EasyMock.createNiceMock(MasterService.class);
        FilterConfig filterConfig = EasyMock.createNiceMock(FilterConfig.class);

        EasyMock.expect(keystoreService.getKeystoreForGateway()).andReturn(ks).anyTimes();
        EasyMock.expect(masterService.getMasterSecret()).andReturn("apacheknox".toCharArray()).anyTimes();

        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services).anyTimes();
        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(TEST_CLUSTER_NAME).anyTimes();
        EasyMock.expect(services.getService(ServiceType.CRYPTO_SERVICE)).andReturn(cryptoService).anyTimes();
        EasyMock.expect(services.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService).anyTimes();
        EasyMock.expect(services.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(keystoreService).anyTimes();
        EasyMock.expect(services.getService(ServiceType.MASTER_SERVICE)).andReturn(masterService).anyTimes();
        EasyMock.expect(filterConfig.getServletContext()).andReturn(context).anyTimes();
        EasyMock.expect(filterConfig.getInitParameterNames()).andReturn(Collections.enumeration(params)).anyTimes();
        EasyMock.expect(filterConfig.getInitParameter(Pac4jDispatcherFilter.PAC4J_CALLBACK_URL)).andReturn(TEST_CALLBACK_URL).anyTimes();
        EasyMock.expect(filterConfig.getInitParameter(SAML_KEYSTORE_PATH)).andReturn("/var/keystore").anyTimes();
        EasyMock.expect(filterConfig.getInitParameter(SAML_IDENTITY_PROVIDER_METADATA_PATH)).andReturn("/tmp/sp-metadata.xml").anyTimes();
        EasyMock.expect(filterConfig.getInitParameter("clientName")).andReturn("SAML2Client").anyTimes();
        EasyMock.expect(filterConfig.getInitParameter(Pac4jDispatcherFilter.PAC4J_COOKIE_MAX_AGE)).andReturn(COOKIE_MAX_AGE).anyTimes();
        EasyMock.expect(aliasService.getPasswordFromAliasForCluster(TEST_CLUSTER_NAME, KnoxSessionStore.PAC4J_PASSWORD, true))
                .andReturn(KnoxSessionStore.PAC4J_PASSWORD.toCharArray()).anyTimes();


        EasyMock.replay(context, services, cryptoService, aliasService, keystoreService, masterService, filterConfig);

        Pac4jDispatcherFilter filter = new Pac4jDispatcherFilter();
        filter.init(filterConfig);


        java.lang.reflect.Field configField = filter.getClass().getDeclaredField("sessionStoreConfigs");
        configField.setAccessible(true);
        Map<String, String> sessionStoreConfigs = (Map<String, String>) configField.get(filter);
        Assert.assertEquals(COOKIE_MAX_AGE, sessionStoreConfigs.get(Pac4jDispatcherFilter.PAC4J_COOKIE_MAX_AGE));

        // Verify all mock interactions
        EasyMock.verify(context, services, cryptoService, aliasService, keystoreService, masterService, filterConfig);
    }

    /**
     * Test that verifies the default value of PAC4J_COOKIE_MAX_AGE is set (-1) when no value is provided in the configuration
     */
    @Test
    public void testDefaultCookieMaxAge() throws Exception {
        String COOKIE_MAX_AGE = "-1";
        List<String> params = new ArrayList<>();
        params.add(Pac4jDispatcherFilter.PAC4J_CALLBACK_URL);
        params.add("clientName");
        params.add(SAML_KEYSTORE_PATH);
        params.add(SAML_IDENTITY_PROVIDER_METADATA_PATH);

        KeyStore ks = KeyStore.getInstance("JKS");

        ServletContext context = EasyMock.createNiceMock(ServletContext.class);
        GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
        CryptoService cryptoService = EasyMock.createNiceMock(CryptoService.class);
        AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
        KeystoreService keystoreService = EasyMock.createNiceMock(KeystoreService.class);
        MasterService masterService = EasyMock.createNiceMock(MasterService.class);
        FilterConfig filterConfig = EasyMock.createNiceMock(FilterConfig.class);

        EasyMock.expect(keystoreService.getKeystoreForGateway()).andReturn(ks).anyTimes();
        EasyMock.expect(masterService.getMasterSecret()).andReturn("apacheknox".toCharArray()).anyTimes();

        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services).anyTimes();
        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(TEST_CLUSTER_NAME).anyTimes();
        EasyMock.expect(services.getService(ServiceType.CRYPTO_SERVICE)).andReturn(cryptoService).anyTimes();
        EasyMock.expect(services.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService).anyTimes();
        EasyMock.expect(services.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(keystoreService).anyTimes();
        EasyMock.expect(services.getService(ServiceType.MASTER_SERVICE)).andReturn(masterService).anyTimes();
        EasyMock.expect(filterConfig.getServletContext()).andReturn(context).anyTimes();
        EasyMock.expect(filterConfig.getInitParameterNames()).andReturn(Collections.enumeration(params)).anyTimes();
        EasyMock.expect(filterConfig.getInitParameter(Pac4jDispatcherFilter.PAC4J_CALLBACK_URL)).andReturn(TEST_CALLBACK_URL).anyTimes();
        EasyMock.expect(filterConfig.getInitParameter(SAML_KEYSTORE_PATH)).andReturn("/var/keystore").anyTimes();
        EasyMock.expect(filterConfig.getInitParameter(SAML_IDENTITY_PROVIDER_METADATA_PATH)).andReturn("/tmp/sp-metadata.xml").anyTimes();
        EasyMock.expect(filterConfig.getInitParameter("clientName")).andReturn("SAML2Client").anyTimes();
        EasyMock.expect(aliasService.getPasswordFromAliasForCluster(TEST_CLUSTER_NAME, KnoxSessionStore.PAC4J_PASSWORD, true))
                .andReturn(KnoxSessionStore.PAC4J_PASSWORD.toCharArray()).anyTimes();


        EasyMock.replay(context, services, cryptoService, aliasService, keystoreService, masterService, filterConfig);

        Pac4jDispatcherFilter filter = new Pac4jDispatcherFilter();
        filter.init(filterConfig);

        java.lang.reflect.Field configField = filter.getClass().getDeclaredField("sessionStoreConfigs");
        configField.setAccessible(true);
        Map<String, String> sessionStoreConfigs = (Map<String, String>) configField.get(filter);
        Assert.assertEquals(COOKIE_MAX_AGE, sessionStoreConfigs.get(Pac4jDispatcherFilter.PAC4J_COOKIE_MAX_AGE));

        // Verify all mock interactions
        EasyMock.verify(context, services, cryptoService, aliasService, keystoreService, masterService, filterConfig);
    }
}

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

package org.apache.knox.gateway.dispatch;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertNotNull;

public class DefaultHttpAsyncClientFactoryTest {

    @Test
    public void testCreateHttpAsyncClientSSLContextDefaults() throws Exception {
        KeystoreService keystoreService = createMock(KeystoreService.class);
        expect(keystoreService.getTruststoreForHttpClient()).andReturn(null).once();

        GatewayConfig gatewayConfig = createMock(GatewayConfig.class);
        expect(gatewayConfig.isMetricsEnabled()).andReturn(false).once();
        expect(gatewayConfig.getHttpClientMaxConnections()).andReturn(32).once();
        expect(gatewayConfig.getHttpClientConnectionTimeout()).andReturn(20000).once();
        expect(gatewayConfig.getHttpClientSocketTimeout()).andReturn(20000).once();
        expect(gatewayConfig.getHttpClientCookieSpec()).andReturn(CookieSpecs.STANDARD).anyTimes();

        GatewayServices gatewayServices = createMock(GatewayServices.class);
        expect(gatewayServices.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(keystoreService).once();

        ServletContext servletContext = createMock(ServletContext.class);
        expect(servletContext.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gatewayConfig).atLeastOnce();
        expect(servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gatewayServices).atLeastOnce();

        FilterConfig filterConfig = createMock(FilterConfig.class);
        expect(filterConfig.getServletContext()).andReturn(servletContext).atLeastOnce();
        expect(filterConfig.getInitParameter("useTwoWaySsl")).andReturn("false").once();
        expect(filterConfig.getInitParameter("httpclient.maxConnections")).andReturn(null).once();
        expect(filterConfig.getInitParameter("httpclient.connectionTimeout")).andReturn(null).once();
        expect(filterConfig.getInitParameter("httpclient.socketTimeout")).andReturn(null).once();
        expect(filterConfig.getInitParameter("serviceRole")).andReturn(null).once();
        expect(filterConfig.getInitParameter("httpclient.cookieSpec")).andReturn(null).anyTimes();

        replay(keystoreService, gatewayConfig, gatewayServices, servletContext, filterConfig);

        DefaultHttpAsyncClientFactory factory = new DefaultHttpAsyncClientFactory();
        HttpAsyncClient client = factory.createAsyncHttpClient(filterConfig);
        assertNotNull(client);

        verify(keystoreService, gatewayConfig, gatewayServices, servletContext, filterConfig);
    }
}

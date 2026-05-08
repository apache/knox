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
package org.apache.knox.gateway.service.restcatalog;

import org.apache.http.client.methods.HttpRequestBase;
import org.apache.knox.gateway.security.CommonTokenConstants;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.easymock.EasyMock;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TokenMetadataHeaderHandlerTest {

    @Test
    public void testNullTokenMetadataHeadersConfig() throws Exception {
        FilterConfig filterConfig = createFilterConfig(null, null, null, null);
        // This should not throw NPE
        TokenMetadataHeaderHandler handler = new TokenMetadataHeaderHandler(filterConfig);
        assertNotNull(handler);
    }

    @Test
    public void testEmptyTokenMetadataHeadersConfig() throws Exception {
        FilterConfig filterConfig = createFilterConfig("", null, null, null);
        TokenMetadataHeaderHandler handler = new TokenMetadataHeaderHandler(filterConfig);
        assertNotNull(handler);
    }

    @Test
    public void testApplyHeaders() throws Exception {
        final String clientId = "test-client-id";
        Map<String, String> metadataMap = new HashMap<>();
        metadataMap.put("userName", "test-user");
        metadataMap.put("custom-meta", "custom-value");
        metadataMap.put("another-meta", "another-value");

        FilterConfig filterConfig = createFilterConfig("custom-meta, another-meta", null, clientId, metadataMap);
        TokenMetadataHeaderHandler handler = new TokenMetadataHeaderHandler(filterConfig);

        HttpServletRequest inboundRequest = createInboundRequest(clientId);
        TestHttpUriRequest outboundRequest = new TestHttpUriRequest();
        handler.applyHeadersToRequest(inboundRequest, outboundRequest);

        assertEquals("test-user", outboundRequest.getFirstHeader("X-Knox-Meta-userName").getValue());
        assertEquals("custom-value", outboundRequest.getFirstHeader("X-Knox-Meta-custom-meta").getValue());
        assertEquals("another-value", outboundRequest.getFirstHeader("X-Knox-Meta-another-meta").getValue());
    }

    @Test
    public void testApplyHeadersCustomPrefix() throws Exception {
        final String clientId = "test-client-id";
        Map<String, String> metadataMap = new HashMap<>();
        metadataMap.put("userName", "test-user");

        FilterConfig filterConfig = createFilterConfig(null, "Custom-Prefix-", clientId, metadataMap);
        TokenMetadataHeaderHandler handler = new TokenMetadataHeaderHandler(filterConfig);

        HttpServletRequest inboundRequest = createInboundRequest(clientId);
        TestHttpUriRequest outboundRequest = new TestHttpUriRequest();
        handler.applyHeadersToRequest(inboundRequest, outboundRequest);

        assertEquals("test-user", outboundRequest.getFirstHeader("Custom-Prefix-userName").getValue());
    }

    @Test
    public void testApplyHeadersMissingMetadata() throws Exception {
        final String clientId = "test-client-id";
        Map<String, String> metadataMap = new HashMap<>(); // Empty metadata

        FilterConfig filterConfig = createFilterConfig("custom-meta", null, clientId, metadataMap);
        TokenMetadataHeaderHandler handler = new TokenMetadataHeaderHandler(filterConfig);

        HttpServletRequest inboundRequest = createInboundRequest(clientId);
        TestHttpUriRequest outboundRequest = new TestHttpUriRequest();
        handler.applyHeadersToRequest(inboundRequest, outboundRequest);

        assertNull("userName header should be null if metadata is missing", outboundRequest.getFirstHeader("X-Knox-Meta-userName"));
        assertNull("custom-meta header should be null if metadata is missing", outboundRequest.getFirstHeader("X-Knox-Meta-custom-meta"));
    }

    private FilterConfig createFilterConfig(String tokenMetadataHeaders, String headerPrefix, String clientId, Map<String, String> metadataMap) throws Exception {
        FilterConfig filterConfig = EasyMock.createNiceMock(FilterConfig.class);
        ServletContext servletContext = EasyMock.createNiceMock(ServletContext.class);
        GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
        TokenStateService tss = EasyMock.createNiceMock(TokenStateService.class);

        EasyMock.expect(filterConfig.getServletContext()).andReturn(servletContext).anyTimes();
        EasyMock.expect(servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gatewayServices).anyTimes();
        EasyMock.expect(gatewayServices.getService(ServiceType.TOKEN_STATE_SERVICE)).andReturn(tss).anyTimes();
        EasyMock.expect(filterConfig.getInitParameter(TokenMetadataHeaderHandler.TOKEN_METADATA_PARAM)).andReturn(tokenMetadataHeaders).anyTimes();
        EasyMock.expect(filterConfig.getInitParameter(TokenMetadataHeaderHandler.METADATA_HEADER_PREFIX_PARAM)).andReturn(headerPrefix).anyTimes();

        if (clientId != null && metadataMap != null) {
            TokenMetadata tokenMetadata = EasyMock.createNiceMock(TokenMetadata.class);
            EasyMock.expect(tss.getTokenMetadata(clientId)).andReturn(tokenMetadata).anyTimes();
            EasyMock.expect(tokenMetadata.getMetadataMap()).andReturn(metadataMap).anyTimes();
            EasyMock.replay(tokenMetadata);
        }

        EasyMock.replay(filterConfig, servletContext, gatewayServices, tss);
        return filterConfig;
    }

    private HttpServletRequest createInboundRequest(String clientId) {
        final String clientSecret = Base64.getEncoder().encodeToString(
                (Base64.getEncoder().encodeToString(clientId.getBytes(UTF_8)) + "::" +
                 Base64.getEncoder().encodeToString("secret".getBytes(UTF_8))).getBytes(UTF_8));

        HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(inboundRequest.getParameter(CommonTokenConstants.GRANT_TYPE)).andReturn(CommonTokenConstants.CLIENT_CREDENTIALS).anyTimes();
        EasyMock.expect(inboundRequest.getParameter(CommonTokenConstants.CLIENT_SECRET)).andReturn(clientSecret).anyTimes();
        EasyMock.replay(inboundRequest);
        return inboundRequest;
    }

    private static class TestHttpUriRequest extends HttpRequestBase {
        @Override
        public String getMethod() {
            return HttpMethod.GET.asString();
        }
    }
}

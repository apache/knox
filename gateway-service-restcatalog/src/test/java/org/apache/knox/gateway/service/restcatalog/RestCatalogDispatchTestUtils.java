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
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.knox.gateway.security.CommonTokenConstants;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.test.mock.MockHttpServletResponse;
import org.easymock.EasyMock;
import org.eclipse.jetty.http.HttpMethod;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import static org.easymock.EasyMock.anyString;

public class RestCatalogDispatchTestUtils {

    static FilterConfig createBaseMocks(final Map<String, String> metaMap,
                                        final String              metaConfig,
                                        final String              client_id) throws Exception {
        return createBaseMocks(metaMap, metaConfig, null, client_id);
    }

    static FilterConfig createBaseMocks(final Map<String, String> metaMap,
                                        final String              metaConfig,
                                        final String              headerPrefix,
                                        final String              client_id) throws Exception {
        TokenMetadata mockMetadata = EasyMock.createNiceMock(TokenMetadata.class);
        EasyMock.expect(mockMetadata.getMetadataMap()).andReturn(metaMap).anyTimes();
        EasyMock.replay(mockMetadata);

        TokenStateService mockTss = EasyMock.createNiceMock(TokenStateService.class);
        if (client_id != null) {
            EasyMock.expect(mockTss.getTokenMetadata(client_id)).andReturn(mockMetadata).anyTimes();
        } else {
            EasyMock.expect(mockTss.getTokenMetadata(anyString())).andReturn(mockMetadata).anyTimes();
        }
        EasyMock.replay(mockTss);

        GatewayServices gws = EasyMock.createNiceMock(GatewayServices.class);
        EasyMock.expect(gws.getService(ServiceType.TOKEN_STATE_SERVICE)).andReturn(mockTss).anyTimes();
        EasyMock.replay(gws);

        ServletContext sc = EasyMock.createNiceMock(ServletContext.class);
        EasyMock.expect(sc.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gws).anyTimes();
        EasyMock.replay(sc);

        FilterConfig filterConfig = EasyMock.createNiceMock(FilterConfig.class);
        EasyMock.expect(filterConfig.getServletContext()).andReturn(sc).anyTimes();
        EasyMock.expect(filterConfig.getInitParameter(TokenMetadataHeaderHandler.TOKEN_METADATA_PARAM)).andReturn(metaConfig).anyTimes();
        EasyMock.expect(filterConfig.getInitParameter(TokenMetadataHeaderHandler.METADATA_HEADER_PREFIX_PARAM)).andReturn(headerPrefix).anyTimes();
        EasyMock.replay(filterConfig);

        return filterConfig;
    }

    /**
     *
     * @param metaMap       A Map of test token metadata
     * @param metaConfig    The value of the token-metadata-headers service configuration param
     * @param grantType     The grant type for the incoming request body
     * @param client_id     A client_id for the incoming request body
     * @param clientSecret  A client_secret for the incoming request body
     *
     * @return The resulting outbound request object.
     *
     * @throws Exception
     */
    HttpUriRequest doTest(final Map<String, String> metaMap,
                          final String              metaConfig,
                          final String              grantType,
                          final String              client_id,
                          final String              clientSecret) throws Exception {
        return doTest(metaMap, metaConfig, null, grantType, client_id, clientSecret);
    }

    /**
     *
     * @param metaMap       A Map of test token metadata
     * @param metaConfig    The value of the token-metadata-headers service configuration param
     * @param grantType     The grant type for the incoming request body
     * @param client_id     A client_id for the incoming request body
     * @param clientSecret  A client_secret for the incoming request body
     *
     * @return The resulting outbound request object.
     *
     * @throws Exception
     */
    HttpUriRequest doTest(final Map<String, String> metaMap,
                          final String              metaConfig,
                          final String              headerPrefix,
                          final String              grantType,
                          final String              client_id,
                          final String              clientSecret) throws Exception {
        FilterConfig filterConfig = createBaseMocks(metaMap, metaConfig, headerPrefix, client_id);

        HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(inboundRequest.getParameter(CommonTokenConstants.GRANT_TYPE)).andReturn(grantType).anyTimes();
        EasyMock.expect(inboundRequest.getParameter(CommonTokenConstants.CLIENT_SECRET)).andReturn(clientSecret).anyTimes();
        EasyMock.replay(inboundRequest);

        RestCatalogDispatch dispatch = new RestCatalogDispatch(filterConfig);
        dispatch.init();

        HttpServletResponse outboundResponse = new MockHttpServletResponse();
        HttpUriRequest outboundRequest = new TestHttpUriRequest();
        try {
            dispatch.executeRequest(outboundRequest, inboundRequest, outboundResponse);
        } catch (Exception e) {
            // Expected since the request is not complete and this test is only concerned with the outbound headers
        }

        return outboundRequest;
    }


    static class TestHttpUriRequest extends HttpRequestBase {
        private HttpMethod method = HttpMethod.GET;

        public void setMethod(HttpMethod method) {
            this.method = method;
        }

        @Override
        public String getMethod() {
            return method.name();
        }
    }
}

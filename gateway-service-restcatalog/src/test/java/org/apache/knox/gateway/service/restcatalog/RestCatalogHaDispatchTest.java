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

import org.apache.http.Header;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.params.BasicHttpParams;
import org.apache.knox.gateway.ha.provider.HaDescriptor;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.apache.knox.gateway.ha.provider.impl.DefaultHaProvider;
import org.apache.knox.gateway.ha.provider.impl.HaDescriptorFactory;
import org.apache.knox.gateway.security.CommonTokenConstants;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class RestCatalogHaDispatchTest {

    private static final String LOCATION = "Location";

    @Test
    public void testConnectivityFailure() throws Exception {
        final URI uri1 = new URI("https://host1.invalid");
        final URI uri2 = new URI("https://host2.invalid");
        final URI uri3 = new URI("https://host3.invalid");

        final String client_id = "4606f5e1-9d02-41a1-9071-26a9e74e4ab9";
        final String clientSecret =
                "TkRZd05tWTFaVEV0T1dRd01pMDBNV0V4TFRrd056RXRNalpoT1dVM05HVTBZV0k1OjpNV1JrWkRBMk1tVXRPV0ppTnkwMFkyWTVMVGxqTUdJdFpqWXhZalV5WTJGa1lURmw";

        final String metadata_email = "user@host.com";
        final String metadata_userName = "someuser";
        final String metadata_category = "test category";
        final String metadata_arbitrary = "somevalue";

        Map<String, String> metaMap = new HashMap<>();
        metaMap.put("email", metadata_email);
        metaMap.put("userName", metadata_userName);
        metaMap.put("category", metadata_category);
        metaMap.put("arbitrary", metadata_arbitrary);
        metaMap.put("enabled", "true");

        List<URI> urls = new ArrayList<>();
        urls.add(uri1);
        urls.add(uri2);
        urls.add(uri3);

        HaServiceConfig serviceConfig =
                HaDescriptorFactory.createServiceConfig(RestCatalogHaDispatch.SERVICE_ROLE,
                                                        "true",
                                                        "1",
                                                        "1000",
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null);
        HaProvider provider = createProvider(serviceConfig, urls);
        FilterConfig filterConfig =
                        RestCatalogDispatchTestUtils.createBaseMocks(metaMap, "email, test", client_id);

        RestCatalogDispatchTestUtils.TestHttpUriRequest outboundRequest =
                                                                new RestCatalogDispatchTestUtils.TestHttpUriRequest();
        outboundRequest.setURI(uri1);
        outboundRequest.setParams(new BasicHttpParams());

        HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(inboundRequest.getParameter(CommonTokenConstants.GRANT_TYPE)).andReturn(CommonTokenConstants.CLIENT_CREDENTIALS).anyTimes();
        EasyMock.expect(inboundRequest.getParameter(CommonTokenConstants.CLIENT_SECRET)).andReturn(clientSecret).anyTimes();
        EasyMock.expect(inboundRequest.getRequestURL()).andReturn(new StringBuffer(uri2.toString())).once();
        EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
        EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(1)).once();

        HttpServletResponse outboundResponse = EasyMock.createNiceMock(HttpServletResponse.class);
        EasyMock.expect(outboundResponse.getOutputStream()).andAnswer(new IAnswer<ServletOutputStream>() {
            @Override
            public ServletOutputStream answer() throws Throwable {
                return new ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        throw new IOException("unreachable-host.invalid");
                    }

                    @Override
                    public void setWriteListener(WriteListener arg0) {
                    }

                    @Override
                    public boolean isReady() {
                        return false;
                    }
                };
            }
        }).once();
        EasyMock.replay(inboundRequest, outboundResponse);

        assertEquals(uri1.toString(), provider.getActiveURL(RestCatalogHaDispatch.SERVICE_ROLE));
        RestCatalogHaDispatch dispatch = new RestCatalogHaDispatch(filterConfig);
        HttpClientBuilder builder = HttpClientBuilder.create();
        CloseableHttpClient client = builder.build();
        dispatch.setHttpClient(client);
        dispatch.setHaProvider(provider);
        dispatch.init();
        long startTime = System.currentTimeMillis();
        try {
            dispatch.executeRequest(outboundRequest, inboundRequest, outboundResponse);
        } catch (IOException e) {
            //this is expected after the failover limit is reached
        }
        long elapsedTime = System.currentTimeMillis() - startTime;
        assertEquals(uri3.toString(), provider.getActiveURL(RestCatalogHaDispatch.SERVICE_ROLE));

        // Validate the outbound request headers expected from the token metadata
        assertEquals("Unexpected number of headers in the outbound request",2, outboundRequest.getAllHeaders().length);
        Header[] usernameHeaders = outboundRequest.getHeaders(TokenMetadataHeaderHandler.DEFAULT_HEADER_PREFIX + "userName");
        assertEquals("userName is not configured as metadata which should result in an outbound header, but it is included by default.",
                1, usernameHeaders.length);
        assertEquals(metadata_userName, usernameHeaders[0].getValue());
        Header[] emailHeaders = outboundRequest.getHeaders(TokenMetadataHeaderHandler.DEFAULT_HEADER_PREFIX + "email");
        assertEquals("email is configured as metadata which should result in an outbound header.",
                1, emailHeaders.length);
        assertEquals(metadata_email, emailHeaders[0].getValue());
        assertEquals("Even though the test metadata is configured, there should be no header since there is no actual such metadata.",
                0,outboundRequest.getHeaders(TokenMetadataHeaderHandler.DEFAULT_HEADER_PREFIX + "test").length);
        assertEquals("Even though there is metadata by this name, it is not configured to be conveyed as a header, so it should be ignored.",
                0, outboundRequest.getHeaders(TokenMetadataHeaderHandler.DEFAULT_HEADER_PREFIX + "arbitrary").length);

        //test to make sure the sleep took place
        Assert.assertTrue(elapsedTime > 1000);
    }

    private HaProvider createProvider(HaServiceConfig serviceConfig, List<URI> urls) throws Exception {
        HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
        descriptor.addServiceConfig(serviceConfig);

        ArrayList<String> urlList = new ArrayList<>();
        for (URI uri : urls) {
            urlList.add(uri.toString());
        }

        HaProvider provider = new DefaultHaProvider(descriptor);
        provider.addHaService(RestCatalogHaDispatch.SERVICE_ROLE, urlList);

        return provider;
    }

}

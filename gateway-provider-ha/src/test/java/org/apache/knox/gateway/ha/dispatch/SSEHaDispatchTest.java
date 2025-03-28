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
package org.apache.knox.gateway.ha.dispatch;

import org.apache.http.HttpStatus;
import org.apache.http.client.config.CookieSpecs;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.ha.provider.HaDescriptor;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.impl.DefaultHaProvider;
import org.apache.knox.gateway.ha.provider.impl.HaDescriptorFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.test.mock.MockServer;
import org.apache.knox.test.mock.MockServletContext;
import org.apache.knox.test.mock.MockServletInputStream;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.servlet.AsyncContext;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SSEHaDispatchTest {

    private static MockServer MOCK_SSE_SERVER;
    private static URI URL;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        MOCK_SSE_SERVER = new MockServer("SSE", true);
        URL = new URI("http://localhost:" + MOCK_SSE_SERVER.getPort() + "/sse");
    }

    @Test
    public void testHADispatchURL() throws Exception {
        String serviceName = "HIVE";
        HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
        descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", null, null, null, null, "true", "true", null, null, null, null));
        HaProvider provider = new DefaultHaProvider(descriptor);
        URI uri1 = new URI("http://host1.valid");
        URI uri2 = new URI("http://host2.valid");
        URI uri3 = new URI("http://host3.valid");
        ArrayList<String> urlList = new ArrayList<>();
        urlList.add(uri1.toString());
        urlList.add(uri2.toString());
        urlList.add(uri3.toString());
        provider.addHaService(serviceName, urlList);

        KeystoreService keystoreService = createMock(KeystoreService.class);
        expect(keystoreService.getTruststoreForHttpClient()).andReturn(null).once();

        GatewayConfig gatewayConfig = createMock(GatewayConfig.class);
        expect(gatewayConfig.isMetricsEnabled()).andReturn(false).once();
        expect(gatewayConfig.isSSLEnabled()).andReturn(false).once();
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

        HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(inboundRequest.getRequestURL()).andReturn(new StringBuffer(provider.getActiveURL(serviceName))).anyTimes();
        EasyMock.replay(inboundRequest, gatewayConfig, filterConfig, servletContext, keystoreService, gatewayServices);

        SSEHaDispatch dispatch = new SSEHaDispatch(filterConfig);
        dispatch.setHaProvider(provider);
        dispatch.setServiceRole(serviceName);
        dispatch.init();

        /* make sure the dispatch URL is always active URL */
        Assert.assertEquals(provider.getActiveURL(serviceName), dispatch.getDispatchUrl(inboundRequest).toString());
    }

    @Test
    public void testStickySessionCookie() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEHaDispatch sseHaDispatch = this.createDispatch(null);
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        Capture<Cookie> capturedArgument = Capture.newInstance();

        this.expectResponseBodyAndHeader(printWriter, outboundResponse, capturedArgument);
        replay(inboundRequest, asyncContext, outboundResponse, printWriter);

        MOCK_SSE_SERVER.expect()
                .method("GET")
                .pathInfo("/sse")
                .header("Accept", "text/event-stream")
                .respond()
                .status(HttpStatus.SC_OK)
                .content("id:1\ndata:data1\nevent:event1\n\ndata:data2\nevent:event2\nid:2\nretry:1\n:testing\n\n", StandardCharsets.UTF_8)
                .header("response", "header")
                .contentType("text/event-stream");

        sseHaDispatch.doGet(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
        assertEquals("KNOX_BACKEND-HIVE", capturedArgument.getValue().getName());
    }

    @Test
    public void testNamedStickySessionCookie() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEHaDispatch sseHaDispatch = this.createDispatch("COOKIE_NAME");
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        Capture<Cookie> capturedArgument = Capture.newInstance();

        this.expectResponseBodyAndHeader(printWriter, outboundResponse, capturedArgument);
        replay(inboundRequest, asyncContext, outboundResponse, printWriter);

        MOCK_SSE_SERVER.expect()
                .method("GET")
                .pathInfo("/sse")
                .header("Accept", "text/event-stream")
                .respond()
                .status(HttpStatus.SC_OK)
                .content("id:1\ndata:data1\nevent:event1\n\ndata:data2\nevent:event2\nid:2\nretry:1\n:testing\n\n", StandardCharsets.UTF_8)
                .header("response", "header")
                .contentType("text/event-stream");

        sseHaDispatch.doGet(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
        assertEquals("COOKIE_NAME-HIVE", capturedArgument.getValue().getName());
    }

    @Test
    public void testLoadBalancingWithoutStickySessionCookie() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEHaDispatch sseHaDispatch = this.createDispatch(null);
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        Capture<Cookie> capturedArgument = Capture.newInstance();

        this.expectResponseBodyAndHeader(printWriter, outboundResponse, capturedArgument);
        replay(inboundRequest, asyncContext, outboundResponse, printWriter);

        MOCK_SSE_SERVER.expect()
                .method("GET")
                .pathInfo("/sse")
                .header("Accept", "text/event-stream")
                .respond()
                .status(HttpStatus.SC_OK)
                .content("id:1\ndata:data1\nevent:event1\n\ndata:data2\nevent:event2\nid:2\nretry:1\n:testing\n\n", StandardCharsets.UTF_8)
                .header("response", "header")
                .contentType("text/event-stream");

        sseHaDispatch.doGet(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
        assertEquals("http://localhost:" + MOCK_SSE_SERVER.getPort() + "/sse2", sseHaDispatch.getHaProvider().getActiveURL("HIVE"));
    }

    private SSEHaDispatch createDispatch(String cookieName) throws Exception {
        KeystoreService keystoreService = createMock(KeystoreService.class);
        expect(keystoreService.getTruststoreForHttpClient()).andReturn(null).once();

        GatewayConfig gatewayConfig = createMock(GatewayConfig.class);
        expect(gatewayConfig.isMetricsEnabled()).andReturn(false).once();
        expect(gatewayConfig.isSSLEnabled()).andReturn(true).once();
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

        HaProvider provider = this.createProvider(cookieName);

        replay(keystoreService, gatewayConfig, gatewayServices, servletContext, filterConfig);

        SSEHaDispatch dispatch = new SSEHaDispatch(filterConfig);
        dispatch.setHaProvider(provider);
        dispatch.setServiceRole("HIVE");
        dispatch.init();

        return dispatch;
    }

    private HttpServletResponse getServletResponse(int statusCode) {
        HttpServletResponse outboundResponse = EasyMock.createNiceMock(HttpServletResponse.class);

        outboundResponse.setStatus(statusCode);
        EasyMock.expectLastCall();

        return outboundResponse;
    }

    private AsyncContext getAsyncContext(CountDownLatch latch, HttpServletResponse outboundResponse) {
        AsyncContext asyncContext = EasyMock.createNiceMock(AsyncContext.class);

        EasyMock.expect(asyncContext.getResponse()).andReturn(outboundResponse).anyTimes();
        asyncContext.complete();
        EasyMock.expectLastCall().andAnswer(() -> {
            latch.countDown();
            return null;
        });

        return asyncContext;
    }

    private HttpServletRequest getHttpServletRequest(AsyncContext asyncContext) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("request", "header");
        InputStream stream = new ByteArrayInputStream("{\"request\":\"body\"}".getBytes(StandardCharsets.UTF_8));
        MockServletInputStream mockServletInputStream = new MockServletInputStream(stream);
        HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);

        EasyMock.expect(inboundRequest.getHeaderNames()).andReturn(Collections.enumeration(headers.keySet())).anyTimes();
        EasyMock.expect(inboundRequest.startAsync()).andReturn(asyncContext).anyTimes();
        EasyMock.expect(inboundRequest.getHeader("request")).andReturn("header").once();
        EasyMock.expect(inboundRequest.getContentType()).andReturn("application/json").anyTimes();
        EasyMock.expect(inboundRequest.getInputStream()).andReturn(mockServletInputStream).anyTimes();
        EasyMock.expect(inboundRequest.getContentLength()).andReturn(mockServletInputStream.available()).anyTimes();
        EasyMock.expect(inboundRequest.getServletContext()).andReturn(new MockServletContext()).anyTimes();

        return inboundRequest;
    }

    private void expectResponseBodyAndHeader(PrintWriter printWriter, HttpServletResponse outboundResponse, Capture<Cookie> capturedArgument) throws Exception {
        outboundResponse.addCookie(capture(capturedArgument));
        EasyMock.expectLastCall();
        EasyMock.expect(outboundResponse.getWriter()).andReturn(printWriter).anyTimes();
        printWriter.write("id:1\nevent:event1\ndata:data1");
        EasyMock.expectLastCall();
        printWriter.write("id:2\nevent:event2\ndata:data2\nretry:1\n:testing");
        EasyMock.expectLastCall();
        printWriter.println('\n');
        EasyMock.expectLastCall().times(2);
    }

    private HaProvider createProvider(String cookieName) throws Exception {
        String serviceName = "HIVE";
        HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
        descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", null, null, null, null, "true", "true", cookieName, null, null, null));
        HaProvider provider = new DefaultHaProvider(descriptor);
        URI uri1 = new URI("http://localhost:" + MOCK_SSE_SERVER.getPort() + "/sse");
        URI uri2 = new URI("http://localhost:" + MOCK_SSE_SERVER.getPort() + "/sse2");
        URI uri3 = new URI("http://localhost:" + MOCK_SSE_SERVER.getPort() + "/sse3");
        ArrayList<String> urlList = new ArrayList<>();
        urlList.add(uri1.toString());
        urlList.add(uri2.toString());
        urlList.add(uri3.toString());
        provider.addHaService(serviceName, urlList);
        return provider;
    }
}
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
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SSEHaDispatchTest {

    private MockServer MOCK_SSE_SERVER;
    private URI URL;
    private final String serviceName = "SSE";

    @Before
    public void setUpBeforeClass() throws Exception {
        MOCK_SSE_SERVER = new MockServer("SSE", true);
        URL = new URI("http://localhost:" + MOCK_SSE_SERVER.getPort() + "/sse");
    }

    @After
    public void cleanUp() throws Exception {
        if (MOCK_SSE_SERVER != null) {
            MOCK_SSE_SERVER.stop();
        }
    }

    @Test
    public void testHADispatchURL() throws Exception {
        String serviceName = "SSE";
        HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
        descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null, null, null));
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
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null, "agentX,user1,agentY", null);
        SSEHaDispatch sseHaDispatch = this.createDispatch(false, haServiceConfig);
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
        assertEquals("KNOX_BACKEND-SSE", capturedArgument.getValue().getName());
    }

    @Test
    public void testNamedStickySessionCookie() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", "COOKIE_NAME", null, "agentX,user1,agentY", null);
        SSEHaDispatch sseHaDispatch = this.createDispatch(false, haServiceConfig);
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
        assertEquals("COOKIE_NAME-SSE", capturedArgument.getValue().getName());
    }

    @Test
    public void testLoadBalancingWithoutStickySessionCookie() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null, "agentX,user1,agentY", null);
        SSEHaDispatch sseHaDispatch = this.createDispatch(false, haServiceConfig);
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        Capture<Cookie> capturedArgument = Capture.newInstance();
        expect(inboundRequest.getHeader("User-Agent")).andReturn("unknown").anyTimes();
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
        assertEquals("http://host2.valid:3333/sse", sseHaDispatch.getHaProvider().getActiveURL("SSE"));
    }

    @Test
    public void testLoadBalancingDisabledWithUserAgent() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null, "agentX,user1,agentY", null);
        SSEHaDispatch sseHaDispatch = this.createDispatch(false, haServiceConfig);
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        expect(inboundRequest.getHeader("User-Agent")).andReturn("user1").anyTimes();
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
        assertEquals("http://localhost:" + MOCK_SSE_SERVER.getPort() + "/sse", sseHaDispatch.getHaProvider().getActiveURL("SSE"));
    }

    @Test
    public void testLoadBalancingDisabledWithStickySession() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null, "agentX,user1,agentY", null);
        SSEHaDispatch sseHaDispatch = this.createDispatch(false, haServiceConfig);
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
        assertEquals("http://host2.valid:3333/sse", sseHaDispatch.getHaProvider().getActiveURL("SSE"));


        //Second request, sticky session cookie included
        CountDownLatch latch2 = new CountDownLatch(1);
        Cookie[] cookies = new Cookie[1];
        cookies[0] = new Cookie(capturedArgument.getValue().getName(), capturedArgument.getValue().getValue());
        PrintWriter printWriter2 = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse2 = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext2 = this.getAsyncContext(latch2, outboundResponse2);
        Capture<Cookie> capturedArgument2 = Capture.newInstance();
        this.expectResponseBodyAndHeader(printWriter2, outboundResponse2, capturedArgument2);
        HttpServletRequest inboundRequest2 = this.getHttpServletRequest(asyncContext2);
        expect(inboundRequest2.getCookies()).andReturn(cookies).anyTimes();
        replay(inboundRequest2, asyncContext2, outboundResponse2, printWriter2);

        MOCK_SSE_SERVER.expect()
                .method("GET")
                .pathInfo("/sse")
                .header("Accept", "text/event-stream")
                .respond()
                .status(HttpStatus.SC_OK)
                .content("id:1\ndata:data1\nevent:event1\n\ndata:data2\nevent:event2\nid:2\nretry:1\n:testing\n\n", StandardCharsets.UTF_8)
                .header("response", "header")
                .contentType("text/event-stream");

        sseHaDispatch.doGet(URL, inboundRequest2, outboundResponse2);
        latch2.await(1L, TimeUnit.SECONDS);

        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter, inboundRequest2);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
        assertEquals("http://host2.valid:3333/sse", sseHaDispatch.getHaProvider().getActiveURL("SSE"));
    }

    @Test
    public void testConnectivityFailover() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null, "agentX,user1,agentY", null);
        SSEHaDispatch sseHaDispatch = this.createDispatch(true, haServiceConfig);
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        Capture<Cookie> capturedArgument = Capture.newInstance();
        expect(inboundRequest.getHeader("User-Agent")).andReturn("unknown").anyTimes();
        expect(inboundRequest.getRequestURL()).andReturn(new StringBuffer(URL.toString())).once();
        expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
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
        sseHaDispatch.doGet(new URI("http://unknown-host.invalid"), inboundRequest, outboundResponse);

        latch.await(2L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
        assertEquals("http://host2.valid:3333/sse", sseHaDispatch.getHaProvider().getActiveURL("SSE"));
    }

    @Test
    public void testNoLoadBalancingStickyFailoverNoFallback() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "false", "true", null, "true", null, null);
        SSEHaDispatch sseHaDispatch = this.createDispatch(true, haServiceConfig);
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        expect(inboundRequest.getHeader("User-Agent")).andReturn("unknown").anyTimes();
        expect(inboundRequest.getRequestURL()).andReturn(new StringBuffer(URL.toString())).once();
        expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
        this.expectResponseBodyAndHeader(printWriter, outboundResponse, null);
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
        sseHaDispatch.doGet(new URI("http://unknown-host.invalid"), inboundRequest, outboundResponse);

        latch.await(2L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
        assertEquals(URL.toString(), sseHaDispatch.getHaProvider().getActiveURL("SSE"));
    }

    @Test
    public void testNoFallbackWhenStickyDisabled() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "false", "false", null, "true", null, null);
        SSEHaDispatch sseHaDispatch = this.createDispatch(true, haServiceConfig);
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        expect(inboundRequest.getHeader("User-Agent")).andReturn("unknown").anyTimes();
        expect(inboundRequest.getRequestURL()).andReturn(new StringBuffer(URL.toString())).once();
        expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
        this.expectResponseBodyAndHeader(printWriter, outboundResponse, null);
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
        sseHaDispatch.doGet(new URI("http://unknown-host.invalid"), inboundRequest, outboundResponse);

        latch.await(2L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
        assertEquals(URL.toString(), sseHaDispatch.getHaProvider().getActiveURL("SSE"));
    }

    @Test
    public void testMaxFailoverLimitReached() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "0", "1000", null, null, "true", "true", null, null, "agentX,user1,agentY", null);
        SSEHaDispatch sseHaDispatch = this.createDispatch(true, haServiceConfig);
        HttpServletResponse outboundResponse = this.getServletResponse(0);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        expect(inboundRequest.getHeader("User-Agent")).andReturn("unknown").anyTimes();
        expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
        outboundResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Service connection error, max failover attempts reached");
        EasyMock.expectLastCall().once();
        this.expectResponseBodyAndHeader(null, outboundResponse, null);
        replay(inboundRequest, asyncContext, outboundResponse);

        MOCK_SSE_SERVER.expect()
                .method("GET")
                .pathInfo("/sse")
                .header("Accept", "text/event-stream")
                .respond()
                .status(HttpStatus.SC_OK)
                .content("id:1\ndata:data1\nevent:event1\n\ndata:data2\nevent:event2\nid:2\nretry:1\n:testing\n\n", StandardCharsets.UTF_8)
                .header("response", "header")
                .contentType("text/event-stream");
        sseHaDispatch.doGet(new URI("http://unknown-host.invalid"), inboundRequest, outboundResponse);

        latch.await(2L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest);
        assertFalse(MOCK_SSE_SERVER.isEmpty());
        assertEquals(URL.toString(), sseHaDispatch.getHaProvider().getActiveURL("SSE"));
    }

    @Test
    public void testGETFailoverIdempotentDisabled() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null, "agentX,user1,agentY", "false");
        SSEHaDispatch sseHaDispatch = this.createDispatch(true, haServiceConfig);
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        Capture<Cookie> capturedArgument = Capture.newInstance();
        expect(inboundRequest.getHeader("User-Agent")).andReturn("unknown").anyTimes();
        expect(inboundRequest.getRequestURL()).andReturn(new StringBuffer(URL.toString())).once();
        expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
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
        sseHaDispatch.doGet(new URI("http://unknown-host.invalid"), inboundRequest, outboundResponse);

        latch.await(2L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
        assertEquals("http://host2.valid:3333/sse", sseHaDispatch.getHaProvider().getActiveURL("SSE"));
    }

    @Test
    public void testPOSTFailoverIdempotentEnabled() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null, "agentX,user1,agentY", "true");
        SSEHaDispatch sseHaDispatch = this.createDispatch(true, haServiceConfig);
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        Capture<Cookie> capturedArgument = Capture.newInstance();
        expect(inboundRequest.getHeader("User-Agent")).andReturn("unknown").anyTimes();
        expect(inboundRequest.getRequestURL()).andReturn(new StringBuffer(URL.toString())).once();
        expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
        this.expectResponseBodyAndHeader(printWriter, outboundResponse, capturedArgument);
        replay(inboundRequest, asyncContext, outboundResponse, printWriter);

        MOCK_SSE_SERVER.expect()
                .method("POST")
                .pathInfo("/sse")
                .header("Accept", "text/event-stream")
                .respond()
                .status(HttpStatus.SC_OK)
                .content("id:1\ndata:data1\nevent:event1\n\ndata:data2\nevent:event2\nid:2\nretry:1\n:testing\n\n", StandardCharsets.UTF_8)
                .header("response", "header")
                .contentType("text/event-stream");
        sseHaDispatch.doPost(new URI("http://unknown-host.invalid"), inboundRequest, outboundResponse);

        latch.await(2L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
        assertEquals("http://host2.valid:3333/sse", sseHaDispatch.getHaProvider().getActiveURL("SSE"));
    }

    @Test
    public void testPOSTConnectErrorFailoverIdempotentDisabled() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null, "agentX,user1,agentY", "false");
        SSEHaDispatch sseHaDispatch = this.createDispatch(true, haServiceConfig);
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        Capture<Cookie> capturedArgument = Capture.newInstance();
        expect(inboundRequest.getHeader("User-Agent")).andReturn("unknown").anyTimes();
        expect(inboundRequest.getRequestURL()).andReturn(new StringBuffer(URL.toString())).once();
        expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
        this.expectResponseBodyAndHeader(printWriter, outboundResponse, capturedArgument);
        replay(inboundRequest, asyncContext, outboundResponse, printWriter);

        MOCK_SSE_SERVER.expect()
                .method("POST")
                .pathInfo("/sse")
                .header("Accept", "text/event-stream")
                .respond()
                .status(HttpStatus.SC_OK)
                .content("id:1\ndata:data1\nevent:event1\n\ndata:data2\nevent:event2\nid:2\nretry:1\n:testing\n\n", StandardCharsets.UTF_8)
                .header("response", "header")
                .contentType("text/event-stream");
        sseHaDispatch.doPost(new URI("http://unknown-host.invalid"), inboundRequest, outboundResponse);

        latch.await(2L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
        assertEquals("http://host2.valid:3333/sse", sseHaDispatch.getHaProvider().getActiveURL("SSE"));
    }

    @Test
    public void testFailoverDisabledWithStickySession() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null, "agentX,user1,agentY", null);
        SSEHaDispatch sseHaDispatch = this.createDispatch(true, haServiceConfig);
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        Capture<Cookie> capturedArgument = Capture.newInstance();
        expect(inboundRequest.getHeader("User-Agent")).andReturn("unknown").anyTimes();
        expect(inboundRequest.getRequestURL()).andReturn(new StringBuffer(URL.toString())).once();
        expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
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
        sseHaDispatch.doGet(new URI("http://unknown-host.invalid"), inboundRequest, outboundResponse);

        latch.await(2L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
        assertEquals("http://host2.valid:3333/sse", sseHaDispatch.getHaProvider().getActiveURL("SSE"));

        //Second request, sticky session cookie included
        MOCK_SSE_SERVER.stop();
        CountDownLatch latch2 = new CountDownLatch(1);
        Cookie[] cookies = new Cookie[1];
        cookies[0] = new Cookie(capturedArgument.getValue().getName(), capturedArgument.getValue().getValue());
        PrintWriter printWriter2 = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse2 = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext2 = this.getAsyncContext(latch2, outboundResponse2);
        Capture<Cookie> capturedArgument2 = Capture.newInstance();
        this.expectResponseBodyAndHeader(printWriter2, outboundResponse2, capturedArgument2);
        HttpServletRequest inboundRequest2 = this.getHttpServletRequest(asyncContext2);
        expect(inboundRequest2.getCookies()).andReturn(cookies).anyTimes();
        replay(inboundRequest2, asyncContext2, outboundResponse2, printWriter2);

        sseHaDispatch.doGet(URL, inboundRequest2, outboundResponse2);
        latch2.await(1L, TimeUnit.SECONDS);

        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter, inboundRequest2);
        assertEquals("http://host2.valid:3333/sse", sseHaDispatch.getHaProvider().getActiveURL("SSE"));
    }

    @Test
    public void testFailoverWithUserAgentLBDisabled() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null, "agentX,user1,agentY", null);
        SSEHaDispatch sseHaDispatch = this.createDispatch(true, haServiceConfig);
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        Capture<Cookie> capturedArgument = Capture.newInstance();
        expect(inboundRequest.getHeader("User-Agent")).andReturn("agentX").anyTimes();
        expect(inboundRequest.getRequestURL()).andReturn(new StringBuffer(URL.toString())).once();
        expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
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
        sseHaDispatch.doGet(new URI("http://unknown-host.invalid"), inboundRequest, outboundResponse);

        latch.await(2L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
        assertEquals(URL.toString(), sseHaDispatch.getHaProvider().getActiveURL("SSE"));
    }

    @Test
    public void testNoFailoverAfterResponseIsCommitted() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HaServiceConfig haServiceConfig = HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null, "agentX,user1,agentY", null);
        SSEHaDispatch sseHaDispatch = this.createDispatch(false, haServiceConfig);
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);
        Capture<Cookie> capturedArgument = Capture.newInstance();
        expect(inboundRequest.getHeader("User-Agent")).andReturn("unknown").anyTimes();
        expect(outboundResponse.isCommitted()).andReturn(true).anyTimes();
        this.expectResponseBodyAndHeaderWithError(printWriter, outboundResponse, capturedArgument);
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

        latch.await(2L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
        assertEquals("http://host2.valid:3333/sse", sseHaDispatch.getHaProvider().getActiveURL("SSE"));
    }

    private SSEHaDispatch createDispatch(boolean failoverNeeded, HaServiceConfig serviceConfig) throws Exception {
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

        HaProvider provider = this.createProvider(failoverNeeded, serviceConfig);

        replay(keystoreService, gatewayConfig, gatewayServices, servletContext, filterConfig);

        SSEHaDispatch dispatch = new SSEHaDispatch(filterConfig);
        dispatch.setHaProvider(provider);
        dispatch.setServiceRole("SSE");
        dispatch.init();

        return dispatch;
    }

    private HttpServletResponse getServletResponse(int statusCode) {
        HttpServletResponse outboundResponse = EasyMock.createNiceMock(HttpServletResponse.class);

        if (statusCode != 0) {
            outboundResponse.setStatus(statusCode);
            EasyMock.expectLastCall();
        }

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

    private void expectResponseBodyAndHeader(PrintWriter printWriter, HttpServletResponse outboundResponse,
                                             Capture<Cookie> capturedArgument) throws Exception {
        if (capturedArgument != null) {
            outboundResponse.addCookie(capture(capturedArgument));
            EasyMock.expectLastCall();
        }

        if (printWriter != null) {
            EasyMock.expect(outboundResponse.getWriter()).andReturn(printWriter).anyTimes();
            printWriter.write("id:1\nevent:event1\ndata:data1");
            EasyMock.expectLastCall();
            printWriter.write("id:2\nevent:event2\ndata:data2\nretry:1\n:testing");
            EasyMock.expectLastCall();
            printWriter.println('\n');
            EasyMock.expectLastCall().times(2);
        }
    }

    private void expectResponseBodyAndHeaderWithError(PrintWriter printWriter, HttpServletResponse outboundResponse,
                                             Capture<Cookie> capturedArgument) throws Exception {
        if (capturedArgument != null) {
            outboundResponse.addCookie(capture(capturedArgument));
            EasyMock.expectLastCall();
        }

        EasyMock.expect(outboundResponse.getWriter()).andReturn(printWriter).anyTimes();
        printWriter.write("id:1\nevent:event1\ndata:data1");
        EasyMock.expectLastCall();
        printWriter.write("id:2\nevent:event2\ndata:data2\nretry:1\n:testing");
        EasyMock.expectLastCall().andThrow(new IndexOutOfBoundsException("Custom exception"));
    }

    private HaProvider createProvider(boolean failoverNeeded, HaServiceConfig serviceConfig) throws Exception {
        HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
        descriptor.addServiceConfig(serviceConfig);
        HaProvider provider = new DefaultHaProvider(descriptor);
        URI uri1 = new URI("http://localhost:" + MOCK_SSE_SERVER.getPort() + "/sse");
        URI uri2 = new URI("http://host2.valid:3333/sse");
        URI uri3 = new URI("http://host3.valid:3333/sse");
        ArrayList<String> urlList = new ArrayList<>();
        if (failoverNeeded) {
            urlList.add(new URI("http://unknown-host.invalid").toString());
        }
        urlList.add(uri1.toString());
        urlList.add(uri2.toString());
        urlList.add(uri3.toString());
        provider.addHaService(serviceName, urlList);
        return provider;
    }

}

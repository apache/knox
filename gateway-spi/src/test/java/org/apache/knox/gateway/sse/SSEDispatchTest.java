/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.sse;

import org.apache.http.HttpStatus;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.test.mock.MockServer;
import org.apache.knox.test.mock.MockServletContext;
import org.apache.knox.test.mock.MockServletInputStream;
import org.easymock.EasyMock;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.servlet.AsyncContext;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SSEDispatchTest {

    private static MockServer MOCK_SSE_SERVER;
    private static URI URL;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        MOCK_SSE_SERVER = new MockServer("SSE", true);
        URL = new URI("http://localhost:" + MOCK_SSE_SERVER.getPort() + "/sse");
    }

    @Test
    public void testCreateAndDestroyClient() throws Exception {
        SSEDispatch sseDispatch = this.createDispatch();
        assertNotNull(sseDispatch.getAsyncClient());

        sseDispatch.destroy();
        assertFalse(((CloseableHttpAsyncClient) sseDispatch.getAsyncClient()).isRunning());
    }

    @Test
    public void testGet2xx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEDispatch sseDispatch = this.createDispatch();
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);

        this.expectResponseBodyAndHeader(printWriter, outboundResponse);
        replay(inboundRequest, asyncContext, outboundResponse, printWriter);

        MOCK_SSE_SERVER.expect()
                .method("GET")
                .pathInfo("/sse")
                .header("request", "header")
                .header("Accept", "text/event-stream")
                .respond()
                .status(HttpStatus.SC_OK)
                .content("id:1\ndata:data1\nevent:event1\n\ndata:data2\nevent:event2\nid:2\nretry:1\n:testing\n\n", StandardCharsets.UTF_8)
                .header("response", "header")
                .contentType("text/event-stream");

        sseDispatch.doGet(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
    }

    @Test
    public void testGet4xx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEDispatch sseDispatch = this.createDispatch();
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_BAD_REQUEST);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);

        replay(inboundRequest, asyncContext, outboundResponse);

        MOCK_SSE_SERVER.expect()
                .method("GET")
                .pathInfo("/sse")
                .respond()
                .status(HttpStatus.SC_BAD_REQUEST);

        sseDispatch.doGet(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
    }

    @Test
    public void testGet5xx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEDispatch sseDispatch = this.createDispatch();
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);

        replay(inboundRequest, asyncContext, outboundResponse);

        MOCK_SSE_SERVER.expect()
                .method("GET")
                .pathInfo("/sse")
                .respond()
                .status(HttpStatus.SC_INTERNAL_SERVER_ERROR);

        sseDispatch.doGet(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
    }

    @Test
    public void testPost2xx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEDispatch sseDispatch = this.createDispatch();
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);

        this.expectResponseBodyAndHeader(printWriter, outboundResponse);
        replay(inboundRequest, asyncContext, outboundResponse, printWriter);

        MOCK_SSE_SERVER.expect()
                .method("POST")
                .pathInfo("/sse")
                .header("request", "header")
                .header("Accept", "text/event-stream")
                .content("{\"request\":\"body\"}", StandardCharsets.UTF_8)
                .respond()
                .status(HttpStatus.SC_OK)
                .content("id:1\ndata:data1\nevent:event1\n\ndata:data2\nevent:event2\nid:2\nretry:1\n:testing\n\n", StandardCharsets.UTF_8)
                .header("response", "header")
                .contentType("text/event-stream");

        sseDispatch.doPost(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
    }

    @Test
    public void testPost4xx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEDispatch sseDispatch = this.createDispatch();
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_NOT_FOUND);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);

        replay(inboundRequest, asyncContext, outboundResponse);

        MOCK_SSE_SERVER.expect()
                .method("POST")
                .pathInfo("/sse")
                .respond()
                .status(HttpStatus.SC_NOT_FOUND);

        sseDispatch.doPost(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
    }

    @Test
    public void testPost5xx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEDispatch sseDispatch = this.createDispatch();
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);

        replay(inboundRequest, asyncContext, outboundResponse);

        MOCK_SSE_SERVER.expect()
                .method("POST")
                .pathInfo("/sse")
                .respond()
                .status(HttpStatus.SC_INTERNAL_SERVER_ERROR);

        sseDispatch.doPost(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
    }

    @Test
    public void testPut2xx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEDispatch sseDispatch = this.createDispatch();
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);

        this.expectResponseBodyAndHeader(printWriter, outboundResponse);
        replay(inboundRequest, asyncContext, outboundResponse, printWriter);

        MOCK_SSE_SERVER.expect()
                .method("PUT")
                .pathInfo("/sse")
                .header("request", "header")
                .header("Accept", "text/event-stream")
                .content("{\"request\":\"body\"}", StandardCharsets.UTF_8)
                .respond()
                .status(HttpStatus.SC_OK)
                .content("id:1\ndata:data1\nevent:event1\n\ndata:data2\nevent:event2\nid:2\nretry:1\n:testing\n\n", StandardCharsets.UTF_8)
                .header("response", "header")
                .contentType("text/event-stream");

        sseDispatch.doPut(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
    }

    @Test
    public void testPut4xx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEDispatch sseDispatch = this.createDispatch();
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_NOT_FOUND);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);

        replay(inboundRequest, asyncContext, outboundResponse);

        MOCK_SSE_SERVER.expect()
                .method("PUT")
                .pathInfo("/sse")
                .respond()
                .status(HttpStatus.SC_NOT_FOUND);

        sseDispatch.doPut(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
    }

    @Test
    public void testPut5xx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEDispatch sseDispatch = this.createDispatch();
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);

        replay(inboundRequest, asyncContext, outboundResponse);

        MOCK_SSE_SERVER.expect()
                .method("PUT")
                .pathInfo("/sse")
                .respond()
                .status(HttpStatus.SC_INTERNAL_SERVER_ERROR);

        sseDispatch.doPut(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
    }

    @Test
    public void testPatch2xx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEDispatch sseDispatch = this.createDispatch();
        PrintWriter printWriter = EasyMock.createNiceMock(PrintWriter.class);
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_OK);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);

        this.expectResponseBodyAndHeader(printWriter, outboundResponse);
        replay(inboundRequest, asyncContext, outboundResponse, printWriter);

        MOCK_SSE_SERVER.expect()
                .method("PATCH")
                .pathInfo("/sse")
                .header("request", "header")
                .header("Accept", "text/event-stream")
                .content("{\"request\":\"body\"}", StandardCharsets.UTF_8)
                .respond()
                .status(HttpStatus.SC_OK)
                .content("id:1\ndata:data1\nevent:event1\n\ndata:data2\nevent:event2\nid:2\nretry:1\n:testing\n\n", StandardCharsets.UTF_8)
                .header("response", "header")
                .contentType("text/event-stream");

        sseDispatch.doPatch(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest, printWriter);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
    }

    @Test
    public void testPatch4xx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEDispatch sseDispatch = this.createDispatch();
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_NOT_FOUND);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);

        replay(inboundRequest, asyncContext, outboundResponse);

        MOCK_SSE_SERVER.expect()
                .method("PATCH")
                .pathInfo("/sse")
                .respond()
                .status(HttpStatus.SC_NOT_FOUND);

        sseDispatch.doPatch(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
    }

    @Test
    public void testPatch5xx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEDispatch sseDispatch = this.createDispatch();
        HttpServletResponse outboundResponse = this.getServletResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);

        replay(inboundRequest, asyncContext, outboundResponse);

        MOCK_SSE_SERVER.expect()
                .method("PATCH")
                .pathInfo("/sse")
                .respond()
                .status(HttpStatus.SC_INTERNAL_SERVER_ERROR);

        sseDispatch.doPatch(URL, inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest);
        assertTrue(MOCK_SSE_SERVER.isEmpty());
    }

    @Test
    public void testServerNotAvailable() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SSEDispatch sseDispatch = this.createDispatch();
        HttpServletResponse outboundResponse = EasyMock.createNiceMock(HttpServletResponse.class);
        AsyncContext asyncContext = this.getAsyncContext(latch, outboundResponse);
        HttpServletRequest inboundRequest = this.getHttpServletRequest(asyncContext);

        replay(inboundRequest, asyncContext, outboundResponse);

        sseDispatch.doGet(new URI("http://localhost:11223/sse"), inboundRequest, outboundResponse);

        latch.await(1L, TimeUnit.SECONDS);
        EasyMock.verify(asyncContext, outboundResponse, inboundRequest);
    }

    private HttpServletRequest getHttpServletRequest(AsyncContext asyncContext) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("request", "header");
        InputStream stream = new ByteArrayInputStream("{\"request\":\"body\"}".getBytes(StandardCharsets.UTF_8));
        MockServletInputStream mockServletInputStream = new MockServletInputStream(stream);
        HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);

        EasyMock.expect(inboundRequest.getHeaderNames()).andReturn(Collections.enumeration(headers.keySet())).anyTimes();
        EasyMock.expect(inboundRequest.startAsync()).andReturn(asyncContext).once();
        EasyMock.expect(inboundRequest.getHeader("request")).andReturn("header").once();
        EasyMock.expect(inboundRequest.getContentType()).andReturn("application/json").anyTimes();
        EasyMock.expect(inboundRequest.getInputStream()).andReturn(mockServletInputStream).anyTimes();
        EasyMock.expect(inboundRequest.getContentLength()).andReturn(mockServletInputStream.available()).anyTimes();
        EasyMock.expect(inboundRequest.getServletContext()).andReturn(new MockServletContext()).anyTimes();

        return inboundRequest;
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

    private HttpServletResponse getServletResponse(int statusCode) {
        HttpServletResponse outboundResponse = EasyMock.createNiceMock(HttpServletResponse.class);

        outboundResponse.setStatus(statusCode);
        EasyMock.expectLastCall();

        return outboundResponse;
    }

    private void expectResponseBodyAndHeader(PrintWriter printWriter, HttpServletResponse outboundResponse) throws Exception {
        outboundResponse.addHeader("response", "header");
        EasyMock.expectLastCall();
        EasyMock.expect(outboundResponse.getWriter()).andReturn(printWriter).anyTimes();
        printWriter.write("id:1\nevent:event1\ndata:data1");
        EasyMock.expectLastCall();
        printWriter.write("id:2\nevent:event2\ndata:data2\nretry:1\n:testing");
        EasyMock.expectLastCall();
        printWriter.println('\n');
        EasyMock.expectLastCall().times(2);
    }

    private SSEDispatch createDispatch() throws Exception {
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

        return new SSEDispatch(filterConfig);
    }
}
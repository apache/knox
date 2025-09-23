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

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHeader;
import org.easymock.Capture;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NiFiResponseUtilTest {

    @Test
    public void testModifyOutboundResponse_302WithLocationHeader() throws IOException {
        HttpServletRequest inboundRequest = createMock(HttpServletRequest.class);
        HttpServletResponse outboundResponse = createMock(HttpServletResponse.class);
        HttpResponse inboundResponse = createMock(HttpResponse.class);
        StatusLine statusLine = createMock(StatusLine.class);
        Capture<String> locationCapture = Capture.newInstance();

        setupCommonMocks(inboundRequest, inboundResponse, statusLine,
                "http://backend:8080/nifi/api/flow/status",
                "/nifi/api/flow/status");

        outboundResponse.setHeader(eq("Location"), capture(locationCapture));
        expectLastCall();

        replay(inboundRequest, outboundResponse, inboundResponse, statusLine);
        NiFiResponseUtil.modifyOutboundResponse(inboundRequest, outboundResponse, inboundResponse);
        verify(inboundRequest, outboundResponse, inboundResponse, statusLine);
        assertEquals("URL should be properly transformed",
                "https://knox.proxy:8443/gateway/default/nifi/api/flow/status",
                locationCapture.getValue());
    }

    @Test
    public void testModifyOutboundResponse_302WithTrailingSlash() throws IOException {
        HttpServletRequest inboundRequest = createMock(HttpServletRequest.class);
        HttpServletResponse outboundResponse = createMock(HttpServletResponse.class);
        HttpResponse inboundResponse = createMock(HttpResponse.class);
        StatusLine statusLine = createMock(StatusLine.class);
        Capture<String> locationCapture = Capture.newInstance();

        setupCommonMocks(inboundRequest, inboundResponse, statusLine,
                "http://backend:8080/nifi/api/flow/status",
                "/nifi/api/flow/");

        outboundResponse.setHeader(eq("Location"), capture(locationCapture));
        expectLastCall();

        replay(inboundRequest, outboundResponse, inboundResponse, statusLine);
        NiFiResponseUtil.modifyOutboundResponse(inboundRequest, outboundResponse, inboundResponse);
        verify(inboundRequest, outboundResponse, inboundResponse, statusLine);
        assertEquals("URL should preserve trailing slash",
                "https://knox.proxy:8443/gateway/default/nifi/api/flow/",
                locationCapture.getValue());
    }

    @Test
    public void testModifyOutboundResponse_WithQueryParams() throws IOException {
        HttpServletRequest inboundRequest = createMock(HttpServletRequest.class);
        HttpServletResponse outboundResponse = createMock(HttpServletResponse.class);
        HttpResponse inboundResponse = createMock(HttpResponse.class);
        StatusLine statusLine = createMock(StatusLine.class);

        Capture<String> locationCapture = Capture.newInstance();

        setupCommonMocks(inboundRequest, inboundResponse, statusLine,
                "http://backend:8080/nifi/api/flow/status?param1=value1&param2=value2",
                "/nifi/api/flow/status");

        outboundResponse.setHeader(eq("Location"), capture(locationCapture));
        expectLastCall();
        replay(inboundRequest, outboundResponse, inboundResponse, statusLine);
        NiFiResponseUtil.modifyOutboundResponse(inboundRequest, outboundResponse, inboundResponse);
        verify(inboundRequest, outboundResponse, inboundResponse, statusLine);
        String transformedUrl = locationCapture.getValue();
        assertTrue("URL should preserve query parameters", transformedUrl.contains("param1=value1"));
        assertTrue("URL should preserve query parameters", transformedUrl.contains("param2=value2"));
        assertTrue("URL should have correct base path",
                transformedUrl.startsWith("https://knox.proxy:8443/gateway/default/nifi/api/flow/status"));
    }

    @Test
    public void testModifyOutboundResponse_WithFragment() throws IOException {
        HttpServletRequest inboundRequest = createMock(HttpServletRequest.class);
        HttpServletResponse outboundResponse = createMock(HttpServletResponse.class);
        HttpResponse inboundResponse = createMock(HttpResponse.class);
        StatusLine statusLine = createMock(StatusLine.class);

        Capture<String> locationCapture = Capture.newInstance();

        setupCommonMocks(inboundRequest, inboundResponse, statusLine,
                "http://backend:8080/nifi/api/flow/status#section1",
                "/nifi/api/flow/status");

        outboundResponse.setHeader(eq("Location"), capture(locationCapture));
        expectLastCall();

        replay(inboundRequest, outboundResponse, inboundResponse, statusLine);

        NiFiResponseUtil.modifyOutboundResponse(inboundRequest, outboundResponse, inboundResponse);

        verify(inboundRequest, outboundResponse, inboundResponse, statusLine);
        assertEquals("URL should preserve fragment",
                "https://knox.proxy:8443/gateway/default/nifi/api/flow/status#section1",
                locationCapture.getValue());
    }

    @Test
    public void testModifyOutboundResponse_WithQueryParamsAndFragment() throws IOException {
        HttpServletRequest inboundRequest = createMock(HttpServletRequest.class);
        HttpServletResponse outboundResponse = createMock(HttpServletResponse.class);
        HttpResponse inboundResponse = createMock(HttpResponse.class);
        StatusLine statusLine = createMock(StatusLine.class);
        Capture<String> locationCapture = Capture.newInstance();

        setupCommonMocks(inboundRequest, inboundResponse, statusLine,
                "http://backend:8080/nifi/api/flow/status?param1=value1&param2=value2#section1",
                "/nifi/api/flow/status");

        outboundResponse.setHeader(eq("Location"), capture(locationCapture));
        expectLastCall();

        replay(inboundRequest, outboundResponse, inboundResponse, statusLine);
        NiFiResponseUtil.modifyOutboundResponse(inboundRequest, outboundResponse, inboundResponse);
        verify(inboundRequest, outboundResponse, inboundResponse, statusLine);

        String transformedUrl = locationCapture.getValue();
        assertTrue("URL should preserve query parameters", transformedUrl.contains("param1=value1"));
        assertTrue("URL should preserve query parameters", transformedUrl.contains("param2=value2"));
        assertTrue("URL should preserve fragment", transformedUrl.endsWith("#section1"));
        assertTrue("URL should have correct base path",
                transformedUrl.startsWith("https://knox.proxy:8443/gateway/default/nifi/api/flow/status"));
    }

    @Test
    public void testModifyOutboundResponse_Non302Status() throws IOException {
        HttpServletRequest inboundRequest = createMock(HttpServletRequest.class);
        HttpServletResponse outboundResponse = createMock(HttpServletResponse.class);
        HttpResponse inboundResponse = createMock(HttpResponse.class);
        StatusLine statusLine = createMock(StatusLine.class);

        expect(inboundResponse.getStatusLine()).andReturn(statusLine);
        expect(statusLine.getStatusCode()).andReturn(HttpServletResponse.SC_OK);

        replay(inboundRequest, outboundResponse, inboundResponse, statusLine);
        NiFiResponseUtil.modifyOutboundResponse(inboundRequest, outboundResponse, inboundResponse);
        verify(inboundRequest, outboundResponse, inboundResponse, statusLine);
    }

    @Test(expected = RuntimeException.class)
    public void testModifyOutboundResponse_302WithoutLocationHeader() throws IOException {
        HttpServletRequest inboundRequest = createMock(HttpServletRequest.class);
        HttpServletResponse outboundResponse = createMock(HttpServletResponse.class);
        HttpResponse inboundResponse = createMock(HttpResponse.class);
        StatusLine statusLine = createMock(StatusLine.class);

        expect(inboundResponse.getStatusLine()).andReturn(statusLine);
        expect(statusLine.getStatusCode()).andReturn(HttpServletResponse.SC_FOUND);
        expect(inboundResponse.getFirstHeader("Location")).andReturn(null);

        replay(inboundRequest, outboundResponse, inboundResponse, statusLine);

        NiFiResponseUtil.modifyOutboundResponse(inboundRequest, outboundResponse, inboundResponse);
    }

    @Test
    public void testModifyOutboundResponse_LocationNotContainingRequestPath() throws IOException {
        HttpServletRequest inboundRequest = createMock(HttpServletRequest.class);
        HttpServletResponse outboundResponse = createMock(HttpServletResponse.class);
        HttpResponse inboundResponse = createMock(HttpResponse.class);
        StatusLine statusLine = createMock(StatusLine.class);
        Header locationHeader = new BasicHeader("Location", "http://different.server.com/other/path");

        expect(inboundResponse.getStatusLine()).andReturn(statusLine);
        expect(statusLine.getStatusCode()).andReturn(HttpServletResponse.SC_FOUND);
        expect(inboundResponse.getFirstHeader("Location")).andReturn(locationHeader);
        expect(inboundRequest.getRequestURI()).andReturn("/nifi/api/flow/status");

        replay(inboundRequest, outboundResponse, inboundResponse, statusLine);
        NiFiResponseUtil.modifyOutboundResponse(inboundRequest, outboundResponse, inboundResponse);
        verify(inboundRequest, outboundResponse, inboundResponse, statusLine);
    }

    private void setupCommonMocks(
            HttpServletRequest inboundRequest,
            HttpResponse inboundResponse,
            StatusLine statusLine,
            String locationUrl,
            String requestUri) {

        expect(inboundResponse.getStatusLine()).andReturn(statusLine);
        expect(statusLine.getStatusCode()).andReturn(HttpServletResponse.SC_FOUND);
        expect(inboundResponse.getFirstHeader("Location")).andReturn(
                new BasicHeader("Location", locationUrl));
        expect(inboundRequest.getRequestURI()).andReturn(requestUri);

        expect(inboundRequest.getHeader(NiFiHeaders.X_FORWARDED_PROTO)).andReturn("https");
        expect(inboundRequest.getHeader(NiFiHeaders.X_FORWARDED_HOST)).andReturn("knox.proxy");
        expect(inboundRequest.getHeader(NiFiHeaders.X_FORWARDED_PORT)).andReturn("8443");
        expect(inboundRequest.getHeader(NiFiHeaders.X_FORWARDED_CONTEXT)).andReturn("/gateway/default");
        expect(inboundRequest.getPathInfo()).andReturn(requestUri);
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link LivyHaDispatch}.
 * Covers resolveTargetBackend(), findMatchingBackend(), extractHostname(),
 * extractHostParam(), and removeHostParam().
 */
public class LivyHaDispatchTest {

    private static final String BACKEND_1 = "https://host-1.example.com:28998";
    private static final String BACKEND_4 = "https://host-4.example.com:28998";
    private static final String BACKEND_8 = "https://host-8.example.com:28998";

    private static final List<String> ALL_BACKENDS = Arrays.asList(BACKEND_1, BACKEND_4, BACKEND_8);

    private TestLivyHaDispatch dispatch;

    /**
     * Testable subclass that overrides getBackendUrls() to inject
     * backend URLs without needing real HA provider initialization.
     * This avoids any dependency on FilterConfig, ServletContext, or HaProvider.
     */
    private static class TestLivyHaDispatch extends LivyHaDispatch {

        private final List<String> testBackendUrls;

        TestLivyHaDispatch(List<String> backendUrls) {
            this.testBackendUrls = backendUrls;
        }

        @Override
        protected List<String> getBackendUrls() {
            return testBackendUrls;
        }
    }

    @Before
    public void setUp() {
        dispatch = new TestLivyHaDispatch(ALL_BACKENDS);
    }

    private static HttpServletRequest mockRequest(String hostParam, String referer, Cookie[] cookies) {
        HttpServletRequest request = EasyMock.createMock(HttpServletRequest.class);
        EasyMock.expect(request.getParameter(LivyHaDispatch.HOST_PARAM)).andReturn(hostParam).anyTimes();
        EasyMock.expect(request.getHeader("Referer")).andReturn(referer).anyTimes();
        EasyMock.expect(request.getCookies()).andReturn(cookies).anyTimes();
        EasyMock.replay(request);
        return request;
    }

    // ==================== resolveTargetBackend() Tests ====================

    @Test
    public void testResolveTargetBackend_HostParam_FullUrl() {
        HttpServletRequest request = mockRequest(BACKEND_4, null, null);
        assertEquals(BACKEND_4, dispatch.resolveTargetBackend(request));
    }

    @Test
    public void testResolveTargetBackend_HostParam_Backend8() {
        HttpServletRequest request = mockRequest(BACKEND_8, null, null);
        assertEquals(BACKEND_8, dispatch.resolveTargetBackend(request));
    }

    @Test
    public void testResolveTargetBackend_HostParam_NoMatch() {
        HttpServletRequest request = mockRequest("https://unknown-host.domain.com:28998", null, null);
        assertNull(dispatch.resolveTargetBackend(request));
    }

    @Test
    public void testResolveTargetBackend_Referer_WithHostParam() {
        HttpServletRequest request = mockRequest(null,
                "https://knox-gw:8443/gateway/cdp-proxy/livy_for_spark3/ui?host=" + BACKEND_4, null);
        assertEquals(BACKEND_4, dispatch.resolveTargetBackend(request));
    }

    @Test
    public void testResolveTargetBackend_Referer_WithoutHostParam() {
        HttpServletRequest request = mockRequest(null,
                "https://knox-gw:8443/gateway/cdp-proxy/livy_for_spark3/sessions", null);
        assertNull(dispatch.resolveTargetBackend(request));
    }

    @Test
    public void testResolveTargetBackend_Cookie() {
        Cookie backendCookie = new Cookie(LivyHaDispatch.BACKEND_COOKIE_NAME, BACKEND_8);
        HttpServletRequest request = mockRequest(null, null, new Cookie[]{backendCookie});
        assertEquals(BACKEND_8, dispatch.resolveTargetBackend(request));
    }

    @Test
    public void testResolveTargetBackend_Cookie_NoMatch() {
        Cookie backendCookie = new Cookie(LivyHaDispatch.BACKEND_COOKIE_NAME, "https://invalid:28998");
        HttpServletRequest request = mockRequest(null, null, new Cookie[]{backendCookie});
        assertNull(dispatch.resolveTargetBackend(request));
    }

    @Test
    public void testResolveTargetBackend_Priority_HostParamOverReferer() {
        HttpServletRequest request = mockRequest(BACKEND_1,
                "https://knox-gw:8443/path?host=" + BACKEND_4, null);
        assertEquals(BACKEND_1, dispatch.resolveTargetBackend(request));
    }

    @Test
    public void testResolveTargetBackend_Priority_RefererOverCookie() {
        Cookie cookie = new Cookie(LivyHaDispatch.BACKEND_COOKIE_NAME, BACKEND_8);
        HttpServletRequest request = mockRequest(null,
                "https://knox-gw:8443/path?host=" + BACKEND_4, new Cookie[]{cookie});
        assertEquals(BACKEND_4, dispatch.resolveTargetBackend(request));
    }

    @Test
    public void testResolveTargetBackend_NullEverything() {
        HttpServletRequest request = mockRequest(null, null, null);
        assertNull(dispatch.resolveTargetBackend(request));
    }

    @Test
    public void testResolveTargetBackend_EmptyHostParam() {
        HttpServletRequest request = mockRequest("", null, null);
        assertNull(dispatch.resolveTargetBackend(request));
    }

    // ==================== findMatchingBackend() Tests ====================

    @Test
    public void testFindMatchingBackend_ExactFqdnMatch() {
        assertEquals(BACKEND_4, dispatch.findMatchingBackend(BACKEND_4));
    }

    @Test
    public void testFindMatchingBackend_AllThreeBackends() {
        assertEquals(BACKEND_1, dispatch.findMatchingBackend(BACKEND_1));
        assertEquals(BACKEND_4, dispatch.findMatchingBackend(BACKEND_4));
        assertEquals(BACKEND_8, dispatch.findMatchingBackend(BACKEND_8));
    }

    @Test
    public void testFindMatchingBackend_ShortHostnameMatch() {
        TestLivyHaDispatch shortDispatch = new TestLivyHaDispatch(
                Arrays.asList(
                        "https://host-1.domain.com:28998",
                        "https://host-4.domain.com:28998"
                )
        );
        String result = shortDispatch.findMatchingBackend("https://host-4.other.domain:28998");
        assertEquals("https://host-4.domain.com:28998", result);
    }

    @Test
    public void testFindMatchingBackend_CaseInsensitive() {
        assertEquals(BACKEND_4, dispatch.findMatchingBackend(
                "https://HOST-4.example.com:28998"));
    }

    @Test
    public void testFindMatchingBackend_NullInput() {
        assertNull(dispatch.findMatchingBackend(null));
    }

    @Test
    public void testFindMatchingBackend_EmptyInput() {
        assertNull(dispatch.findMatchingBackend(""));
    }

    @Test
    public void testFindMatchingBackend_NoBackendsConfigured() {
        TestLivyHaDispatch emptyDispatch = new TestLivyHaDispatch(Collections.emptyList());
        assertNull(emptyDispatch.findMatchingBackend(BACKEND_4));
    }

    @Test
    public void testFindMatchingBackend_DifferentPort() {
        assertEquals(BACKEND_4, dispatch.findMatchingBackend(
                "https://host-4.example.com:9999"));
    }

    @Test
    public void testFindMatchingBackend_HttpVsHttps() {
        assertEquals(BACKEND_4, dispatch.findMatchingBackend(
                "http://host-4.example.com:28998"));
    }

    // ==================== extractHostname() Tests ====================

    @Test
    public void testExtractHostname_FullUrl() {
        assertEquals("host-4.example.com",
                LivyHaDispatch.extractHostname("https://host-4.example.com:28998"));
    }

    @Test
    public void testExtractHostname_NoScheme() {
        assertEquals("host-4.domain.com",
                LivyHaDispatch.extractHostname("host-4.domain.com:28998"));
    }

    @Test
    public void testExtractHostname_PlainHostname() {
        assertEquals("host-4",
                LivyHaDispatch.extractHostname("host-4"));
    }

    @Test
    public void testExtractHostname_WithPath() {
        assertEquals("host-1.domain.com",
                LivyHaDispatch.extractHostname("https://host-1.domain.com:28998/sessions"));
    }

    @Test
    public void testExtractHostname_Null() {
        assertNull(LivyHaDispatch.extractHostname(null));
    }

    @Test
    public void testExtractHostname_Empty() {
        assertNull(LivyHaDispatch.extractHostname(""));
    }

    // ==================== extractHostParam() Tests ====================

    @Test
    public void testExtractHostParam_FromFullUrl() {
        assertEquals("https://host-4:28998",
                LivyHaDispatch.extractHostParam(
                        "https://knox:8443/path?host=https://host-4:28998"));
    }

    @Test
    public void testExtractHostParam_Encoded() {
        assertEquals("https://host-4:28998",
                LivyHaDispatch.extractHostParam(
                        "https://knox:8443/path?host=https%3A%2F%2Fhost-4%3A28998&other=val"));
    }

    @Test
    public void testExtractHostParam_WithAmpersand() {
        assertEquals("https://host-4:28998",
                LivyHaDispatch.extractHostParam(
                        "https://knox:8443/path?host=https://host-4:28998&profile=token"));
    }

    @Test
    public void testExtractHostParam_NoHostParam() {
        assertNull(LivyHaDispatch.extractHostParam("https://knox:8443/path?other=val"));
    }

    @Test
    public void testExtractHostParam_Null() {
        assertNull(LivyHaDispatch.extractHostParam(null));
    }

    @Test
    public void testExtractHostParam_Empty() {
        assertNull(LivyHaDispatch.extractHostParam(""));
    }

    // ==================== removeHostParam() Tests ====================

    @Test
    public void testRemoveHostParam_OnlyHostParam() {
        assertEquals("", LivyHaDispatch.removeHostParam("host=https://host-4:28998"));
    }

    @Test
    public void testRemoveHostParam_HostParamFirst() {
        assertEquals("profile=token&doAs=user",
                LivyHaDispatch.removeHostParam("host=https://host-4:28998&profile=token&doAs=user"));
    }

    @Test
    public void testRemoveHostParam_HostParamInMiddle() {
        assertEquals("before=1&after=2",
                LivyHaDispatch.removeHostParam("before=1&host=https://host-4:28998&after=2"));
    }

    @Test
    public void testRemoveHostParam_NoHostParam() {
        assertEquals("param1=val1&param2=val2",
                LivyHaDispatch.removeHostParam("param1=val1&param2=val2"));
    }

    @Test
    public void testRemoveHostParam_Null() {
        assertNull(LivyHaDispatch.removeHostParam(null));
    }
}

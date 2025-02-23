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
package org.apache.knox.gateway.filter;

import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.Subject;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class RemoteAuthFilterTest {

    public static final String BEARER_INVALID_TOKEN = "Bearer invalid-token";
    public static final String BEARER_VALID_TOKEN = "Bearer valid-token";
    public static final String URL_SUCCESS = "http://example.com/auth";
    private static final String URL_FAIL = "http://example.com/authfail";
    public static final String X_AUTHENTICATED_USER = "X-Authenticated-User";
    public static final String X_AUTHENTICATED_GROUP = "X-Authenticated-Group";
    private RemoteAuthFilter filter;
    private HttpServletRequest requestMock;
    private HttpServletResponse responseMock;
    private TestFilterChain chainMock;
    @Before
    public void setUp() {
        FilterConfig filterConfigMock = EasyMock.createNiceMock(FilterConfig.class);
        requestMock = EasyMock.createMock(HttpServletRequest.class);
        responseMock = EasyMock.createMock(HttpServletResponse.class);
        chainMock = new TestFilterChain();

        EasyMock.expect(filterConfigMock.getInitParameter("remote.auth.url")).andReturn("http://example.com/auth").anyTimes();
        EasyMock.expect(filterConfigMock.getInitParameter("remote.auth.include.headers")).andReturn("Authorization").anyTimes();
        EasyMock.expect(filterConfigMock.getInitParameter("remote.auth.cache.key")).andReturn("Authorization").anyTimes();
        EasyMock.expect(filterConfigMock.getInitParameter("remote.auth.expire.after")).andReturn("5").anyTimes();
        EasyMock.expect(filterConfigMock.getInitParameter("remote.auth.user.header")).andReturn(X_AUTHENTICATED_USER).anyTimes();
        EasyMock.expect(filterConfigMock.getInitParameter("remote.auth.group.header")).andReturn(X_AUTHENTICATED_GROUP).anyTimes();
        EasyMock.expect(filterConfigMock.getInitParameter("remote.auth.truststore.location")).andReturn("/path/to/truststore").anyTimes();
        EasyMock.expect(filterConfigMock.getInitParameter("remote.auth.truststore.password")).andReturn("changeit").anyTimes();

        EasyMock.replay(filterConfigMock);

        filter = new RemoteAuthFilter();
        try {
            filter.init(filterConfigMock);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupURLConnection(String url) {
        try {
            filter.httpURLConnection = new MockHttpURLConnection(new URL(url));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void successfulAuthentication() throws Exception {
        EasyMock.expect(requestMock.getHeader("Authorization")).andReturn(BEARER_VALID_TOKEN).anyTimes();
        EasyMock.expect(responseMock.getStatus()).andReturn(200).anyTimes();
        responseMock.sendError(EasyMock.eq(HttpServletResponse.SC_UNAUTHORIZED), EasyMock.anyString());
        EasyMock.expectLastCall().andThrow(new AssertionError("Authentication should be successful, but was not.")).anyTimes();

        EasyMock.replay(requestMock, responseMock);

        try {
            setupURLConnection(URL_SUCCESS);

            filter.doFilter(requestMock, responseMock, chainMock);
            assertEquals(responseMock.getStatus(), HttpServletResponse.SC_OK);

            assertTrue("Filter chain should have been called but wasn't", chainMock.doFilterCalled);
            Set<PrimaryPrincipal> primaryPrincipals = chainMock.subject.getPrincipals(PrimaryPrincipal.class);
            if (!primaryPrincipals.isEmpty()) {
                assertEquals("lmccay", ((Principal)primaryPrincipals.toArray()[0]).getName());
            }
            Set<GroupPrincipal> groupPrincipals = chainMock.subject.getPrincipals(GroupPrincipal.class);
            if (!groupPrincipals.isEmpty()) {
                assertEquals(2, groupPrincipals.toArray().length);
                assertTrue("Groups should include admin but don't", groupPrincipals.stream()
                        .anyMatch(p -> p.getName().equals("admin")));
                assertTrue("Groups should include engineers but don't", groupPrincipals.stream()
                        .anyMatch(p -> p.getName().equals("engineers")));
            }
        } catch (AssertionError e) {
            assert false : "Authentication failed unexpectedly";
        }
    }

    @Test
    public void authenticationFailsWithInvalidToken() throws Exception {
        EasyMock.expect(responseMock.getStatus()).andReturn(401).anyTimes();
        EasyMock.expect(requestMock.getHeader("Authorization")).andReturn(BEARER_INVALID_TOKEN).anyTimes();
        responseMock.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
        EasyMock.expectLastCall();

        EasyMock.replay(requestMock, responseMock);

        try {
            setupURLConnection(URL_FAIL);

            filter.doFilter(requestMock, responseMock, chainMock);
            assertEquals(HttpServletResponse.SC_UNAUTHORIZED, responseMock.getStatus());

        } catch (Exception e) {
            assertEquals("Expected an IOException for invalid authentication", IOException.class, e.getClass());
            assertFalse("Filter chain should NOT have been called but was", chainMock.doFilterCalled);
            assertTrue(chainMock.subject.getPrincipals(PrimaryPrincipal.class).isEmpty());
            assertTrue(chainMock.subject.getPrincipals(GroupPrincipal.class).isEmpty());
        }

        EasyMock.verify(responseMock); // Verification ensures the error response was indeed sent.
    }

    @Test
    public void testCacheBehavior() throws Exception {
        String principalName = "lmccayiv";
        String groupNames = "admin2,scientists";
        Subject subject = new Subject();
        subject.getPrincipals().add(new PrimaryPrincipal(principalName));
        // Add groups to the principal if available
        Arrays.stream(groupNames.split(",")).forEach(groupName -> subject.getPrincipals()
                .add(new GroupPrincipal(groupName)));
        filter.authenticationCache.put(BEARER_VALID_TOKEN, subject);

        EasyMock.expect(requestMock.getHeader("Authorization")).andReturn(BEARER_VALID_TOKEN).anyTimes();
        EasyMock.expect(responseMock.getStatus()).andReturn(200).anyTimes();
        responseMock.sendError(EasyMock.eq(HttpServletResponse.SC_UNAUTHORIZED), EasyMock.anyString());
        EasyMock.expectLastCall().andThrow(new AssertionError("Authentication should be successful, but was not.")).anyTimes();

        EasyMock.replay(requestMock, responseMock);

        try {
            setupURLConnection(URL_SUCCESS);

            filter.doFilter(requestMock, responseMock, chainMock);
            assertEquals(responseMock.getStatus(), HttpServletResponse.SC_OK);

            assertTrue("Filter chain should have been called but wasn't", chainMock.doFilterCalled);
            Set<PrimaryPrincipal> primaryPrincipals = chainMock.subject.getPrincipals(PrimaryPrincipal.class);
            if (!primaryPrincipals.isEmpty()) {
                assertEquals("lmccayiv", ((Principal)primaryPrincipals.toArray()[0]).getName());
            }
            Set<GroupPrincipal> groupPrincipals = chainMock.subject.getPrincipals(GroupPrincipal.class);
            if (!groupPrincipals.isEmpty()) {
                assertEquals(2, groupPrincipals.toArray().length);
                assertTrue("Groups should include admin2 but don't", groupPrincipals.stream()
                        .anyMatch(p -> p.getName().equals("admin2")));
                assertTrue("Groups should include scientists but don't", groupPrincipals.stream()
                        .anyMatch(p -> p.getName().equals("scientists")));
            }
        } catch (AssertionError e) {
            assert false : "Authentication failed unexpectedly";
        }
    }

    public static class MockHttpURLConnection extends HttpURLConnection {
        private final URL url;
        private int responseCode;
        private final Map<String, String> headers;

        public MockHttpURLConnection(URL url) {
            super(url);
            this.url = url;
            this.responseCode = getCode();
            this.headers = new HashMap<>();

            if (url.toString().equals(URL_SUCCESS)) {
                headers.put(X_AUTHENTICATED_USER, "lmccay");
                headers.put(X_AUTHENTICATED_GROUP, "admin,engineers");
            }
        }

        private int getCode() {
            return this.url.toString().equals(URL_SUCCESS) ? 200 : 401;
        }

        @Override
        public void connect() throws IOException {
            // No need to connect in this mock
        }

        @Override
        public void disconnect() {
            // No need to disconnect in this mock
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public int getResponseCode() throws IOException {
            return responseCode;
        }

        @Override
        public String getHeaderField(String name) {
            return headers.get(name);
        }

        public void setResponseCode(int code) {
            this.responseCode = code;
        }

        public void addHeader(String name, String value) {
            headers.put(name, value);
        }
    }

    protected static class TestFilterChain implements FilterChain {
        boolean doFilterCalled;
        Subject subject;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            doFilterCalled = true;

            subject = Subject.getSubject( AccessController.getContext() );
        }

        public Subject getSubject() {
            return subject;
        }
    }
}
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
package org.apache.knox.gateway.pac4j;

import org.easymock.EasyMock;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class MockHttpServletRequest extends HttpServletRequestWrapper {

    private String requestUrl;
    private Cookie[] cookies;
    private String serverName;
    private Map<String, String> parameters = new HashMap<>();
    private Map<String, String> headers = new HashMap<>();
    private Map<String, Object> attributes = new HashMap<>();

    public MockHttpServletRequest() {
        super(setupMockRequest());
    }

    private static HttpServletRequest setupMockRequest() {
        HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.replay(request);
        return request;
    }

    @Override
    public Cookie[] getCookies() {
        return cookies;
    }

    public void setCookies(final Cookie[] cookies) {
        this.cookies = cookies;
    }

    @Override
    public StringBuffer getRequestURL() {
        return new StringBuffer(requestUrl);
    }

    public void setRequestURL(final String requestUrl) {
        this.requestUrl = requestUrl;
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    public void setServerName(final String serverName) {
        this.serverName = serverName;
    }

    @Override
    public String getParameter(String name) {
        return parameters.get(name);
    }

    public void addParameter(String key, String value) {
        parameters.put(key, value);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headers.keySet());
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }
}

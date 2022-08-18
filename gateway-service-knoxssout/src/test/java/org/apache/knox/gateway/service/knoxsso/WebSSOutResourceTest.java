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
package org.apache.knox.gateway.service.knoxsso;

import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class WebSSOutResourceTest {

  @Test
  public void testClearCookies() {
    testClearCookie("hadoop-jwt");
    testClearCookie(UUID.randomUUID().toString());
  }

  private void testClearCookie(String cookieName) {
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameter("knoxsso.cookie.name")).andReturn(cookieName);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(""));
    Principal mockPrincipal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(mockPrincipal.getName()).andReturn("admin").anyTimes();
    EasyMock.expect(request.getUserPrincipal()).andReturn(mockPrincipal).anyTimes();

    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    ServletOutputStream outputStream = EasyMock.createNiceMock(ServletOutputStream.class);
    CookieResponseWrapper responseWrapper = new CookieResponseWrapper(response, outputStream);

    EasyMock.replay(context, request, mockPrincipal);

    WebSSOutResource webSSOutResponse = new WebSSOutResource();
    webSSOutResponse.request = request;
    webSSOutResponse.response = responseWrapper;
    webSSOutResponse.context = context;
    webSSOutResponse.init();

    // Issue a token
    webSSOutResponse.doGet();

    // Check the cookie
    Cookie cookie = responseWrapper.getCookie(cookieName);
    assertNotNull(cookie);
    assertNull(cookie.getValue());
  }

  /**
   * A wrapper for HttpServletResponseWrapper to store the cookies
   */
  private static class CookieResponseWrapper extends HttpServletResponseWrapper {

    private ServletOutputStream outputStream;
    private Map<String, Cookie> cookies = new HashMap<>();

    CookieResponseWrapper(HttpServletResponse response, ServletOutputStream outputStream) {
      super(response);
      this.outputStream = outputStream;
    }

    @Override
    public ServletOutputStream getOutputStream() {
      return outputStream;
    }

    @Override
    public void addCookie(Cookie cookie) {
      super.addCookie(cookie);
      cookies.put(cookie.getName(), cookie);
    }

    Cookie getCookie(String name) {
      return cookies.get(name);
    }
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
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

import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.Enumeration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class XForwardedHeaderRequestWrapperTest {

  @Test
  public void testDefaultForwardedHeaders() {
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);

    EasyMock.expect(request.getHeader("X-Forwarded-For"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Proto"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Port"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Host"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Context"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("Host"))
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getRemoteAddr())
        .andReturn("127.0.0.1")
        .anyTimes();
    EasyMock.expect(request.isSecure())
        .andReturn(false)
        .anyTimes();
    EasyMock.expect(request.getServerName())
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getContextPath())
        .andReturn("/gateway/default")
        .anyTimes();

    EasyMock.replay(request);

    XForwardedHeaderRequestWrapper wrapper =
        new XForwardedHeaderRequestWrapper(request);

    assertThat(wrapper.getHeader("X-Forwarded-For"), is("127.0.0.1"));
    assertThat(wrapper.getHeader("X-Forwarded-Proto"), is("http"));
    assertThat(wrapper.getHeader("X-Forwarded-Port"), is("80"));
    assertThat(wrapper.getHeader("X-Forwarded-Host"), is("localhost"));
    assertThat(wrapper.getHeader("X-Forwarded-Server"), is("localhost"));
    assertThat(wrapper.getHeader("X-Forwarded-Context"), is("/gateway/default"));
  }

  @Test
  public void testExistingForwardedHeadersArePreserved() {
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);

    EasyMock.expect(request.getHeader("X-Forwarded-For"))
        .andReturn("10.0.0.1")
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Proto"))
        .andReturn("https")
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Port"))
        .andReturn("8443")
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Host"))
        .andReturn("proxy.example.com:8443")
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Context"))
        .andReturn("/proxy")
        .anyTimes();
    EasyMock.expect(request.getRemoteAddr())
        .andReturn("10.0.0.2")
        .anyTimes();
    EasyMock.expect(request.getServerName())
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getContextPath())
        .andReturn("/gateway/default")
        .anyTimes();

    EasyMock.replay(request);

    XForwardedHeaderRequestWrapper wrapper =
        new XForwardedHeaderRequestWrapper(request);

    assertThat(
        wrapper.getHeader("X-Forwarded-For"),
        is("10.0.0.1,10.0.0.2"));

    assertThat(
        wrapper.getHeader("X-Forwarded-Proto"),
        is("https"));

    assertThat(
        wrapper.getHeader("X-Forwarded-Port"),
        is("8443"));

    assertThat(
        wrapper.getHeader("X-Forwarded-Host"),
        is("proxy.example.com:8443"));

    assertThat(
        wrapper.getHeader("X-Forwarded-Context"),
        is("/proxy/gateway/default"));
  }

  @Test
  public void testSecureRequestUsesHttpsAndDefaultPort443() {
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);

    EasyMock.expect(request.getHeader("X-Forwarded-For"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Proto"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Port"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Host"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("Host"))
        .andReturn("secure.example.com")
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Context"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getRemoteAddr())
        .andReturn("192.168.1.10")
        .anyTimes();
    EasyMock.expect(request.isSecure())
        .andReturn(true)
        .anyTimes();
    EasyMock.expect(request.getServerName())
        .andReturn("secure.example.com")
        .anyTimes();
    EasyMock.expect(request.getContextPath())
        .andReturn("/gateway/default")
        .anyTimes();

    EasyMock.replay(request);

    XForwardedHeaderRequestWrapper wrapper =
        new XForwardedHeaderRequestWrapper(request);

    assertThat(wrapper.getHeader("X-Forwarded-Proto"), is("https"));
    assertThat(wrapper.getHeader("X-Forwarded-Port"), is("443"));
    assertThat(
        wrapper.getHeader("X-Forwarded-Host"),
        is("secure.example.com"));
  }

  @Test
  public void testHeaderLookupIsCaseInsensitive() {
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);

    EasyMock.expect(request.getHeader("X-Forwarded-For"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Proto"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Port"))
        .andReturn("8080")
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Host"))
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Context"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("Host"))
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getRemoteAddr())
        .andReturn("127.0.0.1")
        .anyTimes();
    EasyMock.expect(request.isSecure())
        .andReturn(false)
        .anyTimes();
    EasyMock.expect(request.getServerName())
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getContextPath())
        .andReturn("/gateway")
        .anyTimes();

    EasyMock.replay(request);

    XForwardedHeaderRequestWrapper wrapper =
        new XForwardedHeaderRequestWrapper(request);

    assertThat(
        wrapper.getHeader("x-forwarded-port"),
        is("8080"));

    assertThat(
        wrapper.getHeader("X-FORWARDED-HOST"),
        is("localhost"));
  }

  @Test
  public void testServiceContextTakesPrecedence() {
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);

    EasyMock.expect(request.getHeader("X-Forwarded-For"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Proto"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Port"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Host"))
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Context"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("Host"))
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getRemoteAddr())
        .andReturn("127.0.0.1")
        .anyTimes();
    EasyMock.expect(request.isSecure())
        .andReturn(false)
        .anyTimes();
    EasyMock.expect(request.getServerName())
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getContextPath())
        .andReturn("/gateway/sandbox")
        .anyTimes();

    EasyMock.replay(request);

    XForwardedHeaderRequestWrapper wrapper =
        new XForwardedHeaderRequestWrapper(
            request,
            true,
            "livy/v1");

    assertThat(
        wrapper.getHeader("X-Forwarded-Context"),
        is("/gateway/sandbox/livy/v1"));
  }

  @Test
  public void testServiceNameIsAppendedToContext() {
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);

    EasyMock.expect(request.getHeader("X-Forwarded-For"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Proto"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Port"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Host"))
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Context"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("Host"))
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getRemoteAddr())
        .andReturn("127.0.0.1")
        .anyTimes();
    EasyMock.expect(request.isSecure())
        .andReturn(false)
        .anyTimes();
    EasyMock.expect(request.getServerName())
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getContextPath())
        .andReturn("/gateway/sandbox")
        .anyTimes();
    EasyMock.expect(request.getRequestURI())
        .andReturn("/gateway/sandbox/webhdfs/v1")
        .anyTimes();

    EasyMock.replay(request);

    XForwardedHeaderRequestWrapper wrapper =
        new XForwardedHeaderRequestWrapper(
            request,
            true,
            null);

    assertThat(
        wrapper.getHeader("X-Forwarded-Context"),
        is("/gateway/sandbox/webhdfs"));
  }

  @Test
  public void testGetHeadersReturnsForwardedHeaderValue() {
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);

    EasyMock.expect(request.getHeader("X-Forwarded-For"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Proto"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Port"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Host"))
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getHeader("X-Forwarded-Context"))
        .andReturn(null)
        .anyTimes();
    EasyMock.expect(request.getHeader("Host"))
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getRemoteAddr())
        .andReturn("127.0.0.1")
        .anyTimes();
    EasyMock.expect(request.isSecure())
        .andReturn(false)
        .anyTimes();
    EasyMock.expect(request.getServerName())
        .andReturn("localhost")
        .anyTimes();
    EasyMock.expect(request.getContextPath())
        .andReturn("/gateway")
        .anyTimes();

    EasyMock.replay(request);

    XForwardedHeaderRequestWrapper wrapper =
        new XForwardedHeaderRequestWrapper(request);

    Enumeration<String> headers =
        wrapper.getHeaders("X-Forwarded-Host");

    assertThat(
        Collections.list(headers),
        is(Collections.singletonList("localhost")));
  }
}

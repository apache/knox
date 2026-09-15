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

import static org.apache.knox.gateway.dispatch.DefaultDispatch.SET_COOKIE;
import static org.apache.knox.gateway.dispatch.DefaultDispatch.WWW_AUTHENTICATE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHeader;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.mock.MockHttpServletResponse;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;

public class PassAllHeadersDispatchTest {

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testOutboundRequestExcludeHeadersAlwaysContainContentLengthAndTransferEncoding() {
    PassAllHeadersDispatch dispatch = new PassAllHeadersDispatch();

    // configuration is ignored -- always excludes exactly these two headers
    dispatch.setRequestExcludeHeaders(String.join(",", Arrays.asList(HttpHeaders.ACCEPT, "TEST")));

    assertThat(dispatch.getOutboundRequestExcludeHeaders().size(), is(2));
    assertThat(dispatch.getOutboundRequestExcludeHeaders().contains("Content-Length"), is(true));
    assertThat(dispatch.getOutboundRequestExcludeHeaders().contains("Transfer-Encoding"), is(true));
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testOutboundResponseExcludeHeadersIsAlwaysEmpty() {
    PassAllHeadersDispatch dispatch = new PassAllHeadersDispatch();

    // configuration is ignored -- no response headers are excluded by name
    dispatch.setResponseExcludeHeaders(String.join(",", Arrays.asList("TEST", WWW_AUTHENTICATE)));

    assertThat(dispatch.getOutboundResponseExcludeHeaders().isEmpty(), is(true));
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testCopyRequestHeaderFieldsStripsContentLengthAndTransferEncoding() {
    PassAllHeadersDispatch dispatch = new PassAllHeadersDispatch();

    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Length", "1234");
    headers.put("Transfer-Encoding", "chunked");
    headers.put(HttpHeaders.AUTHORIZATION, "Basic ...");
    headers.put("TEST", "test");

    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getHeaderNames()).andReturn(Collections.enumeration(headers.keySet())).anyTimes();
    Capture<String> capturedArgument = Capture.newInstance();
    EasyMock.expect(inboundRequest.getHeader(EasyMock.capture(capturedArgument)))
        .andAnswer(() -> headers.get(capturedArgument.getValue())).anyTimes();
    EasyMock.replay(inboundRequest);

    HttpUriRequest outboundRequest = new HttpGet();
    dispatch.copyRequestHeaderFields(outboundRequest, inboundRequest);

    Header[] outboundRequestHeaders = outboundRequest.getAllHeaders();
    assertThat(outboundRequestHeaders.length, is(2));
    assertThat(outboundRequest.getFirstHeader("Content-Length"), nullValue());
    assertThat(outboundRequest.getFirstHeader("Transfer-Encoding"), nullValue());
    assertThat(outboundRequest.getFirstHeader(HttpHeaders.AUTHORIZATION).getValue(), is("Basic ..."));
    assertThat(outboundRequest.getFirstHeader("TEST").getValue(), is("test"));
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testCopyResponseHeaderFieldsPassesThroughWwwAuthenticateAndOtherHeaders() {
    PassAllHeadersDispatch dispatch = new PassAllHeadersDispatch();

    Header[] headers = new Header[]{
        new BasicHeader(WWW_AUTHENTICATE, "negotiate"),
        new BasicHeader("TEST", "testValue"),
        new BasicHeader(HttpHeaders.ACCEPT, "application/json")
    };

    HttpResponse inboundResponse = EasyMock.createNiceMock(HttpResponse.class);
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(headers).anyTimes();
    EasyMock.replay(inboundResponse);

    HttpServletResponse outboundResponse = new MockHttpServletResponse();
    dispatch.copyResponseHeaderFields(outboundResponse, inboundResponse);

    assertThat(outboundResponse.getHeaderNames().size(), is(3));
    assertThat(outboundResponse.getHeader(WWW_AUTHENTICATE), is("negotiate"));
    assertThat(outboundResponse.getHeader("TEST"), is("testValue"));
    assertThat(outboundResponse.getHeader(HttpHeaders.ACCEPT), is("application/json"));
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testCopyResponseHeaderFieldsExcludesSetCookieByDefault() {
    PassAllHeadersDispatch dispatch = new PassAllHeadersDispatch();

    Header[] headers = new Header[]{
        new BasicHeader(SET_COOKIE, "JSESSIONID=abc; Path=/; Secure"),
        new BasicHeader("TEST", "testValue")
    };

    HttpResponse inboundResponse = EasyMock.createNiceMock(HttpResponse.class);
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(headers).anyTimes();
    EasyMock.replay(inboundResponse);

    HttpServletResponse outboundResponse = new MockHttpServletResponse();
    dispatch.copyResponseHeaderFields(outboundResponse, inboundResponse);

    // before setResponseExcludeHeaders is ever configured, Set-Cookie directives
    // are still fully excluded (inherited default), so only "TEST" passes through
    assertThat(outboundResponse.getHeaderNames().size(), is(1));
    assertThat(outboundResponse.getHeader("TEST"), is("testValue"));
    assertThat(outboundResponse.getHeader(SET_COOKIE), nullValue());
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testCopyResponseHeaderFieldsBlocksOnlyAuthCookiesOnceConfigured() {
    PassAllHeadersDispatch dispatch = new PassAllHeadersDispatch();
    // the passed-in value is ignored, but calling this switches Set-Cookie
    // filtering from "exclude everything" to "exclude only known auth cookies"
    dispatch.setResponseExcludeHeaders("anything");

    Header[] headers = new Header[]{
        new BasicHeader(SET_COOKIE, "hadoop.auth=\"secret\"; Path=/; Secure"),
        new BasicHeader(SET_COOKIE, "JSESSIONID=abc; Path=/; Secure")
    };

    HttpResponse inboundResponse = EasyMock.createNiceMock(HttpResponse.class);
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(headers).anyTimes();
    EasyMock.replay(inboundResponse);

    HttpServletResponse outboundResponse = new MockHttpServletResponse();
    dispatch.copyResponseHeaderFields(outboundResponse, inboundResponse);

    assertThat(outboundResponse.getHeaderNames().size(), is(1));
    assertThat(outboundResponse.getHeader(SET_COOKIE), is("JSESSIONID=abc; Path=/; Secure"));
  }
}

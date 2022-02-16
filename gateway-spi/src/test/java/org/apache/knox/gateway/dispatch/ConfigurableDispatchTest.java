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

import static org.apache.knox.gateway.dispatch.AbstractGatewayDispatch.REQUEST_ID_HEADER_NAME;
import static org.apache.knox.gateway.dispatch.DefaultDispatch.SET_COOKIE;
import static org.apache.knox.gateway.dispatch.DefaultDispatch.WWW_AUTHENTICATE;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
import org.apache.logging.log4j.CloseableThreadContext;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;

public class ConfigurableDispatchTest {
  public final String TRACE_ID = "trace_id";

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testGetDispatchUrl() {
    HttpServletRequest request;
    String path;
    String query;
    URI uri;

    ConfigurableDispatch dispatch = new ConfigurableDispatch();

    path = "http://test-host:42/test-path";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( null ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test-path" ) );

    path = "http://test-host:42/test,path";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( null ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test,path" ) );

    path = "http://test-host:42/test%2Cpath";
    query = "service_config_version_note.matches(Updated%20Kerberos-related%20configurations";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( query ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test%2Cpath?service_config_version_note.matches(Updated%20Kerberos-related%20configurations" ) );

    path = "http://test-host:42/test%2Cpath";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( null ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test%2Cpath" ) );

    path = "http://test-host:42/test%2Cpath";
    query = "test%26name=test%3Dvalue";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( query ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test%2Cpath?test%26name=test%3Dvalue" ) );
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testGetDispatchUrlNoUrlEncoding() {
    HttpServletRequest request;
    String path;
    String query;
    URI uri;

    ConfigurableDispatch dispatch = new ConfigurableDispatch();
    dispatch.setRemoveUrlEncoding(Boolean.TRUE.toString());

    path = "http://test-host:42/test-path";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( null ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test-path" ) );

    path = "http://test-host:42/test,path";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( null ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test,path" ) );

    // encoding in the patch remains
    path = "http://test-host:42/test%2Cpath";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( null ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test%2Cpath" ) );

    // encoding in query %20 is not removed
    path = "http://test-host:42/test%2Cpath";
    query = "service_config_version_note.matches(Updated%20Kerberos-related%20configurations";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( query ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test%2Cpath?service_config_version_note.matches(Updated%20Kerberos-related%20configurations" ) );

    // encoding in query string is removed
    path = "http://test-host:42/test%2Cpath";
    query = "test%26name=test%3Dvalue";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( query ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test%2Cpath?test&name=test=value" ) );

    // double quotes removed
    path = "https://test-host:42/api/v1/views/TEZ/versions/0.7.0.2.6.2.0-205/instances/TEZ_CLUSTER_INSTANCE/resources/atsproxy/ws/v1/timeline/TEZ_DAG_ID";
    query = "limit=9007199254740991&primaryFilter=applicationId:%22application_1518808140659_0007%22&_=1519053586839";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( query ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "https://test-host:42/api/v1/views/TEZ/versions/0.7.0.2.6.2.0-205/instances/TEZ_CLUSTER_INSTANCE/resources/atsproxy/ws/v1/timeline/TEZ_DAG_ID?limit=9007199254740991&primaryFilter=applicationId:%22application_1518808140659_0007%22&_=1519053586839" ) );

    // encode < and > sign
    path = "http://test-host:8080/api/v1/clusters/mmolnar-knox2/configurations/service_config_versions";
    query = "group_id%3E0&fields=*&_=1541527314780";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( query ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:8080/api/v1/clusters/mmolnar-knox2/configurations/service_config_versions?group_id%3E0&fields=*&_=1541527314780" ) );
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testRequestExcludeHeadersDefault() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();

    Map<String, String> headers = new HashMap<>();
    headers.put(HttpHeaders.AUTHORIZATION, "Basic ...");
    headers.put(HttpHeaders.ACCEPT, "abc");
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
    assertThat(outboundRequestHeaders[0].getName(), is(HttpHeaders.ACCEPT));
    assertThat(outboundRequestHeaders[1].getName(), is("TEST"));
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testRequestExcludeHeadersConfig() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();
    dispatch.setRequestExcludeHeaders(String.join(",", Arrays.asList(HttpHeaders.ACCEPT, "TEST")));

    Map<String, String> headers = new HashMap<>();
    headers.put(HttpHeaders.AUTHORIZATION, "Basic ...");
    headers.put(HttpHeaders.ACCEPT, "abc");
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
    assertThat(outboundRequestHeaders.length, is(1));
    assertThat(outboundRequestHeaders[0].getName(), is(HttpHeaders.AUTHORIZATION));
  }

  @Test
  public void testResponseExcludeHeadersDefault() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();

    Header[] headers = new Header[]{
        new BasicHeader(SET_COOKIE, "abc"),
        new BasicHeader(WWW_AUTHENTICATE, "negotiate"),
        new BasicHeader("TEST", "testValue"),
        new BasicHeader("Accept", "applcation/json")
    };

    HttpResponse inboundResponse = EasyMock.createNiceMock(HttpResponse.class);
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(headers).anyTimes();
    EasyMock.replay(inboundResponse);

    HttpServletResponse outboundResponse = new MockHttpServletResponse();
    dispatch.copyResponseHeaderFields(outboundResponse, inboundResponse);

    assertThat(outboundResponse.getHeaderNames().size(), is(2));
    assertThat(outboundResponse.getHeader("TEST"), is("testValue"));
    assertThat(outboundResponse.getHeader("Accept"), is("applcation/json"));
  }

  @Test
  public void testResponseExcludeHeadersConfig() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();
    dispatch.setResponseExcludeHeaders(String.join(",", Arrays.asList("test", "caseINSENSITIVEheader")));

    Header[] headers = new Header[]{
        new BasicHeader(SET_COOKIE, "abc"),
        new BasicHeader(WWW_AUTHENTICATE, "negotiate"),
        new BasicHeader("TEST", "testValue"),
        new BasicHeader("caseInsensitiveHeader", "caseInsensitiveHeaderValue")
    };

    HttpResponse inboundResponse = EasyMock.createNiceMock(HttpResponse.class);
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(headers).anyTimes();
    EasyMock.replay(inboundResponse);

    HttpServletResponse outboundResponse = new MockHttpServletResponse();
    dispatch.copyResponseHeaderFields(outboundResponse, inboundResponse);

    assertThat(outboundResponse.getHeaderNames().size(), is(2));
    assertThat(outboundResponse.getHeader(SET_COOKIE), is("abc"));
    assertThat(outboundResponse.getHeader(WWW_AUTHENTICATE), is("negotiate"));
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testRequestAppendHeadersConfig() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();
    dispatch.setRequestAppendHeaders("a:b;c:d");

    Map<String, String> headers = new HashMap<>();
    headers.put(HttpHeaders.AUTHORIZATION, "Basic ...");
    headers.put(HttpHeaders.ACCEPT, "abc");
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
    assertThat(outboundRequestHeaders.length, is(4));
    assertThat(outboundRequestHeaders[0].getName(), is(HttpHeaders.ACCEPT));
    assertThat(outboundRequestHeaders[1].getName(), is("TEST"));
    assertThat(outboundRequestHeaders[2].getName(), is("a"));
    assertThat(outboundRequestHeaders[3].getName(), is("c"));
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testRequestExcludeAndAppendHeadersConfig() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();
    dispatch.setRequestAppendHeaders("a : b ; c : d");
    dispatch.setRequestExcludeHeaders(String.join(",", Arrays.asList(HttpHeaders.ACCEPT, "TEST")));

    Map<String, String> headers = new HashMap<>();
    headers.put(HttpHeaders.AUTHORIZATION, "Basic ...");
    headers.put(HttpHeaders.ACCEPT, "abc");
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
    assertThat(outboundRequestHeaders.length, is(3));
    assertThat(outboundRequestHeaders[0].getName(), is(HttpHeaders.AUTHORIZATION));
    assertThat(outboundRequestHeaders[1].getName(), is("a"));
    assertThat(outboundRequestHeaders[2].getName(), is("c"));
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testRequestExcludeAndAppendWithDifferentValueHeadersConfig() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();
    dispatch.setRequestAppendHeaders("a:b; TEST :xyz=abc,def=ghi");
    dispatch.setRequestExcludeHeaders(String.join(",", Arrays.asList(HttpHeaders.ACCEPT, "TEST")));

    Map<String, String> headers = new HashMap<>();
    headers.put(HttpHeaders.AUTHORIZATION, "Basic ...");
    headers.put(HttpHeaders.ACCEPT, "abc");
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
    assertThat(outboundRequestHeaders.length, is(3));
    assertThat(outboundRequestHeaders[0].getName(), is(HttpHeaders.AUTHORIZATION));
    assertThat(outboundRequestHeaders[1].getName(), is("a"));
    assertThat(outboundRequestHeaders[2].getName(), is("TEST"));
    assertThat(outboundRequestHeaders[2].getValue(), is("xyz=abc,def=ghi"));
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testInvalidRequestAppendHeadersConfig() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();
    dispatch.setRequestAppendHeaders("a:;b;c:d");

    Map<String, String> headers = new HashMap<>();
    headers.put(HttpHeaders.AUTHORIZATION, "Basic ...");
    headers.put(HttpHeaders.ACCEPT, "abc");
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
    assertThat(outboundRequestHeaders[0].getName(), is(HttpHeaders.ACCEPT));
    assertThat(outboundRequestHeaders[1].getName(), is("TEST"));
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testRequestExcludeAndInvalidAppendHeadersConfig() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();
    dispatch.setRequestAppendHeaders(" a :; b ; c : d ");
    dispatch.setRequestExcludeHeaders(String.join(",", Arrays.asList(HttpHeaders.ACCEPT, "TEST")));

    Map<String, String> headers = new HashMap<>();
    headers.put(HttpHeaders.AUTHORIZATION, "Basic ...");
    headers.put(HttpHeaders.ACCEPT, "abc");
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
    assertThat(outboundRequestHeaders.length, is(1));
    assertThat(outboundRequestHeaders[0].getName(), is(HttpHeaders.AUTHORIZATION));
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testRequestExcludeAndInvalidAppendWithDifferentValueHeadersConfig() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();
    dispatch.setRequestAppendHeaders("a:b; TEST :xyz=abc;def=ghi");
    dispatch.setRequestExcludeHeaders(String.join(",", Arrays.asList(HttpHeaders.ACCEPT, "TEST")));

    Map<String, String> headers = new HashMap<>();
    headers.put(HttpHeaders.AUTHORIZATION, "Basic ...");
    headers.put(HttpHeaders.ACCEPT, "abc");
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
    assertThat(outboundRequestHeaders.length, is(1));
    assertThat(outboundRequestHeaders[0].getName(), is(HttpHeaders.AUTHORIZATION));
  }

  @Test
  public void shouldExcludeCertainPartsFromSetCookieHeader() throws Exception {
    final Header[] headers = new Header[] { new BasicHeader(SET_COOKIE, "Domain=localhost; Secure; HttpOnly; Max-Age=1") };
    final HttpResponse inboundResponse = EasyMock.createNiceMock(HttpResponse.class);
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(headers).anyTimes();
    EasyMock.replay(inboundResponse);

    final ConfigurableDispatch dispatch = new ConfigurableDispatch();
    final String setCookieExludeHeaders = SET_COOKIE + ": Domain=localhost; HttpOnly";
    dispatch.setResponseExcludeHeaders(setCookieExludeHeaders);

    final HttpServletResponse outboundResponse = new MockHttpServletResponse();
    dispatch.copyResponseHeaderFields(outboundResponse, inboundResponse);

    assertThat(outboundResponse.getHeaderNames().size(), is(1));
    assertThat(outboundResponse.getHeader(SET_COOKIE), is("Secure; Max-Age=1"));
  }

  /**
   * Make sure that SET-COOKIE attributes order is maintained
   */
  @Test
  public void testSetCookieHeaderOrder() {
    final String SET_COOKIE_VALUE = "JSESSIONID=ba760126-414f-406d-baa1-99e14eb47656; zx=yz; SameSite=none; Secure; Path=/; HttpOnly; a=b; c=d";
    final String EXPECTED_SET_COOKIE_VALUE = "JSESSIONID=ba760126-414f-406d-baa1-99e14eb47656; zx=yz; SameSite=none; Path=/; HttpOnly; a=b; c=d";
    final Header[] headers = new Header[] { new BasicHeader(SET_COOKIE, SET_COOKIE_VALUE) };
    final HttpResponse inboundResponse = EasyMock.createNiceMock(HttpResponse.class);
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(headers).anyTimes();
    EasyMock.replay(inboundResponse);

    final ConfigurableDispatch dispatch = new ConfigurableDispatch();
    final String setCookieExludeHeaders = SET_COOKIE + ": Secure";
    dispatch.setResponseExcludeHeaders(setCookieExludeHeaders);

    final HttpServletResponse outboundResponse = new MockHttpServletResponse();
    dispatch.copyResponseHeaderFields(outboundResponse, inboundResponse);

    assertThat(outboundResponse.getHeader(SET_COOKIE), is(EXPECTED_SET_COOKIE_VALUE));
  }

  /**
   * When exclude list is defined and set-cookie is not in the list
   * make sure auth cookies are blocked.
   * @throws Exception
   */
  @Test
  public void testPreventSetCookieHeaderDirectivesDefault() throws Exception {
    final Header[] headers = new Header[] {
        new BasicHeader(SET_COOKIE, "hadoop.auth=\"u=knox&p=knox/knox.local@knox.local&t=kerberos&e=1604347441986c=\"; Path=/; Domain=knox.local; Expires=Fri, 13-Nov-2020 17:26:18 GMT; Secure; HttpOnly[\\r][\\n]")};
    final HttpResponse inboundResponse = EasyMock.createNiceMock(HttpResponse.class);
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(headers).anyTimes();
    EasyMock.replay(inboundResponse);

    final ConfigurableDispatch dispatch = new ConfigurableDispatch();

    final String setCookieExludeHeaders = "WWW-AUTHENTICATE";
    dispatch.setResponseExcludeHeaders(setCookieExludeHeaders);

    final HttpServletResponse outboundResponse = new MockHttpServletResponse();
    dispatch.copyResponseHeaderFields(outboundResponse, inboundResponse);

    assertThat(outboundResponse.getHeaderNames().size(), is(0));
  }

  /**
   * When exclude list is defined and set-cookie is not in the list
   * make sure non-auth cookies are not blocked.
   * @throws Exception
   */
  @Test
  public void testAllowSetCookieHeaderDirectivesDefault() throws Exception {
    final String SET_COOKIE_VALUE = "RANGERADMINSESSIONID=5C0C1805BD3B43BA8E9FC04A63586505; Path=/; Secure; HttpOnly";
    final Header[] headers = new Header[] {
        new BasicHeader(SET_COOKIE, SET_COOKIE_VALUE)};
    final HttpResponse inboundResponse = EasyMock.createNiceMock(HttpResponse.class);
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(headers).anyTimes();
    EasyMock.replay(inboundResponse);

    final ConfigurableDispatch dispatch = new ConfigurableDispatch();

    final String setCookieExludeHeaders = "WWW-AUTHENTICATE";
    dispatch.setResponseExcludeHeaders(setCookieExludeHeaders);

    final HttpServletResponse outboundResponse = new MockHttpServletResponse();
    dispatch.copyResponseHeaderFields(outboundResponse, inboundResponse);

    assertThat(outboundResponse.getHeaderNames().size(), is(1));
    assertThat(outboundResponse.getHeader(SET_COOKIE), is(SET_COOKIE_VALUE));
  }

    /**
     * Test a case where SET-COOKIE header does not use spaces
     * @throws Exception
     */
    @Test
    public void testAllowSetCookieHeaderNoSpaces() throws Exception {
        final Header[] headers = new Header[] {
                new BasicHeader(SET_COOKIE, "SESSION=e69d3d08-7452-45cb-90bb-9cdde3fa1342;Path=/;HttpOnly")};
        final HttpResponse inboundResponse = EasyMock.createNiceMock(HttpResponse.class);
        EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(headers).anyTimes();
        EasyMock.replay(inboundResponse);

        final ConfigurableDispatch dispatch = new ConfigurableDispatch();

        final String setCookieExludeHeaders = "WWW-AUTHENTICATE";
        dispatch.setResponseExcludeHeaders(setCookieExludeHeaders);

        final HttpServletResponse outboundResponse = new MockHttpServletResponse();
        dispatch.copyResponseHeaderFields(outboundResponse, inboundResponse);

        assertThat(outboundResponse.getHeaderNames().size(), is(1));
        assertThat(outboundResponse.getHeader(SET_COOKIE), is("SESSION=e69d3d08-7452-45cb-90bb-9cdde3fa1342; Path=/; HttpOnly"));
    }

  /**
   * Case where auth cookie can be configured to pass through
   *
   * @throws Exception
   */
  @Test
  public void testPassthroughSetCookieHeaderDirectivesDefault()
      throws Exception {
    final Header[] headers = new Header[] { new BasicHeader(SET_COOKIE,
        "hadoop.auth=\"u=knox&p=knox/knox.local@nox.local&t=kerberos&e=1604347441986c=\"; Path=/; Domain=knox.local; Expires=Fri, 13-Nov-2020 17:26:18 GMT; Secure; HttpOnly[\\r][\\n]") };
    final HttpResponse inboundResponse = EasyMock
        .createNiceMock(HttpResponse.class);
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(headers)
        .anyTimes();
    EasyMock.replay(inboundResponse);

    final ConfigurableDispatch dispatch = new ConfigurableDispatch();

    /**
     * NOTE: here we are defining what set-cookie attributes we
     * want to block. We are blocking only 'Secure' attribute
     * other attributes such as 'hadoop.auth' are free to pass
     **/
    final String setCookieExludeHeaders = "WWW-AUTHENTICATE, SET-COOKIE: Secure";
    dispatch.setResponseExcludeHeaders(setCookieExludeHeaders);

    final HttpServletResponse outboundResponse = new MockHttpServletResponse();
    dispatch.copyResponseHeaderFields(outboundResponse, inboundResponse);

    assertThat(outboundResponse.getHeaderNames().size(), is(1));
    assertThat(outboundResponse.getHeader(SET_COOKIE), containsString("hadoop.auth="));
  }

  /**
   * Test the case where the incoming request to Knox does not
   * have X-Request-Id header.
   * Expected outcome is that correlation id will be used
   * as X-Request-Id value for outgoing requests.
   */
  @Test
  public void testCorrelationIDXRequestIDHeader() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();
    final String reqID = UUID.randomUUID().toString();

    Map<String, String> headers = new HashMap<>();
    headers.put(HttpHeaders.ACCEPT, "abc");
    headers.put("TEST", "test");

    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getHeaderNames()).andReturn(Collections.enumeration(headers.keySet())).anyTimes();
    Capture<String> capturedArgument = Capture.newInstance();
    EasyMock.expect(inboundRequest.getHeader(EasyMock.capture(capturedArgument)))
        .andAnswer(() -> headers.get(capturedArgument.getValue())).anyTimes();
    EasyMock.replay(inboundRequest);

    HttpUriRequest outboundRequest = new HttpGet();
    try(CloseableThreadContext.Instance ctc = CloseableThreadContext.put(TRACE_ID, reqID)) {
      dispatch.copyRequestHeaderFields(outboundRequest, inboundRequest);
    }

    Header[] outboundRequestHeaders = outboundRequest.getAllHeaders();
    assertThat(outboundRequestHeaders.length, is(3));
    assertThat(outboundRequest.getHeaders(REQUEST_ID_HEADER_NAME)[0].getValue(), is(reqID));
  }

  /**
   * Test the case where the incoming request to Knox comtains
   * X-Request-Id header.
   * Expected outcome is that correlation id will NOT be used
   * as X-Request-Id value instead the X-Request-Id value
   * coming from the inbound request will be used.
   */
  @Test
  public void testRequestHeaderXRequestID() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();
    final String reqID = UUID.randomUUID().toString();
    final String headerReqID = "1234567890ABCD";

    Map<String, String> headers = new HashMap<>();
    headers.put(REQUEST_ID_HEADER_NAME, headerReqID);
    headers.put(HttpHeaders.ACCEPT, "abc");
    headers.put("TEST", "test");

    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getHeaderNames()).andReturn(Collections.enumeration(headers.keySet())).anyTimes();
    Capture<String> capturedArgument = Capture.newInstance();
    EasyMock.expect(inboundRequest.getHeader(EasyMock.capture(capturedArgument)))
        .andAnswer(() -> headers.get(capturedArgument.getValue())).anyTimes();
    EasyMock.replay(inboundRequest);

    HttpUriRequest outboundRequest = new HttpGet();
    try(CloseableThreadContext.Instance ctc = CloseableThreadContext.put(TRACE_ID, reqID)) {
      dispatch.copyRequestHeaderFields(outboundRequest, inboundRequest);
    }

    Header[] outboundRequestHeaders = outboundRequest.getAllHeaders();
    assertThat(outboundRequestHeaders.length, is(3));
    assertThat(outboundRequest.getHeaders(REQUEST_ID_HEADER_NAME)[0].getValue(), is(headerReqID));
  }

  /**
   * Make sure X-Request-Id header is not added when it is configured
   * in exclude header list.
   * This test case tests case where X-Request-Id is passed from inbound request.
   */
  @Test
  public void testXRequestIDHeaderExcludeList() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();
    dispatch.setResponseExcludeHeaders(String.join(",", Arrays.asList("test", REQUEST_ID_HEADER_NAME)));

    Header[] headers = new Header[]{
        new BasicHeader(REQUEST_ID_HEADER_NAME, UUID.randomUUID().toString()),
        new BasicHeader(WWW_AUTHENTICATE, "negotiate"),
        new BasicHeader("TEST", "testValue"),
    };

    HttpResponse inboundResponse = EasyMock.createNiceMock(HttpResponse.class);
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(headers).anyTimes();
    EasyMock.replay(inboundResponse);

    HttpServletResponse outboundResponse = new MockHttpServletResponse();
    try(CloseableThreadContext.Instance ctc = CloseableThreadContext.put(TRACE_ID, UUID.randomUUID().toString())) {
      dispatch.copyResponseHeaderFields(outboundResponse, inboundResponse);
    }

    assertThat(outboundResponse.getHeaderNames().size(), is(1));
    assertThat(outboundResponse.getHeader(WWW_AUTHENTICATE), is("negotiate"));
    assertThat(outboundResponse.getHeader(REQUEST_ID_HEADER_NAME), nullValue());
  }

  /**
   * Make sure X-Request-Id header is not added when it is configured
   * in exclude header list.
   * This test case tests case where no-request id passed from inbound request.
   */
  @Test
  public void testXRequestIDHeaderExcludeListNoReqHeader() {
    ConfigurableDispatch dispatch = new ConfigurableDispatch();
    dispatch.setResponseExcludeHeaders(String.join(",", Arrays.asList("test", REQUEST_ID_HEADER_NAME)));

    Header[] headers = new Header[]{
        new BasicHeader(WWW_AUTHENTICATE, "negotiate"),
        new BasicHeader("TEST", "testValue"),
    };

    HttpResponse inboundResponse = EasyMock.createNiceMock(HttpResponse.class);
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(headers).anyTimes();
    EasyMock.replay(inboundResponse);

    HttpServletResponse outboundResponse = new MockHttpServletResponse();
    try(CloseableThreadContext.Instance ctc = CloseableThreadContext.put(TRACE_ID, UUID.randomUUID().toString())) {
      dispatch.copyResponseHeaderFields(outboundResponse, inboundResponse);
    }

    assertThat(outboundResponse.getHeaderNames().size(), is(1));
    assertThat(outboundResponse.getHeader(WWW_AUTHENTICATE), is("negotiate"));
    assertThat(outboundResponse.getHeader(REQUEST_ID_HEADER_NAME), nullValue());
  }

}

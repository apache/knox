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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.knox.gateway.dispatch.DefaultDispatch.SET_COOKIE;
import static org.apache.knox.gateway.dispatch.DefaultDispatch.WWW_AUTHENTICATE;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ConfigurableDispatchTest {
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
    dispatch.setResponseExcludeHeaders(String.join(",", Collections.singletonList("TEST")));

    Header[] headers = new Header[]{
        new BasicHeader(SET_COOKIE, "abc"),
        new BasicHeader(WWW_AUTHENTICATE, "negotiate"),
        new BasicHeader("TEST", "testValue")
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

}

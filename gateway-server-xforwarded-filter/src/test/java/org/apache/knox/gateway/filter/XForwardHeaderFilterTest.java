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

import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class XForwardHeaderFilterTest {

  public static class AssertXForwardedHeaders extends TestFilterAdapter {
    @Override
    public void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain ) {
      assertThat( request.getHeader( "X-Forwarded-For" ), is( "127.0.0.1" ) );
      assertThat( request.getHeader( "X-Forwarded-Proto" ), is( "http" ) );
      assertThat( request.getHeader( "X-Forwarded-Port" ), is( "8888" ) );
      assertThat( request.getHeader( "X-Forwarded-Host" ), is( "localhost:8888" ) );
      assertThat( request.getHeader( "X-Forwarded-Server" ), is( "localhost" ) );
      assertThat( request.getHeader( "X-Forwarded-Context" ), is( "/context" ) );
    }
  }

  @Test
  public void testXForwardHeaders() throws ServletException, IOException {
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRemoteAddr() ).andReturn( "127.0.0.1" ).anyTimes();
    EasyMock.expect( request.isSecure() ).andReturn( false ).anyTimes();
    EasyMock.expect( request.getLocalPort() ).andReturn( 8888 ).anyTimes();
    EasyMock.expect( request.getHeader( "Host" ) ).andReturn( "localhost:8888" ).anyTimes();
    EasyMock.expect( request.getServerName() ).andReturn( "localhost" ).anyTimes();
    EasyMock.expect( request.getContextPath() ).andReturn( "/context" ).anyTimes();
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( request, response );

    TestFilterChain chain = new TestFilterChain();

    XForwardedHeaderFilter filter = new XForwardedHeaderFilter();

    chain.push( new AssertXForwardedHeaders() );
    chain.push( filter );
    chain.doFilter( request, response );
  }

  public static class AssertProxiedXForwardedHeaders extends TestFilterAdapter {
    @Override
    public void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain ) {
      assertThat( request.getHeader( "X-Forwarded-For" ), is( "127.0.0.0,127.0.0.1" ) );
      assertThat( request.getHeader( "X-Forwarded-Proto" ), is( "https" ) );
      assertThat( request.getHeader( "X-Forwarded-Port" ), is( "9999" ) );
      assertThat( request.getHeader( "X-Forwarded-Host" ), is( "remotehost:9999" ) );
      assertThat( request.getHeader( "X-Forwarded-Server" ), is( "localhost" ) );
      assertThat( request.getHeader( "X-Forwarded-Context" ), is( "/upstream/context" ) );
    }
  }

  /**
   * Dummy XForwardedHeaderFilter that is used to call
   * XForwardedHeaderRequestWrapper letting it know
   * to append service name.
   * @since 1.3.0
   */
  public static class DummyXForwardedHeaderFilter extends XForwardedHeaderFilter {
    boolean isAppendServiceName;
    String serviceContext;

    DummyXForwardedHeaderFilter(final boolean isAppendServiceName, final String serviceContext) {
      super();
      this.isAppendServiceName = isAppendServiceName;
      this.serviceContext = serviceContext;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response,
                            FilterChain chain) throws IOException, ServletException {
      chain.doFilter( new XForwardedHeaderRequestWrapper( request ,
          this.isAppendServiceName, this.serviceContext), response );
    }
  }

  /**
   * Dummy class that tests for proper X-Forwarded-Context assertion.
   * @since 1.3.0
   */
  public static class AssertProxiedXForwardedContextHeaders extends TestFilterAdapter {
    boolean appendServiceName;
    String serviceContext;

    AssertProxiedXForwardedContextHeaders(boolean appendServiceName, String serviceContext) {
      super();
      this.appendServiceName = appendServiceName;
      this.serviceContext = serviceContext;
    }

    @Override
    public void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain ) {
      assertThat( request.getHeader( "X-Forwarded-For" ), is( "127.0.0.0,127.0.0.1" ) );
      assertThat( request.getHeader( "X-Forwarded-Proto" ), is( "https" ) );
      assertThat( request.getHeader( "X-Forwarded-Port" ), is( "9999" ) );
      assertThat( request.getHeader( "X-Forwarded-Host" ), is( "remotehost:9999" ) );
      assertThat( request.getHeader( "X-Forwarded-Server" ), is( "localhost" ) );
      if(serviceContext!=null) {
        assertThat( request.getHeader( "X-Forwarded-Context" ), is( "/gateway/sandbox/"+serviceContext ) );
      }
      else if(appendServiceName) {
        assertThat( request.getHeader( "X-Forwarded-Context" ), is( "/gateway/sandbox/webhdfs" ) );
      } else {
        assertThat( request.getHeader( "X-Forwarded-Context" ), is( "/gateway/sandbox" ) );
      }
    }
  }

  @Test
  public void testProxiedXForwardHeaders() throws ServletException, IOException {
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );

    EasyMock.expect( request.getHeader( "X-Forwarded-For" ) ).andReturn( "127.0.0.0" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Proto" ) ).andReturn( "https" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Port" ) ).andReturn( "9999" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Host" ) ).andReturn( "remotehost:9999" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Server" ) ).andReturn( "remotehost" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Context" ) ).andReturn( "/upstream" ).anyTimes();

    EasyMock.expect( request.getRemoteAddr() ).andReturn( "127.0.0.1" ).anyTimes();
    EasyMock.expect( request.isSecure() ).andReturn( false ).anyTimes();
    EasyMock.expect( request.getLocalPort() ).andReturn( 8888 ).anyTimes();
    EasyMock.expect( request.getHeader( "Host" ) ).andReturn( "localhost:8888" ).anyTimes();
    EasyMock.expect( request.getServerName() ).andReturn( "localhost" ).anyTimes();
    EasyMock.expect( request.getContextPath() ).andReturn( "/context" ).anyTimes();

    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( request, response );

    TestFilterChain chain = new TestFilterChain();

    XForwardedHeaderFilter filter = new XForwardedHeaderFilter();

    chain.push( new AssertProxiedXForwardedHeaders() );
    chain.push( filter );
    chain.doFilter( request, response );
  }

  /*
   * Test the case where service name is appended to X-Forwarded-Context along with request context.
   */
  @Test
  public void testProxiedXForwardContextHeaders() throws ServletException, IOException {
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );

    EasyMock.expect( request.getHeader( "X-Forwarded-For" ) ).andReturn( "127.0.0.0" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Proto" ) ).andReturn( "https" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Port" ) ).andReturn( "9999" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Host" ) ).andReturn( "remotehost:9999" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Server" ) ).andReturn( "remotehost" ).anyTimes();

    EasyMock.expect( request.getRemoteAddr() ).andReturn( "127.0.0.1" ).anyTimes();
    EasyMock.expect( request.isSecure() ).andReturn( false ).anyTimes();
    EasyMock.expect( request.getLocalPort() ).andReturn( 8888 ).anyTimes();
    EasyMock.expect( request.getHeader( "Host" ) ).andReturn( "localhost:8888" ).anyTimes();
    EasyMock.expect( request.getServerName() ).andReturn( "localhost" ).anyTimes();
    EasyMock.expect( request.getContextPath() ).andReturn( "/gateway/sandbox" ).anyTimes();
    EasyMock.expect( request.getRequestURI() ).andReturn( "/gateway/sandbox/webhdfs/key?value" ).anyTimes();

    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( request, response );

    TestFilterChain chain = new TestFilterChain();

    XForwardedHeaderFilter filter = new DummyXForwardedHeaderFilter(true, null);

    chain.push( new AssertProxiedXForwardedContextHeaders(true, null) );
    chain.push( filter );
    chain.doFilter( request, response );
  }

  /*
   * Test the case where service name is appended to X-Forwarded-Context along with request context.
   */
  @Test
  public void testProxiedXForwardContextHeadersServiceParam() throws ServletException, IOException {
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );

    EasyMock.expect( request.getHeader( "X-Forwarded-For" ) ).andReturn( "127.0.0.0" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Proto" ) ).andReturn( "https" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Port" ) ).andReturn( "9999" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Host" ) ).andReturn( "remotehost:9999" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Server" ) ).andReturn( "remotehost" ).anyTimes();

    EasyMock.expect( request.getRemoteAddr() ).andReturn( "127.0.0.1" ).anyTimes();
    EasyMock.expect( request.isSecure() ).andReturn( false ).anyTimes();
    EasyMock.expect( request.getLocalPort() ).andReturn( 8888 ).anyTimes();
    EasyMock.expect( request.getHeader( "Host" ) ).andReturn( "localhost:8888" ).anyTimes();
    EasyMock.expect( request.getServerName() ).andReturn( "localhost" ).anyTimes();
    EasyMock.expect( request.getContextPath() ).andReturn( "/gateway/sandbox" ).anyTimes();
    EasyMock.expect( request.getRequestURI() ).andReturn( "/gateway/sandbox/livy/v1/key?value" ).anyTimes();

    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( request, response );

    TestFilterChain chain = new TestFilterChain();

    XForwardedHeaderFilter filter = new DummyXForwardedHeaderFilter(true, "livy/v1");

    chain.push( new AssertProxiedXForwardedContextHeaders(true, "livy/v1") );
    chain.push( filter );
    chain.doFilter( request, response );
  }

  /*
   * Test the case where appending service name to X-Forwarded-Context is disabled
   */
  @Test
  public void testProxiedXForwardContextHeadersNegativeTest() throws ServletException, IOException {
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );

    EasyMock.expect( request.getHeader( "X-Forwarded-For" ) ).andReturn( "127.0.0.0" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Proto" ) ).andReturn( "https" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Port" ) ).andReturn( "9999" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Host" ) ).andReturn( "remotehost:9999" ).anyTimes();
    EasyMock.expect( request.getHeader( "X-Forwarded-Server" ) ).andReturn( "remotehost" ).anyTimes();

    EasyMock.expect( request.getRemoteAddr() ).andReturn( "127.0.0.1" ).anyTimes();
    EasyMock.expect( request.isSecure() ).andReturn( false ).anyTimes();
    EasyMock.expect( request.getLocalPort() ).andReturn( 8888 ).anyTimes();
    EasyMock.expect( request.getHeader( "Host" ) ).andReturn( "localhost:8888" ).anyTimes();
    EasyMock.expect( request.getServerName() ).andReturn( "localhost" ).anyTimes();
    EasyMock.expect( request.getContextPath() ).andReturn( "/gateway/sandbox" ).anyTimes();
    EasyMock.expect( request.getRequestURI() ).andReturn( "/gateway/sandbox/webhdfs/key?value" ).anyTimes();

    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( request, response );

    TestFilterChain chain = new TestFilterChain();

    XForwardedHeaderFilter filter = new DummyXForwardedHeaderFilter(false, null);

    chain.push( new AssertProxiedXForwardedContextHeaders(false, null) );
    chain.push( filter );
    chain.doFilter( request, response );
  }
}

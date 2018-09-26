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
    public void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain ) throws IOException, ServletException {
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
    public void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain ) throws IOException, ServletException {
      assertThat( request.getHeader( "X-Forwarded-For" ), is( "127.0.0.0,127.0.0.1" ) );
      assertThat( request.getHeader( "X-Forwarded-Proto" ), is( "https" ) );
      assertThat( request.getHeader( "X-Forwarded-Port" ), is( "9999" ) );
      assertThat( request.getHeader( "X-Forwarded-Host" ), is( "remotehost:9999" ) );
      assertThat( request.getHeader( "X-Forwarded-Server" ), is( "localhost" ) );
      assertThat( request.getHeader( "X-Forwarded-Context" ), is( "/upstream/context" ) );
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
}

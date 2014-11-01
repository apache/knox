/**
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
package org.apache.hadoop.gateway;

import org.apache.hadoop.gateway.audit.api.AuditServiceFactory;
import org.apache.hadoop.gateway.filter.AbstractGatewayFilter;
import org.apache.hadoop.test.category.FastTests;
import org.apache.hadoop.test.category.UnitTests;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 *
 */
@Category( { UnitTests.class, FastTests.class } )
public class GatewayFilterTest {

  @Before
  public void setup() {
    AuditServiceFactory.getAuditService().createContext();
  }

  @After
  public void reset() {
    AuditServiceFactory.getAuditService().detachContext();
  }

  @Test
  public void testNoFilters() throws ServletException, IOException {

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.replay( config );

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getPathInfo() ).andReturn( "source" ).anyTimes();
    EasyMock.replay( request );

    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    FilterChain chain = EasyMock.createNiceMock( FilterChain.class );
    EasyMock.replay( chain );

    GatewayFilter gateway = new GatewayFilter();
    gateway.init( config );
    gateway.doFilter( request, response, chain );
    gateway.destroy();
  }

  @Test
  public void testNoopFilter() throws ServletException, IOException, URISyntaxException {

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.replay( config );

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getPathInfo() ).andReturn( "source" ).anyTimes();
    EasyMock.replay( request );

    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    FilterChain chain = EasyMock.createNiceMock( FilterChain.class );
    EasyMock.replay( chain );

    Filter filter = EasyMock.createNiceMock( Filter.class );
    EasyMock.replay( filter );

    GatewayFilter gateway = new GatewayFilter();
    gateway.addFilter( "path", "filter", filter, null, null );
    gateway.init( config );
    gateway.doFilter( request, response, chain );
    gateway.destroy();

  }

  public static class TestRoleFilter extends AbstractGatewayFilter {

    public Object role;

    @Override
    protected void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain ) throws IOException, ServletException {
      this.role = request.getAttribute( AbstractGatewayFilter.TARGET_SERVICE_ROLE );
    }

  }

  @Test
  public void testTargetServiceRoleRequestAttribute() throws Exception {

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.replay( config );

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getPathInfo() ).andReturn( "test-path/test-resource" ).anyTimes();
    request.setAttribute( AbstractGatewayFilter.TARGET_SERVICE_ROLE, "test-role" );
    EasyMock.expectLastCall().anyTimes();
    EasyMock.expect( request.getAttribute( AbstractGatewayFilter.TARGET_SERVICE_ROLE ) ).andReturn( "test-role" ).anyTimes();
    EasyMock.replay( request );

    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    TestRoleFilter filter = new TestRoleFilter();

    GatewayFilter gateway = new GatewayFilter();
    gateway.addFilter( "test-path/**", "test-filter", filter, null, "test-role" );
    gateway.init( config );
    gateway.doFilter( request, response );
    gateway.destroy();

    assertThat( (String)filter.role, is( "test-role" ) );

  }

}

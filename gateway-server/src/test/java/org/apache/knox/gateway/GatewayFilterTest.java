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
package org.apache.knox.gateway;

import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.test.category.FastTests;
import org.apache.knox.test.category.UnitTests;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
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
    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    GatewayConfig gatewayConfig = EasyMock.createNiceMock( GatewayConfig.class );
    EasyMock.expect( request.getPathInfo() ).andReturn( "source" ).anyTimes();
    EasyMock.expect( request.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect( context.getAttribute(
        GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gatewayConfig).anyTimes();
    EasyMock.expect(gatewayConfig.getHeaderNameForRemoteAddress()).andReturn(
        "Custom-Forwarded-For").anyTimes();
    EasyMock.replay( request );
    EasyMock.replay( context );
    EasyMock.replay( gatewayConfig );
    
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
    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    GatewayConfig gatewayConfig = EasyMock.createNiceMock( GatewayConfig.class );
    EasyMock.expect( request.getPathInfo() ).andReturn( "source" ).anyTimes();
    EasyMock.expect( request.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect( context.getAttribute(
        GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gatewayConfig).anyTimes();
    EasyMock.expect(gatewayConfig.getHeaderNameForRemoteAddress()).andReturn(
        "Custom-Forwarded-For").anyTimes();
    EasyMock.replay( request );
    EasyMock.replay( context );
    EasyMock.replay( gatewayConfig );

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
    public String defaultServicePath;
    public String url;

    @Override
    protected void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain ) throws IOException, ServletException {
      this.role = request.getAttribute( AbstractGatewayFilter.TARGET_SERVICE_ROLE );
      Topology topology = (Topology)request.getServletContext().getAttribute( "org.apache.knox.gateway.topology" );
      if (topology != null) {
        this.defaultServicePath = (String) topology.getDefaultServicePath();
        url = new String(request.getRequestURL());
      }
    }

  }

  @Test
  public void testTargetServiceRoleRequestAttribute() throws Exception {

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.replay( config );

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    GatewayConfig gatewayConfig = EasyMock.createNiceMock( GatewayConfig.class );
    EasyMock.expect( request.getPathInfo() ).andReturn( "test-path/test-resource" ).anyTimes();
    EasyMock.expect( request.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect( context.getAttribute(
        GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gatewayConfig).anyTimes();
    EasyMock.expect(gatewayConfig.getHeaderNameForRemoteAddress()).andReturn(
        "Custom-Forwarded-For").anyTimes();
    request.setAttribute( AbstractGatewayFilter.TARGET_SERVICE_ROLE, "test-role" );
    EasyMock.expectLastCall().anyTimes();
    EasyMock.expect( request.getAttribute( AbstractGatewayFilter.TARGET_SERVICE_ROLE ) ).andReturn( "test-role" ).anyTimes();
    EasyMock.replay( request );
    EasyMock.replay( context );
    EasyMock.replay( gatewayConfig );

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

  @Test
  public void testDefaultServicePathTopologyRequestAttribute() throws Exception {

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.replay( config );

    Topology topology = EasyMock.createNiceMock( Topology.class );
    topology.setDefaultServicePath("test-role/");
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    GatewayConfig gatewayConfig = EasyMock.createNiceMock( GatewayConfig.class );
    EasyMock.expect( topology.getDefaultServicePath() ).andReturn( "test-role" ).anyTimes();
    EasyMock.expect( request.getPathInfo() ).andReturn( "/test-path/test-resource" ).anyTimes();
    EasyMock.expect( request.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect( context.getAttribute(
        GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gatewayConfig).anyTimes();
    EasyMock.expect(gatewayConfig.getHeaderNameForRemoteAddress()).andReturn(
        "Custom-Forwarded-For").anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer("http://host:8443/gateway/sandbox/test-path/test-resource/") ).anyTimes();

    EasyMock.expect( context.getAttribute( "org.apache.knox.gateway.topology" ) ).andReturn( topology ).anyTimes();
    EasyMock.replay( request );
    EasyMock.replay( context );
    EasyMock.replay( topology );
    EasyMock.replay( gatewayConfig );

    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    TestRoleFilter filter = new TestRoleFilter();

    GatewayFilter gateway = new GatewayFilter();
    gateway.addFilter( "test-role/**/**", "test-filter", filter, null, "test-role" );
    gateway.init( config );
    gateway.doFilter( request, response );
    gateway.destroy();

    assertThat( (String)filter.defaultServicePath, is( "test-role" ) );
    assertThat( (String)filter.url, is("http://host:8443/gateway/sandbox/test-role/test-path/test-resource"));

  }
}

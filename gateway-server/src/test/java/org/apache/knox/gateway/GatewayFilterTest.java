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
import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.api.CorrelationService;
import org.apache.knox.gateway.audit.api.CorrelationServiceFactory;
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

import static org.apache.knox.gateway.filter.CorrelationHandler.REQUEST_ID_HEADER_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Category( { UnitTests.class, FastTests.class } )
public class GatewayFilterTest {

  @Before
  public void setUp() {
    AuditServiceFactory.getAuditService().createContext();
  }

  @After
  public void tearDown() {
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
        this.defaultServicePath = topology.getDefaultServicePath();
        url = new String(request.getRequestURL());
      }
    }

  }

  public static class TestCorrelationFilter extends AbstractGatewayFilter {
    public String correlation_id;
    public String request_id;
    @Override
    protected void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain ) throws IOException, ServletException {
      this.request_id = request.getHeader( REQUEST_ID_HEADER_NAME );
      CorrelationService correlationService = CorrelationServiceFactory.getCorrelationService();
      CorrelationContext correlationContext = correlationService.getContext();
      correlation_id = correlationContext.getRequestId();
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

    assertThat(filter.role, is( "test-role" ) );

  }

  /**
   * make sure request id passed by request to knox is picked up as a correlation id
   * @throws Exception
   */
  @Test
  public void testLoadBalancerCorrelationID() throws Exception {

    final String TEST_REQ_ID = "7365dfbc1028ad7e4501dad3454a34c3";
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.replay( config );

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    HttpServletRequest requestNoID = EasyMock.createNiceMock( HttpServletRequest.class );

    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    GatewayConfig gatewayConfig = EasyMock.createNiceMock( GatewayConfig.class );
    EasyMock.expect( context.getAttribute(
    GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gatewayConfig).anyTimes();
    EasyMock.expect(gatewayConfig.getHeaderNameForRemoteAddress()).andReturn(
        "Custom-Forwarded-For").anyTimes();
    request.setAttribute( AbstractGatewayFilter.TARGET_SERVICE_ROLE, "test-role" );
    EasyMock.expectLastCall().anyTimes();

    EasyMock.expect( request.getPathInfo() ).andReturn( "test-path/test-resource" ).anyTimes();
    EasyMock.expect( request.getServletContext() ).andReturn( context ).anyTimes();
    EasyMock.expect( request.getHeader( REQUEST_ID_HEADER_NAME ) ).andReturn( TEST_REQ_ID ).anyTimes();
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );

    EasyMock.expect( requestNoID.getPathInfo() ).andReturn( "test-path/test-resource" ).anyTimes();
    EasyMock.expect( requestNoID.getServletContext() ).andReturn( context ).anyTimes();

    EasyMock.replay(response,request,requestNoID,context,gatewayConfig);


    TestCorrelationFilter filter = new TestCorrelationFilter();

    /* test a case where request coming into knox has request id header */
    GatewayFilter gateway = new GatewayFilter();
    gateway.addFilter( "test-path/**", "test-filter", filter, null, "test-role" );
    gateway.init( config );
    gateway.doFilter( request, response );
    assertThat(filter.request_id, is( TEST_REQ_ID ) );
    assertThat(filter.correlation_id, is( TEST_REQ_ID ) );

    /* test the case where request id for request coming to knox is absent */
    gateway.doFilter( requestNoID, response );
    assertThat(filter.request_id, nullValue() );
    assertThat(filter.correlation_id, notNullValue() );
    assertThat(filter.correlation_id, not( TEST_REQ_ID ) );
    gateway.destroy();
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

    assertThat(filter.defaultServicePath, is( "test-role" ) );
    assertThat(filter.url, is("http://host:8443/gateway/sandbox/test-role/test-path/test-resource"));

  }
}

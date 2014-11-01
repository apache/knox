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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hadoop.gateway.audit.api.Action;
import org.apache.hadoop.gateway.audit.api.ActionOutcome;
import org.apache.hadoop.gateway.audit.api.AuditContext;
import org.apache.hadoop.gateway.audit.api.AuditServiceFactory;
import org.apache.hadoop.gateway.audit.api.CorrelationContext;
import org.apache.hadoop.gateway.audit.api.ResourceType;
import org.apache.hadoop.gateway.audit.log4j.audit.AuditConstants;
import org.apache.hadoop.gateway.audit.log4j.audit.Log4jAuditService;
import org.apache.hadoop.gateway.audit.log4j.correlation.Log4jCorrelationService;
import org.apache.hadoop.gateway.dispatch.HttpClientDispatch;
import org.apache.hadoop.gateway.i18n.resources.ResourcesFactory;
import org.apache.hadoop.test.log.CollectAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AuditLoggingTest {
  private static final String PATH = "path";
  private static final String CONTEXT_PATH = "contextPath/";
  private static final String ADDRESS = "address";
  private static final String HOST = "host";

  private static final GatewayResources RES = ResourcesFactory.get( GatewayResources.class );

  @Before
  public void loggingSetup() {
    AuditServiceFactory.getAuditService().createContext();
    CollectAppender.queue.clear();
  }

  @After
  public void reset() {
    AuditServiceFactory.getAuditService().detachContext();
  }

  @Test
  /**
   * Empty filter chain. Two events with same correlation ID are expected:
   * 
   * action=access request_type=uri outcome=unavailable
   * action=access request_type=uri outcome=success message=Response status: 404
   */
  public void testNoFiltersAudit() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.replay( config );

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getPathInfo() ).andReturn( PATH ).anyTimes();
    EasyMock.expect( request.getContextPath() ).andReturn( CONTEXT_PATH ).anyTimes();
    EasyMock.expect( request.getRemoteAddr() ).andReturn( ADDRESS ).anyTimes();
    EasyMock.expect( request.getRemoteHost() ).andReturn( HOST ).anyTimes();

    EasyMock.replay( request );

    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    FilterChain chain = EasyMock.createNiceMock( FilterChain.class );
    EasyMock.replay( chain );

    GatewayFilter gateway = new GatewayFilter();
    gateway.init( config );
    gateway.doFilter( request, response, chain );
    gateway.destroy();

    assertThat( CollectAppender.queue.size(), is( 1 ) );
    Iterator<LoggingEvent> iterator = CollectAppender.queue.iterator();
    LoggingEvent accessEvent = iterator.next();
    verifyAuditEvent( accessEvent, CONTEXT_PATH + PATH, ResourceType.URI, Action.ACCESS, ActionOutcome.UNAVAILABLE, null, null );
  }

  @Test
  /**
   * One NoOp filter in chain. Single audit event with same with specified request URI is expected:
   * 
   * action=access request_type=uri outcome=unavailable
   */
  public void testNoopFilter() throws ServletException, IOException,
      URISyntaxException {

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.replay( config );

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getPathInfo() ).andReturn( PATH ).anyTimes();
    EasyMock.expect( request.getContextPath() ).andReturn( CONTEXT_PATH ).anyTimes();
    EasyMock.expect( request.getRemoteAddr() ).andReturn( ADDRESS ).anyTimes();
    EasyMock.expect( request.getRemoteHost() ).andReturn( HOST ).anyTimes();
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

    assertThat( CollectAppender.queue.size(), is( 1 ) );
    Iterator<LoggingEvent> iterator = CollectAppender.queue.iterator();
    LoggingEvent accessEvent = iterator.next();
    verifyAuditEvent( accessEvent, CONTEXT_PATH + PATH, ResourceType.URI,
        Action.ACCESS, ActionOutcome.UNAVAILABLE, null, null );

  }

  @Test
  /**
   * Dispatching outbound request. Remote host is unreachable. Two log events is expected:
   * 
   * action=dispatch request_type=uri outcome=FAILED
   * action=dispatch request_type=uri outcome=unavailable
   */
  public void testHttpClientOutboundException() throws IOException,
      URISyntaxException {
    String uri = "http://outbound-host:port/path";

    HttpServletRequest inboundRequest = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( inboundRequest.getHeaderNames() ).andReturn( Collections.enumeration( new ArrayList<String>() ) ).anyTimes();
    EasyMock.replay( inboundRequest );

    HttpServletResponse outboundResponse = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( outboundResponse );

    HttpClientDispatch dispatch = new HttpClientDispatch();
    try {
      dispatch.doGet( new URI( uri ), inboundRequest, outboundResponse );
      fail( "Expected exception while accessing to unreachable host" );
    } catch ( IOException e ) {
      Iterator<LoggingEvent> iterator = CollectAppender.queue.iterator();
      LoggingEvent failureEvent = iterator.next();
      verifyValue( (String) failureEvent.getMDC( AuditConstants.MDC_RESOURCE_NAME_KEY ), uri );
      verifyValue( (String) failureEvent.getMDC( AuditConstants.MDC_RESOURCE_TYPE_KEY ), ResourceType.URI );
      verifyValue( (String) failureEvent.getMDC( AuditConstants.MDC_ACTION_KEY ), Action.DISPATCH );
      verifyValue( (String) failureEvent.getMDC( AuditConstants.MDC_OUTCOME_KEY ), ActionOutcome.FAILURE );

      LoggingEvent unavailableEvent = iterator.next();
      verifyValue( (String) unavailableEvent.getMDC( AuditConstants.MDC_RESOURCE_NAME_KEY ), uri );
      verifyValue( (String) unavailableEvent.getMDC( AuditConstants.MDC_RESOURCE_TYPE_KEY ), ResourceType.URI );
      verifyValue( (String) unavailableEvent.getMDC( AuditConstants.MDC_ACTION_KEY ), Action.DISPATCH );
      verifyValue( (String) unavailableEvent.getMDC( AuditConstants.MDC_OUTCOME_KEY ), ActionOutcome.UNAVAILABLE );
    }
  }

  private void verifyAuditEvent( LoggingEvent event, String resourceName,
      String resourceType, String action, String outcome, String targetService,
      String message ) {
    event.getMDCCopy();
    CorrelationContext cc = (CorrelationContext) event.getMDC( Log4jCorrelationService.MDC_CORRELATION_CONTEXT_KEY );
    assertThat( cc, notNullValue() );
    assertThat( cc.getRequestId(), is( notNullValue() ) );
    AuditContext ac = (AuditContext) event.getMDC( Log4jAuditService.MDC_AUDIT_CONTEXT_KEY );
    assertThat( ac, notNullValue() );
    assertThat( ac.getRemoteIp(), is( ADDRESS ) );
    assertThat( ac.getRemoteHostname(), is( HOST ) );
    assertThat( (String) event.getMDC( AuditConstants.MDC_SERVICE_KEY ), is( AuditConstants.KNOX_SERVICE_NAME ) );
    assertThat( (String) event.getMDC( AuditConstants.MDC_COMPONENT_KEY ), is( AuditConstants.KNOX_COMPONENT_NAME ) );
    assertThat( (String) event.getLoggerName(), is( AuditConstants.DEFAULT_AUDITOR_NAME ) );
    verifyValue( (String) event.getMDC( AuditConstants.MDC_RESOURCE_NAME_KEY ), resourceName );
    verifyValue( (String) event.getMDC( AuditConstants.MDC_RESOURCE_TYPE_KEY ), resourceType );
    verifyValue( (String) event.getMDC( AuditConstants.MDC_ACTION_KEY ), action );
    verifyValue( (String) event.getMDC( AuditConstants.MDC_OUTCOME_KEY ), outcome );
    verifyValue( ac.getTargetServiceName(), targetService );
    verifyValue( event.getRenderedMessage(), message );
  }

  private void verifyValue( String actual, String expected ) {
    if( expected == null ) {
      assertThat( actual, nullValue() );
    } else {
      assertThat( actual, is( expected ) );
    }
  }

  private String getRequestId( LoggingEvent event ) {
    CorrelationContext cc = (CorrelationContext) event
        .getMDC( Log4jCorrelationService.MDC_CORRELATION_CONTEXT_KEY );
    return cc == null ? null : cc.getRequestId();
  }

}

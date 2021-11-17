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

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.audit.log4j.audit.Log4jAuditContext;
import org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationContext;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.dispatch.DefaultDispatch;
import org.apache.knox.test.log.CollectAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.fail;

public class AuditLoggingTest {
  private static Logger LOG = LogManager.getLogger( AuditLoggingTest.class );

  private static final String METHOD = "GET";
  private static final String PATH = "path";
  private static final String CONTEXT_PATH = "contextPath/";
  private static final String ADDRESS = "address";
  private static final String HOST = "host";

  @Before
  public void setUp() {
    AuditServiceFactory.getAuditService().createContext();
    CollectAppender.queue.clear();
  }

  @After
  public void tearDown() {
    AuditServiceFactory.getAuditService().detachContext();
  }

  /*
   * Empty filter chain. Two events with same correlation ID are expected:
   *
   * action=access request_type=uri outcome=unavailable
   * action=access request_type=uri outcome=success message=Response status: 404
   */
  @Test
  public void testNoFiltersAudit() throws Exception {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.replay( config );

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    GatewayConfig gatewayConfig = EasyMock.createNiceMock( GatewayConfig.class );
    EasyMock.expect( request.getMethod() ).andReturn( METHOD ).anyTimes();
    EasyMock.expect( request.getPathInfo() ).andReturn( PATH ).anyTimes();
    EasyMock.expect( request.getContextPath() ).andReturn( CONTEXT_PATH ).anyTimes();
    EasyMock.expect( request.getRemoteAddr() ).andReturn( ADDRESS ).anyTimes();
    EasyMock.expect( request.getRemoteHost() ).andReturn( HOST ).anyTimes();
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

    Random rnd = ThreadLocalRandom.current();

    // Make number of total requests between 1-100
    int numberTotalRequests = rnd.nextInt(99) + 1;
    Set<Callable<Void>> callables = new HashSet<>(numberTotalRequests);
    for (int i = 0; i < numberTotalRequests; i++) {
      callables.add(() -> {
        GatewayFilter gateway = new GatewayFilter();
        gateway.init( config );
        gateway.doFilter( request, response, chain );
        gateway.destroy();
        return null;
      });
    }

    // Make number of concurrent requests between 1-10
    int numberConcurrentRequests = rnd.nextInt( 9) + 1;

    LOG.info("Executing %d total requests with %d concurrently", numberTotalRequests, numberConcurrentRequests);

    ExecutorService executor = Executors.newFixedThreadPool(numberConcurrentRequests);
    executor.invokeAll(callables);
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);
    assertThat(executor.isTerminated(), is(true));

    assertThat( CollectAppender.queue.size(), is( numberTotalRequests ) );

    // Use a set to make sure to dedupe any requestIds to get only unique ones
    Set<String> requestIds = new HashSet<>();
    for (LogEvent accessEvent : CollectAppender.queue) {
      verifyAuditEvent( accessEvent, CONTEXT_PATH + PATH, ResourceType.URI, Action.ACCESS, ActionOutcome.UNAVAILABLE, null, "Request method: GET" );

      CorrelationContext cc = Log4jCorrelationContext.of(accessEvent);
      // There are some events that do not have a CorrelationContext associated (ie: deploy)
      if(cc != null) {
        requestIds.add(cc.getRequestId());
      }
    }

    // There should be a unique correlation id for each request
    assertThat(requestIds.size(), is(numberTotalRequests));
  }

  /*
   * One NoOp filter in chain. Single audit event with same with specified request URI is expected:
   *
   * action=access request_type=uri outcome=unavailable
   */
  @Test
  public void testNoopFilter() throws ServletException, IOException,
      URISyntaxException {

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.replay( config );

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    GatewayConfig gatewayConfig = EasyMock.createNiceMock( GatewayConfig.class );
    EasyMock.expect( request.getMethod() ).andReturn( METHOD ).anyTimes();
    EasyMock.expect( request.getPathInfo() ).andReturn( PATH ).anyTimes();
    EasyMock.expect( request.getContextPath() ).andReturn( CONTEXT_PATH ).anyTimes();
    EasyMock.expect( request.getRemoteAddr() ).andReturn( ADDRESS ).anyTimes();
    EasyMock.expect( request.getRemoteHost() ).andReturn( HOST ).anyTimes();
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

    assertThat( CollectAppender.queue.size(), is( 1 ) );
    Iterator<LogEvent> iterator = CollectAppender.queue.iterator();
    LogEvent accessEvent = iterator.next();
    verifyAuditEvent( accessEvent, CONTEXT_PATH + PATH, ResourceType.URI,
        Action.ACCESS, ActionOutcome.UNAVAILABLE, null, "Request method: GET" );

  }

  /*
   * Dispatching outbound request. Remote host is unreachable. Two log events is expected:
   *
   * action=dispatch request_type=uri outcome=FAILED
   * action=dispatch request_type=uri outcome=unavailable
   */
  @Test
  public void testHttpClientOutboundException() throws IOException,
      URISyntaxException {
    String uri = "http://outbound-host.invalid:port/path";

    HttpServletRequest inboundRequest = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( inboundRequest.getHeaderNames() ).andReturn( Collections.enumeration(new ArrayList<>() ) ).anyTimes();
    EasyMock.replay( inboundRequest );

    HttpServletResponse outboundResponse = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( outboundResponse );

    DefaultDispatch dispatch = new DefaultDispatch();
    HttpClientBuilder builder = HttpClientBuilder.create();
    CloseableHttpClient client = builder.build();
    dispatch.setHttpClient(client);
    try {
      dispatch.doGet( new URI( uri ), inboundRequest, outboundResponse );
      fail( "Expected exception while accessing to unreachable host" );
    } catch ( IOException e ) {
      Iterator<LogEvent> iterator = CollectAppender.queue.iterator();

      LogEvent unavailableEvent = iterator.next();
      verifyValue(unavailableEvent.getContextData().getValue( AuditConstants.MDC_RESOURCE_NAME_KEY ), uri );
      verifyValue(unavailableEvent.getContextData().getValue( AuditConstants.MDC_RESOURCE_TYPE_KEY ), ResourceType.URI );
      verifyValue(unavailableEvent.getContextData().getValue( AuditConstants.MDC_ACTION_KEY ), Action.DISPATCH );
      verifyValue(unavailableEvent.getContextData().getValue( AuditConstants.MDC_OUTCOME_KEY ), ActionOutcome.UNAVAILABLE );

      LogEvent failureEvent = iterator.next();
      verifyValue(failureEvent.getContextData().getValue( AuditConstants.MDC_RESOURCE_NAME_KEY ), uri );
      verifyValue(failureEvent.getContextData().getValue( AuditConstants.MDC_RESOURCE_TYPE_KEY ), ResourceType.URI );
      verifyValue(failureEvent.getContextData().getValue( AuditConstants.MDC_ACTION_KEY ), Action.DISPATCH );
      verifyValue(failureEvent.getContextData().getValue( AuditConstants.MDC_OUTCOME_KEY ), ActionOutcome.FAILURE );

    }
  }

  private void verifyAuditEvent( LogEvent event, String resourceName,
      String resourceType, String action, String outcome, String targetService,
      String message ) {

    ReadOnlyStringMap eventContextData = event.getContextData();

    CorrelationContext cc = Log4jCorrelationContext.of(event);
    assertThat(cc, notNullValue());
    assertThat(cc.getRequestId(), is(notNullValue()));
    AuditContext ac = Log4jAuditContext.of(event);
    assertThat(ac, notNullValue());
    assertThat(ac.getRemoteIp(), is(ADDRESS));
    assertThat(ac.getRemoteHostname(), is(HOST));
    assertThat(eventContextData.getValue(AuditConstants.MDC_SERVICE_KEY), is(AuditConstants.KNOX_SERVICE_NAME));
    assertThat(eventContextData.getValue(AuditConstants.MDC_COMPONENT_KEY), is(AuditConstants.KNOX_COMPONENT_NAME));
    assertThat(event.getLoggerName(), is(AuditConstants.DEFAULT_AUDITOR_NAME));
    verifyValue(eventContextData.getValue(AuditConstants.MDC_RESOURCE_NAME_KEY), resourceName);
    verifyValue(eventContextData.getValue(AuditConstants.MDC_RESOURCE_TYPE_KEY), resourceType);
    verifyValue(eventContextData.getValue(AuditConstants.MDC_ACTION_KEY), action);
    verifyValue(eventContextData.getValue(AuditConstants.MDC_OUTCOME_KEY), outcome);
    verifyValue(ac.getTargetServiceName(), targetService);
    verifyValue(event.getMessage().getFormattedMessage(), message);
  }

  private void verifyValue( String actual, String expected ) {
    if( expected == null ) {
      assertThat( actual, nullValue() );
    } else {
      assertThat( actual, is( expected ) );
    }
  }
}

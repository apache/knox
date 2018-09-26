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
package org.apache.knox.gateway.audit;

import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.api.CorrelationService;
import org.apache.knox.gateway.audit.api.CorrelationServiceFactory;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.audit.log4j.audit.Log4jAuditService;
import org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationService;
import org.apache.knox.test.log.CollectAppender;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Iterator;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.nullValue;

public class AuditServiceTest {
  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static CorrelationService correlationService = CorrelationServiceFactory.getCorrelationService();
  private static Auditor auditor = AuditServiceFactory.getAuditService().getAuditor( "audit.forward", AuditConstants.KNOX_COMPONENT_NAME, AuditConstants.KNOX_SERVICE_NAME );
  
  private String username = "user";
  private String proxyUsername = "proxyuser";
  private String remoteIp = "127.0.0.1";
  private String remoteHostname = "localhost";
  private String targetServiceName = "service";

  @Before
  public void setup() {
    cleanup();
  }

  @After
  public void cleanup() {
    CollectAppender.queue.clear();
    LogManager.shutdown();
    String absolutePath = "target/audit";
    File db = new File( absolutePath + ".db" );
    if( db.exists() ) {
      assertThat( "Failed to delete audit store db file.", db.delete(), is( true ) );
    }
    File lg = new File( absolutePath + ".lg" );
    if( lg.exists() ) {
      assertThat( "Failed to delete audit store lg file.", lg.delete(), is( true ) );
    }
    PropertyConfigurator.configure( ClassLoader.getSystemResourceAsStream( "audit-log4j.properties" ) );
  }

  @Test
  public void testMultipleRequestEvents() {
    int iterations = 1000;
    
    AuditContext ac = auditService.createContext();
    ac.setUsername( username );
    ac.setProxyUsername( proxyUsername );
    ac.setRemoteIp( remoteIp );
    ac.setRemoteHostname( remoteHostname );
    ac.setTargetServiceName( targetServiceName );
    
    CorrelationContext cc = correlationService.createContext();
    cc.setRequestId( UUID.randomUUID().toString() );
    cc.setParentRequestId( UUID.randomUUID().toString() );
    cc.setRootRequestId( UUID.randomUUID().toString() );
    
    CollectAppender.queue.clear();
    for( int i = 0; i < iterations; i++ ) {
      auditor.audit( "action" + i, "resource" + i, "resource type" + i, "outcome" + i, "message" + i );
    }
    
    auditService.detachContext();
    correlationService.detachContext();
    assertThat( CollectAppender.queue.size(), is( iterations ) );
    
    //Verify events number and audit/correlation parameters in each event
    Iterator<LoggingEvent> iterator = CollectAppender.queue.iterator();
    int counter = 0;
    while(iterator.hasNext()) {
      LoggingEvent event = iterator.next();
      checkLogEventContexts( event, cc, ac );
      
      assertThat( (String)event.getMDC( AuditConstants.MDC_ACTION_KEY ), is( "action" + counter ) );
      assertThat( (String)event.getMDC( AuditConstants.MDC_RESOURCE_NAME_KEY ), is( "resource" + counter ) );
      assertThat( (String)event.getMDC( AuditConstants.MDC_RESOURCE_TYPE_KEY ), is( "resource type" + counter ) );
      assertThat( (String)event.getMDC( AuditConstants.MDC_OUTCOME_KEY ), is( "outcome" + counter ) );
      assertThat( (String)event.getMDC( AuditConstants.MDC_SERVICE_KEY ), is( AuditConstants.KNOX_SERVICE_NAME ) );
      assertThat( (String)event.getMDC( AuditConstants.MDC_COMPONENT_KEY ), is( AuditConstants.KNOX_COMPONENT_NAME ) );
      assertThat( (String)event.getRenderedMessage(), is( "message" + counter ) );

      counter++;
    }
    assertThat( auditService.getContext(), nullValue() );
    assertThat( correlationService.getContext(), nullValue() );
    
  }

  @Test
  public void testSequentialRequests() {
    AuditContext ac = auditService.createContext();
    ac.setUsername( username );
    ac.setProxyUsername( proxyUsername );
    ac.setRemoteIp( remoteIp );
    ac.setRemoteHostname( remoteHostname );
    ac.setTargetServiceName( targetServiceName );
    
    CorrelationContext cc = correlationService.createContext();
    cc.setRequestId( UUID.randomUUID().toString() );
    cc.setParentRequestId( UUID.randomUUID().toString() );
    cc.setRootRequestId( UUID.randomUUID().toString() );

    auditor.audit( "action", "resource", "resource type", "outcome", "message" );
    
    auditService.detachContext();
    correlationService.detachContext();
    
    assertThat( CollectAppender.queue.size(), is( 1 ) );
    LoggingEvent event = CollectAppender.queue.iterator().next();
    checkLogEventContexts( event, cc, ac );
    
    CollectAppender.queue.clear();
    
    ac = auditService.createContext();
    ac.setUsername( username + "1" );
    ac.setProxyUsername( proxyUsername + "1" );
    ac.setRemoteIp( remoteIp + "1" );
    ac.setRemoteHostname( remoteHostname + "1" );
    ac.setTargetServiceName( targetServiceName + "1" );
    
    cc = correlationService.createContext();
    cc.setRequestId( UUID.randomUUID().toString() );
    cc.setParentRequestId( UUID.randomUUID().toString() );
    cc.setRootRequestId( UUID.randomUUID().toString() );
    
    auditor.audit( "action", "resource", "resource type", "outcome", "message" );
    
    auditService.detachContext();
    correlationService.detachContext();
    
    assertThat( CollectAppender.queue.size(), is( 1 ) );
    event = CollectAppender.queue.iterator().next();
    checkLogEventContexts( event, cc, ac );
  }
  
  private void checkLogEventContexts( LoggingEvent event, CorrelationContext expectedCorrelationContext, AuditContext expectedAuditContext ) {
    AuditContext context = (AuditContext) event.getMDC( Log4jAuditService.MDC_AUDIT_CONTEXT_KEY );
    assertThat( context.getUsername(), is( expectedAuditContext.getUsername() ) );
    assertThat( context.getProxyUsername(), is( expectedAuditContext.getProxyUsername() ) );
    assertThat( context.getSystemUsername(), is( expectedAuditContext.getSystemUsername() ) );
    assertThat( context.getRemoteIp(), is( expectedAuditContext.getRemoteIp() ) );
    assertThat( context.getRemoteHostname(), is( expectedAuditContext.getRemoteHostname() ) );
    assertThat( context.getTargetServiceName(), is( expectedAuditContext.getTargetServiceName() ) );
         
    CorrelationContext correlationContext = (CorrelationContext)event.getMDC( Log4jCorrelationService.MDC_CORRELATION_CONTEXT_KEY );
    assertThat( correlationContext.getRequestId(), is( expectedCorrelationContext.getRequestId() ) );
    assertThat( correlationContext.getRootRequestId(), is( expectedCorrelationContext.getRootRequestId() ) );
    assertThat( correlationContext.getParentRequestId(), is( expectedCorrelationContext.getParentRequestId() ) );
  }

}

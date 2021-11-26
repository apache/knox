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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Locale;

import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.api.CorrelationService;
import org.apache.knox.gateway.audit.api.CorrelationServiceFactory;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationContext;
import org.apache.knox.gateway.audit.log4j.layout.AuditLayout;
import org.apache.knox.test.log.CollectAppender;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AuditLayoutTest {
  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static CorrelationService correlationService = CorrelationServiceFactory
      .getCorrelationService();
  private static Auditor auditor = auditService.getAuditor( "audit.forward", AuditConstants.KNOX_COMPONENT_NAME, AuditConstants.KNOX_SERVICE_NAME );
  private static AuditLayout layout = new AuditLayout(StandardCharsets.UTF_8);

  private static final String USERNAME = "username";
  private static final String PROXYUSERNAME = "proxy_username";
  private static final String SYSTEMUSERNAME = "system_username";
  private static final String HOST_NAME = "hostname";
  private static final String HOST_ADDRESS = "hostaddress";
  private static final String ACTION = "action";
  private static final String OUTCOME = "outcome";
  private static final String RESOURCE_NAME = "resource_name";
  private static final String RESOURCE_TYPE = "resource_type";
  private static final String TARGET_SERVICE = "WEBHDFS";
  private static final String MESSAGE = "message";
  private static final String ROOT_REQUEST_ID = "1";
  private static final String PARENT_REQUEST_ID = "2";
  private static final String REQUEST_ID = "3";
  private static final String EMPTY = "";
  private static final String RECORD_PATTERN = "%s %s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s%s";

  @Before
  public void setUp() {
    tearDown();
  }

  @After
  public void tearDown() {
    CollectAppender.queue.clear();
    auditService.detachContext();
    correlationService.detachContext();
    String absolutePath = "target/audit";
    File db = new File( absolutePath + ".db" );
    if( db.exists() ) {
      assertThat( "Failed to delete audit store db file.", db.delete(), is( true ) );
    }
    File lg = new File( absolutePath + ".lg" );
    if( lg.exists() ) {
      assertThat( "Failed to delete audit store lg file.", lg.delete(), is( true ) );
    }
  }

  @Test
  public void testFullyFilledAuditEvent() {
    AuditContext auditContext = auditService.createContext();
    auditContext.setProxyUsername( PROXYUSERNAME );
    auditContext.setSystemUsername( SYSTEMUSERNAME );
    auditContext.setUsername( USERNAME );
    auditContext.setRemoteHostname( HOST_NAME );
    auditContext.setRemoteIp( HOST_ADDRESS );
    auditContext.setTargetServiceName( TARGET_SERVICE );

    auditService.attachContext(auditContext);

    CorrelationContext correlationContext = new Log4jCorrelationContext(
            REQUEST_ID, PARENT_REQUEST_ID, ROOT_REQUEST_ID);
    correlationService.attachContext(correlationContext);
    auditor.audit( ACTION, RESOURCE_NAME, RESOURCE_TYPE, OUTCOME, MESSAGE );

    assertThat( CollectAppender.queue.size(), is( 1 ) );
    LogEvent event = CollectAppender.queue.iterator().next();
    SimpleDateFormat format = new SimpleDateFormat( "yy/MM/dd HH:mm:ss", Locale.getDefault() );
    String formatedDate = format.format( event.getTimeMillis() );
    //14/01/24 12:40:24 1|2|3|audit.forward|hostaddress|WEBHDFS|username|proxy_username|system_username|action|resource_type|resource_name|outcome|message
    String expectedOutput = String.format(Locale.ROOT,
        RECORD_PATTERN, formatedDate,
        ROOT_REQUEST_ID, PARENT_REQUEST_ID, REQUEST_ID, "audit.forward",
        HOST_ADDRESS, TARGET_SERVICE, USERNAME, PROXYUSERNAME, SYSTEMUSERNAME, ACTION,
        RESOURCE_TYPE, RESOURCE_NAME, OUTCOME, MESSAGE, System.lineSeparator() );
    String auditOutput = layout.toSerializable( event );
    assertThat( auditOutput, is( expectedOutput ) );
  }

  @Test
  public void testAuditEventWithoutContexts() {
    auditor.audit( ACTION, RESOURCE_NAME, RESOURCE_TYPE, OUTCOME, MESSAGE );
    assertThat( CollectAppender.queue.size(), is( 1 ) );
    LogEvent event = CollectAppender.queue.iterator().next();
    SimpleDateFormat format = new SimpleDateFormat( "yy/MM/dd HH:mm:ss", Locale.getDefault() );
    String formatedDate = format.format( event.getTimeMillis() );
    //14/01/24 12:41:47 |||audit.forward|||||action|resource_type|resource_name|outcome|message
    String expectedOutput = String.format( Locale.ROOT,
        RECORD_PATTERN, formatedDate,
        EMPTY, EMPTY, EMPTY, "audit.forward",
        EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, ACTION, RESOURCE_TYPE, RESOURCE_NAME, OUTCOME, MESSAGE, System.lineSeparator() );
    String auditOutput = layout.toSerializable( event );
    assertThat( auditOutput, is( expectedOutput ) );
  }

  @Test
  public void testAuditEventWithoutMessage() {
    auditor.audit( ACTION, RESOURCE_NAME, RESOURCE_TYPE, OUTCOME );
    assertThat( CollectAppender.queue.size(), is( 1 ) );
    LogEvent event = CollectAppender.queue.iterator().next();
    SimpleDateFormat format = new SimpleDateFormat( "yy/MM/dd HH:mm:ss", Locale.getDefault() );
    String formatedDate = format.format(event.getTimeMillis());
    //14/01/24 12:41:47 |||audit.forward|||||action|resource_type|resource_name|outcome|
    String expectedOutput = String.format( Locale.ROOT,
        RECORD_PATTERN, formatedDate,
        EMPTY, EMPTY, EMPTY, "audit.forward",
        EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, ACTION, RESOURCE_TYPE, RESOURCE_NAME, OUTCOME, EMPTY, System.lineSeparator() );
    String auditOutput = layout.toSerializable( event );
    assertThat( auditOutput, is( expectedOutput ) );
  }
}

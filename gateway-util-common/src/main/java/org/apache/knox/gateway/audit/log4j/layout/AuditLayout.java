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
package org.apache.knox.gateway.audit.log4j.layout;

import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.audit.log4j.audit.Log4jAuditService;
import org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationService;
import org.apache.log4j.helpers.DateLayout;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Formats audit record to following output:
 * date time root_request_id|parent_request_id|request_id|channel|target_service|username|proxy_username|system_username|action|resource_type|resource_name|outcome|message
 */
public class AuditLayout extends DateLayout {
  
  private static final String DATE_FORMAT = "yy/MM/dd HH:mm:ss";
  private static final String SEPARATOR = "|";
  private StringBuffer sb = new StringBuffer();
  
  @Override
  public void activateOptions() {
    setDateFormat( DATE_FORMAT );
  }

  @Override
  public String format( LoggingEvent event ) {
    sb.setLength( 0 );
    dateFormat( sb, event );
    CorrelationContext cc = (CorrelationContext)event.getMDC( Log4jCorrelationService.MDC_CORRELATION_CONTEXT_KEY );
    AuditContext ac = (AuditContext)event.getMDC( Log4jAuditService.MDC_AUDIT_CONTEXT_KEY );
    appendParameter( cc == null ? null : cc.getRootRequestId() );
    appendParameter( cc == null ? null : cc.getParentRequestId() );
    appendParameter( cc == null ? null : cc.getRequestId() );
    appendParameter( event.getLoggerName() );
    appendParameter( ac == null ? null : ac.getRemoteIp() );
    appendParameter( ac == null ? null : ac.getTargetServiceName() );
    appendParameter( ac == null ? null : ac.getUsername() );
    appendParameter( ac == null ? null : ac.getProxyUsername() );
    appendParameter( ac == null ? null : ac.getSystemUsername() );
    appendParameter( (String)event.getMDC( AuditConstants.MDC_ACTION_KEY ) );
    appendParameter( (String)event.getMDC( AuditConstants.MDC_RESOURCE_TYPE_KEY ) );
    appendParameter( (String)event.getMDC( AuditConstants.MDC_RESOURCE_NAME_KEY ) );
    appendParameter( (String)event.getMDC( AuditConstants.MDC_OUTCOME_KEY ) );
    String message = event.getRenderedMessage();
    sb.append( message == null ? "" : message ).append( LINE_SEP );
    return sb.toString();
  }

  @Override
  public boolean ignoresThrowable() {
    return true;
  }
  
  private void appendParameter( String parameter ) {
    if ( parameter != null ) {
      sb.append( parameter );
    }
    sb.append( SEPARATOR );
  }

}

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
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.Strings;

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Formats audit record to following output:
 * date time root_request_id|parent_request_id|request_id|channel|target_service|username|proxy_username|system_username|action|resource_type|resource_name|outcome|message
 */
public class AuditLayout extends AbstractStringLayout {
  private final DateFormat DATE_FORMATTER;
  private static final Character SEPARATOR = '|';
  private StringBuffer sb = new StringBuffer();

  public AuditLayout(Charset charset) {
    super(charset);
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yy/MM/dd HH:mm:ss ", Locale.getDefault());
    simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    DATE_FORMATTER = simpleDateFormat;
  }

  private void appendParameter( String parameter ) {
    if ( parameter != null ) {
      sb.append( parameter );
    }
    sb.append( SEPARATOR );
  }

  @Override
  public String toSerializable(LogEvent event) {
    sb.setLength( 0 );
    sb.append(DATE_FORMATTER.format(event.getTimeMillis()));
    CorrelationContext cc = Log4jCorrelationService.createContext(event);
    AuditContext ac = Log4jAuditService.createContext(event);
    appendParameter( cc == null ? null : cc.getRootRequestId() );
    appendParameter( cc == null ? null : cc.getParentRequestId() );
    appendParameter( cc == null ? null : cc.getRequestId() );
    appendParameter( event.getLoggerName() );
    appendParameter( ac == null ? null : ac.getRemoteIp() );
    appendParameter( ac == null ? null : ac.getTargetServiceName() );
    appendParameter( ac == null ? null : ac.getUsername() );
    appendParameter( ac == null ? null : ac.getProxyUsername() );
    appendParameter( ac == null ? null : ac.getSystemUsername() );
    ReadOnlyStringMap eventContextData = event.getContextData();
    appendParameter( eventContextData.getValue( AuditConstants.MDC_ACTION_KEY ) );
    appendParameter( eventContextData.getValue( AuditConstants.MDC_RESOURCE_TYPE_KEY ) );
    appendParameter( eventContextData.getValue( AuditConstants.MDC_RESOURCE_NAME_KEY ) );
    appendParameter( eventContextData.getValue( AuditConstants.MDC_OUTCOME_KEY ) );
    String message = event.getMessage().getFormattedMessage();
    sb.append( "null".equals(message) ? Strings.EMPTY : message ).append( System.lineSeparator() );
    return sb.toString();
  }
}

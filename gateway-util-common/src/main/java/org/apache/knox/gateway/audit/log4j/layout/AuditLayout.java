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

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.audit.log4j.audit.Log4jAuditContext;
import org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationContext;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.Strings;

/**
 * Formats audit record to following output:
 * date time root_request_id|parent_request_id|request_id|channel|target_service|username|proxy_username|system_username|action|resource_type|resource_name|outcome|message
 */
@Plugin(name = "AuditLayout", category = Core.CATEGORY_NAME, elementType = Layout.ELEMENT_TYPE, printObject = true)
public class AuditLayout extends AbstractStringLayout {
  private static final String DATE_PATTERN = "yy/MM/dd HH:mm:ss ";
  private final DateFormat dateFormat;
  private static final Character SEPARATOR = '|';
  private final StringBuffer sb = new StringBuffer();

  @PluginFactory
  public static AuditLayout createLayout(@PluginAttribute(value = "charset", defaultString = "UTF-8") Charset charset) {
    return new AuditLayout(charset);
  }

  public AuditLayout(Charset charset) {
    super(charset);
    dateFormat = dateFormat();
  }

  private SimpleDateFormat dateFormat() {
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_PATTERN, Locale.getDefault(Locale.Category.FORMAT));
    simpleDateFormat.setTimeZone(TimeZone.getDefault());
    return simpleDateFormat;
  }

  @Override
  public String toSerializable(LogEvent event) {
    sb.setLength( 0 );
    sb.append(dateFormat.format(event.getTimeMillis()));
    CorrelationContext cc = Log4jCorrelationContext.of(event);
    appendParameter( cc == null ? null : cc.getRootRequestId() );
    appendParameter( cc == null ? null : cc.getParentRequestId() );
    appendParameter( cc == null ? null : cc.getRequestId() );
    appendParameter( event.getLoggerName() );
    AuditContext ac = Log4jAuditContext.of(event);
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
    String message = event.getMessage() == null ? null : event.getMessage().getFormattedMessage();
    sb.append(message == null || "null".equals(message) ? Strings.EMPTY : message).append( System.lineSeparator());
    return sb.toString();
  }

  private void appendParameter( String parameter ) {
    if ( parameter != null ) {
      sb.append( parameter );
    }
    sb.append( SEPARATOR );
  }
}

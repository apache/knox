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
package org.apache.knox.gateway.audit.log4j.appender;

import static org.apache.knox.gateway.audit.log4j.audit.Log4jAuditService.MDC_AUDIT_CONTEXT_KEY;
import static org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationService.KEY_CORRELATION_CONTEXT;
import static org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationService.KEY_PARENT_REQUEST_ID;
import static org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationService.KEY_REQUEST_ID;
import static org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationService.KEY_ROOT_REQUEST_ID;

import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.SortedArrayStringMap;

final class AuditEvents {
  private final SortedArrayStringMap data = new SortedArrayStringMap();
  private String message;
  private long timeMillis = 1_763_380_800_123L;

  private AuditEvents() {
  }

  static AuditEvents event(String action, String resourceType, String resourceName, String outcome) {
    AuditEvents events = new AuditEvents();
    events.put(AuditConstants.MDC_ACTION_KEY, action);
    events.put(AuditConstants.MDC_RESOURCE_TYPE_KEY, resourceType);
    events.put(AuditConstants.MDC_RESOURCE_NAME_KEY, resourceName);
    events.put(AuditConstants.MDC_OUTCOME_KEY, outcome);
    events.put(AuditConstants.MDC_SERVICE_KEY, AuditConstants.KNOX_SERVICE_NAME);
    events.put(AuditConstants.MDC_COMPONENT_KEY, AuditConstants.KNOX_COMPONENT_NAME);
    return events;
  }

  AuditEvents users(String username, String proxyUsername, String systemUsername) {
    put(MDC_AUDIT_CONTEXT_KEY, "true");
    put(MDC_AUDIT_CONTEXT_KEY + "_username", username);
    put(MDC_AUDIT_CONTEXT_KEY + "_proxyUsername", proxyUsername);
    put(MDC_AUDIT_CONTEXT_KEY + "_systemUsername", systemUsername);
    return this;
  }

  AuditEvents remote(String targetService, String remoteIp) {
    put(MDC_AUDIT_CONTEXT_KEY + "_targetServiceName", targetService);
    put(MDC_AUDIT_CONTEXT_KEY + "_remoteIp", remoteIp);
    return this;
  }

  AuditEvents correlation(String requestId, String parentRequestId, String rootRequestId) {
    put(KEY_CORRELATION_CONTEXT, "true");
    put(KEY_REQUEST_ID, requestId);
    put(KEY_PARENT_REQUEST_ID, parentRequestId);
    put(KEY_ROOT_REQUEST_ID, rootRequestId);
    return this;
  }

  AuditEvents message(String message) {
    this.message = message;
    return this;
  }

  AuditEvents time(long timeMillis) {
    this.timeMillis = timeMillis;
    return this;
  }

  LogEvent build() {
    return Log4jLogEvent.newBuilder()
        .setLoggerName("audit")
        .setLevel(Level.INFO)
        .setTimeMillis(timeMillis)
        .setMessage(new SimpleMessage(message))
        .setContextData(data)
        .build();
  }

  private void put(String key, String value) {
    if (value != null) {
      data.putValue(key, value);
    }
  }
}

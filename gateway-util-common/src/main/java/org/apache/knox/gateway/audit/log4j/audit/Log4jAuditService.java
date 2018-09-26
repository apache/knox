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
package org.apache.knox.gateway.audit.log4j.audit;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.log4j.MDC;

public class Log4jAuditService implements AuditService {

  public static final String MDC_AUDIT_CONTEXT_KEY = "audit_context";
  private Map<String, Auditor> auditors = new ConcurrentHashMap<String, Auditor>();

  @Override
  public AuditContext createContext() {
    AuditContext context = getContext();
    if ( context == null ) {
      context = new Log4jAuditContext();
      attachContext( context );
    }
    return context;
  }

  @Override
  public AuditContext getContext() {
    return (Log4jAuditContext) MDC.get( MDC_AUDIT_CONTEXT_KEY );
  }

  @Override
  public void attachContext(AuditContext context) {
    if ( context != null ) {
      MDC.put( MDC_AUDIT_CONTEXT_KEY, context );
    }
  }

  @Override
  public AuditContext detachContext() {
    AuditContext context = (AuditContext) MDC.get( MDC_AUDIT_CONTEXT_KEY );
    MDC.remove( MDC_AUDIT_CONTEXT_KEY );
    return context;
  }

  @Override
  public <T> T execute( AuditContext context, Callable<T> callable ) throws Exception {
    try {
      attachContext( context );
      return callable.call();
    } finally {
      detachContext();
    }
  }
  
  @Override
  public synchronized Auditor getAuditor( String auditorName, String componentName, String serviceName ) {
    String key = auditorName + componentName + serviceName;
    Auditor auditor = auditors.get( key );
    if( auditor == null ) {
      auditor = new Log4jAuditor( auditorName, componentName, serviceName );
      auditors.put( key, auditor );
    }
    return auditor;
  }

}

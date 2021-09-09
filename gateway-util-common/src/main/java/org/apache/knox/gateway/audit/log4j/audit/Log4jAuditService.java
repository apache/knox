/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import org.apache.logging.log4j.ThreadContext;

public class Log4jAuditService implements AuditService {

  public static final String MDC_AUDIT_CONTEXT_KEY = "audit_context";
  private Map<String, Auditor> auditors = new ConcurrentHashMap<>();

  @Override
  public AuditContext createContext() {
    AuditContext context = new Log4jAuditContext();
    attachContext(context);
    return context;
  }

  @Override
  public AuditContext getContext() {
    if (ThreadContext.get(MDC_AUDIT_CONTEXT_KEY) == null) {
      return null;
    }
    return new Log4jAuditContext(
        ThreadContext.get(MDC_AUDIT_CONTEXT_KEY + "_username"),
        ThreadContext.get(MDC_AUDIT_CONTEXT_KEY + "_proxyUsername"),
        ThreadContext.get(MDC_AUDIT_CONTEXT_KEY + "_systemUsername"),
        ThreadContext.get(MDC_AUDIT_CONTEXT_KEY + "_targetServiceName"),
        ThreadContext.get(MDC_AUDIT_CONTEXT_KEY + "_remoteIp"),
        ThreadContext.get(MDC_AUDIT_CONTEXT_KEY + "_remoteHostname")
    );
  }

  @Override
  public void attachContext(AuditContext context) {
    if (context != null) {
      ThreadContext.put(MDC_AUDIT_CONTEXT_KEY, "true");
      ThreadContext.put(MDC_AUDIT_CONTEXT_KEY + "_username", context.getUsername());
      ThreadContext.put(MDC_AUDIT_CONTEXT_KEY + "_proxyUsername", context.getProxyUsername());
      ThreadContext.put(MDC_AUDIT_CONTEXT_KEY + "_systemUsername", context.getSystemUsername());
      ThreadContext.put(MDC_AUDIT_CONTEXT_KEY + "_targetServiceName", context.getTargetServiceName());
      ThreadContext.put(MDC_AUDIT_CONTEXT_KEY + "_remoteIp", context.getRemoteIp());
      ThreadContext.put(MDC_AUDIT_CONTEXT_KEY + "_remoteHostname", context.getRemoteHostname());
    }
  }

  @Override
  public AuditContext detachContext() {
    AuditContext context = getContext();
    ThreadContext.remove(MDC_AUDIT_CONTEXT_KEY);
    ThreadContext.remove(MDC_AUDIT_CONTEXT_KEY + "_username");
    ThreadContext.remove(MDC_AUDIT_CONTEXT_KEY + "_proxyUsername");
    ThreadContext.remove(MDC_AUDIT_CONTEXT_KEY + "_systemUsername");
    ThreadContext.remove(MDC_AUDIT_CONTEXT_KEY + "_targetServiceName");
    ThreadContext.remove(MDC_AUDIT_CONTEXT_KEY + "_remoteIp");
    ThreadContext.remove(MDC_AUDIT_CONTEXT_KEY + "_remoteHostname");
    return context;
  }

  @Override
  public <T> T execute(AuditContext context, Callable<T> callable) throws Exception {
    try {
      attachContext(context);
      return callable.call();
    } finally {
      detachContext();
    }
  }

  @Override
  public synchronized Auditor getAuditor(String auditorName, String componentName, String serviceName) {
    String key = auditorName + componentName + serviceName;
    Auditor auditor = auditors.get(key);
    if (auditor == null) {
      auditor = new Log4jAuditor(auditorName, componentName, serviceName);
      auditors.put(key, auditor);
    }
    return auditor;
  }

}

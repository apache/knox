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

import static org.apache.knox.gateway.audit.log4j.audit.Log4jAuditService.MDC_AUDIT_CONTEXT_KEY;

import java.util.Map;

import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.logging.log4j.core.LogEvent;

public class Log4jAuditContext implements AuditContext {
  private String username;
  private String proxyUsername;
  private String systemUsername;
  private String targetServiceName;
  private String remoteIp;
  private String remoteHostname;

  public static Log4jAuditContext of(LogEvent event) {
    if (event == null) {
      return null;
    }
    Map<String, String> data = event.getContextData().toMap();
    return new Log4jAuditContext(
            data.get(MDC_AUDIT_CONTEXT_KEY + "_username"),
            data.get(MDC_AUDIT_CONTEXT_KEY + "_proxyUsername"),
            data.get(MDC_AUDIT_CONTEXT_KEY + "_systemUsername"),
            data.get(MDC_AUDIT_CONTEXT_KEY + "_targetServiceName"),
            data.get(MDC_AUDIT_CONTEXT_KEY + "_remoteIp"),
            data.get(MDC_AUDIT_CONTEXT_KEY + "_remoteHostname"));
  }

  public Log4jAuditContext() {
  }

  public Log4jAuditContext(String username, String proxyUsername, String systemUsername,
                           String targetServiceName, String remoteIp, String remoteHostname) {
    this.username = username;
    this.proxyUsername = proxyUsername;
    this.systemUsername = systemUsername;
    this.targetServiceName = targetServiceName;
    this.remoteIp = remoteIp;
    this.remoteHostname = remoteHostname;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public void setUsername( String username ) {
    this.username = username;
  }

  @Override
  public String getProxyUsername() {
    return proxyUsername;
  }

  @Override
  public void setProxyUsername( String proxyUsername ) {
    this.proxyUsername = proxyUsername;
  }

  @Override
  public String getSystemUsername() {
    return systemUsername;
  }

  @Override
  public void setSystemUsername( String systemUsername ) {
    this.systemUsername = systemUsername;
  }

  @Override
  public String getTargetServiceName() {
    return targetServiceName;
  }

  @Override
  public void setTargetServiceName( String targetServiceName ) {
    this.targetServiceName = targetServiceName;
  }

  @Override
  public String getRemoteIp() {
    return remoteIp;
  }

  @Override
  public void setRemoteIp( String remoteIp ) {
    this.remoteIp = remoteIp;
  }

  @Override
  public String getRemoteHostname() {
    return remoteHostname;
  }

  @Override
  public void setRemoteHostname( String remoteHostname ) {
    this.remoteHostname = remoteHostname;
  }

  @Override
  public String toString() {
    return "[" +
               "username=" + username +
               ", proxy_username=" + proxyUsername +
               ", system_username=" + systemUsername +
               ", targetServiceName=" + targetServiceName +
               ", remoteIp=" + remoteIp +
               ", remoteHostname=" + remoteHostname +
               "]";
  }

  @Override
  public void destroy() {
  }
}

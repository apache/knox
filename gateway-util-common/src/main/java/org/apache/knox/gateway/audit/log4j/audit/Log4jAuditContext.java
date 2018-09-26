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

import java.io.Serializable;

import org.apache.knox.gateway.audit.api.AuditContext;

public class Log4jAuditContext implements Serializable, AuditContext {

  private static final long serialVersionUID = 1L;

  private String username;
  private String proxyUsername;
  private String systemUsername;
  private String targetServiceName;
  private String remoteIp;
  private String remoteHostname;

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
    StringBuilder sb = new StringBuilder();
    sb.append( "[" );
    sb.append( "username=" ).append( username );
    sb.append( ", proxy_username=" ).append( proxyUsername );
    sb.append( ", system_username=" ).append( systemUsername );
    sb.append( ", targetServiceName=" ).append( targetServiceName );
    sb.append( ", remoteIp=" ).append( remoteIp );
    sb.append( ", remoteHostname=" ).append( remoteHostname );
    sb.append( "]" );
    return sb.toString();
  }

  @Override
  public void destroy() {   
  }

}

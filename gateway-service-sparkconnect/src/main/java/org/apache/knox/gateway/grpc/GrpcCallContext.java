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
package org.apache.knox.gateway.grpc;

import java.util.Collections;
import java.util.Set;

import io.grpc.Context;

/**
 * Per-call state shared across the interceptor chain and the proxy handler.
 * <p>
 * The instance is created by the outermost interceptor and attached to the gRPC
 * {@link Context}, then filled in as the call descends the chain: authentication
 * sets the principal and groups, routing sets the topology and backend. Because
 * every interceptor mutates one object rather than layering new context values,
 * the audit interceptor — which wraps the call from outside — can still report
 * what the inner interceptors resolved, including on the paths where they
 * rejected the call.
 * <p>
 * Instances are confined to a single call. gRPC may invoke listener callbacks on
 * different threads, so the fields are volatile; they are written once during
 * interceptor descent and only read afterwards.
 */
// volatile, not synchronized: gRPC dispatches a call's listener callbacks across
// threads, and these fields are written once during interceptor descent and read
// afterwards. A lock would serialise readers for no benefit.
@SuppressWarnings("PMD.AvoidUsingVolatile")
public class GrpcCallContext {

  public static final Context.Key<GrpcCallContext> KEY = Context.key("KnoxGrpcCallContext");

  private final String methodName;
  private final String authority;
  private final String remoteAddress;
  private final long startNanos;

  private volatile String principal;
  private volatile Set<String> groups = Collections.emptySet();
  private volatile String topology;
  private volatile String backendUrl;
  private volatile String sessionId;
  private volatile String operationId;

  public GrpcCallContext(String methodName, String authority, String remoteAddress, long startNanos) {
    this.methodName = methodName;
    this.authority = authority;
    this.remoteAddress = remoteAddress;
    this.startNanos = startNanos;
  }

  /**
   * Returns the call context attached to the current gRPC context, or null when
   * called outside a proxied call.
   *
   * @return the current call context, or null
   */
  public static GrpcCallContext current() {
    return KEY.get();
  }

  public String getMethodName() {
    return methodName;
  }

  public String getAuthority() {
    return authority;
  }

  public String getRemoteAddress() {
    return remoteAddress;
  }

  public long getStartNanos() {
    return startNanos;
  }

  public String getPrincipal() {
    return principal;
  }

  public void setPrincipal(String principal) {
    this.principal = principal;
  }

  public Set<String> getGroups() {
    return groups;
  }

  public void setGroups(Set<String> groups) {
    this.groups = groups == null ? Collections.emptySet() : Collections.unmodifiableSet(groups);
  }

  public String getTopology() {
    return topology;
  }

  public void setTopology(String topology) {
    this.topology = topology;
  }

  public String getBackendUrl() {
    return backendUrl;
  }

  public void setBackendUrl(String backendUrl) {
    this.backendUrl = backendUrl;
  }

  /**
   * The Spark Connect session this call belongs to, once a request message has
   * been parsed. Null on the generic byte-level path, which never looks inside
   * messages.
   *
   * @return the session id, or null
   */
  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getOperationId() {
    return operationId;
  }

  public void setOperationId(String operationId) {
    this.operationId = operationId;
  }
}

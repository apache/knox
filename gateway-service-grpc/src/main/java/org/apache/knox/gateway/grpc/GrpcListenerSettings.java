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

/**
 * Everything one gRPC listener needs in order to run: its transport limits, what
 * it fronts, where the identity goes, and the TLS identity it presents.
 * <p>
 * Plain values rather than reads against {@code GatewayConfig}, so that a
 * listener can be built and tested without a gateway configuration to hand — and
 * so that a gateway running several listeners has one of these per listener
 * rather than each of them reaching back into shared configuration.
 * <p>
 * The limits here are the listener's DoS surface. A new socket accepting 128 MB
 * messages on long-lived streams needs message-size, stream-count and
 * keepalive-abuse bounds configured from the start, not added after the first
 * incident.
 */
public class GrpcListenerSettings {

  private String name = "grpc";
  private int port;
  private int maxMessageSize = 134217728;
  private int maxConcurrentCallsPerConnection = 1000;
  private long permitKeepAliveTimeMillis = 10000L;
  private boolean permitKeepAliveWithoutCalls = true;
  private long channelIdleTimeoutMillis = 1800000L;
  private long drainTimeoutMillis = 30000L;
  private String backendTokenAlias;
  private String topologyMetadataKey = GrpcMetadataKeys.DEFAULT_TOPOLOGY_KEY;
  private String serviceRole = "GRPC";
  private Set<String> protoServices = Collections.emptySet();
  private String defaultTopology;
  private String identityRules;
  private int identityScanLimit = 131072;
  private String methodsDeny;
  private String methodsAllow;
  private boolean sslEnabled = true;
  private String sslKeystorePath;
  private String sslKeystoreType = "PKCS12";
  private String sslKeystoreAlias;
  private String sslKeystorePasswordAlias;

  /**
   * The Knox service role this listener resolves backends under, and the prefix
   * for its ACL and method-list parameters in topology XML.
   *
   * @return the service role
   */
  public String getServiceRole() {
    return serviceRole;
  }

  public GrpcListenerSettings serviceRole(String value) {
    this.serviceRole = value;
    return this;
  }

  /**
   * The fully qualified proto service names this listener fronts. Anything else
   * is answered {@code UNIMPLEMENTED}.
   *
   * @return the proxied service names
   */
  public Set<String> getProtoServices() {
    return protoServices;
  }

  public GrpcListenerSettings protoServices(Set<String> value) {
    this.protoServices = value == null ? Collections.emptySet() : value;
    return this;
  }

  /** @return the topology to use when a client selects none, or null */
  public String getDefaultTopology() {
    return defaultTopology;
  }

  public GrpcListenerSettings defaultTopology(String value) {
    this.defaultTopology = value;
    return this;
  }

  /** @return the identity rewrite rules as configured, or null for none */
  public String getIdentityRules() {
    return identityRules;
  }

  public GrpcListenerSettings identityRules(String value) {
    this.identityRules = value;
    return this;
  }

  public int getIdentityScanLimit() {
    return identityScanLimit;
  }

  public GrpcListenerSettings identityScanLimit(int value) {
    this.identityScanLimit = value;
    return this;
  }

  public String getMethodsDeny() {
    return methodsDeny;
  }

  public GrpcListenerSettings methodsDeny(String value) {
    this.methodsDeny = value;
    return this;
  }

  public String getMethodsAllow() {
    return methodsAllow;
  }

  public GrpcListenerSettings methodsAllow(String value) {
    this.methodsAllow = value;
    return this;
  }

  /** @return whether this listener presents TLS */
  public boolean isSslEnabled() {
    return sslEnabled;
  }

  public GrpcListenerSettings sslEnabled(boolean value) {
    this.sslEnabled = value;
    return this;
  }

  /**
   * A keystore holding this listener's own server certificate, or null to present
   * the gateway identity Jetty also presents.
   * <p>
   * Distinct key material per listener is what lets several listeners answer for
   * several hostnames on one gateway, each with a plain single-name certificate.
   * That matters where the platform PKI cannot issue multi-name (SAN or wildcard)
   * certificates, which would otherwise be the only way to serve more than one
   * name from one endpoint.
   *
   * @return the keystore path, or null for the gateway identity
   */
  public String getSslKeystorePath() {
    return sslKeystorePath;
  }

  public GrpcListenerSettings sslKeystorePath(String value) {
    this.sslKeystorePath = value;
    return this;
  }

  public String getSslKeystoreType() {
    return sslKeystoreType;
  }

  public GrpcListenerSettings sslKeystoreType(String value) {
    this.sslKeystoreType = value;
    return this;
  }

  /** @return the entry within the keystore to present, or null for the sole entry */
  public String getSslKeystoreAlias() {
    return sslKeystoreAlias;
  }

  public GrpcListenerSettings sslKeystoreAlias(String value) {
    this.sslKeystoreAlias = value;
    return this;
  }

  /** @return the alias holding the keystore password, or null for the gateway's */
  public String getSslKeystorePasswordAlias() {
    return sslKeystorePasswordAlias;
  }

  public GrpcListenerSettings sslKeystorePasswordAlias(String value) {
    this.sslKeystorePasswordAlias = value;
    return this;
  }

  public String getName() {
    return name;
  }

  public GrpcListenerSettings name(String value) {
    this.name = value;
    return this;
  }

  public int getPort() {
    return port;
  }

  public GrpcListenerSettings port(int value) {
    this.port = value;
    return this;
  }

  public int getMaxMessageSize() {
    return maxMessageSize;
  }

  public GrpcListenerSettings maxMessageSize(int value) {
    this.maxMessageSize = value;
    return this;
  }

  public int getMaxConcurrentCallsPerConnection() {
    return maxConcurrentCallsPerConnection;
  }

  public GrpcListenerSettings maxConcurrentCallsPerConnection(int value) {
    this.maxConcurrentCallsPerConnection = value;
    return this;
  }

  public long getPermitKeepAliveTimeMillis() {
    return permitKeepAliveTimeMillis;
  }

  public GrpcListenerSettings permitKeepAliveTimeMillis(long value) {
    this.permitKeepAliveTimeMillis = value;
    return this;
  }

  public boolean isPermitKeepAliveWithoutCalls() {
    return permitKeepAliveWithoutCalls;
  }

  public GrpcListenerSettings permitKeepAliveWithoutCalls(boolean value) {
    this.permitKeepAliveWithoutCalls = value;
    return this;
  }

  public long getChannelIdleTimeoutMillis() {
    return channelIdleTimeoutMillis;
  }

  public GrpcListenerSettings channelIdleTimeoutMillis(long value) {
    this.channelIdleTimeoutMillis = value;
    return this;
  }

  public long getDrainTimeoutMillis() {
    return drainTimeoutMillis;
  }

  public GrpcListenerSettings drainTimeoutMillis(long value) {
    this.drainTimeoutMillis = value;
    return this;
  }

  /**
   * The metadata entry a client uses to select a topology. It is also the
   * connection-string parameter users write, so a deployment may prefer a name
   * that describes the choice rather than the gateway making it.
   *
   * @return the metadata key name
   */
  public String getTopologyMetadataKey() {
    return topologyMetadataKey;
  }

  public GrpcListenerSettings topologyMetadataKey(String value) {
    this.topologyMetadataKey = value;
    return this;
  }

  public String getBackendTokenAlias() {
    return backendTokenAlias;
  }

  public GrpcListenerSettings backendTokenAlias(String value) {
    this.backendTokenAlias = value;
    return this;
  }
}

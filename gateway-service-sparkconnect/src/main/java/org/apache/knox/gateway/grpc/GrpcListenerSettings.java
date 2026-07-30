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

/**
 * Transport and lifecycle settings for a {@link GrpcGatewayListener}.
 * <p>
 * These are deliberately plain values rather than reads against
 * {@code GatewayConfig}. The listener is meant to be reusable for any gRPC
 * service, so the layer that knows which {@code gateway.*} properties apply —
 * currently the Spark Connect plugin — is the layer that reads them.
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

  public String getBackendTokenAlias() {
    return backendTokenAlias;
  }

  public GrpcListenerSettings backendTokenAlias(String value) {
    this.backendTokenAlias = value;
    return this;
  }
}

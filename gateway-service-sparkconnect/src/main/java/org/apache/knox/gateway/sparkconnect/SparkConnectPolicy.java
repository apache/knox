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
package org.apache.knox.gateway.sparkconnect;

import java.util.List;
import java.util.Objects;

import org.apache.knox.gateway.config.GatewayConfig;

/**
 * The message-level controls, held together so they can be replaced atomically.
 * <p>
 * These are the settings that can change on a running gateway. The handlers
 * registered at startup hold a reference to the listener rather than to a
 * particular guard, and read the current policy per call, so swapping this
 * object takes effect on the next RPC without rebuilding the gRPC service.
 * <p>
 * It is one object rather than separate fields so a configuration change is seen
 * whole: a call can never observe the new artifact-gating rule alongside the old
 * reserved prefix.
 */
final class SparkConnectPolicy {

  private final String reservedConfigPrefix;
  private final String addArtifactsMode;
  private final List<String> addArtifactsAllowedUsers;
  private final ReservedConfigGuard reservedConfigGuard;
  private final AddArtifactsGuard addArtifactsGuard;

  private SparkConnectPolicy(String reservedConfigPrefix,
                             String addArtifactsMode,
                             List<String> addArtifactsAllowedUsers) {
    this.reservedConfigPrefix = reservedConfigPrefix;
    this.addArtifactsMode = addArtifactsMode;
    this.addArtifactsAllowedUsers = addArtifactsAllowedUsers;
    this.reservedConfigGuard = new ReservedConfigGuard(reservedConfigPrefix);
    this.addArtifactsGuard = new AddArtifactsGuard(addArtifactsMode, addArtifactsAllowedUsers);
  }

  static SparkConnectPolicy from(GatewayConfig config) {
    return new SparkConnectPolicy(
        config.getSparkConnectReservedConfigPrefix(),
        config.getSparkConnectAddArtifactsMode(),
        config.getSparkConnectAddArtifactsAllowedUsers());
  }

  ReservedConfigGuard reservedConfigGuard() {
    return reservedConfigGuard;
  }

  AddArtifactsGuard addArtifactsGuard() {
    return addArtifactsGuard;
  }

  /**
   * Whether this policy differs from another, used to decide if a configuration
   * change is worth logging. Compares the configured values rather than the
   * derived guards, which have no meaningful equality.
   *
   * @param other the policy to compare against, may be null
   * @return true if the two express different rules
   */
  boolean differsFrom(SparkConnectPolicy other) {
    return other == null
        || !Objects.equals(reservedConfigPrefix, other.reservedConfigPrefix)
        || !Objects.equals(addArtifactsMode, other.addArtifactsMode)
        || !Objects.equals(addArtifactsAllowedUsers, other.addArtifactsAllowedUsers);
  }

  @Override
  public String toString() {
    return "addArtifactsMode=" + addArtifactsMode
        + ", addArtifactsAllowedUsers=" + addArtifactsAllowedUsers
        + ", reservedConfigPrefix=" + reservedConfigPrefix;
  }
}

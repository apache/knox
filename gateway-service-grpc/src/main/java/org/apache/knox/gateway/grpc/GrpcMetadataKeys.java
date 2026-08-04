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

import java.util.Locale;
import java.util.regex.Pattern;

import io.grpc.Metadata;

/**
 * Call metadata keys the gateway reads or writes.
 * <p>
 * Everything here is expressible in a vanilla {@code sc://} connection string.
 * The {@code token=} parameter becomes {@link #AUTHORIZATION}, and any parameter
 * the client does not recognise — {@code knox-topology=analytics}, say — is sent
 * verbatim as metadata on every request. That is what lets clients select a
 * topology despite gRPC forbidding a path component in the connection URL.
 */
public final class GrpcMetadataKeys {

  /** Carries the Knox-issued bearer token, set by the client's {@code token=} parameter. */
  public static final Metadata.Key<String> AUTHORIZATION =
      Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

  /**
   * The default name of the metadata entry that selects a topology. Deployments
   * can rename it, since it appears verbatim in the connection strings users
   * write and need not advertise which gateway is reading it.
   */
  public static final String DEFAULT_TOPOLOGY_KEY = "knox-topology";

  private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9_.-]+");
  private static final String BINARY_SUFFIX = "-bin";

  public static final String BEARER_PREFIX = "Bearer ";

  private GrpcMetadataKeys() {
  }

  /**
   * Builds the metadata key used to select a topology.
   * <p>
   * gRPC restricts header names to lowercase ASCII letters, digits and
   * {@code -_.}, and reserves the {@code -bin} suffix for binary values. An
   * invalid name would otherwise surface as an obscure failure from deep inside
   * the transport, so it is rejected here with an explanation instead.
   *
   * @param name the configured metadata key name
   * @return the metadata key to read topology selection from
   * @throws IllegalArgumentException if the name is not usable as a gRPC metadata key
   */
  public static Metadata.Key<String> topologyKey(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException(
          "The topology metadata key name must not be empty");
    }
    final String trimmed = name.trim();
    if (!trimmed.equals(trimmed.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException(
          "gRPC metadata key names are case-insensitive and must be given in lower case: " + name);
    }
    if (!VALID_KEY.matcher(trimmed).matches()) {
      throw new IllegalArgumentException(
          "The topology metadata key name may contain only a-z, 0-9, '-', '_' and '.': " + name);
    }
    if (trimmed.endsWith(BINARY_SUFFIX)) {
      throw new IllegalArgumentException(
          "gRPC reserves the '-bin' suffix for binary metadata; the topology key carries text: " + name);
    }
    if (AUTHORIZATION.name().equals(trimmed)) {
      throw new IllegalArgumentException(
          "The topology metadata key must not be 'authorization', which carries the bearer token");
    }
    return Metadata.Key.of(trimmed, Metadata.ASCII_STRING_MARSHALLER);
  }
}

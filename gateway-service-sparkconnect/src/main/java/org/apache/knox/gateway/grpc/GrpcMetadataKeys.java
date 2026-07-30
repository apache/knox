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

  /** Selects the topology, set by a {@code knox-topology=} connection-string parameter. */
  public static final Metadata.Key<String> TOPOLOGY =
      Metadata.Key.of("knox-topology", Metadata.ASCII_STRING_MARSHALLER);

  public static final String BEARER_PREFIX = "Bearer ";

  private GrpcMetadataKeys() {
  }
}

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.knox.gateway.config.GatewayConfig;

/**
 * Turns gateway configuration into one {@link GrpcListenerSettings} per listener.
 *
 * <h2>Why more than one listener</h2>
 * A listener is a transport endpoint, not a policy boundary: each one still
 * routes to as many topologies as its clients select, exactly as a single
 * listener does. What separates them is the socket and the certificate on it.
 * <p>
 * That is worth having because TLS identity is per-socket. Serving several
 * hostnames from one endpoint needs one certificate naming all of them, and a
 * platform PKI that cannot issue multi-name (SAN or wildcard) certificates
 * cannot produce one. Several listeners, each presenting a plain single-name
 * certificate for the hostname its clients dial, is the way to serve those
 * clients without that certificate.
 *
 * <h2>How a listener is configured</h2>
 * {@code gateway.grpc.listener.names} lists them. Every other property is read
 * from {@code gateway.grpc.<name>.<property>} when that listener sets it, and
 * from the plain {@code gateway.grpc.<property>} otherwise — so shared settings
 * are written once and only the differences are repeated.
 * <p>
 * Naming no listeners yields exactly one, configured entirely from the plain
 * properties. That is the ordinary deployment, and it means the multi-listener
 * machinery costs nothing to a gateway that does not use it.
 */
public final class GrpcListenerSettingsFactory {

  private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9][a-z0-9_-]*");

  /**
   * First segments of the plain properties. A listener may not be named after
   * one, because {@code gateway.grpc.identity.rules} and a listener called
   * {@code identity} would occupy the same configuration namespace.
   */
  private static final Set<String> RESERVED_NAMES = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList("enabled", "port", "service", "proto", "identity", "default",
          "topology", "methods", "max", "permit", "channel", "drain", "backend", "ssl",
          "listener")));

  private GrpcListenerSettingsFactory() {
  }

  /**
   * Builds the settings for every configured listener.
   *
   * @param config the gateway configuration
   * @return one settings object per listener, in configured order; never empty
   * @throws IllegalArgumentException if a listener name is unusable, duplicated,
   *         or two listeners would bind the same port
   */
  public static List<GrpcListenerSettings> create(GatewayConfig config) {
    final List<String> names = config.getGrpcListenerNames();
    final List<GrpcListenerSettings> settings = new ArrayList<>();

    if (names == null || names.isEmpty()) {
      settings.add(build(config, null, Collections.emptyMap()));
    } else {
      final Set<String> seen = new LinkedHashSet<>();
      for (String name : names) {
        final String listener = validate(name, seen);
        settings.add(build(config, listener, config.getGrpcListenerConfig(listener)));
      }
    }
    requireDistinctPorts(settings);
    return Collections.unmodifiableList(settings);
  }

  private static String validate(String name, Set<String> seen) {
    final String trimmed = name == null ? "" : name.trim();
    if (!VALID_NAME.matcher(trimmed).matches()) {
      throw new IllegalArgumentException("A gRPC listener name may contain only a-z, 0-9, '-' and"
          + " '_', and must start with a letter or digit, got: " + name);
    }
    if (RESERVED_NAMES.contains(trimmed)) {
      throw new IllegalArgumentException("'" + trimmed + "' cannot be a gRPC listener name because"
          + " gateway.grpc." + trimmed + ".* is already a configuration property");
    }
    if (!seen.add(trimmed)) {
      throw new IllegalArgumentException("Duplicate gRPC listener name: " + trimmed);
    }
    return trimmed;
  }

  /**
   * Two listeners on one port is a startup failure rather than a race to bind:
   * whichever lost would fail with an address-in-use error naming neither of the
   * listeners involved.
   */
  private static void requireDistinctPorts(List<GrpcListenerSettings> settings) {
    final Map<Integer, String> byPort = new java.util.HashMap<>();
    for (GrpcListenerSettings listener : settings) {
      final String other = byPort.put(listener.getPort(), listener.getName());
      if (other != null) {
        throw new IllegalArgumentException("gRPC listeners '" + other + "' and '"
            + listener.getName() + "' are both configured on port " + listener.getPort());
      }
    }
  }

  private static GrpcListenerSettings build(GatewayConfig config, String name,
                                            Map<String, String> overrides) {
    final String serviceRole = string(overrides, "service.role", config.getGrpcServiceRole());
    return new GrpcListenerSettings()
        // An unnamed listener is named after its service role, so a single-listener
        // deployment reads in the log for the thing being fronted rather than for
        // the transport. A named one uses the name the operator chose.
        .name(name == null ? serviceRole : name)
        .serviceRole(serviceRole)
        .port(integer(overrides, "port", config.getGrpcPort()))
        .protoServices(protoServices(string(overrides, "proto.services", config.getGrpcProtoServices())))
        .defaultTopology(string(overrides, "default.topology", config.getGrpcDefaultTopology()))
        .topologyMetadataKey(string(overrides, "topology.metadata.key", config.getGrpcTopologyMetadataKey()))
        .identityRules(string(overrides, "identity.rules", config.getGrpcIdentityRules()))
        .identityScanLimit(integer(overrides, "identity.scan.limit", config.getGrpcIdentityScanLimit()))
        .methodsDeny(string(overrides, "methods.deny", config.getGrpcMethodsDeny()))
        .methodsAllow(string(overrides, "methods.allow", config.getGrpcMethodsAllow()))
        .maxMessageSize(integer(overrides, "max.message.size", config.getGrpcMaxMessageSize()))
        .maxConcurrentCallsPerConnection(integer(overrides, "max.concurrent.calls.per.connection",
            config.getGrpcMaxConcurrentCallsPerConnection()))
        .permitKeepAliveTimeMillis(longValue(overrides, "permit.keepalive.time",
            config.getGrpcPermitKeepAliveTime()))
        .permitKeepAliveWithoutCalls(bool(overrides, "permit.keepalive.without.calls",
            config.isGrpcPermitKeepAliveWithoutCalls()))
        .channelIdleTimeoutMillis(longValue(overrides, "channel.idle.timeout",
            config.getGrpcChannelIdleTimeout()))
        .drainTimeoutMillis(longValue(overrides, "drain.timeout", config.getGrpcDrainTimeout()))
        .backendTokenAlias(string(overrides, "backend.token.alias", config.getGrpcBackendTokenAlias()))
        .sslEnabled(bool(overrides, "ssl.enabled", config.isSSLEnabled()))
        .sslKeystorePath(string(overrides, "ssl.keystore.path", null))
        .sslKeystoreType(string(overrides, "ssl.keystore.type", "PKCS12"))
        .sslKeystoreAlias(string(overrides, "ssl.keystore.alias", null))
        .sslKeystorePasswordAlias(string(overrides, "ssl.keystore.password.alias", null));
  }

  static Set<String> protoServices(String configured) {
    if (configured == null || configured.trim().isEmpty()) {
      return Collections.emptySet();
    }
    final Set<String> names = new LinkedHashSet<>();
    for (String name : configured.trim().split("\\s*,\\s*")) {
      if (!name.isEmpty()) {
        names.add(name);
      }
    }
    return Collections.unmodifiableSet(names);
  }

  private static String string(Map<String, String> overrides, String key, String fallback) {
    final String value = overrides.get(key);
    return value == null || value.trim().isEmpty() ? fallback : value.trim();
  }

  private static int integer(Map<String, String> overrides, String key, int fallback) {
    final String value = string(overrides, key, null);
    return value == null ? fallback : parse(key, value).intValue();
  }

  private static long longValue(Map<String, String> overrides, String key, long fallback) {
    final String value = string(overrides, key, null);
    return value == null ? fallback : parse(key, value);
  }

  private static boolean bool(Map<String, String> overrides, String key, boolean fallback) {
    final String value = string(overrides, key, null);
    return value == null ? fallback : Boolean.parseBoolean(value);
  }

  private static Long parse(String key, String value) {
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "gRPC listener property '" + key + "' must be a number, got: " + value, e);
    }
  }

  /** @return the name of the property a listener would set to override this one */
  static String propertyName(String listener, String property) {
    return listener == null
        ? "gateway.grpc." + property
        : String.format(Locale.ROOT, "gateway.grpc.%s.%s", listener, property);
  }
}

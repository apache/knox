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
package org.apache.knox.gateway.provider.federation.jwt.filter;

import java.util.Locale;
import java.util.Optional;

/**
 * The service name and namespace parsed out of a destination FQDN header value of the form
 * {@code server-name.namespace<clusterSuffix>}, optionally followed by {@code :port}. The header
 * value is trusted, so a trailing {@code :port}, if present, is only stripped off, never
 * validated or retained -- an incorrect value simply fails to match later.
 *
 * <p>Both are lower-cased at parse time, matching {@link AudienceResource}'s namespace and
 * service-name, so that a comparison between the two is a plain, case-sensitive {@code equals}.
 */
public record DestinationServiceName(String serviceName, String namespace) {

  public static Optional<DestinationServiceName> parse(String headerValue, String clusterSuffix) {
    if (headerValue == null || headerValue.isEmpty() || clusterSuffix == null || clusterSuffix.isEmpty()) {
      return Optional.empty();
    }
    String value = headerValue.trim();
    final int colon = value.lastIndexOf(':');
    if (colon >= 0) {
      value = value.substring(0, colon);
    }
    if (!value.endsWith(clusterSuffix)) {
      return Optional.empty();
    }
    final String withoutSuffix = value.substring(0, value.length() - clusterSuffix.length());
    final String[] labels = withoutSuffix.split("\\.", -1);
    if (labels.length != 2) {
      return Optional.empty();
    }
    final String serviceName = labels[0];
    final String namespace = labels[1];
    if (serviceName.isEmpty() || namespace.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new DestinationServiceName(serviceName.toLowerCase(Locale.ROOT), namespace.toLowerCase(Locale.ROOT)));
  }
}

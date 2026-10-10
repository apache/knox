/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.util.Optional;

/**
 * A parsed Kubernetes service-account subject, of the form
 * {@code system:serviceaccount:<namespace>:<sa-name>}.
 */
record ServiceAccountSubject(String namespace, String name) {
  static final String K8S_SERVICE_ACCOUNT_SUBJECT_PREFIX = "system:serviceaccount:";

  static Optional<ServiceAccountSubject> parse(String sub) {
    if (sub == null || !sub.startsWith(K8S_SERVICE_ACCOUNT_SUBJECT_PREFIX)) {
      return Optional.empty();
    }
    final String namespaceAndName = sub.substring(K8S_SERVICE_ACCOUNT_SUBJECT_PREFIX.length());
    final int separator = namespaceAndName.indexOf(':');
    if (separator < 0) {
      return Optional.empty();
    }
    final String namespace = namespaceAndName.substring(0, separator);
    final String name = namespaceAndName.substring(separator + 1);
    if (namespace.isEmpty() || name.isEmpty() || name.indexOf(':') >= 0) {
      return Optional.empty();
    }
    return Optional.of(new ServiceAccountSubject(namespace, name));
  }
}

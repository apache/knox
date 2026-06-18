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
package org.apache.knox.gateway.preauth.k8s;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public record SpiffeId(String trustDomain, String namespace, String serviceAccount) {
    private static final String SCHEME = "spiffe";

    public static Optional<SpiffeId> parse(String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        final URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        if (!SCHEME.equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return Optional.empty();
        }
        final String path = uri.getPath();
        if (path == null) {
            return Optional.empty();
        }
        final String[] parts = path.split("/");
        if (parts.length != 5 || !"ns".equals(parts[1]) || !"sa".equals(parts[3])) {
            return Optional.empty();
        }
        final String namespace = parts[2];
        final String serviceAccount = parts[4];
        if (namespace.isEmpty() || serviceAccount.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SpiffeId(uri.getHost(), namespace, serviceAccount));
    }
}

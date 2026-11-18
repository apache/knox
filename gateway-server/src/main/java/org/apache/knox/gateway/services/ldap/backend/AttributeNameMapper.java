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
package org.apache.knox.gateway.services.ldap.backend;

import java.util.Map;
import java.util.TreeMap;

/**
 * Bidirectional, case-insensitive mapping between a remote server's attribute names and the names
 * the proxy exposes to clients. Configured per backend via {@code attributeMappings} as a
 * comma-separated list of {@code remoteName:proxyName} pairs, for example
 * {@code whenChanged:modifyTimestamp}. LDAP attribute names are case-insensitive, so all lookups
 * ignore case.
 * <p>
 * The mapping is applied in three places so a renamed attribute behaves end to end:
 * <ul>
 *   <li>when a remote entry is converted into a proxy entry, the remote name is replaced with the
 *       proxy name ({@link #toProxyName(String)});</li>
 *   <li>when a client's requested attribute list is forwarded to the remote server, the proxy name
 *       is replaced with the remote name ({@link #toRemoteName(String)});</li>
 *   <li>when a search filter referencing the proxy name is forwarded, the leaf is rewritten to the
 *       remote name ({@link #toRemoteName(String)}).</li>
 * </ul>
 */
public final class AttributeNameMapper {

    private static final AttributeNameMapper EMPTY =
            new AttributeNameMapper(new TreeMap<>(String.CASE_INSENSITIVE_ORDER));

    // remote name -> proxy name (converting a remote entry into a proxy entry)
    private final Map<String, String> remoteToProxy;
    // proxy name -> remote name (translating requested attributes and search filters)
    private final Map<String, String> proxyToRemote;

    private AttributeNameMapper(Map<String, String> remoteToProxy) {
        this.remoteToProxy = remoteToProxy;
        final Map<String, String> inverse = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> entry : remoteToProxy.entrySet()) {
            // Last writer wins if two remote names map to the same proxy name; the reverse direction
            // can only pick one, and a many-to-one rename is a misconfiguration regardless.
            inverse.put(entry.getValue(), entry.getKey());
        }
        this.proxyToRemote = inverse;
    }

    /**
     * Parses a comma-separated {@code remoteName:proxyName} configuration value. A null or blank
     * value yields an empty mapper. A pair without a colon, or with an empty side, is skipped
     * rather than failing the whole backend.
     */
    public static AttributeNameMapper fromConfig(String configValue) {
        if (configValue == null || configValue.trim().isEmpty()) {
            return EMPTY;
        }
        final Map<String, String> remoteToProxy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String pair : configValue.split(",")) {
            final String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final int colon = trimmed.indexOf(':');
            // Require a colon with a non-empty name on each side.
            if (colon <= 0 || colon == trimmed.length() - 1) {
                continue;
            }
            final String remote = trimmed.substring(0, colon).trim();
            final String proxy = trimmed.substring(colon + 1).trim();
            if (!remote.isEmpty() && !proxy.isEmpty()) {
                remoteToProxy.put(remote, proxy);
            }
        }
        return remoteToProxy.isEmpty() ? EMPTY : new AttributeNameMapper(remoteToProxy);
    }

    public boolean isEmpty() {
        return remoteToProxy.isEmpty();
    }

    /** Returns the proxy name for a remote attribute, or {@code remoteName} itself when unmapped. */
    public String toProxyName(String remoteName) {
        if (remoteName == null) {
            return null;
        }
        final String mapped = remoteToProxy.get(remoteName);
        return mapped != null ? mapped : remoteName;
    }

    /** Returns the remote name for a proxy attribute, or {@code proxyName} itself when unmapped. */
    public String toRemoteName(String proxyName) {
        if (proxyName == null) {
            return null;
        }
        final String mapped = proxyToRemote.get(proxyName);
        return mapped != null ? mapped : proxyName;
    }
}

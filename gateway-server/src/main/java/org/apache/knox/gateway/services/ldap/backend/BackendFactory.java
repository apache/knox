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

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LdapMessages;

import java.util.Map;
import java.util.ServiceLoader;

/**
 * Factory for loading backend implementations using ServiceLoader for full extensibility.
 * Backends are discovered via META-INF/services/org.apache.knox.gateway.services.ldap.backend.LdapBackend
 * Built-in backends (file, ldap) are registered via ServiceLoader along with any external plugins.
 */
public class BackendFactory {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    public static LdapBackend createBackend(String backendName, Map<String, String> config) throws Exception {
        String backendType = config.get("backendType");
        if (backendType == null) {
            // No backend type configured found
            LOG.ldapBackendTypeNotFound(backendName);
            throw new IllegalArgumentException("No LDAP backend type configured for : " + backendName);
        }

        // Use ServiceLoader to discover all available backend factories (built-in and external plugins)
        ServiceLoader<LdapBackendFactory> loader = ServiceLoader.load(LdapBackendFactory.class);
        for (LdapBackendFactory backendFactory : loader) {
            if (backendFactory.getType().equalsIgnoreCase(backendType)) {
                LOG.ldapBackendLoading(backendType, "ServiceLoader");
                LdapBackend backend = backendFactory.create(backendName, config);
                return backend;
            }
        }

        // No matching backend found
        LOG.ldapBackendNotFound(backendType, backendName);
        throw new IllegalArgumentException("No LDAP backend factory of type " + backendType + " found for : " + backendName);
    }
}

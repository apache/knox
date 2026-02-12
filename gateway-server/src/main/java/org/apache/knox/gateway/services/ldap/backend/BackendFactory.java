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
        // Use ServiceLoader to discover all available backends (built-in and external plugins)
        ServiceLoader<LdapBackend> loader = ServiceLoader.load(LdapBackend.class);
        for (LdapBackend backend : loader) {
            if (backend.getName().equalsIgnoreCase(backendName)) {
                LOG.ldapBackendLoading(backend.getName(), "ServiceLoader");
                backend.initialize(config);
                return backend;
            }
        }

        // No matching backend found
        LOG.ldapBackendNotFound(backendName);
        throw new IllegalArgumentException("No LDAP backend found for type: " + backendName);
    }
}

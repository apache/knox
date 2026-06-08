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
package org.apache.knox.gateway.services.ldap.interceptor;

import org.apache.directory.server.core.api.interceptor.Interceptor;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LdapMessages;

import java.util.Map;
import java.util.ServiceLoader;

/**
 * Factory for creating LDAP Interceptor implementations using ServiceLoader for full extensibility.
 * Backends are discovered via META-INF/services/org.apache.knox.gateway.services.ldap.interceptor.KnoxLdapInterceptorFactory
 * Built-in interceptors are registered via ServiceLoader along with any external plugins.
 */
public class InterceptorFactory {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    public static Interceptor createInterceptor(String interceptorName, Map<String, String> config) throws Exception {
        String interceptorType = config.get("interceptorType");
        if (interceptorType == null) {
            // No backend type configured found
            LOG.ldapInterceptorTypeNotFound(interceptorName);
            throw new IllegalArgumentException("No LDAP interceptor type configured for : " + interceptorName);
        }

        // Use ServiceLoader to discover all available interceptors (built-in and external plugins)
        // Indirect instantiation through a factory is used to allow configuration of multiple instances
        // of the same class of interceptor. e.g., if multiple backends are configured
        ServiceLoader<KnoxLdapInterceptorFactory> loader = ServiceLoader.load(KnoxLdapInterceptorFactory.class);
        for (KnoxLdapInterceptorFactory interceptorFactory : loader) {
            if (interceptorFactory.getType().equalsIgnoreCase(interceptorType)) {
                LOG.ldapInterceptorCreating(interceptorType, "ServiceLoader");
                return interceptorFactory.create(interceptorName, config);
            }
        }

        // No matching interceptor found
        LOG.ldapInterceptorNotFound(interceptorType, interceptorName);
        throw new IllegalArgumentException("No LDAP interceptor of type " + interceptorType + " found for : " + interceptorName);
    }
}

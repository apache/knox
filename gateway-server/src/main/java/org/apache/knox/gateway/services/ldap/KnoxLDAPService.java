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
package org.apache.knox.gateway.services.ldap;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;

import java.io.File;
import java.util.Map;

/**
 * Knox LDAP Service - provides an embedded LDAP server with pluggable backends
 * for user and group lookups.
 */
public class KnoxLDAPService implements Service {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    private KnoxLDAPServerManager ldapServerManager;
    private boolean enabled;

    @Override
    public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
        this.enabled = config.isLDAPEnabled();

        if (!enabled) {
            return;
        }

        try {
            // Initialize the LDAP server manager with configuration
            ldapServerManager = new KnoxLDAPServerManager();

            // Prepare work directory for LDAP data
            File gatewayDataDir = new File(config.getGatewayDataDir());
            File ldapWorkDir = new File(gatewayDataDir, "ldap-server");

            // Get configuration
            int port = config.getLDAPPort();
            String baseDn = config.getLDAPBaseDN();
            String backendType = config.getLDAPBackendType();

            // Get backend-specific configuration using prefixed properties
            Map<String, String> backendConfig = config.getLDAPBackendConfig(backendType);

            // Add common configuration
            backendConfig.put("baseDn", baseDn);

            // Add legacy dataFile property for backwards compatibility with file backend
            if ("file".equalsIgnoreCase(backendType) && !backendConfig.containsKey("dataFile")) {
                backendConfig.put("dataFile", config.getLDAPBackendDataFile());
            }

            // For proxy backends, extract remoteBaseDn if present
            String remoteBaseDn = backendConfig.get("remoteBaseDn");

            // Initialize but don't start yet
            ldapServerManager.initialize(ldapWorkDir, port, baseDn, backendType, backendConfig, remoteBaseDn);

        } catch (Exception e) {
            throw new ServiceLifecycleException("Failed to initialize LDAP service", e);
        }
    }

    @Override
    public void start() throws ServiceLifecycleException {
        if (!enabled) {
            return;
        }

        try {
            // Start the LDAP server
            ldapServerManager.start();
        } catch (Exception e) {
            LOG.ldapServiceStartFailed(e);
            throw new ServiceLifecycleException("Failed to start LDAP service", e);
        }
    }

    @Override
    public void stop() throws ServiceLifecycleException {
        if (!enabled || ldapServerManager == null) {
            return;
        }

        try {
            ldapServerManager.stop();
        } catch (Exception e) {
            LOG.ldapServiceStopFailed(e);
            throw new ServiceLifecycleException("Failed to stop LDAP service", e);
        }
    }

    /**
     * Get the port the LDAP server is listening on
     */
    public int getLdapPort() {
        return ldapServerManager != null ? ldapServerManager.getPort() : -1;
    }

    /**
     * Get the base DN for LDAP entries
     */
    public String getBaseDn() {
        return ldapServerManager != null ? ldapServerManager.getBaseDn() : null;
    }

    /**
     * Check if the LDAP service is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}

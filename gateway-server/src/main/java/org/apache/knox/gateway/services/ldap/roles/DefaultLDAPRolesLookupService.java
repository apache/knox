/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.services.ldap.roles;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.GatewayConfigChangeListener;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ldap.LDAPRolesLookupService;
import org.apache.knox.gateway.services.ldap.LdapMessages;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DefaultLDAPRolesLookupService implements LDAPRolesLookupService, GatewayConfigChangeListener {

    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    private LdapRolesLookup ldapRolesLookup;

    @Override
    public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
        try {
            this.ldapRolesLookup = LdapRolesLookupFactory.create(config);
            logStatus(config);
        } catch (RoleLookupException e) {
            throw new ServiceLifecycleException("Error while initializing LDAP roles lookup service", e);
        }
    }

    private void logStatus(GatewayConfig config) {
        if (enabled()) {
            LOG.ldapRolesLookupEnabled(config.getLdapRolesLookupStrategy());
        } else {
            LOG.ldapRolesLookupDisabled();
        }
    }

    @Override
    public boolean enabled() {
        return ldapRolesLookup != null;
    }

    @Override
    public Collection<String> lookupRoles(String userId, Collection<String> groups) throws RoleLookupException {
        return enabled() ? ldapRolesLookup.lookupRoles(userId, groups) : List.of();
    }

    @Override
    public void onGatewayConfigChanged(GatewayConfig config) {
        LOG.ldapRolesLookupReloadingConfig();
        try {
            this.ldapRolesLookup = LdapRolesLookupFactory.create(config);
            logStatus(config);
        } catch (RoleLookupException e) {
            LOG.ldapRolesLookupReloadFailed(e);
        }
    }
}

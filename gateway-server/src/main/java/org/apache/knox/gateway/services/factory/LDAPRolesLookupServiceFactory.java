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
package org.apache.knox.gateway.services.factory;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.ldap.roles.DefaultLDAPRolesLookupService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class LDAPRolesLookupServiceFactory extends AbstractServiceFactory {

    private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);

    @Override
    protected Service createService(GatewayServices gatewayServices, ServiceType serviceType, GatewayConfig gatewayConfig, Map<String, String> options, String implementation) throws ServiceLifecycleException {
        final DefaultLDAPRolesLookupService ldapRolesLookupService = new DefaultLDAPRolesLookupService();
        GatewayServer.registerConfigChangeListener(ldapRolesLookupService);
        logServiceUsage(ldapRolesLookupService.getClass().getName(), serviceType);
        return ldapRolesLookupService;
    }

    @Override
    protected ServiceType getServiceType() {
        return ServiceType.LDAP_ROLES_LOOKUP_SERVICE;
    }

    @Override
    protected Collection<String> getKnownImplementations() {
        return List.of(DefaultLDAPRolesLookupService.class.getName());
    }
}

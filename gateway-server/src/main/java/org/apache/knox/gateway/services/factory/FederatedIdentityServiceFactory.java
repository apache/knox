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
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.federation.FederatedIdentityService;
import org.apache.knox.gateway.services.knoxidf.federation.H2DBFederatedIdentityService;
import org.apache.knox.gateway.services.knoxidf.federation.JdbcFederatedIdentityService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class FederatedIdentityServiceFactory extends AbstractServiceFactory {

    private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);

    @Override
    protected Service createService(GatewayServices gatewayServices, ServiceType serviceType, GatewayConfig gatewayConfig, Map<String, String> options, String implementation)
            throws ServiceLifecycleException {

        FederatedIdentityService service = null;
        if (shouldCreateService(implementation)) {
            // Embedded H2 is the zero-config OOTB default that replaced the retired Derby backend (KNOX-3401).
            if (matchesImplementation(implementation, H2DBFederatedIdentityService.class, true)) {
                service = createH2Service(gatewayServices, gatewayConfig, options);
            } else if (matchesImplementation(implementation, JdbcFederatedIdentityService.class)) {
                service = createJdbcService(gatewayServices, gatewayConfig, options);
            }
            if (service != null) {
                logServiceUsage(service.getClass().getName(), serviceType);
            }
        }
        return service;
    }

    private FederatedIdentityService createH2Service(GatewayServices gatewayServices, GatewayConfig gatewayConfig, Map<String, String> options)
            throws ServiceLifecycleException {
        try {
            final H2DBFederatedIdentityService h2Service = new H2DBFederatedIdentityService();
            h2Service.setAliasService(getAliasService(gatewayServices));
            h2Service.setMasterService(getMasterService(gatewayServices));
            h2Service.init(gatewayConfig, options);
            return h2Service;
        } catch (ServiceLifecycleException e) {
            LOG.errorInitializingService(H2DBFederatedIdentityService.class.getName(), e.getMessage(), e);
            throw e;
        }
    }

    private FederatedIdentityService createJdbcService(GatewayServices gatewayServices, GatewayConfig gatewayConfig, Map<String, String> options)
            throws ServiceLifecycleException {
        try {
            final JdbcFederatedIdentityService jdbcService = new JdbcFederatedIdentityService();
            jdbcService.setAliasService(getAliasService(gatewayServices));
            jdbcService.init(gatewayConfig, options);
            return jdbcService;
        } catch (ServiceLifecycleException e) {
            LOG.errorInitializingService(JdbcFederatedIdentityService.class.getName(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected ServiceType getServiceType() {
        return ServiceType.KNOXIDF_FEDERATED_IDENTITY_SERVICE;
    }

    @Override
    protected Collection<String> getKnownImplementations() {
        return List.of(H2DBFederatedIdentityService.class.getName(), JdbcFederatedIdentityService.class.getName());
    }
}

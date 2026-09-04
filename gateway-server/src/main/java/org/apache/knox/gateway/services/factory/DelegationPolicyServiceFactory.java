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
import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.delegation.EmptyDelegationPolicyService;
import org.apache.knox.gateway.services.knoxidf.delegation.H2DBDelegationPolicyService;
import org.apache.knox.gateway.services.knoxidf.delegation.JdbcDelegationPolicyService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DelegationPolicyServiceFactory extends AbstractServiceFactory {

  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  private static final String DEFAULT_IMPLEMENTATION = EmptyDelegationPolicyService.class.getName();

  @Override
  protected Service createService(GatewayServices gatewayServices, ServiceType serviceType,
      GatewayConfig gatewayConfig, Map<String, String> options, String implementation)
      throws ServiceLifecycleException {

    String implementationToUse = implementation;
    // No explicit impl configured: auto-select a persistence backend when KnoxIDF is deployed.
    // Otherwise honor the configured impl (very likely a prod JDBC store).
    if (isEmptyDefaultImplementation(implementationToUse) && isKnoxIdfEnabledInAnyTopology(gatewayServices, gatewayConfig)) {
      implementationToUse = chooseAutoImplementation(gatewayConfig);
    }

    Service service = null;
    if (shouldCreateService(implementationToUse)) {
      if (matchesImplementation(implementationToUse, EmptyDelegationPolicyService.class, true)) {
        service = new EmptyDelegationPolicyService();
      } else if (matchesImplementation(implementationToUse, H2DBDelegationPolicyService.class)) {
        service = createH2Service(gatewayServices, gatewayConfig, options);
      } else if (matchesImplementation(implementationToUse, JdbcDelegationPolicyService.class)) {
        service = createJdbcService(gatewayServices, gatewayConfig, options);
      }
      if (service != null) {
        logServiceUsage(service.getClass().getName(), serviceType);
      }
    }
    return service;
  }

  /**
   * Chooses the auto-enabled implementation when KnoxIDF is deployed with no explicit impl: an
   * operator-configured external database wins (very likely a prod JDBC store); everything else
   * (the {@code none} default, or an explicit {@code h2}) selects the self-provisioning embedded H2
   * store that replaced the retired Derby backend as the zero-config default (KNOX-3401) so the
   * delegation policy registry works out of the box without any extra infrastructure.
   */
  String chooseAutoImplementation(GatewayConfig gatewayConfig) {
    return isExternalDatabaseConfigured(gatewayConfig)
        ? JdbcDelegationPolicyService.class.getName()
        : H2DBDelegationPolicyService.class.getName();
  }

  private boolean isExternalDatabaseConfigured(GatewayConfig gatewayConfig) {
    try {
      // The embedded self-provisioning engine (H2) is not an external store.
      return DatabaseType.fromString(gatewayConfig.getDatabaseType()) != DatabaseType.H2;
    } catch (IllegalArgumentException e) {
      // "none" (the default) or any unrecognized value: no real external DB -> use embedded.
      return false;
    }
  }

  private Service createH2Service(GatewayServices gatewayServices, GatewayConfig gatewayConfig, Map<String, String> options)
      throws ServiceLifecycleException {
    try {
      final H2DBDelegationPolicyService h2Service = new H2DBDelegationPolicyService();
      h2Service.setAliasService(getAliasService(gatewayServices));
      h2Service.setMasterService(getMasterService(gatewayServices));
      h2Service.init(gatewayConfig, options);
      return h2Service;
    } catch (ServiceLifecycleException e) {
      LOG.errorInitializingService(H2DBDelegationPolicyService.class.getName(), e.getMessage(), e);
      return new EmptyDelegationPolicyService();
    }
  }

  private Service createJdbcService(GatewayServices gatewayServices, GatewayConfig gatewayConfig, Map<String, String> options)
      throws ServiceLifecycleException {
    try {
      final JdbcDelegationPolicyService jdbcService = new JdbcDelegationPolicyService();
      jdbcService.setAliasService(getAliasService(gatewayServices));
      jdbcService.init(gatewayConfig, options);
      return jdbcService;
    } catch (ServiceLifecycleException e) {
      LOG.errorInitializingService(JdbcDelegationPolicyService.class.getName(), e.getMessage(), e);
      return new EmptyDelegationPolicyService();
    }
  }

  @Override
  protected ServiceType getServiceType() {
    return ServiceType.DELEGATION_POLICY_SERVICE;
  }

  @Override
  protected Collection<String> getKnownImplementations() {
    return List.of(DEFAULT_IMPLEMENTATION, JdbcDelegationPolicyService.class.getName(), H2DBDelegationPolicyService.class.getName());
  }
}

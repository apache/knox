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
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.DerbyDBTrustedOidcIssuerService;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.EmptyTrustedOidcIssuerService;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.JdbcTrustedOidcIssuerService;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.TrustedOidcIssuerService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TrustedOidcIssuerServiceFactory extends AbstractServiceFactory {

  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  private static final String DEFAULT_IMPLEMENTATION = EmptyTrustedOidcIssuerService.class.getName();

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

    TrustedOidcIssuerService service = null;
    if (shouldCreateService(implementationToUse)) {
      if (matchesImplementation(implementationToUse, EmptyTrustedOidcIssuerService.class, true)) {
        service = new EmptyTrustedOidcIssuerService();
      } else if (matchesImplementation(implementationToUse, DerbyDBTrustedOidcIssuerService.class)) {
        service = createDerbyService(gatewayServices, gatewayConfig, options);
      } else if (matchesImplementation(implementationToUse, JdbcTrustedOidcIssuerService.class)) {
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
   * operator-configured external database wins (very likely a prod JDBC store), otherwise a
   * self-provisioning embedded Derby store (the {@code none}/{@code derbydb} default) so the
   * trusted OIDC issuer registry works out of the box without any extra infrastructure.
   */
  String chooseAutoImplementation(GatewayConfig gatewayConfig) {
    return isExternalDatabaseConfigured(gatewayConfig)
        ? JdbcTrustedOidcIssuerService.class.getName()
        : DerbyDBTrustedOidcIssuerService.class.getName();
  }

  private boolean isExternalDatabaseConfigured(GatewayConfig gatewayConfig) {
    final String databaseType = gatewayConfig.getDatabaseType();
    try {
      return DatabaseType.fromString(databaseType) != DatabaseType.DERBY;
    } catch (IllegalArgumentException e) {
      // "none" (the default) or any unrecognized value: no real external DB -> use Derby.
      return false;
    }
  }

  private TrustedOidcIssuerService createDerbyService(GatewayServices gatewayServices, GatewayConfig gatewayConfig, Map<String, String> options)
      throws ServiceLifecycleException {
    try {
      final DerbyDBTrustedOidcIssuerService derbyService = new DerbyDBTrustedOidcIssuerService();
      derbyService.setAliasService(getAliasService(gatewayServices));
      derbyService.setMasterService(getMasterService(gatewayServices));
      derbyService.init(gatewayConfig, options);
      return derbyService;
    } catch (ServiceLifecycleException e) {
      LOG.errorInitializingService(DerbyDBTrustedOidcIssuerService.class.getName(), e.getMessage(), e);
      return new EmptyTrustedOidcIssuerService();
    }
  }

  private TrustedOidcIssuerService createJdbcService(GatewayServices gatewayServices, GatewayConfig gatewayConfig, Map<String, String> options)
      throws ServiceLifecycleException {
    try {
      final JdbcTrustedOidcIssuerService jdbcService = new JdbcTrustedOidcIssuerService();
      jdbcService.setAliasService(getAliasService(gatewayServices));
      jdbcService.init(gatewayConfig, options);
      return jdbcService;
    } catch (ServiceLifecycleException e) {
      LOG.errorInitializingService(JdbcTrustedOidcIssuerService.class.getName(), e.getMessage(), e);
      return new EmptyTrustedOidcIssuerService();
    }
  }

  @Override
  protected ServiceType getServiceType() {
    return ServiceType.TRUSTED_OIDC_ISSUER_SERVICE;
  }

  @Override
  protected Collection<String> getKnownImplementations() {
    return List.of(DEFAULT_IMPLEMENTATION, JdbcTrustedOidcIssuerService.class.getName(), DerbyDBTrustedOidcIssuerService.class.getName());
  }
}

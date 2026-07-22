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
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.EmptyTrustedOidcIssuerService;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.JdbcTrustedOidcIssuerService;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.TrustedOidcIssuerService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Topology;

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
    if (isEmptyDefaultImplementation(implementationToUse)) {
      if (isKnoxIdfEnabledInAnyTopology(gatewayServices)) {
        implementationToUse = JdbcTrustedOidcIssuerService.class.getName();
      }
    }

    TrustedOidcIssuerService service = null;
    if (shouldCreateService(implementationToUse)) {
      if (matchesImplementation(implementationToUse, EmptyTrustedOidcIssuerService.class, true)) {
        service = new EmptyTrustedOidcIssuerService();
      } else if (matchesImplementation(implementationToUse, JdbcTrustedOidcIssuerService.class)) {
        try {
          final JdbcTrustedOidcIssuerService jdbcService = new JdbcTrustedOidcIssuerService();
          jdbcService.setAliasService(getAliasService(gatewayServices));
          jdbcService.init(gatewayConfig, options);
          service = jdbcService;
        } catch (ServiceLifecycleException e) {
          LOG.errorInitializingService(implementationToUse, e.getMessage(), e);
          service = new EmptyTrustedOidcIssuerService();
        } catch (Exception e) {
          throw new ServiceLifecycleException(
              "Error while creating TrustedOidcIssuerService: " + e, e);
        }
      }
      if (service != null) {
        logServiceUsage(service.getClass().getName(), serviceType);
      }
    }
    return service;
  }

  /**
   * Returns true if any deployed topology contains a service with role {@code KNOXIDF}
   * or {@code KNOXIDF_ADMIN}. The trusted issuer registry is activated by either role
   * because the admin API ({@code KNOXIDF_ADMIN}) also needs to persist registrations.
   */
  private boolean isKnoxIdfEnabledInAnyTopology(GatewayServices gatewayServices) {
    final TopologyService topologyService = gatewayServices.getService(ServiceType.TOPOLOGY_SERVICE);
    if (topologyService != null) {
      for (Topology topology : topologyService.getTopologies()) {
        if (topology.getServices().stream().anyMatch(
            s -> "KNOXIDF".equals(s.getRole()) || "KNOXIDF_ADMIN".equals(s.getRole()))) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  protected ServiceType getServiceType() {
    return ServiceType.TRUSTED_OIDC_ISSUER_SERVICE;
  }

  @Override
  protected Collection<String> getKnownImplementations() {
    return List.of(DEFAULT_IMPLEMENTATION, JdbcTrustedOidcIssuerService.class.getName());
  }
}

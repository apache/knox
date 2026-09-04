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
package org.apache.knox.gateway.services.factory;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.token.impl.DefaultTokenStateService;
import org.apache.knox.gateway.services.token.impl.H2DBTokenStateService;
import org.apache.knox.gateway.services.token.impl.JDBCTokenStateService;

import java.util.Collection;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

public class TokenStateServiceFactory extends AbstractServiceFactory {

  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);

  @Override
  protected Service createService(GatewayServices gatewayServices, ServiceType serviceType, GatewayConfig gatewayConfig, Map<String, String> options, String implementation)
      throws ServiceLifecycleException {
    Service service = null;
    if (shouldCreateService(implementation)) {
      // Embedded H2 is the zero-config OOTB default that replaces the retired Derby backend (KNOX-3401).
      if (matchesImplementation(implementation, H2DBTokenStateService.class, true)) {
        service = useH2DatabaseTokenStateService(gatewayServices, gatewayConfig, options);
      } else if (matchesImplementation(implementation, DefaultTokenStateService.class)) {
        service = new DefaultTokenStateService();
      } else if (matchesImplementation(implementation, JDBCTokenStateService.class)) {
        try {
          service = new JDBCTokenStateService();
          ((JDBCTokenStateService) service).setAliasService(getAliasService(gatewayServices));
          service.init(gatewayConfig, options);
        } catch (ServiceLifecycleException e) {
          LOG.errorInitializingService(implementation, e.getMessage(), e);
          service = useH2DatabaseTokenStateService(gatewayServices, gatewayConfig, options);
        }
      }

      logServiceUsage(service.getClass().getName(), serviceType);
    }

    return service;
  }

  private Service useH2DatabaseTokenStateService(GatewayServices gatewayServices, GatewayConfig gatewayConfig, Map<String, String> options) {
    Service service;
    try {
      service = new H2DBTokenStateService();
      ((H2DBTokenStateService) service).setAliasService(getAliasService(gatewayServices));
      ((H2DBTokenStateService) service).setMasterService(getMasterService(gatewayServices));
      service.init(gatewayConfig, options);
    } catch (ServiceLifecycleException e) {
      LOG.errorInitializingService(H2DBTokenStateService.class.getName(), e.getMessage(), e);
      service = new DefaultTokenStateService();
    }
    return service;
  }

  @Override
  protected ServiceType getServiceType() {
    return ServiceType.TOKEN_STATE_SERVICE;
  }

  @Override
  protected Collection<String> getKnownImplementations() {
    return unmodifiableList(asList(DefaultTokenStateService.class.getName(), JDBCTokenStateService.class.getName(), H2DBTokenStateService.class.getName()));
  }
}

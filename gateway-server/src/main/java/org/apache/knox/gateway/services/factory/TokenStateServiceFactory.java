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

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import java.util.Collection;
import java.util.Map;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.token.impl.AliasBasedTokenStateService;
import org.apache.knox.gateway.services.token.impl.DefaultTokenStateService;
import org.apache.knox.gateway.services.token.impl.JDBCTokenStateService;
import org.apache.knox.gateway.services.token.impl.JournalBasedTokenStateService;
import org.apache.knox.gateway.services.token.impl.ZookeeperTokenStateService;

public class TokenStateServiceFactory extends AbstractServiceFactory {

  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);

  @Override
  protected Service createService(GatewayServices gatewayServices, ServiceType serviceType, GatewayConfig gatewayConfig, Map<String, String> options, String implementation)
      throws ServiceLifecycleException {
    Service service = null;
    if (shouldCreateService(implementation)) {
      if (matchesImplementation(implementation, DefaultTokenStateService.class)) {
        service = new DefaultTokenStateService();
      } else if (matchesImplementation(implementation, AliasBasedTokenStateService.class, true)) {
        service = new AliasBasedTokenStateService();
        ((AliasBasedTokenStateService) service).setAliasService(getAliasService(gatewayServices));
      } else if (matchesImplementation(implementation, JournalBasedTokenStateService.class)) {
        service = new JournalBasedTokenStateService();
      } else if (matchesImplementation(implementation, ZookeeperTokenStateService.class)) {
        service = new ZookeeperTokenStateService(gatewayServices);
      } else if (matchesImplementation(implementation, JDBCTokenStateService.class)) {
        try {
          service = new JDBCTokenStateService();
          ((JDBCTokenStateService) service).setAliasService(getAliasService(gatewayServices));
          service.init(gatewayConfig, options);
        } catch (ServiceLifecycleException e) {
          LOG.errorInitializingService(implementation, e.getMessage(), e);
          service = new AliasBasedTokenStateService();
          ((AliasBasedTokenStateService) service).setAliasService(getAliasService(gatewayServices));
        }
      }

      logServiceUsage(isEmptyDefaultImplementation(implementation) ? AliasBasedTokenStateService.class.getName() : implementation, serviceType);
    }

    return service;
  }

  @Override
  protected ServiceType getServiceType() {
    return ServiceType.TOKEN_STATE_SERVICE;
  }

  @Override
  protected Collection<String> getKnownImplementations() {
    return unmodifiableList(asList(DefaultTokenStateService.class.getName(), AliasBasedTokenStateService.class.getName(), JournalBasedTokenStateService.class.getName(),
        ZookeeperTokenStateService.class.getName(), JDBCTokenStateService.class.getName()));
  }
}

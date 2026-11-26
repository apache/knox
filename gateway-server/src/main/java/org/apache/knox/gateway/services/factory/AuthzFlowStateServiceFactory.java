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
import org.apache.knox.gateway.services.knoxidf.flowstate.AuthzFlowStateService;
import org.apache.knox.gateway.services.knoxidf.flowstate.AuthzFlowStateServiceMessages;
import org.apache.knox.gateway.services.knoxidf.flowstate.InMemoryAuthzFlowStateService;
import org.apache.knox.gateway.services.knoxidf.flowstate.JdbcAuthzFlowStateService;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Creates the KnoxIDF authorization flow state service: JDBC for an external gateway database,
 * memory otherwise. Embedded H2 is local to one instance, so it would add nothing over memory.
 */
public class AuthzFlowStateServiceFactory extends AbstractServiceFactory {

  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  private static final AuthzFlowStateServiceMessages FLOW_STATE_LOG = MessagesFactory.get(AuthzFlowStateServiceMessages.class);

  private static final Set<DatabaseType> EXTERNAL_DATABASE_TYPES =
      EnumSet.of(DatabaseType.POSTGRESQL, DatabaseType.MYSQL, DatabaseType.MARIADB, DatabaseType.ORACLE);

  @Override
  protected Service createService(GatewayServices gatewayServices, ServiceType serviceType, GatewayConfig gatewayConfig, Map<String, String> options, String implementation)
      throws ServiceLifecycleException {
    AuthzFlowStateService service = null;
    if (shouldCreateService(implementation)) {
      if (matchesImplementation(implementation, JdbcAuthzFlowStateService.class)) {
        // Explicitly configured: fail rather than silently lose cross-instance flows.
        service = createJdbcService(gatewayServices, gatewayConfig, options);
      } else if (matchesImplementation(implementation, InMemoryAuthzFlowStateService.class)) {
        service = createInMemoryService(gatewayConfig, options);
      } else if (implementation == null || isEmptyDefaultImplementation(implementation)) {
        service = createDefaultService(gatewayServices, gatewayConfig, options);
      }
      if (service != null) {
        logServiceUsage(service.getClass().getName(), serviceType);
      }
    }
    return service;
  }

  private AuthzFlowStateService createDefaultService(GatewayServices gatewayServices, GatewayConfig gatewayConfig, Map<String, String> options)
      throws ServiceLifecycleException {
    if (isExternalDatabase(gatewayConfig.getDatabaseType())) {
      try {
        return createJdbcService(gatewayServices, gatewayConfig, options);
      } catch (ServiceLifecycleException e) {
        FLOW_STATE_LOG.fallingBackToInMemoryAuthzFlowState(e.getMessage(), e);
      }
    }
    return createInMemoryService(gatewayConfig, options);
  }

  private AuthzFlowStateService createJdbcService(GatewayServices gatewayServices, GatewayConfig gatewayConfig, Map<String, String> options)
      throws ServiceLifecycleException {
    try {
      final JdbcAuthzFlowStateService jdbcService = new JdbcAuthzFlowStateService();
      jdbcService.setAliasService(getAliasService(gatewayServices));
      jdbcService.init(gatewayConfig, options);
      return jdbcService;
    } catch (ServiceLifecycleException e) {
      LOG.errorInitializingService(JdbcAuthzFlowStateService.class.getName(), e.getMessage(), e);
      throw e;
    }
  }

  private AuthzFlowStateService createInMemoryService(GatewayConfig gatewayConfig, Map<String, String> options) {
    final InMemoryAuthzFlowStateService inMemoryService = new InMemoryAuthzFlowStateService();
    inMemoryService.init(gatewayConfig, options);
    return inMemoryService;
  }

  private static boolean isExternalDatabase(String databaseType) {
    if (databaseType == null) {
      return false;
    }
    final String type = databaseType.trim().toLowerCase(Locale.ROOT);
    return EXTERNAL_DATABASE_TYPES.stream().anyMatch(dt -> dt.type().equals(type));
  }

  @Override
  protected ServiceType getServiceType() {
    return ServiceType.KNOXIDF_AUTHZ_FLOW_STATE_SERVICE;
  }

  @Override
  protected Collection<String> getKnownImplementations() {
    return List.of(InMemoryAuthzFlowStateService.class.getName(), JdbcAuthzFlowStateService.class.getName());
  }
}

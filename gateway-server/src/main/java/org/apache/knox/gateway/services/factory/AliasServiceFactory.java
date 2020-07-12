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

import org.apache.knox.gateway.backend.hashicorp.vault.HashicorpVaultAliasService;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.impl.DefaultAliasService;
import org.apache.knox.gateway.services.security.impl.RemoteAliasService;
import org.apache.knox.gateway.services.security.impl.ZookeeperRemoteAliasService;
import org.apache.knox.gateway.services.security.impl.ZookeeperRemoteAliasServiceProvider;

public class AliasServiceFactory extends AbstractServiceFactory {

  @Override
  protected Service createService(GatewayServices gatewayServices, ServiceType serviceType, GatewayConfig gatewayConfig, Map<String, String> options, String implementation)
      throws ServiceLifecycleException {
    Service service = null;
    if (shouldCreateService(implementation)) {
      final AliasService defaultAliasService = new DefaultAliasService();
      ((DefaultAliasService) defaultAliasService).setMasterService(getMasterService(gatewayServices));
      ((DefaultAliasService) defaultAliasService).setKeystoreService(getKeystoreService(gatewayServices));
      defaultAliasService.init(gatewayConfig, options); // invoking init on DefaultAliasService twice is ok (in case implementation is set to 'default')

      if (matchesImplementation(implementation, DefaultAliasService.class, true)) {
        service = defaultAliasService;
      } else if (matchesImplementation(implementation, HashicorpVaultAliasService.class)) {
        service = new HashicorpVaultAliasService(defaultAliasService);
      } else if (matchesImplementation(implementation, RemoteAliasService.class)) {
        service = new RemoteAliasService(defaultAliasService, getMasterService(gatewayServices));
      } else if (matchesImplementation(implementation, ZookeeperRemoteAliasService.class)) {
        service = new ZookeeperRemoteAliasServiceProvider().newInstance(defaultAliasService, getMasterService(gatewayServices));
      }

      logServiceUsage(implementation, serviceType);
    }
    return service;
  }

  @Override
  protected ServiceType getServiceType() {
    return ServiceType.ALIAS_SERVICE;
  }

  @Override
  protected Collection<String> getKnownImplementations() {
    return unmodifiableList(
        asList(DefaultAliasService.class.getName(), HashicorpVaultAliasService.class.getName(), RemoteAliasService.class.getName(), ZookeeperRemoteAliasService.class.getName()));
  }
}

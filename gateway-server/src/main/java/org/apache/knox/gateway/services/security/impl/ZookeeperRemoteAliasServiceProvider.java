/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.services.security.impl;

import java.util.Locale;

import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.config.ConfigurationException;
import org.apache.knox.gateway.security.RemoteAliasServiceProvider;
import org.apache.knox.gateway.service.config.remote.zk.ZooKeeperClientService;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.util.KnoxCLI;

public class ZookeeperRemoteAliasServiceProvider implements RemoteAliasServiceProvider {
  @Override
  public String getType() {
    return ZookeeperRemoteAliasService.TYPE;
  }

  @Override
  public AliasService newInstance(AliasService localAliasService, MasterService ms) {
    return newInstance(null, localAliasService, ms);
  }

  @Override
  public AliasService newInstance(GatewayServices gatewayServices, AliasService localAliasService, MasterService masterService) {
    final RemoteConfigurationRegistryClientService registryClientService = getRemoteConfigRegistryClientService(gatewayServices);
    if (registryClientService != null) {
      return new ZookeeperRemoteAliasService(localAliasService, masterService, registryClientService);
    }

    throw new ConfigurationException(String.format(Locale.ROOT, "%s service not configured", ZooKeeperClientService.TYPE));
  }

  private RemoteConfigurationRegistryClientService getRemoteConfigRegistryClientService(GatewayServices gatewayServices) {
    GatewayServices services = gatewayServices;
    if (gatewayServices == null) {
      services = GatewayServer.getGatewayServices() != null ? GatewayServer.getGatewayServices() : KnoxCLI.getGatewayServices();
    }
    return services == null ? null : services.getService(ServiceType.REMOTE_REGISTRY_CLIENT_SERVICE);
  }
}

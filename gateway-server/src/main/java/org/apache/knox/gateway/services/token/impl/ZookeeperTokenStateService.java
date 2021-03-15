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
package org.apache.knox.gateway.services.token.impl;

import static org.apache.knox.gateway.services.ServiceType.ALIAS_SERVICE;

import java.util.Map;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.factory.AliasServiceFactory;
import org.apache.knox.gateway.services.security.impl.ZookeeperRemoteAliasService;

/**
 * A Zookeeper Token State Service is actually an Alias based TSS where the 'alias service' happens to be the 'zookeeper' implementation.
 * This means the only important thing that should be overridden here is the init method where the underlying alias service is configured
 * properly.
 */
public class ZookeeperTokenStateService extends AliasBasedTokenStateService {

  private final GatewayServices gatewayServices;
  private final AliasServiceFactory aliasServiceFactory;

  public ZookeeperTokenStateService(GatewayServices gatewayServices) {
    this(gatewayServices, new AliasServiceFactory());
  }

  public ZookeeperTokenStateService(GatewayServices gatewayServices, AliasServiceFactory aliasServiceFactory) {
    this.gatewayServices = gatewayServices;
    this.aliasServiceFactory = aliasServiceFactory;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    final ZookeeperRemoteAliasService zookeeperAliasService = (ZookeeperRemoteAliasService) aliasServiceFactory.create(gatewayServices, ALIAS_SERVICE, config, options,
        ZookeeperRemoteAliasService.class.getName());
    options.put(ZookeeperRemoteAliasService.OPTION_NAME_SHOULD_CREATE_TOKENS_SUB_NODE, "true");
    zookeeperAliasService.init(config, options);
    super.setAliasService(zookeeperAliasService);
    super.init(config, options);
    options.remove(ZookeeperRemoteAliasService.OPTION_NAME_SHOULD_CREATE_TOKENS_SUB_NODE);
  }
}

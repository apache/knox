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
package org.apache.knox.gateway.services;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.service.config.remote.RemoteConfigurationRegistryClientServiceFactory;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.impl.RemoteAliasService;
import org.apache.knox.gateway.services.topology.impl.DefaultTopologyService;
import org.apache.knox.gateway.services.security.impl.DefaultAliasService;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.apache.knox.gateway.services.security.impl.DefaultKeystoreService;
import org.apache.knox.gateway.services.security.impl.CLIMasterService;
import org.apache.knox.gateway.topology.Provider;

import java.util.List;
import java.util.Map;

public class CLIGatewayServices extends AbstractGatewayServices {

  public CLIGatewayServices() {
    super("Services", "GatewayServices");
  }

  @Override
  public void init(GatewayConfig config, Map<String,String> options) throws ServiceLifecycleException {
    CLIMasterService ms = new CLIMasterService();
    ms.init(config, options);
    addService(ServiceType.MASTER_SERVICE, ms);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, options);
    addService(ServiceType.KEYSTORE_SERVICE, ks);

    DefaultAliasService defaultAlias = new DefaultAliasService();
    defaultAlias.setKeystoreService(ks);
    defaultAlias.setMasterService(ms);
    defaultAlias.init(config, options);

    /*
    Doesn't make sense for this to be set to the remote alias service since the impl could
    be remote itself. This uses the default alias service in case of ZK digest authentication.
    IE: If ZK digest auth and using ZK remote alias service, then wouldn't be able to connect
    to ZK anyway due to the circular dependency.
     */
    final RemoteConfigurationRegistryClientService registryClientService =
        RemoteConfigurationRegistryClientServiceFactory.newInstance(config);
    registryClientService.setAliasService(defaultAlias);
    registryClientService.init(config, options);
    addService(ServiceType.REMOTE_REGISTRY_CLIENT_SERVICE, registryClientService);


    /* create an instance so that it can be passed to other services */
    final RemoteAliasService alias = new RemoteAliasService(defaultAlias, ms);
    /*
     * Setup and initialize remote Alias Service.
     * NOTE: registryClientService.init() needs to
     * be called before alias.start();
     */
    alias.init(config, options);
    addService(ServiceType.ALIAS_SERVICE, alias);

    DefaultCryptoService crypto = new DefaultCryptoService();
    crypto.setKeystoreService(ks);
    crypto.setAliasService(alias);
    crypto.init(config, options);
    addService(ServiceType.CRYPTO_SERVICE, crypto);

    DefaultTopologyService tops = new DefaultTopologyService();
    tops.init(  config, options  );
    addService(ServiceType.TOPOLOGY_SERVICE, tops);
  }

  @Override
  public void initializeContribution(DeploymentContext context) {
  }

  @Override
  public void contributeProvider(DeploymentContext context, Provider provider) {
  }

  @Override
  public void contributeFilter(DeploymentContext context, Provider provider,
      org.apache.knox.gateway.topology.Service service,
      ResourceDescriptor resource, List<FilterParamDescriptor> params) {
  }

  @Override
  public void finalizeContribution(DeploymentContext context) {
  }
}

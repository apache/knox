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

import java.util.List;
import java.util.Map;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.services.security.impl.CLIMasterService;
import org.apache.knox.gateway.topology.Provider;

public class CLIGatewayServices extends AbstractGatewayServices {

  private final GatewayServiceFactory gatewayServiceFactory = new GatewayServiceFactory();

  public CLIGatewayServices() {
    super("Services", "GatewayServices");
  }

  @Override
  public void init(GatewayConfig config, Map<String,String> options) throws ServiceLifecycleException {
    addService(ServiceType.MASTER_SERVICE, gatewayServiceFactory.create(this, ServiceType.MASTER_SERVICE, config, options, CLIMasterService.class.getName()));
    addService(ServiceType.KEYSTORE_SERVICE, gatewayServiceFactory.create(this, ServiceType.KEYSTORE_SERVICE, config, options));

    /*
    Doesn't make sense for this to be set to the remote alias service since the impl could
    be remote itself. This uses the default alias service in case of ZK digest authentication.
    IE: If ZK digest auth and using ZK remote alias service, then wouldn't be able to connect
    to ZK anyway due to the circular dependency.
     */
    addService(ServiceType.REMOTE_REGISTRY_CLIENT_SERVICE, gatewayServiceFactory.create(this, ServiceType.REMOTE_REGISTRY_CLIENT_SERVICE, config, options));

    addService(ServiceType.ALIAS_SERVICE, gatewayServiceFactory.create(this, ServiceType.ALIAS_SERVICE, config, options));

    addService(ServiceType.CRYPTO_SERVICE, gatewayServiceFactory.create(this, ServiceType.CRYPTO_SERVICE, config, options));

    addService(ServiceType.TOPOLOGY_SERVICE, gatewayServiceFactory.create(this, ServiceType.TOPOLOGY_SERVICE, config, options));
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

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

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.topology.Provider;

public class DefaultGatewayServices extends AbstractGatewayServices {
  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );
  private final GatewayServiceFactory gatewayServiceFactory = new GatewayServiceFactory();

  public DefaultGatewayServices() {
    super("Services", "GatewayServices");
  }

  @Override
  public void init(GatewayConfig config, Map<String,String> options) throws ServiceLifecycleException {
    //order is important: different service factory implementations may use already added services
    addService(ServiceType.MASTER_SERVICE, gatewayServiceFactory.create(this, ServiceType.MASTER_SERVICE, config, options));
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

    addService(ServiceType.SSL_SERVICE, gatewayServiceFactory.create(this, ServiceType.SSL_SERVICE, config, options));

    // The DefaultTokenAuthorityService needs to be initialized after the JettySSLService to ensure
    // that the signing keystore is available for it.
    // probably should not allow the token service to be looked up?
    addService(ServiceType.TOKEN_SERVICE, gatewayServiceFactory.create(this, ServiceType.TOKEN_SERVICE, config, options));

    addService(ServiceType.TOKEN_STATE_SERVICE, gatewayServiceFactory.create(this, ServiceType.TOKEN_STATE_SERVICE, config, options));

    addService(ServiceType.SERVICE_REGISTRY_SERVICE, gatewayServiceFactory.create(this, ServiceType.SERVICE_REGISTRY_SERVICE, config, options));

    addService(ServiceType.HOST_MAPPING_SERVICE, gatewayServiceFactory.create(this, ServiceType.HOST_MAPPING_SERVICE, config, options));

    addService(ServiceType.SERVER_INFO_SERVICE, gatewayServiceFactory.create(this, ServiceType.SERVER_INFO_SERVICE, config, options));

    addService(ServiceType.CLUSTER_CONFIGURATION_MONITOR_SERVICE, gatewayServiceFactory.create(this, ServiceType.CLUSTER_CONFIGURATION_MONITOR_SERVICE, config, options));

    addService(ServiceType.TOPOLOGY_SERVICE, gatewayServiceFactory.create(this, ServiceType.TOPOLOGY_SERVICE, config, options));

    addService(ServiceType.SERVICE_DEFINITION_REGISTRY, gatewayServiceFactory.create(this, ServiceType.SERVICE_DEFINITION_REGISTRY, config, options));

    addService(ServiceType.METRICS_SERVICE, gatewayServiceFactory.create(this, ServiceType.METRICS_SERVICE, config, options));

    addService(ServiceType.CONCURRENT_SESSION_VERIFIER, gatewayServiceFactory.create(this, ServiceType.CONCURRENT_SESSION_VERIFIER, config, options));

    addService(ServiceType.GATEWAY_STATUS_SERVICE, gatewayServiceFactory.create(this, ServiceType.GATEWAY_STATUS_SERVICE, config, options));
  }

  @Override
  public void initializeContribution(DeploymentContext context) {
    // setup credential store as appropriate
    String clusterName = context.getTopology().getName();
    try {
      KeystoreService ks = getService(ServiceType.KEYSTORE_SERVICE);
      if (!ks.isCredentialStoreForClusterAvailable(clusterName)) {
        log.creatingCredentialStoreForCluster(clusterName);
        ks.createCredentialStoreForCluster(clusterName);
      }
      else {
        log.credentialStoreForClusterFoundNotCreating(clusterName);
      }
    } catch (KeystoreServiceException e) {
      throw new RuntimeException("Credential store was found but was unable to be loaded - the provided (or persisted) master secret may not match the password for the credential store.", e);
    }
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
    // Tell the provider the location of the descriptor.
    context.getWebAppDescriptor().createListener().listenerClass( GatewayServicesContextListener.class.getName() );
    context.getWebAppDescriptor().createListener().listenerClass(GatewayMetricsServletContextListener.class.getName());
  }
}

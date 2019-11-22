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

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.service.config.remote.RemoteConfigurationRegistryClientServiceFactory;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.registry.impl.DefaultServiceDefinitionRegistry;
import org.apache.knox.gateway.services.metrics.impl.DefaultMetricsService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.impl.RemoteAliasService;
import org.apache.knox.gateway.services.token.impl.AliasBasedTokenStateService;
import org.apache.knox.gateway.services.topology.impl.DefaultClusterConfigurationMonitorService;
import org.apache.knox.gateway.services.topology.impl.DefaultTopologyService;
import org.apache.knox.gateway.services.hostmap.impl.DefaultHostMapperService;
import org.apache.knox.gateway.services.registry.impl.DefaultServiceRegistryService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.impl.DefaultAliasService;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.apache.knox.gateway.services.security.impl.DefaultKeystoreService;
import org.apache.knox.gateway.services.security.impl.DefaultMasterService;
import org.apache.knox.gateway.services.security.impl.JettySSLService;
import org.apache.knox.gateway.services.token.impl.DefaultTokenAuthorityService;
import org.apache.knox.gateway.topology.Provider;

import java.util.List;
import java.util.Map;

public class DefaultGatewayServices extends AbstractGatewayServices {
  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );

  public DefaultGatewayServices() {
    super("Services", "GatewayServices");
  }

  @Override
  public void init(GatewayConfig config, Map<String,String> options) throws ServiceLifecycleException {
    DefaultMasterService ms = new DefaultMasterService();
    ms.init(config, options);
    addService(ServiceType.MASTER_SERVICE, ms);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, options);
    addService(ServiceType.KEYSTORE_SERVICE, ks);

    final DefaultAliasService defaultAlias = new DefaultAliasService();
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

    JettySSLService ssl = new JettySSLService();
    ssl.setAliasService(alias);
    ssl.setKeystoreService(ks);
    ssl.init(config, options);
    addService(ServiceType.SSL_SERVICE, ssl);

    // The DefaultTokenAuthorityService needs to be initialized after the JettySSLService to ensure
    // that the signing keystore is available for it.
    DefaultTokenAuthorityService ts = new DefaultTokenAuthorityService();
    ts.setAliasService(alias);
    ts.setKeystoreService(ks);
    ts.init(config, options);
    // prolly should not allow the token service to be looked up?
    addService(ServiceType.TOKEN_SERVICE, ts);

    AliasBasedTokenStateService tss = new AliasBasedTokenStateService();
    tss.setAliasService(alias);
    tss.init(config, options);
    addService(ServiceType.TOKEN_STATE_SERVICE, tss);

    DefaultServiceRegistryService sr = new DefaultServiceRegistryService();
    sr.setCryptoService( crypto );
    sr.init( config, options );
    addService(ServiceType.SERVICE_REGISTRY_SERVICE, sr);

    DefaultHostMapperService hm = new DefaultHostMapperService();
    hm.init( config, options );
    addService(ServiceType.HOST_MAPPING_SERVICE, hm );

    DefaultServerInfoService sis = new DefaultServerInfoService();
    sis.init( config, options );
    addService(ServiceType.SERVER_INFO_SERVICE, sis );

    DefaultClusterConfigurationMonitorService ccs = new DefaultClusterConfigurationMonitorService();
    ccs.setAliasService(alias);
    ccs.init(config, options);
    addService(ServiceType.CLUSTER_CONFIGURATION_MONITOR_SERVICE, ccs);

    DefaultTopologyService tops = new DefaultTopologyService();
    tops.setAliasService(alias);
    tops.init(  config, options  );
    addService(ServiceType.TOPOLOGY_SERVICE, tops  );

    DefaultServiceDefinitionRegistry sdr = new DefaultServiceDefinitionRegistry();
    sdr.init( config, options );
    addService(ServiceType.SERVICE_DEFINITION_REGISTRY, sdr );

    DefaultMetricsService metricsService = new DefaultMetricsService();
    metricsService.init( config, options );
    addService(ServiceType.METRICS_SERVICE, metricsService );
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

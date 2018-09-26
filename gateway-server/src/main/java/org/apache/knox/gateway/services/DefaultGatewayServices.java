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
import org.apache.knox.gateway.services.security.impl.RemoteAliasService;
import org.apache.knox.gateway.services.topology.impl.DefaultClusterConfigurationMonitorService;
import org.apache.knox.gateway.services.topology.impl.DefaultTopologyService;
import org.apache.knox.gateway.services.hostmap.impl.DefaultHostMapperService;
import org.apache.knox.gateway.services.registry.impl.DefaultServiceRegistryService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.SSLService;
import org.apache.knox.gateway.services.security.impl.DefaultAliasService;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.apache.knox.gateway.services.security.impl.DefaultKeystoreService;
import org.apache.knox.gateway.services.security.impl.DefaultMasterService;
import org.apache.knox.gateway.services.security.impl.JettySSLService;
import org.apache.knox.gateway.services.token.impl.DefaultTokenAuthorityService;
import org.apache.knox.gateway.topology.Provider;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultGatewayServices implements GatewayServices {

  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );

  private Map<String,Service> services = new HashMap<>();
  private DefaultMasterService ms = null;
  private DefaultKeystoreService ks = null;

  public DefaultGatewayServices() {
    super();
  }

  public void init(GatewayConfig config, Map<String,String> options) throws ServiceLifecycleException {
    ms = new DefaultMasterService();
    ms.init(config, options);
    services.put("MasterService", ms);

    ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, options);
    services.put(KEYSTORE_SERVICE, ks);

    /* create an instance so that it can be passed to other services */
    final RemoteAliasService alias = new RemoteAliasService();

    final RemoteConfigurationRegistryClientService registryClientService =
        RemoteConfigurationRegistryClientServiceFactory.newInstance(config);
    registryClientService.setAliasService(alias);
    registryClientService.init(config, options);
    services.put(REMOTE_REGISTRY_CLIENT_SERVICE, registryClientService);

    final DefaultAliasService defaultAlias = new DefaultAliasService();
    defaultAlias.setKeystoreService(ks);
    defaultAlias.setMasterService(ms);
    defaultAlias.init(config, options);

    /*
     * Setup and initialize remote Alias Service.
     * NOTE: registryClientService.init() needs to
     * be called before alias.start();
     */
    alias.setLocalAliasService(defaultAlias);
    alias.setMasterService(ms);
    alias.setRegistryClientService(registryClientService);
    alias.init(config, options);
    alias.start();
    services.put(ALIAS_SERVICE, alias);


    DefaultCryptoService crypto = new DefaultCryptoService();
    crypto.setKeystoreService(ks);
    crypto.setAliasService(alias);
    crypto.init(config, options);
    services.put(CRYPTO_SERVICE, crypto);
    
    DefaultTokenAuthorityService ts = new DefaultTokenAuthorityService();
    ts.setAliasService(alias);
    ts.setKeystoreService(ks);
    ts.init(config, options);
    // prolly should not allow the token service to be looked up?
    services.put(TOKEN_SERVICE, ts);
    
    JettySSLService ssl = new JettySSLService();
    ssl.setAliasService(alias);
    ssl.setKeystoreService(ks);
    ssl.setMasterService(ms);
    ssl.init(config, options);
    services.put(SSL_SERVICE, ssl);

    DefaultServiceRegistryService sr = new DefaultServiceRegistryService();
    sr.setCryptoService( crypto );
    sr.init( config, options );
    services.put( SERVICE_REGISTRY_SERVICE, sr );

    DefaultHostMapperService hm = new DefaultHostMapperService();
    hm.init( config, options );
    services.put( HOST_MAPPING_SERVICE, hm );

    DefaultServerInfoService sis = new DefaultServerInfoService();
    sis.init( config, options );
    services.put( SERVER_INFO_SERVICE, sis );


    DefaultClusterConfigurationMonitorService ccs = new DefaultClusterConfigurationMonitorService();
    ccs.setAliasService(alias);
    ccs.init(config, options);
    services.put(CLUSTER_CONFIGURATION_MONITOR_SERVICE, ccs);

    DefaultTopologyService tops = new DefaultTopologyService();
    tops.setAliasService(alias);
    tops.init(  config, options  );
    services.put(  TOPOLOGY_SERVICE, tops  );

    DefaultServiceDefinitionRegistry sdr = new DefaultServiceDefinitionRegistry();
    sdr.init( config, options );
    services.put( SERVICE_DEFINITION_REGISTRY, sdr );

    DefaultMetricsService metricsService = new DefaultMetricsService();
    metricsService.init( config, options );
    services.put( METRICS_SERVICE, metricsService );
  }

  public void start() throws ServiceLifecycleException {
    ms.start();

    ks.start();

    Service alias = services.get(ALIAS_SERVICE);
    alias.start();

    SSLService ssl = (SSLService) services.get(SSL_SERVICE);
    ssl.start();

    ServerInfoService sis = (ServerInfoService) services.get(SERVER_INFO_SERVICE);
    sis.start();

    RemoteConfigurationRegistryClientService clientService =
                            (RemoteConfigurationRegistryClientService)services.get(REMOTE_REGISTRY_CLIENT_SERVICE);
    clientService.start();

    (services.get(CLUSTER_CONFIGURATION_MONITOR_SERVICE)).start();

    DefaultTopologyService tops = (DefaultTopologyService)services.get(TOPOLOGY_SERVICE);
    tops.start();

    DefaultMetricsService metricsService = (DefaultMetricsService) services.get(METRICS_SERVICE);
    metricsService.start();
  }

  public void stop() throws ServiceLifecycleException {
    ms.stop();

    ks.stop();

    (services.get(CLUSTER_CONFIGURATION_MONITOR_SERVICE)).stop();

    Service alias = services.get(ALIAS_SERVICE);
    alias.stop();

    SSLService ssl = (SSLService) services.get(SSL_SERVICE);
    ssl.stop();

    ServerInfoService sis = (ServerInfoService) services.get(SERVER_INFO_SERVICE);
    sis.stop();

    DefaultTopologyService tops = (DefaultTopologyService)services.get(TOPOLOGY_SERVICE);
    tops.stop();

    DefaultMetricsService metricsService = (DefaultMetricsService) services.get(METRICS_SERVICE);
    metricsService.stop();

  }
  
  /* (non-Javadoc)
   * @see org.apache.knox.gateway.GatewayServices#getServiceNames()
   */
  @Override
  public Collection<String> getServiceNames() {
    return services.keySet();
  }
  
  /* (non-Javadoc)
   * @see org.apache.knox.gateway.GatewayServices#getService(java.lang.String)
   */
  @Override
  public <T> T getService(String serviceName) {
    return (T)services.get(serviceName);
  }

  @Override
  public String getRole() {
    return "Services";
  }

  @Override
  public String getName() {
    return "GatewayServices";
  }

  @Override
  public void initializeContribution(DeploymentContext context) {
    // setup credential store as appropriate
    String clusterName = context.getTopology().getName();
    try {
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

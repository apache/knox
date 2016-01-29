/**
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
package org.apache.hadoop.gateway.services;

import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.topology.impl.DefaultTopologyService;
import org.apache.hadoop.gateway.services.hostmap.impl.DefaultHostMapperService;
import org.apache.hadoop.gateway.services.registry.impl.DefaultServiceRegistryService;
import org.apache.hadoop.gateway.services.security.KeystoreServiceException;
import org.apache.hadoop.gateway.services.security.SSLService;
import org.apache.hadoop.gateway.services.security.impl.DefaultAliasService;
import org.apache.hadoop.gateway.services.security.impl.DefaultCryptoService;
import org.apache.hadoop.gateway.services.security.impl.DefaultKeystoreService;
import org.apache.hadoop.gateway.services.security.impl.DefaultMasterService;
import org.apache.hadoop.gateway.services.security.impl.JettySSLService;
import org.apache.hadoop.gateway.services.token.impl.DefaultTokenAuthorityService;
import org.apache.hadoop.gateway.topology.Provider;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultGatewayServices implements GatewayServices {

  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );

  private Map<String,Service> services = new HashMap<String, Service>();
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
    
    DefaultAliasService alias = new DefaultAliasService();
    alias.setKeystoreService(ks);
    alias.setMasterService(ms);
    alias.init(config, options);
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

    DefaultTopologyService tops = new DefaultTopologyService();
    tops.init(  config, options  );
    services.put(  TOPOLOGY_SERVICE, tops  );
  }
  
  public void start() throws ServiceLifecycleException {
    ms.start();

    ks.start();

    DefaultAliasService alias = (DefaultAliasService) services.get(ALIAS_SERVICE);
    alias.start();

    SSLService ssl = (SSLService) services.get(SSL_SERVICE);
    ssl.start();

    ServerInfoService sis = (ServerInfoService) services.get(SERVER_INFO_SERVICE);
    sis.start();

    DefaultTopologyService tops = (DefaultTopologyService)services.get(TOPOLOGY_SERVICE);
    tops.start();
  }

  public void stop() throws ServiceLifecycleException {
    ms.stop();

    ks.stop();

    DefaultAliasService alias = (DefaultAliasService) services.get(ALIAS_SERVICE);
    alias.stop();

    SSLService ssl = (SSLService) services.get(SSL_SERVICE);
    ssl.stop();

    ServerInfoService sis = (ServerInfoService) services.get(SERVER_INFO_SERVICE);
    sis.stop();

    DefaultTopologyService tops = (DefaultTopologyService)services.get(TOPOLOGY_SERVICE);
    tops.stop();
  }
  
  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.GatewayServices#getServiceNames()
   */
  @Override
  public Collection<String> getServiceNames() {
    return services.keySet();
  }
  
  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.GatewayServices#getService(java.lang.String)
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
      org.apache.hadoop.gateway.topology.Service service,
      ResourceDescriptor resource, List<FilterParamDescriptor> params) {
  }

  @Override
  public void finalizeContribution(DeploymentContext context) {
    // Tell the provider the location of the descriptor.
    context.getWebAppDescriptor().createListener().listenerClass( GatewayServicesContextListener.class.getName() );
  }
}

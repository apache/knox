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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ProviderDeploymentContributor;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.Service;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.security.impl.DefaultAliasService;
import org.apache.hadoop.gateway.services.security.impl.DefaultCryptoService;
import org.apache.hadoop.gateway.services.security.impl.DefaultKeystoreService;
import org.apache.hadoop.gateway.services.security.impl.DefaultMasterService;
import org.apache.hadoop.gateway.topology.Provider;

public class DefaultGatewayServices implements Service, ProviderDeploymentContributor, GatewayServices {
  private static final String GATEWAY_IDENTITY_PASSPHRASE = "gateway-identity-passphrase";
  private static final String GATEWAY_CREDENTIAL_STORE_NAME = "__gateway";
  public static String CRYPTO_SERVICE = "CryptoService";
  public static String ALIAS_SERVICE = "AliasService";

  private Map<String,Service> services = new HashMap<String, Service>();
  private DefaultMasterService ms = null;
  private DefaultKeystoreService ks = null;

  public DefaultGatewayServices() {
    super();
  }

  public void init(GatewayConfig config, Map<String,String> options) throws ServiceLifecycleException {
    ms = new DefaultMasterService();
    ms.init(config, options);

    ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, options);
    
    DefaultAliasService alias = new DefaultAliasService();
    alias.setKeystoreService(ks);
    alias.init(config, options);
    services.put(ALIAS_SERVICE, alias);

    DefaultCryptoService crypto = new DefaultCryptoService();
    crypto.setAliasService(alias);
    crypto.init(config, options);
    services.put(CRYPTO_SERVICE, crypto);
    
    // do gateway global provisioning
    provisionSSLArtifacts();
  }

  private void provisionSSLArtifacts() {
    DefaultAliasService alias = (DefaultAliasService) services.get(ALIAS_SERVICE);
    if (!ks.isCredentialStoreForClusterAvailable(GATEWAY_CREDENTIAL_STORE_NAME)) {
      System.out.println("creating credstore for gateway");
      ks.createCredentialStoreForCluster(GATEWAY_CREDENTIAL_STORE_NAME);
      alias.generateAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
    }
    else {
      // TODO: log appropriately
      System.out.println("credstore found for gateway - no need to create one");
    }

    if (!ks.isKeystoreForGatewayAvailable()) {
      System.out.println("creating keystore for gateway");
      ks.createKeystoreForGateway();
      char[] passphrase = alias.getPasswordFromAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
      ks.addSelfSignedCertForGateway("gateway-identity", passphrase);
    }
    else {
      // TODO: log appropriately
      System.out.println("keystore found for gateway - no need to create one");
    }
  }
  
  public void start() throws ServiceLifecycleException {
    ms.start();

    ks.start();

    DefaultAliasService alias = (DefaultAliasService) services.get(ALIAS_SERVICE);
    alias.start();
  }

  public void stop() throws ServiceLifecycleException {
    ms.stop();

    ks.stop();

    DefaultAliasService alias = (DefaultAliasService) services.get(ALIAS_SERVICE);
    alias.stop();
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
  public Service getService(String serviceName) {
    return services.get(serviceName);
  }

  @Override
  public String getRole() {
    // TODO Auto-generated method stub
    return "Services";
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return "GatewayServices";
  }

  @Override
  public void initializeContribution(DeploymentContext context) {
    String clusterName = context.getTopology().getName();
    if (!ks.isCredentialStoreForClusterAvailable(clusterName)) {
      System.out.println("creating credentialstore for cluster: " + clusterName);
      ks.createCredentialStoreForCluster(clusterName);
    }
    else {
      // TODO: log appropriately
      System.out.println("credentialstore found for: " + clusterName + " - no need to create one");
    }
  }

  @Override
  public void contributeProvider(DeploymentContext context, Provider provider) {
  }

  @Override
  public void contributeFilter(DeploymentContext context, Provider provider,
      org.apache.hadoop.gateway.topology.Service service,
      ResourceDescriptor resource, List<FilterParamDescriptor> params) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void finalizeContribution(DeploymentContext context) {
    // Tell the provider the location of the descriptor.
    context.getWebAppDescriptor().createListener().listenerClass( GatewayServicesContextListener.class.getName() );
  }
}

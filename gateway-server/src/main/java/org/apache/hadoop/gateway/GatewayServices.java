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
package org.apache.hadoop.gateway;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ProviderDeploymentContributor;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.services.Service;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.security.impl.DefaultAliasService;
import org.apache.hadoop.gateway.services.security.impl.DefaultCryptoService;
import org.apache.hadoop.gateway.services.security.impl.DefaultKeystoreService;
import org.apache.hadoop.gateway.services.security.impl.DefaultMasterService;
import org.apache.hadoop.gateway.topology.Provider;

public class GatewayServices implements Service, ProviderDeploymentContributor {
  public static String CRYPTO_SERVICE = "CryptoService";
  public static String ALIAS_SERVICE = "AliasService";

  private Map<String,Service> services = new HashMap<String, Service>();
  private DefaultMasterService ms = null;
  private DefaultKeystoreService ks = null;

  public GatewayServices() {
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
  
  public Collection<String> getServiceNames() {
    return services.keySet();
  }
  
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
    // TODO Auto-generated method stub
    
  }
}

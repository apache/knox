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

import java.util.Collection;

import org.apache.knox.gateway.deploy.ProviderDeploymentContributor;

public interface GatewayServices extends Service,
    ProviderDeploymentContributor {

  public static final String GATEWAY_CLUSTER_ATTRIBUTE = "org.apache.knox.gateway.gateway.cluster";
  public static final String GATEWAY_SERVICES_ATTRIBUTE = "org.apache.knox.gateway.gateway.services";

  public static final String SSL_SERVICE = "SSLService";
  public static final String CRYPTO_SERVICE = "CryptoService";
  public static final String ALIAS_SERVICE = "AliasService";
  public static final String KEYSTORE_SERVICE = "KeystoreService";
  public static final String TOKEN_SERVICE = "TokenService";
  public static final String SERVICE_REGISTRY_SERVICE = "ServiceRegistryService";
  public static final String HOST_MAPPING_SERVICE = "HostMappingService";
  public static final String SERVER_INFO_SERVICE = "ServerInfoService";
  public static final String TOPOLOGY_SERVICE = "TopologyService";
  public static final String SERVICE_DEFINITION_REGISTRY = "ServiceDefinitionRegistry";
  public static final String METRICS_SERVICE = "MetricsService";

  String REMOTE_REGISTRY_CLIENT_SERVICE = "RemoteConfigRegistryClientService";

  String CLUSTER_CONFIGURATION_MONITOR_SERVICE = "ClusterConfigurationMonitorService";

  public abstract Collection<String> getServiceNames();

  public abstract <T> T getService( String serviceName );

}

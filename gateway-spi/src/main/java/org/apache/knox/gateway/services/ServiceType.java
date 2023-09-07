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

import java.util.Locale;

public enum ServiceType {
  ALIAS_SERVICE("AliasService"),
  CLUSTER_CONFIGURATION_MONITOR_SERVICE("ClusterConfigurationMonitorService"),
  CRYPTO_SERVICE("CryptoService"),
  HOST_MAPPING_SERVICE("HostMappingService"),
  KEYSTORE_SERVICE("KeystoreService"),
  MASTER_SERVICE("MasterService"),
  METRICS_SERVICE("MetricsService"),
  REMOTE_REGISTRY_CLIENT_SERVICE("RemoteConfigRegistryClientService"),
  SERVER_INFO_SERVICE("ServerInfoService"),
  SERVICE_DEFINITION_REGISTRY("ServiceDefinitionRegistry"),
  SERVICE_REGISTRY_SERVICE("ServiceRegistryService"),
  SSL_SERVICE("SSLService"),
  TOKEN_SERVICE("TokenService"),
  TOKEN_STATE_SERVICE("TokenStateService"),
  TOPOLOGY_SERVICE("TopologyService"),
  CONCURRENT_SESSION_VERIFIER("ConcurrentSessionVerifier"),
  REMOTE_CONFIGURATION_MONITOR("RemoteConfigurationMonitor"),
  GATEWAY_STATUS_SERVICE("GatewayStatusService");

  private final String serviceTypeName;
  private final String shortName;

  ServiceType(String serviceTypeName) {
    this.serviceTypeName = serviceTypeName;
    this.shortName = serviceTypeName.toLowerCase(Locale.ROOT).replace("service", "");
  }

  public String getServiceTypeName() {
    return serviceTypeName;
  }

  public String getShortName() {
    return shortName;
  }
}

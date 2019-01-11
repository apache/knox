/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.monitor;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;


public class DefaultRemoteConfigurationMonitorTest {

  @Test(expected=IllegalStateException.class)
  public void testInitWithoutRequiredConfig() {
    GatewayConfig testConfig = new TestGatewayConfig();
    new DefaultRemoteConfigurationMonitor(testConfig, new TestRemoteConfigurationRegistryClientService());
  }

  private static class TestGatewayConfig implements GatewayConfig {
    @Override
    public String getGatewayConfDir() {
      return null;
    }

    @Override
    public String getGatewayDataDir() {
      return null;
    }

    @Override
    public String getGatewayServicesDir() {
      return null;
    }

    @Override
    public String getGatewayApplicationsDir() {
      return null;
    }

    @Override
    public String getHadoopConfDir() {
      return null;
    }

    @Override
    public String getGatewayHost() {
      return null;
    }

    @Override
    public int getGatewayPort() {
      return 0;
    }

    @Override
    public String getGatewayPath() {
      return null;
    }

    @Override
    public String getGatewayProvidersConfigDir() {
      return "./shared-providers";
    }

    @Override
    public String getGatewayDescriptorsDir() {
      return "./descriptors";
    }

    @Override
    public String getGatewayTopologyDir() {
      return null;
    }

    @Override
    public String getGatewaySecurityDir() {
      return null;
    }

    @Override
    public String getGatewayDeploymentDir() {
      return null;
    }

    @Override
    public InetSocketAddress getGatewayAddress() throws UnknownHostException {
      return null;
    }

    @Override
    public boolean isSSLEnabled() {
      return false;
    }

    @Override
    public List<String> getExcludedSSLProtocols() {
      return null;
    }

    @Override
    public List<String> getIncludedSSLCiphers() {
      return null;
    }

    @Override
    public List<String> getExcludedSSLCiphers() {
      return null;
    }

    @Override
    public boolean isHadoopKerberosSecured() {
      return false;
    }

    @Override
    public String getKerberosConfig() {
      return null;
    }

    @Override
    public boolean isKerberosDebugEnabled() {
      return false;
    }

    @Override
    public String getKerberosLoginConfig() {
      return null;
    }

    @Override
    public String getDefaultTopologyName() {
      return null;
    }

    @Override
    public String getDefaultAppRedirectPath() {
      return null;
    }

    @Override
    public String getFrontendUrl() {
      return null;
    }

    @Override
    public boolean isClientAuthNeeded() {
      return false;
    }

    @Override
    public boolean isClientAuthWanted() {
      return false;
    }

    @Override
    public String getTruststorePath() {
      return null;
    }

    @Override
    public boolean getTrustAllCerts() {
      return false;
    }

    @Override
    public String getKeystoreType() {
      return null;
    }

    @Override
    public String getTruststoreType() {
      return null;
    }

    @Override
    public boolean isXForwardedEnabled() {
      return false;
    }

    @Override
    public String getEphemeralDHKeySize() {
      return null;
    }

    @Override
    public int getHttpClientMaxConnections() {
      return 0;
    }

    @Override
    public int getHttpClientConnectionTimeout() {
      return 0;
    }

    @Override
    public int getHttpClientSocketTimeout() {
      return 0;
    }

    @Override
    public int getThreadPoolMax() {
      return 0;
    }

    @Override
    public int getHttpServerRequestBuffer() {
      return 0;
    }

    @Override
    public int getHttpServerRequestHeaderBuffer() {
      return 0;
    }

    @Override
    public int getHttpServerResponseBuffer() {
      return 0;
    }

    @Override
    public int getHttpServerResponseHeaderBuffer() {
      return 0;
    }

    @Override
    public int getGatewayDeploymentsBackupVersionLimit() {
      return 0;
    }

    @Override
    public long getGatewayDeploymentsBackupAgeLimit() {
      return 0;
    }

    @Override
    public long getGatewayIdleTimeout() {
      return 0;
    }

    @Override
    public String getSigningKeystoreName() {
      return null;
    }

    @Override
    public String getSigningKeyAlias() {
      return null;
    }

    @Override
    public List<String> getGlobalRulesServices() {
      return null;
    }

    @Override
    public boolean isWebsocketEnabled() {
      return false;
    }

    @Override
    public int getWebsocketMaxTextMessageSize() {
      return 0;
    }

    @Override
    public int getWebsocketMaxBinaryMessageSize() {
      return 0;
    }

    @Override
    public int getWebsocketMaxTextMessageBufferSize() {
      return 0;
    }

    @Override
    public int getWebsocketMaxBinaryMessageBufferSize() {
      return 0;
    }

    @Override
    public int getWebsocketInputBufferSize() {
      return 0;
    }

    @Override
    public int getWebsocketAsyncWriteTimeout() {
      return 0;
    }

    @Override
    public int getWebsocketIdleTimeout() {
      return 0;
    }

    @Override
    public boolean isMetricsEnabled() {
      return false;
    }

    @Override
    public boolean isJmxMetricsReportingEnabled() {
      return false;
    }

    @Override
    public boolean isGraphiteMetricsReportingEnabled() {
      return false;
    }

    @Override
    public String getGraphiteHost() {
      return null;
    }

    @Override
    public int getGraphitePort() {
      return 0;
    }

    @Override
    public int getGraphiteReportingFrequency() {
      return 0;
    }

    @Override
    public boolean isCookieScopingToPathEnabled() {
      return false;
    }

    @Override
    public String getHeaderNameForRemoteAddress() {
      return null;
    }

    @Override
    public String getAlgorithm() {
      return null;
    }

    @Override
    public String getPBEAlgorithm() {
      return null;
    }

    @Override
    public String getTransformation() {
      return null;
    }

    @Override
    public String getSaltSize() {
      return null;
    }

    @Override
    public String getIterationCount() {
      return null;
    }

    @Override
    public String getKeyLength() {
      return null;
    }

    @Override
    public Map<String, Integer> getGatewayPortMappings() {
      return null;
    }

    @Override
    public boolean isGatewayPortMappingEnabled() {
      return false;
    }

    @Override
    public boolean isGatewayServerHeaderEnabled() {
      return false;
    }

    @Override
    public String getDefaultDiscoveryAddress() {
      return null;
    }

    @Override
    public String getDefaultDiscoveryCluster() {
      return null;
    }

    @Override
    public int getClusterMonitorPollingInterval(String type) {
      return 0;
    }

    @Override
    public boolean isClusterMonitorEnabled(String type) {
      return false;
    }

    @Override
    public List<String> getRemoteRegistryConfigurationNames() {
      return null;
    }

    @Override
    public String getRemoteRegistryConfiguration(String name) {
      return null;
    }

    @Override
    public String getRemoteConfigurationMonitorClientName() {
      return null;
    }

    @Override
    public boolean allowUnauthenticatedRemoteRegistryReadAccess() {
      return false;
    }

    @Override
    public boolean isRemoteAliasServiceEnabled() {
      return false;
    }

    @Override
    public List<String> getReadOnlyOverrideTopologyNames() {
      return null;
    }

    @Override
    public String getKnoxAdminGroups() {
      return null;
    }

    @Override
    public String getKnoxAdminUsers() {
      return null;
    }

    @Override
    public String getFederationHeaderName() {
      return null;
    }

    @Override
    public List<String> getAutoDeployTopologyNames() {
      return null;
    }

    @Override
    public String getDispatchWhitelist() {
      return null;
    }

    @Override
    public List<String> getDispatchWhitelistServices() {
      return null;
    }

    @Override
    public boolean isTopologyValidationEnabled() {
      return false;
    }
  }

  private static class TestRemoteConfigurationRegistryClientService implements RemoteConfigurationRegistryClientService {
    @Override
    public void setAliasService(AliasService aliasService) {

    }

    @Override
    public RemoteConfigurationRegistryClient get(String l) {
      return null;
    }

    @Override
    public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {

    }

    @Override
    public void start() throws ServiceLifecycleException {

    }

    @Override
    public void stop() throws ServiceLifecycleException {

    }
  }
}

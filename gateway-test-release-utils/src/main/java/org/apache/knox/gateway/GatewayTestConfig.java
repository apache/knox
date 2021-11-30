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
package org.apache.knox.gateway;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class GatewayTestConfig extends Configuration implements GatewayConfig {

  /* Websocket defaults */
  public static final boolean DEFAULT_WEBSOCKET_FEATURE_ENABLED = false;
  public static final int DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE = Integer.MAX_VALUE;
  public static final int DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_SIZE = Integer.MAX_VALUE;
  public static final int DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_BUFFER_SIZE = 32768;
  public static final int DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_BUFFER_SIZE = 32768;
  public static final int DEFAULT_WEBSOCKET_INPUT_BUFFER_SIZE = 4096;
  public static final int DEFAULT_WEBSOCKET_ASYNC_WRITE_TIMEOUT = 60000;
  public static final int DEFAULT_WEBSOCKET_IDLE_TIMEOUT = 300000;
  public static final int DEFAULT_WEBSOCKET_MAX_WAIT_BUFFER_COUNT = 100;

  private Path gatewayHomePath = Paths.get("gateway-home");
  private String hadoopConfDir = "hadoop";
  private String gatewayHost = "localhost";
  private int gatewayPort;
  private String gatewayPath = "gateway";
  private boolean hadoopKerberosSecured;
  private String kerberosConfig;
  private boolean kerberosDebugEnabled;
  private String kerberosLoginConfig;
  private String frontendUrl;
  private boolean xForwardedEnabled = true;
  private String gatewayApplicationsDir;
  private String gatewayServicesDir;
  private String defaultTopologyName = "default";
  private List<String> includedSSLCiphers;
  private List<String> excludedSSLCiphers;
  private boolean sslEnabled;
  private String truststoreType = "jks";
  private String keystoreType = "jks";
  private boolean isTopologyPortMappingEnabled = true;
  private ConcurrentMap<String, Integer> topologyPortMapping = new ConcurrentHashMap<>();
  private int backupVersionLimit = -1;
  private long backupAgeLimit = -1;

  public GatewayTestConfig() {

    Iterable<Path> paths = FileSystems.getDefault().getRootDirectories();
    Path rootPath = null;
    for (Path path : paths) {
      rootPath = path;
    }
    if (rootPath == null) {
      rootPath = Paths.get("/");
    }

    // /etc/knox/conf
    Path etcConfKnoxPath = rootPath.resolve("etc").resolve("knox").resolve("conf");

    // /etc/knox/conf/krb5.conf
    kerberosConfig = etcConfKnoxPath.resolve("krb5.conf").toString();

    // /etc/knox/conf/krb5JAASLogin.conf
    kerberosLoginConfig = etcConfKnoxPath.resolve("krb5JAASLogin.conf").toString();
  }


  public void setGatewayHomeDir( String gatewayHomeDir ) {
    this.gatewayHomePath = Paths.get(gatewayHomeDir);
  }

  public String getGatewayHomeDir() {
    return gatewayHomePath.toString();
  }

  @Override
  public String getGatewayConfDir() {
    return getGatewayConfPath().toString();
  }

  private Path getGatewayConfPath() {
    return gatewayHomePath.resolve("conf");
  }

  @Override
  public String getGatewayDataDir() {
    return getGatewayDataPath().toString();
  }

  private Path getGatewayDataPath() {
    return gatewayHomePath.resolve("data");
  }

  @Override
  public String getGatewaySecurityDir() {
    return getGatewaySecurityPath().toString();
  }

  private Path getGatewaySecurityPath() {
    return getGatewayDataPath().resolve("security");
  }

  @Override
  public String getGatewayKeystoreDir() {
    return getGatewayKeystorePath().toString();
  }

  private Path getGatewayKeystorePath() {
    return getGatewayDataPath().resolve("keystores");
  }

  @Override
  public String getGatewayTopologyDir() {
    return gatewayHomePath.resolve("topologies").toString();
  }

  @Override
  public String getGatewayDeploymentDir() {
    return gatewayHomePath.resolve("deployments").toString();
  }

  @Override
  public String getHadoopConfDir() {
    return hadoopConfDir;
  }

  @Override
  public String getGatewayHost() {
    return gatewayHost;
  }

  @Override
  public int getGatewayPort() {
    return gatewayPort;
  }

  @Override
  public String getGatewayPath() {
    return gatewayPath;
  }

  public void setGatewayPath( String gatewayPath ) {
    this.gatewayPath = gatewayPath;
  }

  @Override
  public InetSocketAddress getGatewayAddress() throws UnknownHostException {
    return new InetSocketAddress( getGatewayHost(), getGatewayPort() );
  }

  @Override
  public long getGatewayIdleTimeout() {
    return 0L;
  }

  @Override
  public String getIdentityKeystorePath() {
    return getGatewayKeystorePath().resolve(DEFAULT_GATEWAY_KEYSTORE_NAME).toString();
  }

  @Override
  public String getIdentityKeystoreType() {
    return DEFAULT_IDENTITY_KEYSTORE_TYPE;
  }

  @Override
  public String getIdentityKeystorePasswordAlias() {
    return DEFAULT_IDENTITY_KEYSTORE_PASSWORD_ALIAS;
  }

  @Override
  public String getIdentityKeyAlias() {
    return DEFAULT_IDENTITY_KEY_ALIAS;
  }

  @Override
  public String getIdentityKeyPassphraseAlias() {
    return DEFAULT_IDENTITY_KEY_PASSPHRASE_ALIAS;
  }

  @Override
  public boolean isSSLEnabled() {
    return sslEnabled;
  }

  public void setSSLEnabled( boolean sslEnabled ) {
    this.sslEnabled = sslEnabled;
  }

  @Override
  public boolean isHadoopKerberosSecured() {
    return hadoopKerberosSecured;
  }

  public void setHadoopKerberosSecured(boolean hadoopKerberosSecured) {
    this.hadoopKerberosSecured = hadoopKerberosSecured;
  }

  @Override
  public String getKerberosConfig() {
    return kerberosConfig;
  }

  public void setKerberosConfig(String kerberosConfig) {
    this.kerberosConfig = kerberosConfig;
  }

  @Override
  public boolean isKerberosDebugEnabled() {
    return kerberosDebugEnabled;
  }

  public void setKerberosDebugEnabled(boolean kerberosDebugEnabled) {
    this.kerberosDebugEnabled = kerberosDebugEnabled;
  }

  @Override
  public String getKerberosLoginConfig() {
    return kerberosLoginConfig;
  }

  @Override
  public String getDefaultTopologyName() {
    return defaultTopologyName;
  }

  public void setDefaultTopologyName( String defaultTopologyName ) {
    this.defaultTopologyName = defaultTopologyName;
  }

  @Override
  public String getDefaultAppRedirectPath() {
    if(StringUtils.isBlank(this.defaultTopologyName)) {
      return "/gateway/sandbox";
    } else {
      return "/gateway/"+this.defaultTopologyName;
    }
  }

  @Override
  public String getFrontendUrl() { return frontendUrl; }

  public void setFrontendUrl( String frontendUrl ) {
    this.frontendUrl = frontendUrl;
  }

  @Override
  public List getExcludedSSLProtocols() {
    List<String> protocols = new ArrayList<>();
    protocols.add("SSLv3");
    return protocols;
  }

  @Override
  public List getIncludedSSLCiphers() {
    return includedSSLCiphers;
  }

  public void setIncludedSSLCiphers( List<String> list ) {
    includedSSLCiphers = list;
  }

  @Override
  public List getExcludedSSLCiphers() {
    return excludedSSLCiphers;
  }

  public void setExcludedSSLCiphers( List<String> list ) {
    excludedSSLCiphers = list;
  }

  @Override
  public boolean isClientAuthNeeded() {
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
  public String getTruststoreType() {
    return truststoreType;
  }

  @Override
  public String getTruststorePasswordAlias() {
    return null;
  }

  public void setTruststoreType( String truststoreType ) {
    this.truststoreType = truststoreType;
  }

  @Override
  public String getKeystoreType() {
    return keystoreType;
  }

  public void setKeystoreType( String keystoreType ) {
    this.keystoreType = keystoreType;
  }

  public void setKerberosLoginConfig(String kerberosLoginConfig) {
   this.kerberosLoginConfig = kerberosLoginConfig;
  }

   @Override
   public String getGatewayServicesDir() {
    if( gatewayServicesDir != null ) {
      return gatewayServicesDir;
    } else {
      return Paths.get(System.getProperty("user.dir"), "target", "services").toString();
    }
  }

  public void setGatewayServicesDir( String gatewayServicesDir ) {
    this.gatewayServicesDir = gatewayServicesDir;
  }

  @Override
  public String getGatewayApplicationsDir() {
    if( gatewayApplicationsDir != null ) {
      return gatewayApplicationsDir;
    } else {
      return getGatewayConfPath().resolve("applications").toString();
    }
  }

  public void setGatewayApplicationsDir( String gatewayApplicationsDir ) {
    this.gatewayApplicationsDir = gatewayApplicationsDir;
   }

  @Override
  public boolean isXForwardedEnabled() {
    return xForwardedEnabled;
  }

  public void setXForwardedEnabled(boolean enabled) {
    xForwardedEnabled = enabled;
  }

  @Override
  public String getEphemeralDHKeySize() {
    return "2048";
  }

  @Override
  public int getHttpClientMaxConnections() {
    return 16;
  }

  @Override
  public int getHttpClientConnectionTimeout() {
    return -1;
  }

  @Override
  public int getHttpClientSocketTimeout() {
    return -1;
  }

  @Override
  public String getHttpClientTruststorePath() {
    return null;
  }

  @Override
  public String getHttpClientTruststoreType() {
    return null;
  }

  @Override
  public String getHttpClientTruststorePasswordAlias() {
    return null;
  }

  @Override
  public String getCredentialStoreAlgorithm() {
    return DEFAULT_CREDENTIAL_STORE_ALG;
  }

  @Override
  public String getCredentialStoreType() {
    return DEFAULT_CREDENTIAL_STORE_TYPE;
  }

  @Override
  public int getThreadPoolMax() {
    return 254;
  }

  @Override
  public int getHttpServerRequestBuffer() {
    return 16*1024;
  }

  @Override
  public int getHttpServerRequestHeaderBuffer() {
    return 8*1024;
  }

  @Override
  public int getHttpServerResponseBuffer() {
    return 32*1024;
  }

  @Override
  public int getHttpServerResponseHeaderBuffer() {
    return 8*1024;
  }

  public void setGatewayDeploymentsBackupVersionLimit( int newBackupVersionLimit ) {
    backupVersionLimit = newBackupVersionLimit;
  }

  @Override
  public int getGatewayDeploymentsBackupVersionLimit() {
    return backupVersionLimit;
  }

  public void setTopologyPortMapping(ConcurrentMap<String, Integer> topologyPortMapping) {
    this.topologyPortMapping = topologyPortMapping;
  }

  public void setGatewayPortMappingEnabled(
      boolean topologyPortMappingEnabled) {
    isTopologyPortMappingEnabled = topologyPortMappingEnabled;
  }

  @Override
  public long getGatewayDeploymentsBackupAgeLimit() {
    return backupAgeLimit;
  }

  public void setGatewayDeploymentsBackupAgeLimit( long newBackupAgeLimit ) {
    backupAgeLimit = newBackupAgeLimit;
  }

  @Override
  public String getSigningKeystoreName() {
    return null;
  }

  @Override
  public String getSigningKeystorePath() {
    return getGatewayKeystorePath().resolve(DEFAULT_GATEWAY_KEYSTORE_NAME).toString();
  }

  @Override
  public String getSigningKeystoreType() {
    return DEFAULT_SIGNING_KEYSTORE_TYPE;
  }

  @Override
  public String getSigningKeyAlias() {
    return DEFAULT_SIGNING_KEY_ALIAS;
  }

  @Override
  public String getSigningKeystorePasswordAlias() {
    return DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS;
  }

  @Override
  public String getSigningKeyPassphraseAlias() {
    return DEFAULT_SIGNING_KEY_PASSPHRASE_ALIAS;
  }

  @Override
  public List<String> getGlobalRulesServices() {
    ArrayList<String> services = new ArrayList<>();
    services.add("WEBHDFS");
    services.add("HBASE");
    services.add("HIVE");
    services.add("OOZIE");
    services.add("RESOURCEMANAGER");
    services.add("STORM");
    return services;
  }

  @Override
  public boolean isWebsocketEnabled() {
    return DEFAULT_WEBSOCKET_FEATURE_ENABLED;
  }

  @Override
  public int getWebsocketMaxTextMessageSize() {
    return DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE;
  }

  @Override
  public int getWebsocketMaxBinaryMessageSize() {
    return DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_SIZE;
  }

  @Override
  public int getWebsocketMaxTextMessageBufferSize() {
    return DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_BUFFER_SIZE;
  }

  @Override
  public int getWebsocketMaxBinaryMessageBufferSize() {
    return DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_BUFFER_SIZE;
  }

  @Override
  public int getWebsocketInputBufferSize() {
    return DEFAULT_WEBSOCKET_INPUT_BUFFER_SIZE;
  }

  @Override
  public int getWebsocketAsyncWriteTimeout() {
    return DEFAULT_WEBSOCKET_ASYNC_WRITE_TIMEOUT;
  }

  @Override
  public int getWebsocketIdleTimeout() {
    return DEFAULT_WEBSOCKET_IDLE_TIMEOUT;
  }

  @Override
  public int getWebsocketMaxWaitBufferCount() {
    return DEFAULT_WEBSOCKET_MAX_WAIT_BUFFER_COUNT;
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
  public  boolean isCookieScopingToPathEnabled() {
    return false;
  }

  @Override
  public String getHeaderNameForRemoteAddress() {
    return "X-Forwarded-For";
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
    return topologyPortMapping;
  }

  @Override
  public boolean isGatewayPortMappingEnabled() {
    return isTopologyPortMappingEnabled;
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
  public boolean isClientAuthWanted() {
    return false;
  }

  @Override
  public String getGatewayProvidersConfigDir() {
    return getGatewayConfPath().resolve("shared-providers").toString();
  }

  @Override
  public String getGatewayDescriptorsDir() {
    return getGatewayConfPath().resolve("descriptors").toString();
  }

  @Override
  public List<String> getRemoteRegistryConfigurationNames() {
    return Collections.emptyList();
  }

  @Override
  public String getRemoteRegistryConfiguration(String s) {
    return null;
  }

  @Override
  public String getRemoteConfigurationMonitorClientName() {
    return null;
  }

  @Override
  public boolean isRemoteAliasServiceEnabled() {
    return true;
  }

  @Override
  public String getRemoteAliasServiceConfigurationPrefix() {
    return null;
  }

  @Override
  public Map<String, String> getRemoteAliasServiceConfiguration() {
    return Collections.emptyMap();
  }

  @Override
  public int getClusterMonitorPollingInterval(String type) {
    return 600;
  }

  @Override
  public boolean isClusterMonitorEnabled(String type) {
    return false;
  }

  @Override
  public boolean allowUnauthenticatedRemoteRegistryReadAccess() {
    return false;
  }

  @Override
  public List<String> getReadOnlyOverrideTopologyNames() {
    List<String> readOnly = new ArrayList<>();

    String value = get(GatewayConfigImpl.READ_ONLY_OVERRIDE_TOPOLOGIES);
    if (value != null && !value.isEmpty()) {
      readOnly.addAll(Arrays.asList(value.trim().split("\\s*,\\s*")));
    }

    return readOnly;
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
    return "SM_USER";
  }

  @Override
  public List<String> getAutoDeployTopologyNames() {
    ArrayList<String> names = new ArrayList<>();
    names.add("manager");
    names.add("admin");
    return null;
  }

  @Override
  public String getDispatchWhitelist() {
    return null;
  }

  @Override
  public List<String> getDispatchWhitelistServices() {
    return Collections.emptyList();
  }

  @Override
  public boolean isTopologyValidationEnabled() {
    return false;
  }

  @Override
  public boolean topologyRedeploymentRequiresChanges() {
    return false;
  }

  @Override
  public List<String> getXForwardContextAppendServices() {
    return null;
  }

  @Override
  public Set<String> getServicesToIgnoreDoAs() {
    return null;
  }

  @Override
  public long getClouderaManagerDescriptorsMonitoringInterval() {
    return 0;
  }

  @Override
  public long getClouderaManagerAdvancedServiceDiscoveryConfigurationMonitoringInterval() {
    return 0;
  }

  @Override
  public boolean isServerManagedTokenStateEnabled() {
    return false;
  }

  @Override
  public long getKnoxTokenEvictionInterval() {
    return 0;
  }

  @Override
  public long getKnoxTokenEvictionGracePeriod() {
    return 0;
  }

  @Override
  public long getKnoxTokenStateAliasPersistenceInterval() {
    return 0;
  }

  @Override
  public String getKnoxTokenHashAlgorithm() {
    return null;
  }

  @Override
  public int getMaximumNumberOfTokensPerUser() {
    return 0;
  }

  @Override
  public Set<String> getHiddenTopologiesOnHomepage() {
    return Collections.emptySet();
  }

  @Override
  public Set<String> getPinnedTopologiesOnHomepage() {
    return Collections.emptySet();
  }

  /**
   * @return returns whether know token permissive failure is enabled
   */
  @Override
  public boolean isKnoxTokenPermissiveValidationEnabled() {
    return false;
  }

  @Override
  public String getServiceParameter(String service, String parameter) {
    return "";
  }

  @Override
  public boolean homePageLogoutEnabled() {
    return false;
  }

  @Override
  public String getGlobalLogoutPageUrl() {
    return null;
  }

  @Override
  public long getKeystoreCacheSizeLimit() {
    return 0;
  }

  @Override
  public long getKeystoreCacheEntryTimeToLiveInMinutes() {
    return 0;
  }

  @Override
  public boolean isGatewayServerIncomingXForwardedSupportEnabled() {
    return true;
  }

  @Override
  public Map<String, Collection<String>> getHomePageProfiles() {
    return null;
  }

  @Override
  public String getDatabaseType() {
    return null;
  }

  @Override
  public String getDatabaseConnectionUrl() {
    return null;
  }

  @Override
  public String getDatabaseHost() {
    return null;
  }

  @Override
  public int getDatabasePort() {
    return 0;
  }

  @Override
  public String getDatabaseName() {
    return null;
  }


  @Override
  public boolean isDatabaseSslEnabled() {
    return false;
  }


  @Override
  public boolean verifyDatabaseSslServerCertificate() {
    return false;
  }


  @Override
  public String getDatabaseSslTruststoreFileName() {
    return null;
  }

}

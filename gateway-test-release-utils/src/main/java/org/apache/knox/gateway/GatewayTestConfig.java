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

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GatewayTestConfig extends Configuration implements GatewayConfig {

  /* Websocket defaults */
  public static final boolean DEFAULT_WEBSOCKET_FEATURE_ENABLED = false;
  public static final int DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE = Integer.MAX_VALUE;;
  public static final int DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_SIZE = Integer.MAX_VALUE;;
  public static final int DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_BUFFER_SIZE = 32768;
  public static final int DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_BUFFER_SIZE = 32768;
  public static final int DEFAULT_WEBSOCKET_INPUT_BUFFER_SIZE = 4096;
  public static final int DEFAULT_WEBSOCKET_ASYNC_WRITE_TIMEOUT = 60000;
  public static final int DEFAULT_WEBSOCKET_IDLE_TIMEOUT = 300000;

  private String gatewayHomeDir = "gateway-home";
  private String hadoopConfDir = "hadoop";
  private String gatewayHost = "localhost";
  private int gatewayPort = 0;
  private String gatewayPath = "gateway";
  private boolean hadoopKerberosSecured = false;
  private String kerberosConfig = "/etc/knox/conf/krb5.conf";
  private boolean kerberosDebugEnabled = false;
  private String kerberosLoginConfig = "/etc/knox/conf/krb5JAASLogin.conf";
  private String frontendUrl = null;
  private boolean xForwardedEnabled = true;
  private String gatewayApplicationsDir = null;
  private String gatewayServicesDir;
  private String defaultTopologyName = "default";
  private List<String> includedSSLCiphers = null;
  private List<String> excludedSSLCiphers = null;
  private boolean sslEnabled = false;
  private String truststoreType = "jks";
  private String keystoreType = "jks";
  private boolean isTopologyPortMappingEnabled = true;
  private ConcurrentHashMap<String, Integer> topologyPortMapping = new ConcurrentHashMap<>();
  private int backupVersionLimit = -1;
  private long backupAgeLimit = -1;

  public void setGatewayHomeDir( String gatewayHomeDir ) {
    this.gatewayHomeDir = gatewayHomeDir;
  }

  public String getGatewayHomeDir() {
    return this.gatewayHomeDir;
  }

  @Override
  public String getGatewayConfDir() {
    return gatewayHomeDir;
  }

  @Override
  public String getGatewayDataDir() {
    return gatewayHomeDir;
  }

  @Override
  public String getGatewaySecurityDir() {
    return gatewayHomeDir + "/security";
  }

  @Override
  public String getGatewayTopologyDir() {
    return gatewayHomeDir + "/topologies";
  }

  @Override
  public String getGatewayDeploymentDir() {
    return gatewayHomeDir + "/deployments";
  }

//  public void setDeploymentDir( String clusterConfDir ) {
//    this.deployDir = clusterConfDir;
//  }

  @Override
  public String getHadoopConfDir() {
    return hadoopConfDir;
  }

//  public void setHadoopConfDir( String hadoopConfDir ) {
//    this.hadoopConfDir = hadoopConfDir;
//  }

  @Override
  public String getGatewayHost() {
    return gatewayHost;
  }

//  public void setGatewayHost( String gatewayHost ) {
//    this.gatewayHost = gatewayHost;
//  }

  @Override
  public int getGatewayPort() {
    return gatewayPort;
  }

//  public void setGatewayPort( int gatewayPort ) {
//    this.gatewayPort = gatewayPort;
//  }

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


  public long getGatewayIdleTimeout() {
    return 0l;
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

  /* (non-Javadoc)
   * @see GatewayConfig#getDefaultAppRedirectPath()
   */
  @Override
  public String getDefaultAppRedirectPath() {

    if(StringUtils.isBlank(this.defaultTopologyName)) {
      return "/gateway/sandbox";
    } else {
      return "/gateway/"+this.defaultTopologyName;
    }

  }

  /* (non-Javadoc)
   * @see GatewayConfig#getFrontendUrl()
   */
  @Override
  public String getFrontendUrl() { return frontendUrl; }

  public void setFrontendUrl( String frontendUrl ) {
    this.frontendUrl = frontendUrl;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getExcludedSSLProtocols()
   */
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

  /* (non-Javadoc)
   * @see GatewayConfig#isClientAuthNeeded()
   */
  @Override
  public boolean isClientAuthNeeded() {
    // TODO Auto-generated method stub
    return false;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getTruststorePath()
   */
  @Override
  public String getTruststorePath() {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getTrustAllCerts()
   */
  @Override
  public boolean getTrustAllCerts() {
    // TODO Auto-generated method stub
    return false;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getTruststoreType()
   */
  @Override
  public String getTruststoreType() {
    return truststoreType;
  }

  public void setTruststoreType( String truststoreType ) {
    this.truststoreType = truststoreType;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getKeystoreType()
   */
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
      File targetDir = new File( System.getProperty( "user.dir" ), "target/services" );
      return targetDir.getPath();
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
      return getGatewayConfDir() + "/applications";
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

  /* (non-Javadoc)
   * @see GatewayConfig#getEphemeralDHKeySize()
   */
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

  public int getGatewayDeploymentsBackupVersionLimit() {
    return backupVersionLimit;
  }

  public void setTopologyPortMapping(ConcurrentHashMap<String, Integer> topologyPortMapping) {
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

  /* (non-Javadoc)
   * @see GatewayConfig#getSigningKeystoreName()
   */
  @Override
  public String getSigningKeystoreName() {
    return null;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getSigningKeyAlias()
   */
  @Override
  public String getSigningKeyAlias() {
    return null;
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

  /* (non-Javadoc)
   * @see GatewayConfig#isWebsocketEnabled()
   */
  @Override
  public boolean isWebsocketEnabled() {
    return DEFAULT_WEBSOCKET_FEATURE_ENABLED;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getWebsocketMaxTextMessageSize()
   */
  @Override
  public int getWebsocketMaxTextMessageSize() {
    return DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getWebsocketMaxBinaryMessageSize()
   */
  @Override
  public int getWebsocketMaxBinaryMessageSize() {
    return DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_SIZE;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getWebsocketMaxTextMessageBufferSize()
   */
  @Override
  public int getWebsocketMaxTextMessageBufferSize() {
    return DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_BUFFER_SIZE;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getWebsocketMaxBinaryMessageBufferSize()
   */
  @Override
  public int getWebsocketMaxBinaryMessageBufferSize() {
    return DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_BUFFER_SIZE;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getWebsocketInputBufferSize()
   */
  @Override
  public int getWebsocketInputBufferSize() {
    return DEFAULT_WEBSOCKET_INPUT_BUFFER_SIZE;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getWebsocketAsyncWriteTimeout()
   */
  @Override
  public int getWebsocketAsyncWriteTimeout() {
    return DEFAULT_WEBSOCKET_ASYNC_WRITE_TIMEOUT;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getWebsocketIdleTimeout()
   */
  @Override
  public int getWebsocketIdleTimeout() {
    return DEFAULT_WEBSOCKET_IDLE_TIMEOUT;
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

  /* (non-Javadoc)
   * @see GatewayConfig#getMimeTypesToCompress()
   */
  @Override
  public List<String> getMimeTypesToCompress() {
    return new ArrayList<String>();
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

  /**
   * Map of Topology names and their ports.
   *
   * @return
   */
  @Override
  public Map<String, Integer> getGatewayPortMappings() {
    return topologyPortMapping;
  }

  /**
   * Is the Port Mapping feature on ?
   *
   * @return
   */
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
    return null;
  }

  @Override
  public String getGatewayDescriptorsDir() {
    return null;
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

  /**
   * Returns whether the Remote Alias Service is enabled or not.
   * This value also depends on whether remote registry is enabled or not.
   * if it is enabled then this option takes effect else this option has no
   * effect.
   *
   * @return
   */
  @Override
  public boolean isRemoteAliasServiceEnabled() {
    return true;
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

  /**
   * Custom header name to be used to pass the authenticated principal via
   * dispatch
   *
   * @return
   * @since 1.1.0
   */
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

  public String getDispatchWhitelist() {
    return null;
  }

  @Override
  public List<String> getDispatchWhitelistServices() {
    return Collections.emptyList();
  }

  /**
   * Returns true when strict topology validation is enabled, in which case if
   * topology validation fails Knox will throw a runtime exception. If false and
   * topology validation fails Knox will log an ERROR and move on.
   *
   * @return
   * @since 1.1.0
   */
  @Override
  public boolean isTopologyValidationEnabled() {
    return false;
  }

}

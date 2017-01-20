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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.gateway.config.GatewayConfig;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GatewayTestConfig extends Configuration implements GatewayConfig {

  /* Websocket defaults */
  public static final boolean DEFAULT_WEBSOCKET_FEATURE_ENABLED =  false;
  public static final int DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE =  Integer.MAX_VALUE;;
  public static final int DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_SIZE =  Integer.MAX_VALUE;;
  public static final int DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_BUFFER_SIZE =  32768;
  public static final int DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_BUFFER_SIZE =  32768;
  public static final int DEFAULT_WEBSOCKET_INPUT_BUFFER_SIZE =  4096;
  public static final int DEFAULT_WEBSOCKET_ASYNC_WRITE_TIMEOUT =  60000;
  public static final int DEFAULT_WEBSOCKET_IDLE_TIMEOUT =  300000;

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

  public void setGatewayHomeDir( String gatewayHomeDir ) {
    this.gatewayHomeDir = gatewayHomeDir;
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

  @Override
  public boolean isSSLEnabled() {
    // TODO Auto-generated method stub
    return false;
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
  
//  public void setKerberosDebugEnabled(boolean kerberosConfigEnabled) {
//    this.kerberosDebugEnabled = kerberosDebugEnabled;
//  }
  
  @Override
  public String getKerberosLoginConfig() {
    return kerberosLoginConfig;
  }

  public void setKerberosLoginConfig(String kerberosLoginConfig) {
    this.kerberosLoginConfig = kerberosLoginConfig;
  }

  /* (non-Javadoc)
     * @see org.apache.hadoop.gateway.config.GatewayConfig#getDefaultTopologyName()
     */
  @Override
  public String getDefaultTopologyName() {
    return "default";
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getDefaultAppRedirectPath()
   */
  @Override
  public String getDefaultAppRedirectPath() {
    // TODO Auto-generated method stub
    return "/gateway/sandbox";
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getFrontendUrl()
   */
  @Override
  public String getFrontendUrl() { return frontendUrl; }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getExcludedSSLProtocols()
   */
  @Override
  public List getExcludedSSLProtocols() {
    List<String> protocols = new ArrayList<String>();
    protocols.add("SSLv3");
    return protocols;
  }

  @Override
  public List getIncludedSSLCiphers() {
    return null;
  }

  @Override
  public List getExcludedSSLCiphers() {
    return null;
  }

  public void setFrontendUrl( String frontendUrl ) {
    this.frontendUrl = frontendUrl;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#isClientAuthNeeded()
   */
  @Override
  public boolean isClientAuthNeeded() {
    // TODO Auto-generated method stub
    return false;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getTruststorePath()
   */
  @Override
  public String getTruststorePath() {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getTrustAllCerts()
   */
  @Override
  public boolean getTrustAllCerts() {
    // TODO Auto-generated method stub
    return false;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getTruststoreType()
   */
  @Override
  public String getTruststoreType() {
    // TODO Auto-generated method stub
    return null;
  }
  
  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getKeystoreType()
   */
  @Override
  public String getKeystoreType() {
    // TODO Auto-generated method stub
    return null;
  }

//  public void setKerberosLoginConfig(String kerberosLoginConfig) {
//   this.kerberosLoginConfig = kerberosLoginConfig;
//  }

   @Override
   public String getGatewayServicesDir() {
      return gatewayHomeDir + "/data/services";
   }

  @Override
  public String getGatewayApplicationsDir() {
    return gatewayHomeDir + "/conf/applications";
  }

  @Override
  public boolean isXForwardedEnabled() {
    return xForwardedEnabled;
  }

  public void setXForwardedEnabled(boolean enabled) {
    xForwardedEnabled = enabled;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getEphemeralDHKeySize()
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
    return 16;
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

  @Override
  public int getGatewayDeploymentsBackupVersionLimit() {
    return Integer.MAX_VALUE;
  }

  @Override
  public long getGatewayDeploymentsBackupAgeLimit() {
    return Long.MAX_VALUE;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getSigningKeystoreName()
   */
  @Override
  public String getSigningKeystoreName() {
    return null;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getSigningKeyAlias()
   */
  @Override
  public String getSigningKeyAlias() {
    return null;
  }

  @Override
  public List<String> getGlobalRulesServices() {
    return Collections.EMPTY_LIST;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#isWebsocketEnabled()
   */
  @Override
  public boolean isWebsocketEnabled() {
    return DEFAULT_WEBSOCKET_FEATURE_ENABLED;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getWebsocketMaxTextMessageSize()
   */
  @Override
  public int getWebsocketMaxTextMessageSize() {
    return DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getWebsocketMaxBinaryMessageSize()
   */
  @Override
  public int getWebsocketMaxBinaryMessageSize() {
    return DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_SIZE;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getWebsocketMaxTextMessageBufferSize()
   */
  @Override
  public int getWebsocketMaxTextMessageBufferSize() {
    return DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_BUFFER_SIZE;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getWebsocketMaxBinaryMessageBufferSize()
   */
  @Override
  public int getWebsocketMaxBinaryMessageBufferSize() {
    return DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_BUFFER_SIZE;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getWebsocketInputBufferSize()
   */
  @Override
  public int getWebsocketInputBufferSize() {
    return DEFAULT_WEBSOCKET_INPUT_BUFFER_SIZE;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getWebsocketAsyncWriteTimeout()
   */
  @Override
  public int getWebsocketAsyncWriteTimeout() {
    return DEFAULT_WEBSOCKET_ASYNC_WRITE_TIMEOUT;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getWebsocketIdleTimeout()
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
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getMimeTypesToCompress()
   */
  @Override
  public List<String> getMimeTypesToCompress() {
    return new ArrayList<String>();
  }
}

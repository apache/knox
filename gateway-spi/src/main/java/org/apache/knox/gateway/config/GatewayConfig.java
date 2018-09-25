/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.config;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public interface GatewayConfig {

  // Used as the basis for any home directory that is not specified.
  static final String GATEWAY_HOME_VAR = "GATEWAY_HOME";

  // Variable name for the location of configuration files edited by users
  static final String GATEWAY_CONF_HOME_VAR = "GATEWAY_CONF_HOME";

  // Variable name for the location of data files generated by the gateway at runtime.
  static final String GATEWAY_DATA_HOME_VAR = "GATEWAY_DATA_HOME";

  public static final String GATEWAY_CONFIG_ATTRIBUTE = "org.apache.knox.gateway.config";
  public static final String HADOOP_KERBEROS_SECURED = "gateway.hadoop.kerberos.secured";
  public static final String KRB5_CONFIG = "java.security.krb5.conf";
  public static final String KRB5_DEBUG = "sun.security.krb5.debug";
  public static final String KRB5_LOGIN_CONFIG = "java.security.auth.login.config";
  public static final String KRB5_USE_SUBJECT_CREDS_ONLY = "javax.security.auth.useSubjectCredsOnly";
  public static final String SIGNING_KEYSTORE_NAME = "gateway.signing.keystore.name";
  public static final String SIGNING_KEY_ALIAS = "gateway.signing.key.alias";

  String REMOTE_CONFIG_REGISTRY_TYPE = "type";
  String REMOTE_CONFIG_REGISTRY_ADDRESS = "address";
  String REMOTE_CONFIG_REGISTRY_NAMESPACE = "namespace";
  String REMOTE_CONFIG_REGISTRY_AUTH_TYPE = "authType";
  String REMOTE_CONFIG_REGISTRY_PRINCIPAL = "principal";
  String REMOTE_CONFIG_REGISTRY_CREDENTIAL_ALIAS = "credentialAlias";
  String REMOTE_CONFIG_REGISTRY_KEYTAB = "keytab";
  String REMOTE_CONFIG_REGISTRY_USE_KEYTAB = "useKeytab";
  String REMOTE_CONFIG_REGISTRY_USE_TICKET_CACHE = "useTicketCache";

  /**
   * The location of the gateway configuration.
   * Subdirectories will be: topologies
   * @return The location of the gateway configuration.
   */
  String getGatewayConfDir();

  /**
   * The location of the gateway runtime generated data.
   * Subdirectories will be security, deployments
   * @return The location of the gateway runtime generated data.
   */
  String getGatewayDataDir();

  /**
   * The location of the gateway services definition's root directory
   * @return The location of the gateway services top level directory.
   */
  String getGatewayServicesDir();

  /**
   * The location of the gateway applications's root directory
   * @return The location of the gateway applications top level directory.
   */
  String getGatewayApplicationsDir();

  String getHadoopConfDir();

  String getGatewayHost();

  int getGatewayPort();

  String getGatewayPath();

  String getGatewayProvidersConfigDir();

  String getGatewayDescriptorsDir();

  String getGatewayTopologyDir();

  String getGatewaySecurityDir();

  String getGatewayDeploymentDir();

  InetSocketAddress getGatewayAddress() throws UnknownHostException;

  boolean isSSLEnabled();
  
  List<String> getExcludedSSLProtocols();

  List<String> getIncludedSSLCiphers();

  List<String> getExcludedSSLCiphers();

  boolean isHadoopKerberosSecured();

  String getKerberosConfig();

  boolean isKerberosDebugEnabled();

  String getKerberosLoginConfig();

  String getDefaultTopologyName();

  String getDefaultAppRedirectPath();

  String getFrontendUrl();

  boolean isClientAuthNeeded();

  boolean isClientAuthWanted();

  String getTruststorePath();

  boolean getTrustAllCerts();

  String getKeystoreType();

  String getTruststoreType();

  boolean isXForwardedEnabled();

  String getEphemeralDHKeySize();

  int getHttpClientMaxConnections();

  int getHttpClientConnectionTimeout();

  int getHttpClientSocketTimeout();

  int getThreadPoolMax();

  int getHttpServerRequestBuffer();

  int getHttpServerRequestHeaderBuffer();

  int getHttpServerResponseBuffer();

  int getHttpServerResponseHeaderBuffer();

  int getGatewayDeploymentsBackupVersionLimit();

  long getGatewayDeploymentsBackupAgeLimit();

  long getGatewayIdleTimeout();

  String getSigningKeystoreName();

  String getSigningKeyAlias();

  List<String> getGlobalRulesServices();

  /**
   * Returns true if websocket feature enabled else false.
   * Default is false.
   * @since 0.10
   * @return
   */
  boolean isWebsocketEnabled();

  /**
   * Websocket connection max text message size.
   * @since 0.10
   * @return
   */
  int getWebsocketMaxTextMessageSize();

  /**
   * Websocket connection max binary message size.
   * @since 0.10
   * @return
   */
  int getWebsocketMaxBinaryMessageSize();

  /**
   * Websocket connection max text message buffer size.
   * @since 0.10
   * @return
   */
  int getWebsocketMaxTextMessageBufferSize();

  /**
   * Websocket connection max binary message buffer size.
   * @since 0.10
   * @return
   */
  int getWebsocketMaxBinaryMessageBufferSize();

  /**
   * Websocket connection input buffer size.
   * @since 0.10
   * @return
   */
  int getWebsocketInputBufferSize();

  /**
   * Websocket connection async write timeout.
   * @since 0.10
   * @return
   */
  int getWebsocketAsyncWriteTimeout();

  /**
   * Websocket connection idle timeout.
   * @since 0.10
   * @return
   */
  int getWebsocketIdleTimeout();

  boolean isMetricsEnabled();

  boolean isJmxMetricsReportingEnabled();

  boolean isGraphiteMetricsReportingEnabled();

  String getGraphiteHost();

  int getGraphitePort();

  int getGraphiteReportingFrequency();

  /**
   * List of MIME Type to be compressed.
   * @since 0.12
   */
  List<String> getMimeTypesToCompress();

  /**
   * Enable cookie scoping to gateway path
   *
   * @since 0.13
   */
  boolean isCookieScopingToPathEnabled();

  /**
   * Configured name of the HTTP Header that is expected
   * to be set by a proxy in front of the gateway.
   * @return
   */
  String getHeaderNameForRemoteAddress();

  /**
   * Configured Algorithm name to be used by the CryptoService
   * and MasterService implementations
   * @return
   */
  String getAlgorithm();

  /**
   * Configured Algorithm name to be used by the CryptoService
   * for password based encryption
   * @return
   */
  String getPBEAlgorithm();

  /**
   * Configured Transformation name to be used by the CryptoService
   * and MasterService implementations
   * @return
   */
  String getTransformation();

  /**
   * Configured SaltSize to be used by the CryptoService
   * and MasterService implementations
   * @return
   */
  String getSaltSize();

  /**
   * Configured IterationCount to be used by the CryptoService
   * and MasterService implementations
   * @return
   */
  String getIterationCount();

  /**
   * Configured KeyLength to be used by the CryptoService
   * and MasterService implementations
   * @return
   */
  String getKeyLength();

  /**
   * Map of Topology names and their ports.
   *
   * @return
   */
  Map<String, Integer> getGatewayPortMappings();

  /**
   * Is the Port Mapping feature on
   * @return
   */
  boolean isGatewayPortMappingEnabled();

  /**
   * Is the Server header suppressed
   * @return
   */
  boolean isGatewayServerHeaderEnabled();

  /**
   * Determine the default address for discovering service endpoint details.
   *
   * @return A valid discovery source address, or null (because this property is optional).
   */
  String getDefaultDiscoveryAddress();

  /**
   * Determine the default target cluster for discovering service endpoint details.
   *
   * @return A valid cluster name, or null (because this property is optional).
   */
  String getDefaultDiscoveryCluster();

  /**
   *
   * @param type The type of cluster configuration monitor for which the interval should be returned.
   *
   * @return The polling interval configuration value, or -1 if it has not been configured.
   */
  int getClusterMonitorPollingInterval(String type);
  
  /**
   *
   * @param type The type of cluster configuration monitor for which the interval should be returned.
   *
   * @return The enabled status of the specified type of cluster configuration monitor.
   */
  boolean isClusterMonitorEnabled(String type);
  
  /**
   * @return The list of the names of any remote registry configurations defined herein.
   */
  List<String> getRemoteRegistryConfigurationNames();

  /**
   *
   * @param name The name of the remote registry configuration
   *
   * @return The configuration associated with the specified name.
   */
  String getRemoteRegistryConfiguration(String name);

  /**
   *
   * @return The name of a remote configuration registry client
   */
  String getRemoteConfigurationMonitorClientName();

  /**
   * When new remote registry entries must be created, or new ACLs applied to existing entries, this method indicates
   * whether unauthenticated connections should be given read access to those entries.
   *
   * @return true, if unauthenticated clients should be allowed to access remote registry entries.
   */
  boolean allowUnauthenticatedRemoteRegistryReadAccess();

  /**
   * Returns whether the Remote Alias Service is enabled or not.
   *
   * This value also depends on whether the remote configuration registry is enabled or not.
   * If it is enabled, then this option takes effect, else this option has no effect.
   *
   * @return true, if the remote alias service is enabled; otherwise, false;
   */
  boolean isRemoteAliasServiceEnabled();

  /**
   * Get the list of those topology names which should be treated as read-only, regardless of their actual read-write
   * status.
   *
   * @return A list of the names of those topologies which should be treated as read-only.
   */
  List<String> getReadOnlyOverrideTopologyNames();

  /**
   * Get the comma separated list of group names that represent Knox Admin users
   * @return
   */
  String getKnoxAdminGroups();

  /**
   * Get the comma separated list of user names that represent Knox Admin users
   * @return
   */
  String getKnoxAdminUsers();

  /**
   * Custom header name to be used to pass the authenticated principal
   * via dispatch
   * @since 1.1.0
   * @return
   */
  String getFederationHeaderName();

  /**
   * Get the list of topology names that should be redeployed on restart.
   * manager and admin are default topologies as they may depend on gateway-site.xml
   * configuration for deployment time config. 
   * @return
   */
  List<String> getAutoDeployTopologyNames();

  /*
   * Get the semicolon-delimited set of regular expressions defining to which hosts Knox will permit requests to be
   * dispatched.
   *
   * @return The whitelist, which will be null if none is configured (in which case, requests to any host are permitted).
   */
  String getDispatchWhitelist();

  /**
   * Get the set of service roles to which the dispatch whitelist will be applied.
   *
   * @return The service roles, or an empty list if none are configured.
   */
  List<String> getDispatchWhitelistServices();

  /**
   * Returns true when strict topology validation is enabled,
   * in which case if topology validation fails Knox will throw
   * a runtime exception. If false and topology validation fails
   * Knox will log an ERROR and move on.
   *
   * @since 1.1.0
   * @return
   */
  boolean isTopologyValidationEnabled();


}

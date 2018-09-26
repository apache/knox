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
package org.apache.knox.gateway.config.impl;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The configuration for the Gateway.
 *
 * The Gateway configuration variables are described in gateway-default.xml
 *
 * The Gateway specific configuration is split into two layers:
 *
 * 1. gateway-default.xml - All the configuration variables that the
 *    Gateway needs.  These are the defaults that ship with the app
 *    and should only be changed by the app developers.
 *
 * 2. gateway-site.xml - The (possibly empty) configuration that the
 *    system administrator can set variables for their Hadoop cluster.
 *
 * To find the gateway configuration files the following process is used.
 * First, if the GATEWAY_HOME system property contains a valid directory name,
 * an attempt will be made to read the configuration files from that directory.
 * Second, if the GATEWAY_HOME environment variable contains a valid directory name,
 * an attempt will be made to read the configuration files from that directory.
 * Third, an attempt will be made to load the configuration files from the directory
 * specified via the "user.dir" system property.
 * Fourth, an attempt will be made to load the configuration files from the classpath.
 * Last, defaults will be used for all values will be used.
 *
 * If GATEWAY_HOME isn't set via either the system property or environment variable then
 * a value for this will be defaulted.  The default selected will be the directory that
 * contained the last loaded configuration file that was not contained in a JAR.  If
 * no such configuration file is loaded the value of the "user.dir" system property will be used
 * as the value of GATEWAY_HOME.  This is important to consider for any relative file names as they
 * will be resolved relative to the value of GATEWAY_HOME.  One such relative value is the
 * name of the directory containing cluster topologies.  This value default to "clusters".
 */
public class GatewayConfigImpl extends Configuration implements GatewayConfig {

  private static final String GATEWAY_DEFAULT_TOPOLOGY_NAME_PARAM = "default.app.topology.name";
  private static final String GATEWAY_DEFAULT_TOPOLOGY_NAME = null;

  private static final GatewayMessages log = MessagesFactory.get( GatewayMessages.class );

  private static final String GATEWAY_CONFIG_DIR_PREFIX = "conf";

  private static final String GATEWAY_CONFIG_FILE_PREFIX = "gateway";

  private static final String DEFAULT_STACKS_SERVICES_DIR = "services";

  private static final String DEFAULT_APPLICATIONS_DIR = "applications";

  public static final String[] GATEWAY_CONFIG_FILENAMES = {
      GATEWAY_CONFIG_DIR_PREFIX + "/" + GATEWAY_CONFIG_FILE_PREFIX + "-default.xml",
      GATEWAY_CONFIG_DIR_PREFIX + "/" + GATEWAY_CONFIG_FILE_PREFIX + "-site.xml"
  };

//  private static final String[] HADOOP_CONF_FILENAMES = {
//      "core-default.xml",
//      "core-site.xml"
////      "hdfs-default.xml",
////      "hdfs-site.xml",
////      "mapred-default.xml",
////      "mapred-site.xml"
//  };

//  private static final String[] HADOOP_PREFIX_VARS = {
//      "HADOOP_PREFIX",
//      "HADOOP_HOME"
//  };

  public static final String HTTP_HOST = GATEWAY_CONFIG_FILE_PREFIX + ".host";
  public static final String HTTP_PORT = GATEWAY_CONFIG_FILE_PREFIX + ".port";
  public static final String HTTP_PATH = GATEWAY_CONFIG_FILE_PREFIX + ".path";
  public static final String DEPLOYMENT_DIR = GATEWAY_CONFIG_FILE_PREFIX + ".deployment.dir";
  public static final String SECURITY_DIR = GATEWAY_CONFIG_FILE_PREFIX + ".security.dir";
  public static final String DATA_DIR = GATEWAY_CONFIG_FILE_PREFIX + ".data.dir";
  public static final String STACKS_SERVICES_DIR = GATEWAY_CONFIG_FILE_PREFIX + ".services.dir";
  public static final String GLOBAL_RULES_SERVICES = GATEWAY_CONFIG_FILE_PREFIX + ".global.rules.services";
  public static final String APPLICATIONS_DIR = GATEWAY_CONFIG_FILE_PREFIX + ".applications.dir";
  public static final String HADOOP_CONF_DIR = GATEWAY_CONFIG_FILE_PREFIX + ".hadoop.conf.dir";
  public static final String FRONTEND_URL = GATEWAY_CONFIG_FILE_PREFIX + ".frontend.url";
  private static final String TRUST_ALL_CERTS = GATEWAY_CONFIG_FILE_PREFIX + ".trust.all.certs";
  private static final String CLIENT_AUTH_NEEDED = GATEWAY_CONFIG_FILE_PREFIX + ".client.auth.needed";
  private static final String CLIENT_AUTH_WANTED = GATEWAY_CONFIG_FILE_PREFIX + ".client.auth.wanted";
  private static final String TRUSTSTORE_PATH = GATEWAY_CONFIG_FILE_PREFIX + ".truststore.path";
  private static final String TRUSTSTORE_TYPE = GATEWAY_CONFIG_FILE_PREFIX + ".truststore.type";
  private static final String KEYSTORE_TYPE = GATEWAY_CONFIG_FILE_PREFIX + ".keystore.type";
  private static final String XFORWARDED_ENABLED = GATEWAY_CONFIG_FILE_PREFIX + ".xforwarded.enabled";
  private static final String EPHEMERAL_DH_KEY_SIZE = GATEWAY_CONFIG_FILE_PREFIX + ".jdk.tls.ephemeralDHKeySize";
  private static final String HTTP_CLIENT_MAX_CONNECTION = GATEWAY_CONFIG_FILE_PREFIX + ".httpclient.maxConnections";
  private static final String HTTP_CLIENT_CONNECTION_TIMEOUT = GATEWAY_CONFIG_FILE_PREFIX + ".httpclient.connectionTimeout";
  private static final String HTTP_CLIENT_SOCKET_TIMEOUT = GATEWAY_CONFIG_FILE_PREFIX + ".httpclient.socketTimeout";
  private static final String THREAD_POOL_MAX = GATEWAY_CONFIG_FILE_PREFIX + ".threadpool.max";
  public static final String HTTP_SERVER_REQUEST_BUFFER = GATEWAY_CONFIG_FILE_PREFIX + ".httpserver.requestBuffer";
  public static final String HTTP_SERVER_REQUEST_HEADER_BUFFER = GATEWAY_CONFIG_FILE_PREFIX + ".httpserver.requestHeaderBuffer";
  public static final String HTTP_SERVER_RESPONSE_BUFFER = GATEWAY_CONFIG_FILE_PREFIX + ".httpserver.responseBuffer";
  public static final String HTTP_SERVER_RESPONSE_HEADER_BUFFER = GATEWAY_CONFIG_FILE_PREFIX + ".httpserver.responseHeaderBuffer";
  public static final String DEPLOYMENTS_BACKUP_VERSION_LIMIT = GATEWAY_CONFIG_FILE_PREFIX + ".deployment.backup.versionLimit";
  public static final String DEPLOYMENTS_BACKUP_AGE_LIMIT = GATEWAY_CONFIG_FILE_PREFIX + ".deployment.backup.ageLimit";
  public static final String METRICS_ENABLED = GATEWAY_CONFIG_FILE_PREFIX + ".metrics.enabled";
  public static final String JMX_METRICS_REPORTING_ENABLED = GATEWAY_CONFIG_FILE_PREFIX + ".jmx.metrics.reporting.enabled";
  public static final String GRAPHITE_METRICS_REPORTING_ENABLED = GATEWAY_CONFIG_FILE_PREFIX + ".graphite.metrics.reporting.enabled";
  public static final String GRAPHITE_METRICS_REPORTING_HOST = GATEWAY_CONFIG_FILE_PREFIX + ".graphite.metrics.reporting.host";
  public static final String GRAPHITE_METRICS_REPORTING_PORT = GATEWAY_CONFIG_FILE_PREFIX + ".graphite.metrics.reporting.port";
  public static final String GRAPHITE_METRICS_REPORTING_FREQUENCY = GATEWAY_CONFIG_FILE_PREFIX + ".graphite.metrics.reporting.frequency";
  public static final String GATEWAY_IDLE_TIMEOUT = GATEWAY_CONFIG_FILE_PREFIX + ".idle.timeout";
  public static final String REMOTE_IP_HEADER_NAME = GATEWAY_CONFIG_FILE_PREFIX + ".remote.ip.header.name";

  /* @since 0.10 Websocket config variables */
  public static final String WEBSOCKET_FEATURE_ENABLED = GATEWAY_CONFIG_FILE_PREFIX + ".websocket.feature.enabled";
  public static final String WEBSOCKET_MAX_TEXT_MESSAGE_SIZE = GATEWAY_CONFIG_FILE_PREFIX + ".websocket.max.text.size";
  public static final String WEBSOCKET_MAX_BINARY_MESSAGE_SIZE = GATEWAY_CONFIG_FILE_PREFIX + ".websocket.max.binary.size";
  public static final String WEBSOCKET_MAX_TEXT_MESSAGE_BUFFER_SIZE = GATEWAY_CONFIG_FILE_PREFIX + ".websocket.max.text.buffer.size";
  public static final String WEBSOCKET_MAX_BINARY_MESSAGE_BUFFER_SIZE = GATEWAY_CONFIG_FILE_PREFIX + ".websocket.max.binary.buffer.size";
  public static final String WEBSOCKET_INPUT_BUFFER_SIZE = GATEWAY_CONFIG_FILE_PREFIX + ".websocket.input.buffer.size";
  public static final String WEBSOCKET_ASYNC_WRITE_TIMEOUT = GATEWAY_CONFIG_FILE_PREFIX + ".websocket.async.write.timeout";
  public static final String WEBSOCKET_IDLE_TIMEOUT = GATEWAY_CONFIG_FILE_PREFIX + ".websocket.idle.timeout";

  /**
   * Properties for for gateway port mapping feature
   */
  public static final String GATEWAY_PORT_MAPPING_PREFIX = GATEWAY_CONFIG_FILE_PREFIX + ".port.mapping.";
  public static final String GATEWAY_PORT_MAPPING_REGEX = GATEWAY_CONFIG_FILE_PREFIX + "\\.port\\.mapping\\..*";
  public static final String GATEWAY_PORT_MAPPING_ENABLED = GATEWAY_PORT_MAPPING_PREFIX + "enabled";

  /**
   * Comma-separated list of MIME Types to be compressed by Knox on the way out.
   *
   * @since 0.12
   */
  public static final String MIME_TYPES_TO_COMPRESS = GATEWAY_CONFIG_FILE_PREFIX + ".gzip.compress.mime.types";

  public static final String CLUSTER_CONFIG_MONITOR_PREFIX = GATEWAY_CONFIG_FILE_PREFIX + ".cluster.config.monitor.";
  public static final String CLUSTER_CONFIG_MONITOR_INTERVAL_SUFFIX = ".interval";
  public static final String CLUSTER_CONFIG_MONITOR_ENABLED_SUFFIX = ".enabled";


  // These config property names are not inline with the convention of using the
  // GATEWAY_CONFIG_FILE_PREFIX as is done by those above. These are left for
  // backward compatibility. 
  // LET'S NOT CONTINUE THIS PATTERN BUT LEAVE THEM FOR NOW.
  private static final String SSL_ENABLED = "ssl.enabled";
  private static final String SSL_EXCLUDE_PROTOCOLS = "ssl.exclude.protocols";
  private static final String SSL_INCLUDE_CIPHERS = "ssl.include.ciphers";
  private static final String SSL_EXCLUDE_CIPHERS = "ssl.exclude.ciphers";
  // END BACKWARD COMPATIBLE BLOCK
  
  public static final String DEFAULT_HTTP_PORT = "8888";
  public static final String DEFAULT_HTTP_PATH = "gateway";
  public static final String DEFAULT_DEPLOYMENT_DIR = "deployments";
  public static final String DEFAULT_SECURITY_DIR = "security";
  public static final String DEFAULT_DATA_DIR = "data";
  private static final String PROVIDERCONFIG_DIR_NAME = "shared-providers";
  private static final String DESCRIPTORS_DIR_NAME = "descriptors";
  public static final String REMOTE_ALIAS_SERVICE_ENABLED = GATEWAY_CONFIG_FILE_PREFIX + ".remote.alias.service.enabled";
  public static final String STRICT_TOPOLOGY_VALIDATION = GATEWAY_CONFIG_FILE_PREFIX + ".strict.topology.validation";

  /**
   * Comma-separated list of topology names, which should be forcibly treated as read-only.
   * @since 1.1.0
   */
  public static final String READ_ONLY_OVERRIDE_TOPOLOGIES =
                                                    GATEWAY_CONFIG_FILE_PREFIX + ".read.only.override.topologies";

  /* Websocket defaults */
  public static final boolean DEFAULT_WEBSOCKET_FEATURE_ENABLED = false;
  public static final int DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE = Integer.MAX_VALUE;;
  public static final int DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_SIZE = Integer.MAX_VALUE;;
  public static final int DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_BUFFER_SIZE = 32768;
  public static final int DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_BUFFER_SIZE = 32768;
  public static final int DEFAULT_WEBSOCKET_INPUT_BUFFER_SIZE = 4096;
  public static final int DEFAULT_WEBSOCKET_ASYNC_WRITE_TIMEOUT = 60000;
  public static final int DEFAULT_WEBSOCKET_IDLE_TIMEOUT = 300000;

  public static final boolean DEFAULT_GATEWAY_PORT_MAPPING_ENABLED = true;
  public static final boolean DEFAULT_REMOTE_ALIAS_SERVICE_ENABLED = true;
  public static final boolean DEFAULT_STRICT_TOPOLOGY_VALIDATION = false;

  /**
   * Default list of MIME Type to be compressed.
   * @since 0.12
   */
  public static final String DEFAULT_MIME_TYPES_TO_COMPRESS =
        "text/html, text/plain, text/xml, text/css, application/javascript, application/x-javascript, text/javascript";

  public static final String COOKIE_SCOPING_ENABLED = GATEWAY_CONFIG_FILE_PREFIX + ".scope.cookies.feature.enabled";
  public static final boolean DEFAULT_COOKIE_SCOPING_FEATURE_ENABLED = false;
  private static final String CRYPTO_ALGORITHM = GATEWAY_CONFIG_FILE_PREFIX + ".crypto.algorithm";
  private static final String CRYPTO_PBE_ALGORITHM = GATEWAY_CONFIG_FILE_PREFIX + ".crypto.pbe.algorithm";
  private static final String CRYPTO_TRANSFORMATION = GATEWAY_CONFIG_FILE_PREFIX + ".crypto.transformation";
  private static final String CRYPTO_SALTSIZE = GATEWAY_CONFIG_FILE_PREFIX + ".crypto.salt.size";
  private static final String CRYPTO_ITERATION_COUNT = GATEWAY_CONFIG_FILE_PREFIX + ".crypto.iteration.count";
  private static final String CRYPTO_KEY_LENGTH = GATEWAY_CONFIG_FILE_PREFIX + ".crypto.key.length";
  public static final String SERVER_HEADER_ENABLED = GATEWAY_CONFIG_FILE_PREFIX + ".server.header.enabled";

  /* @since 0.15 Remote configuration monitoring */
  static final String CONFIG_REGISTRY_PREFIX = GATEWAY_CONFIG_FILE_PREFIX + ".remote.config.registry";
  static final String REMOTE_CONFIG_MONITOR_CLIENT_NAME = GATEWAY_CONFIG_FILE_PREFIX + ".remote.config.monitor.client";
  static final String REMOTE_CONFIG_MONITOR_CLIENT_ALLOW_READ_ACCESS =
                                                  REMOTE_CONFIG_MONITOR_CLIENT_NAME + ".allowUnauthenticatedReadAccess";

  /* @since 1.1.0 Default discovery configuration */
  static final String DEFAULT_DISCOVERY_ADDRESS = GATEWAY_CONFIG_FILE_PREFIX + ".discovery.default.address";
  static final String DEFAULT_DISCOVERY_CLUSTER = GATEWAY_CONFIG_FILE_PREFIX + ".discovery.default.cluster";

  static final String KNOX_ADMIN_GROUPS = GATEWAY_CONFIG_FILE_PREFIX + ".knox.admin.groups";
  static final String KNOX_ADMIN_USERS = GATEWAY_CONFIG_FILE_PREFIX + ".knox.admin.users";

  /* property that specifies custom header name to be added to outgoing federated request */
  static final String CUSTOM_FEDERATION_HEADER_NAME = GATEWAY_CONFIG_FILE_PREFIX + ".custom.federation.header.name";
  /* Default federated header name, see HeaderPreAuthFederationFilter.headerName */
  static final String DEFAULT_FEDERATION_HEADER_NAME = "SM_USER";
  static final String AUTO_DEPLOY_TOPOLOGIES = GATEWAY_CONFIG_FILE_PREFIX + ".auto.deploy.topologies";
  static final String DEFAULT_AUTO_DEPLOY_TOPOLOGIES = "manager,admin";

  static final String DISPATCH_HOST_WHITELIST          = GATEWAY_CONFIG_FILE_PREFIX + ".dispatch.whitelist";
  static final String DISPATCH_HOST_WHITELIST_SERVICES = DISPATCH_HOST_WHITELIST + ".services";

  private static List<String> DEFAULT_GLOBAL_RULES_SERVICES;


  public GatewayConfigImpl() {
    init();
  }

  private String getVar( String variableName, String defaultValue ) {
    String value = get( variableName );
    if( value == null ) {
      value = System.getProperty( variableName );
    }
    if( value == null ) {
      value = System.getenv( variableName );
    }
    if( value == null ) {
      value = defaultValue;
    }
    return value;
  }

  private String getGatewayHomeDir() {
    String home = get(
        GATEWAY_HOME_VAR,
        System.getProperty(
            GATEWAY_HOME_VAR,
            System.getenv( GATEWAY_HOME_VAR ) ) );
    return home;
  }

  private void setGatewayHomeDir( String dir ) {
    set( GATEWAY_HOME_VAR, dir );
  }

  @Override
  public String getGatewayConfDir() {
    String value = getVar( GATEWAY_CONF_HOME_VAR, getGatewayHomeDir() + File.separator + "conf"  );
    return FilenameUtils.normalize(value);
  }

  @Override
  public String getGatewayDataDir() {
    String systemValue =
        System.getProperty(GATEWAY_DATA_HOME_VAR, System.getenv(GATEWAY_DATA_HOME_VAR));
    String dataDir = null;
    if (systemValue != null) {
      dataDir = systemValue;
    } else {
      dataDir = get(DATA_DIR, getGatewayHomeDir() + File.separator + DEFAULT_DATA_DIR);
    }
    return FilenameUtils.normalize(dataDir);
  }

  @Override
  public String getGatewayServicesDir() {
    return get(STACKS_SERVICES_DIR, getGatewayDataDir() + File.separator + DEFAULT_STACKS_SERVICES_DIR);
  }

  @Override
  public String getGatewayApplicationsDir() {
    return get(APPLICATIONS_DIR, getGatewayDataDir() + File.separator + DEFAULT_APPLICATIONS_DIR);
  }

  @Override
  public String getHadoopConfDir() {
    return get( HADOOP_CONF_DIR );
  }

  private void init() {
    // Load environment variables.
    for( Map.Entry<String, String> e : System.getenv().entrySet() ) {
      set( "env." + e.getKey(), e.getValue() );
    }
    // Load system properties.
    for( Map.Entry<Object, Object> p : System.getProperties().entrySet() ) {
      set( "sys." + p.getKey().toString(), p.getValue().toString() );
    }

    URL lastFileUrl = null;
    for( String fileName : GATEWAY_CONFIG_FILENAMES ) {
      lastFileUrl = loadConfig( fileName, lastFileUrl );
    }
    //set default services list
    setDefaultGlobalRulesServices();

    initGatewayHomeDir( lastFileUrl );

    // log whether the scoping cookies to the gateway.path feature is enabled
    log.cookieScopingFeatureEnabled(isCookieScopingToPathEnabled());
  }

  private void setDefaultGlobalRulesServices() {
    DEFAULT_GLOBAL_RULES_SERVICES = new ArrayList<>();
    DEFAULT_GLOBAL_RULES_SERVICES.add("NAMENODE");
    DEFAULT_GLOBAL_RULES_SERVICES.add("JOBTRACKER");
    DEFAULT_GLOBAL_RULES_SERVICES.add("WEBHDFS");
    DEFAULT_GLOBAL_RULES_SERVICES.add("WEBHCAT");
    DEFAULT_GLOBAL_RULES_SERVICES.add("OOZIE");
    DEFAULT_GLOBAL_RULES_SERVICES.add("WEBHBASE");
    DEFAULT_GLOBAL_RULES_SERVICES.add("HIVE");
    DEFAULT_GLOBAL_RULES_SERVICES.add("RESOURCEMANAGER");
  }

  private void initGatewayHomeDir( URL lastFileUrl ) {
    String home = System.getProperty( GATEWAY_HOME_VAR );
    if( home != null ) {
      set( GATEWAY_HOME_VAR, home );
      log.settingGatewayHomeDir( "system property", home );
      return;
    }
    home = System.getenv( GATEWAY_HOME_VAR );
    if( home != null ) {
      set( GATEWAY_HOME_VAR, home );
      log.settingGatewayHomeDir( "environment variable", home );
      return;
    }
    if( lastFileUrl != null ) {
      File file = new File( lastFileUrl.getFile() ).getAbsoluteFile();
      File dir = file.getParentFile().getParentFile(); // Move up two levels to get to parent of conf.
      if( dir.exists() && dir.canRead() )
        home = dir.getAbsolutePath();
      set( GATEWAY_HOME_VAR, home );
      log.settingGatewayHomeDir( "configuration file location", home );
      return;
    }
    home = System.getProperty( "user.dir" );
    if( home != null ) {
      set( GATEWAY_HOME_VAR, home );
      log.settingGatewayHomeDir( "user.dir system property", home );
      return;
    }
  }

  // 1. GATEWAY_HOME system property
  // 2. GATEWAY_HOME environment variable
  // 3. user.dir system property
  // 4. class path
  private URL loadConfig( String fileName, URL lastFileUrl ) {
    lastFileUrl = loadConfigFile( System.getProperty( GATEWAY_HOME_VAR ), fileName );
    if( lastFileUrl == null ) {
      lastFileUrl = loadConfigFile( System.getenv( GATEWAY_HOME_VAR ), fileName );
    }
    if( lastFileUrl == null ) {
      lastFileUrl = loadConfigFile( System.getProperty( "user.dir" ), fileName );
    }
    if( lastFileUrl == null ) {
      lastFileUrl = loadConfigResource( fileName );
    }
    if( lastFileUrl != null && !"file".equals( lastFileUrl.getProtocol() ) ) {
      lastFileUrl = null;
    }
    return lastFileUrl;
  }

  private URL loadConfigFile( String dir, String file ) {
    URL url = null;
    if( dir != null ) {
      File f = new File( dir, file );
      if( f.exists() ) {
        String path = f.getAbsolutePath();
        try {
          url = f.toURI().toURL();
          addResource( new Path( path ) );
          log.loadingConfigurationFile( path );
        } catch ( MalformedURLException e ) {
          log.failedToLoadConfig( path, e );
        }
      }
    }
    return url;
  }

  private URL loadConfigResource( String file ) {
    URL url = getResource( file );
    if( url != null ) {
      log.loadingConfigurationResource( url.toExternalForm() );
      addResource( url );
    }
    return url;
  }

  @Override
  public String getGatewayHost() {
    String host = get( HTTP_HOST, "0.0.0.0" );
    return host;
  }

  @Override
  public int getGatewayPort() {
    return Integer.parseInt( get( HTTP_PORT, DEFAULT_HTTP_PORT ) );
  }

  @Override
  public String getGatewayPath() {
    return get( HTTP_PATH, DEFAULT_HTTP_PATH );
  }

  @Override
  public String getGatewayProvidersConfigDir() {
    return getGatewayConfDir() + File.separator + PROVIDERCONFIG_DIR_NAME;
  }

  @Override
  public String getGatewayDescriptorsDir() {
    return getGatewayConfDir() + File.separator + DESCRIPTORS_DIR_NAME;
  }

  @Override
  public String getGatewayTopologyDir() {
    return getGatewayConfDir() + File.separator + "topologies";
  }

  @Override
  public String getGatewayDeploymentDir() {
    return get(DEPLOYMENT_DIR, getGatewayDataDir() + File.separator + DEFAULT_DEPLOYMENT_DIR);
  }

  @Override
  public String getGatewaySecurityDir() {
    return get(SECURITY_DIR, getGatewayDataDir() + File.separator + DEFAULT_SECURITY_DIR);
  }

  @Override
  public InetSocketAddress getGatewayAddress() throws UnknownHostException {
    String host = getGatewayHost();
    int port = getGatewayPort();
    InetSocketAddress address = new InetSocketAddress( host, port );
    return address;
  }

  @Override
  public boolean isSSLEnabled() {
    String enabled = get( SSL_ENABLED, "true" );
    
    return "true".equals(enabled);
  }

  @Override
  public boolean isHadoopKerberosSecured() {
    String hadoopKerberosSecured = get( HADOOP_KERBEROS_SECURED, "false" );
    return "true".equals(hadoopKerberosSecured);
  }

  @Override
  public String getKerberosConfig() {
    return get( KRB5_CONFIG ) ;
  }

  @Override
  public boolean isKerberosDebugEnabled() {
    String kerberosDebugEnabled = get( KRB5_DEBUG, "false" );
    return "true".equals(kerberosDebugEnabled);
  }
  
  @Override
  public String getKerberosLoginConfig() {
    return get( KRB5_LOGIN_CONFIG );
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getDefaultTopologyName()
   */
  @Override
  public String getDefaultTopologyName() {
    String name = get(GATEWAY_DEFAULT_TOPOLOGY_NAME_PARAM);
    return name != null ? name : GATEWAY_DEFAULT_TOPOLOGY_NAME;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getDefaultAppRedirectPath()
   */
  @Override
  public String getDefaultAppRedirectPath() {
    String defTopo = getDefaultTopologyName();
    if( defTopo == null ) {
      return null;
    } else {
      return "/" + getGatewayPath() + "/" + defTopo;
    }
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getFrontendUrl()
   */
  @Override
  public String getFrontendUrl() {
    String url = get( FRONTEND_URL, null );
    return url;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getExcludedSSLProtocols()
   */
  @Override
  public List<String> getExcludedSSLProtocols() {
    List<String> protocols = null;
    String value = get(SSL_EXCLUDE_PROTOCOLS);
    if (!"none".equals(value)) {
      protocols = Arrays.asList(value.split("\\s*,\\s*"));
    }
    return protocols;
  }

  @Override
  public List<String> getIncludedSSLCiphers() {
    List<String> list = null;
    String value = get(SSL_INCLUDE_CIPHERS);
    if (value != null && !value.isEmpty() && !"none".equalsIgnoreCase(value.trim())) {
      list = Arrays.asList(value.trim().split("\\s*,\\s*"));
    }
    return list;
  }

  @Override
  public List<String> getExcludedSSLCiphers() {
    List<String> list = null;
    String value = get(SSL_EXCLUDE_CIPHERS);
    if (value != null && !value.isEmpty() && !"none".equalsIgnoreCase(value.trim())) {
      list = Arrays.asList(value.trim().split("\\s*,\\s*"));
    }
    return list;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#isClientAuthNeeded()
   */
  @Override
  public boolean isClientAuthNeeded() {
    String clientAuthNeeded = get( CLIENT_AUTH_NEEDED, "false" );
    return "true".equals(clientAuthNeeded);
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.config.GatewayConfig#isClientAuthWanted()
   */
  @Override
  public boolean isClientAuthWanted() {
    String clientAuthWanted = get( CLIENT_AUTH_WANTED, "false" );
    return "true".equals(clientAuthWanted);
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getTruststorePath()
   */
  @Override
  public String getTruststorePath() {
    return get( TRUSTSTORE_PATH, null);
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getTrustAllCerts()
   */
  @Override
  public boolean getTrustAllCerts() {
    String trustAllCerts = get( TRUST_ALL_CERTS, "false" );
    return "true".equals(trustAllCerts);
  }
  
  /* (non-Javadoc)
   * @see GatewayConfig#getTruststorePath()
   */
  @Override
  public String getTruststoreType() {
    return get( TRUSTSTORE_TYPE, "JKS");
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getTruststorePath()
   */
  @Override
  public String getKeystoreType() {
    return get( KEYSTORE_TYPE, "JKS");
  }

  @Override
  public boolean isXForwardedEnabled() {
    String xForwardedEnabled = get( XFORWARDED_ENABLED, "true" );
    return "true".equals(xForwardedEnabled);
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getEphemeralDHKeySize()
   */
  @Override
  public String getEphemeralDHKeySize() {
    return get( EPHEMERAL_DH_KEY_SIZE, "2048");
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getHttpClientMaxConnections()
   */
  @Override
  public int getHttpClientMaxConnections() {
    return getInt( HTTP_CLIENT_MAX_CONNECTION, 32 );
  }

  @Override
  public int getHttpClientConnectionTimeout() {
    int t = -1;
    String s = get( HTTP_CLIENT_CONNECTION_TIMEOUT, String.valueOf(TimeUnit.SECONDS.toMillis(20)));
    if ( s != null ) {
      try {
        t = (int)parseNetworkTimeout( s );
      } catch ( Exception e ) {
        // Ignore it and use the default.
      }
    }
    return t;
  }

  @Override
  public int getHttpClientSocketTimeout() {
    int t = -1;
    String s = get( HTTP_CLIENT_SOCKET_TIMEOUT, String.valueOf(TimeUnit.SECONDS.toMillis(20)) );
    if ( s != null ) {
      try {
        t = (int)parseNetworkTimeout( s );
      } catch ( Exception e ) {
        // Ignore it and use the default.
      }
    }
    return t;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#getThreadPoolMax()
   */
  @Override
  public int getThreadPoolMax() {
    int i = getInt( THREAD_POOL_MAX, 254 );
    // Testing has shown that a value lower than 5 prevents Jetty from servicing request.
    if( i < 5 ) {
      i = 5;
    }
    return i;
  }

  @Override
  public int getHttpServerRequestBuffer() {
    int i = getInt( HTTP_SERVER_REQUEST_BUFFER, 16 * 1024 );
    return i;
  }

  @Override
  public int getHttpServerRequestHeaderBuffer() {
    int i = getInt( HTTP_SERVER_REQUEST_HEADER_BUFFER, 8 * 1024 );
    return i;
  }

  @Override
  public int getHttpServerResponseBuffer() {
    int i = getInt( HTTP_SERVER_RESPONSE_BUFFER, 32 * 1024 );
    return i;
  }

  @Override
  public int getHttpServerResponseHeaderBuffer() {
    int i = getInt( HTTP_SERVER_RESPONSE_HEADER_BUFFER, 8 * 1024 );
    return i;
  }

  @Override
  public int getGatewayDeploymentsBackupVersionLimit() {
    int i = getInt( DEPLOYMENTS_BACKUP_VERSION_LIMIT, 5 );
    if( i < 0 ) {
      i = -1;
    }
    return i;
  }

  @Override
  public long getGatewayIdleTimeout() {
    return getLong(GATEWAY_IDLE_TIMEOUT, 300000l);
  }

  @Override
  public long getGatewayDeploymentsBackupAgeLimit() {
    PeriodFormatter f = new PeriodFormatterBuilder().appendDays().toFormatter();
    String s = get( DEPLOYMENTS_BACKUP_AGE_LIMIT, "-1" );
    long d;
    try {
      Period p = Period.parse( s, f );
      d = p.toStandardDuration().getMillis();
      if( d < 0 ) {
        d = -1;
      }
    } catch( Exception e ) {
      d = -1;
    }
    return d;
  }

  @Override
  public String getSigningKeystoreName() {
    return get(SIGNING_KEYSTORE_NAME);
  }

  @Override
  public String getSigningKeyAlias() {
    return get(SIGNING_KEY_ALIAS);
  }

  @Override
  public List<String> getGlobalRulesServices() {
    String value = get( GLOBAL_RULES_SERVICES );
    if ( value != null && !value.isEmpty() && !"none".equalsIgnoreCase(value.trim()) ) {
      return Arrays.asList( value.trim().split("\\s*,\\s*") );
    }
    return DEFAULT_GLOBAL_RULES_SERVICES;
  }

  @Override
  public boolean isMetricsEnabled() {
    String metricsEnabled = get( METRICS_ENABLED, "false" );
    return "true".equals(metricsEnabled);
  }

  @Override
  public boolean isJmxMetricsReportingEnabled() {
    String enabled = get( JMX_METRICS_REPORTING_ENABLED, "false" );
    return "true".equals(enabled);
  }

  @Override
  public boolean isGraphiteMetricsReportingEnabled() {
    String enabled = get( GRAPHITE_METRICS_REPORTING_ENABLED, "false" );
    return "true".equals(enabled);
  }

  @Override
  public String getGraphiteHost() {
    String host = get( GRAPHITE_METRICS_REPORTING_HOST, "localhost" );
    return host;
  }

  @Override
  public int getGraphitePort() {
    int i = getInt( GRAPHITE_METRICS_REPORTING_PORT, 32772 );
    return i;
  }

  @Override
  public int getGraphiteReportingFrequency() {
    int i = getInt( GRAPHITE_METRICS_REPORTING_FREQUENCY, 1 );
    return i;
  }

  /* (non-Javadoc)
   * @see GatewayConfig#isWebsocketEnabled()
   */
  @Override
  public boolean isWebsocketEnabled() {
    final String result = get( WEBSOCKET_FEATURE_ENABLED, Boolean.toString(DEFAULT_WEBSOCKET_FEATURE_ENABLED));
    return Boolean.parseBoolean(result);
  }

  /* (non-Javadoc)
   * @see GatewayConfig#websocketMaxTextMessageSize()
   */
  @Override
  public int getWebsocketMaxTextMessageSize() {
    return getInt( WEBSOCKET_MAX_TEXT_MESSAGE_SIZE, DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE);
  }

  /* (non-Javadoc)
   * @see GatewayConfig#websocketMaxBinaryMessageSize()
   */
  @Override
  public int getWebsocketMaxBinaryMessageSize() {
    return getInt( WEBSOCKET_MAX_BINARY_MESSAGE_SIZE, DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_SIZE);
  }

  /* (non-Javadoc)
   * @see GatewayConfig#websocketMaxTextMessageBufferSize()
   */
  @Override
  public int getWebsocketMaxTextMessageBufferSize() {
    return getInt( WEBSOCKET_MAX_TEXT_MESSAGE_BUFFER_SIZE, DEFAULT_WEBSOCKET_MAX_TEXT_MESSAGE_BUFFER_SIZE);
  }

  /* (non-Javadoc)
   * @see GatewayConfig#websocketMaxBinaryMessageBufferSize()
   */
  @Override
  public int getWebsocketMaxBinaryMessageBufferSize() {
    return getInt( WEBSOCKET_MAX_BINARY_MESSAGE_BUFFER_SIZE, DEFAULT_WEBSOCKET_MAX_BINARY_MESSAGE_BUFFER_SIZE);
  }

  /* (non-Javadoc)
   * @see GatewayConfig#websocketInputBufferSize()
   */
  @Override
  public int getWebsocketInputBufferSize() {
    return getInt( WEBSOCKET_INPUT_BUFFER_SIZE, DEFAULT_WEBSOCKET_INPUT_BUFFER_SIZE);
  }

  /* (non-Javadoc)
   * @see GatewayConfig#websocketAsyncWriteTimeout()
   */
  @Override
  public int getWebsocketAsyncWriteTimeout() {
    return getInt( WEBSOCKET_ASYNC_WRITE_TIMEOUT, DEFAULT_WEBSOCKET_ASYNC_WRITE_TIMEOUT);
  }

  /* (non-Javadoc)
   * @see GatewayConfig#websocketIdleTimeout()
   */
  @Override
  public int getWebsocketIdleTimeout() {
    return getInt( WEBSOCKET_IDLE_TIMEOUT, DEFAULT_WEBSOCKET_IDLE_TIMEOUT);
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * GatewayConfig#getMimeTypesToCompress()
   */
  @Override
  public List<String> getMimeTypesToCompress() {
    List<String> mimeTypes = null;
    String value = get(MIME_TYPES_TO_COMPRESS, DEFAULT_MIME_TYPES_TO_COMPRESS);
    if (value != null && !value.isEmpty()) {
      mimeTypes = Arrays.asList(value.trim().split("\\s*,\\s*"));
    }
    return mimeTypes;
  }

  /**
   * Map of Topology names and their ports.
   *
   * @return
   */
  @Override
  public Map<String, Integer> getGatewayPortMappings() {

    final Map<String, Integer> result = new ConcurrentHashMap<String, Integer>();
    final Map<String, String> properties = getValByRegex(GATEWAY_PORT_MAPPING_REGEX);

    // Convert port no. from string to int
    for(final Map.Entry<String, String> e : properties.entrySet()) {
      // ignore the GATEWAY_PORT_MAPPING_ENABLED property
      if(!e.getKey().equalsIgnoreCase(GATEWAY_PORT_MAPPING_ENABLED)) {
        // extract the topology name and use it as a key
        result.put(StringUtils.substringAfter(e.getKey(), GATEWAY_PORT_MAPPING_PREFIX), Integer.parseInt(e.getValue()) );
      }

    }

    return Collections.unmodifiableMap(result);
  }

  /**
   * Is the Port Mapping feature on ?
   *
   * @return
   */
  @Override
  public boolean isGatewayPortMappingEnabled() {
    final String result = get( GATEWAY_PORT_MAPPING_ENABLED, Boolean.toString(DEFAULT_GATEWAY_PORT_MAPPING_ENABLED));
    return Boolean.parseBoolean(result);
  }

  private static long parseNetworkTimeout(String s ) {
    PeriodFormatter f = new PeriodFormatterBuilder()
        .appendMinutes().appendSuffix("m"," min")
        .appendSeconds().appendSuffix("s"," sec")
        .appendMillis().toFormatter();
    Period p = Period.parse( s, f );
    return p.toStandardDuration().getMillis();
  }

  @Override
  public boolean isCookieScopingToPathEnabled() {
    final boolean result = Boolean.parseBoolean(get(COOKIE_SCOPING_ENABLED,
            Boolean.toString(DEFAULT_COOKIE_SCOPING_FEATURE_ENABLED)));
    return result;
  }

  @Override
  public String getHeaderNameForRemoteAddress() {
    String value = getVar(REMOTE_IP_HEADER_NAME, "X-Forwarded-For");
    return value;
  }

  @Override
  public String getAlgorithm() {
  return getVar(CRYPTO_ALGORITHM, null);
  }

  @Override
  public String getPBEAlgorithm() {
  return getVar(CRYPTO_PBE_ALGORITHM, null);
  }

  @Override
  public String getTransformation() {
  return getVar(CRYPTO_TRANSFORMATION, null);
  }

  @Override
  public String getSaltSize() {
  return getVar(CRYPTO_SALTSIZE, null);
  }

  @Override
  public String getIterationCount() {
  return getVar(CRYPTO_ITERATION_COUNT, null);
  }

  @Override
  public String getKeyLength() {
  return getVar(CRYPTO_KEY_LENGTH, null);
  }

  @Override
  public boolean isGatewayServerHeaderEnabled() {
    return Boolean.parseBoolean(getVar(SERVER_HEADER_ENABLED, "true"));
  }

  @Override
  public String getDefaultDiscoveryAddress() {
    return getVar(DEFAULT_DISCOVERY_ADDRESS, null);
  }

  @Override
  public String getDefaultDiscoveryCluster() {
    return getVar(DEFAULT_DISCOVERY_CLUSTER, null);
  }

  @Override
  public int getClusterMonitorPollingInterval(String type) {
    return getInt(CLUSTER_CONFIG_MONITOR_PREFIX + type.toLowerCase(Locale.ROOT) + CLUSTER_CONFIG_MONITOR_INTERVAL_SUFFIX, -1);
  }
  
  @Override
  public boolean isClusterMonitorEnabled(String type) {
    return getBoolean(CLUSTER_CONFIG_MONITOR_PREFIX + type.toLowerCase(Locale.ROOT) + CLUSTER_CONFIG_MONITOR_ENABLED_SUFFIX, true);
  }

  @Override
  public List<String> getRemoteRegistryConfigurationNames() {
    List<String> result = new ArrayList<>();

    // Iterate over all the properties in this configuration
    for (Map.Entry<String, String> entry : this) {
      String propertyName = entry.getKey();

      // Search for all the remote config registry properties
      if (propertyName.startsWith(CONFIG_REGISTRY_PREFIX)) {
        String registryName = propertyName.substring(CONFIG_REGISTRY_PREFIX.length() + 1);
        result.add(registryName);
      }
    }

    return result;
  }

  @Override
  public String getRemoteRegistryConfiguration(String name) {
    return get(CONFIG_REGISTRY_PREFIX + "." + name );
  }

  @Override
  public String getRemoteConfigurationMonitorClientName() {
    return get(REMOTE_CONFIG_MONITOR_CLIENT_NAME);
  }

  @Override
  public boolean allowUnauthenticatedRemoteRegistryReadAccess() {
    return Boolean.parseBoolean(get(REMOTE_CONFIG_MONITOR_CLIENT_ALLOW_READ_ACCESS, String.valueOf(false)));
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
    final String result = get( REMOTE_ALIAS_SERVICE_ENABLED, Boolean.toString(DEFAULT_REMOTE_ALIAS_SERVICE_ENABLED));
    return Boolean.parseBoolean(result);
  }

  @Override
  public List<String> getReadOnlyOverrideTopologyNames() {
    List<String> topologyNames = new ArrayList<>();

    String value = get(READ_ONLY_OVERRIDE_TOPOLOGIES);
    if (value != null && !value.isEmpty()) {
      topologyNames.addAll(Arrays.asList(value.trim().split("\\s*,\\s*")));
    }

    return topologyNames;
  }

  @Override
  public String getKnoxAdminGroups() {
    final String result = get(KNOX_ADMIN_GROUPS, null);
    return result;
  }

  @Override
  public String getKnoxAdminUsers() {
    final String result = get(KNOX_ADMIN_USERS, null);
    return result;
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
    return get(CUSTOM_FEDERATION_HEADER_NAME, DEFAULT_FEDERATION_HEADER_NAME);
  }

  @Override
  public List<String> getAutoDeployTopologyNames() {
    List<String> topologyNames = new ArrayList<>();

    String value = get(AUTO_DEPLOY_TOPOLOGIES);
    if (value == null) {
      value = DEFAULT_AUTO_DEPLOY_TOPOLOGIES;
    }
    topologyNames.addAll(Arrays.asList(value.trim().split("\\s*,\\s*")));

    return topologyNames;
  }

  public String getDispatchWhitelist() {
    return get(DISPATCH_HOST_WHITELIST);
  }

  @Override
  public List<String> getDispatchWhitelistServices() {
    List<String> result = new ArrayList<>();

    String serviceList = get(DISPATCH_HOST_WHITELIST_SERVICES);
    if (serviceList != null) {
      for (String service : Arrays.asList(serviceList.split(","))) {
        result.add(service.trim());
      }
    }

    return result;
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
    final String result = get(STRICT_TOPOLOGY_VALIDATION, Boolean.toString(DEFAULT_STRICT_TOPOLOGY_VALIDATION));
    return Boolean.parseBoolean(result);
  }

}

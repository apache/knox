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
package org.apache.hadoop.gateway.config.impl;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The configuration for the Gateway.
 *
 * The Gateway configuration variables are described in gateway-default.xml
 *
 * The Gateway specific configuration is split into two layers:
 *
 * 1. gateway-default.xml - All the configuration variables that the
 *    Gateway needs.  These are the defaults that ship with the app
 *    and should only be changed be the app developers.
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
  private static final String GATEWAY_DEFAULT_TOPOLOGY_NAME = "sandbox";

  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );

  private static final String GATEWAY_CONFIG_DIR_PREFIX = "conf";

  private static final String GATEWAY_CONFIG_FILE_PREFIX = "gateway";

  private static final String DEFAULT_STACKS_SERVICES_DIR = "services";

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
  public static final String HADOOP_CONF_DIR = GATEWAY_CONFIG_FILE_PREFIX + ".hadoop.conf.dir";
  public static final String FRONTEND_URL = GATEWAY_CONFIG_FILE_PREFIX + ".frontend.url";
  private static final String TRUST_ALL_CERTS = GATEWAY_CONFIG_FILE_PREFIX + ".trust.all.certs";
  private static final String CLIENT_AUTH_NEEDED = GATEWAY_CONFIG_FILE_PREFIX + ".client.auth.needed";
  private static final String TRUSTSTORE_PATH = GATEWAY_CONFIG_FILE_PREFIX + ".truststore.path";
  private static final String TRUSTSTORE_TYPE = GATEWAY_CONFIG_FILE_PREFIX + ".truststore.type";
  private static final String KEYSTORE_TYPE = GATEWAY_CONFIG_FILE_PREFIX + ".keystore.type";
  private static final String XFORWARDED_ENABLED = GATEWAY_CONFIG_FILE_PREFIX + ".xforwarded.enabled";
  private static final String EPHEMERAL_DH_KEY_SIZE = GATEWAY_CONFIG_FILE_PREFIX + ".jdk.tls.ephemeralDHKeySize";
  private static final String HTTP_CLIENT_MAX_CONNECTION = GATEWAY_CONFIG_FILE_PREFIX + ".httpclient.maxConnections";
  private static final String THREAD_POOL_MAX = GATEWAY_CONFIG_FILE_PREFIX + ".threadpool.max";
  public static final String HTTP_SERVER_REQUEST_BUFFER = GATEWAY_CONFIG_FILE_PREFIX + ".httpserver.requestBuffer";
  public static final String HTTP_SERVER_REQUEST_HEADER_BUFFER = GATEWAY_CONFIG_FILE_PREFIX + ".httpserver.requestHeaderBuffer";
  public static final String HTTP_SERVER_RESPONSE_BUFFER = GATEWAY_CONFIG_FILE_PREFIX + ".httpserver.responseBuffer";
  public static final String HTTP_SERVER_RESPONSE_HEADER_BUFFER = GATEWAY_CONFIG_FILE_PREFIX + ".httpserver.responseHeaderBuffer";

  // These config property names are not inline with the convention of using the
  // GATEWAY_CONFIG_FILE_PREFIX as is done by those above. These are left for
  // backward compatibility. 
  // LET'S NOT CONTINUE THIS PATTERN BUT LEAVE THEM FOR NOW.
  private static final String SSL_ENABLED = "ssl.enabled";
  private static final String SSL_EXCLUDE_PROTOCOLS = "ssl.exclude.protocols";
  // END BACKWARD COMPATIBLE BLOCK
  
  public static final String DEFAULT_HTTP_PORT = "8888";
  public static final String DEFAULT_HTTP_PATH = "gateway";
  public static final String DEFAULT_DEPLOYMENT_DIR = "deployments";
  public static final String DEFAULT_SECURITY_DIR = "security";
  public static final String DEFAULT_DATA_DIR = "data";
  

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
    return value;
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
    return dataDir;
  }

  @Override
  public String getGatewayServicesDir() {
    return get(STACKS_SERVICES_DIR, getGatewayDataDir() + File.separator + DEFAULT_STACKS_SERVICES_DIR);
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
    initGatewayHomeDir( lastFileUrl );
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
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getDefaultTopologyName()
   */
  @Override
  public String getDefaultTopologyName() {
    String name = get(GATEWAY_DEFAULT_TOPOLOGY_NAME_PARAM);
    return name != null ? name : GATEWAY_DEFAULT_TOPOLOGY_NAME;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getDefaultAppRedirectPath()
   */
  @Override
  public String getDefaultAppRedirectPath() {
    return "/" + getGatewayPath() + "/" + getDefaultTopologyName();
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getFrontendUrl()
   */
  @Override
  public String getFrontendUrl() {
    String url = get( FRONTEND_URL, null );
    return url;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getExcludedSSLProtocols()
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

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#isClientAuthNeeded()
   */
  @Override
  public boolean isClientAuthNeeded() {
    String clientAuthNeeded = get( CLIENT_AUTH_NEEDED, "false" );
    return "true".equals(clientAuthNeeded);
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getTruststorePath()
   */
  @Override
  public String getTruststorePath() {
    return get( TRUSTSTORE_PATH, null);
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getTrustAllCerts()
   */
  @Override
  public boolean getTrustAllCerts() {
    String trustAllCerts = get( TRUST_ALL_CERTS, "false" );
    return "true".equals(trustAllCerts);
  }
  
  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getTruststorePath()
   */
  @Override
  public String getTruststoreType() {
    return get( TRUSTSTORE_TYPE, "JKS");
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getTruststorePath()
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
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getEphemeralDHKeySize()
   */
  @Override
  public String getEphemeralDHKeySize() {
    return get( EPHEMERAL_DH_KEY_SIZE, "2048");
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getHttpClientMaxConnections()
   */
  @Override
  public int getHttpClientMaxConnections() {
    return getInt( HTTP_CLIENT_MAX_CONNECTION, 32 );
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.config.GatewayConfig#getThreadPoolMax()
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

}

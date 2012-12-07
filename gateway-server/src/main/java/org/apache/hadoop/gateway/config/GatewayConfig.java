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
package org.apache.hadoop.gateway.config;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
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
public class GatewayConfig extends Configuration {

  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );

  public static final String GATEWAY_HOME_VAR = "GATEWAY_HOME";

  private static final String GATEWAY_CONFIG_PREFIX = "gateway";

  public static final String[] GATEWAY_CONFIG_FILENAMES = {
      GATEWAY_CONFIG_PREFIX + "-default.xml",
      GATEWAY_CONFIG_PREFIX + "-site.xml"
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

  public static final String HTTP_HOST = GATEWAY_CONFIG_PREFIX + ".host";
  public static final String HTTP_PORT = GATEWAY_CONFIG_PREFIX + ".port";
  public static final String HTTP_PATH = GATEWAY_CONFIG_PREFIX + ".path";
  public static final String CLUSTER_CONF_DIR = GATEWAY_CONFIG_PREFIX + ".cluster.conf.dir";
  public static final String HADOOP_CONF_DIR = GATEWAY_CONFIG_PREFIX + ".hadoop.conf.dir";
  public static final String SHIRO_CONFIG_FILE = GATEWAY_CONFIG_PREFIX + ".shiro.config.file";

  public static final String DEFAULT_HTTP_PORT = "8888";
  public static final String DEFAULT_HTTP_PATH = "gateway";
  public static final String DEFAULT_CLUSTER_CONF_DIR = "clusters";
  public static final String DEFAULT_SHIRO_CONFIG_FILE = "shiro.ini";

  public GatewayConfig() {
    init();
  }

  public String getGatewayHomeDir() {
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
      File dir = file.getParentFile();
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

  public String getGatewayHost() {
    String host = get( HTTP_HOST, "0.0.0.0" );
    return host;
  }

  public int getGatewayPort() {
    return Integer.parseInt( get( HTTP_PORT, DEFAULT_HTTP_PORT ) );
  }

  public String getGatewayPath() {
    return get( HTTP_PATH, DEFAULT_HTTP_PATH );
  }

  public String getClusterConfDir() {
    return get( CLUSTER_CONF_DIR, DEFAULT_CLUSTER_CONF_DIR );
  }

  public String getShiroConfigFile() {
    return get( SHIRO_CONFIG_FILE, DEFAULT_SHIRO_CONFIG_FILE );
  }

//  public String getNameNodeAddress() {
//    return get( NAMENODE_ADDRESS, DEFAULT_NAMENODE_ADDRESS );
//  }

//  public String getTempletonAddress() {
//    return get( TEMPLETON_ADDRESS, DEFAULT_TEMPLETON_ADDRESS );
//  }

  public InetSocketAddress getGatewayAddress() throws UnknownHostException {
    String host = getGatewayHost();
    int port = getGatewayPort();
    InetSocketAddress address = new InetSocketAddress( host, port );
    return address;
  }

}

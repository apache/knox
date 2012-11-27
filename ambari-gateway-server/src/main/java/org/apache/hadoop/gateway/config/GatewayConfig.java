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

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * The configuration for the Gateway.  This merges the normal Hadoop
 * configuration with the Gateway specific variables.
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
 * The configuration files are loaded in this order with later files
 * overriding earlier ones.
 *
 * To find the configuration files, we first attempt to load a file
 * from the CLASSPATH and then look in the directory specified in the
 * GATEWAY_HOME environment variable.
 *
 * In addition the configuration files may access the special env
 * variable env for all environment variables.  For example, the
 * hadoop executable could be specified using:
 *<pre>
 *      ${env.HADOOP_PREFIX}/bin/hadoop
 *</pre>
 */
public class GatewayConfig extends Configuration {

//  private static final GatewayMessages log = MessagesFactory.get( GatewayMessages.class );

  public static final String GATEWAY_HOME_VAR = "GATEWAY_HOME";

  private static final String GATEWAY_CONF_PREFIX = "gateway";

  public static final String[] GATEWAY_CONF_FILENAMES = {
      GATEWAY_CONF_PREFIX + "-default.xml",
      GATEWAY_CONF_PREFIX + "-site.xml"
  };

  private static final String[] HADOOP_CONF_FILENAMES = {
      "core-default.xml",
      "core-site.xml"
//      "hdfs-default.xml",
//      "hdfs-site.xml",
//      "mapred-default.xml",
//      "mapred-site.xml"
  };

//  private static final String[] HADOOP_PREFIX_VARS = {
//      "HADOOP_PREFIX",
//      "HADOOP_HOME"
//  };

  public static final String HTTP_HOST = GATEWAY_CONF_PREFIX + ".host";
  public static final String HTTP_PORT = GATEWAY_CONF_PREFIX + ".port";
  public static final String HTTP_PATH = GATEWAY_CONF_PREFIX + ".path";
  public static final String HADOOP_CONF_DIR = GATEWAY_CONF_PREFIX + ".hadoop.conf.dir";
  public static final String SHIRO_CONFIG_FILE = GATEWAY_CONF_PREFIX + ".shiro.config.file";
//  public static final String AMBARI_ADDRESS = GATEWAY_CONF_PREFIX + ".ambari.address";
//  public static final String NAMENODE_ADDRESS = GATEWAY_CONF_PREFIX + ".namenode.address";
//  public static final String TEMPLETON_ADDRESS = GATEWAY_CONF_PREFIX + ".templeton.address";

  public static final String DEFAULT_HTTP_PORT = "8888";
  public static final String DEFAULT_HTTP_PATH = "gateway";
  public static final String DEFAULT_SHIRO_CONFIG_FILE = "shiro.ini";
  public static final String DEFAULT_AMBARI_ADDRESS = "localhost:50000";
//  public static final String DEFAULT_NAMENODE_ADDRESS = "localhost:50070";
//  public static final String DEFAULT_TEMPLETON_ADDRESS = "localhost:50111";

  public GatewayConfig() {
    init();
  }

  public static String getGatewayHomeDir() {
    return System.getProperty( GATEWAY_HOME_VAR, System.getenv( GATEWAY_HOME_VAR ) );
  }

  public String getHadoopConfDir() {
    return get( HADOOP_CONF_DIR );
  }

  private void init() {
    for( Map.Entry<String, String> e : System.getenv().entrySet() ) {
      set( "env." + e.getKey(), e.getValue() );
    }

    String hadoopConfDir = getHadoopConfDir();
    for( String fname : HADOOP_CONF_FILENAMES ) {
      loadFileConfig( hadoopConfDir, fname );
    }

    String homeDir = getGatewayHomeDir();
    for( String fname : GATEWAY_CONF_FILENAMES ) {
      if( !loadClassPathConfig( fname ) ) {
        loadFileConfig( homeDir, fname );
      }
    }
  }

  private boolean loadFileConfig( String dir, String file ) {
    if( dir != null ) {
      File f = new File( dir, file );
      if( f.exists() ) {
        addResource( new Path( f.getAbsolutePath() ) );
        return true;
      }
    }
    return false;
  }

  private boolean loadClassPathConfig( String file ) {
    URL url = getResource( file );
    if( url != null ) {
      addResource( url );
      return true;
    }
    return false;
  }

  public String getGatewayHost() throws UnknownHostException {
    String host = get( HTTP_HOST, null );
    if( host == null ) {
      host = InetAddress.getLocalHost().getCanonicalHostName();
    }
    return host;
  }

  public int getGatewayPort() {
    return Integer.parseInt( get( HTTP_PORT, DEFAULT_HTTP_PORT ) );
  }

  public String getGatewayPath() {
    return get( HTTP_PATH, DEFAULT_HTTP_PATH );
  }

//  public String getAmbariAddress() {
//    return get( AMBARI_ADDRESS, DEFAULT_AMBARI_ADDRESS );
//  }

  public String getShiroConfigFile() {
    return get( SHIRO_CONFIG_FILE, DEFAULT_SHIRO_CONFIG_FILE );
  }

//  public String getNameNodeAddress() {
//    return get( NAMENODE_ADDRESS, DEFAULT_NAMENODE_ADDRESS );
//  }

//  public String getTempletonAddress() {
//    return get( TEMPLETON_ADDRESS, DEFAULT_TEMPLETON_ADDRESS );
//  }

  public InetSocketAddress getGatewayAddr() throws UnknownHostException {
    String host = get( HTTP_HOST, null );
    int port = getGatewayPort();
    InetSocketAddress addr;
    if( host != null ) {
      addr = new InetSocketAddress( host, port );
    } else {
      addr = new InetSocketAddress( port );
    }
    return addr;
  }
}

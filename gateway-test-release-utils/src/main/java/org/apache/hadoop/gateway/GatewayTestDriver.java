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

import com.mycila.xmltool.XMLTag;
import org.apache.commons.io.FileUtils;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.security.ldap.SimpleLdapDirectoryServer;
import org.apache.hadoop.gateway.services.DefaultGatewayServices;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class GatewayTestDriver {

  private static Logger log = LoggerFactory.getLogger(GatewayTestDriver.class);

  public Class<?> resourceBaseClass;
  public SimpleLdapDirectoryServer ldap;
  public TcpTransport ldapTransport;
  public boolean useGateway;
  public GatewayServer gateway;
  public GatewayConfig config;
  public String clusterName;

  /**
   * Sets the class from which relative test resource names should be resolved.
   * @param resourceBaseClass The class from which relative test resource names should be resolved.
   */
  public void setResourceBase( Class<?> resourceBaseClass ) {
    this.resourceBaseClass = resourceBaseClass;
  }

  /**
   * Starts an embedded LDAP server of the specified port.
   * @param port The desired port the LDAP server should listen on.
   * @return The actual port the LDAP server is listening on.
   * @throws Exception Thrown if a failure occurs.
   */
  public int setupLdap( int port ) throws Exception {
    URL usersUrl = getResourceUrl("users.ldif");
    ldapTransport = new TcpTransport( 0 );
    ldap = new SimpleLdapDirectoryServer( "dc=hadoop,dc=apache,dc=org", new File( usersUrl.toURI() ), ldapTransport );
    ldap.start();
    log.info( "LDAP port = " + ldapTransport.getAcceptor().getLocalAddress().getPort() );
    return port;
  }


  /**
   * Creates a GATEWAY_HOME, starts a gateway instance and deploys a test topology.
   */
  public void setupGateway( GatewayTestConfig config, String cluster, XMLTag topology, boolean use ) throws Exception {
    this.useGateway = use;
    this.config = config;
    this.clusterName = cluster;

    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();

    config.setGatewayHomeDir( gatewayDir.getAbsolutePath() );

    File topoDir = new File( config.getGatewayTopologyDir() );
    topoDir.mkdirs();

    File deployDir = new File( config.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    File descriptor = new File( topoDir, cluster + ".xml" );
    FileOutputStream stream = new FileOutputStream( descriptor );
    topology.toStream( stream );
    stream.close();

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<String,String>();
    options.put("persist-master", "false");
    options.put("master", "password");
    try {
      srvcs.init(config, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace(); // I18N not required.
    }
    File stacksDir = new File( config.getGatewayServicesDir() );
    stacksDir.mkdirs();
    //TODO: [sumit] This is a hack for now, need to find a better way to locate the source resources for 'stacks' to be tested
    String pathToStacksSource = "gateway-service-definitions/src/main/resources/services";
    File stacksSourceDir = new File( targetDir.getParent(), pathToStacksSource);
    if (!stacksSourceDir.exists()) {
      stacksSourceDir = new File( targetDir.getParentFile().getParentFile().getParent(), pathToStacksSource);
    }
    if (stacksSourceDir.exists()) {
      FileUtils.copyDirectoryToDirectory(stacksSourceDir, stacksDir);
    }

    gateway = GatewayServer.startGateway(config, srvcs);
    MatcherAssert.assertThat("Failed to start gateway.", gateway, notNullValue());

    log.info( "Gateway port = " + gateway.getAddresses()[ 0 ].getPort() );
  }

  public void cleanup() throws Exception {
    gateway.stop();
    FileUtils.deleteQuietly( new File( config.getGatewayTopologyDir() ) );
    FileUtils.deleteQuietly( new File( config.getGatewayConfDir() ) );
    FileUtils.deleteQuietly( new File( config.getGatewaySecurityDir() ) );
    FileUtils.deleteQuietly( new File( config.getGatewayDeploymentDir() ) );
    FileUtils.deleteQuietly( new File( config.getGatewayDataDir() ) );
    FileUtils.deleteQuietly( new File( config.getGatewayServicesDir() ) );
    ldap.stop( true );
  }


  public String getResourceBaseName() {
    return resourceBaseClass.getName().replaceAll( "\\.", "/" ) + "/";
  }

  public String getResourceName( String resource ) {
    return getResourceBaseName() + resource;
  }

  public URL getResourceUrl( String resource ) {
    URL url = ClassLoader.getSystemResource( getResourceName( resource ) );
    assertThat( "Failed to find test resource " + resource, url, Matchers.notNullValue() );
    return url;
  }

  public String getLdapUrl() {
    return "ldap://localhost:" + ldapTransport.getAcceptor().getLocalAddress().getPort();
  }

  public String getClusterUrl() {
    String url;
    String localHostName = getLocalHostName();
    url = "http://" + localHostName + ":" + gateway.getAddresses()[0].getPort() + "/" + config.getGatewayPath() + "/" + clusterName;
    return url;
  }

  public int getGatewayPort() {
    return gateway.getAddresses()[0].getPort();
  }

  private String getLocalHostName() {
    String hostName = "localhost";
    try {
      hostName = InetAddress.getByName("127.0.0.1").getHostName();
    } catch( UnknownHostException e ) {
      // Ignore and use the default.
    }
    return hostName;
  }

}

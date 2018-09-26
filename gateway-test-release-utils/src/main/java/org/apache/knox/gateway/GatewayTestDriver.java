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

import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.security.ldap.SimpleLdapDirectoryServer;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.test.mock.MockServer;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mycila.xmltool.XMLTag;

/**
 * This class was created to reduce much of the duplication and boiler plate that was ending up in the GatewayBasicFuncTest class.
 * It basically does a number of different things.
 * 1) Creates a GATEWAY_HOME starts a gateway instance and deployes a test topology.
 * 2) Provides a registry of mock Hadoop services.
 * 3) Provides "bundled" methods for common Hadoop operations to avoid duplication in tests.
 * 4) Provides methods to access test resources.
 */
public class GatewayTestDriver {

  private static Logger log = LoggerFactory.getLogger( GatewayTestDriver.class );

  public Class<?> resourceBaseClass;
  public Map<String,Service> services = new HashMap<>();
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
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }
    Path path = FileSystems.getDefault().getPath(basedir, "/src/test/resources/users.ldif");
    return setupLdap( port, path.toFile() );
  }

  public int setupLdap( int port, File ldifConfig ) throws Exception {
    ldapTransport = new TcpTransport( port );
    ldap = new SimpleLdapDirectoryServer( "dc=hadoop,dc=apache,dc=org", ldifConfig, ldapTransport );
    ldap.start();
    log.info( "LDAP port = " + ldapTransport.getAcceptor().getLocalAddress().getPort() );
    return port;
  }

  /**
   * Adds a mock service to the registry.
   */
  public void setupService( String role, String realUrl, String gatewayPath, boolean mock ) throws Exception {
    Service service = new Service( role, realUrl, gatewayPath, mock );
    services.put( role, service );
    log.info( role + " port = " + service.server.getPort() );
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
    Map<String,String> options = new HashMap<>();
    options.put("persist-master", "false");
    options.put("master", "password");
    try {
      srvcs.init(config, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace(); // I18N not required.
    }

    gateway = GatewayServer.startGateway( config, srvcs );
    MatcherAssert.assertThat( "Failed to start gateway.", gateway, CoreMatchers.notNullValue() );

    log.info( "Gateway port = " + gateway.getAddresses()[ 0 ].getPort() );
  }

  public void cleanup() throws Exception {
    if ( gateway != null ) {
      gateway.stop();
    }
    if ( config != null ) {
      FileUtils.deleteQuietly( new File( config.getGatewayTopologyDir() ) );
      FileUtils.deleteQuietly( new File( config.getGatewayConfDir() ) );
      FileUtils.deleteQuietly( new File( config.getGatewaySecurityDir() ) );
      FileUtils.deleteQuietly( new File( config.getGatewayDeploymentDir() ) );
      FileUtils.deleteQuietly( new File( config.getGatewayDataDir() ) );
    }

    for( Service service : services.values() ) {
      service.server.stop();
    }
    services.clear();

    ldap.stop( true );
  }

  public boolean isUseGateway() {
    return useGateway;
  }

  public MockServer getMock( String serviceRole ) {
    Service service = services.get( serviceRole );
    return service.server;
  }

  public String getRealUrl( String serviceRole ) {
    return getUrl( serviceRole, true );
  }

  public String getUrl( String serviceRole ) {
    return getUrl( serviceRole, false );
  }

  private String getLocalHostName() {
    String hostName = "localhost";
    try {
      hostName = InetAddress.getByName( "127.0.0.1" ).getHostName();
    } catch( UnknownHostException e ) {
      // Ignore and use the default.
    }
    return hostName;
  }

  public String getUrl( String serviceRole, boolean real ) {
    String url;
    String localHostName = getLocalHostName();
    Service service = services.get( serviceRole );
    if( useGateway && !real ) {
      url = "http://" + localHostName + ":" + gateway.getAddresses()[0].getPort() + "/" + config.getGatewayPath() + service.gatewayPath;
    } else if( service.mock ) {
      url = "http://" + localHostName + ":" + service.server.getPort();
    } else {
      url = service.realUrl.toASCIIString();
    }
    return url;
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

  public String getRealAddr( String role ) {
    String addr;
    String localHostName = getLocalHostName();
    Service service = services.get( role );
    if( service.mock ) {
      addr = localHostName + ":" + service.server.getPort();
    } else {
      addr = service.realUrl.getHost() + ":" + service.realUrl.getPort();
    }
    return addr;
  }

  public String getLdapUrl() {
    return "ldap://localhost:" + ldapTransport.getAcceptor().getLocalAddress().getPort();
  }

  private static class Service {
    String role;
    URI realUrl;
    String gatewayPath;
    boolean mock;
    MockServer server;
    private Service( String role, String realUrl, String gatewayPath, boolean mock ) throws Exception {
      this.role = role;
      this.realUrl = new URI( realUrl );
      this.gatewayPath = gatewayPath;
      this.mock = mock;
      this.server = new MockServer( role, true );
    }
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

  public InputStream getResourceStream( String resource ) throws IOException {
    InputStream stream = null;
    if( resource.startsWith( "file:/" ) ) {
      try {
        stream = FileUtils.openInputStream( new File( new URI( resource ) ) );
      } catch( URISyntaxException e ) {
        throw new IOException( e  );
      }
    } else {
      stream = ClassLoader.getSystemResourceAsStream( getResourceName( resource ) );
    }
    assertThat( "Failed to find test resource " + resource, stream, Matchers.notNullValue() );
    return stream;
  }

  public byte[] getResourceBytes( String resource ) throws IOException {
    return IOUtils.toByteArray( getResourceStream( resource ) );
  }

  public String getResourceString( String resource, Charset charset ) throws IOException {
    return IOUtils.toString( getResourceBytes( resource ), "UTF-8" );
  }

  public void assertComplete() {
    // Check to make sure that all interaction were satisfied if for mocked services.
    // Otherwise just clear the mock interaction queue.
    for( Service service : services.values() ) {
      if( service.mock ) {
        assertThat(
            "Service " + service.role + " has remaining expected interactions.",
            service.server.getCount(), Matchers.is(0) );
      }
      service.server.reset();
    }
  }


  public void assertNotComplete(String serviceName) {
    // Check to make sure that all interaction were satisfied if for mocked services.
    // Otherwise just clear the mock interaction queue.

    Service service = services.get(serviceName);

    if(service != null) {
      if(service.mock) {
        assertThat(
            "Service " + service.role + " has remaining expected interactions.",
            service.server.getCount(), Matchers.not(0));
      }
      service.server.reset();
    } else {
      fail();
    }
  }

  public void reset() {
    for( Service service : services.values() ) {
      service.server.reset();
    }
  }


}

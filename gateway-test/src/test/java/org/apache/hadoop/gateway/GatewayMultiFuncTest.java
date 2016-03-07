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

import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import com.jayway.restassured.RestAssured;
import org.apache.commons.io.FileUtils;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.hadoop.gateway.security.ldap.SimpleLdapDirectoryServer;
import org.apache.hadoop.gateway.services.DefaultGatewayServices;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.topology.TopologyService;
import org.apache.hadoop.test.TestUtils;
import org.apache.hadoop.test.category.ReleaseTest;
import org.apache.hadoop.test.mock.MockServer;
import org.apache.http.HttpStatus;
import org.apache.log4j.Appender;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.ConnectionConfig.connectionConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.hadoop.test.TestUtils.LOG_ENTER;
import static org.apache.hadoop.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@Category(ReleaseTest.class)
public class GatewayMultiFuncTest {

  private static Logger LOG = LoggerFactory.getLogger( GatewayMultiFuncTest.class );
  private static Class DAT = GatewayMultiFuncTest.class;

  private static Enumeration<Appender> appenders;
  private static GatewayTestConfig config;
  private static DefaultGatewayServices services;
  private static GatewayServer gateway;
  private static int gatewayPort;
  private static String gatewayUrl;
  private static String clusterUrl;
  private static SimpleLdapDirectoryServer ldap;
  private static TcpTransport ldapTransport;
  private static int ldapPort;
  private static Properties params;
  private static TopologyService topos;
  private static MockServer mockWebHdfs;

  @BeforeClass
  public static void setupSuite() throws Exception {
    LOG_ENTER();
    RestAssured.config = newConfig().connectionConfig(connectionConfig().closeIdleConnectionsAfterEachResponse());
    //appenders = NoOpAppender.setUp();
    setupLdap();
    setupGateway();
    LOG_EXIT();
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    LOG_ENTER();
    gateway.stop();
    ldap.stop( true );
    FileUtils.deleteQuietly( new File( config.getGatewayHomeDir() ) );
    //NoOpAppender.tearDown( appenders );
    LOG_EXIT();
  }

  @After
  public void cleanupTest() throws Exception {
    FileUtils.cleanDirectory( new File( config.getGatewayTopologyDir() ) );
    FileUtils.cleanDirectory( new File( config.getGatewayDeploymentDir() ) );
  }

  public static void setupLdap() throws Exception {
    URL usersUrl = TestUtils.getResourceUrl( DAT, "users.ldif" );
    ldapTransport = new TcpTransport( 0 );
    ldap = new SimpleLdapDirectoryServer( "dc=hadoop,dc=apache,dc=org", new File( usersUrl.toURI() ), ldapTransport );
    ldap.start();
    LOG.info( "LDAP port = " + ldapTransport.getAcceptor().getLocalAddress().getPort() );
  }

  public static void setupGateway() throws Exception {

    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();

    config = new GatewayTestConfig();
    config.setGatewayHomeDir( gatewayDir.getAbsolutePath() );

    URL svcsFileUrl = TestUtils.getResourceUrl( DAT, "services/readme.txt" );
    File svcsFile = new File( svcsFileUrl.getFile() );
    File svcsDir = svcsFile.getParentFile();
    config.setGatewayServicesDir( svcsDir.getAbsolutePath() );

    URL appsFileUrl = TestUtils.getResourceUrl( DAT, "applications/readme.txt" );
    File appsFile = new File( appsFileUrl.getFile() );
    File appsDir = appsFile.getParentFile();
    config.setGatewayApplicationsDir( appsDir.getAbsolutePath() );

    File topoDir = new File( config.getGatewayTopologyDir() );
    topoDir.mkdirs();

    File deployDir = new File( config.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    setupMockServers();
    startGatewayServer();
  }

  public static void setupMockServers() throws Exception {
    mockWebHdfs = new MockServer( "WEBHDFS", true );
  }

  public static void startGatewayServer() throws Exception {
    services = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<String,String>();
    options.put( "persist-master", "false" );
    options.put( "master", "password" );
    try {
      services.init( config, options );
    } catch ( ServiceLifecycleException e ) {
      e.printStackTrace(); // I18N not required.
    }
    topos = services.getService(GatewayServices.TOPOLOGY_SERVICE);

    gateway = GatewayServer.startGateway( config, services );
    MatcherAssert.assertThat( "Failed to start gateway.", gateway, notNullValue() );

    gatewayPort = gateway.getAddresses()[0].getPort();
    gatewayUrl = "http://localhost:" + gatewayPort + "/" + config.getGatewayPath();
    clusterUrl = gatewayUrl + "/test-topology";

    LOG.info( "Gateway port = " + gateway.getAddresses()[ 0 ].getPort() );

    params = new Properties();
    params.put( "LDAP_URL", "ldap://localhost:" + ldapTransport.getAcceptor().getLocalAddress().getPort() );
    params.put( "WEBHDFS_URL", "http://localhost:" + mockWebHdfs.getPort() );
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testDefaultJsonMimeTypeHandlingKnox678() throws Exception {
    LOG_ENTER();

    MockServer mock = new MockServer( "REPEAT", true );

    params.put( "MOCK_SERVER_PORT", mock.getPort() );

    String topoStr = TestUtils.merge( DAT, "topologies/test-knox678-utf8-chars-topology.xml", params );
    File topoFile = new File( config.getGatewayTopologyDir(), "topology.xml" );
    FileUtils.writeStringToFile( topoFile, topoStr );

    topos.reloadTopologies();

    String uname = "guest";
    String pword = uname + "-password";

    mock.expect().respond().contentType( "application/json" ).content( "{\"msg\":\"H\u00eallo\"}", Charset.forName( "UTF8" ) );
    given()
        //.log().all()
        .auth().preemptive().basic( uname, pword )
        .expect()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .body( "msg", is( "H\u00eallo" ) )
        .when().get( gatewayUrl + "/topology/repeat" );
    assertThat( mock.isEmpty(), is(true) );

    mock.expect().respond().contentType( "application/json" ).content( "{\"msg\":\"H\u00eallo\"}", Charset.forName( "UTF8" ) );
    given()
        //.log().all()
        .auth().preemptive().basic( uname, pword )
        .expect()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .body( "msg", is( "H\u00eallo" ) )
        .when().get( gatewayUrl + "/topology/repeat" );
    assertThat( mock.isEmpty(), is(true) );

    mock.expect().respond().contentType( "application/octet-stream" ).content( "H\u00eallo".getBytes() );
    byte[] body = given()
        //.log().all()
        .auth().preemptive().basic( uname, pword )
        .expect()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        //.contentType( "application/octet-stream" )
        .when().get( gatewayUrl + "/topology/repeat" ).andReturn().asByteArray();
    assertThat( body, is(equalTo("H\u00eallo".getBytes())) );
    assertThat( mock.isEmpty(), is(true) );

    LOG_EXIT();
  }

}

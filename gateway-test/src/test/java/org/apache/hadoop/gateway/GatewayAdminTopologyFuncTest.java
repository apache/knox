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

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.ResponseBody;
import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.security.ldap.SimpleLdapDirectoryServer;
import org.apache.hadoop.gateway.services.DefaultGatewayServices;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.http.HttpStatus;
import org.apache.log4j.Appender;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.jayway.restassured.RestAssured.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class GatewayAdminTopologyFuncTest {

  private static Class RESOURCE_BASE_CLASS = GatewayAdminTopologyFuncTest.class;
  private static Logger LOG = LoggerFactory.getLogger( GatewayAdminTopologyFuncTest.class );

  public static Enumeration<Appender> appenders;
  public static GatewayConfig config;
  public static GatewayServer gateway;
  public static String gatewayUrl;
  public static String clusterUrl;
  public static SimpleLdapDirectoryServer ldap;
  public static TcpTransport ldapTransport;

  @BeforeClass
  public static void setupSuite() throws Exception {
    //appenders = NoOpAppender.setUp();
    setupLdap();
    setupGateway();
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    gateway.stop();
    ldap.stop( true );
    //FileUtils.deleteQuietly( new File( config.getGatewayHomeDir() ) );
    //NoOpAppender.tearDown( appenders );
  }

  public static void setupLdap() throws Exception {
    URL usersUrl = getResourceUrl( "users.ldif" );
    int port = findFreePort();
    ldapTransport = new TcpTransport( port );
    ldap = new SimpleLdapDirectoryServer( "dc=hadoop,dc=apache,dc=org", new File( usersUrl.toURI() ), ldapTransport );
    ldap.start();
    LOG.info( "LDAP port = " + ldapTransport.getPort() );
  }

  public static void setupGateway() throws IOException {

    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();

    GatewayTestConfig testConfig = new GatewayTestConfig();
    config = testConfig;
    testConfig.setGatewayHomeDir( gatewayDir.getAbsolutePath() );

    File topoDir = new File( testConfig.getGatewayTopologyDir() );
    topoDir.mkdirs();

    File deployDir = new File( testConfig.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    File descriptor = new File( topoDir, "admin.xml" );
    FileOutputStream stream = new FileOutputStream( descriptor );
    createKnoxTopology().toStream( stream );
    stream.close();

    File descriptor2 = new File( topoDir, "test-cluster.xml" );
    FileOutputStream stream2 = new FileOutputStream( descriptor2 );
    createNormalTopology().toStream( stream2 );
    stream.close();


    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<String,String>();
    options.put( "persist-master", "false" );
    options.put( "master", "password" );

    try {
      srvcs.init( testConfig, options );
    } catch ( ServiceLifecycleException e ) {
      e.printStackTrace(); // I18N not required.
    }
    gateway = GatewayServer.startGateway( testConfig, srvcs );
    MatcherAssert.assertThat( "Failed to start gateway.", gateway, notNullValue() );

    LOG.info( "Gateway port = " + gateway.getAddresses()[ 0 ].getPort() );

    gatewayUrl = "http://localhost:" + gateway.getAddresses()[0].getPort() + "/" + config.getGatewayPath();
    clusterUrl = gatewayUrl + "/admin";
  }

  private static XMLTag createNormalTopology() {
    XMLTag xml = XMLDoc.newDocument( true )
        .addRoot( "topology" )
        .addTag( "gateway" )
        .addTag( "provider" )
        .addTag( "role" ).addText( "webappsec" )
        .addTag( "name" ).addText( "WebAppSec" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "param" )
        .addTag( "name" ).addText( "csrf.enabled" )
        .addTag( "value" ).addText( "true" ).gotoParent().gotoParent()
        .addTag( "provider" )
        .addTag( "role" ).addText( "authentication" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm" )
        .addTag( "value" ).addText( "org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.userDnTemplate" )
        .addTag( "value" ).addText( "uid={0},ou=people,dc=hadoop,dc=apache,dc=org" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.url" )
        .addTag( "value" ).addText( "ldap://localhost:" + ldapTransport.getPort() ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.authenticationMechanism" )
        .addTag( "value" ).addText( "simple" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "urls./**" )
        .addTag( "value" ).addText( "authcBasic" ).gotoParent().gotoParent()
        .addTag( "provider" )
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "Pseudo" ).gotoParent()
        .addTag( "provider" )
        .addTag( "role" ).addText( "authorization" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "AclsAuthz" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "webhdfs-acl" )
        .addTag( "value" ).addText( "hdfs;*;*" ).gotoParent()
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "WEBHDFS" )
        .addTag( "url" ).addText( "http://localhost:50070/webhdfs/v1" ).gotoParent()
        .gotoRoot();
//     System.out.println( "GATEWAY=" + xml.toString() );
    return xml;
  }

  private static XMLTag createKnoxTopology() {
    XMLTag xml = XMLDoc.newDocument( true )
        .addRoot( "topology" )
        .addTag( "gateway" )
        .addTag( "provider" )
        .addTag( "role" ).addText( "authentication" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm" )
        .addTag( "value" ).addText( "org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.userDnTemplate" )
        .addTag( "value" ).addText( "uid={0},ou=people,dc=hadoop,dc=apache,dc=org" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.url" )
        .addTag( "value" ).addText( "ldap://localhost:" + ldapTransport.getPort() ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.authenticationMechanism" )
        .addTag( "value" ).addText( "simple" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "urls./**" )
        .addTag( "value" ).addText( "authcBasic" ).gotoParent().gotoParent()
        .addTag("provider")
        .addTag( "role" ).addText( "authorization" )
        .addTag( "name" ).addText( "AclsAuthz" )
        .addTag( "enabled" ).addText( "true" )
        .addTag("param")
        .addTag("name").addText("knox.acl")
        .addTag("value").addText("admin;*;*").gotoParent().gotoParent()
        .addTag("provider")
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "Pseudo" ).gotoParent()
        .addTag( "provider" )
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "KNOX" )
        .gotoRoot();
    // System.out.println( "GATEWAY=" + xml.toString() );
    return xml;
  }

  private static int findFreePort() throws IOException {
    ServerSocket socket = new ServerSocket(0);
    int port = socket.getLocalPort();
    socket.close();
    return port;
  }

  public static InputStream getResourceStream( String resource ) throws IOException {
    return getResourceUrl( resource ).openStream();
  }

  public static URL getResourceUrl( String resource ) {
    URL url = ClassLoader.getSystemResource( getResourceName( resource ) );
    assertThat( "Failed to find test resource " + resource, url, Matchers.notNullValue() );
    return url;
  }

  public static String getResourceName( String resource ) {
    return getResourceBaseName() + resource;
  }

  public static String getResourceBaseName() {
    return RESOURCE_BASE_CLASS.getName().replaceAll( "\\.", "/" ) + "/";
  }

  @Ignore
  @Test
  public void waitForManualTesting() throws IOException {
    System.in.read();
  }

  @Test
  public void testTopologyCollection() throws ClassNotFoundException {

    String username = "admin";
    String password = "admin-password";
    String serviceUrl =  clusterUrl + "/api/v1/topologies";
    String href = given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .expect()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType("application/JSON")
        .body("name[0]", not(nullValue()))
        .body("name[1]", not(nullValue()))
        .body("uri[0]", not(nullValue()))
        .body("uri[1]", not(nullValue()))
        .body("href[0]", not(nullValue()))
        .body("href[1]", not(nullValue()))
        .body("timestamp[0]", not(nullValue()))
        .body("timestamp[1]", not(nullValue()))
        .when().get(serviceUrl).thenReturn().getBody().path("href[1]");

    given().auth().preemptive().basic(username, password)
        .expect()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType("application/JSON")
        .body("name", equalTo("test-cluster"))
        .when().get(href);

  }

  @Test
  public void testTopologyObject() throws ClassNotFoundException {

    String username = "admin";
    String password = "admin-password";
    String serviceUrl =  clusterUrl + "/api/v1/topologies";
    String href = given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .expect()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType("application/JSON")
        .when().get(serviceUrl).thenReturn().getBody().path("href[1]");

    String timestamp = given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .expect()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType("application/JSON")
        .when().get(serviceUrl)
        .thenReturn()
        .getBody()
        .path("timestamp[1]");

    ResponseBody js = given()
        .auth().preemptive().basic(username, password)
        .expect()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType("application/JSON")
        .body("name", equalTo("test-cluster"))
        .body("timestamp", equalTo(Long.parseLong(timestamp)))
        .when()
        .get(href).andReturn().getBody();

  }

  @Test
  public void testPositiveAuthorization() throws ClassNotFoundException{


    String adminUser = "admin";
    String adminPass = "admin-password";
    String url =  clusterUrl + "/api/v1/topologies";

    given()
        //.log().all()
        .auth().preemptive().basic(adminUser, adminPass)
        .expect()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType(ContentType.JSON)
        .body("name[0]", not(nullValue()))
        .body("name[1]", not(nullValue()))
        .body("uri[0]", not(nullValue()))
        .body("uri[1]", not(nullValue()))
        .body("href[0]", not(nullValue()))
        .body("href[1]", not(nullValue()))
        .body("timestamp[0]", not(nullValue()))
        .body("timestamp[1]", not(nullValue()))
        .get(url);

  }

  @Test
  public void testNegativeAuthorization() throws ClassNotFoundException{

    String guestUser = "guest";
    String guestPass = "guest-password";
    String url =  clusterUrl + "/api/v1/topologies";

    given()
        //.log().all()
        .auth().basic(guestUser, guestPass)
        .expect()
        //.log().all()
        .statusCode(HttpStatus.SC_FORBIDDEN)
        .get(url);



  }

}

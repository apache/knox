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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.category.ReleaseTest;
import org.apache.knox.test.mock.MockServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.xmlmatchers.XmlMatchers.hasXPath;
import static org.xmlmatchers.transform.XmlConverters.the;

@Category(ReleaseTest.class)
public class GatewayMultiFuncTest {
  private static final Logger LOG = LoggerFactory.getLogger( GatewayMultiFuncTest.class );
  private static final Class<?> DAT = GatewayMultiFuncTest.class;

  private static GatewayTestConfig config;
  private static DefaultGatewayServices services;
  private static GatewayServer gateway;
  private static int gatewayPort;
  private static String gatewayUrl;
  private static Properties params;
  private static TopologyService topos;
  private static GatewayTestDriver driver = new GatewayTestDriver();

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    LOG_ENTER();
    driver.setupLdap(0);
    setupGateway();
    LOG_EXIT();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    LOG_ENTER();
    gateway.stop();
    driver.cleanup();
    FileUtils.deleteQuietly( new File( config.getGatewayHomeDir() ) );
    LOG_EXIT();
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

    File descDir = new File( config.getGatewayDescriptorsDir() );
    descDir.mkdirs();

    File provConfDir = new File( config.getGatewayProvidersConfigDir() );
    provConfDir.mkdirs();

    File deployDir = new File( config.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    startGatewayServer();
  }

  public static void startGatewayServer() throws Exception {
    services = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<>();
    options.put( "persist-master", "false" );
    options.put( "master", "password" );
    try {
      services.init( config, options );
    } catch ( ServiceLifecycleException e ) {
      e.printStackTrace(); // I18N not required.
    }
    topos = services.getService(ServiceType.TOPOLOGY_SERVICE);

    gateway = GatewayServer.startGateway( config, services );
    assertThat( "Failed to start gateway.", gateway, notNullValue() );

    gatewayPort = gateway.getAddresses()[0].getPort();
    gatewayUrl = "http://localhost:" + gatewayPort + "/" + config.getGatewayPath();

    LOG.info( "Gateway port = " + gateway.getAddresses()[ 0 ].getPort() );

    params = new Properties();
    params.put( "LDAP_URL", driver.getLdapUrl() );
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testDefaultJsonMimeTypeHandlingKnox678() throws Exception {
    LOG_ENTER();

    MockServer mock = new MockServer( "REPEAT", true );

    params = new Properties();
    params.put( "LDAP_URL", driver.getLdapUrl() );
    params.put( "MOCK_SERVER_PORT", mock.getPort() );

    String topoStr = TestUtils.merge( DAT, "topologies/test-knox678-utf8-chars-topology.xml", params );
    File topoFile = new File( config.getGatewayTopologyDir(), "knox678.xml" );
    FileUtils.writeStringToFile( topoFile, topoStr, StandardCharsets.UTF_8 );

    topos.reloadTopologies();

    String uname = "guest";
    String pword = uname + "-password";

    mock.expect().method( "GET" )
        .respond().contentType( "application/json" ).contentLength( -1 ).content( "{\"msg\":\"H\u00eallo\"}", StandardCharsets.UTF_8 );
    String json = given()
        //.log().all()
        .auth().preemptive().basic( uname, pword )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "application/json;charset=UTF-8" )
        .when().get( gatewayUrl + "/knox678/repeat" ).andReturn().asString();
    assertThat( json, is("{\"msg\":\"H\u00eallo\"}") );
    assertThat( mock.isEmpty(), is(true) );

    mock.expect().method( "GET" )
        .respond().contentType( "application/octet-stream" ).contentLength( -1 ).content( "H\u00eallo".getBytes(StandardCharsets.UTF_8) );
    byte[] bytes = given()
        //.log().all()
        .auth().preemptive().basic( uname, pword )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "application/octet-stream" )
        .when().get( gatewayUrl + "/knox678/repeat" ).andReturn().asByteArray();
    assertThat( bytes, is(equalTo("H\u00eallo".getBytes(StandardCharsets.UTF_8))) );
    assertThat( mock.isEmpty(), is(true) );

    mock.stop();

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testPostWithContentTypeKnox681() throws Exception {
    LOG_ENTER();

    MockServer mock = new MockServer( "REPEAT", true );

    params = new Properties();
    params.put( "MOCK_SERVER_PORT", mock.getPort() );
    params.put( "LDAP_URL", driver.getLdapUrl() );

    String topoStr = TestUtils.merge( DAT, "topologies/test-knox678-utf8-chars-topology.xml", params );
    File topoFile = new File( config.getGatewayTopologyDir(), "knox681.xml" );
    FileUtils.writeStringToFile( topoFile, topoStr, StandardCharsets.UTF_8 );

    topos.reloadTopologies();

    mock
        .expect()
        .method( "PUT" )
        .pathInfo( "/repeat-context/" )
        .respond()
        .status( HttpStatus.SC_CREATED )
        .content( "{\"name\":\"value\"}".getBytes(StandardCharsets.UTF_8) )
        .contentLength( -1 )
        .contentType( "application/json;charset=UTF-8" )
        .header( "Location", gatewayUrl + "/knox681/repeat" );

    String uname = "guest";
    String pword = uname + "-password";

    HttpHost targetHost = new HttpHost( "localhost", gatewayPort, "http" );
    CredentialsProvider credsProvider = new BasicCredentialsProvider();
    credsProvider.setCredentials(
        new AuthScope( targetHost.getHostName(), targetHost.getPort() ),
        new UsernamePasswordCredentials( uname, pword ) );

    AuthCache authCache = new BasicAuthCache();
    BasicScheme basicAuth = new BasicScheme();
    authCache.put( targetHost, basicAuth );

    HttpClientContext context = HttpClientContext.create();
    context.setCredentialsProvider( credsProvider );
    context.setAuthCache( authCache );

    CloseableHttpClient client = HttpClients.createDefault();
    HttpPut request = new HttpPut( gatewayUrl + "/knox681/repeat" );
    request.addHeader( "X-XSRF-Header", "jksdhfkhdsf" );
    request.addHeader( "Content-Type", "application/json" );
    CloseableHttpResponse response = client.execute( request, context );
    assertThat( response.getStatusLine().getStatusCode(), is( HttpStatus.SC_CREATED ) );
    assertThat( response.getFirstHeader( "Location" ).getValue(), endsWith("/gateway/knox681/repeat" ) );
    assertThat( response.getFirstHeader( "Content-Type" ).getValue(), is("application/json;charset=utf-8") );
    String body = new String( IOUtils.toByteArray( response.getEntity().getContent() ), StandardCharsets.UTF_8 );
    assertThat( body, is( "{\"name\":\"value\"}" ) );
    response.close();
    client.close();

    mock
        .expect()
        .method( "PUT" )
        .pathInfo( "/repeat-context/" )
        .respond()
        .status( HttpStatus.SC_CREATED )
        .content( "<test-xml/>".getBytes(StandardCharsets.UTF_8) )
        .contentType( "application/xml; charset=UTF-8" )
        .header( "Location", gatewayUrl + "/knox681/repeat" );

    client = HttpClients.createDefault();
    request = new HttpPut( gatewayUrl + "/knox681/repeat" );
    request.addHeader( "X-XSRF-Header", "jksdhfkhdsf" );
    request.addHeader( "Content-Type", "application/xml" );
    response = client.execute( request, context );
    assertThat( response.getStatusLine().getStatusCode(), is( HttpStatus.SC_CREATED ) );
    assertThat( response.getFirstHeader( "Location" ).getValue(), endsWith("/gateway/knox681/repeat" ) );
    assertThat( response.getFirstHeader( "Content-Type" ).getValue(), is("application/xml; charset=UTF-8") );
    body = new String( IOUtils.toByteArray( response.getEntity().getContent() ), StandardCharsets.UTF_8 );
    assertThat( the(body), hasXPath( "/test-xml" ) );
    response.close();
    client.close();

    mock.stop();

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testLdapSearchConfigEnhancementsKnox694() throws Exception {
    LOG_ENTER();

    String topoStr;
    File topoFile;

    String adminUName = "uid=admin,ou=people,dc=hadoop,dc=apache,dc=org";
    String adminPWord = "admin-password";
    String uname = "people\\guest";
    String pword = "guest-password";
    String invalidPword = "invalid-guest-password";

    params = new Properties();
    params.put( "LDAP_URL", driver.getLdapUrl() );
    params.put( "LDAP_SYSTEM_USERNAME", adminUName );
    params.put( "LDAP_SYSTEM_PASSWORD", adminPWord );

    topoStr = TestUtils.merge( DAT, "topologies/test-knox694-principal-regex-user-dn-template.xml", params );
    topoFile = new File( config.getGatewayTopologyDir(), "knox694-1.xml" );
    FileUtils.writeStringToFile( topoFile, topoStr, StandardCharsets.UTF_8 );
    topos.reloadTopologies();

    given()
        //.log().all()
        .auth().preemptive().basic( uname, pword )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .body( is( "test-service-response" ) )
        .when().get( gatewayUrl + "/knox694-1/test-service-path/test-resource-path" );
    given()
        //.log().all()
        .auth().preemptive().basic( uname, invalidPword )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_UNAUTHORIZED )
        .when().get( gatewayUrl + "/knox694-1/test-service-path/test-resource-path" );

    topoStr = TestUtils.merge( DAT, "topologies/test-knox694-principal-regex-search-attribute.xml", params );
    topoFile = new File( config.getGatewayTopologyDir(), "knox694-2.xml" );
    FileUtils.writeStringToFile( topoFile, topoStr, StandardCharsets.UTF_8 );
    topos.reloadTopologies();

    given()
        //.log().all()
        .auth().preemptive().basic( uname, pword )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .body( is( "test-service-response" ) )
        .when().get( gatewayUrl + "/knox694-2/test-service-path/test-resource-path" );
    given()
        //.log().all()
        .auth().preemptive().basic( uname, invalidPword )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_UNAUTHORIZED )
        .when().get( gatewayUrl + "/knox694-2/test-service-path/test-resource-path" );

    topoStr = TestUtils.merge( DAT, "topologies/test-knox694-principal-regex-search-filter.xml", params );
    topoFile = new File( config.getGatewayTopologyDir(), "knox694-3.xml" );
    FileUtils.writeStringToFile( topoFile, topoStr, StandardCharsets.UTF_8 );
    topos.reloadTopologies();

    given()
        //.log().all()
        .auth().preemptive().basic( uname, pword )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .body( is( "test-service-response" ) )
        .when().get( gatewayUrl + "/knox694-3/test-service-path/test-resource-path" );
    given()
        //.log().all()
        .auth().preemptive().basic( uname, invalidPword )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_UNAUTHORIZED )
        .when().get( gatewayUrl + "/knox694-3/test-service-path/test-resource-path" );

    topoStr = TestUtils.merge( DAT, "topologies/test-knox694-principal-regex-search-scope-object.xml", params );
    topoFile = new File( config.getGatewayTopologyDir(), "knox694-4.xml" );
    FileUtils.writeStringToFile( topoFile, topoStr, StandardCharsets.UTF_8 );
    topos.reloadTopologies();

    given()
        //.log().all()
        .auth().preemptive().basic( uname, pword )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .body( is( "test-service-response" ) )
        .when().get( gatewayUrl + "/knox694-4/test-service-path/test-resource-path" );
    given()
        //.log().all()
        .auth().preemptive().basic( uname, invalidPword )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_UNAUTHORIZED )
        .when().get( gatewayUrl + "/knox694-4/test-service-path/test-resource-path" );

    topoStr = TestUtils.merge( DAT, "topologies/test-knox694-principal-regex-search-scope-onelevel-positive.xml", params );
    topoFile = new File( config.getGatewayTopologyDir(), "knox694-5.xml" );
    FileUtils.writeStringToFile( topoFile, topoStr, StandardCharsets.UTF_8 );
    topos.reloadTopologies();

    given()
        //.log().all()
        .auth().preemptive().basic( uname, pword )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .body( is( "test-service-response" ) )
        .when().get( gatewayUrl + "/knox694-5/test-service-path/test-resource-path" );
    given()
        //.log().all()
        .auth().preemptive().basic( uname, invalidPword )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_UNAUTHORIZED )
        .when().get( gatewayUrl + "/knox694-5/test-service-path/test-resource-path" );

    topoStr = TestUtils.merge( DAT, "topologies/test-knox694-principal-regex-search-scope-onelevel-negative.xml", params );
    topoFile = new File( config.getGatewayTopologyDir(), "knox694-6.xml" );
    FileUtils.writeStringToFile( topoFile, topoStr, StandardCharsets.UTF_8 );
    topos.reloadTopologies();

    given()
        //.log().all()
        .auth().preemptive().basic( uname, pword )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_UNAUTHORIZED )
        .when().get( gatewayUrl + "/knox694-6/test-service-path/test-resource-path" );

    LOG_EXIT();
  }
}



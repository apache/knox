/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.mock.MockServer;
import org.apache.http.HttpStatus;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.notNullValue;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

public class AmbariServiceDefinitionTest {

  private static Logger LOG = LoggerFactory.getLogger( AmbariServiceDefinitionTest.class );
  private static Class<?> DAT = AmbariServiceDefinitionTest.class;

  private static GatewayTestConfig config;
  private static DefaultGatewayServices services;
  private static GatewayServer gateway;
  private static int gatewayPort;
  private static String gatewayUrl;
  private static String clusterUrl;
  private static String clusterPath;
  private static Properties params;
  private static TopologyService topos;
  private static MockServer mockAmbari;

  private static VelocityEngine velocity;
  private static VelocityContext context;

  @BeforeClass
  public static void setupSuite() throws Exception {
    LOG_ENTER();
    setupGateway();
    String topoStr = TestUtils.merge( DAT, "test-topology.xml", params );
    File topoFile = new File( config.getGatewayTopologyDir(), "test-topology.xml" );
    FileUtils.writeStringToFile( topoFile, topoStr, StandardCharsets.UTF_8 );
    topos.reloadTopologies();
    LOG_EXIT();
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    LOG_ENTER();
    gateway.stop();
    FileUtils.deleteQuietly( new File( config.getGatewayHomeDir() ) );
    LOG_EXIT();
  }

  @After
  public void cleanupTest() throws Exception {
    FileUtils.cleanDirectory( new File( config.getGatewayTopologyDir() ) );
    // Test run should not fail if deleting deployment files is not successful.
    // Deletion has been already done by TopologyService.
    FileUtils.deleteQuietly( new File( config.getGatewayDeploymentDir() ) );
  }

  public static void setupGateway() throws Exception {
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();

    config = new GatewayTestConfig();
    config.setGatewayHomeDir( gatewayDir.getAbsolutePath() );

    File topoDir = new File( config.getGatewayTopologyDir() );
    topoDir.mkdirs();

    File deployDir = new File( config.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    File descDir = new File( config.getGatewayDescriptorsDir() );
    descDir.mkdirs();

    File providerConfigDir = new File( config.getGatewayProvidersConfigDir() );
    providerConfigDir.mkdirs();

    setupMockServers();
    startGatewayServer();
  }

  public static void setupMockServers() throws Exception {
    mockAmbari = new MockServer( "AMBARI", true );
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
    MatcherAssert.assertThat( "Failed to start gateway.", gateway, notNullValue() );

    gatewayPort = gateway.getAddresses()[0].getPort();
    gatewayUrl = "http://localhost:" + gatewayPort + "/" + config.getGatewayPath();
    String topologyPath = "/test-topology";
    clusterPath = "/" + config.getGatewayPath() + topologyPath;
    clusterUrl = gatewayUrl + topologyPath;

    LOG.info( "Gateway port = " + gateway.getAddresses()[ 0 ].getPort() );

    params = new Properties();
    params.put( "AMBARI_URL", "http://localhost:" + mockAmbari.getPort() );

    velocity = new VelocityEngine();
    velocity.setProperty( RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, "org.apache.velocity.runtime.log.NullLogSystem" );
    velocity.setProperty( RuntimeConstants.RESOURCE_LOADER, "classpath" );
    velocity.setProperty( "classpath.resource.loader.class", ClasspathResourceLoader.class.getName() );
    velocity.init();

    context = new VelocityContext();
    context.put( "cluster_url", clusterUrl );
    context.put( "cluster_path", clusterPath );
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void clusters() throws Exception {
    LOG_ENTER();

    String username = "guest";
    String password = "guest-password";
    String serviceUrl = clusterUrl + "/ambari/api/v1/clusters";

    mockAmbari.expect()
        .method( "GET" )
        .pathInfo( "/api/v1/clusters" )
        .respond()
        .status( HttpStatus.SC_OK )
        .content( TestUtils.getResourceStream( DAT, "clusters-response.json" ) )
        .contentType( "text/plain" );

    String body = given()
//        .log().all()
        .auth().preemptive().basic( username, password )
        .then()
//        .log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .when().get( serviceUrl ).asString();


    String name = TestUtils.getResourceName( this.getClass(), "clusters-response-expected.json" );
    Template template = velocity.getTemplate( name );
    StringWriter sw = new StringWriter();
    template.merge( context, sw );
    String expected = sw.toString();

    MatcherAssert.assertThat(body, sameJSONAs(expected));
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void historyServer() throws Exception {
    LOG_ENTER();

    String username = "guest";
    String password = "guest-password";
    String serviceUrl = clusterUrl + "/ambari/api/v1/clusters/test/hosts/c6401.ambari.apache.org/host_components/HISTORYSERVER";

    mockAmbari.expect()
        .method( "GET" )
        .pathInfo( "/api/v1/clusters/test/hosts/c6401.ambari.apache.org/host_components/HISTORYSERVER" )
        .respond()
        .status( HttpStatus.SC_OK )
        .content( TestUtils.getResourceStream( DAT, "history-server-response.json" ) )
        .contentType( "text/plain" );

    String body = given()
        .auth().preemptive().basic( username, password )
        .then()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .when().get( serviceUrl ).asString();


    String name = TestUtils.getResourceName( this.getClass(), "history-server-response-expected.json" );
    Template template = velocity.getTemplate( name );
    StringWriter sw = new StringWriter();
    template.merge( context, sw );
    String expected = sw.toString();

    MatcherAssert.assertThat(body, sameJSONAs(expected));
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void unwiseCharacterRequest() throws Exception {
    String username = "guest";
    String password = "guest-password";
    String serviceUrl = clusterUrl + "/ambari/api/v1/clusters/test/components";

    mockAmbari.expect()
        .method( "GET" )
        .pathInfo( "/api/v1/clusters/test/components" )
        .queryParam("ServiceComponentInfo/component_name", "APP_TIMELINE_SERVER|ServiceComponentInfo/category=MASTER")
        .respond()
        .status( HttpStatus.SC_OK )
        .content( TestUtils.getResourceStream( DAT, "unwise-character-response.json" ) )
        .contentType( "text/plain" );
    //only assertion here is to make sure the request can be made successfully with the unwise characters present
    //in the request url
     given()
        .auth().preemptive().basic( username, password )
        .queryParam("ServiceComponentInfo/component_name", "APP_TIMELINE_SERVER|ServiceComponentInfo/category=MASTER")
        .then()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .when().get( serviceUrl ).asString();

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void encryptedResponse() throws Exception {
    LOG_ENTER();

    String username = "guest";
    String password = "guest-password";
    String serviceUrl = clusterUrl + "/ambari/api/v1/persist/CLUSTER_CURRENT_STATUS?_=1457977721091";

    mockAmbari.expect()
        .method( "GET" )
        .pathInfo( "/api/v1/persist/CLUSTER_CURRENT_STATUS" )
        .queryParam("_","1457977721091")
        .respond()
        .status( HttpStatus.SC_OK )
        .content( TestUtils.getResourceStream( DAT, "encrypted-response.txt" ) )
        .contentType( "text/plain" );

    String body = given()
        .auth().preemptive().basic( username, password )
        .then()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .when().get( serviceUrl ).asString();

    Assert.assertNotNull(body);
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void postDataWithWrongContentType() throws Exception {
    LOG_ENTER();

    String username = "guest";
    String password = "guest-password";
    String serviceUrl = clusterUrl + "/ambari/api/v1/stacks/HDP/versions/2.3/recommendations";

    mockAmbari.expect()
        .method( "POST" )
        .pathInfo( "/api/v1/stacks/HDP/versions/2.3/recommendations" )
        .content( TestUtils.getResourceStream( DAT, "post-data-wrong-type.json" ) )
        .respond()
        .status( HttpStatus.SC_OK )
        .contentType( "application/x-www-form-urlencoded" );


    String body = given()
        .auth().preemptive().basic( username, password )
        .body(IOUtils.toByteArray(TestUtils.getResourceStream( DAT, "post-data-wrong-type.json")))
        .then()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "application/x-www-form-urlencoded" )
        .when().post( serviceUrl ).asString();

    Assert.assertNotNull(body);
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void contextPathInViewsResponse() throws Exception {
    LOG_ENTER();

    String username = "guest";
    String password = "guest-password";

    String serviceUrl = clusterUrl + "/ambari/api/v1/views?fields=versions/instances/ViewInstanceInfo,versions/" +
        "ViewVersionInfo/label&versions/ViewVersionInfo/system=false&_=1461186937589";

    mockAmbari.expect()
        .method( "GET" )
        .pathInfo( "/api/v1/views" )
        .queryParam("_", "1461186937589")
        .queryParam("versions/ViewVersionInfo/system", "false")
        .queryParam("fields", "versions/instances/ViewInstanceInfo,versions/ViewVersionInfo/label")
        .respond()
        .status( HttpStatus.SC_OK )
        .content( TestUtils.getResourceStream( DAT, "views-response.json" ) )
        .contentType( "text/plain" );

    String body = given()
        .auth().preemptive().basic( username, password )
        .then()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .when().get( serviceUrl ).asString();


    String name = TestUtils.getResourceName( this.getClass(), "views-response-expected.json" );
    Template template = velocity.getTemplate( name );
    StringWriter sw = new StringWriter();
    template.merge( context, sw );
    String expected = sw.toString();

    MatcherAssert.assertThat(body, sameJSONAs(expected));
    LOG_EXIT();
  }

}

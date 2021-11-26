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

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.test.TestUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hamcrest.MatcherAssert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.is;

public class GatewayAdminFuncTest {
  private static final Logger LOG = LogManager.getLogger( GatewayAdminFuncTest.class );

  public static GatewayConfig config;
  public static GatewayServer gateway;
  public static String gatewayUrl;
  public static String clusterUrl;
  private static GatewayTestDriver driver = new GatewayTestDriver();

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    TestUtils.LOG_ENTER();
    driver.setupLdap(0);
    setupGateway();
    TestUtils.LOG_EXIT();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    TestUtils.LOG_ENTER();
    gateway.stop();
    driver.cleanup();
    TestUtils.LOG_EXIT();
  }

  public static void setupGateway() throws Exception {

    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();

    GatewayTestConfig testConfig = new GatewayTestConfig();
    config = testConfig;
    testConfig.setGatewayHomeDir( gatewayDir.getAbsolutePath() );

    File topoDir = new File( testConfig.getGatewayTopologyDir() );
    topoDir.mkdirs();

    File descDir = new File( testConfig.getGatewayDescriptorsDir() );
    descDir.mkdirs();

    File provConfDir = new File( testConfig.getGatewayProvidersConfigDir() );
    provConfDir.mkdirs();

    File deployDir = new File( testConfig.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    File descriptor = new File( topoDir, "test-cluster.xml" );
    try(OutputStream stream = Files.newOutputStream(descriptor.toPath())) {
      createTopology().toStream( stream );
    }

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<>();
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
    clusterUrl = gatewayUrl + "/test-cluster";
  }

  private static XMLTag createTopology() {
    return XMLDoc.newDocument( true )
        .addRoot( "topology" )
        .addTag( "gateway" )
        .addTag( "provider" )
        .addTag( "role" ).addText( "authentication" )
        .addTag( "name" ).addText( "ShiroProvider" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm" )
        .addTag( "value" ).addText( "org.apache.knox.gateway.shirorealm.KnoxLdapRealm" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.userDnTemplate" )
        .addTag( "value" ).addText( "uid={0},ou=people,dc=hadoop,dc=apache,dc=org" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.url" )
        .addTag( "value" ).addText( driver.getLdapUrl() ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.authenticationMechanism" )
        .addTag( "value" ).addText( "simple" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "urls./**" )
        .addTag( "value" ).addText( "authcBasic" ).gotoParent().gotoParent()
        .addTag( "provider" )
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "Default" ).gotoParent()
        .addTag( "provider" )
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "KNOX" )
        .gotoRoot();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testAdminService() throws ClassNotFoundException {
    TestUtils.LOG_ENTER();

    String username = "guest";
    String password = "guest-password";
    String serviceUrl = clusterUrl + "/api/v1/version";
    given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .header("Accept", MediaType.APPLICATION_JSON)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        //.body( is( "{\"hash\":\"unknown\",\"version\":\"unknown\"}" ) )
        .when().get( serviceUrl );

    TestUtils.LOG_EXIT();
  }

  @Test(timeout = TestUtils.MEDIUM_TIMEOUT)
  public void testAliasAPI() {
    String username = "guest";
    String password = "guest-password";

    String addAliasUrl = clusterUrl + "/api/v1/aliases/test-cluster/myalias";
    String putAliasUrl = clusterUrl + "/api/v1/aliases/test-cluster/putalias";
    String getAliasUrl = clusterUrl + "/api/v1/aliases/test-cluster";
    String deleteAliasUrl = clusterUrl + "/api/v1/aliases/test-cluster/myalias";

    /* Test POST Alias */
    given().auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON).param("value", "mysecret")
        .then().statusCode(HttpStatus.SC_CREATED).body(
        is("{ \"created\" : { \"topology\": \"test-cluster\", \"alias\": \"myalias\" } }"))
        .when().post(addAliasUrl);

    /* Test PUT Alias */
    given().auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON).body("mysecret").then()
        .statusCode(HttpStatus.SC_CREATED).body(
        is("{ \"created\" : { \"topology\": \"test-cluster\", \"alias\": \"putalias\" } }"))
        .when().put(putAliasUrl);

    /* Test GET Alias */
    given().auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON).then()
        .statusCode(HttpStatus.SC_OK).body(
        is("{\"topology\":\"test-cluster\",\"aliases\":[\"putalias\",\"myalias\",\"encryptquerystring\"]}"))
        .when().get(getAliasUrl);

    /* Test DELETE Alias */
    given().auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON).then()
        .statusCode(HttpStatus.SC_OK).body(
        is("{ \"deleted\" : { \"topology\": \"test-cluster\", \"alias\": \"myalias\" } }"))
        .when().delete(deleteAliasUrl);

    /* Test delete/get Alias */
    given().auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON).then()
        .statusCode(HttpStatus.SC_OK).body(
        is("{\"topology\":\"test-cluster\",\"aliases\":[\"putalias\",\"encryptquerystring\"]}"))
        .when().get(getAliasUrl);
  }

  @Test(timeout = TestUtils.MEDIUM_TIMEOUT)
  public void testAliasAPIFail() {
    String username = "guest";
    String password = "guest-password";

    String addAliasUrl = clusterUrl + "/api/v1/aliases/test-cluster/myalias";
    String putAliasUrl = clusterUrl + "/api/v1/aliases/test-cluster/putalias";
    String getAliasUrl = clusterUrl + "/api/v1/aliases/no-cluster";

    /* Test POST fail */
    given().auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON).then()
        .statusCode(HttpStatus.SC_BAD_REQUEST)
        .body(is("Alias value cannot be null or blank")).when()
        .post(addAliasUrl);

    /* Test PUT fail */
    given().auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON).then()
        .statusCode(HttpStatus.SC_BAD_REQUEST)
        .body(is("Alias value cannot be null or blank")).when()
        .put(putAliasUrl);

    /* Test GET  */
    given().auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON).then()
        .statusCode(HttpStatus.SC_OK)
        .body(is("{\"topology\":\"no-cluster\",\"aliases\":[]}")).when()
        .get(getAliasUrl);
  }
}

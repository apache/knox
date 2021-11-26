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
import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.test.TestUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class GatewayLocalServiceFuncTest {
  private static final Logger LOG = LogManager.getLogger( GatewayTestDriver.class );

  public static GatewayConfig config;
  public static GatewayServer gateway;
  public static String gatewayUrl;
  public static String clusterUrl;
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
    FileUtils.deleteQuietly( new File( config.getGatewayConfDir() ) );
    FileUtils.deleteQuietly( new File( config.getGatewayDataDir() ) );
    LOG_EXIT();
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

    File descriptor = new File( topoDir, "cluster.xml" );
    try(OutputStream stream = Files.newOutputStream(descriptor.toPath())) {
      createTopology().toStream(stream);
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
    assertThat( "Failed to start gateway.", gateway, notNullValue() );

    LOG.info( "Gateway port = " + gateway.getAddresses()[ 0 ].getPort() );

    gatewayUrl = "http://localhost:" + gateway.getAddresses()[0].getPort() + "/" + config.getGatewayPath();
    clusterUrl = gatewayUrl + "/cluster";
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
        .addTag( "role" ).addText( "test-jersey-service-role" )
        .gotoRoot();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testJerseyService() throws ClassNotFoundException {
    LOG_ENTER();
    assertThat( ClassLoader.getSystemClassLoader().loadClass( "org.glassfish.jersey.servlet.ServletContainer" ), notNullValue() );
    assertThat( ClassLoader.getSystemClassLoader().loadClass(
        "org.apache.knox.gateway.jersey.JerseyDispatchDeploymentContributor"), notNullValue() );
    assertThat( ClassLoader.getSystemClassLoader().loadClass(
        "org.apache.knox.gateway.jersey.JerseyServiceDeploymentContributorBase"), notNullValue() );
    assertThat( ClassLoader.getSystemClassLoader().loadClass(
        "org.apache.knox.gateway.TestJerseyService"), notNullValue() );

    String username = "guest";
    String password = "guest-password";
    String serviceUrl = clusterUrl + "/test-jersey-service/test-jersey-resource-path";
    given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .body( is( "test-jersey-resource-response" ) )
        .when().get( serviceUrl );
    LOG_EXIT();
  }
}

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

import static io.restassured.RestAssured.given;
import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.test.TestUtils;
import org.apache.http.HttpStatus;
import org.apache.log4j.Appender;
import org.hamcrest.MatcherAssert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;

/**
 * Functional test to verify : looking up ldap groups from directory
 * and using them in acl authorization checks
 *
 */
public class GatewayLdapDynamicGroupFuncTest {

  private static Logger LOG = LoggerFactory.getLogger( GatewayLdapDynamicGroupFuncTest.class );

  public static Enumeration<Appender> appenders;
  public static GatewayConfig config;
  public static GatewayServer gateway;
  public static String gatewayUrl;
  public static String clusterUrl;
  public static String serviceUrl;
  private static GatewayTestDriver driver = new GatewayTestDriver();

  @BeforeClass
  public static void setupSuite() throws Exception {
    LOG_ENTER();
    //appenders = NoOpAppender.setUp();
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }
    Path path = FileSystems.getDefault().getPath(basedir, "/src/test/resources/users-dynamic.ldif");
    driver.setupLdap( 0, path.toFile() );
    setupGateway();
    TestUtils.awaitNon404HttpStatus( new URL( serviceUrl ), 10000, 100 );
    LOG_EXIT();
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    LOG_ENTER();
    gateway.stop();
    driver.cleanup();
    //FileUtils.deleteQuietly( new File( config.getGatewayHomeDir() ) );
    //NoOpAppender.tearDown( appenders );
    LOG_EXIT();
  }

  public static void setupGateway() throws IOException, Exception {

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

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<>();
    options.put( "persist-master", "false" );
    options.put( "master", "password" );
    try {
      srvcs.init( testConfig, options );
    } catch ( ServiceLifecycleException e ) {
      e.printStackTrace(); // I18N not required.
    }

    /*
    System.setProperty(GatewayConfig.GATEWAY_HOME_VAR, gatewayDir.getAbsolutePath());
    System.err.println("GH 10: " + System.getProperty(GatewayConfig.GATEWAY_HOME_VAR));
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outContent));
    String[] argvals = {"create-alias", "ldcSystemPassword", "--value", "guest-password", "--master", "hadoop", "--cluster", "testdg-cluster"};
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    cli.run(argvals);

    outContent.reset();
    String[] args1 = {"list-alias", "--cluster", "testdg-cluster", "--master", "hadoop"};
    cli = new KnoxCLI();
    cli.run(args1);
    System.err.println("ALIAS LIST: " + outContent.toString());

    AliasService as1 = cli.getGatewayServices().getService(GatewayServices.ALIAS_SERVICE);
    char[] passwordChars1 = as1.getPasswordFromAliasForCluster( "test-cluster", "ldcsystemPassword");
    System.err.println("ALIAS value1: " + new String(passwordChars1));
    */

    gateway = GatewayServer.startGateway( testConfig, srvcs );
    MatcherAssert.assertThat( "Failed to start gateway.", gateway, notNullValue() );

    LOG.info( "Gateway port = " + gateway.getAddresses()[ 0 ].getPort() );

    gatewayUrl = "http://localhost:" + gateway.getAddresses()[0].getPort() + "/" + config.getGatewayPath();
    clusterUrl = gatewayUrl + "/testdg-cluster";
    serviceUrl = clusterUrl + "/test-service-path/test-service-resource";

    ///*
    GatewayServices services = GatewayServer.getGatewayServices();
    AliasService aliasService = (AliasService)services.getService(GatewayServices.ALIAS_SERVICE);
    aliasService.addAliasForCluster("testdg-cluster", "ldcSystemPassword", "guest-password");

    //char[] password1 = aliasService.getPasswordFromAliasForCluster( "testdg-cluster", "ldcSystemPassword");
    //System.err.println("SETUP password 10: " + ((password1 == null) ? "NULL" : new String(password1)));

    File descriptor = new File( topoDir, "testdg-cluster.xml" );
    FileOutputStream stream = new FileOutputStream( descriptor );
    createTopology().toStream( stream );
    stream.close();

  }

  private static XMLTag createTopology() {
    XMLTag xml = XMLDoc.newDocument( true )
        .addRoot( "topology" )
        .addTag( "gateway" )

        .addTag( "provider" )
        .addTag( "role" ).addText( "authentication" )
        .addTag( "name" ).addText( "ShiroProvider" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm" )
        .addTag( "value" ).addText( "org.apache.knox.gateway.shirorealm.KnoxLdapRealm" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapGroupContextFactory" )
        .addTag( "value" ).addText( "org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory" )
        .addTag( "value" ).addText( "$ldapGroupContextFactory" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.authenticationMechanism" )
        .addTag( "value" ).addText( "simple" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.url" )
        .addTag( "value" ).addText( driver.getLdapUrl())
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.userDnTemplate" )
        .addTag( "value" ).addText( "uid={0},ou=people,dc=hadoop,dc=apache,dc=org" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.authorizationEnabled" )
        .addTag( "value" ).addText( "true" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.systemAuthenticationMechanism" )
        .addTag( "value" ).addText( "simple" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.searchBase" )
        .addTag( "value" ).addText( "ou=groups,dc=hadoop,dc=apache,dc=org" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.groupObjectClass" )
        .addTag( "value" ).addText( "groupofurls" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.memberAttribute" )
        .addTag( "value" ).addText( "memberurl" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.memberAttributeValueTemplate" )
        .addTag( "value" ).addText( "uid={0},ou=people,dc=hadoop,dc=apache,dc=org" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.systemUsername" )
        .addTag( "value" ).addText( "uid=guest,ou=people,dc=hadoop,dc=apache,dc=org" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.clusterName" )
        .addTag( "value" ).addText( "testdg-cluster" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.systemPassword" )
        .addTag( "value" ).addText( "S{ALIAS=ldcSystemPassword}" )
        // .addTag( "value" ).addText( "guest-password" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "urls./**" )
        .addTag( "value" ).addText( "authcBasic" )

        .gotoParent().gotoParent().addTag( "provider" )
        .addTag( "role" ).addText( "authorization" )
        .addTag( "name" ).addText( "AclsAuthz" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "param" )
        .addTag( "name" ).addText( "test-service-role.acl" ) // FIXME[dilli]
        .addTag( "value" ).addText( "*;directors;*" )

        .gotoParent().gotoParent().addTag( "provider" )
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "Default" ).gotoParent()

        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "test-service-role" )
        .gotoRoot();
         // System.out.println( "GATEWAY=" + xml.toString() );
    return xml;
  }

  // @Test
  public void waitForManualTesting() throws IOException {
    System.in.read();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testGroupMember() throws ClassNotFoundException, Exception {
    LOG_ENTER();
    String username = "bob";
    String password = "bob-password";
    String serviceUrl = clusterUrl + "/test-service-path/test-service-resource";
    given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .body( is( "test-service-response" ) )
        .when().get( serviceUrl );
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testNonGroupMember() throws ClassNotFoundException {
    LOG_ENTER();
    String username = "guest";
    String password = "guest-password";
    String serviceUrl = clusterUrl + "/test-service-path/test-service-resource";
    given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_FORBIDDEN )
        .when().get( serviceUrl );
    LOG_EXIT();
  }

}

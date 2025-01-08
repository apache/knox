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
import io.restassured.response.Response;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.category.ReleaseTest;
import org.apache.http.HttpStatus;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.net.URL;

import static io.restassured.RestAssured.given;
import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Functional test to verify : looking up ldap groups from directory
 * and using them in acl authorization checks
 *
 */
@Category(ReleaseTest.class)
public class GatewayLdapPosixGroupFuncTest {
  private static final GatewayTestDriver driver = new GatewayTestDriver();
  private static final String cluster = "test-cluster";
  private static final long sleep = 200;

  @BeforeClass
  public static void setupSuite() throws Exception {
    LOG_ENTER();
    URL usersUrl = TestUtils.getResourceUrl( GatewayLdapPosixGroupFuncTest.class, "users.ldif" );
    driver.setupLdap( 0, new File( usersUrl.toURI() ) );
    setupGateway();
    LOG_EXIT();
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    LOG_ENTER();
    driver.cleanup();
    LOG_EXIT();
  }

  public static void setupGateway() throws Exception {
    GatewayTestConfig config = new GatewayTestConfig();
    XMLTag topology = createTopology();
    driver.setupGateway(config, cluster, topology, true);
    String serviceUrl = driver.getClusterUrl() + "/test-service-path/test-service-resource";
    TestUtils.awaitNon404HttpStatus( new URL( serviceUrl ), 10000, 100 );

    GatewayServices services = GatewayServer.getGatewayServices();
    AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);
    aliasService.addAliasForCluster(cluster, "ldcSystemPassword", "guest-password");

    driver.stop();
    driver.start();

    TestUtils.updateFile(new File(driver.config.getGatewayTopologyDir()), cluster + ".xml", "dummyService", "dummyService_1");

    serviceUrl = driver.getClusterUrl() + "/test-service-path/test-service-resource";
    TestUtils.awaitNon404HttpStatus( new URL( serviceUrl ), 10000, 100 );
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
        .addTag( "value" ).addText( driver.getLdapUrl() )
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
        .addTag( "value" ).addText( "posixGroup" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.memberAttribute" )
        .addTag( "value" ).addText( "memberUid" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.memberAttributeValueTemplate" )
        .addTag( "value" ).addText( "uid={0}" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.clusterName" )
        .addTag( "value" ).addText( cluster )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.systemUsername" )
        .addTag( "value" ).addText( "uid=guest,ou=people,dc=hadoop,dc=apache,dc=org" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.systemPassword" )
        .addTag( "value" ).addText( "S{ALIAS=ldcSystemPassword}" )
        .gotoParent().addTag( "param" )
        .addTag( "name" ).addText( "urls./**" )
        .addTag( "value" ).addText( "authcBasic" )

        .gotoParent().gotoParent().addTag( "provider" )
        .addTag( "role" ).addText( "authorization" )
        .addTag( "name" ).addText( "AclsAuthz" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "param" )
        .addTag( "name" ).addText( "test-service-role.acl" )
        .addTag( "value" ).addText( "*;analyst;*" )

        .gotoParent().gotoParent().addTag( "provider" )
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "Default" ).gotoParent()

        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "test-service-role" )
        .gotoRoot()

        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "dummyService" )
        .gotoRoot();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testGroupMember() throws InterruptedException {
    LOG_ENTER();
    String username = "sam";
    String password = "sam-password";
    String serviceUrl = driver.getClusterUrl() + "/test-service-path/test-service-resource";
    Response response = waitForActiveTopology(serviceUrl, username, password);
    assertEquals( HttpStatus.SC_OK, response.getStatusCode() );
    assertThat( response.getContentType(), containsString( "text/plain" ) );
    assertThat( response.getBody().asString(), is( "test-service-response" ) );
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testNonGroupMember() throws InterruptedException {
    LOG_ENTER();
    String username = "guest";
    String password = "guest-password";
    String serviceUrl = driver.getClusterUrl() + "/test-service-path/test-service-resource";
    Response response = waitForActiveTopology(serviceUrl, username, password);
    assertEquals( HttpStatus.SC_FORBIDDEN, response.getStatusCode() );
    LOG_EXIT();
  }

  private Response waitForActiveTopology( String url, String username, String password ) throws InterruptedException {
    while( true ) {
      Response response = given()
              .auth().preemptive().basic( username, password )
              .when().get( url ).andReturn();
      if( response.getStatusCode() == HttpStatus.SC_NOT_FOUND || response.getStatusCode() == HttpStatus.SC_SERVICE_UNAVAILABLE ) {
        Thread.sleep( sleep );
        continue;
      }
      return response;
    }
  }

}

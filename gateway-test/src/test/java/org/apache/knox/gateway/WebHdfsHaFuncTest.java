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

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.category.ReleaseTest;
import org.apache.knox.test.mock.MockServer;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.is;

@Category(ReleaseTest.class)
public class WebHdfsHaFuncTest {

   // Specifies if the test requests should go through the gateway or directly to the services.
   // This is frequently used to verify the behavior of the test both with and without the gateway.
   private static final boolean USE_GATEWAY = true;

   // Specifies if the test requests should be sent to mock services or the real services.
   // This is frequently used to verify the behavior of the test both with and without mock services.
   private static final boolean USE_MOCK_SERVICES = true;

   private static GatewayTestDriver driver = new GatewayTestDriver();

   private static MockServer masterServer;

   private static MockServer standbyServer;

   /**
    * Creates a deployment of a gateway instance that all test methods will share.  This method also creates a
    * registry of sorts for all of the services that will be used by the test methods.
    * The createTopology method is used to create the topology file that would normally be read from disk.
    * The driver.setupGateway invocation is where the creation of GATEWAY_HOME occurs.
    * <p>
    * This would normally be done once for this suite but the failure tests start affecting each other depending
    * on the state the last 'active' url
    *
    * @throws Exception Thrown if any failure occurs.
    */
   @Before
   public void setup() throws Exception {
      LOG_ENTER();
      //Log.setLog(new NoOpLogger());
      masterServer = new MockServer("master", true);
      standbyServer = new MockServer("standby", true);
      GatewayTestConfig config = new GatewayTestConfig();
      config.setGatewayPath("gateway");
      driver.setResourceBase(WebHdfsHaFuncTest.class);
      driver.setupLdap(0);
      driver.setupService("WEBHDFS", "http://vm.local:50070/webhdfs", "/cluster/webhdfs", USE_MOCK_SERVICES);
      driver.setupGateway(config, "cluster", createTopology(), USE_GATEWAY);
      LOG_EXIT();
   }

   @After
   public void cleanup() throws Exception {
      LOG_ENTER();
      driver.cleanup();
      driver.reset();
      masterServer.reset();
      standbyServer.reset();
      LOG_EXIT();
   }

   /**
    * Creates a topology that is deployed to the gateway instance for the test suite.
    * Note that this topology is shared by all of the test methods in this suite.
    *
    * @return A populated XML structure for a topology file.
    */
   private static XMLTag createTopology() {
      return XMLDoc.newDocument(true)
            .addRoot("topology")
            .addTag("gateway")
            .addTag("provider")
            .addTag("role").addText("webappsec")
            .addTag("name").addText("WebAppSec")
            .addTag("enabled").addText("true")
            .addTag("param")
            .addTag("name").addText("csrf.enabled")
            .addTag("value").addText("true").gotoParent().gotoParent()
            .addTag("provider")
            .addTag("role").addText("authentication")
            .addTag("name").addText("ShiroProvider")
            .addTag("enabled").addText("true")
            .addTag("param")
            .addTag("name").addText("main.ldapRealm")
            .addTag("value").addText("org.apache.knox.gateway.shirorealm.KnoxLdapRealm").gotoParent()
            .addTag("param")
            .addTag("name").addText("main.ldapRealm.userDnTemplate")
            .addTag("value").addText("uid={0},ou=people,dc=hadoop,dc=apache,dc=org").gotoParent()
            .addTag("param")
            .addTag("name").addText("main.ldapRealm.contextFactory.url")
            .addTag("value").addText(driver.getLdapUrl()).gotoParent()
            .addTag("param")
            .addTag("name").addText("main.ldapRealm.contextFactory.authenticationMechanism")
            .addTag("value").addText("simple").gotoParent()
            .addTag("param")
            .addTag("name").addText("urls./**")
            .addTag("value").addText("authcBasic").gotoParent().gotoParent()
            .addTag("provider")
            .addTag("role").addText("identity-assertion")
            .addTag("enabled").addText("true")
            .addTag("name").addText("Default").gotoParent()
            .addTag("provider")
            .addTag("role").addText("authorization")
            .addTag("enabled").addText("true")
            .addTag("name").addText("AclsAuthz").gotoParent()
            .addTag("param")
            .addTag("name").addText("webhdfs-acl")
            .addTag("value").addText("hdfs;*;*").gotoParent()
            .addTag("provider")
            .addTag("role").addText("ha")
            .addTag("enabled").addText("true")
            .addTag("name").addText("HaProvider")
            .addTag("param")
            .addTag("name").addText("WEBHDFS")
            .addTag("value").addText("maxFailoverAttempts=3;failoverSleep=15;enabled=true").gotoParent()
            .gotoRoot()
            .addTag("service")
            .addTag("role").addText("WEBHDFS")
            .addTag("url").addText("http://localhost:" + masterServer.getPort() + "/webhdfs")
            .addTag("url").addText("http://localhost:" + standbyServer.getPort() + "/webhdfs").gotoParent()
            .gotoRoot();
   }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
   public void testBasicListOperation() throws IOException {
      LOG_ENTER();
      String username = "hdfs";
      String password = "hdfs-password";
      masterServer.expect()
            .method("GET")
            .pathInfo("/webhdfs/v1/")
            .queryParam("op", "LISTSTATUS")
            .queryParam("user.name", username)
            .respond()
            .status(HttpStatus.SC_OK)
            .content(driver.getResourceBytes("webhdfs-liststatus-success.json"))
            .contentType("application/json");
      given()
            .auth().preemptive().basic(username, password)
            .header("X-XSRF-Header", "jksdhfkhdsf")
            .queryParam("op", "LISTSTATUS")
            .then()
            .log().ifError()
            .statusCode(HttpStatus.SC_OK)
            .body("FileStatuses.FileStatus[0].pathSuffix", is("app-logs"))
            .when().get(driver.getUrl("WEBHDFS") + "/v1/");
      masterServer.isEmpty();
      LOG_EXIT();
   }

   @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
   @Ignore( "KNOX-446" )
   public void testFailoverListOperation() throws Exception {
      LOG_ENTER();
      String username = "hdfs";
      String password = "hdfs-password";
      //Shutdown master and then standby to serve the list response
      masterServer.stop();
      standbyServer.expect()
            .method("GET")
            .pathInfo("/webhdfs/v1/")
            .queryParam("op", "LISTSTATUS")
            .queryParam("user.name", username)
            .respond()
            .status(HttpStatus.SC_OK)
            .content(driver.getResourceBytes("webhdfs-liststatus-success.json"))
            .contentType("application/json");
      given()
            .auth().preemptive().basic(username, password)
            .header("X-XSRF-Header", "jksdhfkhdsf")
            .queryParam("op", "LISTSTATUS")
            .then()
            .log().ifError()
            .statusCode(HttpStatus.SC_OK)
            .body("FileStatuses.FileStatus[0].pathSuffix", is("app-logs"))
            .when().get(driver.getUrl("WEBHDFS") + "/v1/");
      standbyServer.isEmpty();
      masterServer.start();
      LOG_EXIT();
   }

   @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
   public void testFailoverLimit() throws Exception {
      LOG_ENTER();
      String username = "hdfs";
      String password = "hdfs-password";
      //Shutdown master and then standby to serve the list response
      masterServer.stop();
      standbyServer.stop();
      given()
            .auth().preemptive().basic(username, password)
            .header("X-XSRF-Header", "jksdhfkhdsf")
            .queryParam("op", "LISTSTATUS")
            .then()
//            .log().ifError()
            .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
            .when().get(driver.getUrl("WEBHDFS") + "/v1/");
      standbyServer.start();
      masterServer.start();
      LOG_EXIT();
   }


   @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
   @Ignore( "KNOX-446" )
   public void testServerInStandby() throws IOException {
      LOG_ENTER();
      String username = "hdfs";
      String password = "hdfs-password";
      //make master the server that is in standby
      masterServer.expect()
            .method("GET")
            .pathInfo("/webhdfs/v1/")
            .queryParam("op", "LISTSTATUS")
            .queryParam("user.name", username)
            .respond()
            .status(HttpStatus.SC_FORBIDDEN)
            .content(driver.getResourceBytes("webhdfs-liststatus-standby.json"))
            .contentType("application/json");
      //standby server is 'active' in this test case and serves the list response
      standbyServer.expect()
            .method("GET")
            .pathInfo("/webhdfs/v1/")
            .queryParam("op", "LISTSTATUS")
            .queryParam("user.name", username)
            .respond()
            .status(HttpStatus.SC_OK)
            .content(driver.getResourceBytes("webhdfs-liststatus-success.json"))
            .contentType("application/json");
      given()
            .auth().preemptive().basic(username, password)
            .header("X-XSRF-Header", "jksdhfkhdsf")
            .queryParam("op", "LISTSTATUS")
            .then()
            .log().ifError()
            .statusCode(HttpStatus.SC_OK)
            .body("FileStatuses.FileStatus[0].pathSuffix", is("app-logs"))
            .when().get(driver.getUrl("WEBHDFS") + "/v1/");
      masterServer.isEmpty();
      standbyServer.isEmpty();
      LOG_EXIT();
   }

   @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
   public void testServerInStandbyFailoverLimit() throws IOException {
      LOG_ENTER();
      String username = "hdfs";
      String password = "hdfs-password";
      //make master the server that is in standby
      masterServer.expect()
            .method("GET")
            .pathInfo("/webhdfs/v1/")
            .queryParam("op", "LISTSTATUS")
            .queryParam("user.name", username)
            .respond()
            .status(HttpStatus.SC_FORBIDDEN)
            .content(driver.getResourceBytes("webhdfs-liststatus-standby.json"))
            .contentType("application/json");
      standbyServer.expect()
            .method("GET")
            .pathInfo("/webhdfs/v1/")
            .queryParam("op", "LISTSTATUS")
            .queryParam("user.name", username)
            .respond()
            .status(HttpStatus.SC_FORBIDDEN)
            .content(driver.getResourceBytes("webhdfs-liststatus-standby.json"))
            .contentType("application/json");
      masterServer.expect()
            .method("GET")
            .pathInfo("/webhdfs/v1/")
            .queryParam("op", "LISTSTATUS")
            .queryParam("user.name", username)
            .respond()
            .status(HttpStatus.SC_FORBIDDEN)
            .content(driver.getResourceBytes("webhdfs-liststatus-standby.json"))
            .contentType("application/json");
      standbyServer.expect()
            .method("GET")
            .pathInfo("/webhdfs/v1/")
            .queryParam("op", "LISTSTATUS")
            .queryParam("user.name", username)
            .respond()
            .status(HttpStatus.SC_FORBIDDEN)
            .content(driver.getResourceBytes("webhdfs-liststatus-standby.json"))
            .contentType("application/json");
      given()
            .auth().preemptive().basic(username, password)
            .header("X-XSRF-Header", "jksdhfkhdsf")
            .queryParam("op", "LISTSTATUS")
            .then()
//            .log().ifError()
            .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
            .when().get(driver.getUrl("WEBHDFS") + "/v1/");
      masterServer.isEmpty();
      standbyServer.isEmpty();
      LOG_EXIT();
   }

   @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
   public void testServerInSafeMode() throws IOException {
      LOG_ENTER();
      String username = "hdfs";
      String password = "hdfs-password";
      //master is in safe mode
      masterServer.expect()
            .method("POST")
            .pathInfo("/webhdfs/v1/user/hdfs/foo.txt")
            .queryParam("op", "RENAME")
            .queryParam("destination", "/user/hdfs/foo.txt")
            .queryParam("user.name", username)
            .respond()
            .status(HttpStatus.SC_FORBIDDEN)
            .content(driver.getResourceBytes("webhdfs-rename-safemode.json"))
            .contentType("application/json");
      masterServer.expect()
            .method("POST")
            .pathInfo("/webhdfs/v1/user/hdfs/foo.txt")
            .queryParam("op", "RENAME")
            .queryParam("destination", "/user/hdfs/foo.txt")
            .queryParam("user.name", username)
            .respond()
            .status(HttpStatus.SC_OK)
            .content(driver.getResourceBytes("webhdfs-rename-safemode-off.json"))
            .contentType("application/json");
      given()
            .auth().preemptive().basic(username, password)
            .header("X-XSRF-Header", "jksdhfkhdsf")
            .queryParam("op", "RENAME")
            .queryParam("destination", "/user/hdfs/foo.txt")
            .then()
            .log().ifError()
            .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
            .when().post(driver.getUrl("WEBHDFS") + "/v1/user/hdfs/foo.txt");
      masterServer.isEmpty();
      LOG_EXIT();
   }

   @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
   public void testServerInSafeModeFailover() throws IOException {
      LOG_ENTER();
      String username = "hdfs";
      String password = "hdfs-password";
      //master is in safe mode
      masterServer.expect()
            .method("POST")
            .pathInfo("/webhdfs/v1/user/hdfs/new")
            .queryParam("op", "MKDIRS")
            .queryParam("user.name", username)
            .respond()
            .status(HttpStatus.SC_FORBIDDEN)
            .content(driver.getResourceBytes("webhdfs-mkdirs-safemode.json"))
            .contentType("application/json");
      standbyServer.expect()
            .method("POST")
            .pathInfo("/webhdfs/v1/user/hdfs/new")
            .queryParam("op", "MKDIRS")
            .queryParam("user.name", username)
            .respond()
            .status(HttpStatus.SC_FORBIDDEN)
            .content(driver.getResourceBytes("webhdfs-liststatus-standby.json"))
            .contentType("application/json");
      masterServer.expect()
            .method("POST")
            .pathInfo("/webhdfs/v1/user/hdfs/new")
            .queryParam("op", "MKDIRS")
            .queryParam("user.name", username)
            .respond()
            .status(HttpStatus.SC_OK)
            .content(driver.getResourceBytes("webhdfs-rename-safemode-off.json"))
            .contentType("application/json");
      given()
            .auth().preemptive().basic(username, password)
            .header("X-XSRF-Header", "jksdhfkhdsf")
            .queryParam("op", "MKDIRS")
            .then()
            .log().ifError()
            .statusCode(HttpStatus.SC_OK)
            .body("boolean", is(true))
            .when().post(driver.getUrl("WEBHDFS") + "/v1/user/hdfs/new");
      masterServer.isEmpty();
      LOG_EXIT();
   }

}

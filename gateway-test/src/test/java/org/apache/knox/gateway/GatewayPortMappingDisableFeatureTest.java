/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.ConcurrentHashMap;

import static io.restassured.RestAssured.given;
import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.is;

/**
 * Test that the Gateway Topology Port Mapping feature is disabled properly.
 *
 */
@Category(ReleaseTest.class)
public class GatewayPortMappingDisableFeatureTest {

  // Specifies if the test requests should go through the gateway or directly to the services.
  // This is frequently used to verify the behavior of the test both with and without the gateway.
  private static final boolean USE_GATEWAY = true;

  // Specifies if the test requests should be sent to mock services or the real services.
  // This is frequently used to verify the behavior of the test both with and without mock services.
  private static final boolean USE_MOCK_SERVICES = true;

  private static GatewayTestDriver driver = new GatewayTestDriver();

  private static MockServer masterServer;

  private int eeriePort;

  @Rule
  public ExpectedException exception = ExpectedException.none();


  public GatewayPortMappingDisableFeatureTest() {
    super();
  }

  /**
   * Creates a deployment of a gateway instance that all test methods will share.  This method also creates a
   * registry of sorts for all of the services that will be used by the test methods.
   * The createTopology method is used to create the topology file that would normally be read from disk.
   * The driver.setupGateway invocation is where the creation of GATEWAY_HOME occurs.
   * <p/>
   * This would normally be done once for this suite but the failure tests start affecting each other depending
   * on the state the last 'active' url
   *
   * @throws Exception Thrown if any failure occurs.
   */
  @Before
  public void setup() throws Exception {
    LOG_ENTER();

    eeriePort = getAvailablePort(1240, 49151);

    ConcurrentHashMap<String, Integer> topologyPortMapping = new ConcurrentHashMap<String, Integer>();
    topologyPortMapping.put("eerie", eeriePort);

    masterServer = new MockServer("master", true);
    GatewayTestConfig config = new GatewayTestConfig();
    config.setGatewayPath("gateway");
    config.setTopologyPortMapping(topologyPortMapping);
    // disable the feature
    config.setGatewayPortMappingEnabled(false);

    driver.setResourceBase(WebHdfsHaFuncTest.class);
    driver.setupLdap(0);

    driver.setupService("WEBHDFS", "http://vm.local:50070/webhdfs", "/eerie/webhdfs", USE_MOCK_SERVICES);

    driver.setupGateway(config, "eerie", createTopology("WEBHDFS"), USE_GATEWAY);

    LOG_EXIT();
  }

  @After
  public void cleanup() throws Exception {
    LOG_ENTER();
    driver.cleanup();
    driver.reset();
    masterServer.reset();
    LOG_EXIT();
  }

  /**
   * Test the standard case
   *
   * @throws IOException
   */
  @Test(timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testBasicListOperation() throws IOException {
    LOG_ENTER();
    test(driver.getUrl("WEBHDFS") );
    LOG_EXIT();
  }

  /**
   * Test the multi port fail scenario when the feature is disabled.
   * @throws IOException
   */
  @Test(timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testMultiPortFailOperation() throws IOException {
    LOG_ENTER();
    exception.expect(ConnectException.class);
    exception.expectMessage("Connection refused");

    test("http://localhost:" + eeriePort + "/webhdfs" );
    LOG_EXIT();
  }


  private void test (final String url) throws IOException {
    String password = "hdfs-password";
    String username = "hdfs";

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
        .when().get(url + "/v1/");
    masterServer.isEmpty();
  }


  /**
   * Creates a topology that is deployed to the gateway instance for the test suite.
   * Note that this topology is shared by all of the test methods in this suite.
   *
   * @return A populated XML structure for a topology file.
   */
  private static XMLTag createTopology(final String role) {
    XMLTag xml = XMLDoc.newDocument(true)
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
        .addTag("value").addText("maxFailoverAttempts=3;failoverSleep=15;maxRetryAttempts=3;retrySleep=10;enabled=true").gotoParent()
        .gotoRoot()
        .addTag("service")
        .addTag("role").addText(role)
        .addTag("url").addText("http://localhost:" + masterServer.getPort() + "/webhdfs")
        .gotoRoot();
    return xml;
  }

  /**
   * This utility method will return the next available port
   * that can be used.
   *
   * @return Port that is available.
   */
  public static int getAvailablePort(final int min, final int max) {

    for (int i = min; i <= max; i++) {

      if (!GatewayServer.isPortInUse(i)) {
        return i;
      }
    }
    // too bad
    return -1;
  }

}

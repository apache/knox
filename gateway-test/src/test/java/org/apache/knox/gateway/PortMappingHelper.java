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
import org.apache.http.HttpStatus;
import org.apache.knox.test.mock.MockServer;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import static io.restassured.RestAssured.given;
import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.is;

/**
 * Helper class that contains common code used by port mapping tests
 */
public abstract class PortMappingHelper {
  // Specifies if the test requests should go through the gateway or directly to the services.
  // This is frequently used to verify the behavior of the test both with and without the gateway.
  private static final boolean USE_GATEWAY = true;
  // Specifies if the test requests should be sent to mock services or the real services.
  // This is frequently used to verify the behavior of the test both with and without mock services.
  private static final boolean USE_MOCK_SERVICES = true;
  static GatewayTestDriver driver = new GatewayTestDriver();
  static MockServer masterServer;
  static int eeriePort;

  PortMappingHelper() {
    super();
  }

  /**
   * Creates a deployment of a gateway instance that all test methods will
   * share.  This method also creates a registry of sorts for all of the
   * services that will be used by the test methods. The createTopology method
   * is used to create the topology file that would normally be read from disk.
   * The driver.setupGateway invocation is where the creation of GATEWAY_HOME
   * occurs.
   * <p>
   * This would normally be done once for this suite but the failure tests start
   * affecting each other depending on the state the last 'active' url
   *
   * @param defaultTopologyName default topology name
   * @param topologyPortMapping mapping to topology to port
   * @param isPortMappingEnabled boolean if port mapping is enabled
   * @throws Exception Thrown if any failure occurs.
   */
  public static void init(final String defaultTopologyName,
                          final ConcurrentHashMap<String, Integer> topologyPortMapping,
                          final boolean isPortMappingEnabled) throws Exception {
    LOG_ENTER();

    masterServer = new MockServer("master", true);
    GatewayTestConfig config = new GatewayTestConfig();
    config.setGatewayPath("gateway");

    /* define default topology to be used */
    if (defaultTopologyName != null) {
      config.setDefaultTopologyName(defaultTopologyName);
    }

    if (topologyPortMapping != null) {
      config.setTopologyPortMapping(topologyPortMapping);
    }

    config.setGatewayPortMappingEnabled(isPortMappingEnabled);

    driver.setResourceBase(WebHdfsHaFuncTest.class);
    driver.setupLdap(0);

    driver.setupService("WEBHDFS", "http://vm.local:50070/webhdfs",
        "/eerie/webhdfs", USE_MOCK_SERVICES);

    driver.setupGateway(config, "eerie",
        createTopology("WEBHDFS", driver.getLdapUrl(), masterServer.getPort()),
        USE_GATEWAY);

    LOG_EXIT();
  }

  public static void init(final String defaultTopologyName, final boolean isPortMappingEnabled)
      throws Exception {
    init(defaultTopologyName, null, isPortMappingEnabled);
  }

  public static void init(final String defaultTopologyName, final ConcurrentHashMap<String, Integer> topologyPortMapping)
      throws Exception {
    init(defaultTopologyName, topologyPortMapping, true);
  }

  /**
   * Creates a topology that is deployed to the gateway instance for the test
   * suite. Note that this topology is shared by all of the test methods in this
   * suite.
   *
   * @param role        role name
   * @param ldapURL     ldap url
   * @param gatewayPort port for the gateway
   * @return A populated XML structure for a topology file.
   */
  static XMLTag createTopology(final String role, final String ldapURL, final int gatewayPort) {
    return XMLDoc.newDocument(true).addRoot("topology").addTag("gateway")
        .addTag("provider").addTag("role").addText("webappsec").addTag("name")
        .addText("WebAppSec").addTag("enabled").addText("true").addTag("param")
        .addTag("name").addText("csrf.enabled").addTag("value").addText("true")
        .gotoParent().gotoParent().addTag("provider").addTag("role")
        .addText("authentication").addTag("name").addText("ShiroProvider")
        .addTag("enabled").addText("true").addTag("param").addTag("name")
        .addText("main.ldapRealm").addTag("value")
        .addText("org.apache.knox.gateway.shirorealm.KnoxLdapRealm")
        .gotoParent().addTag("param").addTag("name")
        .addText("main.ldapRealm.userDnTemplate").addTag("value")
        .addText("uid={0},ou=people,dc=hadoop,dc=apache,dc=org").gotoParent()
        .addTag("param").addTag("name")
        .addText("main.ldapRealm.contextFactory.url").addTag("value")
        .addText(ldapURL).gotoParent().addTag("param").addTag("name")
        .addText("main.ldapRealm.contextFactory.authenticationMechanism")
        .addTag("value").addText("simple").gotoParent().addTag("param")
        .addTag("name").addText("urls./**").addTag("value")
        .addText("authcBasic").gotoParent().gotoParent().addTag("provider")
        .addTag("role").addText("identity-assertion").addTag("enabled")
        .addText("true").addTag("name").addText("Default").gotoParent()
        .addTag("provider").addTag("role").addText("authorization")
        .addTag("enabled").addText("true").addTag("name").addText("AclsAuthz")
        .gotoParent().addTag("param").addTag("name").addText("webhdfs-acl")
        .addTag("value").addText("hdfs;*;*").gotoParent().addTag("provider")
        .addTag("role").addText("ha").addTag("enabled").addText("true")
        .addTag("name").addText("HaProvider").addTag("param").addTag("name")
        .addText("WEBHDFS").addTag("value").addText(
            "maxFailoverAttempts=3;failoverSleep=15;enabled=true")
        .gotoParent().gotoRoot().addTag("service").addTag("role").addText(role)
        .addTag("url").addText("http://localhost:" + gatewayPort + "/webhdfs")
        .gotoRoot();
  }

  /**
   * This utility method will return the next available port that can be used.
   *
   * @param min min port to check
   * @param max max port to check
   * @return Port that is available.
   */
  static int getAvailablePort(final int min, final int max) {
    for (int i = min; i <= max; i++) {
      if (!GatewayServer.isPortInUse(i)) {
        return i;
      }
    }
    // too bad
    return -1;
  }

  void test(final String url) throws IOException {
    String password = "hdfs-password";
    String username = "hdfs";

    masterServer.expect().method("GET").pathInfo("/webhdfs/v1/")
        .queryParam("op", "LISTSTATUS").queryParam("user.name", username)
        .respond().status(HttpStatus.SC_OK)
        .content(driver.getResourceBytes("webhdfs-liststatus-success.json"))
        .contentType("application/json");

    given().auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf").queryParam("op", "LISTSTATUS")
        .then().log().ifError().statusCode(HttpStatus.SC_OK)
        .body("FileStatuses.FileStatus[0].pathSuffix", is("app-logs")).when()
        .get(url + "/v1/");
    masterServer.isEmpty();
  }
}

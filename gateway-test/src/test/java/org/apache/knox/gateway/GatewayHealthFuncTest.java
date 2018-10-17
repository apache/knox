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

import io.restassured.path.json.JsonPath;
import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.knox.test.TestUtils;
import org.apache.http.HttpStatus;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import static io.restassured.RestAssured.given;

public class GatewayHealthFuncTest {
  private static GatewayTestDriver driver = new GatewayTestDriver();

  // Specifies if the test requests should go through the gateway or directly to the services.
  // This is frequently used to verify the behavior of the test both with and without the gateway.
  private static final boolean USE_GATEWAY = true;

  // Specifies if the GATEWAY_HOME created for the test should be deleted when the test suite is complete.
  // This is frequently used during debugging to keep the GATEWAY_HOME around for inspection.
  private static final boolean CLEANUP_TEST = true;

  @BeforeClass
  public static void setupSuite() throws Exception {
    TestUtils.LOG_ENTER();
    driver.setupLdap(0);
    driver.setupGateway( new GatewayTestConfig(), "cluster", createTopology(), USE_GATEWAY );
    TestUtils.LOG_EXIT();
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    TestUtils.LOG_ENTER();
    if( CLEANUP_TEST ) {
      driver.cleanup();
    }
    TestUtils.LOG_EXIT();
  }

  private static XMLTag createTopology() {
    XMLTag xml = XMLDoc.newDocument(true)
        .addRoot("topology")
        .addTag("gateway")
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
        .gotoRoot()
        .addTag("service")
        .addTag("role").addText("HEALTH")
        .gotoRoot();
    return xml;
  }

  @Test(timeout = TestUtils.MEDIUM_TIMEOUT)
  public void testPingResource() {
    TestUtils.LOG_ENTER();
    String username = "guest";
    String password = "guest-password";
    String serviceUrl = driver.getClusterUrl() + "/v1/ping";
    String body = given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.TEXT_PLAIN)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType(MediaType.TEXT_PLAIN)
        .when().get(serviceUrl).asString();
    Assert.assertEquals("OK", body.trim());
    TestUtils.LOG_EXIT();
  }

  @Test(timeout = TestUtils.MEDIUM_TIMEOUT)
  public void testMetricsResource() {
    TestUtils.LOG_ENTER();
    String username = "guest";
    String password = "guest-password";
    String serviceUrl = driver.getClusterUrl() + "/v1/metrics";
    String body = given()
        .auth().preemptive().basic(username, password)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType(MediaType.APPLICATION_JSON)
        .when().get(serviceUrl).asString();
    Map<String, String> hm = JsonPath.from(body).getMap("");
    Assert.assertTrue(hm.size() >= 6);
    Assert.assertTrue(hm.keySet().containsAll(new HashSet<String>(Arrays.asList(new String[]{"timers", "histograms",
        "counters", "gauges", "version", "meters"}))));
    TestUtils.LOG_EXIT();
  }

}

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
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.security.ldap.SimpleLdapDirectoryServer;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.test.TestUtils;
import org.apache.http.HttpStatus;
import org.hamcrest.MatcherAssert;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;

public class GatewayHealthFuncTest {
  private static final Logger LOG = LoggerFactory.getLogger(GatewayAdminFuncTest.class);

  public static GatewayConfig config;
  public static GatewayServer gateway;
  public static String gatewayUrl;
  public static String clusterUrl;
  public static SimpleLdapDirectoryServer ldap;
  public static TcpTransport ldapTransport;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    TestUtils.LOG_ENTER();
    setupLdap();
    setupGateway();
    TestUtils.LOG_EXIT();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    TestUtils.LOG_ENTER();
    gateway.stop();
    ldap.stop(true);
    TestUtils.LOG_EXIT();
  }

  public static void setupLdap() throws Exception {
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    final Path path = FileSystems
        .getDefault().getPath(basedir, "/src/test/resources/users.ldif");

    ldapTransport = new TcpTransport(0);
    ldap = new SimpleLdapDirectoryServer("dc=hadoop,dc=apache,dc=org", path.toFile(), ldapTransport);
    ldap.start();
    LOG.info("LDAP port = " + ldapTransport.getPort());
  }

  public static void setupGateway() throws Exception {

    File targetDir = new File(System.getProperty("user.dir"), "target");
    File gatewayDir = new File(targetDir, "gateway-home-" + UUID.randomUUID());
    gatewayDir.mkdirs();

    GatewayTestConfig testConfig = new GatewayTestConfig();
    config = testConfig;
    testConfig.setGatewayHomeDir(gatewayDir.getAbsolutePath());

    File topoDir = new File(testConfig.getGatewayTopologyDir());
    topoDir.mkdirs();

    File descDir = new File( testConfig.getGatewayDescriptorsDir() );
    descDir.mkdirs();

    File provConfDir = new File( testConfig.getGatewayProvidersConfigDir() );
    provConfDir.mkdirs();

    File deployDir = new File(testConfig.getGatewayDeploymentDir());
    deployDir.mkdirs();

    File descriptor = new File(topoDir, "test-cluster.xml");
    try(OutputStream stream = Files.newOutputStream(descriptor.toPath())) {
      createTopology().toStream(stream);
    }

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String, String> options = new HashMap<>();
    options.put("persist-master", "false");
    options.put("master", "password");
    try {
      srvcs.init(testConfig, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace(); // I18N not required.
    }
    gateway = GatewayServer.startGateway(testConfig, srvcs);
    MatcherAssert.assertThat("Failed to start gateway.", gateway, notNullValue());

    LOG.info("Gateway port = " + gateway.getAddresses()[0].getPort());

    gatewayUrl = "http://localhost:" + gateway.getAddresses()[0].getPort() + "/" + config.getGatewayPath();
    clusterUrl = gatewayUrl + "/test-cluster";
  }

  private static XMLTag createTopology() {
    return XMLDoc.newDocument(true)
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
        .addTag("value").addText("ldap://localhost:" + ldapTransport.getAcceptor().getLocalAddress().getPort()).gotoParent()
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
  }

  @Test(timeout = TestUtils.MEDIUM_TIMEOUT)
  public void testPingResource() {
    TestUtils.LOG_ENTER();
    String username = "guest";
    String password = "guest-password";
    String serviceUrl = clusterUrl + "/v1/ping";
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
    String serviceUrl = clusterUrl + "/v1/metrics";
    String body = given()
        .auth().preemptive().basic(username, password)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType(MediaType.APPLICATION_JSON)
        .when().get(serviceUrl).asString();
    //String version = JsonPath.from(body).getString("version");
    Map<String, String> hm = JsonPath.from(body).getMap("");
    Assert.assertTrue(hm.size() >= 6);
    Assert.assertTrue(hm.keySet().containsAll(new HashSet<>(Arrays.asList(new String[]{"timers", "histograms",
        "counters", "gauges", "version", "meters"}))));
    TestUtils.LOG_EXIT();
  }

}

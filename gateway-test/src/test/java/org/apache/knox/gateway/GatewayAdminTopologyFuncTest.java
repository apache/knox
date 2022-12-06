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

import java.io.File;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.MediaType;

import io.restassured.http.ContentType;
import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Param;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.util.XmlUtils;
import io.restassured.response.ResponseBody;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.knox.test.TestUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import static io.restassured.RestAssured.given;
import static junit.framework.TestCase.assertTrue;
import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class GatewayAdminTopologyFuncTest {
  private static final Logger LOG = LogManager.getLogger( GatewayAdminTopologyFuncTest.class );

  public static GatewayConfig config;
  public static GatewayServer gateway;
  public static String gatewayUrl;
  public static String clusterUrl;
  private static GatewayTestDriver driver = new GatewayTestDriver();

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    driver.setupLdap(0);
    setupGateway(new GatewayTestConfig());
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    gateway.stop();
    driver.cleanup();
  }

  public static void setupGateway(GatewayTestConfig testConfig) throws Exception {
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();

    config = testConfig;
    testConfig.setGatewayHomeDir( gatewayDir.getAbsolutePath() );

    File topoDir = new File( testConfig.getGatewayTopologyDir() );
    topoDir.mkdirs();

    File deployDir = new File( testConfig.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    File providerConfigDir = new File(testConfig.getGatewayConfDir(), "shared-providers");
    providerConfigDir.mkdirs();

    File descriptorsDir = new File(testConfig.getGatewayConfDir(), "descriptors");
    descriptorsDir.mkdirs();

    File descriptor = new File( topoDir, "admin.xml" );
    try(OutputStream stream = Files.newOutputStream(descriptor.toPath())) {
      createKnoxTopology().toStream(stream);
    }

    File descriptor2 = new File( topoDir, "test-cluster.xml" );
    try(OutputStream stream = Files.newOutputStream(descriptor2.toPath())) {
      createNormalTopology().toStream(stream);
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
    clusterUrl = gatewayUrl + "/admin";
  }

  private static XMLTag createNormalTopology() {
    return XMLDoc.newDocument( true )
        .addRoot( "topology" )
        .addTag( "gateway" )
        .addTag( "provider" )
        .addTag( "role" ).addText( "webappsec" )
        .addTag( "name" ).addText( "WebAppSec" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "param" )
        .addTag( "name" ).addText( "csrf.enabled" )
        .addTag( "value" ).addText( "true" ).gotoParent().gotoParent()
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
        .addTag( "role" ).addText( "authorization" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "AclsAuthz" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "webhdfs-acl" )
        .addTag( "value" ).addText( "hdfs;*;*" ).gotoParent()
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "WEBHDFS" )
        .addTag( "url" ).addText( "http://localhost:50070/webhdfs/v1" ).gotoParent()
        .gotoRoot();
  }

  private static XMLTag createKnoxTopology() {
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
        .addTag("provider")
        .addTag( "role" ).addText( "authorization" )
        .addTag( "name" ).addText( "AclsAuthz" )
        .addTag( "enabled" ).addText( "true" )
        .addTag("param")
        .addTag("name").addText("knox.acl")
        .addTag("value").addText("admin;*;*").gotoParent().gotoParent()
        .addTag("provider")
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "Default" ).gotoParent()
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "KNOX" )
        .gotoRoot();
  }

  private static XMLTag createProviderConfiguration() {
    return XMLDoc.newDocument( true )
            .addRoot( "gateway" )
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
            .addTag("provider")
            .addTag( "role" ).addText( "authorization" )
            .addTag( "name" ).addText( "AclsAuthz" )
            .addTag( "enabled" ).addText( "true" )
            .addTag("param")
            .addTag("name").addText("knox.acl")
            .addTag("value").addText("admin;*;*").gotoParent().gotoParent()
            .addTag("provider")
            .addTag( "role" ).addText( "identity-assertion" )
            .addTag( "enabled" ).addText( "true" )
            .addTag( "name" ).addText( "Default" ).gotoParent()
            .gotoRoot();
  }

  private static String createDescriptor(String clusterName) {
    return createDescriptor(clusterName, null);
  }

  private static String createDescriptor(String clusterName, String providerConfigRef) {
    return createDescriptor(clusterName, providerConfigRef, true);
  }

  private static String createDescriptor(String clusterName, String providerConfigRef, boolean discovery) {
    StringBuilder sb = new StringBuilder(1024);
    if (providerConfigRef == null) {
      providerConfigRef = "sandbox-providers";
    }

    sb.append("{\n");
    if (discovery) {
      sb.append("\"discovery-type\":\"AMBARI\",\n" +
                    "\"discovery-address\":\"http://c6401.ambari.apache.org:8080\",\n" +
                    "\"discovery-user\":\"ambariuser\",\n" +
                    "\"discovery-pwd-alias\":\"ambari.discovery.password\",\n");
    }
    sb.append("\"provider-config-ref\":\"")
        .append(providerConfigRef)
        .append("\",\n\"cluster\":\"")
        .append(clusterName)
        .append("\",\n" +
                  "\"services\":[\n" +
                  "{\"name\":\"NAMENODE\"},\n" +
                  "{\"name\":\"JOBTRACKER\"},\n" +
                  "{\"name\":\"WEBHDFS\"},\n" +
                  "{\"name\":\"WEBHCAT\"},\n" +
                  "{\"name\":\"OOZIE\"},\n" +
                  "{\"name\":\"WEBHBASE\"},\n" +
                  "{\"name\":\"HIVE\"},\n" +
                  "{\"name\":\"RESOURCEMANAGER\"},\n" +
                  "{\"name\":\"AMBARI\", \"urls\":[\"http://c6401.ambari.apache.org:8080\"]}\n" +
                  "]\n}\n");

    return sb.toString();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testTopologyCollection() {
    LOG_ENTER();

    String username = "admin";
    String password = "admin-password";
    String serviceUrl = clusterUrl + "/api/v1/topologies";
    String href1 = given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .body("topologies.topology[0].name", not(nullValue()))
        .body("topologies.topology[1].name", not(nullValue()))
        .body("topologies.topology[0].uri", not(nullValue()))
        .body("topologies.topology[1].uri", not(nullValue()))
        .body("topologies.topology[0].href", not(nullValue()))
        .body("topologies.topology[1].href", not(nullValue()))
        .body("topologies.topology[0].timestamp", not(nullValue()))
        .body("topologies.topology[1].timestamp", not(nullValue()))
        .when().get(serviceUrl).thenReturn().getBody().path("topologies.topology.href[1]");

       given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_XML)
        .then()
        //.log().all()
        .body("topologies.topology.href[1]", equalTo(href1))
        .statusCode(HttpStatus.SC_OK)
        .when().get(serviceUrl);

    given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType(MediaType.APPLICATION_XML)
        .when().get(serviceUrl);

    given().auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType("application/json")
        .body("topology.name", equalTo("test-cluster"))
        .when().get(href1);

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testTopologyObject() throws ClassNotFoundException {
    LOG_ENTER();

    String username = "admin";
    String password = "admin-password";
    String serviceUrl = clusterUrl + "/api/v1/topologies";
    String hrefJson = given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .when().get(serviceUrl).thenReturn().getBody().path("topologies.topology[1].href");

    String timestampJson = given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType("application/json")
        .when().get(serviceUrl).andReturn()
        .getBody().path("topologies.topology[1].timestamp");

        given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .body("topology.name", equalTo("test-cluster"))
        .body("topology.timestamp", equalTo(Long.parseLong(timestampJson)))
        .when()
        .get(hrefJson);

    String hrefXml = given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_XML)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .when().get(serviceUrl).thenReturn().getBody().path("topologies.topology[1].href");

    given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_XML)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .when()
        .get(hrefXml);

    LOG_EXIT();
  }

  /*
   * KNOX-1322
   */
  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testTopologyObjectForcedReadOnly() throws Exception {
    LOG_ENTER();

    final String testTopologyName = "test-cluster";

    // First, verify that without any special config, the topology is NOT marked as generated (i.e., read-only)
    validateGeneratedElement(testTopologyName, "false");

    try {
      gateway.stop();

      // Update gateway config, such that the test topology should be marked as generated (i.e., read-only)
      GatewayTestConfig conf = new GatewayTestConfig();
      conf.set(GatewayConfigImpl.READ_ONLY_OVERRIDE_TOPOLOGIES, testTopologyName);
      setupGateway(conf);

      // Verify that the generate element reflects the configuration change
      validateGeneratedElement(testTopologyName, "true");

      // Verify that another topology is unaffected by the configuration
      validateGeneratedElement("admin", "false");

    } finally {
      // Restart the gateway with old settings.
      gateway.stop();
      setupGateway(new GatewayTestConfig());
    }

    LOG_EXIT();
  }

  /**
   * Access the specified topology, and validate the value of the generated element therein.
   *
   * @param topologyName  The name of the topology to validate
   * @param expectedValue The expected value of the generated element.
   * @throws Exception exception on failure
   */
  private void validateGeneratedElement(String topologyName, String expectedValue) throws Exception {
    String testClusterTopology = given().auth().preemptive().basic("admin", "admin-password")
                                        .header("Accept", MediaType.APPLICATION_XML)
                                        .then()
                                        .statusCode(HttpStatus.SC_OK)
                                        .when().get(clusterUrl + "/api/v1/topologies/" + topologyName)
                                               .thenReturn().getBody().asString();
    assertNotNull(testClusterTopology);
    Document doc = XmlUtils.readXml(new InputSource(new StringReader(testClusterTopology)));
    assertNotNull(doc);
    assertThat(doc, hasXPath("/topology/generated", is(expectedValue)));
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPositiveAuthorization() throws ClassNotFoundException{
    LOG_ENTER();

    String adminUser = "admin";
    String adminPass = "admin-password";
    String url = clusterUrl + "/api/v1/topologies";

    given()
        //.log().all()
        .auth().preemptive().basic(adminUser, adminPass)
        .header("Accept", MediaType.APPLICATION_JSON)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType(ContentType.JSON)
        .body("topologies.topology[0].name", not(nullValue()))
        .body("topologies.topology[1].name", not(nullValue()))
        .body("topologies.topology[0].uri", not(nullValue()))
        .body("topologies.topology[1].uri", not(nullValue()))
        .body("topologies.topology[0].href", not(nullValue()))
        .body("topologies.topology[1].href", not(nullValue()))
        .body("topologies.topology[0].timestamp", not(nullValue()))
        .body("topologies.topology[1].timestamp", not(nullValue()))
        .when().get(url);

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testNegativeAuthorization() throws ClassNotFoundException{
    LOG_ENTER();

    String guestUser = "guest";
    String guestPass = "guest-password";
    String url = clusterUrl + "/api/v1/topologies";

    given()
        //.log().all()
        .auth().basic(guestUser, guestPass)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_FORBIDDEN)
        .when().get(url);

    LOG_EXIT();
  }

  private Topology createTestTopology(){
    Topology topology = new Topology();
    topology.setName("test-topology");

    try {
      topology.setUri(new URI(gatewayUrl + "/" + topology.getName()));
    } catch (URISyntaxException ex) {
      assertThat(topology.getUri(), not(nullValue()));
    }

    Provider identityProvider = new Provider();
    identityProvider.setName("Default");
    identityProvider.setRole("identity-assertion");
    identityProvider.setEnabled(true);

    Provider AuthenicationProvider = new Provider();
    AuthenicationProvider.setName("ShiroProvider");
    AuthenicationProvider.setRole("authentication");
    AuthenicationProvider.setEnabled(true);

    Param ldapMain = new Param();
    ldapMain.setName("main.ldapRealm");
    ldapMain.setValue("org.apache.knox.gateway.shirorealm.KnoxLdapRealm");

    Param ldapGroupContextFactory = new Param();
    ldapGroupContextFactory.setName("main.ldapGroupContextFactory");
    ldapGroupContextFactory.setValue("org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory");

    Param ldapRealmContext = new Param();
    ldapRealmContext.setName("main.ldapRealm.contextFactory");
    ldapRealmContext.setValue("$ldapGroupContextFactory");

    Param ldapURL = new Param();
    ldapURL.setName("main.ldapRealm.contextFactory.url");
    ldapURL.setValue(driver.getLdapUrl());

    Param ldapUserTemplate = new Param();
    ldapUserTemplate.setName("main.ldapRealm.userDnTemplate");
    ldapUserTemplate.setValue("uid={0},ou=people,dc=hadoop,dc=apache,dc=org");

    Param authcBasic = new Param();
    authcBasic.setName("urls./**");
    authcBasic.setValue("authcBasic");

    AuthenicationProvider.addParam(ldapGroupContextFactory);
    AuthenicationProvider.addParam(ldapMain);
    AuthenicationProvider.addParam(ldapRealmContext);
    AuthenicationProvider.addParam(ldapURL);
    AuthenicationProvider.addParam(ldapUserTemplate);
    AuthenicationProvider.addParam(authcBasic);

    Service testService = new Service();
    testService.setRole("test-service-role");

    topology.addProvider(AuthenicationProvider);
    topology.addProvider(identityProvider);
    topology.addService(testService);
    topology.setTimestamp(System.nanoTime());

    return topology;
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testDeployTopology() throws Exception {
    LOG_ENTER();

    Topology testTopology = createTestTopology();

    String user = "guest";
    String password = "guest-password";

    String url = gatewayUrl + "/" + testTopology.getName() + "/test-service-path/test-service-resource";

    GatewayServices srvs = GatewayServer.getGatewayServices();

    TopologyService ts = srvs.getService(ServiceType.TOPOLOGY_SERVICE);
    try {
      ts.stopMonitor();

      assertThat( testTopology, not( nullValue() ) );
      assertThat( testTopology.getName(), is( "test-topology" ) );

      given()
          //.log().all()
          .auth().preemptive().basic( "admin", "admin-password" ).header( "Accept", MediaType.APPLICATION_JSON ).then()
          //.log().all()
          .statusCode( HttpStatus.SC_OK ).body( containsString( "ServerVersion" ) ).when().get( gatewayUrl + "/admin/api/v1/version" );

      given()
          //.log().all()
          .auth().preemptive().basic( user, password ).then()
          //.log().all()
          .statusCode( HttpStatus.SC_NOT_FOUND ).when().get( url );

      ts.deployTopology( testTopology );

      given()
          //.log().all()
          .auth().preemptive().basic( user, password ).then()
          //.log().all()
          .statusCode( HttpStatus.SC_OK ).contentType( "text/plain" ).body( is( "test-service-response" ) ).when().get( url ).getBody();

      ts.deleteTopology( testTopology );

      given()
          //.log().all()
          .auth().preemptive().basic( user, password ).then()
          //.log().all()
          .statusCode( HttpStatus.SC_NOT_FOUND ).when().get( url );
    } finally {
      ts.startMonitor();
    }

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testDeleteTopology() throws ClassNotFoundException {
    LOG_ENTER();

    Topology test = createTestTopology();

    String username = "admin";
    String password = "admin-password";
    String url = clusterUrl + "/api/v1/topologies/" + test.getName();

    GatewayServices gs = GatewayServer.getGatewayServices();

    TopologyService ts = gs.getService(ServiceType.TOPOLOGY_SERVICE);

    ts.deployTopology(test);

    given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType(MediaType.APPLICATION_JSON)
        .when().get(url);

    given()
        .auth().preemptive().basic(username, password)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType(MediaType.APPLICATION_JSON)
        .when().delete(url);

    given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_NO_CONTENT)
        .when().get(url);

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPutTopology() throws Exception {
    LOG_ENTER() ;

    String username = "admin";
    String password = "admin-password";
    String url = clusterUrl + "/api/v1/topologies/test-put";

    String JsonPut =
        given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON)
        .get(clusterUrl + "/api/v1/topologies/test-cluster")
        .getBody().asString();

    String XML = given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Accept", MediaType.APPLICATION_XML)
        .body(JsonPut)
        .then()
        .statusCode(HttpStatus.SC_OK)
        //.log().all()
        .when().put(url).getBody().asString();

    InputSource source = new InputSource( new StringReader( XML ) );
    Document doc = XmlUtils.readXml( source );

    assertThat( doc, hasXPath( "/topology/gateway/provider[1]/name", containsString( "WebAppSec" ) ) );
    assertThat( doc, hasXPath( "/topology/gateway/provider[1]/param/name", containsString( "csrf.enabled" ) ) );

    given()
            .auth().preemptive().basic(username, password)
            .header("Accept", MediaType.APPLICATION_XML)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body(equalTo(XML))
            .when().get(url)
            .getBody().asString();

    String XmlPut =
        given()
            .auth().preemptive().basic(username, password)
            .header("Accept", MediaType.APPLICATION_XML)
            .get(clusterUrl + "/api/v1/topologies/test-cluster")
            .getBody().asString();

    String JSON = given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .contentType(MediaType.APPLICATION_XML)
        .header("Accept", MediaType.APPLICATION_JSON)
        .body(XmlPut)
        .then()
        .statusCode(HttpStatus.SC_OK)
            //.log().all()
        .when().put(url).getBody().asString();

    given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body(equalTo(JSON))
        .when().get(url)
        .getBody().asString();

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPutTopologyWithInvalidName() throws Exception {
    LOG_ENTER() ;

    String username = "admin";
    String password = "admin-password";
    String url = clusterUrl + "/api/v1/topologies/test-put-!nvalid";

    String JsonPut = given().auth().preemptive().basic(username, password)
                            .header("Accept", MediaType.APPLICATION_JSON)
                            .get(clusterUrl + "/api/v1/topologies/test-cluster")
                            .getBody().asString();

    given().auth().preemptive().basic(username, password)
           .contentType(MediaType.APPLICATION_JSON)
           .header("Accept", MediaType.APPLICATION_XML)
           .body(JsonPut)
           .then()
           .statusCode(HttpStatus.SC_BAD_REQUEST)
           .when().put(url).getBody().asString();

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPutTopologyWithEntityInjection() throws Exception {
    LOG_ENTER() ;

    final String MALICIOUS_PARAM_NAME = "exposed";

    final String XML_WITH_INJECTION =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<!DOCTYPE foo [<!ENTITY xxeiltvf SYSTEM \"file:///etc/hosts\"> ]>\n" +
        "<topology>\n" +
        "    <gateway>\n" +
        "        <provider>\n" +
        "            <role>authentication</role>\n" +
        "            <name>ShiroProvider</name>\n" +
        "            <enabled>true</enabled>\n" +
        "            <param>\n" +
        "                <name>" + MALICIOUS_PARAM_NAME + "</name>\n" +
        "                <value>&xxeiltvf;</value>\n" +
        "            </param>\n" +
        "            <param>\n" +
        "                <name>sessionTimeout</name>\n" +
        "                <value>30</value>\n" +
        "            </param>\n" +
        "            <param>\n" +
        "                <name>main.ldapRealm</name>\n" +
        "                <value>org.apache.knox.gateway.shirorealm.KnoxLdapRealm</value>\n" +
        "            </param>\n" +
        "            <param>\n" +
        "                <name>main.ldapContextFactory</name>\n" +
        "                <value>org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory</value>\n" +
        "            </param>\n" +
        "            <param>\n" +
        "                <name>main.ldapRealm.contextFactory</name>\n" +
        "                <value>$ldapContextFactory</value>\n" +
        "            </param>\n" +
        "            <param>\n" +
        "                <name>main.ldapRealm.userDnTemplate</name>\n" +
        "                <value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>\n" +
        "            </param>\n" +
        "            <param>\n" +
        "                <name>main.ldapRealm.contextFactory.url</name>\n" +
        "                <value>ldap://localhost:33389</value>\n" +
        "            </param>\n" +
        "            <param>\n" +
        "                <name>main.ldapRealm.contextFactory.authenticationMechanism</name>\n" +
        "                <value>simple</value>\n" +
        "            </param>\n" +
        "            <param>\n" +
        "                <name>urls./**</name>\n" +
        "                <value>authcBasic</value>\n" +
        "            </param>\n" +
        "        </provider>\n" +
        "    </gateway>\n" +
        "    <service>\n" +
        "        <role>NAMENODE</role>\n" +
        "        <url>hdfs://localhost:8020</url>\n" +
        "    </service>\n" +
        "</topology>";

    String username = "admin";
    String password = "admin-password";
    String url = clusterUrl + "/api/v1/topologies/test-put-with-entity-injection";

    // Should get a response with missing entity reference value because of the entity injection prevention safeguard
    String XML_RESPONSE = given().auth().preemptive().basic(username, password)
                                 .contentType(MediaType.APPLICATION_XML)
                                 .header("Accept", MediaType.APPLICATION_XML)
                                 .body(XML_WITH_INJECTION)
                                 .then()
                                 .statusCode(HttpStatus.SC_OK)
                                 .when().put(url).getBody().asString();

    Document doc = XmlUtils.readXml(new InputSource(new StringReader(XML_RESPONSE)));
    assertNotNull(doc);

    assertThat(doc, hasXPath("/topology/gateway/provider[1]/param/name", containsString("exposed")));
    assertThat(doc, hasXPath("/topology/gateway/provider[1]/param[\"" + MALICIOUS_PARAM_NAME + "\"]/value", is("")));

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPutTopologyWithEntityExpansion() throws Exception {
    LOG_ENTER() ;

    final String MALICIOUS_PARAM_NAME = "expanded";

    final String XML_WITH_INJECTION =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<!DOCTYPE foo [<!ENTITY xeevowya0 \"b68et\"><!ENTITY xeevowya1 \"&xeevowya0;&xeevowya0;\"><!ENTITY xeevowya2 \"&xeevowya1;&xeevowya1;\"><!ENTITY xeevowya3 \"&xeevowya2;&xeevowya2;\">]>\n" +
            "<topology>\n" +
            "    <gateway>\n" +
            "        <provider>\n" +
            "            <role>authentication</role>\n" +
            "            <name>ShiroProvider</name>\n" +
            "            <enabled>true</enabled>\n" +
            "            <param>\n" +
            "                <name>" + MALICIOUS_PARAM_NAME + "</name>\n" +
            "                <value>&xeevowya3;</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>sessionTimeout</name>\n" +
            "                <value>30</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>main.ldapRealm</name>\n" +
            "                <value>org.apache.knox.gateway.shirorealm.KnoxLdapRealm</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>main.ldapContextFactory</name>\n" +
            "                <value>org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>main.ldapRealm.contextFactory</name>\n" +
            "                <value>$ldapContextFactory</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>main.ldapRealm.userDnTemplate</name>\n" +
            "                <value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>main.ldapRealm.contextFactory.url</name>\n" +
            "                <value>ldap://localhost:33389</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>main.ldapRealm.contextFactory.authenticationMechanism</name>\n" +
            "                <value>simple</value>\n" +
            "            </param>\n" +
            "            <param>\n" +
            "                <name>urls./**</name>\n" +
            "                <value>authcBasic</value>\n" +
            "            </param>\n" +
            "        </provider>\n" +
            "    </gateway>\n" +
            "    <service>\n" +
            "        <role>NAMENODE</role>\n" +
            "        <url>hdfs://localhost:8020</url>\n" +
            "    </service>\n" +
            "</topology>";

    String username = "admin";
    String password = "admin-password";
    String url = clusterUrl + "/api/v1/topologies/test-put-with-entity-injection";

    // Should get a HTTP 500 response because of the entity injection prevention safeguard
    String XML_RESPONSE = given().auth().preemptive().basic(username, password)
                                 .contentType(MediaType.APPLICATION_XML)
                                 .header("Accept", MediaType.APPLICATION_XML)
                                 .body(XML_WITH_INJECTION)
                                 .then()
                                 .statusCode(HttpStatus.SC_OK)
                                 .when().put(url).getBody().asString();

    Document doc = XmlUtils.readXml(new InputSource(new StringReader(XML_RESPONSE)));
    assertNotNull(doc);

    assertThat(doc, hasXPath("/topology/gateway/provider[1]/param/name", containsString("expanded")));
    assertThat(doc, hasXPath("/topology/gateway/provider[1]/param[\"" + MALICIOUS_PARAM_NAME + "\"]/value", is("")));

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testXForwardedHeaders() {
    LOG_ENTER();

    String username = "admin";
    String password = "admin-password";
    String url = clusterUrl + "/api/v1/topologies";

//    X-Forward header values
    String port = String.valueOf(777);
    String server = "myserver";
    String host = server + ":" + port;
    String proto = "protocol";
    String context = "/mycontext";
    String newUrl = proto + "://" + host + context;
//    String port = String.valueOf(gateway.getAddresses()[0].getPort());

//     Case 1: Add in all x-forward headers (host, port, server, context, proto)
    given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_XML)
        .header("X-Forwarded-Host", host )
        .header("X-Forwarded-Port", port )
        .header("X-Forwarded-Server", server )
        .header("X-Forwarded-Context", context)
        .header("X-Forwarded-Proto", proto)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body(containsString(newUrl))
        .body(containsString("test-cluster"))
        .body(containsString("admin"))
        .when().get(url);

//     Case 2: add in x-forward headers (host, server, proto, context)
    given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_XML)
        .header("X-Forwarded-Host", host )
        .header("X-Forwarded-Server", server )
        .header("X-Forwarded-Context", context )
        .header("X-Forwarded-Proto", proto )
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body(containsString(server))
        .body(containsString(context))
        .body(containsString(proto))
        .body(containsString(host))
        .body(containsString("test-cluster"))
        .body(containsString("admin"))
        .when().get(url);

//     Case 3: add in x-forward headers (host, proto, port, context)
    given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_XML)
        .header("X-Forwarded-Host", host )
        .header("X-Forwarded-Port", port )
        .header("X-Forwarded-Context", context )
        .header("X-Forwarded-Proto", proto)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body(containsString(host))
        .body(containsString(port))
        .body(containsString(context))
        .body(containsString(proto))
        .body(containsString("test-cluster"))
        .body(containsString("admin"))
        .when().get(url);

//     Case 4: add in x-forward headers (host, proto, port, context) no port in host.
    given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_XML)
        .header("X-Forwarded-Host", server)
        .header("X-Forwarded-Port", port)
        .header("X-Forwarded-Context", context)
        .header("X-Forwarded-Proto", proto)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body(containsString(server))
        .body(containsString(port))
        .body(containsString(context))
        .body(containsString(proto))
        .body(containsString("test-cluster"))
        .body(containsString("admin"))
        .when().get(url);

//     Case 5: add in x-forward headers (host, port)
    given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_XML)
        .header("X-Forwarded-Host", host )
        .header("X-Forwarded-Port", port )
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body(containsString(host))
        .body(containsString(port))
        .body(containsString("test-cluster"))
        .body(containsString("admin"))
        .when().get(url);

//     Case 6: Normal Request
    given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_XML)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body(containsString(url))
        .body(containsString("test-cluster"))
        .body(containsString("admin"))
        .when().get(url);

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testGatewayPathChange() throws Exception {
    LOG_ENTER();
    String username = "admin";
    String password = "admin-password";
    String url = clusterUrl + "/api/v1/topologies";

//     Case 1: Normal Request (No Change in gateway.path). Ensure HTTP OK resp + valid URL.
    given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_XML)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body(containsString(url + "/test-cluster"))
        .when().get(url);

//     Case 2: Change gateway.path to another String. Ensure HTTP OK resp + valid URL.
   try {
     gateway.stop();

     GatewayTestConfig conf = new GatewayTestConfig();
     conf.setGatewayPath("new-gateway-path");
     setupGateway(conf);

     String newUrl = clusterUrl + "/api/v1/topologies";

     given()
         .auth().preemptive().basic(username, password)
         .header("Accept", MediaType.APPLICATION_XML)
         .then()
         .statusCode(HttpStatus.SC_OK)
         .body(containsString(newUrl + "/test-cluster"))
         .when().get(newUrl);
   } catch(Exception e){
     fail(e.getMessage());
   }
    finally {
//        Restart the gateway with old settings.
       gateway.stop();
      setupGateway(new GatewayTestConfig());
    }

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testProviderConfigurationCollection() throws Exception {
    LOG_ENTER();

    final String username = "admin";
    final String password = "admin-password";
    final String serviceUrl = clusterUrl + "/api/v1/providerconfig";

    final File sharedProvidersDir = new File(config.getGatewayConfDir(), "shared-providers");
    final List<String> configNames = Arrays.asList("sandbox-providers", "custom-providers");
    final List<String> configFileNames = Arrays.asList(configNames.get(0) + ".xml", configNames.get(1) + ".xml");

    // Request a listing of all the provider configs with an INCORRECT Accept header
    given()
      .auth().preemptive().basic(username, password)
      .header("Accept", MediaType.APPLICATION_XML)
      .then()
      .statusCode(HttpStatus.SC_NOT_ACCEPTABLE)
      .when().get(serviceUrl);

    // Request a listing of all the provider configs (with the CORRECT Accept header)
    ResponseBody responseBody = given()
                                  .auth().preemptive().basic(username, password)
                                  .header("Accept", MediaType.APPLICATION_JSON)
                                  .then()
                                  .statusCode(HttpStatus.SC_OK)
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .when().get(serviceUrl).body();
    List<String> items = responseBody.path("items");
    assertTrue("Expected no items since the shared-providers dir is empty.", items.isEmpty());

    // Manually write a file to the shared-providers directory
    File providerConfig = new File(sharedProvidersDir, configFileNames.get(0));
    try(OutputStream stream = Files.newOutputStream(providerConfig.toPath())) {
      createProviderConfiguration().toStream(stream);
    }

    // Request a listing of all the provider configs
    responseBody = given()
                      .auth().preemptive().basic(username, password)
                      .header("Accept", MediaType.APPLICATION_JSON)
                      .then()
                      .statusCode(HttpStatus.SC_OK)
                      .contentType(MediaType.APPLICATION_JSON)
                      .when().get(serviceUrl).body();
    items = responseBody.path("items");
    assertEquals("Expected items to include the new file in the shared-providers dir.", 1, items.size());
    assertEquals(configFileNames.get(0), responseBody.path("items[0].name"));
    String href1 = responseBody.path("items[0].href");

    // Manually write another file to the shared-providers directory
    File anotherProviderConfig = new File(sharedProvidersDir, configFileNames.get(1));
    try(OutputStream stream = Files.newOutputStream(anotherProviderConfig.toPath())) {
      createProviderConfiguration().toStream(stream);
    }

    // Request a listing of all the provider configs
    responseBody = given()
                      .auth().preemptive().basic(username, password)
                      .header("Accept", MediaType.APPLICATION_JSON)
                      .then()
                      .statusCode(HttpStatus.SC_OK)
                      .contentType(MediaType.APPLICATION_JSON)
                      .when().get(serviceUrl).body();
    items = responseBody.path("items");
    assertEquals(2, items.size());
    String pcOne = responseBody.path("items[0].name");
    String pcTwo = responseBody.path("items[1].name");
    assertTrue(configFileNames.contains(pcOne));
    assertTrue(configFileNames.contains(pcTwo));

    // Request a specific provider configuration (with the CORRECT Accept header)
    responseBody = given()
                      .auth().preemptive().basic(username, password)
                      .header("Accept", MediaType.APPLICATION_XML)
                      .then()
                      .statusCode(HttpStatus.SC_OK)
                      .contentType(MediaType.APPLICATION_XML)
                      .when().get(href1).body();
    String sandboxProvidersConfigContent = responseBody.asString();

    // Parse the result, to make sure it's at least valid XML
    XmlUtils.readXml(new InputSource(new StringReader(sandboxProvidersConfigContent)));

    providerConfig.delete();
    anotherProviderConfig.delete();

    // Request a specific provider configuration, which does NOT exist
    given()
      .auth().preemptive().basic(username, password)
      .header("Accept", MediaType.APPLICATION_XML)
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND)
      .when().get(serviceUrl + "/not-a-real-provider-config");

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPutProviderConfiguration() throws Exception {
    LOG_ENTER();

    final String username = "admin";
    final String password = "admin-password";
    final String serviceUrl = clusterUrl + "/api/v1/providerconfig";

    final String newProviderConfigName     = "new-provider-config";
    final String newProviderConfigFileName = newProviderConfigName + ".xml";

    XMLTag newProviderConfigXML = createProviderConfiguration();

    // Attempt to PUT a provider config with the CORRECT Content-type header
    given()
        .auth().preemptive().basic(username, password)
        .header("Content-type", MediaType.APPLICATION_XML)
        .body(newProviderConfigXML.toBytes(StandardCharsets.UTF_8.name()))
        .then()
        .statusCode(HttpStatus.SC_CREATED)
        .when().put(serviceUrl + "/" + newProviderConfigName);

    // Verify that the provider configuration was written to the expected location
    File newProviderConfigFile =
                  new File(new File(config.getGatewayConfDir(), "shared-providers"), newProviderConfigFileName);
    assertTrue(newProviderConfigFile.exists());

    // Request a listing of all the provider configs to further verify the PUT
    ResponseBody responseBody = given()
                                  .auth().preemptive().basic(username, password)
                                  .header("Accept", MediaType.APPLICATION_JSON)
                                  .then()
                                  .statusCode(HttpStatus.SC_OK)
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .when().get(serviceUrl).body();
    List<String> items = responseBody.path("items");
    assertEquals(1, items.size());
    assertEquals(newProviderConfigFileName, responseBody.path("items[0].name"));
    String href = responseBody.path("items[0].href");

    // Get the new provider config content
    responseBody = given()
                      .auth().preemptive().basic(username, password)
                      .header("Accept", MediaType.APPLICATION_XML)
                      .then()
                      .statusCode(HttpStatus.SC_OK)
                      .contentType(MediaType.APPLICATION_XML)
                      .when().get(href).body();
    String configContent = responseBody.asString();

    // Parse the result, to make sure it's at least valid XML
    XmlUtils.readXml(new InputSource(new StringReader(configContent)));

    // Manually delete the provider config
    newProviderConfigFile.delete();

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPutProviderConfigurationWithInvalidName() throws Exception {
    LOG_ENTER();

    final String username = "admin";
    final String password = "admin-password";
    final String serviceUrl = clusterUrl + "/api/v1/providerconfig";

    final String newProviderConfigName     = "new-provider&config";
    final String newProviderConfigFileName = newProviderConfigName + ".xml";

    XMLTag newProviderConfigXML = createProviderConfiguration();

    // Attempt to PUT a provider config
    given().auth().preemptive().basic(username, password)
           .header("Content-type", MediaType.APPLICATION_XML)
           .body(newProviderConfigXML.toBytes(StandardCharsets.UTF_8.name()))
           .then()
           .statusCode(HttpStatus.SC_BAD_REQUEST)
           .when().put(serviceUrl + "/" + newProviderConfigName);

    // Verify that the provider configuration was written to the expected location
    File newProviderConfigFile =
        new File(new File(config.getGatewayConfDir(), "shared-providers"), newProviderConfigFileName);
    assertFalse(newProviderConfigFile.exists());

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testDeleteProviderConfiguration() throws Exception {
    LOG_ENTER();

    final String username = "admin";
    final String password = "admin-password";
    final String serviceUrl = clusterUrl + "/api/v1/providerconfig";

    final File sharedProvidersDir = new File(config.getGatewayConfDir(), "shared-providers");

    // Manually add two provider config files to the shared-providers directory
    File providerConfigOneFile = new File(sharedProvidersDir, "deleteme-one-config.xml");
    try(OutputStream stream = Files.newOutputStream(providerConfigOneFile.toPath())) {
      createProviderConfiguration().toStream(stream);
    }
    assertTrue(providerConfigOneFile.exists());

    File providerConfigTwoFile = new File(sharedProvidersDir, "deleteme-two-config.xml");
    try(OutputStream stream = Files.newOutputStream(providerConfigTwoFile.toPath())) {
      createProviderConfiguration().toStream(stream);
    }
    assertTrue(providerConfigTwoFile.exists());

    // Request a listing of all the provider configs
    ResponseBody responseBody = given()
                                  .auth().preemptive().basic(username, password)
                                  .header("Accept", MediaType.APPLICATION_JSON)
                                  .then()
                                  .statusCode(HttpStatus.SC_OK)
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .when().get(serviceUrl).body();
    List<String> items = responseBody.path("items");
    assertEquals(2, items.size());
    String name1 = responseBody.path("items[0].name");
    String href1 = responseBody.path("items[0].href");
    String name2 = responseBody.path("items[1].name");
    String href2 = responseBody.path("items[1].href");

    // Delete one of the provider configs
    responseBody = given()
                    .auth().preemptive().basic(username, password)
                    .header("Accept", MediaType.APPLICATION_JSON)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .when().delete(href1).body();
    String deletedMsg = responseBody.path("deleted");
    assertEquals("provider config " + FilenameUtils.getBaseName(name1), deletedMsg);
    assertFalse((new File(sharedProvidersDir, name1)).exists());

    assertTrue((new File(sharedProvidersDir, name2)).exists());
    // Delete the other provider config
    responseBody = given()
                    .auth().preemptive().basic(username, password)
                    .header("Accept", MediaType.APPLICATION_JSON)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .when().delete(href2).body();
    deletedMsg = responseBody.path("deleted");
    assertEquals("provider config " + FilenameUtils.getBaseName(name2), deletedMsg);
    assertFalse((new File(sharedProvidersDir, name2)).exists());

    // Attempt to delete a provider config that does not exist
    given()
      .auth().preemptive().basic(username, password)
      .header("Accept", MediaType.APPLICATION_JSON)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .when().delete(serviceUrl + "/does-not-exist");

    LOG_EXIT();
  }

  /*
   * KNOX-1176
   */
  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testDeleteReferencedProviderConfiguration() throws Exception {
    LOG_ENTER();

    final String username = "admin";
    final String password = "admin-password";
    final String serviceUrl = clusterUrl + "/api/v1/providerconfig";
    final String descriptorsUrl = clusterUrl + "/api/v1/descriptors";

    final File sharedProvidersDir = new File(config.getGatewayConfDir(), "shared-providers");

    // Manually add two provider config files to the shared-providers directory
    File providerConfigOneFile = new File(sharedProvidersDir, "deleteme-one-config.xml");
    try(OutputStream stream = Files.newOutputStream(providerConfigOneFile.toPath())) {
      createProviderConfiguration().toStream(stream);
    }
    assertTrue(providerConfigOneFile.exists());

    File providerConfigTwoFile = new File(sharedProvidersDir, "deleteme-two-config.xml");
    try(OutputStream stream = Files.newOutputStream(providerConfigTwoFile.toPath())) {
      createProviderConfiguration().toStream(stream);
    }
    assertTrue(providerConfigTwoFile.exists());

    // Request a listing of all the provider configs
    ResponseBody responseBody = given()
                                  .auth().preemptive().basic(username, password)
                                  .header("Accept", MediaType.APPLICATION_JSON)
                                  .then()
                                  .statusCode(HttpStatus.SC_OK)
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .when().get(serviceUrl).body();
    List<String> items = responseBody.path("items");
    assertEquals(2, items.size());
    String name1 = responseBody.path("items[0].name");
    String href1 = responseBody.path("items[0].href");
    String name2 = responseBody.path("items[1].name");
    String href2 = responseBody.path("items[1].href");

    /////////////////////////////////////////////////////////////
    // PUT a descriptor, which references one of the provider configurations, which should make that provider
    // configuration ineligible for deletion.
    String descriptorName = "mycluster";
    String newDescriptorJSON = createDescriptor(descriptorName, name1, false);
    given()
        .auth().preemptive().basic(username, password)
        .header("Content-type", MediaType.APPLICATION_JSON)
        .body(newDescriptorJSON.getBytes(StandardCharsets.UTF_8.name()))
        .then()
        .statusCode(HttpStatus.SC_CREATED)
        .when().put(descriptorsUrl + "/" + descriptorName);

    try { // Wait for the reference relationship to be established
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      //
    }

    // Attempt to delete the referenced provider configs, and verify that it is NOT deleted
    given()
        .auth().preemptive().basic(username, password)
        .then()
        .statusCode(HttpStatus.SC_NOT_MODIFIED)
        .when().delete(href1).body();
    assertTrue("Provider config deletion should have been prevented.", (new File(sharedProvidersDir, name1)).exists());

    /////////////////////////////////////////////////////////////
    // Update the descriptor to reference the other provider config, such that the first one should become eligible for
    // deletion.
    String updatedDescriptorJSON = createDescriptor(descriptorName, name2, false);
    given()
        .auth().preemptive().basic(username, password)
        .header("Content-type", MediaType.APPLICATION_JSON)
        .body(updatedDescriptorJSON.getBytes(StandardCharsets.UTF_8.name()))
        .then()
        .statusCode(HttpStatus.SC_NO_CONTENT)
        .when().put(descriptorsUrl + "/" + descriptorName);

    try { // Wait for the reference relationship to be established
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      //
    }

    // Delete the originally-referenced provider config, and verify that it has been deleted
    responseBody = given()
                    .auth().preemptive().basic(username, password)
                    .header("Accept", MediaType.APPLICATION_JSON)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .when().delete(href1).body();
    String deletedMsg = responseBody.path("deleted");
    assertEquals("provider config " + FilenameUtils.getBaseName(name1), deletedMsg);
    assertFalse((new File(sharedProvidersDir, name1)).exists());

    /////////////////////////////////////////////////////////////
    // (KNOX-1176) Update the descriptor to reference an invalid provider config, which should remove the prior
    // provider configuration reference relationship (even though the new reference could not be resolved), and make
    // that previously-referenced provider configuration eligible for deletion.
    updatedDescriptorJSON = createDescriptor(descriptorName, "invalid-provider-config", false);
    given()
        .auth().preemptive().basic(username, password)
        .header("Content-type", MediaType.APPLICATION_JSON)
        .body(updatedDescriptorJSON.getBytes(StandardCharsets.UTF_8.name()))
        .then()
        .statusCode(HttpStatus.SC_NO_CONTENT)
        .when().put(descriptorsUrl + "/" + descriptorName);

    try { // Wait for the reference relationship to be established
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      //
    }

    // Delete the previously-referenced provider config, and verify that it has been deleted
    responseBody = given()
                    .auth().preemptive().basic(username, password)
                    .header("Accept", MediaType.APPLICATION_JSON)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .when().delete(href2).body();
    deletedMsg = responseBody.path("deleted");
    assertEquals("provider config " + FilenameUtils.getBaseName(name2), deletedMsg);
    assertFalse((new File(sharedProvidersDir, name2)).exists());

    LOG_EXIT();
  }

  /*
   * KNOX-1331
   */
  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testForceDeleteReferencedProviderConfiguration() throws Exception {
    LOG_ENTER();

    final String username = "admin";
    final String password = "admin-password";
    final String serviceUrl = clusterUrl + "/api/v1/providerconfig";
    final String descriptorsUrl = clusterUrl + "/api/v1/descriptors";

    final File sharedProvidersDir = new File(config.getGatewayConfDir(), "shared-providers");

    // Manually add two provider config files to the shared-providers directory
    File providerConfigOneFile = new File(sharedProvidersDir, "force-deleteme-one-config.xml");
    try(OutputStream stream = Files.newOutputStream(providerConfigOneFile.toPath())) {
      createProviderConfiguration().toStream(stream);
    }
    assertTrue(providerConfigOneFile.exists());

    File providerConfigTwoFile = new File(sharedProvidersDir, "force-deleteme-two-config.xml");
    try(OutputStream stream = Files.newOutputStream(providerConfigTwoFile.toPath())) {
      createProviderConfiguration().toStream(stream);
    }
    assertTrue(providerConfigTwoFile.exists());

    // Request a listing of all the provider configs
    ResponseBody responseBody = given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_JSON)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType(MediaType.APPLICATION_JSON)
        .when().get(serviceUrl).body();
    List<String> items = responseBody.path("items");
    assertEquals(2, items.size());
    String name1 = responseBody.path("items[0].name");
    String href1 = responseBody.path("items[0].href");
    String name2 = responseBody.path("items[1].name");
    String href2 = responseBody.path("items[1].href");

    /////////////////////////////////////////////////////////////
    // PUT a descriptor, which references one of the provider configurations, which should make that provider
    // configuration ineligible for deletion.
    String descriptorName = "mycluster2";
    String newDescriptorJSON = createDescriptor(descriptorName, name1, false);
    given()
        .auth().preemptive().basic(username, password)
        .header("Content-type", MediaType.APPLICATION_JSON)
        .body(newDescriptorJSON.getBytes(StandardCharsets.UTF_8.name()))
        .then()
        .statusCode(HttpStatus.SC_CREATED)
        .when().put(descriptorsUrl + "/" + descriptorName);

    try { // Wait for the reference relationship to be established
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      //
    }

    // Attempt to delete the referenced provider configs, and verify that it is NOT deleted
    given()
        .auth().preemptive().basic(username, password)
        .then()
        .statusCode(HttpStatus.SC_NOT_MODIFIED)
        .when().delete(href1).body();
    assertTrue("Provider config deletion should have been prevented.", (new File(sharedProvidersDir, name1)).exists());

    /////////////////////////////////////////////////////////////
    // (KNOX-1331)
    // Attempt to delete the referenced provider config with the force query param, and verify that it has been deleted
    responseBody = given()
                      .auth().preemptive().basic(username, password)
                      .header("Accept", MediaType.APPLICATION_JSON)
                      .then()
                      .statusCode(HttpStatus.SC_OK)
                      .contentType(MediaType.APPLICATION_JSON)
                      .when().delete(href1 + "?force=true").body();
    String deletedMsg = responseBody.path("deleted");
    assertEquals("provider config " + FilenameUtils.getBaseName(name1), deletedMsg);
    assertFalse((new File(sharedProvidersDir, name1)).exists());

    /////////////////////////////////////////////////////////////
    // Update the descriptor to reference the other provider config
    String updatedDescriptorJSON = createDescriptor(descriptorName, name2, false);
    given()
        .auth().preemptive().basic(username, password)
        .header("Content-type", MediaType.APPLICATION_JSON)
        .body(updatedDescriptorJSON.getBytes(StandardCharsets.UTF_8.name()))
        .then()
        .statusCode(HttpStatus.SC_NO_CONTENT)
        .when().put(descriptorsUrl + "/" + descriptorName);

    try { // Wait for the reference relationship to be established
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      //
    }

    /////////////////////////////////////////////////////////////
    // (KNOX-1331)
    // Delete the referenced provider config with the force query param, and verify that it has been deleted
    responseBody = given()
                    .auth().preemptive().basic(username, password)
                    .header("Accept", MediaType.APPLICATION_JSON)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .when().delete(href2 + "?force=true").body();
    deletedMsg = responseBody.path("deleted");
    assertEquals("provider config " + FilenameUtils.getBaseName(name2), deletedMsg);
    assertFalse((new File(sharedProvidersDir, name2)).exists());

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testDescriptorCollection() throws Exception {
    LOG_ENTER();

    final String username = "admin";
    final String password = "admin-password";
    final String serviceUrl = clusterUrl + "/api/v1/descriptors";

    final File descriptorsDir = new File(config.getGatewayConfDir(), "descriptors");
    final List<String> clusterNames        = Arrays.asList("clusterOne", "clusterTwo");
    final List<String> descriptorNames     = Arrays.asList("test-descriptor-one", "test-descriptor-two");
    final List<String> descriptorFileNames = Arrays.asList(descriptorNames.get(0) + ".json",
                                                           descriptorNames.get(1) + ".json");

    // Request a listing of all the descriptors with an INCORRECT Accept header
    given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_XML)
        .then()
        .statusCode(HttpStatus.SC_NOT_ACCEPTABLE)
        .when().get(serviceUrl);

    // Request a listing of all the descriptors (with the CORRECT Accept header)
    ResponseBody responseBody = given()
                                  .auth().preemptive().basic(username, password)
                                  .header("Accept", MediaType.APPLICATION_JSON)
                                  .then()
                                  .statusCode(HttpStatus.SC_OK)
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .when().get(serviceUrl).body();
    List<String> items = responseBody.path("items");
    assertTrue("Expected no items since the descriptors dir is empty.", items.isEmpty());

    // Manually write a file to the descriptors directory
    File descriptorOneFile = new File(descriptorsDir, descriptorFileNames.get(0));
    FileUtils.write(descriptorOneFile, createDescriptor(clusterNames.get(0)), StandardCharsets.UTF_8);

    // Request a listing of all the descriptors
    responseBody = given()
                    .auth().preemptive().basic(username, password)
                    .header("Accept", MediaType.APPLICATION_JSON)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .when().get(serviceUrl).body();
    items = responseBody.path("items");
    assertEquals("Expected items to include the new file in the shared-providers dir.", 1, items.size());
    assertEquals(descriptorFileNames.get(0), responseBody.path("items[0].name"));
    String href1 = responseBody.path("items[0].href");

    // Manually write another file to the descriptors directory
    File descriptorTwoFile = new File(descriptorsDir, descriptorFileNames.get(1));
    FileUtils.write(descriptorTwoFile, createDescriptor(clusterNames.get(1)), StandardCharsets.UTF_8);

    // Request a listing of all the descriptors
    responseBody = given()
                    .auth().preemptive().basic(username, password)
                    .header("Accept", MediaType.APPLICATION_JSON)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .when().get(serviceUrl).body();
    items = responseBody.path("items");
    assertEquals(2, items.size());
    String descOne = responseBody.path("items[0].name");
    String descTwo = responseBody.path("items[1].name");
    assertTrue(descriptorFileNames.contains(descOne));
    assertTrue(descriptorFileNames.contains(descTwo));

    // Request a specific descriptor with an INCORRECT Accept header
    given()
        .auth().preemptive().basic(username, password)
        .header("Accept", MediaType.APPLICATION_XML)
        .then()
        .statusCode(HttpStatus.SC_NOT_ACCEPTABLE)
        .when().get(href1).body();

    // Request a specific descriptor (with the CORRECT Accept header)
    responseBody = given()
                    .auth().preemptive().basic(username, password)
                    .header("Accept", MediaType.APPLICATION_JSON)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .when().get(href1).body();
    String cluster = responseBody.path("cluster");
    assertEquals(cluster, clusterNames.get(0));

    // Request a specific descriptor, which does NOT exist
    given()
      .auth().preemptive().basic(username, password)
      .header("Accept", MediaType.APPLICATION_JSON)
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND)
      .when().get(serviceUrl + "/not-a-real-descriptor").body();

    descriptorOneFile.delete();
    descriptorTwoFile.delete();

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPutDescriptor() throws Exception {
    LOG_ENTER();

    final String username = "admin";
    final String password = "admin-password";
    final String serviceUrl = clusterUrl + "/api/v1/descriptors";

    final String clusterName           = "test-cluster";
    final String newDescriptorName     = "new-descriptor";
    final String newDescriptorFileName = newDescriptorName + ".json";

    String newDescriptorJSON = createDescriptor(clusterName);

    // Attempt to PUT a descriptor with an INCORRECT Content-type header
    given()
      .auth().preemptive().basic(username, password)
      .header("Content-type", MediaType.APPLICATION_XML)
      .body(newDescriptorJSON.getBytes(StandardCharsets.UTF_8.name()))
      .then()
      .statusCode(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE)
      .when().put(serviceUrl + "/" + newDescriptorName);

    // Attempt to PUT a descriptor with the CORRECT Content-type header
    given()
      .auth().preemptive().basic(username, password)
      .header("Content-type", MediaType.APPLICATION_JSON)
      .body(newDescriptorJSON.getBytes(StandardCharsets.UTF_8.name()))
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .when().put(serviceUrl + "/" + newDescriptorName);

    // Verify that the descriptor was written to the expected location
    File newDescriptorFile =
            new File(new File(config.getGatewayConfDir(), "descriptors"), newDescriptorFileName);
    assertTrue(newDescriptorFile.exists());

    // Request a listing of all the descriptors to verify the PUT
    ResponseBody responseBody = given()
                                  .auth().preemptive().basic(username, password)
                                  .header("Accept", MediaType.APPLICATION_JSON)
                                  .then()
                                  .statusCode(HttpStatus.SC_OK)
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .when().get(serviceUrl).body();
    List<String> items = responseBody.path("items");
    assertEquals(1, items.size());
    assertEquals(newDescriptorFileName, responseBody.path("items[0].name"));
    String href = responseBody.path("items[0].href");

    // Get the new descriptor content
    responseBody = given()
                    .auth().preemptive().basic(username, password)
                    .header("Accept", MediaType.APPLICATION_JSON)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .when().get(href).body();
    String cluster = responseBody.path("cluster");
    assertEquals(clusterName, cluster);

    // Manually delete the descriptor
    newDescriptorFile.delete();

    LOG_EXIT();
  }

  @Test
  public void testPutDescriptorWithValidEncodedName() throws Exception {

    final String encodedName = "new%2Ddescriptor";
    final String newDescriptorName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name());

    final String username = "admin";
    final String password = "admin-password";
    final String serviceUrl = clusterUrl + "/api/v1/descriptors";

    final String clusterName           = "test-cluster";
    final String newDescriptorFileName = newDescriptorName + ".json";

    String newDescriptorJSON = createDescriptor(clusterName);

    // Attempt to PUT the descriptor
    given().auth().preemptive().basic(username, password)
           .header("Content-type", MediaType.APPLICATION_JSON)
           .body(newDescriptorJSON.getBytes(StandardCharsets.UTF_8.name()))
           .then()
           .statusCode(HttpStatus.SC_CREATED)
           .when().put(serviceUrl + "/" + encodedName);

    // Verify that the descriptor was written to the expected location
    File newDescriptorFile = new File(new File(config.getGatewayConfDir(), "descriptors"), newDescriptorFileName);
    assertTrue(newDescriptorFile.exists());

    // Request a listing of all the descriptors to verify that the PUT FAILED
    ResponseBody responseBody = given().auth().preemptive().basic(username, password)
                                       .header("Accept", MediaType.APPLICATION_JSON)
                                       .then()
                                       .statusCode(HttpStatus.SC_OK)
                                       .contentType(MediaType.APPLICATION_JSON)
                                       .when().get(serviceUrl).body();
    assertNotNull(responseBody);
    boolean isCreated = false;
    List<Map<String, String>> items = responseBody.path("items");
    for (Map<String, String> item : items) {
      if(item.get("name").equals(newDescriptorFileName)) {
        isCreated = true;
        break;
      }
    }
    assertTrue(isCreated);

    newDescriptorFile.delete();
  }

  @Test
  public void testPutDescriptorWithFileExtension() throws Exception {

    final String newDescriptorName = "newdescriptor";

    final String username = "admin";
    final String password = "admin-password";
    final String serviceUrl = clusterUrl + "/api/v1/descriptors";

    final String clusterName           = "test-cluster";
    final String newDescriptorFileName = newDescriptorName + ".json";

    String newDescriptorJSON = createDescriptor(clusterName);

    // Attempt to PUT the descriptor
    given().auth().preemptive().basic(username, password)
           .header("Content-type", MediaType.APPLICATION_JSON)
           .body(newDescriptorJSON.getBytes(StandardCharsets.UTF_8.name()))
           .then()
           .statusCode(HttpStatus.SC_CREATED)
           .when().put(serviceUrl + "/" + newDescriptorFileName);

    // Verify that the descriptor was written to the expected location
    File newDescriptorFile = new File(new File(config.getGatewayConfDir(), "descriptors"), newDescriptorFileName);
    assertTrue(newDescriptorFile.exists());

    // Request a listing of all the descriptors to verify that the PUT FAILED
    ResponseBody responseBody = given().auth().preemptive().basic(username, password)
                                       .header("Accept", MediaType.APPLICATION_JSON)
                                       .then()
                                       .statusCode(HttpStatus.SC_OK)
                                       .contentType(MediaType.APPLICATION_JSON)
                                       .when().get(serviceUrl).body();
    assertNotNull(responseBody);
    boolean isCreated = false;
    List<Map<String, String>> items = responseBody.path("items");
    for (Map<String, String> item : items) {
      if(item.get("name").equals(newDescriptorFileName)) {
        isCreated = true;
        break;
      }
    }
    assertTrue(isCreated);

    newDescriptorFile.delete();
  }

  @Test
  public void testPutDescriptorWithInvalidEncodedName() throws Exception {

    final String encodedName = "'';!--%22%3CXSS%3E=&%7B()%7D";
    final String newDescriptorName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name());

    final String username = "admin";
    final String password = "admin-password";
    final String serviceUrl = clusterUrl + "/api/v1/descriptors";

    final String clusterName           = "test-cluster";
    final String newDescriptorFileName = newDescriptorName + ".json";

    String newDescriptorJSON = createDescriptor(clusterName);

    // Attempt to PUT the descriptor
    given().auth().preemptive().basic(username, password)
           .header("Content-type", MediaType.APPLICATION_JSON)
           .body(newDescriptorJSON.getBytes(StandardCharsets.UTF_8.name()))
           .then()
           .statusCode(HttpStatus.SC_BAD_REQUEST)
           .when().put(serviceUrl + "/" + encodedName);

    // Verify that the descriptor was NOT written to the expected location
    File newDescriptorFile = new File(new File(config.getGatewayConfDir(), "descriptors"), newDescriptorFileName);
    assertFalse(newDescriptorFile.exists());
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPutExistingGeneratedDescriptorFails() throws Exception {
    LOG_ENTER();
    try {
      gateway.stop();
      GatewayTestConfig conf = new GatewayTestConfig();
      String topologyName = "test-desc";
      conf.set(GatewayConfigImpl.READ_ONLY_OVERRIDE_TOPOLOGIES, topologyName);
      setupGateway(conf);
      given()
              .auth().preemptive().basic("admin", "admin-password")
              .header("Content-type", MediaType.APPLICATION_JSON)
              .body("{}")
              .then()
              .statusCode(HttpStatus.SC_CREATED)
              .when().put((clusterUrl + "/api/v1/descriptors/" + topologyName));
      given()
              .auth().preemptive().basic("admin", "admin-password")
              .header("Content-type", MediaType.APPLICATION_JSON)
              .body("new content")
              .then()
              .statusCode(HttpStatus.SC_CONFLICT)
              .when().put((clusterUrl + "/api/v1/descriptors/" + topologyName));
    } finally {
      // Restart the gateway with old settings.
      gateway.stop();
      setupGateway(new GatewayTestConfig());
    }

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPutExistingGeneratedProviderFails() throws Exception {
    LOG_ENTER();
    try {
      gateway.stop();
      GatewayTestConfig conf = new GatewayTestConfig();
      String providerName = "sso";
      conf.set(GatewayConfigImpl.READ_ONLY_OVERRIDE_PROVIDERS, providerName);
      setupGateway(conf);
      given()
              .auth().preemptive().basic("admin", "admin-password")
              .header("Content-type", MediaType.APPLICATION_JSON)
              .body("{}")
              .then()
              .statusCode(HttpStatus.SC_CREATED)
              .when()
              .put((clusterUrl + "/api/v1/providerconfig/" + providerName));
      given()
        .auth().preemptive().basic("admin", "admin-password")
        .header("Content-type", MediaType.APPLICATION_JSON)
        .body("new content")
      .then()
        .statusCode(HttpStatus.SC_CONFLICT)
        .when()
      .put((clusterUrl + "/api/v1/providerconfig/" + providerName));
    } finally {
      // Restart the gateway with old settings.
      gateway.stop();
      setupGateway(new GatewayTestConfig());
    }

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPutDescriptorWithInvalidNameEncodedElement() throws Exception {
    LOG_ENTER();

    doTestPutDescriptorWithInvalidName("new&lt;descriptor&gt;");

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPutDescriptorWithInvalidNamePercent() throws Exception {
    LOG_ENTER();

    doTestPutDescriptorWithInvalidName("newdes%criptor");

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPutDescriptorWithInvalidNameXMLElement() throws Exception {
    LOG_ENTER();

    doTestPutDescriptorWithInvalidName("new<descriptor>");

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testPutDescriptorWithInvalidNameTooLong() throws Exception {
    LOG_ENTER();

    String descName = "newDescriptor";
    while (descName.length() < 101) {
      descName += descName;
    }

    doTestPutDescriptorWithInvalidName(descName);

    LOG_EXIT();
  }

  private void doTestPutDescriptorWithInvalidName(final String newDescriptorName) throws Exception {

    assertNotNull(newDescriptorName);

    final String username = "admin";
    final String password = "admin-password";
    final String serviceUrl = clusterUrl + "/api/v1/descriptors";

    final String clusterName           = "test-cluster";
    final String newDescriptorFileName = newDescriptorName + ".json";

    String newDescriptorJSON = createDescriptor(clusterName);

    // Attempt to PUT the descriptor
    given().auth().preemptive().basic(username, password)
           .header("Content-type", MediaType.APPLICATION_JSON)
           .body(newDescriptorJSON.getBytes(StandardCharsets.UTF_8.name()))
           .then()
           .statusCode(HttpStatus.SC_BAD_REQUEST)
           .when().put(serviceUrl + "/" + newDescriptorName);

    // Verify that the descriptor was written to the expected location
    File newDescriptorFile = new File(new File(config.getGatewayConfDir(), "descriptors"), newDescriptorFileName);
    assertFalse(newDescriptorFile.exists());

    // Request a listing of all the descriptors to verify that the PUT FAILED
    ResponseBody responseBody = given().auth().preemptive().basic(username, password)
                                       .header("Accept", MediaType.APPLICATION_JSON)
                                       .then()
                                       .statusCode(HttpStatus.SC_OK)
                                       .contentType(MediaType.APPLICATION_JSON)
                                       .when().get(serviceUrl).body();
    assertNotNull(responseBody);
    List<Map<String, String>> items = responseBody.path("items");
    for (Map<String, String> item : items) {
      assertNotEquals(item.get("name"), newDescriptorName);
    }
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testDeleteDescriptor() throws Exception {
    LOG_ENTER();

    final String username = "admin";
    final String password = "admin-password";
    final String serviceUrl = clusterUrl + "/api/v1/descriptors";

    final File descriptorsDir = new File(config.getGatewayConfDir(), "descriptors");

    // Manually add two descriptor files to the descriptors directory
    File descriptorOneFile = new File(descriptorsDir, "deleteme-one.json");
    FileUtils.writeStringToFile(descriptorOneFile, createDescriptor("clusterOne"), StandardCharsets.UTF_8);
    assertTrue(descriptorOneFile.exists());

    File descriptorTwoFile = new File(descriptorsDir, "deleteme-two.json");
    FileUtils.writeStringToFile(descriptorTwoFile, createDescriptor("clusterTwo"), StandardCharsets.UTF_8);
    assertTrue(descriptorTwoFile.exists());

    // Request a listing of all the descriptors
    ResponseBody responseBody = given()
                                  .auth().preemptive().basic(username, password)
                                  .header("Accept", MediaType.APPLICATION_JSON)
                                  .then()
                                  .statusCode(HttpStatus.SC_OK)
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .when().get(serviceUrl).body();
    List<String> items = responseBody.path("items");
    assertEquals(2, items.size());
    String name1 = responseBody.path("items[0].name");
    String href1 = responseBody.path("items[0].href");
    String name2 = responseBody.path("items[1].name");
    String href2 = responseBody.path("items[1].href");

    // Delete one of the descriptors
    responseBody = given()
                    .auth().preemptive().basic(username, password)
                    .header("Accept", MediaType.APPLICATION_JSON)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .when().delete(href1).body();
    String deletedMsg = responseBody.path("deleted");
    assertEquals("descriptor " + FilenameUtils.getBaseName(name1), deletedMsg);
    assertFalse((new File(descriptorsDir, name1)).exists());

    assertTrue((new File(descriptorsDir, name2)).exists());
    // Delete the other descriptor
    responseBody = given()
                    .auth().preemptive().basic(username, password)
                    .header("Accept", MediaType.APPLICATION_JSON)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .when().delete(href2).body();
    deletedMsg = responseBody.path("deleted");
    assertEquals("descriptor " + FilenameUtils.getBaseName(name2), deletedMsg);
    assertFalse((new File(descriptorsDir, name2).exists()));

    // Attempt to delete a descriptor that does not exist
    given()
      .auth().preemptive().basic(username, password)
      .header("Accept", MediaType.APPLICATION_JSON)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .when().delete(serviceUrl + "/does-not-exist");

    LOG_EXIT();
  }
}

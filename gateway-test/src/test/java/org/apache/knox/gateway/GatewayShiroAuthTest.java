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
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.hamcrest.MatcherAssert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;

public class GatewayShiroAuthTest {
    private static final Logger LOG = LoggerFactory.getLogger(GatewayShiroAuthTest.class);
    private static final String SHIRO_URL_PATTERN_VALID = "/**";
    private static final String SHIRO_URL_PATTERN_INVALID = "/invalid/**";
    private static final String USERNAME = "guest";
    private static final String PASSWORD = "guest-password";
    private static final String TOPOLOGY_VALID = "shiro-test-cluster";
    private static final String TOPOLOGY_IN_VALID_URL = "shiro-test-cluster-invalid";
    private static final String TOPOLOGY_BLOCK_UNSAFE_CHARS = "shiro-test-cluster-unsafe";
    private static final String SERVICE_RESOURCE_NAME = "/test-service-path/test-service-resource";
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
        LOG_EXIT();
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

        File descDir = new File(testConfig.getGatewayDescriptorsDir());
        descDir.mkdirs();

        File provConfDir = new File(testConfig.getGatewayProvidersConfigDir());
        provConfDir.mkdirs();

        File deployDir = new File(testConfig.getGatewayDeploymentDir());
        deployDir.mkdirs();

        /* create a topology with valid shiro url */
        File descriptor = new File(topoDir, TOPOLOGY_VALID+".xml");
        try (OutputStream stream = Files.newOutputStream(descriptor.toPath())) {
            createTopology(SHIRO_URL_PATTERN_VALID, false).toStream(stream);
        }

        /* create a topology with in-valid shiro url */
        File invalid_descriptor = new File(topoDir, TOPOLOGY_IN_VALID_URL+".xml");
        try (OutputStream stream = Files.newOutputStream(invalid_descriptor.toPath())) {
            createTopology(SHIRO_URL_PATTERN_INVALID, false ).toStream(stream);
        }

        /* create a topology blocking characters deemed by shiro as unsafe - semicolon, backslash, non-ascii */
        File unsafe_descriptor = new File(topoDir, TOPOLOGY_BLOCK_UNSAFE_CHARS+".xml");
        try (OutputStream stream = Files.newOutputStream(unsafe_descriptor.toPath())) {
            createTopology(SHIRO_URL_PATTERN_VALID, true ).toStream(stream);
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
        clusterUrl = gatewayUrl + '/' + TOPOLOGY_VALID;
    }

    private static XMLTag createTopology(final String pattern, final boolean blockUnsafeCharacters) {
        if(!blockUnsafeCharacters) {
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
                    .addTag("value").addText(driver.getLdapUrl()).gotoParent()
                    .addTag("param")
                    .addTag("name").addText("main.ldapRealm.contextFactory.authenticationMechanism")
                    .addTag("value").addText("simple").gotoParent()
                    .addTag("param")
                    .addTag("name").addText("urls." + pattern)
                    .addTag("value").addText("authcBasic").gotoParent().gotoParent()
                    .addTag("provider")
                    .addTag("role").addText("identity-assertion")
                    .addTag("enabled").addText("true")
                    .addTag("name").addText("Default").gotoParent()
                    .addTag("provider")
                    .gotoRoot()
                    .addTag("service")
                    .addTag("role").addText("test-service-role")
                    .gotoRoot();
        } else {
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
                    // enable semicolon
                    .addTag("param")
                    .addTag("name").addText("main.invalidRequest.blockSemicolon")
                    .addTag("value").addText("true").gotoParent()
                    // enable backslash
                    .addTag("param")
                    .addTag("name").addText("main.invalidRequest.blockBackslash")
                    .addTag("value").addText("true").gotoParent()
                    //enable non-ascii
                    .addTag("param")
                    .addTag("name").addText("main.invalidRequest.blockNonAscii")
                    .addTag("value").addText("true").gotoParent()
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
                    .addTag("name").addText("urls." + pattern)
                    .addTag("value").addText("authcBasic").gotoParent().gotoParent()
                    .addTag("provider")
                    .addTag("role").addText("identity-assertion")
                    .addTag("enabled").addText("true")
                    .addTag("name").addText("Default").gotoParent()
                    .addTag("provider")
                    .gotoRoot()
                    .addTag("service")
                    .addTag("role").addText("test-service-role")
                    .gotoRoot();
        }
    }

    /**
     * Make sure semicolons are allowed in the URL out-of-the-box
     */
    @Test
    public void testValidShiro() {
        final String serviceUrl = clusterUrl + SERVICE_RESOURCE_NAME + ";jsessionid=OI24B9ASD7BSSD";
        final String expectedResponse = "test-service-response";
        testShiroAuthSuccess(serviceUrl, expectedResponse);
    }

    /**
     * Make sure semicolons are blocked in the URL using shiro global filter flags.
     * These params are passed via topology provider params overriding default Knox
     * options.
     */
    @Test
    public void testShiroBlockUnsafeCharacters() {
        final String serviceUrl = gatewayUrl + '/' + TOPOLOGY_BLOCK_UNSAFE_CHARS + SERVICE_RESOURCE_NAME +";jsessionid=OI24B9ASD7BSSD";
        testShiroAuthFailure(HttpStatus.SC_BAD_REQUEST, serviceUrl, "HTTP ERROR 400 Invalid request");
    }

    /**
     * Test a case where configured shiro url is invalid and there is auth failure.
     */
    @Test
    public void testInValidShiro() {
        final String serviceUrl = gatewayUrl + '/' + TOPOLOGY_IN_VALID_URL + SERVICE_RESOURCE_NAME;
        testShiroAuthFailure(HttpStatus.SC_INTERNAL_SERVER_ERROR, serviceUrl, "Unable to determine authenticated user from Shiro, please check that your Knox Shiro configuration is correct");
    }

    private void testShiroAuthFailure(final int failure_status_code, final String serviceUrl, final String reason) {
        given()
                .auth().preemptive().basic(USERNAME, USERNAME)
                .then()
                .statusCode(failure_status_code)
                .contentType("text/html")
                .body(containsString(reason))
                .when().get(serviceUrl);
    }

    private void testShiroAuthSuccess(final String serviceUrl, final String expectedResponse) {
        given()
                .auth().preemptive().basic(USERNAME, PASSWORD)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType("text/plain")
                // make sure valid use of semicolon
                .body(containsString(expectedResponse))
                .when().get(serviceUrl);
    }

}

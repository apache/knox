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
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.http.HttpStatus;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.security.ldap.SimpleLdapDirectoryServer;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.test.TestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hamcrest.MatcherAssert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;

public class GatewayHSTSTest {
    private static final Logger LOG = LogManager.getLogger(GatewayHSTSTest.class);

    public static GatewayConfig config;
    public static GatewayServer gateway;
    public static String gatewayUrl;
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

        File descDir = new File(testConfig.getGatewayDescriptorsDir());
        descDir.mkdirs();

        File provConfDir = new File(testConfig.getGatewayProvidersConfigDir());
        provConfDir.mkdirs();

        File deployDir = new File(testConfig.getGatewayDeploymentDir());
        deployDir.mkdirs();

        File strictDescriptor = new File(topoDir, "strict-cluster.xml");
        try (OutputStream stream = Files.newOutputStream(strictDescriptor.toPath())) {
            createTopology(true).toStream(stream);
        }

        File nonStrictDescriptor = new File(topoDir, "non-strict-cluster.xml");
        try (OutputStream stream = Files.newOutputStream(nonStrictDescriptor.toPath())) {
            createTopology(false).toStream(stream);
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
    }

    private static XMLTag createTopology(boolean strictTransport) {
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
                .addTag("role").addText("webappsec")
                .addTag("name").addText("WebAppSec")
                .addTag("enabled").addText("true")
                .addTag("param")
                .addTag("name").addText("strict.transport.enabled")
                .addTag("value").addText(String.valueOf(strictTransport)).gotoParent()
                .addTag("param")
                .addTag("name").addText("strict.transport")
                .addTag("value").addText("max-age=444; includeSubDomains").gotoParent()
                .gotoRoot()
                .addTag("service")
                .addTag("role").addText("HEALTH")
                .gotoRoot();
    }

    @Test(timeout = TestUtils.MEDIUM_TIMEOUT)
    public void testTopologyHSTSHeader404() {
        TestUtils.LOG_ENTER();
        String username = "guest";
        String password = "guest-password";
        String serviceUrl = gatewayUrl + "/strict-cluster/v1/not-exist";
        given()
                .auth().preemptive().basic(username, password)
                .header("Accept", MediaType.TEXT_PLAIN)
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND)
                .header("Strict-Transport-Security", "max-age=444; includeSubDomains")
                .contentType(MediaType.TEXT_PLAIN)
                .when().get(serviceUrl);
        TestUtils.LOG_EXIT();
    }

    @Test(timeout = TestUtils.MEDIUM_TIMEOUT)
    public void testTopologyHSTSHeader200() {
        TestUtils.LOG_ENTER();
        String username = "guest";
        String password = "guest-password";
        String serviceUrl = gatewayUrl + "/strict-cluster/v1/ping";
        given()
                .auth().preemptive().basic(username, password)
                .header("Accept", MediaType.TEXT_PLAIN)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .header("Strict-Transport-Security", "max-age=444; includeSubDomains")
                .contentType(MediaType.TEXT_PLAIN)
                .when().get(serviceUrl);
        TestUtils.LOG_EXIT();
    }

    @Test(timeout = TestUtils.MEDIUM_TIMEOUT)
    public void testGlobalHSTSHeader200() {
        TestUtils.LOG_ENTER();
        String username = "guest";
        String password = "guest-password";
        String serviceUrl = gatewayUrl + "/non-strict-cluster/v1/ping";
        given()
                .auth().preemptive().basic(username, password)
                .header("Accept", MediaType.TEXT_PLAIN)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .header("Strict-Transport-Security", "max-age=3001")
                .contentType(MediaType.TEXT_PLAIN)
                .when().get(serviceUrl);
        TestUtils.LOG_EXIT();
    }

    @Test(timeout = TestUtils.MEDIUM_TIMEOUT)
    public void testGlobalHSTSHeader404() {
        TestUtils.LOG_ENTER();
        String username = "guest";
        String password = "guest-password";
        String serviceUrl = gatewayUrl + "/non-strict-cluster/v1/not-exist";
        given()
                .auth().preemptive().basic(username, password)
                .header("Accept", MediaType.TEXT_PLAIN)
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND)
                .header("Strict-Transport-Security", "max-age=3001")
                .contentType(MediaType.TEXT_PLAIN)
                .when().get(serviceUrl);
        TestUtils.LOG_EXIT();
    }

    @Test(timeout = TestUtils.MEDIUM_TIMEOUT)
    public void testGlobalHSTSHeaderTopologyNotExist() {
        TestUtils.LOG_ENTER();
        String username = "guest";
        String password = "guest-password";
        String serviceUrl = gatewayUrl + "/not-exist/v1/not-exist";
        given()
                .auth().preemptive().basic(username, password)
                .header("Accept", MediaType.TEXT_PLAIN)
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND)
                .header("Strict-Transport-Security", "max-age=3001")
                .contentType(MediaType.TEXT_PLAIN)
                .when().get(serviceUrl);
        TestUtils.LOG_EXIT();
    }
}

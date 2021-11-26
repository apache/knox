/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway;

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.util.KnoxCLI;
import org.apache.knox.test.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class KnoxCliLdapFuncTestPositive {
  public static GatewayTestConfig config;
  public static GatewayServer gateway;
  public static String gatewayUrl;
  public static String clusterUrl;
  private static GatewayTestDriver driver = new GatewayTestDriver();

  private static final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private static final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
  private static final String uuid = UUID.randomUUID().toString();

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    LOG_ENTER();
    System.setOut(new PrintStream(outContent, false, StandardCharsets.UTF_8.name()));
    System.setErr(new PrintStream(errContent, false, StandardCharsets.UTF_8.name()));
    driver.setupLdap(0);
    setupGateway();
    LOG_EXIT();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    LOG_ENTER();
    driver.cleanup();
    LOG_EXIT();
  }

  public static void setupGateway() throws Exception {
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + uuid );
    gatewayDir.mkdirs();

    GatewayTestConfig testConfig = new GatewayTestConfig();
    config = testConfig;
    testConfig.setGatewayHomeDir( gatewayDir.getAbsolutePath() );

    File topoDir = new File( testConfig.getGatewayTopologyDir() );
    topoDir.mkdirs();

    File deployDir = new File( testConfig.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    createTopology(topoDir, "test-cluster.xml", true);
    createTopology(topoDir, "bad-cluster.xml", false);

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<>();
    options.put( "persist-master", "false" );
    options.put( "master", "password" );
    try {
      srvcs.init( testConfig, options );
    } catch ( ServiceLifecycleException e ) {
      e.printStackTrace(); // I18N not required.
    }
  }

  private static void createTopology(File topoDir, String name, boolean goodTopology) throws Exception {
    File descriptor = new File(topoDir, name);

    if(descriptor.exists()){
      descriptor.delete();
      descriptor = new File(topoDir, name);
    }

    try(OutputStream stream = Files.newOutputStream(descriptor.toPath())) {
      if (goodTopology) {
        createTopology().toStream(stream);
      } else {
        createBadTopology().toStream(stream);
      }
    }
  }

  private static XMLTag createBadTopology(){
    return XMLDoc.newDocument(true)
        .addRoot("topology")
        .addTag( "gateway" )
        .addTag("provider")
        .addTag("role").addText("authentication")
        .addTag("name").addText("ShiroProvider")
        .addTag("enabled").addText("true")
        .addTag( "param" )
        .addTag("name").addText("main.ldapRealm")
        .addTag("value").addText("org.apache.knox.gateway.shirorealm.KnoxLdapRealm").gotoParent()
        .addTag( "param" )
        .addTag("name").addText("main.ldapRealm.userDnTemplate")
        .addTag("value").addText("uid={0},ou=people,dc=hadoop,dc=apache,dc=org").gotoParent()
        .addTag( "param" )
        .addTag("name").addText("main.ldapRealm.contextFactory.url")
        .addTag("value").addText(driver.getLdapUrl()).gotoParent()
        .addTag( "param" )
        .addTag("name").addText("main.ldapRealm.contextFactory.authenticationMechanism")
        .addTag("value").addText("simple").gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.authorizationEnabled")
        .addTag("value").addText("true").gotoParent()
        .addTag("param")
        .addTag( "name").addText( "urls./**")
        .addTag("value").addText( "authcBasic" ).gotoParent().gotoParent()
        .addTag( "provider" )
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "Default" ).gotoParent()
        .gotoRoot()
        .addTag( "service")
        .addTag("role").addText( "KNOX" )
        .gotoRoot();
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
        .addTag("param" )
        .addTag("name").addText("main.ldapGroupContextFactory")
        .addTag("value").addText("org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory").gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.searchBase")
        .addTag("value").addText("ou=groups,dc=hadoop,dc=apache,dc=org").gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.groupObjectClass")
        .addTag("value").addText("groupOfNames").gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.memberAttributeValueTemplate")
        .addTag("value").addText("uid={0},ou=people,dc=hadoop,dc=apache,dc=org").gotoParent()
        .addTag("param" )
        .addTag("name").addText("main.ldapRealm.memberAttribute")
        .addTag("value").addText("member").gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.authorizationEnabled")
        .addTag("value").addText("true").gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.contextFactory.systemUsername")
        .addTag("value").addText("uid=guest,ou=people,dc=hadoop,dc=apache,dc=org").gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.contextFactory.systemPassword")
        .addTag( "value" ).addText("guest-password").gotoParent()
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
        .addTag("name" ).addText("urls./**")
        .addTag("value" ).addText("authcBasic").gotoParent().gotoParent()
        .addTag("provider" )
        .addTag("role").addText( "identity-assertion" )
        .addTag( "enabled").addText( "true" )
        .addTag("name").addText( "Default" ).gotoParent()
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "test-service-role" )
        .gotoRoot();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testLDAPAuth() throws Exception {
    LOG_ENTER();

//    Test 1: Make sure authenication is successful and return groups
    outContent.reset();
    String username = "sam";
    String password = "sam-password";
    String[] args = {"user-auth-test", "--master", "knox", "--cluster", "test-cluster", "--u", username, "--p", password,
        "--g"};
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(config);
    cli.run(args);
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("success"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("analyst"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("scientist"));

//    Test 2: Give an invalid name and password combinatinon.
    outContent.reset();
    cli = new KnoxCLI();
    cli.setConf(config);
    username = "bad-name";
    password = "bad-password";
    String[] args2 = {"user-auth-test", "--master", "knox", "--cluster", "test-cluster", "--u", username, "--p", password};
    cli.run(args2);
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("LDAP authentication failed"));

//    Test 3: Authenticate a user who belongs to no groups, but specify groups with --g
    outContent.reset();
    cli = new KnoxCLI();
    cli.setConf(config);
    username = "guest";
    password = "guest-password";
    String[] args3 = {"user-auth-test", "--master", "knox", "--cluster", "test-cluster",
        "--u", username, "--p", password, "--g" };
    cli.run(args3);
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("LDAP authentication success"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("does not belong to any groups"));

    //    Test 4: Pass a non-existent topology
    outContent.reset();
    cli = new KnoxCLI();
    cli.setConf(config);
    username = "guest";
    password = "guest-password";
    String[] args4 = {"user-auth-test", "--master", "knox", "--cluster", "cluster-dne",
        "--u", username, "--p", password };
    cli.run(args4);
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("Topology cluster-dne does not exist"));


    //    Test 5: Authenticate a user who belongs to no groups, but specify groups with --g
    outContent.reset();
    cli = new KnoxCLI();
    cli.setConf(config);
    username = "guest";
    password = "guest-password";
    String[] args5 = {"user-auth-test", "--master", "knox", "--cluster", "test-cluster",
        "--u", username, "--p", password };
    cli.run( args5 );
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("LDAP authentication success"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), not(containsString("does not belong to any groups")));

    LOG_EXIT();
  }
}

/**
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
package org.apache.hadoop.gateway;

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.hadoop.gateway.security.ldap.SimpleLdapDirectoryServer;
import org.apache.hadoop.gateway.services.DefaultGatewayServices;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.util.KnoxCLI;;
import org.apache.log4j.Appender;
import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class KnoxCliLdapFuncTestNegative {

  private static Class RESOURCE_BASE_CLASS = KnoxCliLdapFuncTestPositive.class;
  private static Logger LOG = LoggerFactory.getLogger( KnoxCliLdapFuncTestPositive.class );

  public static Enumeration<Appender> appenders;
  public static GatewayTestConfig config;
  public static GatewayServer gateway;
  public static String gatewayUrl;
  public static String clusterUrl;
  public static SimpleLdapDirectoryServer ldap;
  public static TcpTransport ldapTransport;

  private static final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private static final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
  private static final String uuid = UUID.randomUUID().toString();

  @BeforeClass
  public static void setupSuite() throws Exception {
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
    setupLdap();
    setupGateway();
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    ldap.stop( true );

    //FileUtils.deleteQuietly( new File( config.getGatewayHomeDir() ) );
    //NoOpAppender.tearDown( appenders );
  }

  public static void setupLdap( ) throws Exception {
    URL usersUrl = getResourceUrl( "users.ldif" );
    int port = findFreePort();
    ldapTransport = new TcpTransport( port );
    ldap = new SimpleLdapDirectoryServer( "dc=hadoop,dc=apache,dc=org", new File( usersUrl.toURI() ), ldapTransport );
    ldap.start();
    LOG.info( "LDAP port = " + ldapTransport.getPort() );
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
    Map<String,String> options = new HashMap<String,String>();
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

    FileOutputStream stream = new FileOutputStream( descriptor, false );
    if(goodTopology){
      createTopology().toStream( stream );
    } else {
      createBadTopology().toStream( stream );
    }
    stream.close();

  }

  private static int findFreePort() throws IOException {
    ServerSocket socket = new ServerSocket(0);
    int port = socket.getLocalPort();
    socket.close();
    return port;
  }

  public static InputStream getResourceStream( String resource ) throws IOException {
    return getResourceUrl( resource ).openStream();
  }

  public static URL getResourceUrl( String resource ) {
    URL url = ClassLoader.getSystemResource( getResourceName( resource ) );
    assertThat( "Failed to find test resource " + resource, url, Matchers.notNullValue() );
    return url;
  }

  public static String getResourceName( String resource ) {
    return getResourceBaseName() + resource;
  }

  public static String getResourceBaseName() {
    return RESOURCE_BASE_CLASS.getName().replaceAll( "\\.", "/" ) + "/";
  }

  private static XMLTag createBadTopology(){
    XMLTag xml = XMLDoc.newDocument(true)
        .addRoot("topology")
        .addTag("gateway")
        .addTag( "provider" )
        .addTag("role").addText("authentication")
        .addTag( "name" ).addText( "ShiroProvider" )
        .addTag( "enabled" ).addText( "true" )
        .addTag("param")
        .addTag( "name" ).addText("main.ldapRealm")
        .addTag("value").addText("org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm").gotoParent()
        .addTag("param")
        .addTag( "name" ).addText("main.ldapRealm.userDnTemplate")
        .addTag("value").addText("uid={0},ou=people,dc=hadoop,dc=apache,dc=org").gotoParent()
        .addTag("param")
        .addTag( "name" ).addText("main.ldapRealm.contextFactory.url")
        .addTag("value").addText("ldap://localhost:" + ldapTransport.getPort()).gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.contextFactory.systemUsername")
        .addTag("value").addText("uid=guest,ou=people,dc=hadoop,dc=apache,dc=org").gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.contextFactory.systemPassword")
        .addTag( "value" ).addText("guest-password").gotoParent()
        .addTag("param")
        .addTag( "name" ).addText("main.ldapRealm.contextFactory.authenticationMechanism")
        .addTag("value").addText("simple").gotoParent()
        .addTag("param")
        .addTag( "name" ).addText("urls./**")
        .addTag("value").addText("authcBasic").gotoParent().gotoParent()
        .addTag("provider")
        .addTag( "role" ).addText("identity-assertion")
        .addTag("enabled").addText("true")
        .addTag("name").addText("Default").gotoParent()
        .addTag("provider")
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "KNOX" )
        .gotoRoot();
    // System.out.println( "GATEWAY=" + xml.toString() );
    return xml;
  }

  private static XMLTag createTopology() {

    XMLTag xml = XMLDoc.newDocument(true)
        .addRoot("topology")
        .addTag("gateway" )
        .addTag("provider")
        .addTag("role").addText("authentication")
        .addTag("name").addText("ShiroProvider")
        .addTag("enabled").addText("true")
        .addTag("param")
        .addTag("name").addText("main.ldapRealm")
        .addTag("value").addText("org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm").gotoParent()
        .addTag("param" )
        .addTag("name").addText("main.ldapGroupContextFactory")
        .addTag("value").addText("org.apache.hadoop.gateway.shirorealm.KnoxLdapContextFactory").gotoParent()
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
        .addTag("value").addText("ldap://localhost:" + ldapTransport.getPort()).gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.contextFactory.authenticationMechanism")
        .addTag("value").addText("simple").gotoParent()
        .addTag("param")
        .addTag("name").addText("main.ldapRealm.cachingEnabled")
        .addTag("value").addText("false").gotoParent()
        .addTag("param")
        .addTag("name").addText("com.sun.jndi.ldap.connect.pool")
        .addTag("value").addText("false").gotoParent()
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
    // System.out.println( "GATEWAY=" + xml.toString() );
    return xml;
  }

  @Test
  public void testBadTopology() throws Exception {

    //    Test 4: Authenticate a user with a bad topology configured with nothing required for group lookup in the topology
    outContent.reset();
    String username = "tom";
    String password = "tom-password";
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(config);

    String args1[] = {"user-auth-test", "--master", "knox", "--cluster", "bad-cluster",
        "--u", username, "--p", password, "--g" };
    cli.run( args1 );

    assertThat(outContent.toString(), containsString("LDAP authentication successful"));
    assertThat(outContent.toString(), containsString("Your topology file may be incorrectly configured for group lookup"));
    assertThat(outContent.toString(), containsString("Warn: "));
    assertFalse(outContent.toString().contains("analyst"));


    outContent.reset();
    username = "bad-name";
    password = "bad-password";
    cli = new KnoxCLI();
    cli.setConf( config );

    String args2[] = {"user-auth-test", "--master", "knox", "--cluster", "bad-cluster",
        "--u", username, "--p", password, "--g" };
    cli.run( args2 );

    assertThat(outContent.toString(), containsString("LDAP authentication failed"));
    assertThat(outContent.toString(), containsString("INVALID_CREDENTIALS"));

    outContent.reset();
    username = "sam";
    password = "sam-password";
    cli = new KnoxCLI();
    cli.setConf( config );

    String args3[] = {"user-auth-test", "--master", "knox", "--cluster", "bad-cluster",
        "--u", username, "--p", password, "--g" };
    cli.run( args3 );

    assertThat(outContent.toString(), containsString("LDAP authentication successful"));
    assertThat(outContent.toString(), containsString("Your topology file may be incorrectly configured for group lookup"));
    assertThat(outContent.toString(), containsString("Warn:"));
    assertFalse(outContent.toString().contains("analyst"));
    assertFalse(outContent.toString().contains("scientist"));

  }

}

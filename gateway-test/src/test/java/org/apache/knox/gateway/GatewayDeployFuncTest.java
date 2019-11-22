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

import io.restassured.response.Response;
import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.DefaultGatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.category.ReleaseTest;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

@Category(ReleaseTest.class)
public class GatewayDeployFuncTest {
  private static final Logger LOG = LoggerFactory.getLogger( GatewayDeployFuncTest.class );

  public static GatewayConfig config;
  public static GatewayServer gateway;
  public static File gatewayHome;
  public static String gatewayUrl;
  public static String clusterUrl;
  private static GatewayTestDriver driver = new GatewayTestDriver();

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    LOG_ENTER();
    driver.setupLdap(0);
    LOG_EXIT();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    LOG_ENTER();
    driver.cleanup();
    LOG_EXIT();
  }

  @Before
  public void setupGateway() throws Exception {

    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();
    gatewayHome = gatewayDir;

    GatewayTestConfig testConfig = new GatewayTestConfig();
    config = testConfig;
    testConfig.setGatewayHomeDir( gatewayDir.getAbsolutePath() );

    File topoDir = new File( testConfig.getGatewayTopologyDir() );
    topoDir.mkdirs();

    File deployDir = new File( testConfig.getGatewayDeploymentDir() );
    deployDir.mkdirs();

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
    clusterUrl = gatewayUrl + "/test-cluster";
  }

  @After
  public void cleanupGateway() throws Exception {
    gateway.stop();
    FileUtils.deleteQuietly( gatewayHome );
  }

  private static XMLTag createTopology() {
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
        .addTag( "provider" )
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "Default" ).gotoParent()
        .addTag( "provider" )
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "test-service-role" )
        .gotoRoot();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testDeployRedeployUndeploy() throws InterruptedException, IOException {
    LOG_ENTER();
    long sleep = 200;
    int numFilesInWebInf = 4; // # files in WEB-INF (ie gateway.xml, rewrite.xml, shiro.ini, web.xml)
    String username = "guest";
    String password = "guest-password";
    String serviceUrl = clusterUrl + "/test-service-path/test-service-resource";
    long topoTimestampBefore, topoTimestampAfter;

    File topoDir = new File( config.getGatewayTopologyDir() );
    File deployDir = new File( config.getGatewayDeploymentDir() );
    File earDir;

    // Make sure deployment directory is empty.
    assertThat( topoDir.listFiles().length, is( 0 ) );
    assertThat( deployDir.listFiles().length, is( 0 ) );

    File descriptor = writeTestTopology( "test-cluster", createTopology() );
    long writeTime = System.currentTimeMillis();

    earDir = waitForFiles( deployDir, "test-cluster\\.topo\\.[0-9A-Fa-f]+", 1, 0, sleep );
    File warDir = new File( earDir, "%2F" );
    File webInfDir = new File( warDir, "WEB-INF" );
    waitForFiles( webInfDir, ".*", numFilesInWebInf, 0, sleep );
    waitForAccess( serviceUrl, username, password, sleep );

    // Wait to make sure a second has passed to ensure the the file timestamps are different.
    waitForElapsed( writeTime, 1000, 100 );

    // Redeploy and make sure the timestamp is updated.
    topoTimestampBefore = descriptor.lastModified();
    GatewayServer.redeployTopologies( null );
    writeTime = System.currentTimeMillis();
    topoTimestampAfter = descriptor.lastModified();
    assertThat( topoTimestampAfter, greaterThan( topoTimestampBefore ) );

    // Check to make sure there are two war directories with the same root.
    earDir = waitForFiles( deployDir, "test-cluster\\.topo\\.[0-9A-Fa-f]+", 2, 1, sleep );
    warDir = new File( earDir, "%2F" );
    webInfDir = new File( warDir, "WEB-INF" );
    waitForFiles( webInfDir, ".*", numFilesInWebInf, 0, sleep );
    waitForAccess( serviceUrl, username, password, sleep );

    // Wait to make sure a second has passed to ensure the the file timestamps are different.
    waitForElapsed( writeTime, 1000, 100 );

    // Redeploy and make sure the timestamp is updated.
    topoTimestampBefore = descriptor.lastModified();
    GatewayServer.redeployTopologies( "test-cluster" );
    writeTime = System.currentTimeMillis();
    topoTimestampAfter = descriptor.lastModified();
    assertThat( topoTimestampAfter, greaterThan( topoTimestampBefore ) );

    // Check to make sure there are two war directories with the same root.
    earDir = waitForFiles( deployDir, "test-cluster\\.topo\\.[0-9A-Fa-f]+", 3, 2, sleep );
    warDir = new File( earDir, "%2F" );
    webInfDir = new File( warDir, "WEB-INF" );
    waitForFiles( webInfDir, ".*", numFilesInWebInf, 0, sleep );
    waitForAccess( serviceUrl, username, password, sleep );

    // Delete the test topology.
    assertThat( "Failed to delete the topology file.", descriptor.delete(), is( true ) );

    // Wait to make sure a second has passed to ensure the the file timestamps are different.
    waitForElapsed( writeTime, 1000, 100 );

    waitForFiles( deployDir, ".*", 0, -1, sleep );

    // Wait a bit more to make sure undeployment finished.
    Thread.sleep( sleep );

    // Make sure the test topology is not accessible.
    given().auth().preemptive().basic( username, password )
        .then().statusCode( HttpStatus.SC_NOT_FOUND )
        .when().get( serviceUrl );

    // Make sure deployment directory is empty.
    assertThat( topoDir.listFiles().length, is( 0 ) );
    assertThat( deployDir.listFiles().length, is( 0 ) );
    LOG_EXIT();
  }

  private void waitForElapsed( long from, long total, long sleep ) throws InterruptedException {
    while( System.currentTimeMillis() - from < total ) {
      Thread.sleep( sleep );
    }
  }

  private File writeTestTopology( String name, XMLTag xml ) throws IOException {
    // Create the test topology.
    File tempFile = new File( config.getGatewayTopologyDir(), name + ".xml." + UUID.randomUUID() );
    try(OutputStream stream = Files.newOutputStream(tempFile.toPath())) {
      xml.toStream(stream);
    }
    File descriptor = new File( config.getGatewayTopologyDir(), name + ".xml" );
    tempFile.renameTo( descriptor );
    return descriptor;
  }

  private File waitForFiles( File dir, String pattern, int count, int index, long sleep ) throws InterruptedException {
    RegexDirFilter filter = new RegexDirFilter( pattern );
    while( true ) {
      File[] files = dir.listFiles( filter );
      if( files.length == count ) {
        return ( index < 0 ) ? null : files[ index ];
      }
      Thread.sleep( sleep );
    }
  }

  private void waitForAccess( String url, String username, String password, long sleep ) throws InterruptedException {
    while( true ) {
      Response response = given()
          .auth().preemptive().basic( username, password )
          .when().get( url ).andReturn();
      if( response.getStatusCode() == HttpStatus.SC_NOT_FOUND ) {
        Thread.sleep( sleep );
        continue;
      }
      assertThat( response.getContentType(), containsString( "text/plain" ) );
      assertThat( response.getBody().asString(), is( "test-service-response" ) );
      break;
    }
  }

  private class RegexDirFilter implements FilenameFilter {

    Pattern pattern;

    RegexDirFilter( String regex ) {
      pattern = Pattern.compile( regex );
    }

    @Override
    public boolean accept( File dir, String name ) {
      return pattern.matcher( name ).matches();
    }
  }

}

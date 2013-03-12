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

import com.jayway.restassured.response.Response;
import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.hadoop.test.TestUtils;
import org.apache.hadoop.test.category.FunctionalTests;
import org.apache.hadoop.test.category.MediumTests;
import org.apache.http.HttpStatus;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;

@Category( { FunctionalTests.class, MediumTests.class } )
public class GatewayBasicFuncTest {

//  @Test
//  public void hang() throws IOException {
//    System.out.println( "Server on port " + driver.gateway.getAddresses()[0].getPort() );
//    System.out.println();
//    System.in.read();
//  }

  private static Logger log = LoggerFactory.getLogger( GatewayBasicFuncTest.class );

  public static GatewayFuncTestDriver driver = new GatewayFuncTestDriver();

  private static final String TEST_HOST = "vm.local";
  private static final boolean USE_GATEWAY = true;
  private static final boolean USE_MOCK_SERVICES = true;
  private static final boolean CLEANUP_TEST = true;
//  private static final boolean USE_GATEWAY = false;
//  private static final boolean USE_MOCK_SERVICES = false;
//  private static final boolean CLEANUP_TEST = false;

  private static int findFreePort() throws IOException {
    ServerSocket socket = new ServerSocket(0);
    int port = socket.getLocalPort();
    socket.close();
    return port;
  }

  @BeforeClass
  public static void setupSuite() throws Exception {
    GatewayTestConfig config = new GatewayTestConfig();
    config.setGatewayPath( "gateway" );
    driver.setResourceBase( GatewayBasicFuncTest.class );
    driver.setupLdap( findFreePort() );
    driver.setupService( "NAMENODE", "http://" + TEST_HOST + ":50070/webhdfs/v1", "/cluster/namenode/api/v1", USE_MOCK_SERVICES ); // IPC:8020
    driver.setupService( "DATANODE", "http://" + TEST_HOST + ":50075/webhdfs/v1", "/cluster/datanode/api/v1", USE_MOCK_SERVICES ); // CLIENT:50010, IPC:50020
    // JobTracker: UI:50030,
    // TaskTracker: UI:50060, 127.0.0.1:0
    driver.setupService( "TEMPLETON", "http://" + TEST_HOST + ":50111/templeton/v1", "/cluster/templeton/api/v1", USE_MOCK_SERVICES );
    driver.setupService( "OOZIE", "http://" + TEST_HOST + ":11000/oozie", "/cluster/oozie/api", USE_MOCK_SERVICES );
    driver.setupGateway( config, "cluster", createTopology(), USE_GATEWAY );
  }

  @After
  public void cleanupTest() {
    driver.reset();
  }

  private static XMLTag createTopology() {
    XMLTag xml = XMLDoc.newDocument( true )
        .addRoot( "topology" )
          .addTag( "gateway" )
            .addTag( "provider" )
              .addTag( "role" ).addText( "authentication" )
              .addTag( "enabled" ).addText( "true" )
              .addTag( "param" )
                .addTag( "name" ).addText( "main.ldapRealm" )
                .addTag( "value" ).addText( "org.apache.shiro.realm.ldap.JndiLdapRealm" ).gotoParent()
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
                .addTag( "value" ).addText( "authcBasic" ).gotoParent()
        .gotoRoot()
          .addTag( "service" )
            .addTag( "role" ).addText( "NAMENODE" )
            .addTag( "url" ).addText( driver.getRealUrl( "NAMENODE" ) ).gotoParent()
          .addTag( "service" )
            .addTag( "role" ).addText( "DATANODE" )
            .addTag( "url" ).addText( driver.getRealUrl( "DATANODE" ) ).gotoParent()
          .addTag( "service" )
            .addTag( "role" ).addText( "TEMPLETON" )
            .addTag( "url" ).addText( driver.getRealUrl( "TEMPLETON" ) ).gotoParent()
          .addTag( "service" )
            .addTag( "role" ).addText( "OOZIE" )
            .addTag( "url" ).addText( driver.getRealUrl( "OOZIE" ) )
        .gotoRoot();
    return xml;
  }

  @Test
  public void testBasicJsonUseCase() throws IOException {
    String root = "/tmp/GatewayWebHdfsFuncTest/testBasicHdfsUseCase";
    String username = "hdfs";
    String password = "hdfs-password";

    /* Create a directory.
    curl -i -X PUT "http://<HOST>:<PORT>/<PATH>?op=MKDIRS[&permission=<OCTAL>]"

    The client receives a respond with a boolean JSON object:
    HTTP/1.1 HttpStatus.SC_OK OK
    Content-Type: application/json
    Transfer-Encoding: chunked

    {"boolean": true}
    */
    driver.getMock( "NAMENODE" )
        .expect()
        .method( "PUT" )
        .pathInfo( root + "/dir" )
        .queryParam( "op", "MKDIRS" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "webhdfs-success.json" ) )
        .contentType( "application/json" );
    given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .queryParam( "op", "MKDIRS" )
        .expect()
            //.log().all();
        .statusCode( HttpStatus.SC_OK )
        .contentType( "application/json" )
        .content( "boolean", is( true ) )
        .when().put( driver.getUrl( "NAMENODE" ) + root + "/dir" );
    driver.assertComplete();
  }

  @Test
  public void testBasicOutboundHeaderUseCase() throws IOException {
    String root = "/tmp/GatewayWebHdfsFuncTest/testBasicHdfsUseCase";
    String username = "hdfs";
    String password = "hdfs-password";
    InetSocketAddress gatewayAddress = driver.gateway.getAddresses()[0];

    driver.getMock( "NAMENODE" )
        .expect()
        .method( "PUT" )
        .pathInfo( root + "/dir/file" )
        .queryParam( "op", "CREATE" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_TEMPORARY_REDIRECT )
        .header( "Location", driver.getRealUrl( "DATANODE" ) + root + "/dir/file?op=CREATE&user.name=hdfs" );
    Response response = given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .queryParam( "op", "CREATE" )
        .expect()
            //.log().ifError()
        .statusCode( HttpStatus.SC_TEMPORARY_REDIRECT )
        .when().put( driver.getUrl("NAMENODE") + root + "/dir/file" );
    String location = response.getHeader( "Location" );
    //System.out.println( location );
    log.debug( "Redirect location: " + response.getHeader( "Location" ) );
    if( driver.isUseGateway() ) {
      MatcherAssert.assertThat( location, startsWith( "http://" + gatewayAddress.getHostName() + ":" + gatewayAddress.getPort() + "/" ) );
      MatcherAssert.assertThat( location, containsString( "?_=" ) );
    }
    MatcherAssert.assertThat( location, not( containsString( "host=" ) ) );
    MatcherAssert.assertThat( location, not( containsString( "port=" ) ) );
  }

  @Test
  public void testBasicHdfsUseCase() throws IOException {
    String root = "/tmp/GatewayWebHdfsFuncTest/testBasicHdfsUseCase";
    String username = "hdfs";
    String password = "hdfs-password";
    InetSocketAddress gatewayAddress = driver.gateway.getAddresses()[0];

    // Attempt to delete the test directory in case a previous run failed.
    // Ignore any result.
    // Cleanup anything that might have been leftover because the test failed previously.
    driver.getMock( "NAMENODE" )
        .expect()
        .method( "DELETE" )
        .pathInfo( root )
        .queryParam( "op", "DELETE" )
        .queryParam( "user.name", username )
        .queryParam( "recursive", "true" )
        .respond()
        .status( HttpStatus.SC_OK );
    given()
        .auth().preemptive().basic( username, password )
        .queryParam( "op", "DELETE" )
        .queryParam( "recursive", "true" )
        .expect()
        //.log().all();
        .statusCode( HttpStatus.SC_OK )
        .when().delete( driver.getUrl( "NAMENODE" ) + root + ( driver.isUseGateway() ? "" : "?user.name=" + username ) );
    driver.assertComplete();

    /* Create a directory.
    curl -i -X PUT "http://<HOST>:<PORT>/<PATH>?op=MKDIRS[&permission=<OCTAL>]"

    The client receives a respond with a boolean JSON object:
    HTTP/1.1 HttpStatus.SC_OK OK
    Content-Type: application/json
    Transfer-Encoding: chunked

    {"boolean": true}
    */
    driver.getMock( "NAMENODE" )
        .expect()
        .method( "PUT" )
        .pathInfo( root + "/dir" )
        .queryParam( "op", "MKDIRS" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "webhdfs-success.json" ) )
        .contentType( "application/json" );
    given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .queryParam( "op", "MKDIRS" )
        .expect()
        //.log().all();
        .statusCode( HttpStatus.SC_OK )
        .contentType( "application/json" )
        .content( "boolean", is( true ) )
        .when().put( driver.getUrl( "NAMENODE" ) + root + "/dir" );
    driver.assertComplete();

    driver.getMock( "NAMENODE" )
        .expect()
        .method( "GET" )
        .pathInfo( root )
        .queryParam( "op", "LISTSTATUS" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "webhdfs-liststatus-test.json" ) )
        .contentType( "application/json" );
    given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .queryParam( "op", "LISTSTATUS" )
        .expect()
        //.log().ifError()
        .statusCode( HttpStatus.SC_OK )
        .content( "FileStatuses.FileStatus[0].pathSuffix", is( "dir" ) )
        .when().get( driver.getUrl( "NAMENODE" ) + root );
    driver.assertComplete();

    //NEGATIVE: Test a bad password.
    given()
        //.log().all()
        .auth().preemptive().basic( username, "invalid-password" )
        .queryParam( "op", "LISTSTATUS" )
        .expect()
        //.log().ifError()
        .statusCode( HttpStatus.SC_UNAUTHORIZED );
    driver.assertComplete();

    //NEGATIVE: Test a bad user.
    given()
        //.log().all()
        .auth().preemptive().basic( "invalid-user", "invalid-password" )
        .queryParam( "op", "LISTSTATUS" )
        .expect()
        //.log().ifError()
        .statusCode( HttpStatus.SC_UNAUTHORIZED );
    driver.assertComplete();

    /* Add a file.
    curl -i -X PUT "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=CREATE
                       [&overwrite=<true|false>][&blocksize=<LONG>][&replication=<SHORT>]
                     [&permission=<OCTAL>][&buffersize=<INT>]"

    The expect is redirected to a datanode where the file data is to be written:
    HTTP/1.1 307 TEMPORARY_REDIRECT
    Location: http://<DATANODE>:<PORT>/webhdfs/v1/<PATH>?op=CREATE...
    Content-Length: 0

    Step 2: Submit another HTTP PUT expect using the URL in the Location header with the file data to be written.
    curl -i -X PUT -T <LOCAL_FILE> "http://<DATANODE>:<PORT>/webhdfs/v1/<PATH>?op=CREATE..."

    The client receives a HttpStatus.SC_CREATED Created respond with zero content length and the WebHDFS URI of the file in the Location header:
    HTTP/1.1 HttpStatus.SC_CREATED Created
    Location: webhdfs://<HOST>:<PORT>/<PATH>
    Content-Length: 0
    */
    driver.getMock( "NAMENODE" )
        .expect()
        .method( "PUT" )
        .pathInfo( root + "/dir/file" )
        .queryParam( "op", "CREATE" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_TEMPORARY_REDIRECT )
        .header( "Location", driver.getRealUrl( "DATANODE" ) + root + "/dir/file?op=CREATE&user.name=hdfs" );
    driver.getMock( "DATANODE" )
        .expect()
        .method( "PUT" )
        .pathInfo( root + "/dir/file" )
        .queryParam( "op", "CREATE" )
        .queryParam( "user.name", username )
        .contentType( "text/plain" )
        .content( driver.getResourceBytes( "test.txt" ) )
            //.content( driver.gerResourceBytes( "hadoop-examples.jar" ) )
        .respond()
        .status( HttpStatus.SC_CREATED )
        .header( "Location", "webhdfs://" + driver.getRealAddr( "DATANODE" ) + root + "/dir/file" );
    Response response = given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .queryParam( "op", "CREATE" )
        .expect()
        //.log().ifError()
        .statusCode( HttpStatus.SC_TEMPORARY_REDIRECT )
        .when().put( driver.getUrl("NAMENODE") + root + "/dir/file" );
    String location = response.getHeader( "Location" );
    log.debug( "Redirect location: " + response.getHeader( "Location" ) );
    if( driver.isUseGateway() ) {
      MatcherAssert.assertThat( location, startsWith( "http://" + gatewayAddress.getHostName() + ":" + gatewayAddress.getPort() + "/" ) );
      MatcherAssert.assertThat( location, startsWith( "http://" + gatewayAddress.getHostName() + ":" + gatewayAddress.getPort() + "/" ) );
      MatcherAssert.assertThat( location, containsString( "?_=" ) );
    }
    MatcherAssert.assertThat( location, not( containsString( "host=" ) ) );
    MatcherAssert.assertThat( location, not( containsString( "port=" ) ) );
    response = given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .content( driver.getResourceBytes( "test.txt" ) )
        .contentType( "text/plain" )
        .expect()
        //.log().ifError()
        .statusCode( HttpStatus.SC_CREATED )
        .when().put( location );
    location = response.getHeader( "Location" );
    log.debug( "Created location: " + location );
    if( driver.isUseGateway() ) {
      MatcherAssert.assertThat( location, startsWith( "http://" + gatewayAddress.getHostName() + ":" + gatewayAddress.getPort() + "/" ) );
    }
    driver.assertComplete();

    /* Get the file.
    curl -i -L "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=OPEN
                       [&offset=<LONG>][&length=<LONG>][&buffersize=<INT>]"

    The expect is redirected to a datanode where the file data can be read:
    HTTP/1.1 307 TEMPORARY_REDIRECT
    Location: http://<DATANODE>:<PORT>/webhdfs/v1/<PATH>?op=OPEN...
    Content-Length: 0

    The client follows the redirect to the datanode and receives the file data:
    HTTP/1.1 HttpStatus.SC_OK OK
    Content-Type: application/octet-stream
    Content-Length: 22

    Hello, webhdfs user!
    */
    driver.getMock( "NAMENODE" )
        .expect()
        .method( "GET" )
        .pathInfo( root + "/dir/file" )
        .queryParam( "op", "OPEN" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_TEMPORARY_REDIRECT )
        .header( "Location", driver.getRealUrl( "DATANODE" ) + root + "/dir/file?op=OPEN&user.name=hdfs" );
    driver.getMock( "DATANODE" )
        .expect()
        .method( "GET" )
        .pathInfo( root + "/dir/file" )
        .queryParam( "op", "OPEN" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .content( driver.getResourceBytes( "test.txt" ) );
    given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .queryParam( "op", "OPEN" )
        .expect()
        //.log().ifError()
        .statusCode( HttpStatus.SC_OK )
        .content( is( "TEST" ) )
        .when().get( driver.getUrl("NAMENODE") + root + "/dir/file" );
    driver.assertComplete();

    /* Delete the directory.
    curl -i -X DELETE "http://<host>:<port>/webhdfs/v1/<path>?op=DELETE
                                 [&recursive=<true|false>]"

    The client receives a respond with a boolean JSON object:
    HTTP/1.1 HttpStatus.SC_OK OK
    Content-Type: application/json
    Transfer-Encoding: chunked

    {"boolean": true}
    */
    // Mock the interaction with the namenode.
    driver.getMock( "NAMENODE" )
        .expect()
        .method( "DELETE" )
        .pathInfo( root )
        .queryParam( "op", "DELETE" )
        .queryParam( "user.name", username )
        .queryParam( "recursive", "true" )
        .respond()
        .status( HttpStatus.SC_OK );
    given()
        .auth().preemptive().basic( username, password )
        .queryParam( "op", "DELETE" )
        .queryParam( "recursive", "true" )
        .expect()
        //.log().ifError()
        .statusCode( HttpStatus.SC_OK )
        .when().delete( driver.getUrl( "NAMENODE" ) + root );
    driver.assertComplete();
  }

  // User hdfs in groups hadoop, hdfs
  // User mapred in groups hadoop, mapred
  // User hcat in group hcat
  @Test
  public void testPmHdfsM1UseCase() throws IOException {
    String root = "/tmp/GatewayWebHdfsFuncTest/testPmHdfdM1UseCase";
    String userA = "hdfs";
    String passA = "hdfs-password";
    String userB = "mapred";
    String passB = "mapred-password";
    String userC = "hcat";
    String passC = "hcat-password";
    String groupA = "hdfs";
    String groupB = "mapred";
    String groupAB = "hadoop";
    String groupC = "hcat";

    driver.deleteFile( userA, passA, root, "true", 200 );

    driver.createDir( userA, passA, groupA, root + "/dirA700", "700", 200, 200 );
    driver.createDir( userA, passA, groupA, root + "/dirA770", "770", 200, 200 );
    driver.createDir( userA, passA, groupA, root + "/dirA707", "707", 200, 200 );
    driver.createDir( userA, passA, groupA, root + "/dirA777", "777", 200, 200 );
    driver.createDir( userA, passA, groupAB, root + "/dirAB700", "700", 200, 200 );
    driver.createDir( userA, passA, groupAB, root + "/dirAB770", "770", 200, 200 );
    driver.createDir( userA, passA, groupAB, root + "/dirAB707", "707", 200, 200 );
    driver.createDir( userA, passA, groupAB, root + "/dirAB777", "777", 200, 200 );

    // CREATE: Files
    // userA:groupA
    driver.createFile( userA, passA, groupA, root + "/dirA700/fileA700", "700", "text/plain", "small1.txt", 307, 201, 200 );
    driver.createFile( userA, passA, groupA, root + "/dirA770/fileA770", "770", "text/plain", "small1.txt", 307, 201, 200 );
    driver.createFile( userA, passA, groupA, root + "/dirA707/fileA707", "707", "text/plain", "small1.txt", 307, 201, 200 );
    driver.createFile( userA, passA, groupA, root + "/dirA777/fileA777", "777", "text/plain", "small1.txt", 307, 201, 200 );
    // userA:groupAB
    driver.createFile( userA, passA, groupAB, root + "/dirAB700/fileAB700", "700", "text/plain", "small1.txt", 307, 201, 200 );
    driver.createFile( userA, passA, groupAB, root + "/dirAB770/fileAB770", "770", "text/plain", "small1.txt", 307, 201, 200 );
    driver.createFile( userA, passA, groupAB, root + "/dirAB707/fileAB707", "707", "text/plain", "small1.txt", 307, 201, 200 );
    driver.createFile( userA, passA, groupAB, root + "/dirAB777/fileAB777", "777", "text/plain", "small1.txt", 307, 201, 200 );
    // userB:groupB
    driver.createFile( userB, passB, groupB, root + "/dirA700/fileB700", "700", "text/plain", "small1.txt", 307, 403, 0 );
    driver.createFile( userB, passB, groupB, root + "/dirA770/fileB700", "700", "text/plain", "small1.txt", 307, 403, 0 );
//kam:20130219[ chmod seems to be broken at least in Sandbox 1.2
//    driver.createFile( userB, passB, groupB, root + "/dirA707/fileB700", "700", "text/plain", "small1.txt", 307, 201, 200 );
//    driver.createFile( userB, passB, groupB, root + "/dirA777/fileB700", "700", "text/plain", "small1.txt", 307, 201, 200 );
//kam]
    // userB:groupAB
    driver.createFile( userB, passB, groupAB, root + "/dirA700/fileBA700", "700", "text/plain", "small1.txt", 307, 403, 0 );
    driver.createFile( userB, passB, groupAB, root + "/dirA770/fileBA700", "700", "text/plain", "small1.txt", 307, 403, 0 );
    driver.createFile( userB, passB, groupAB, root + "/dirA707/fileBA700", "700", "text/plain", "small1.txt", 307, 201, 200 );
    driver.createFile( userB, passB, groupAB, root + "/dirA777/fileBA700", "700", "text/plain", "small1.txt", 307, 201, 200 );
    // userC:groupC
    driver.createFile( userC, passC, groupC, root + "/dirA700/fileC700", "700", "text/plain", "small1.txt", 307, 403, 0 );
    driver.createFile( userC, passC, groupC, root + "/dirA770/fileC700", "700", "text/plain", "small1.txt", 307, 403, 0 );
//kam:20130219[ chmod seems to be broken at least in Sandbox 1.2
//    driver.createFile( userC, passC, groupC, root + "/dirA707/fileC700", "700", "text/plain", "small1.txt", 307, 201, 200 );
//    driver.createFile( userC, passC, groupC, root + "/dirA777/fileC700", "700", "text/plain", "small1.txt", 307, 201, 200 );
//kam]

    // READ
    // userA
    driver.readFile( userA, passA, root + "/dirA700/fileA700", "text/plain", "small1.txt", HttpStatus.SC_OK );
    driver.readFile( userA, passA, root + "/dirA770/fileA770", "text/plain", "small1.txt", HttpStatus.SC_OK );
    driver.readFile( userA, passA, root + "/dirA707/fileA707", "text/plain", "small1.txt", HttpStatus.SC_OK );
    driver.readFile( userA, passA, root + "/dirA777/fileA777", "text/plain", "small1.txt", HttpStatus.SC_OK );
    // userB:groupB
    driver.readFile( userB, passB, root + "/dirA700/fileA700", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    driver.readFile( userB, passB, root + "/dirA770/fileA770", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    driver.readFile( userB, passB, root + "/dirA707/fileA707", "text/plain", "small1.txt", HttpStatus.SC_OK );
    driver.readFile( userB, passB, root + "/dirA777/fileA777", "text/plain", "small1.txt", HttpStatus.SC_OK );
    // userB:groupAB
    driver.readFile( userB, passB, root + "/dirAB700/fileAB700", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    driver.readFile( userB, passB, root + "/dirAB770/fileAB770", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    driver.readFile( userB, passB, root + "/dirAB707/fileAB707", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    driver.readFile( userB, passB, root + "/dirAB777/fileAB777", "text/plain", "small1.txt", HttpStatus.SC_OK );
    // userC:groupC
    driver.readFile( userC, passC, root + "/dirA700/fileA700", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    driver.readFile( userC, passC, root + "/dirA770/fileA770", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    driver.readFile( userC, passC, root + "/dirA707/fileA707", "text/plain", "small1.txt", HttpStatus.SC_OK );
    driver.readFile( userC, passC, root + "/dirA777/fileA777", "text/plain", "small1.txt", HttpStatus.SC_OK );

    //NEGATIVE: Test a bad password.
    if( driver.isUseGateway() ) {
      Response response = given()
          //.log().all()
          .auth().preemptive().basic( userA, "invalid-password" )
          .queryParam( "op", "OPEN" )
          .expect()
          //.log().all()
          .statusCode( HttpStatus.SC_UNAUTHORIZED )
          .when().get( driver.getUrl("NAMENODE") + root + "/dirA700/fileA700" );
    }
    driver.assertComplete();

    // UPDATE (Negative First)
    driver.updateFile( userC, passC, root + "/dirA700/fileA700", "text/plain", "small2.txt", 307, 403 );
    driver.updateFile( userB, passB, root + "/dirAB700/fileAB700", "text/plain", "small2.txt", 307, 403 );
    driver.updateFile( userB, passB, root + "/dirAB770/fileAB700", "text/plain", "small2.txt", 307, 403 );
    driver.updateFile( userB, passB, root + "/dirAB770/fileAB770", "text/plain", "small2.txt", 307, 403 );
    driver.updateFile( userA, passA, root + "/dirA700/fileA700", "text/plain", "small2.txt", 307, 201 );

    // DELETE (Negative First)
    driver.deleteFile( userC, passC, root + "/dirA700/fileA700", "false", HttpStatus.SC_FORBIDDEN );
    driver.deleteFile( userB, passB, root + "/dirAB700/fileAB700", "false", HttpStatus.SC_FORBIDDEN );
    driver.deleteFile( userB, passB, root + "/dirAB770/fileAB770", "false", HttpStatus.SC_FORBIDDEN );
    driver.deleteFile( userA, passA, root + "/dirA700/fileA700", "false", HttpStatus.SC_OK );

    // Cleanup anything that might have been leftover because the test failed previously.
    driver.deleteFile( userA, passA, root, "true", HttpStatus.SC_OK );
  }

  @Test
  public void testJavaMapReduceViaTempleton() throws IOException {
    String root = "/tmp/GatewayWebHdfsFuncTest/testJavaMapReduceViaTempleton";
    String user = "mapred";
    String pass = "mapred-password";
    String group = "mapred";
//    String user = "hcat";
//    String pass = "hcat-password";
//    String group = "hcat";

    // Cleanup anything that might have been leftover because the test failed previously.
    driver.deleteFile( user, pass, root, "true", HttpStatus.SC_OK );

    /* Put the mapreduce code into HDFS. (hadoop-examples.jar)
    curl -X PUT --data-binary @hadoop-examples.jar 'http://192.168.1.163:8888/org.apache.org.apache.hadoop.gateway/cluster/namenode/api/v1/user/hdfs/wordcount/hadoop-examples.jar?user.name=hdfs&op=CREATE'
     */
    driver.createFile( user, pass, null, root+"/hadoop-examples.jar", "777", "application/octet-stream", "hadoop-examples.jar", 307, 201, 200 );

    /* Put the data file into HDFS (changes.txt)
    curl -X PUT --data-binary @changes.txt 'http://192.168.1.163:8888/org.apache.org.apache.hadoop.gateway/cluster/namenode/api/v1/user/hdfs/wordcount/input/changes.txt?user.name=hdfs&op=CREATE'
     */
    driver.createFile( user, pass, null, root+"/input/changes.txt", "777", "text/plain", "changes.txt", 307, 201, 200 );

    /* Create the output directory
    curl -X PUT 'http://192.168.1.163:8888/org.apache.org.apache.hadoop.gateway/cluster/namenode/api/v1/user/hdfs/wordcount/output?op=MKDIRS&user.name=hdfs'
    */
    driver.createDir( user, pass, null, root+"/output", "777", 200, 200 );

    /* Submit the job
    curl -d user.name=hdfs -d jar=wordcount/hadoop-examples.jar -d class=org.apache.org.apache.hadoop.examples.WordCount -d arg=wordcount/input -d arg=wordcount/output 'http://localhost:8888/org.apache.org.apache.hadoop.gateway/cluster/templeton/api/v1/mapreduce/jar'
    ﻿{"id":"job_201210301335_0059"}
    */
    String job = driver.submitJava(
        user, pass,
        root+"/hadoop-examples.jar", "org.apache.org.apache.hadoop.examples.WordCount",
        root+"/input", root+"/output",
        200 );

    /* Get the job status
    curl 'http://vm:50111/templeton/v1/queue/:jobid?user.name=hdfs'
    */
    driver.queryQueue( user, pass, job );

    // Can't really check for the output here because the job won't be done.
    /* Retrieve results
    curl 'http://192.168.1.163:8888/org.apache.org.apache.hadoop.gateway/cluster/namenode/api/v1/user/hdfs/wordcount/input?op=LISTSTATUS'
    */

    // Cleanup anything that might have been leftover because the test failed previously.
    driver.deleteFile( user, pass, root, "true", HttpStatus.SC_OK );
  }

  @Test
  public void testPigViaTempleton() throws IOException {
    String root = "/tmp/GatewayTempletonFuncTest/testPigViaTempleton";
    String user = "mapred";
    String pass = "mapred-password";
    String group = "mapred";

    // Cleanup if previous run failed.
    driver.deleteFile( user, pass, root, "true", 200, 404 );

    // Post the data to HDFS
    driver.createFile( user, pass, null, root + "/passwd.txt", "777", "text/plain", "passwd.txt", 307, 201, 200 );

    // Post the script to HDFS
    driver.createFile( user, pass, null, root+"/script.pig", "777", "text/plain", "script.pig", 307, 201, 200 );

    // Create the output directory
    driver.createDir( user, pass, null, root + "/output", "777", 200, 200 );

    // Submit the job
    driver.submitPig( user, pass, group, root + "/script.pig", "-v", root + "/output", 200 );

    // Check job status (if possible)
    // Check output (if possible)

    // Cleanup
    driver.deleteFile( user, pass, root, "true", 200 );
  }

  @Test
  public void testHiveViaTempleton() throws IOException {
    String user = "hive";
    String pass = "hive-password";
    String group = "hive";
    String root = "/tmp/GatewayTempletonFuncTest/testHiveViaTempleton";

    // Cleanup if previous run failed.
    driver.deleteFile( user, pass, root, "true", 200, 404 );

    // Post the data to HDFS

    // Post the script to HDFS
    driver.createFile( user, pass, null, root + "/script.hive", "777", "text/plain", "script.hive", 307, 201, 200 );

    // Submit the job
    driver.submitHive( user, pass, group, root+"/script.hive", root+"/output", 200 );

    // Check job status (if possible)
    // Check output (if possible)

    // Cleanup
    driver.deleteFile( user, pass, root, "true", 200 );
  }

  @Ignore( "WIP" )
  @Test
  public void testOozieGeneralOperations() {
    String user = "oozie";
    String pass = "oozie-password";
//    driver.oozieVersions( user, pass );
  }

  @Test
  public void testOozieJobSubmission() throws Exception {
    String root = "/tmp/GatewayBasicFuncTest/testOozieJobSubmission";
    String user = "hdfs";
    String pass = "hdfs-password";
    String group = "hdfs";

    // Cleanup anything that might have been leftover because the test failed previously.
    driver.deleteFile( user, pass, root, "true", HttpStatus.SC_OK );

    /* Put the workflow definition into HDFS */
    driver.createFile( user, pass, group, root+"/workflow.xml", "666", "application/octet-stream", "oozie-workflow.xml", 307, 201, 200 );

    /* Put the mapreduce code into HDFS. (hadoop-examples.jar)
    curl -X PUT --data-binary @hadoop-examples.jar 'http://192.168.1.163:8888/org.apache.org.apache.hadoop.gateway/cluster/namenode/api/v1/user/hdfs/wordcount/hadoop-examples.jar?user.name=hdfs&op=CREATE'
     */
    driver.createFile( user, pass, group, root+"/lib/hadoop-examples.jar", "777", "application/octet-stream", "hadoop-examples.jar", 307, 201, 200 );

    /* Put the data file into HDFS (changes.txt)
    curl -X PUT --data-binary @changes.txt 'http://192.168.1.163:8888/org.apache.org.apache.hadoop.gateway/cluster/namenode/api/v1/user/hdfs/wordcount/input/changes.txt?user.name=hdfs&op=CREATE'
     */
    driver.createFile( user, pass, group, root+"/input/changes.txt", "666", "text/plain", "changes.txt", 307, 201, 200 );

    VelocityEngine velocity = new VelocityEngine();
    velocity.setProperty( RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, "org.apache.velocity.runtime.log.NullLogSystem" );
    velocity.setProperty( RuntimeConstants.RESOURCE_LOADER, "classpath" );
    velocity.setProperty( "classpath.resource.loader.class", ClasspathResourceLoader.class.getName() );
    velocity.init();

    VelocityContext context = new VelocityContext();
    context.put( "userName", user );
    context.put( "nameNode", "hdfs://sandbox:8020" );
    context.put( "jobTracker", "sandbox:50300" );
    context.put( "appPath", "hdfs://sandbox:8020" + root );
    context.put( "inputDir", root + "/input" );
    context.put( "outputDir", root + "/output" );

    //URL url = TestUtils.getResourceUrl( GatewayBasicFuncTest.class, "oozie-jobs-submit-request.xml" );
    //String name = url.toExternalForm();
    String name = TestUtils.getResourceName( this.getClass(), "oozie-jobs-submit-request.xml" );
    Template template = velocity.getTemplate( name );
    StringWriter sw = new StringWriter();
    template.merge( context, sw );
    String request = sw.toString();
    //System.out.println( "REQUEST=" + request );

    /* Submit the job via Oozie. */
    String id = driver.oozieSubmitJob( user, pass, request, 201 );
    //System.out.println( "ID=" + id );

    String success = "SUCCEEDED";
    String status = "UNKNOWN";
    long delay = 1000 * 20; // 1 second.
    long limit = 1000 * 60; // 60 seconds.
    long start = System.currentTimeMillis();
    while( System.currentTimeMillis() <= start+limit ) {
      status = driver.oozieQueryJobStatus( user, pass, id, 200 );
      //System.out.println( "Status=" + status );
      if( success.equalsIgnoreCase( status ) ) {
        break;
      } else {
        System.out.println( "Status=" + status );
        Thread.sleep( delay );
      }
    }
    //System.out.println( "Status is " + status + " after " + ((System.currentTimeMillis()-start)/1000) + " seconds." );
    MatcherAssert.assertThat( status, is( success ) );

    if( CLEANUP_TEST ) {
      // Cleanup anything that might have been leftover because the test failed previously.
      driver.deleteFile( user, pass, root, "true", HttpStatus.SC_OK );
    }
  }

}
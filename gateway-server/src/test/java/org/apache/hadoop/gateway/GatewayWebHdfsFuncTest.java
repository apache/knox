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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.gateway.security.EmbeddedApacheDirectoryServer;
import org.apache.hadoop.test.category.FunctionalTests;
import org.apache.hadoop.test.category.MediumTests;
import org.apache.hadoop.test.mock.MockServer;
import org.apache.http.HttpStatus;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.UUID;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;

@Category( { FunctionalTests.class, MediumTests.class } )
public class GatewayWebHdfsFuncTest {

//  @Test
//  public void demoWait() throws IOException {
//    System.out.println( "Press any key to continue. Server at " + getGatewayPath() );
//    System.in.read();
//  }

  private static Logger log = LoggerFactory.getLogger( GatewayWebHdfsFuncTest.class );

  private static boolean MOCK = Boolean.parseBoolean( System.getProperty( "MOCK", "true" ) );
  //private static boolean MOCK = false;
  private static boolean GATEWAY = Boolean.parseBoolean( System.getProperty( "GATEWAY", "true" ) );
  //private static boolean GATEWAY = false;

  private static final int LDAP_PORT = 33389;

  //private static String TEST_HOST_NAME = "ec2-23-22-31-165.compute-1.amazonaws.com";
  //private static String TEST_HOST_NAME = "http://192.168.56.101";
  private static String TEST_HOST_NAME = "vm.local";
  private static String NAME_NODE_ADDRESS = TEST_HOST_NAME + ":50070";
  //private static String DATA_NODE_ADDRESS = TEST_HOST_NAME + ":50075";

  private static EmbeddedApacheDirectoryServer ldap;
  private static GatewayServer gateway;
  private static MockServer namenode;
  private static MockServer datanode;

  private static GatewayTestConfig config;

  @BeforeClass
  public static void setupSuite() throws Exception {
    namenode = new MockServer( "NameNode", true );
    datanode = new MockServer( "DataNode", true );
    startLdap();
    setupGateway();
    startGateway();
    log.info( "LDAP port = " + LDAP_PORT );
    log.info( "NameNode port = " + namenode.getPort() );
    log.info( "DataNode port = " + datanode.getPort() );
    log.info( "Gateway address = " + gateway.getAddresses()[ 0 ] );
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    if( gateway != null ) {
      gateway.stop();
    }
    if( ldap != null ) {
      ldap.stop();
    }
    if( namenode != null ) {
      namenode.stop();
    }
    if( datanode != null ) {
      datanode.stop();
    }
    cleanupGateway();
  }

  private static void setupGateway() throws Exception {
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();
    File deployDir = new File( gatewayDir, "clusters" );
    deployDir.mkdirs();

    config = new GatewayTestConfig();
    config.setGatewayHomeDir( gatewayDir.getAbsolutePath() );
    config.setClusterConfDir( "clusters" );
    config.setGatewayPath( "gateway" );
    config.setGatewayPort( 0 );

    XMLTag doc = createTopology();
    File descriptor = new File( deployDir, "cluster.xml" );
    FileOutputStream stream = new FileOutputStream( descriptor );
    doc.toStream( stream );
    stream.close();
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
                .addTag( "value" ).addText( "org.apache.shiro.realm.ldap.JndiLdapRealm" )
              .gotoParent().addTag( "param" )
                .addTag( "name" ).addText( "main.ldapRealm.userDnTemplate" )
                .addTag( "value" ).addText( "uid={0},ou=people,dc=hadoop,dc=apache,dc=org" )
              .gotoParent().addTag( "param" )
                .addTag( "name" ).addText( "main.ldapRealm.contextFactory.url" )
                .addTag( "value" ).addText( "ldap://localhost:33389" )
              .gotoParent().addTag( "param" )
                .addTag( "name" ).addText( "main.ldapRealm.contextFactory.authenticationMechanism" )
                .addTag( "value" ).addText( "simple" )
              .gotoParent().addTag( "param" )
                .addTag( "name" ).addText( "urls./**" )
                .addTag( "value" ).addText( "authcBasic" )
        .gotoRoot()
          .addTag( "service" )
            .addTag( "role" ).addText( "NAMENODE" )
            .addTag( "url" ).addText( "http://localhost:" + namenode.getPort() + "/webhdfs/v1" )
        .gotoRoot();
    return xml;
  }

  private static void startLdap() throws Exception{
    URL usersUrl = ClassLoader.getSystemResource( "users.ldif" );
    ldap = new EmbeddedApacheDirectoryServer( "dc=hadoop,dc=apache,dc=org", null, LDAP_PORT );
    ldap.start();
    ldap.loadLdif( usersUrl );
  }

  private static void startGateway() throws Exception {
    gateway = GatewayServer.startGateway( config );
    assertThat( "Failed to start gateway.", gateway, notNullValue() );
  }

  private static void cleanupGateway() {
    FileUtils.deleteQuietly( new File( config.getGatewayHomeDir() ) );
  }

  private String getGatewayPath() {
    InetSocketAddress address = gateway.getAddresses()[0];
    return "http://localhost:" + address.getPort();
  }

  private String getWebHdfsPath() {
    String path = GATEWAY
        ? getGatewayPath() + "/" + config.getGatewayPath() + "/cluster/namenode/api/v1"
        : "http://" + NAME_NODE_ADDRESS + "/webhdfs/v1";
    return path;
  }

  @Test
  public void testBasicHdfsInvalidUserUseCase() throws IOException {
    String namenodePath = getWebHdfsPath();

    // Attempt to delete the test directory in case a previous run failed.
    // Ignore any result.
    if( MOCK ) {
      namenode
          .expect()
          .method( "DELETE" )
          .pathInfo( "/webhdfs/v1/test" )
//          .queryParam( "user.name", null )
          .queryParam( "op", "DELETE" )
          .queryParam( "recursive", "true" )
          .respond()
          .status( HttpStatus.SC_OK );
    }
    given()
        //.log().all()
        .auth().preemptive().basic( "invaldUser", "password" )
//        .queryParam( "user.name", "hdfs" )
        .queryParam( "op", "DELETE" )
        .queryParam( "recursive", "true" )
        .expect()
        //.log().all()
        .statusCode( equalTo( HttpStatus.SC_UNAUTHORIZED ) )
        .when()
        .delete( namenodePath + "/test" );
    if( MOCK ) {
      // make sure that it wasn't deleted
      assertThat( namenode.getCount(), is( 1 ) );
    }
  }  
  
  @Test
  public void testBasicHdfsUseCase() throws IOException {
    String namenodePath = getWebHdfsPath();

    // Attempt to delete the test directory in case a previous run failed.
    // Ignore any result.
    if( MOCK ) {
      namenode
          .expect()
          .method( "DELETE" )
          .pathInfo( "/webhdfs/v1/test" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "DELETE" )
          .queryParam( "recursive", "true" )
          .respond()
          .status( HttpStatus.SC_OK );
    }
    given()
        //.log().all()
        .auth().preemptive().basic( "hdfs", "hdfs-password" )
//        .queryParam( "user.name", "hdfs" )
        .queryParam( "op", "DELETE" )
        .queryParam( "recursive", "true" )
        .expect()
        //.log().all()
        .statusCode( anyOf( equalTo( HttpStatus.SC_OK ), equalTo( HttpStatus.SC_NOT_FOUND ) ) )
        .when()
        .delete( namenodePath + "/test" );
    if( MOCK ) {
      assertThat( namenode.getCount(), is( 0 ) );
    }

    /*
    curl -i -L http://vm-hdpt:50070/webhdfs/v1/?op=LISTSTATUS
    HTTP/1.1 200 OK
    Content-Type: application/json
    Content-Length: 760
    Server: Jetty(6.1.26)

    {"FileStatuses":{"FileStatus":[
    {"accessTime":0,"blockSize":0,"group":"hdfs","length":0,"modificationTime":1350595859762,"owner":"hdfs","pathSuffix":"apps","permission":"755","replication":0,"type":"DIRECTORY"},
    {"accessTime":0,"blockSize":0,"group":"mapred","length":0,"modificationTime":1350595874024,"owner":"mapred","pathSuffix":"mapred","permission":"755","replication":0,"type":"DIRECTORY"},
    {"accessTime":0,"blockSize":0,"group":"hdfs","length":0,"modificationTime":1350596040075,"owner":"hdfs","pathSuffix":"tmp","permission":"777","replication":0,"type":"DIRECTORY"},
    {"accessTime":0,"blockSize":0,"group":"hdfs","length":0,"modificationTime":1350595857178,"owner":"hdfs","pathSuffix":"user","permission":"755","replication":0,"type":"DIRECTORY"}
    ]}}
     */
    if( MOCK ) {
      namenode
          .expect()
          .method( "GET" )
          .pathInfo( "/webhdfs/v1" )
          .queryParam( "op", "LISTSTATUS" )
          .respond()
          .status( HttpStatus.SC_OK )
          .content( ClassLoader.getSystemResource( "webhdfs-liststatus-default.json" ) )
          .contentType( "application/json" );
    }
    given()
        //.log().all()
        .auth().preemptive().basic( "hdfs", "hdfs-password" )
        .queryParam( "op", "LISTSTATUS" )
        .expect()
        .log().ifError()
        .statusCode( HttpStatus.SC_OK )
        //.content( "FileStatuses.FileStatus[0].pathSuffix", equalTo( "apps" ) )
        .content( "FileStatuses.FileStatus[0].pathSuffix", equalTo( "mapred" ) )
        .content( "FileStatuses.FileStatus[1].pathSuffix", equalTo( "tmp" ) )
        .content( "FileStatuses.FileStatus[2].pathSuffix", equalTo( "user" ) )
        .when()
        .get( namenodePath + "/" );
    if( MOCK ) {
      assertThat( namenode.getCount(), is( 0 ) );
    }

    /* Create a directory.
    curl -i -X PUT "http://<HOST>:<PORT>/<PATH>?op=MKDIRS[&permission=<OCTAL>]"

    The client receives a respond with a boolean JSON object:
    HTTP/1.1 200 OK
    Content-Type: application/json
    Transfer-Encoding: chunked

    {"boolean": true}
    */
    if( MOCK ) {
      namenode
          .expect()
          .method( "PUT" )
          .pathInfo( "/webhdfs/v1/test" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "MKDIRS" )
          .respond()
          .status( HttpStatus.SC_OK )
          .content( ClassLoader.getSystemResource( "webhdfs-success.json" ) )
          .contentType( "application/json" );
    }
    given()
        //.log().all()
        .auth().preemptive().basic( "hdfs", "hdfs-password" )
//        .queryParam( "user.name", "hdfs" )
        .queryParam( "op", "MKDIRS" )
        .expect()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "application/json" )
        .content( "boolean", equalTo( true ) )
        .when().put( namenodePath + "/test" );
    if( MOCK ) {
      assertThat( namenode.getCount(), is( 0 ) );
    }

    if( MOCK ) {
      namenode
          .expect()
          .method( "GET" )
          .pathInfo( "/webhdfs/v1" )
          .queryParam( "op", "LISTSTATUS" )
          .respond()
          .status( HttpStatus.SC_OK )
          .content( ClassLoader.getSystemResource( "webhdfs-liststatus-test.json" ) )
          .contentType( "application/json" );
    }
    given()
        //.log().all()
        .auth().preemptive().basic( "hdfs", "hdfs-password" )
        .queryParam( "op", "LISTSTATUS" )
        .expect()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        //.content( "FileStatuses.FileStatus[0].pathSuffix", equalTo( "apps" ) )
        .content( "FileStatuses.FileStatus[0].pathSuffix", equalTo( "mapred" ) )
        .content( "FileStatuses.FileStatus[1].pathSuffix", equalTo( "test" ) )
        .content( "FileStatuses.FileStatus[2].pathSuffix", equalTo( "tmp" ) )
        .content( "FileStatuses.FileStatus[3].pathSuffix", equalTo( "user" ) )
        .when().get( namenodePath + "/" );
    if( MOCK ) {
      assertThat( namenode.getCount(), is( 0 ) );
    }

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

    The client receives a 201 Created respond with zero content length and the WebHDFS URI of the file in the Location header:
    HTTP/1.1 201 Created
    Location: webhdfs://<HOST>:<PORT>/<PATH>
    Content-Length: 0
     */
    if( MOCK ) {
      namenode
          .expect()
          .method( "PUT" )
          .pathInfo( "/webhdfs/v1/test/file" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "CREATE" )
          .respond()
          .status( HttpStatus.SC_TEMPORARY_REDIRECT )
          .header( "Location", "http://localhost:" + datanode.getPort() + "/webhdfs/v1/test/file?op=CREATE&user.name=hdfs" );
      datanode
          .expect()
          .method( "PUT" )
          .pathInfo( "/webhdfs/v1/test/file" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "CREATE" )
          .contentType( "text/plain" )
          .content( ClassLoader.getSystemResource( "test.txt" ) )
          //.content( ClassLoader.getSystemResource( "org.apache.hadoop-examples.jar" ) )
          .respond()
          .status( HttpStatus.SC_CREATED )
          .header( "Location", "webhdfs://localhost:" + namenode.getPort() + "/test/file" );
    }
    if( GATEWAY ) {
      Response response = given()
          //.log().all()
          .auth().preemptive().basic( "hdfs", "hdfs-password" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "CREATE" )
          .contentType( "text/plain" )
          .content( IOUtils.toByteArray( ClassLoader.getSystemResourceAsStream( "test.txt" ) ) )
          //.content( IOUtils.toByteArray( ClassLoader.getSystemResourceAsStream( "org.apache.hadoop-examples.jar" ) ) )
          .expect()
          //.log().all()
          .statusCode( HttpStatus.SC_CREATED )
          .when().put( namenodePath + "/test/file" );
      String location = response.getHeader( "Location" );
      log.info( "Location=" + location );
      assertThat( location, startsWith( getWebHdfsPath() ) );
    } else {
      Response response = given()
          //.log().all()
          .auth().preemptive().basic( "hdfs", "hdfs-password" )
//          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "CREATE" )
          .expect()
              //.log().all()
          .statusCode( HttpStatus.SC_TEMPORARY_REDIRECT )
          .when().put( namenodePath + "/test/file" );
      String location = response.getHeader( "Location" );
      log.debug( "Redirect location: " + response.getHeader( "Location" ) );
      response = given()
          //.log().all()
          .auth().preemptive().basic( "hdfs", "hdfs-password" )
          .content( IOUtils.toByteArray( ClassLoader.getSystemResourceAsStream( "test.txt" ) ) )
          .expect()
              //.log().all()
          .statusCode( HttpStatus.SC_CREATED )
          .when().put( location );
      location = response.getHeader( "Location" );
      log.debug( "Created location: " + location );
    }
    if( MOCK ) {
      assertThat( namenode.getCount(), is( 0 ) );
      assertThat( datanode.getCount(), is( 0 ) );
    }

    /* Get the file.
    curl -i -L "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=OPEN
                       [&offset=<LONG>][&length=<LONG>][&buffersize=<INT>]"

    The expect is redirected to a datanode where the file data can be read:
    HTTP/1.1 307 TEMPORARY_REDIRECT
    Location: http://<DATANODE>:<PORT>/webhdfs/v1/<PATH>?op=OPEN...
    Content-Length: 0

    The client follows the redirect to the datanode and receives the file data:
    HTTP/1.1 200 OK
    Content-Type: application/octet-stream
    Content-Length: 22

    Hello, webhdfs user!
    */
    if( MOCK ) {
      namenode
          .expect()
          .method( "GET" )
          .pathInfo( "/webhdfs/v1/test/file" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "OPEN" )
          .respond()
          .status( HttpStatus.SC_TEMPORARY_REDIRECT )
          .header( "Location", "http://localhost:" + datanode.getPort() + "/webhdfs/v1/test/file?op=OPEN&user.name=hdfs" );
      datanode
          .expect()
          .method( "GET" )
          .pathInfo( "/webhdfs/v1/test/file" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "OPEN" )
          .respond()
          .status( HttpStatus.SC_OK )
          .contentType( "text/plain" )
          .content( ClassLoader.getSystemResource( "test.txt" ) );
    }
    given()
        //.log().all()
        .auth().preemptive().basic( "hdfs", "hdfs-password" )
//        .queryParam( "user.name", "hdfs" )
        .queryParam( "op", "OPEN" )
        .expect()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .content( equalTo( "TEST" ) )
        .when().get( namenodePath + "/test/file" );
    if( MOCK ) {
      assertThat( namenode.getCount(), is( 0 ) );
      assertThat( datanode.getCount(), is( 0 ) );
    }

    /* Delete the directory.
    curl -i -X DELETE "http://<host>:<port>/webhdfs/v1/<path>?op=DELETE
                                  [&recursive=<true|false>]"

    The client receives a respond with a boolean JSON object:
    HTTP/1.1 200 OK
    Content-Type: application/json
    Transfer-Encoding: chunked

    {"boolean": true}
     */
    // Mock the interaction with the namenode.
    if( MOCK ) {
      namenode
          .expect()
          .method( "DELETE" )
          .pathInfo( "/webhdfs/v1/test" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "DELETE" )
          .queryParam( "recursive", "true" )
          .respond()
          .status( HttpStatus.SC_OK );
    }
    given()
        .auth().preemptive().basic( "hdfs", "hdfs-password" )
//        .queryParam( "user.name", "hdfs" )
        .queryParam( "op", "DELETE" )
        .queryParam( "recursive", "true" )
        .expect()
        .statusCode( HttpStatus.SC_OK )
        .when().delete( namenodePath + "/test" );
    if( MOCK ) {
      assertThat( namenode.getCount(), is( 0 ) );
    }
  }

}

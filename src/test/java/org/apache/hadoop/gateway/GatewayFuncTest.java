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

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.hadoop.gateway.config.Config;
import org.apache.hadoop.gateway.config.GatewayConfigFactory;
import org.apache.hadoop.gateway.jetty.JettyGatewayFactory;
import org.apache.hadoop.gateway.util.Streams;
import org.apache.hadoop.test.IntegrationTests;
import org.apache.hadoop.test.MediumTests;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;

/**
 *
 */
@Category( { IntegrationTests.class, MediumTests.class } )
public class GatewayFuncTest {

  private static Logger log = LoggerFactory.getLogger( GatewayFuncTest.class );

  private static Server gateway;

  public static void startGateway() throws Exception {

    Map<String,String> params = new HashMap<String,String>();
    params.put( "gateway.address", "localhost:8888" );
    params.put( "namenode.address", "vm.home:50070" );
    params.put( "datanode.address", "vm.home:50075" );

    URL configUrl = ClassLoader.getSystemResource( "org/apache/hadoop/gateway/GatewayFuncTest.xml" );
    Config config = GatewayConfigFactory.create( configUrl, params );

    ContextHandlerCollection contexts = new ContextHandlerCollection();
    contexts.addHandler( JettyGatewayFactory.create( "/gateway/cluster", config ) );

    gateway = new Server( 8888 ); // Picks an open port.
    gateway.setHandler( contexts );
    gateway.start();
  }

  @BeforeClass
  public static void setupSuite() throws Exception {
//    URL loginUrl = ClassLoader.getSystemResource( "jaas.conf" );
//    System.setProperty( "java.security.auth.login.config", loginUrl.getFile() );
//    URL krbUrl = ClassLoader.getSystemResource( "krb5.conf" );
//    System.setProperty( "java.security.krb5.conf", krbUrl.getFile() );

    startGateway();
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    gateway.stop();
    gateway.join();
  }

  private String getGatewayPath() {
    Connector conn = gateway.getConnectors()[0];
    return "http://localhost:" + conn.getLocalPort();
  }

  @Test
  public void testBasicHdfsOperations() throws IOException {
    String namenodePath = getGatewayPath() + "/gateway/cluster/namenode/api/v1";
    //String namenodePath = "http://vm-hdpt:50070/webhdfs/v1";

    // Attempt to delete the test directory in case a previous run failed.
    // Ignore any result.
    given()
        //.log().all()
        .param( "user.name", "hdfs" )
        .param( "op", "DELETE" )
        .param( "recursive", "true" )
        .expect()
        //.log().all()
        .when()
        .delete( namenodePath + "/test" );

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
    given()
        //.log().all()
        .param( "op", "LISTSTATUS" )
        .expect()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .body( "FileStatuses.FileStatus[0].pathSuffix", equalTo( "apps" ) )
        .body( "FileStatuses.FileStatus[1].pathSuffix", equalTo( "mapred" ) )
        .body( "FileStatuses.FileStatus[2].pathSuffix", equalTo( "tmp" ) )
        .body( "FileStatuses.FileStatus[3].pathSuffix", equalTo( "user" ) )
        .when()
        .get( namenodePath + "/" );

    /* Create a directory.
    curl -i -X PUT "http://<HOST>:<PORT>/<PATH>?op=MKDIRS[&permission=<OCTAL>]"

    The client receives a response with a boolean JSON object:
    HTTP/1.1 200 OK
    Content-Type: application/json
    Transfer-Encoding: chunked

    {"boolean": true}
    */
    given()
        .param( "user.name", "hdfs" )
        .param( "op", "MKDIRS" )
        .expect()
        .statusCode( HttpStatus.SC_OK )
        .body( "boolean", equalTo( true ) )
        .when().put( namenodePath + "/test" );

    given()
        .param( "op", "LISTSTATUS" )
        .expect()
        .statusCode( HttpStatus.SC_OK )
        .body( "FileStatuses.FileStatus[0].pathSuffix", equalTo( "apps" ) )
        .body( "FileStatuses.FileStatus[1].pathSuffix", equalTo( "mapred" ) )
        .body( "FileStatuses.FileStatus[2].pathSuffix", equalTo( "test" ) )
        .body( "FileStatuses.FileStatus[3].pathSuffix", equalTo( "tmp" ) )
        .body( "FileStatuses.FileStatus[4].pathSuffix", equalTo( "user" ) )
        .when().get( namenodePath + "/" );

//    given()
//        .param( "user.name", "hdfs" )
//        .param( "op", "DELETE" )
//        .expect()
//        .statusCode( 200 )
//        .body( "boolean", equalTo( true ) )
//        .when().delete( namenodePath + "/test" );

    /* Add a file.
    curl -i -X PUT "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=CREATE
                        [&overwrite=<true|false>][&blocksize=<LONG>][&replication=<SHORT>]
                        [&permission=<OCTAL>][&buffersize=<INT>]"

    The request is redirected to a datanode where the file data is to be written:
    HTTP/1.1 307 TEMPORARY_REDIRECT
    Location: http://<DATANODE>:<PORT>/webhdfs/v1/<PATH>?op=CREATE...
    Content-Length: 0

    Step 2: Submit another HTTP PUT request using the URL in the Location header with the file data to be written.
    curl -i -X PUT -T <LOCAL_FILE> "http://<DATANODE>:<PORT>/webhdfs/v1/<PATH>?op=CREATE..."

    The client receives a 201 Created response with zero content length and the WebHDFS URI of the file in the Location header:
    HTTP/1.1 201 Created
    Location: webhdfs://<HOST>:<PORT>/<PATH>
    Content-Length: 0
     */
    Response response = given()
        .param( "user.name", "hdfs" )
        .param( "op", "CREATE" )
        .body( Streams.drainStream( ClassLoader.getSystemResourceAsStream( "test.txt" ) ) )
        .expect()
        .statusCode( HttpStatus.SC_TEMPORARY_REDIRECT )
        .when().put( namenodePath + "/test/file" );
    String location = response.getHeader( "Location" );
    assertThat( location, startsWith( getGatewayPath() + "/gateway/cluster" ) );

    log.info( "Location=" + location );
    given()
        .param( "user.name", "hdfs" )
        .param( "op", "CREATE" )
        .body( Streams.drainStream( ClassLoader.getSystemResourceAsStream( "test.txt" ) ) )
        .expect()
        .statusCode( HttpStatus.SC_CREATED )
        //.header( "Location", equalTo( location ) )
        .when().put( location );

    /* Get the file.
    curl -i -L "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=OPEN
                       [&offset=<LONG>][&length=<LONG>][&buffersize=<INT>]"

    The request is redirected to a datanode where the file data can be read:
    HTTP/1.1 307 TEMPORARY_REDIRECT
    Location: http://<DATANODE>:<PORT>/webhdfs/v1/<PATH>?op=OPEN...
    Content-Length: 0

    The client follows the redirect to the datanode and receives the file data:
    HTTP/1.1 200 OK
    Content-Type: application/octet-stream
    Content-Length: 22

    Hello, webhdfs user!
    */
    given()
        .param( "user.name", "hdfs" )
        .param( "op", "OPEN" )
        .expect()
        .statusCode( HttpStatus.SC_OK )
        .body( equalTo( "TEST" ) )
        .when().get( namenodePath + "/test/file" );

    /* Delete the directory.
    curl -i -X DELETE "http://<host>:<port>/webhdfs/v1/<path>?op=DELETE
                                  [&recursive=<true|false>]"

    The client receives a response with a boolean JSON object:
    HTTP/1.1 200 OK
    Content-Type: application/json
    Transfer-Encoding: chunked

    {"boolean": true}
     */
    given()
        .param( "user.name", "hdfs" )
        .param( "op", "DELETE" )
        .param( "recursive", "true" )
        .when().delete( namenodePath + "/test" );
  }

}

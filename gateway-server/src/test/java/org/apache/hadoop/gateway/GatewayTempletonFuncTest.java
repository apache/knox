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
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.gateway.descriptor.ClusterDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterDescriptorFactory;
import org.apache.hadoop.gateway.jetty.JettyGatewayFactory;
import org.apache.hadoop.gateway.security.EmbeddedApacheDirectoryServer;
import org.apache.hadoop.gateway.util.Streams;
import org.apache.hadoop.test.category.IntegrationTests;
import org.apache.hadoop.test.category.MediumTests;
import org.apache.hadoop.test.mock.MockServer;
import org.apache.http.HttpStatus;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.HttpMethod;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.path.json.JsonPath.from;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.junit.Assert.assertThat;

@Category( { IntegrationTests.class, MediumTests.class } )
public class GatewayTempletonFuncTest {

//  @Test
//  public void demoWait() throws IOException {
//    System.out.println( "Press any key to continue. Server at " + getGatewayPath() );
//    System.in.read();
//  }

  private static Logger log = LoggerFactory.getLogger( GatewayTempletonFuncTest.class );

  private static boolean MOCK = Boolean.parseBoolean( System.getProperty( "MOCK", "true" ) );
  //private static boolean MOCK = false;
  private static boolean GATEWAY = Boolean.parseBoolean( System.getProperty( "GATEWAY", "true" ) );
  //private static boolean GATEWAY = false;

  private static final int LDAP_PORT = 33389;
  private static final int GATEWAY_PORT = 8888;

  private static String TEST_HOST_NAME = "vm.home";
  private static String NAME_NODE_ADDRESS = TEST_HOST_NAME + ":50070";
  private static String DATA_NODE_ADDRESS = TEST_HOST_NAME + ":50075";
  private static String TEMPLETON_ADDRESS = TEST_HOST_NAME + ":50111";

  private static EmbeddedApacheDirectoryServer ldap;
  private static Server gateway;
  private static MockServer namenode;
  private static MockServer datanode;
  private static MockServer templeton;

  public static void startGateway() throws Exception {

    Map<String,String> params = new HashMap<String,String>();
    params.put( "gateway.address", "localhost:" + GATEWAY_PORT );
    if( MOCK ) {
      params.put( "namenode.address", "localhost:" + namenode.getPort() );
      params.put( "datanode.address", "localhost:" + datanode.getPort() );
      params.put( "templeton.address", "localhost:" + templeton.getPort() );
    } else {
      params.put( "namenode.address", NAME_NODE_ADDRESS );
      params.put( "datanode.address", DATA_NODE_ADDRESS );
      params.put( "templeton.address", TEMPLETON_ADDRESS );
    }

    URL configUrl = ClassLoader.getSystemResource( "org/apache/hadoop/gateway/GatewayFuncTest.xml" );
    Reader configReader = new InputStreamReader( configUrl.openStream() );
    ClusterDescriptor config = ClusterDescriptorFactory.load( "xml", configReader );
    //Config config = ClusterConfigFactory.create( configUrl, params );

    Handler handler = JettyGatewayFactory.create( "/org/apache/org.apache.hadoop/gateway/cluster", config );
    ContextHandlerCollection contexts = new ContextHandlerCollection();
    contexts.addHandler( handler );

    gateway = new Server( GATEWAY_PORT );
    gateway.setHandler( contexts );
    gateway.start();
  }

  private static void startLdap() throws Exception{
    URL usersUrl = ClassLoader.getSystemResource( "users.ldif" );
    ldap = new EmbeddedApacheDirectoryServer( "dc=hadoop,dc=apache,dc=org", null, LDAP_PORT );
    ldap.start();
    ldap.loadLdif( usersUrl );
  }

  @BeforeClass
  public static void setupSuite() throws Exception {
//    org.apache.log4j.Logger.getLogger( "org.apache.shiro" ).setLevel( org.apache.log4j.Level.ALL );
//    org.apache.log4j.Logger.getLogger( "org.apache.http" ).setLevel( org.apache.log4j.Level.ALL );
//    org.apache.log4j.Logger.getLogger( "org.apache.http.wire" ).setLevel( org.apache.log4j.Level.ALL );
//    org.apache.log4j.Logger.getLogger( "org.apache.http.impl.conn" ).setLevel( org.apache.log4j.Level.ALL );
//    org.apache.log4j.Logger.getLogger( "org.apache.http.impl.client" ).setLevel( org.apache.log4j.Level.ALL );
//    org.apache.log4j.Logger.getLogger( "org.apache.http.client" ).setLevel( org.apache.log4j.Level.ALL );

//    URL loginUrl = ClassLoader.getSystemResource( "jaas.conf" );
//    System.setProperty( "java.security.auth.login.config", loginUrl.getFile() );
//    URL krbUrl = ClassLoader.getSystemResource( "krb5.conf" );
//    System.setProperty( "java.security.krb5.conf", krbUrl.getFile() );

    startLdap();
    namenode = new MockServer( "NameNode", true );
    datanode = new MockServer( "DataNode", true );
    templeton = new MockServer( "Templeton", true );
    startGateway();
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    gateway.stop();
    gateway.join();
    namenode.stop();
    datanode.stop();
    templeton.stop();
    ldap.stop();
  }

  private String getGatewayPath() {
    Connector conn = gateway.getConnectors()[0];
    return "http://localhost:" + conn.getLocalPort();
  }

  private String getWebHdfsPath() {
    return GATEWAY ? getGatewayPath()+ "/gateway/cluster/namenode/api/v1" : "http://"+NAME_NODE_ADDRESS+"/webhdfs/v1";
  }

  private String getTempletonPath() {
    return GATEWAY ? getGatewayPath()+ "/gateway/cluster/templeton/api/v1" : "http://"+TEMPLETON_ADDRESS+"/templeton/v1";
  }

  @Test
  public void testBasicTempletonUseCase() throws IOException {
    String hdfsPath = getWebHdfsPath();
    String templetonPath = getTempletonPath();
    Response response;
    String location;

    /* Delete anything left over from a previous run.
    curl -X DELETE 'http://192.168.1.163:8888/org.apache.org.apache.hadoop.gateway/cluster/namenode/api/v1/user/hdfs/wordcount?user.name=hdfs&op=DELETE&recursive=true'
     */
    if( MOCK ) {
      namenode
          .expect()
          .method( HttpMethod.DELETE )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "DELETE" )
          .respond()
          .status( HttpStatus.SC_OK )
          .contentType( "application/json" )
          .content( "{\"boolean\":true}".getBytes() );
    }
    given()
        //.log().all()
        .auth().preemptive().basic( "allowedUser", "password" )
        .queryParam( "user.name", "hdfs" )
        .queryParam( "op", "DELETE" )
        .queryParam( "recursive", "true" )
        .expect()
        //.log().all()
        .statusCode( isOneOf( HttpStatus.SC_OK, HttpStatus.SC_NOT_FOUND ) )
        .when()
        .delete( hdfsPath + "/user/hdfs/test" );
    if( MOCK ) {
      assertThat( namenode.isEmpty(), is( true ) );
    }

    /* Put the mapreduce code into HDFS. (org.apache.hadoop-examples.jar)
    curl -X PUT --data-binary @org.apache.hadoop-examples.jar 'http://192.168.1.163:8888/org.apache.org.apache.hadoop.gateway/cluster/namenode/api/v1/user/hdfs/wordcount/org.apache.hadoop-examples.jar?user.name=hdfs&op=CREATE'
     */
    if( MOCK ) {
      namenode
          .expect()
          .method( "PUT" )
          .pathInfo( "/webhdfs/v1/user/hdfs/test/org.apache.hadoop-examples.jar" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "CREATE" )
          .respond()
          .status( HttpStatus.SC_TEMPORARY_REDIRECT )
          .header( "Location", "http://localhost:" + datanode.getPort() + "/webhdfs/v1/user/hdfs/test/org.apache.hadoop-examples.jar?op=CREATE&user.name=hdfs" );
      datanode
          .expect()
          .method( "PUT" )
          .pathInfo( "/webhdfs/v1/user/hdfs/test/org.apache.hadoop-examples.jar" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "CREATE" )
          .content( ClassLoader.getSystemResourceAsStream( "hadoop-examples.jar" ) )
          .respond()
          .status( HttpStatus.SC_CREATED )
          .header( "Location", "webhdfs://localhost:" + namenode.getPort() + "/user/hdfs/test/org.apache.hadoop-examples.jar" );
    }
    if( GATEWAY ) {
      response = given()
          //.log().all()
          .auth().preemptive().basic( "allowedUser", "password" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "CREATE" )
          .content( IOUtils.toByteArray( ClassLoader.getSystemResourceAsStream( "hadoop-examples.jar" ) ) )
          .expect()
          //.log().all()
          .statusCode( HttpStatus.SC_CREATED )
          .when().put( hdfsPath + "/user/hdfs/test/org.apache.hadoop-examples.jar" );
      location = response.getHeader( "Location" );
      log.debug( "Created location: " + location );
    } else {
      response = given()
          //.log().all()
          .auth().preemptive().basic( "allowedUser", "password" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "CREATE" )
          .expect()
          //.log().all()
          .statusCode( HttpStatus.SC_TEMPORARY_REDIRECT )
          .when().put( hdfsPath + "/user/hdfs/test/org.apache.hadoop-examples.jar" );
      location = response.getHeader( "Location" );
      log.debug( "Redirect location: " + response.getHeader( "Location" ) );
      response = given()
          //.log().all()
          .auth().preemptive().basic( "allowedUser", "password" )
          .content( IOUtils.toByteArray( ClassLoader.getSystemResourceAsStream( "hadoop-examples.jar" ) ) )
          .expect()
          //.log().all()
          .statusCode( HttpStatus.SC_CREATED )
          .when().put( location );
      location = response.getHeader( "Location" );
      log.debug( "Created location: " + location );
    }
    if( MOCK ) {
      assertThat( namenode.isEmpty(), is( true ) );
      assertThat( datanode.isEmpty(), is( true ) );
    }

    /* Put the data file into HDFS (CHANGES.txt)
    curl -X PUT --data-binary @CHANGES.txt 'http://192.168.1.163:8888/org.apache.org.apache.hadoop.gateway/cluster/namenode/api/v1/user/hdfs/wordcount/input/CHANGES.txt?user.name=hdfs&op=CREATE'
     */
    if( MOCK ) {
      namenode
          .expect()
          .method( "PUT" )
          .pathInfo( "/webhdfs/v1/user/hdfs/test/input/CHANGES.txt" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "CREATE" )
          .respond()
          .status( HttpStatus.SC_TEMPORARY_REDIRECT )
          .header( "Location", "http://localhost:" + datanode.getPort() + "/webhdfs/v1/user/hdfs/test/input/CHANGES.txt?op=CREATE&user.name=hdfs" );
      datanode
          .expect()
          .method( "PUT" )
          .pathInfo( "/webhdfs/v1/user/hdfs/test/input/CHANGES.txt" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "CREATE" )
          .content( Streams.drainStream( ClassLoader.getSystemResourceAsStream( "CHANGES.txt" ) ) )
          .respond()
          .status( HttpStatus.SC_CREATED )
          .header( "Location", "webhdfs://localhost:" + namenode.getPort() + "/user/hdfs/test/input/CHANGES.txt" );
    }
    if( GATEWAY ) {
      response = given()
          //.log().all()
          .auth().preemptive().basic( "allowedUser", "password" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "CREATE" )
          .content( Streams.drainStream( ClassLoader.getSystemResourceAsStream( "CHANGES.txt" ) ) )
          .expect()
          //.log().all()
          .statusCode( HttpStatus.SC_CREATED )
          .when().put( hdfsPath + "/user/hdfs/test/input/CHANGES.txt" );
      location = response.getHeader( "Location" );
      log.debug( "Created location: " + location );
    } else {
      response = given()
          //.log().all()
          .auth().preemptive().basic( "allowedUser", "password" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "CREATE" )
          .expect()
          //.log().all()
          .statusCode( HttpStatus.SC_TEMPORARY_REDIRECT )
          .when().put( hdfsPath + "/user/hdfs/test/input/CHANGES.txt" );
      location = response.getHeader( "Location" );
      log.debug( "Redirect location: " + response.getHeader( "Location" ) );
      response = given()
          //.log().all()
          .auth().preemptive().basic( "allowedUser", "password" )
          .content( Streams.drainStream( ClassLoader.getSystemResourceAsStream( "CHANGES.txt" ) ) )
          .expect()
          //.log().all()
          .statusCode( HttpStatus.SC_CREATED )
          .when().put( location );
      location = response.getHeader( "Location" );
      log.debug( "Created location: " + location );
    }
    if( MOCK ) {
      assertThat( namenode.isEmpty(), is( true ) );
      assertThat( datanode.isEmpty(), is( true ) );
    }

    /* Create the output directory
    curl -X PUT 'http://192.168.1.163:8888/org.apache.org.apache.hadoop.gateway/cluster/namenode/api/v1/user/hdfs/wordcount/output?op=MKDIRS&user.name=hdfs'
    */
    if( MOCK ) {
      namenode
          .expect()
          .method( "PUT" )
          .pathInfo( "/webhdfs/v1/user/hdfs/test/output" )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "MKDIRS" )
          .respond()
          .status( HttpStatus.SC_OK )
          .content( ClassLoader.getSystemResource( "webhdfs-success.json" ) )
          .contentType( "application/json" );
    }
    String mkdirs = given()
        //.log().all()
        .auth().preemptive().basic( "allowedUser", "password" )
        .queryParam( "user.name", "hdfs" )
        .queryParam( "op", "MKDIRS" )
        .expect()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .content( "boolean", equalTo( true ) )
        .when().put( hdfsPath + "/user/hdfs/test/output" ).asString();
    log.debug( "MKDIRS=" + mkdirs );
    if( MOCK ) {
      assertThat( namenode.isEmpty(), is( true ) );
    }

    /* Submit the job
    curl -d user.name=hdfs -d jar=wordcount/org.apache.hadoop-examples.jar -d class=org.apache.org.apache.hadoop.examples.WordCount -d arg=wordcount/input -d arg=wordcount/output 'http://localhost:8888/org.apache.org.apache.hadoop.gateway/cluster/templeton/api/v1/mapreduce/jar'
    ﻿{"id":"job_201210301335_0059"}
    */
    if( MOCK ) {
      templeton
          .expect()
          .method( "POST" )
          .pathInfo( "/templeton/v1/mapreduce/jar" )
          .respond()
          .status( HttpStatus.SC_OK )
          .content( "{\"id\":\"job_201210301335_0086\"}".getBytes() )
          .contentType( "application/json" );
    }
    String json = given()
        //.log().all()
        .auth().preemptive().basic( "allowedUser", "password" )
        .formParam( "user.name", "hdfs" )
        .formParam( "jar", "test/org.apache.hadoop-examples.jar" )
        .formParam( "class", "org.apache.org.apache.hadoop.examples.WordCount" )
        .formParam( "arg", "test/input", "test/output" )
        .expect()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .when().post( templetonPath + "/mapreduce/jar" ).asString();
    log.info( "JSON=" + json );
    String job = from( json ).getString( "id" );
    log.debug( "JOB=" + job );
    if( MOCK ) {
      assertThat( namenode.isEmpty(), is( true ) );
    }

    /* Get the job status
    curl 'http://vm:50111/templeton/v1/queue/:jobid?user.name=hdfs'
    */
    if( MOCK ) {
      templeton
          .expect()
          .method( "GET" )
          .pathInfo( "/templeton/v1/queue/" + job )
          .respond()
          .status( HttpStatus.SC_OK )
          .content( ClassLoader.getSystemResource( "templeton-job-status.json" ) )
          .contentType( "application/json" );
    }
    String status = given()
        //.log().all()
        .auth().preemptive().basic( "allowedUser", "password" )
        .queryParam( "user.name", "hdfs" )
        .pathParam( "job", job )
        .expect()
         //.log().all()
        .content( "status.jobId", equalTo( job ) )
        .statusCode( HttpStatus.SC_OK )
        .when().get( templetonPath + "/queue/{job}" ).asString();
    log.info( "STATUS=" + status );
    if( MOCK ) {
      assertThat( namenode.isEmpty(), is( true ) );
    }

    // Can't really check for the output here because the job won't be done.
    /* Retrieve results
    curl 'http://192.168.1.163:8888/org.apache.org.apache.hadoop.gateway/cluster/namenode/api/v1/user/hdfs/wordcount/input?op=LISTSTATUS'
    */
    if( MOCK ) {
      namenode
          .expect()
          .method( "GET" )
          .pathInfo( "/webhdfs/v1/user/hdfs/test/output" )
          .queryParam( "op", "LISTSTATUS" )
          .respond()
          .status( HttpStatus.SC_OK )
          .content( ClassLoader.getSystemResource( "webhdfs-liststatus-empty.json" ) )
          .contentType( "application/json" );
    }
    String list = given()
        //.log().all()
        .auth().preemptive().basic( "allowedUser", "password" )
        .queryParam( "user.name", "hdfs" )
        .queryParam( "op", "LISTSTATUS" )
        .expect()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        //.content( "FileStatuses.FileStatus[0].pathSuffix", equalTo( "apps" ) )
        .when()
        .get( hdfsPath + "/user/hdfs/test/output" ).asString();
    log.debug( "LISTSTATUS=" + list );
    if( MOCK ) {
      assertThat( namenode.isEmpty(), is( true ) );
    }
  }

}

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
import org.apache.hadoop.gateway.util.Streams;
import org.apache.hadoop.test.category.FunctionalTests;
import org.apache.hadoop.test.category.MediumTests;
import org.apache.hadoop.test.mock.MockServer;
import org.apache.http.HttpStatus;
import org.hamcrest.MatcherAssert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.HttpMethod;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.UUID;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.path.json.JsonPath.from;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

@Category( { FunctionalTests.class, MediumTests.class } )
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

  private static String TEST_HOST_NAME = "vm.home";
  private static String NAME_NODE_ADDRESS = TEST_HOST_NAME + ":50070";
  private static String TEMPLETON_ADDRESS = TEST_HOST_NAME + ":50111";
  //private static String DATA_NODE_ADDRESS = TEST_HOST_NAME + ":50075";

  private static EmbeddedApacheDirectoryServer ldap;
  private static GatewayServer gateway;
  private static MockServer namenode;
  private static MockServer datanode;
  private static MockServer templeton;

  private static GatewayTestConfig config;

  @BeforeClass
  public static void setupSuite() throws Exception {
    namenode = new MockServer( "NameNode", true );
    datanode = new MockServer( "DataNode", true );
    templeton = new MockServer( "Templeton", true );
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
              .addTag( "param" )
                .addTag( "name" ).addText( "main.ldapRealm.userDnTemplate" )
                .addTag( "value" ).addText( "uid={0},ou=people,dc=hadoop,dc=apache,dc=org" )
              .addTag( "param" )
                .addTag( "name" ).addText( "main.ldapRealm.contextFactory.url" )
                .addTag( "value" ).addText( "ldap://localhost:33389" )
              .addTag( "param" )
                .addTag( "name" ).addText( "main.ldapRealm.contextFactory.authenticationMechanism" )
                .addTag( "value" ).addText( "simple" )
              .addTag( "param" )
                .addTag( "name" ).addText( "urls./**" )
                .addTag( "value" ).addText( "authcBasic" )
        .gotoRoot()
          .addTag( "service" )
            .addTag( "role" ).addText( "NAMENODE" )
            .addTag( "url" ).addText( "http://localhost:" + namenode.getPort() + "/webhdfs/v1" )
            .gotoParent()
          .addTag( "service" )
            .addTag( "role" ).addText( "TEMPLETON" )
            .addTag( "url" ).addText( "http://localhost:" + templeton.getPort() + "/templeton/v1" )
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
    MatcherAssert.assertThat( "Failed to start gateway.", gateway, notNullValue() );
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
        .log().ifError()
        .statusCode( anyOf( equalTo( HttpStatus.SC_OK ), equalTo( HttpStatus.SC_NOT_FOUND ) ) )
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

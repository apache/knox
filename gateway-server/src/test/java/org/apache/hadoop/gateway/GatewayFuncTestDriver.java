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
import com.mycila.xmltool.XMLTag;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.security.EmbeddedApacheDirectoryServer;
import org.apache.hadoop.test.mock.MockServer;
import org.apache.http.HttpStatus;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.path.json.JsonPath.from;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertThat;

public class GatewayFuncTestDriver {

  private static Logger log = LoggerFactory.getLogger( GatewayFuncTestDriver.class );

  public Class<?> resourceBaseClass;
  public Map<String,Service> services = new HashMap<String,Service>();
  public EmbeddedApacheDirectoryServer ldap;
  public boolean useGateway;
  public GatewayServer gateway;
  public GatewayConfig config;

  public void setResourceBase( Class<?> resourceBaseClass ) {
    this.resourceBaseClass = resourceBaseClass;
  }

  public int setupLdap( int port ) throws Exception {
    URL usersUrl = getResourceUrl( "users.ldif" );
    ldap = new EmbeddedApacheDirectoryServer( "dc=hadoop,dc=apache,dc=org", null, port );
    ldap.start();
    ldap.loadLdif( usersUrl );
    log.info( "LDAP port = " + port );
    return port;
  }

  public void setupService( String role, String realUrl, String gatewayPath, boolean mock ) throws Exception {
    Service service = new Service( role, realUrl, gatewayPath, mock );
    services.put( role, service );
    log.info( role + " port = " + service.server.getPort() );
  }

  public void setupGateway( GatewayTestConfig config, String cluster, XMLTag topology, boolean use ) throws IOException {
    this.useGateway = use;

    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();
    File deployDir = new File( gatewayDir, config.getDeploymentDir() );
    deployDir.mkdirs();

    this.config = config;
    config.setGatewayHomeDir( gatewayDir.getAbsolutePath() );
    config.setDeploymentDir( "clusters" );

    File descriptor = new File( deployDir, cluster + ".xml" );
    FileOutputStream stream = new FileOutputStream( descriptor );
    topology.toStream( stream );
    stream.close();

    gateway = GatewayServer.startGateway( config );
    MatcherAssert.assertThat( "Failed to start gateway.", gateway, notNullValue() );

    log.info( "Gateway port = " + gateway.getAddresses()[ 0 ].getPort() );
  }

  public void cleanup() throws Exception {
    gateway.stop();
    FileUtils.deleteQuietly( new File( config.getGatewayHomeDir() ) );

    for( Service service : services.values() ) {
      service.server.stop();
    }
    services.clear();

    ldap.stop();
  }

  public MockServer getMock( String serviceRole ) {
    Service service = services.get( serviceRole );
    return service.server;
  }

  public String getRealUrl( String serviceRole ) {
    return getUrl( serviceRole, true );
  }

  public String getUrl( String serviceRole ) {
    return getUrl( serviceRole, false );
  }

  public String getUrl( String serviceRole, boolean real ) {
    String url;
    Service service = services.get( serviceRole );
    if( useGateway && !real ) {
      url = "http://localhost:" + gateway.getAddresses()[0].getPort() + "/" + config.getGatewayPath() + service.gatewayPath;
    } else if( service.mock ) {
      url = "http://localhost:" + service.server.getPort();
    } else {
      url = service.realUrl.toExternalForm();
    }
    return url;
  }

  public String getRealAddr( String role ) {
    String addr;
    Service service = services.get( role );
    if( service.mock ) {
      addr = "localhost:" + service.server.getPort();
    } else {
      addr = service.realUrl.getHost() + ":" + service.realUrl.getPort();
    }
    return addr;
  }

  public String getLdapUrl() {
    return "ldap://localhost:" + ldap.getTransport().getPort();
  }

  private static class Service {
    String role;
    URL realUrl;
    String gatewayPath;
    boolean mock;
    MockServer server;
    private Service( String role, String realUrl, String gatewayPath, boolean mock ) throws Exception {
      this.role = role;
      this.realUrl = new URL( realUrl );
      this.gatewayPath = gatewayPath;
      this.mock = mock;
      this.server = new MockServer( role, true );
    }
  }

  public String getResourceBaseName() {
    return resourceBaseClass.getName().replaceAll( "\\.", "/" ) + "/";
  }

  public String getResourceName( String resource ) {
    return getResourceBaseName() + resource;
  }

  public URL getResourceUrl( String resource ) {
    URL url = ClassLoader.getSystemResource( getResourceName( resource ) );
    assertThat( "Failed to find test resource " + resource, url, Matchers.notNullValue() );
    return url;
  }

  public InputStream getResourceStream( String resource ) {
    InputStream stream = ClassLoader.getSystemResourceAsStream( getResourceName( resource ) );
    assertThat( "Failed to find test resource " + resource, stream, Matchers.notNullValue() );
    return stream;
  }

  public byte[] getResourceBytes( String resource ) throws IOException {
    return IOUtils.toByteArray( getResourceStream( resource ) );
  }

  private String getResourceString( String resource ) throws IOException {
    return IOUtils.toString( getResourceBytes( resource ), "UTF-8" );
  }

  public void assertComplete() {
    // Check to make sure that all interaction were satisfied if for mocked services.
    // Otherwise just clear the mock interaction queue.
    for( Service service : services.values() ) {
      if( service.mock ) {
        assertThat( "Service " + service.role + " has expected interactions.",
            service.server.isEmpty(), CoreMatchers.is( true ) );
      }
      service.server.reset();
    }
  }

  public void reset() {
    for( Service service : services.values() ) {
      service.server.reset();
    }
  }

  public String createFileNN( String user, String password, String file, String permsOctal, int status ) throws IOException {
    if( status == HttpStatus.SC_TEMPORARY_REDIRECT ) {
      getMock( "NAMENODE" )
          .expect()
          .method( "PUT" )
          .pathInfo( file )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .respond()
          .status( status )
          .header( "Location", getRealUrl("DATANODE") + file + "?op=CREATE&user.name="+user );
    } else {
      getMock( "NAMENODE" )
          .expect()
          .method( "PUT" )
          .pathInfo( file )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .respond()
          .status( status );
    }
    Response response = given()
        //.log().headers()
        //.log().parameters()
        .auth().preemptive().basic( user, password )
        .queryParam( "op", "CREATE" )
        .queryParam( "permission", permsOctal )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().put( getUrl( "NAMENODE" ) + file );
    String location = response.getHeader( "Location" );
    log.trace( "Redirect location: " + response.getHeader( "Location" ) );
    return location;
  }

  public int createFileDN( String user, String password, String path, String location, String contentType, String resource, int status ) throws IOException {
    if( status == HttpStatus.SC_CREATED ) {
      getMock( "DATANODE" )
          .expect()
          .method( "PUT" )
          .pathInfo( path )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .contentType( contentType )
          .content( getResourceBytes( resource ) )
          .respond()
          .status( status )
          .header( "Location", "webhdfs://" + getRealAddr( "DATANODE" ) + path );
    } else {
      getMock( "DATANODE" )
          .expect()
          .method( "PUT" )
          .pathInfo( path )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .contentType( contentType )
          .content( getResourceStream( resource ) )
          .respond()
          .status( status );
    }
    Response response = given()
        //.log().headers()
        //.log().params()
        .auth().preemptive().basic( user, password )
        .contentType( contentType )
        .content( getResourceBytes( resource ) )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().put( location );
    return response.getStatusCode();
  }

  public String createFile(
      String user, String password, String group, String file, String permsOctal, String contentType, String resource,
      int nnStatus, int dnStatus, int chownStatus ) throws IOException {
    String location = createFileNN( user, password, file, permsOctal, nnStatus );
    if( location != null ) {
      int status = createFileDN( user, password, file, location, contentType, resource, dnStatus );
      if( status < 300 && permsOctal != null ) {
        chownFile( user, password, file, user, group, chownStatus );
      }
    }
    assertComplete();
    return location;
  }

  public void readFile( String user, String password, String file, String contentType, String resource, int status ) throws IOException {
    getMock( "NAMENODE" )
        .expect()
        .method( "GET" )
        .pathInfo( file )
        .queryParam( "user.name", user )
        .queryParam( "op", "OPEN" )
        .respond()
        .status( HttpStatus.SC_TEMPORARY_REDIRECT )
        .header( "Location", getRealUrl( "DATANODE" ) + file + "?op=OPEN&user.name="+user );
    if( status == HttpStatus.SC_OK ) {
      getMock( "DATANODE" )
          .expect()
          .method( "GET" )
          .pathInfo( file )
          .queryParam( "user.name", user )
          .queryParam( "op", "OPEN" )
          .respond()
          .status( status )
          .contentType( contentType )
          .content( getResourceBytes( resource ) );
    } else {
      getMock( "DATANODE" )
          .expect()
          .method( "GET" )
          .pathInfo( file )
          .queryParam( "user.name", user )
          .queryParam( "op", "OPEN" )
          .respond()
          .status( status );
    }
    Response response = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .queryParam( "op", "OPEN" )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().get( getUrl("NAMENODE") + file );
    if( response.getStatusCode() == HttpStatus.SC_OK ) {
      String actualContent = response.asString();
      String expectedContent = getResourceString( resource );
      assertThat( actualContent, is( expectedContent ) );
    }
    assertComplete();
  }

  public void chownFile( String user, String password, String file, String owner, String group, int status ) {
    getMock( "NAMENODE" )
        .expect()
        .method( "PUT" )
        .pathInfo( file )
        .queryParam( "op", "SETOWNER" )
        .queryParam( "user.name", user )
        .queryParam( "owner", owner )
        .queryParam( "group", group )
        .respond()
        .status( HttpStatus.SC_OK );
    given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .queryParam( "op", "SETOWNER" )
        .queryParam( "owner", owner )
        .queryParam( "group", group )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().put( getUrl("NAMENODE") + file );
    assertComplete();
  }

  public void chmodFile( String user, String password, String file, String permsOctal, int status ) {
    getMock( "NAMENODE" )
        .expect()
        .method( "PUT" )
        .pathInfo( file )
        .queryParam( "op", "SETPERMISSION" )
        .queryParam( "user.name", user )
        .queryParam( "permission", permsOctal )
        .respond()
        .status( HttpStatus.SC_OK );
    given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .queryParam( "op", "SETPERMISSION" )
        .queryParam( "permission", permsOctal )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().put( getUrl("NAMENODE") + file );
    assertComplete();
  }

  public String updateFile( String user, String password, String file, String contentType, String resource, int nnStatus, int dnStatus ) throws IOException {
    String location;
    location = updateFileNN( user, password, file, resource, nnStatus );
    if( location != null ) {
      updateFileDN( user, password, file, location, contentType, resource, dnStatus );
    }
    assertComplete();
    return location;
  }

  public String updateFileNN( String user, String password, String file, String resource, int status ) throws IOException {
    if( status == HttpStatus.SC_TEMPORARY_REDIRECT ) {
      getMock( "NAMENODE" )
          .expect()
          .method( "PUT" )
          .pathInfo( file )
          .queryParam( "op", "CREATE" )
          .queryParam( "user.name", user )
          .queryParam( "overwrite", "true" )
          .respond()
          .status( status )
          .header( "Location", getRealUrl("DATANODE") + file + "?op=CREATE&user.name="+user );
    } else {
      getMock( "NAMENODE" )
          .expect()
          .method( "PUT" )
          .pathInfo( file )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .respond()
          .status( status );
    }
    Response response = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .queryParam( "op", "CREATE" )
        .queryParam( "overwrite", "true" )
        .content( getResourceBytes( resource ) )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().put( getUrl("NAMENODE") + file );
    String location = response.getHeader( "Location" );
    log.trace( "Redirect location: " + response.getHeader( "Location" ) );
    return location;
  }

  public void updateFileDN( String user, String password, String path, String location, String contentType, String resource, int status ) throws IOException {
    if( status == HttpStatus.SC_CREATED ) {
      getMock( "DATANODE" )
          .expect()
          .method( "PUT" )
          .pathInfo( path )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .contentType( contentType )
          .content( getResourceBytes( resource ) )
          .respond()
          .status( status )
          .header( "Location", "webhdfs://" + getRealAddr( "DATANODE" ) + path );
    } else {
      getMock( "DATANODE" )
          .expect()
          .method( "PUT" )
          .pathInfo( path )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .contentType( contentType )
          .content( getResourceBytes( resource ) )
          .respond()
          .status( status );
    }
    Response response = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .queryParam( "op", "CREATE" )
        .queryParam( "overwrite", "true" )
        .contentType( contentType )
        .content( getResourceBytes( resource ) )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().put( location );
  }

  public void deleteFile( String user, String password, String file, String recursive, int... status ) {
    getMock( "NAMENODE" )
        .expect()
        .method( "DELETE" )
        .pathInfo( file )
        .queryParam( "user.name", user )
        .queryParam( "op", "DELETE" )
        .queryParam( "recursive", recursive )
        .respond().status( status[0] );
    given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .queryParam( "op", "DELETE" )
        .queryParam( "recursive", recursive )
        .expect()
        //.log().all()
        .statusCode( isIn( ArrayUtils.toObject( status ) ) )
        .when()
        .delete( getUrl( "NAMENODE" ) + file );
    assertComplete();
  }

  public String createDir( String user, String password, String dir, String permsOctal, int status ) {
    getMock( "NAMENODE" )
        .expect()
        .method( "PUT" )
        .pathInfo( dir )
        .queryParam( "op", "MKDIRS" )
        .queryParam( "user.name", user )
        .queryParam( "permission", permsOctal )
        .respond()
        .status( HttpStatus.SC_OK )
        .contentType( "application/json" )
        .content( "{\"boolean\": true}".getBytes() );
    Response response = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .queryParam( "op", "MKDIRS" )
        .queryParam( "permission", permsOctal )
        .expect()
        //.log().all()
        .statusCode( status )
        .contentType( "application/json" )
        .content( "boolean", equalTo( true ) )
        .when()
        .put( getUrl("NAMENODE") + dir );
    String location = response.getHeader( "Location" );
    return location;
  }

  public String createDir( String user, String password, String group, String dir, String permsOctal, int nnStatus, int chownStatus ) {
    String location = createDir( user, password, dir, permsOctal, nnStatus );
    if( location != null ) {
      chownFile( user, password, dir, user, group, chownStatus );
    }
    return location;
  }

  public void readDir( String user, String password, String dir, String resource, int status ) {
    given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .queryParam( "op", "LISTSTATUS" )
        .expect()
        //.log().all()
        .statusCode( status )
        .content( equalTo( "TODO" ) )
        .when()
        .get( getUrl( "NAMENODE" ) + dir );
  }

  public String submitJava( String user, String password, String jar, String main, String input, String output, int status ) {
    getMock( "TEMPLETON" )
        .expect()
        .method( "POST" )
        .pathInfo( "/mapreduce/jar" )
        .respond()
        .status( status )
        .contentType( "application/json" )
        .content( "{\"id\":\"job_201210301335_0086\"}".getBytes() );
    String json = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        //.formParam( "user.name", user )
        .formParam( "jar", jar )    //"/user/hdfs/test/hadoop-examples.jar" )
        .formParam( "class", main ) //"org.apache.org.apache.hadoop.examples.WordCount" )
        .formParam( "arg", input, output ) //.formParam( "arg", "/user/hdfs/test/input", "/user/hdfs/test/output" )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().post( getUrl( "TEMPLETON" ) + "/mapreduce/jar" ).asString();
    log.trace( "JSON=" + json );
    String job = from( json ).getString( "id" );
    log.debug( "JOB=" + job );
    assertComplete();
    return job;
  }

  public String submitPig( String user, String password, String group, String file, String arg, String statusDir, int... status ) {
    getMock( "TEMPLETON" )
        .expect()
        .method( "POST" )
        .pathInfo( "/pig" )
        .respond()
        .status( status[0] )
        .contentType( "application/json" )
        .content( "{\"id\":\"job_201210301335_0086\"}".getBytes() );
    String json = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
//BUG: The identity asserter needs to check for this too.
        .formParam( "user.name", user )
        .queryParam( "group", group )
        .formParam( "file", file )
        .queryParam( "arg", arg )
        .queryParam( "statusdir", statusDir )
        .expect()
        //.log().all();
        .statusCode( isIn( ArrayUtils.toObject( status ) ) )
        .contentType( "application/json" )
        //.content( "boolean", equalTo( true ) )
        .when()
        .post( getUrl( "TEMPLETON" ) + "/pig" )
        .asString();
    log.trace( "JSON=" + json );
    String job = from( json ).getString( "id" );
    log.debug( "JOB=" + job );
    assertComplete();
    return job;
  }

  public String submitHive( String user, String password, String group, String file, String statusDir, int... status ) {
    getMock( "TEMPLETON" )
        .expect()
        .method( "POST" )
        .pathInfo( "/hive" )
        .respond()
        .status( status[ 0 ] )
        .contentType( "application/json" )
        .content( "{\"id\":\"job_201210301335_0086\"}".getBytes() );
    String json = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .queryParam( "group", group )
        .queryParam( "file", file )
        .queryParam( "statusdir", statusDir )
        .expect()
        //.log().all()
        .statusCode( isIn( ArrayUtils.toObject( status ) ) )
        .contentType( "application/json" )
        //.content( "boolean", equalTo( true ) )
        .when()
        .post( getUrl( "TEMPLETON" ) + "/hive" )
        .asString();
    log.trace( "JSON=" + json );
    String job = from( json ).getString( "id" );
    log.debug( "JOB=" + job );
    assertComplete();
    return job;
  }

  public void queryQueue( String user, String password, String job ) throws IOException {
    getMock( "TEMPLETON" )
          .expect()
          .method( "GET" )
          .pathInfo( "/queue/" + job )
          .respond()
          .status( HttpStatus.SC_OK )
          .content( getResourceBytes( "templeton-job-status.json" ) )
          .contentType( "application/json" );
    String status = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .pathParam( "job", job )
        .expect()
        //.log().all()
        .content( "status.jobId", equalTo( job ) )
        .statusCode( HttpStatus.SC_OK )
        .when().get( getUrl( "TEMPLETON" ) + "/queue/{job}" ).asString();
    log.debug( "STATUS=" + status );
    assertComplete();
  }

}

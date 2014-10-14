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
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.security.ldap.SimpleLdapDirectoryServer;
import org.apache.hadoop.gateway.services.DefaultGatewayServices;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.test.mock.MockServer;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.path.json.JsonPath.from;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertThat;

/**
 * This class was created to reduce much of the duplication and boiler plate that was ending up in the GatewayBasicFuncTest class.
 * It basically does a number of different things.
 * 1) Creates a GATEWAY_HOME starts a gateway instance and deployes a test topology.
 * 2) Provides a registry of mock Hadoop services.
 * 3) Provides "bundled" methods for common Hadoop operations to avoid duplication in tests.
 * 4) Provides methods to access test resources.
 */
public class GatewayFuncTestDriver {

  private static Logger log = LoggerFactory.getLogger( GatewayFuncTestDriver.class );

  public Class<?> resourceBaseClass;
  public Map<String,Service> services = new HashMap<String,Service>();
  public SimpleLdapDirectoryServer ldap;
  public TcpTransport ldapTransport;
  public boolean useGateway;
  public GatewayServer gateway;
  public GatewayConfig config;

  /**
   * Sets the class from which relative test resource names should be resolved.
   * @param resourceBaseClass The class from which relative test resource names should be resolved.
   */
  public void setResourceBase( Class<?> resourceBaseClass ) {
    this.resourceBaseClass = resourceBaseClass;
  }

  /**
   * Starts an embedded LDAP server of the specified port.
   * @param port The desired port the LDAP server should listen on.
   * @return The actual port the LDAP server is listening on.
   * @throws Exception Thrown if a failure occurs.
   */
  public int setupLdap( int port ) throws Exception {
    URL usersUrl = getResourceUrl( "users.ldif" );
    ldapTransport = new TcpTransport( port );
    ldap = new SimpleLdapDirectoryServer( "dc=hadoop,dc=apache,dc=org", new File( usersUrl.toURI() ), ldapTransport );
    ldap.start();
    log.info( "LDAP port = " + port );
    return port;
  }

  /**
   * Adds a mock service to the registry.
   */
  public void setupService( String role, String realUrl, String gatewayPath, boolean mock ) throws Exception {
    Service service = new Service( role, realUrl, gatewayPath, mock );
    services.put( role, service );
    log.info( role + " port = " + service.server.getPort() );
  }

  /**
   * Creates a GATEWAY_HOME, starts a gateway instance and deploys a test topology.
   */
  public void setupGateway( GatewayTestConfig config, String cluster, XMLTag topology, boolean use ) throws IOException {
    this.useGateway = use;
    this.config = config;

    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();

    config.setGatewayHomeDir( gatewayDir.getAbsolutePath() );

    File topoDir = new File( config.getGatewayTopologyDir() );
    topoDir.mkdirs();

    File deployDir = new File( config.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    File descriptor = new File( topoDir, cluster + ".xml" );
    FileOutputStream stream = new FileOutputStream( descriptor );
    topology.toStream( stream );
    stream.close();

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<String,String>();
    options.put("persist-master", "false");
    options.put("master", "password");
    try {
      srvcs.init(config, options);
    } catch (ServiceLifecycleException e) {
      e.printStackTrace(); // I18N not required.
    }
    gateway = GatewayServer.startGateway( config, srvcs );
    MatcherAssert.assertThat( "Failed to start gateway.", gateway, notNullValue() );

    log.info( "Gateway port = " + gateway.getAddresses()[ 0 ].getPort() );
  }

  public void cleanup() throws Exception {
    gateway.stop();
    FileUtils.deleteQuietly( new File( config.getGatewayTopologyDir() ) );
    FileUtils.deleteQuietly( new File( config.getGatewayConfDir() ) );
    FileUtils.deleteQuietly( new File( config.getGatewaySecurityDir() ) );
    FileUtils.deleteQuietly( new File( config.getGatewayDeploymentDir() ) );
    FileUtils.deleteQuietly( new File( config.getGatewayDataDir() ) );

    for( Service service : services.values() ) {
      service.server.stop();
    }
    services.clear();

    ldap.stop( true );
  }

  public boolean isUseGateway() {
    return useGateway;
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
      url = service.realUrl.toASCIIString();
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
    return "ldap://localhost:" + ldapTransport.getPort();
  }

  private static class Service {
    String role;
    URI realUrl;
    String gatewayPath;
    boolean mock;
    MockServer server;
    private Service( String role, String realUrl, String gatewayPath, boolean mock ) throws Exception {
      this.role = role;
      this.realUrl = new URI( realUrl );
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

  public InputStream getResourceStream( String resource ) throws IOException {
    InputStream stream = null;
    if( resource.startsWith( "file:/" ) ) {
      try {
        stream = FileUtils.openInputStream( new File( new URI( resource ) ) );
      } catch( URISyntaxException e ) {
        throw new IOException( e  );
      }
    } else {
      stream = ClassLoader.getSystemResourceAsStream( getResourceName( resource ) );
    }
    assertThat( "Failed to find test resource " + resource, stream, Matchers.notNullValue() );
    return stream;
  }

  public byte[] getResourceBytes( String resource ) throws IOException {
    return IOUtils.toByteArray( getResourceStream( resource ) );
  }

  public String getResourceString( String resource, Charset charset ) throws IOException {
    return IOUtils.toString( getResourceBytes( resource ), "UTF-8" );
  }

  public void assertComplete() {
    // Check to make sure that all interaction were satisfied if for mocked services.
    // Otherwise just clear the mock interaction queue.
    for( Service service : services.values() ) {
      if( service.mock ) {
        assertThat(
            "Service " + service.role + " has remaining expected interactions.",
            service.server.getCount(), is( 0 ) );
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
      getMock( "WEBHDFS" )
          .expect()
          .method( "PUT" )
          .pathInfo( "/v1" + file )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .respond()
          .status( status )
          .header( "Location", getRealUrl("DATANODE") + file + "?op=CREATE&user.name="+user );
    } else {
      getMock( "WEBHDFS" )
          .expect()
          .method( "PUT" )
          .pathInfo( "/v1" + file )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .respond()
          .status( status );
    }
    Response response = given()
        //.log().headers()
        //.log().parameters()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .queryParam( "op", "CREATE" )
        .queryParam( "permission", permsOctal )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().put( getUrl( "WEBHDFS" ) + "/v1" + file + ( isUseGateway() ? "" : "?user.name=" + user ) );
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
          .header( "Location", "webhdfs://" + getRealAddr( "DATANODE" ) + "/v1" + path );
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
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
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
        chmodFile( user, password, file, permsOctal, chownStatus );
        if( group != null ) {
          chownFile( user, password, file, user, group, chownStatus );
        }
      }
    }
    assertComplete();
    return location;
  }

  public void readFile( String user, String password, String file, String contentType, String resource, int status ) throws IOException {
    getMock( "WEBHDFS" )
        .expect()
        .method( "GET" )
        .pathInfo( "/v1" + file )
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
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .queryParam( "op", "OPEN" )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().get( getUrl("WEBHDFS") + "/v1" + file + ( isUseGateway() ? "" : "?user.name=" + user ) );
    if( response.getStatusCode() == HttpStatus.SC_OK ) {
      String actualContent = response.asString();
      String expectedContent = getResourceString( resource, Charset.forName("UTF-8") );
      assertThat( actualContent, is( expectedContent ) );
    }
    assertComplete();
  }

  public void chownFile( String user, String password, String file, String owner, String group, int status ) {
    getMock( "WEBHDFS" )
        .expect()
        .method( "PUT" )
        .pathInfo( "/v1" + file )
        .queryParam( "op", "SETOWNER" )
        .queryParam( "user.name", user )
        .queryParam( "owner", owner )
        .queryParam( "group", group )
        .respond()
        .status( HttpStatus.SC_OK );
    given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .queryParam( "op", "SETOWNER" )
        .queryParam( "owner", owner )
        .queryParam( "group", group )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().put( getUrl("WEBHDFS") + "/v1" + file + ( isUseGateway() ? "" : "?user.name=" + user ) );
    assertComplete();
  }

  public void chmodFile( String user, String password, String file, String permsOctal, int status ) {
    getMock( "WEBHDFS" )
        .expect()
        .method( "PUT" )
        .pathInfo( "/v1" + file )
        .queryParam( "op", "SETPERMISSION" )
        .queryParam( "user.name", user )
        .queryParam( "permission", permsOctal )
        .respond()
        .status( HttpStatus.SC_OK );
    given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .queryParam( "op", "SETPERMISSION" )
        .queryParam( "permission", permsOctal )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().put( getUrl("WEBHDFS") + "/v1" + file + ( isUseGateway() ? "" : "?user.name=" + user ) );
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
      getMock( "WEBHDFS" )
          .expect()
          .method( "PUT" )
          .pathInfo( "/v1" + file )
          .queryParam( "op", "CREATE" )
          .queryParam( "user.name", user )
          .queryParam( "overwrite", "true" )
          .respond()
          .status( status )
          .header( "Location", getRealUrl("DATANODE") + file + "?op=CREATE&user.name="+user );
    } else {
      getMock( "WEBHDFS" )
          .expect()
          .method( "PUT" )
          .pathInfo( "v1" + file )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .respond()
          .status( status );
    }
    Response response = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .queryParam( "op", "CREATE" )
        .queryParam( "overwrite", "true" )
        .content( getResourceBytes( resource ) )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().put( getUrl("WEBHDFS") + "/v1" + file + ( isUseGateway() ? "" : "?user.name=" + user ) );
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
          .header( "Location", "webhdfs://" + getRealAddr( "DATANODE" ) + "/v1" + path );
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
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
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
    getMock( "WEBHDFS" )
        .expect()
        .method( "DELETE" )
        .pathInfo( "/v1" + file )
        .queryParam( "user.name", user )
        .queryParam( "op", "DELETE" )
        .queryParam( "recursive", recursive )
        .respond().status( status[0] );
    given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .queryParam( "op", "DELETE" )
        .queryParam( "recursive", recursive )
        .expect()
        //.log().all()
        .statusCode( isIn( ArrayUtils.toObject( status ) ) )
        .when()
        .delete( getUrl( "WEBHDFS" ) + "/v1" + file + ( isUseGateway() ? "" : "?user.name=" + user ) );
    assertComplete();
  }

  public String createDir( String user, String password, String dir, String permsOctal, int status ) {
    getMock( "WEBHDFS" )
        .expect()
        .method( "PUT" )
        .pathInfo( "/v1" + dir )
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
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .queryParam( "op", "MKDIRS" )
        .queryParam( "permission", permsOctal )
        .expect()
        //.log().all()
        .statusCode( status )
        .contentType( "application/json" )
        .content( "boolean", equalTo( true ) )
        .when()
        .put( getUrl("WEBHDFS") + "/v1" + dir + ( isUseGateway() ? "" : "?user.name=" + user ) );
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
        .get( getUrl( "WEBHDFS" ) + "/v1" + dir );
  }

  public String submitJava( String user, String password, String jar, String main, String input, String output, int status ) {
    getMock( "WEBHCAT" )
        .expect()
        .method( "POST" )
        .pathInfo( "/v1/mapreduce/jar" )
        .formParam( "user.name", user )
        .formParam( "jar", jar )
        .formParam( "class", main )
        .formParam( "arg", input, output )
        .respond()
        .status( status )
        .contentType( "application/json" )
        .content( "{\"id\":\"job_201210301335_0086\"}".getBytes() );
    String json = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .formParam( "user.name", user )
        .formParam( "jar", jar )    //"/user/hdfs/test/hadoop-examples.jar" )
        .formParam( "class", main ) //"org.apache.org.apache.hadoop.examples.WordCount" )
        .formParam( "arg", input, output ) //.formParam( "arg", "/user/hdfs/test/input", "/user/hdfs/test/output" )
        .expect()
        //.log().all()
        .statusCode( status )
        .when().post( getUrl( "WEBHCAT" ) + "/v1/mapreduce/jar" + ( isUseGateway() ? "" : "?user.name=" + user ) ).asString();
    log.trace( "JSON=" + json );
    String job = from( json ).getString( "id" );
    log.debug( "JOB=" + job );
    assertComplete();
    return job;
  }

  public String submitPig( String user, String password, String group, String file, String arg, String statusDir, int... status ) {
    getMock( "WEBHCAT" )
        .expect()
        .method( "POST" )
        .pathInfo( "/v1/pig" )
        .respond()
        .status( status[0] )
        .contentType( "application/json" )
        .content( "{\"id\":\"job_201210301335_0086\"}".getBytes() );
    String json = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
//BUG: The identity asserter needs to check for this too.
        .formParam( "user.name", user )
        .formParam( "group", group )
        .formParam( "file", file )
        .formParam( "arg", arg )
        .formParam( "statusdir", statusDir )
        .expect()
        //.log().all();
        .statusCode( isIn( ArrayUtils.toObject( status ) ) )
        .contentType( "application/json" )
        //.content( "boolean", equalTo( true ) )
        .when()
        .post( getUrl( "WEBHCAT" ) + "/v1/pig" + ( isUseGateway() ? "" : "?user.name=" + user ) )
        .asString();
    log.trace( "JSON=" + json );
    String job = from( json ).getString( "id" );
    log.debug( "JOB=" + job );
    assertComplete();
    return job;
  }

  public String submitHive( String user, String password, String group, String file, String statusDir, int... status ) {
    getMock( "WEBHCAT" )
        .expect()
        .method( "POST" )
        .pathInfo( "/v1/hive" )
        .respond()
        .status( status[ 0 ] )
        .contentType( "application/json" )
        .content( "{\"id\":\"job_201210301335_0086\"}".getBytes() );
    String json = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .formParam( "user.name", user )
        .formParam( "group", group )
        .formParam( "group", group )
        .formParam( "file", file )
        .formParam( "statusdir", statusDir )
        .expect()
        //.log().all()
        .statusCode( isIn( ArrayUtils.toObject( status ) ) )
        .contentType( "application/json" )
        //.content( "boolean", equalTo( true ) )
        .when()
        .post( getUrl( "WEBHCAT" ) + "/v1/hive" + ( isUseGateway() ? "" : "?user.name=" + user ) )
        .asString();
    log.trace( "JSON=" + json );
    String job = from( json ).getString( "id" );
    log.debug( "JOB=" + job );
    assertComplete();
    return job;
  }

  public void queryQueue( String user, String password, String job ) throws IOException {
    getMock( "WEBHCAT" )
          .expect()
          .method( "GET" )
          .pathInfo( "/v1/jobs/" + job )
          .respond()
          .status( HttpStatus.SC_OK )
          .content( getResourceBytes( "webhcat-job-status.json" ) )
          .contentType( "application/json" );
    String status = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .pathParam( "job", job )
        .expect()
        //.log().all()
        .content( "status.jobId", equalTo( job ) )
        .statusCode( HttpStatus.SC_OK )
        .when().get( getUrl( "WEBHCAT" ) + "/v1/jobs/{job}" + ( isUseGateway() ? "" : "?user.name=" + user ) ).asString();
    log.debug( "STATUS=" + status );
    assertComplete();
  }

  /* GET /oozie/versions
  HTTP/1.1 200 OK
  Content-Type: application/json;charset=UTF-8
  Content-Length: 5
  Server: Apache-Coyote/1.1
  Date: Thu, 14 Feb 2013 15:47:51 GMT
  See: oozie-versions.json
  */
  public void oozieGetVersions( String user, String password ) throws IOException {
    given()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .expect()
        .statusCode( 200 )
        .body( "", hasItems( 0, 1 ) )
        .when().get( getUrl( "OOZIE" ) + "/versions" + ( isUseGateway() ? "" : "?user.name=" + user ) ).asString();
  }

  /* GET /oozie/v1/admin/status
  HTTP/1.1 200 OK
  Content-Type: application/json;charset=UTF-8
  Content-Length: 23
  Server: Apache-Coyote/1.1
  Date: Thu, 14 Feb 2013 15:49:16 GMT
  See: oozie-admin-status.json
  */

  /* PUT /oozie/v1/admin/status?safemode=true
TODO
  */

  /* GET /oozie/v1/admin/os-env
  HTTP/1.1 200 OK
  Content-Type: application/json;charset=UTF-8
  Content-Length: 2039
  Server: Apache-Coyote/1.1
  Date: Thu, 14 Feb 2013 15:51:56 GMT
  See: oozie-admin-os-env.json
  */

  /* GET /oozie/v1/admin/java-sys-properties
  HTTP/1.1 200 OK
  Content-Type: application/json;charset=UTF-8
  Content-Length: 3673
  Server: Apache-Coyote/1.1
  Date: Thu, 14 Feb 2013 15:53:00 GMT
  See: oozie-admin-java-sys-properties.json
  */

  /* GET /oozie/v1/admin/configuration
  HTTP/1.1 200 OK
  Transfer-Encoding: Identity
  Content-Type: application/json;charset=UTF-8
  Server: Apache-Coyote/1.1
  Date: Thu, 14 Feb 2013 15:53:31 GMT
  See: oozie-admin-configuration.json
  */

  /* GET /oozie/v1/admin/instrumentation
  HTTP/1.1 200 OK
  Transfer-Encoding: Identity
  Content-Type: application/json;charset=UTF-8
  Server: Apache-Coyote/1.1
  Date: Thu, 14 Feb 2013 15:55:43 GMT
  See: oozie-admin-instrumentation.json
  */

  /* GET /oozie/v1/admin/build-version
  HTTP/1.1 200 OK
  Content-Type: application/json;charset=UTF-8
  Content-Length: 27
  Server: Apache-Coyote/1.1
  Date: Thu, 14 Feb 2013 16:08:31 GMT
  See: oozie-admin-build-version.json
  */

  /* POST /oozie/v1/jobs (request XML; contains URL, response JSON)
  Content-Type: application/json;charset=UTF-8
  Content-Length: 45
  Server: Apache-Coyote/1.1
  Date: Thu, 14 Feb 2013 18:10:52 GMT
  */
  public String oozieSubmitJob( String user, String password, String request, int status ) throws IOException, URISyntaxException {
    getMock( "OOZIE" )
        .expect()
        .method( "POST" )
        .pathInfo( "/v1/jobs" )
        .respond()
        .status( HttpStatus.SC_CREATED )
        .content( getResourceBytes( "oozie-jobs-submit-response.json" ) )
        .contentType( "application/json" );
    //System.out.println( "REQUEST LENGTH = " + request.length() );

    URL url = new URL( getUrl( "OOZIE" ) + "/v1/jobs?action=start" + ( isUseGateway() ? "" : "&user.name=" + user ) );
    HttpHost targetHost = new HttpHost( url.getHost(), url.getPort(), url.getProtocol() );
    DefaultHttpClient client = new DefaultHttpClient();
    client.getCredentialsProvider().setCredentials(
        new AuthScope( targetHost ),
        new UsernamePasswordCredentials( user, password ) );

    // Create AuthCache instance
    AuthCache authCache = new BasicAuthCache();
    // Generate BASIC scheme object and add it to the local auth cache
    BasicScheme basicAuth = new BasicScheme();
    authCache.put( targetHost, basicAuth );
    // Add AuthCache to the execution context
    BasicHttpContext localContext = new BasicHttpContext();
    localContext.setAttribute( ClientContext.AUTH_CACHE, authCache );

    HttpPost post = new HttpPost( url.toURI() );
//    post.getParams().setParameter( "action", "start" );
    StringEntity entity = new StringEntity( request, ContentType.create( "application/xml", "UTF-8" ) );
    post.setEntity( entity );
    post.setHeader( "X-XSRF-Header", "ksdjfhdsjkfhds" );
    HttpResponse response = client.execute( targetHost, post, localContext );
    assertThat( response.getStatusLine().getStatusCode(), is( status ) );
    String json = EntityUtils.toString( response.getEntity() );

//    String json = given()
//        .log().all()
//        .auth().preemptive().basic( user, password )
//        .queryParam( "action", "start" )
//        .contentType( "application/xml;charset=UTF-8" )
//        .content( request )
//        .expect()
//        .log().all()
//        .statusCode( status )
//        .when().post( getUrl( "OOZIE" ) + "/v1/jobs" + ( isUseGateway() ? "" : "?user.name=" + user ) ).asString();
    //System.out.println( "JSON=" + json );
    String id = from( json ).getString( "id" );
    return id;
  }

  /* GET /oozie/v1/jobs?filter=user%3Dbansalm&offset=1&len=50 (body JSON; contains URL)
  HTTP/1.1 200 OK
  Content-Type: application/json;charset=UTF-8
  Content-Length: 46
  Server: Apache-Coyote/1.1
  Date: Thu, 14 Feb 2013 16:10:25 GMT
  */

  /* GET /oozie/v1/job/0000000-130214094519989-oozie-oozi-W
  HTTP/1.1 200 OK
  Content-Type: application/json;charset=UTF-8
  Content-Length: 2611
  Server: Apache-Coyote/1.1
  Date: Thu, 14 Feb 2013 17:39:36 GMT
  */

  /* http://192.168.56.101:11000/oozie/v1/job/0000000-130214094519989-oozie-oozi-W?action=start&user.name=sandbox
  HTTP/1.1 200 OK
  Date: Thu, 14 Feb 2013 17:52:13 GMT
  Content-Length: 0
  Server: Apache-Coyote/1.1
  Set-Cookie: hadoop.auth="u=sandbox&p=sandbox&t=simple&e=1360900333149&s=AU/GeHDNBuK9RBRaBJfrqatjfz8="; Version=1; Path=/
  */

  /* PUT /oozie/v1/job/job-3?action=rerun (request body XML, contains URL)
  HTTP/1.1 200 OK
  Date: Thu, 14 Feb 2013 18:07:45 GMT
  Content-Length: 0
  Server: Apache-Coyote/1.1
  Set-Cookie: hadoop.auth="u=sandbox&p=sandbox&t=simple&e=1360901264892&s=DCOczPqn9mcisCeOb5x2C7LIRc8="; Version=1; Path=/
  */

  /* GET /oozie/v1/job/0000000-130214094519989-oozie-oozi-W?show=info (body JSON, contains URL)
  HTTP/1.1 200 OK
  Content-Type: application/json;charset=UTF-8
  Content-Length: 2611
  Server: Apache-Coyote/1.1
  Date: Thu, 14 Feb 2013 17:45:23 GMT
  */
  public String oozieQueryJobStatus( String user, String password, String id, int status ) throws Exception {
    getMock( "OOZIE" )
        .expect()
        .method( "GET" )
        .pathInfo( "/v1/job/" + id )
        .respond()
        .status( HttpStatus.SC_OK )
        .content( getResourceBytes( "oozie-job-show-info.json" ) )
        .contentType( "application/json" );

    //NOTE:  For some reason REST-assured doesn't like this and ends up failing with Content-Length issues.
    URL url = new URL( getUrl( "OOZIE" ) + "/v1/job/" + id + ( isUseGateway() ? "" : "?user.name=" + user ) );
    HttpHost targetHost = new HttpHost( url.getHost(), url.getPort(), url.getProtocol() );
    DefaultHttpClient client = new DefaultHttpClient();
    client.getCredentialsProvider().setCredentials(
        new AuthScope( targetHost ),
        new UsernamePasswordCredentials( user, password ) );

    // Create AuthCache instance
    AuthCache authCache = new BasicAuthCache();
    // Generate BASIC scheme object and add it to the local auth cache
    BasicScheme basicAuth = new BasicScheme();
    authCache.put( targetHost, basicAuth );
    // Add AuthCache to the execution context
    BasicHttpContext localContext = new BasicHttpContext();
    localContext.setAttribute( ClientContext.AUTH_CACHE, authCache );

    HttpGet request = new HttpGet( url.toURI() );
    request.setHeader("X-XSRF-Header", "ksdhfjkhdsjkf");
    HttpResponse response = client.execute( targetHost, request, localContext );
    assertThat( response.getStatusLine().getStatusCode(), is( status ) );
    String json = EntityUtils.toString( response.getEntity() );
    String jobStatus = from( json ).getString( "status" );
    return jobStatus;
  }

  /* GET /oozie/v1/job/0000000-130214094519989-oozie-oozi-W?show=definition
  HTTP/1.1 200 OK
  Content-Type: application/xml;charset=UTF-8
  Content-Length: 1494
  Server: Apache-Coyote/1.1
  Date: Thu, 14 Feb 2013 17:43:30 GMT
  */

  /* GET GET /oozie/v1/job/0000000-130214094519989-oozie-oozi-W?show=log
  HTTP/1.1 200 OK
  Transfer-Encoding: Identity
  Content-Type: text/plain;charset=UTF-8
  Server: Apache-Coyote/1.1
  Date: Thu, 14 Feb 2013 17:41:43 GMT
  */

}

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

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.http.Header;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.ResponseSpecification;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.knox.gateway.util.KnoxCLI;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.category.MediumTests;
import org.apache.knox.test.category.VerifyTest;
import org.apache.knox.test.mock.MockRequestMatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import static io.restassured.RestAssured.given;
import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.either;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.collection.IsIn.in;
import static org.hamcrest.collection.IsIn.oneOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.xmlmatchers.XmlMatchers.isEquivalentTo;
import static org.xmlmatchers.transform.XmlConverters.the;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

@Category( { VerifyTest.class, MediumTests.class } )
public class GatewayBasicFuncTest {
  private static final Logger log = LogManager.getLogger( GatewayBasicFuncTest.class );

  private static GatewayTestDriver driver = new GatewayTestDriver();

  // Controls the host name to which the gateway dispatch requests.  This may be the name of a sandbox VM
  // or an EC2 instance.  Currently only a single host is supported.
  private static final String TEST_HOST = "vm.local";

  // Specifies if the test requests should go through the gateway or directly to the services.
  // This is frequently used to verify the behavior of the test both with and without the gateway.
  private static final boolean USE_GATEWAY = true;

  // Specifies if the test requests should be sent to mock services or the real services.
  // This is frequently used to verify the behavior of the test both with and without mock services.
  private static final boolean USE_MOCK_SERVICES = true;

  // Specifies if the GATEWAY_HOME created for the test should be deleted when the test suite is complete.
  // This is frequently used during debugging to keep the GATEWAY_HOME around for inspection.
  private static final boolean CLEANUP_TEST = true;

//  private static final boolean USE_GATEWAY = false;
//  private static final boolean USE_MOCK_SERVICES = false;
//  private static final boolean CLEANUP_TEST = false;

  /**
   * Creates a deployment of a gateway instance that all test methods will share.  This method also creates a
   * registry of sorts for all of the services that will be used by the test methods.
   * The createTopology method is used to create the topology file that would normally be read from disk.
   * The driver.setupGateway invocation is where the creation of GATEWAY_HOME occurs.
   * @throws Exception Thrown if any failure occurs.
   */
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    LOG_ENTER();
    GatewayTestConfig config = new GatewayTestConfig();
    driver.setResourceBase(GatewayBasicFuncTest.class);
    driver.setupLdap(0);
    driver.setupService("WEBHDFS", "http://" + TEST_HOST + ":50070/webhdfs", "/cluster/webhdfs", USE_MOCK_SERVICES);
    driver.setupService( "DATANODE", "http://" + TEST_HOST + ":50075/webhdfs", "/cluster/webhdfs/data", USE_MOCK_SERVICES );
    driver.setupService( "WEBHCAT", "http://" + TEST_HOST + ":50111/templeton", "/cluster/templeton", USE_MOCK_SERVICES );
    driver.setupService( "OOZIE", "http://" + TEST_HOST + ":11000/oozie", "/cluster/oozie", USE_MOCK_SERVICES );
    driver.setupService( "HIVE", "http://" + TEST_HOST + ":10000", "/cluster/hive", USE_MOCK_SERVICES );
    driver.setupService( "WEBHBASE", "http://" + TEST_HOST + ":60080", "/cluster/hbase", USE_MOCK_SERVICES );
    driver.setupService( "NAMENODE", "hdfs://" + TEST_HOST + ":8020", null, USE_MOCK_SERVICES );
    driver.setupService( "JOBTRACKER", "thrift://" + TEST_HOST + ":8021", null, USE_MOCK_SERVICES );
    driver.setupService( "RESOURCEMANAGER", "http://" + TEST_HOST + ":8088/ws", "/cluster/resourcemanager", USE_MOCK_SERVICES );
    driver.setupService( "FALCON", "http://" + TEST_HOST + ":15000", "/cluster/falcon", USE_MOCK_SERVICES );
    driver.setupService( "STORM", "http://" + TEST_HOST + ":8477", "/cluster/storm", USE_MOCK_SERVICES );
    driver.setupService( "STORM-LOGVIEWER", "http://" + TEST_HOST + ":8477", "/cluster/storm", USE_MOCK_SERVICES );
    driver.setupService( "SOLR", "http://" + TEST_HOST + ":8983", "/cluster/solr", USE_MOCK_SERVICES );
    driver.setupService( "KAFKA", "http://" + TEST_HOST + ":8477", "/cluster/kafka", USE_MOCK_SERVICES );
    driver.setupGateway( config, "cluster", createTopology(), USE_GATEWAY );
    LOG_EXIT();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    LOG_ENTER();
    if( CLEANUP_TEST ) {
      driver.cleanup();
    }
    LOG_EXIT();
  }

  @After
  public void tearDown() {
    driver.reset();
  }

  /**
   * Creates a topology that is deployed to the gateway instance for the test suite.
   * Note that this topology is shared by all of the test methods in this suite.
   * @return A populated XML structure for a topology file.
   */
  private static XMLTag createTopology() {
    return XMLDoc.newDocument( true )
        .addRoot( "topology" )
          .addTag( "gateway" )
            .addTag( "provider" )
              .addTag( "role" ).addText( "webappsec" )
              .addTag("name").addText("WebAppSec")
              .addTag("enabled").addText("true")
              .addTag( "param" )
                .addTag("name").addText("csrf.enabled")
                .addTag("value").addText("true").gotoParent().gotoParent()
            .addTag("provider")
              .addTag("role").addText("authentication")
              .addTag("name").addText("ShiroProvider")
              .addTag("enabled").addText("true")
              .addTag( "param" )
                .addTag("name").addText("main.ldapRealm")
                .addTag("value").addText("org.apache.knox.gateway.shirorealm.KnoxLdapRealm").gotoParent()
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
            .addTag("provider")
              .addTag("role").addText("identity-assertion")
              .addTag("enabled").addText("true")
              .addTag("name").addText("Default").gotoParent()
            .addTag("provider")
              .addTag( "role" ).addText( "authorization" )
              .addTag( "enabled" ).addText( "true" )
              .addTag("name").addText("AclsAuthz").gotoParent()
              .addTag("param")
                .addTag("name").addText( "webhdfs-acl" )
                .addTag("value").addText( "hdfs;*;*" ).gotoParent()
          .gotoRoot()
          .addTag("service")
            .addTag("role").addText("WEBHDFS")
            .addTag("url").addText(driver.getRealUrl("WEBHDFS")).gotoParent()
          .addTag( "service" )
            .addTag( "role" ).addText( "NAMENODE" )
            .addTag( "url" ).addText( driver.getRealUrl( "NAMENODE" ) ).gotoParent()
          .addTag( "service" )
            .addTag( "role" ).addText( "DATANODE" )
            .addTag( "url" ).addText( driver.getRealUrl( "DATANODE" ) ).gotoParent()
          .addTag( "service" )
            .addTag( "role" ).addText( "JOBTRACKER" )
            .addTag( "url" ).addText( driver.getRealUrl( "JOBTRACKER" ) ).gotoParent()
          .addTag( "service" )
            .addTag( "role" ).addText( "WEBHCAT" )
            .addTag( "url" ).addText( driver.getRealUrl( "WEBHCAT" ) ).gotoParent()
          .addTag( "service" )
            .addTag( "role" ).addText( "OOZIE" )
            .addTag( "url" ).addText( driver.getRealUrl( "OOZIE" ) ).gotoParent()
          .addTag( "service" )
            .addTag( "role" ).addText( "HIVE" )
            .addTag( "url" ).addText( driver.getRealUrl( "HIVE" ) ).gotoParent()
          .addTag( "service" )
            .addTag( "role" ).addText( "WEBHBASE" )
            .addTag( "url" ).addText( driver.getRealUrl( "WEBHBASE" ) ).gotoParent()
        .addTag("service")
            .addTag("role").addText("RESOURCEMANAGER")
            .addTag("url").addText(driver.getRealUrl("RESOURCEMANAGER")).gotoParent()
        .addTag("service")
            .addTag("role").addText("FALCON")
            .addTag("url").addText(driver.getRealUrl("FALCON")).gotoParent()
        .addTag("service")
            .addTag("role").addText("STORM")
            .addTag("url").addText(driver.getRealUrl("STORM")).gotoParent()
        .addTag("service")
            .addTag("role").addText("STORM-LOGVIEWER")
            .addTag("url").addText(driver.getRealUrl("STORM-LOGVIEWER")).gotoParent()
        .addTag("service")
            .addTag("role").addText("SOLR")
            .addTag("url").addText(driver.getRealUrl("SOLR")).gotoParent()
        .addTag("service")
            .addTag("role").addText("KAFKA")
            .addTag("url").addText(driver.getRealUrl("KAFKA")).gotoParent()
        .addTag("service")
        .addTag("role").addText("SERVICE-TEST")
        .gotoRoot();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testBasicJsonUseCase() throws IOException {
    LOG_ENTER();
    String root = "/tmp/GatewayBasicFuncTest/testBasicJsonUseCase";
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
    driver.getMock( "WEBHDFS" )
        .expect()
        .method( "PUT" )
        .pathInfo( "/v1" + root + "/dir" )
        .queryParam( "op", "MKDIRS" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "webhdfs-success.json" ) )
        .contentType( "application/json" );
    Cookie cookie = given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "MKDIRS" )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( "application/json" )
        .body( "boolean", is( true ) )
        .when().put( driver.getUrl( "WEBHDFS" ) + "/v1" + root + "/dir" )
                        .getDetailedCookie( GatewayServer.KNOXSESSIONCOOKIENAME);
    assertThat( cookie.isSecured(), is( true ) );
    assertThat( cookie.isHttpOnly(), is( true ) );
    assertThat( cookie.getPath(), is( "/gateway/cluster" ) );
    assertThat( cookie.getValue().length(), greaterThan( 16 ) );
    driver.assertComplete();
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testBasicOutboundHeaderUseCase() throws IOException {
    LOG_ENTER();
    String root = "/tmp/GatewayBasicFuncTest/testBasicOutboundHeaderUseCase";
    String username = "hdfs";
    String password = "hdfs-password";
    InetSocketAddress gatewayAddress = driver.gateway.getAddresses()[0];
    String gatewayHostName = gatewayAddress.getHostName();
    String gatewayAddrName = InetAddress.getByName(gatewayHostName).getHostAddress();

    driver.getMock( "WEBHDFS" )
        .expect()
        .method( "PUT" )
        .pathInfo( "/v1" + root + "/dir/file" )
        .header( "Host", driver.getRealAddr( "WEBHDFS" ) )
        .queryParam( "op", "CREATE" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_TEMPORARY_REDIRECT )
        .header("Location", driver.getRealUrl("DATANODE") + "/v1" + root + "/dir/file?op=CREATE&user.name=hdfs");
    Response response = given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "CREATE" )
        .then()
        //.log().ifError()
        .statusCode( HttpStatus.SC_TEMPORARY_REDIRECT )
        .when().put( driver.getUrl("WEBHDFS") + "/v1" + root + "/dir/file" );
    String location = response.getHeader( "Location" );
    log.debug( "Redirect location: " + response.getHeader( "Location" ) );
    if( driver.isUseGateway() ) {
      assertThat( location, anyOf(
          startsWith( "http://" + gatewayHostName + ":" + gatewayAddress.getPort() + "/" ),
          startsWith( "http://" + gatewayAddrName + ":" + gatewayAddress.getPort() + "/" ) ) );
      assertThat( location, containsString( "?_=" ) );
    }
    assertThat(location, not(containsString("host=")));
    assertThat(location, not(containsString("port=")));
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testBasicOutboundEncodedHeaderUseCase() {
    LOG_ENTER();
    String root = "/tmp/GatewayBasicFuncTest/testBasicOutboundHeaderUseCase";
    String username = "hdfs";
    String password = "hdfs-password";

    driver.getMock( "WEBHDFS" )
        .expect()
        .method( "PUT" )
        .pathInfo( "/v1" + root + "/dir/fileレポー" )
        .header( "Host", driver.getRealAddr( "WEBHDFS" ) )
        .queryParam( "op", "CREATE" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_TEMPORARY_REDIRECT )
        .header("Location", driver.getRealUrl("DATANODE") + "/v1" + root + "/dir/file%E3%83%AC%E3%83%9D%E3%83%BC?op=CREATE&user.name=hdfs");
    Response response = given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "CREATE" )
        .then()
        //.log().ifError()
        .statusCode( HttpStatus.SC_TEMPORARY_REDIRECT )
        .when().put( driver.getUrl("WEBHDFS") + "/v1" + root + "/dir/fileレポー" );
//        .when().put( driver.getUrl("WEBHDFS") + "/v1" + root + "/dir/file%E3%83%AC%E3%83%9D%E3%83%BC" );
    String location = response.getHeader( "Location" );
    log.debug( "Redirect location: " + response.getHeader( "Location" ) );
    if( driver.isUseGateway() ) {
      assertThat( location, containsString("/dir/file%E3%83%AC%E3%83%9D%E3%83%BC") );
    }
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testHdfsTildeUseCase() throws IOException {
    LOG_ENTER();
    String root = "/tmp/GatewayBasicFuncTest/testHdfsTildeUseCase";
    String username = "hdfs";
    String password = "hdfs-password";

    // Attempt to delete the test directory in case a previous run failed.
    // Ignore any result.
    // Cleanup anything that might have been leftover because the test failed previously.
    driver.getMock( "WEBHDFS" )
        .expect()
        .method( "DELETE" )
        .from( "testHdfsTildeUseCase" )
        .pathInfo( "/v1/user/hdfs" + root )
        .queryParam( "op", "DELETE" )
        .queryParam( "user.name", username )
        .queryParam( "recursive", "true" )
        .respond()
        .status( HttpStatus.SC_OK );

    try {
      // Need to turn off URL encoding here or otherwise the tilde gets encoded and the rewrite rules fail
      RestAssured.urlEncodingEnabled = false;
      given()
          //.log().all()
          .auth().preemptive().basic( username, password )
          .header("X-XSRF-Header", "jksdhfkhdsf")
          .queryParam( "op", "DELETE" )
          .queryParam( "recursive", "true" )
          .then()
          //.log().all()
          .statusCode( HttpStatus.SC_OK )
          .when().delete( driver.getUrl( "WEBHDFS" ) + "/v1/~" + root + ( driver.isUseGateway() ? "" : "?user.name=" + username ) );
      driver.assertComplete();

      driver.getMock( "WEBHDFS" )
          .expect()
          .method( "PUT" )
          .pathInfo( "/v1/user/hdfs/dir" )
          .queryParam( "op", "MKDIRS" )
          .queryParam( "user.name", username )
          .respond()
          .status( HttpStatus.SC_OK )
          .content( driver.getResourceBytes( "webhdfs-success.json" ) )
          .contentType("application/json");
      given()
          //.log().all()
          .auth().preemptive().basic( username, password )
          .header("X-XSRF-Header", "jksdhfkhdsf")
          .queryParam( "op", "MKDIRS" )
          .then()
          //.log().all();
          .statusCode( HttpStatus.SC_OK )
          .contentType( "application/json" )
          .body( "boolean", is( true ) )
          .when().put( driver.getUrl( "WEBHDFS" ) + "/v1/~/dir" );
      driver.assertComplete();
    } finally {
      RestAssured.urlEncodingEnabled = true;
    }
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testBasicHdfsUseCase() throws IOException {
    LOG_ENTER();
    String root = "/tmp/GatewayBasicFuncTest/testBasicHdfsUseCase";
    String username = "hdfs";
    String password = "hdfs-password";
    InetSocketAddress gatewayAddress = driver.gateway.getAddresses()[0];
    String gatewayHostName = gatewayAddress.getHostName();
    String gatewayAddrName = InetAddress.getByName( gatewayHostName ).getHostAddress();

    // Attempt to delete the test directory in case a previous run failed.
    // Ignore any result.
    // Cleanup anything that might have been leftover because the test failed previously.
    driver.getMock( "WEBHDFS" )
        .expect()
        .method( "DELETE" )
        .from( "testBasicHdfsUseCase-1" )
        .pathInfo( "/v1" + root )
        .queryParam( "op", "DELETE" )
        .queryParam( "user.name", username )
        .queryParam( "recursive", "true" )
        .respond()
        .status( HttpStatus.SC_OK );
    given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "DELETE" )
        .queryParam( "recursive", "true" )
        .then()
        //.log().all()
        .statusCode( HttpStatus.SC_OK )
        .when().delete( driver.getUrl( "WEBHDFS" ) + "/v1" + root + ( driver.isUseGateway() ? "" : "?user.name=" + username ) );
    driver.assertComplete();

    /* Create a directory.
    curl -i -X PUT "http://<HOST>:<PORT>/<PATH>?op=MKDIRS[&permission=<OCTAL>]"

    The client receives a respond with a boolean JSON object:
    HTTP/1.1 HttpStatus.SC_OK OK
    Content-Type: application/json
    Transfer-Encoding: chunked

    {"boolean": true}
    */
    driver.getMock( "WEBHDFS" )
        .expect()
        .method( "PUT" )
        .pathInfo( "/v1" + root + "/dir" )
        .queryParam( "op", "MKDIRS" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "webhdfs-success.json" ) )
        .contentType( "application/json" );
    given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "MKDIRS" )
        .then()
        //.log().all();
        .statusCode( HttpStatus.SC_OK )
        .contentType( "application/json" )
        .body( "boolean", is( true ) )
        .when().put( driver.getUrl( "WEBHDFS" ) + "/v1" + root + "/dir" );
    driver.assertComplete();

    driver.getMock( "WEBHDFS" )
        .expect()
        .method( "GET" )
        .pathInfo( "/v1" + root )
        .queryParam( "op", "LISTSTATUS" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "webhdfs-liststatus-test.json" ) )
        .contentType( "application/json" );
    given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "LISTSTATUS" )
        .then()
        //.log().ifError()
        .statusCode( HttpStatus.SC_OK )
        .body( "FileStatuses.FileStatus[0].pathSuffix", is( "dir" ) )
        .when().get( driver.getUrl( "WEBHDFS" ) + "/v1" + root );
    driver.assertComplete();

    //NEGATIVE: Test a bad password.
    given()
        //.log().all()
        .auth().preemptive().basic( username, "invalid-password" )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "LISTSTATUS" )
        .then()
        //.log().ifError()
        .statusCode( HttpStatus.SC_UNAUTHORIZED )
        .when().get( driver.getUrl( "WEBHDFS" ) + "/v1" + root );
    driver.assertComplete();

    //NEGATIVE: Test a bad user.
    given()
        //.log().all()
        .auth().preemptive().basic( "hdfs-user", "hdfs-password" )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "LISTSTATUS" )
        .then()
        //.log().ifError()
        .statusCode( HttpStatus.SC_UNAUTHORIZED )
        .when().get( driver.getUrl( "WEBHDFS" ) + "/v1" + root );
    driver.assertComplete();

    //NEGATIVE: Test a valid but unauthorized user.
    given()
      //.log().all()
      .auth().preemptive().basic( "mapred-user", "mapred-password" )
      .header("X-XSRF-Header", "jksdhfkhdsf")
      .queryParam( "op", "LISTSTATUS" )
      .then()
      //.log().ifError()
      .statusCode( HttpStatus.SC_UNAUTHORIZED )
      .when().get( driver.getUrl( "WEBHDFS" ) + "/v1" + root );

    /* Add a file.
    curl -i -X PUT "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=CREATE
                       [&overwrite=<true|false>][&blocksize=<LONG>][&replication=<SHORT>]
                     [&permission=<OCTAL>][&buffersize=<INT>]"

    The then is redirected to a datanode where the file data is to be written:
    HTTP/1.1 307 TEMPORARY_REDIRECT
    Location: http://<DATANODE>:<PORT>/webhdfs/v1/<PATH>?op=CREATE...
    Content-Length: 0

    Step 2: Submit another HTTP PUT then using the URL in the Location header with the file data to be written.
    curl -i -X PUT -T <LOCAL_FILE> "http://<DATANODE>:<PORT>/webhdfs/v1/<PATH>?op=CREATE..."

    The client receives a HttpStatus.SC_CREATED Created respond with zero content length and the WebHDFS URI of the file in the Location header:
    HTTP/1.1 HttpStatus.SC_CREATED Created
    Location: webhdfs://<HOST>:<PORT>/<PATH>
    Content-Length: 0
    */
    driver.getMock( "WEBHDFS" )
        .expect()
        .method( "PUT" )
        .pathInfo( "/v1" + root + "/dir/file" )
        .queryParam( "op", "CREATE" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_TEMPORARY_REDIRECT )
        .header( "Location", driver.getRealUrl( "DATANODE" ) + "/v1" + root + "/dir/file?op=CREATE&user.name=hdfs" );
    driver.getMock( "DATANODE" )
        .expect()
        .method( "PUT" )
        .pathInfo( "/v1" + root + "/dir/file" )
        .queryParam( "op", "CREATE" )
        .queryParam( "user.name", username )
        .contentType( "text/plain" )
        .content( driver.getResourceBytes( "test.txt" ) )
            //.content( driver.gerResourceBytes( "hadoop-examples.jar" ) )
        .respond()
        .status( HttpStatus.SC_CREATED )
        .header( "Location", "webhdfs://" + driver.getRealAddr( "DATANODE" ) + "/v1" + root + "/dir/file" );
    Response response = given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "CREATE" )
        .then()
        //.log().ifError()
        .statusCode( HttpStatus.SC_TEMPORARY_REDIRECT )
        .when().put( driver.getUrl("WEBHDFS") + "/v1" + root + "/dir/file" );
    String location = response.getHeader( "Location" );
    log.debug( "Redirect location: " + response.getHeader( "Location" ) );
    if( driver.isUseGateway() ) {
      assertThat( location, anyOf(
          startsWith( "http://" + gatewayHostName + ":" + gatewayAddress.getPort() + "/" ),
          startsWith( "http://" + gatewayAddrName + ":" + gatewayAddress.getPort() + "/" ) ) );
      assertThat( location, containsString( "?_=" ) );
    }
    assertThat( location, not( containsString( "host=" ) ) );
    assertThat( location, not( containsString( "port=" ) ) );
    response = given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "test.txt" ) )
        .contentType( "text/plain" )
        .then()
        //.log().ifError()
        .statusCode( HttpStatus.SC_CREATED )
        .when().put( location );
    location = response.getHeader( "Location" );
    log.debug( "Created location: " + location );
    if( driver.isUseGateway() ) {
      assertThat( location, anyOf(
          startsWith( "http://" + gatewayHostName + ":" + gatewayAddress.getPort() + "/" ),
          startsWith( "http://" + gatewayAddrName + ":" + gatewayAddress.getPort() + "/" ) ) );
    }
    driver.assertComplete();

    /* Get the file.
    curl -i -L "http://<HOST>:<PORT>/webhdfs/v1/<PATH>?op=OPEN
                       [&offset=<LONG>][&length=<LONG>][&buffersize=<INT>]"

    The then is redirected to a datanode where the file data can be read:
    HTTP/1.1 307 TEMPORARY_REDIRECT
    Location: http://<DATANODE>:<PORT>/webhdfs/v1/<PATH>?op=OPEN...
    Content-Length: 0

    The client follows the redirect to the datanode and receives the file data:
    HTTP/1.1 HttpStatus.SC_OK OK
    Content-Type: application/octet-stream
    Content-Length: 22

    Hello, webhdfs user!
    */
    driver.getMock( "WEBHDFS" )
        .expect()
        .method( "GET" )
        .pathInfo( "/v1" + root + "/dir/file" )
        .queryParam( "op", "OPEN" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_TEMPORARY_REDIRECT )
        .header( "Location", driver.getRealUrl( "DATANODE" ) + "/v1" + root + "/dir/file?op=OPEN&user.name=hdfs" );
    driver.getMock( "DATANODE" )
        .expect()
        .method( "GET" )
        .pathInfo( "/v1" + root + "/dir/file" )
        .queryParam( "op", "OPEN" )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_OK )
        .contentType( "text/plain" )
        .content( driver.getResourceBytes( "test.txt" ) );
    given()
        //.log().all()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "OPEN" )
        .then()
        //.log().ifError()
        .statusCode( HttpStatus.SC_OK )
        .body( is( "TEST" ) )
        .when().get( driver.getUrl("WEBHDFS") + "/v1" + root + "/dir/file" );
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
    driver.getMock( "WEBHDFS" )
        .expect()
        .from( "testBasicHdfsUseCase-1" )
        .method( "DELETE" )
        .pathInfo( "/v1" + root )
        .queryParam( "op", "DELETE" )
        .queryParam( "user.name", username )
        .queryParam( "recursive", "true" )
        .respond()
        .status( HttpStatus.SC_OK );
    given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "DELETE" )
        .queryParam( "recursive", "true" )
        .then()
        //.log().ifError()
        .statusCode( HttpStatus.SC_OK )
        .when().delete( driver.getUrl( "WEBHDFS" ) + "/v1" + root );
    driver.assertComplete();
    LOG_EXIT();
  }

  // User hdfs in groups hadoop, hdfs
  // User mapred in groups hadoop, mapred
  // User hcat in group hcat
  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testPmHdfsM1UseCase() throws IOException {
    LOG_ENTER();
    String root = "/tmp/GatewayBasicFuncTest/testPmHdfdM1UseCase";
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

    deleteFile( userA, passA, root, "true", 200 );

    createDir( userA, passA, groupA, root + "/dirA700", "700", 200, 200 );
    createDir( userA, passA, groupA, root + "/dirA770", "770", 200, 200 );
    createDir( userA, passA, groupA, root + "/dirA707", "707", 200, 200 );
    createDir( userA, passA, groupA, root + "/dirA777", "777", 200, 200 );
    createDir( userA, passA, groupAB, root + "/dirAB700", "700", 200, 200 );
    createDir( userA, passA, groupAB, root + "/dirAB770", "770", 200, 200 );
    createDir( userA, passA, groupAB, root + "/dirAB707", "707", 200, 200 );
    createDir( userA, passA, groupAB, root + "/dirAB777", "777", 200, 200 );

    // CREATE: Files
    // userA:groupA
    createFile( userA, passA, groupA, root + "/dirA700/fileA700", "700", "text/plain", "small1.txt", 307, 201, 200 );
    createFile( userA, passA, groupA, root + "/dirA770/fileA770", "770", "text/plain", "small1.txt", 307, 201, 200 );
    createFile( userA, passA, groupA, root + "/dirA707/fileA707", "707", "text/plain", "small1.txt", 307, 201, 200 );
    createFile( userA, passA, groupA, root + "/dirA777/fileA777", "777", "text/plain", "small1.txt", 307, 201, 200 );
    // userA:groupAB
    createFile( userA, passA, groupAB, root + "/dirAB700/fileAB700", "700", "text/plain", "small1.txt", 307, 201, 200 );
    createFile( userA, passA, groupAB, root + "/dirAB770/fileAB770", "770", "text/plain", "small1.txt", 307, 201, 200 );
    createFile( userA, passA, groupAB, root + "/dirAB707/fileAB707", "707", "text/plain", "small1.txt", 307, 201, 200 );
    createFile( userA, passA, groupAB, root + "/dirAB777/fileAB777", "777", "text/plain", "small1.txt", 307, 201, 200 );
    // userB:groupB
    createFile( userB, passB, groupB, root + "/dirA700/fileB700", "700", "text/plain", "small1.txt", 307, 403, 0 );
    createFile( userB, passB, groupB, root + "/dirA770/fileB700", "700", "text/plain", "small1.txt", 307, 403, 0 );
//kam:20130219[ chmod seems to be broken at least in Sandbox 1.2
//    createFile( userB, passB, groupB, root + "/dirA707/fileB700", "700", "text/plain", "small1.txt", 307, 201, 200 );
//    createFile( userB, passB, groupB, root + "/dirA777/fileB700", "700", "text/plain", "small1.txt", 307, 201, 200 );
//kam]
    // userB:groupAB
    createFile( userB, passB, groupAB, root + "/dirA700/fileBA700", "700", "text/plain", "small1.txt", 307, 403, 0 );
    createFile( userB, passB, groupAB, root + "/dirA770/fileBA700", "700", "text/plain", "small1.txt", 307, 403, 0 );
    createFile( userB, passB, groupAB, root + "/dirA707/fileBA700", "700", "text/plain", "small1.txt", 307, 201, 200 );
    createFile( userB, passB, groupAB, root + "/dirA777/fileBA700", "700", "text/plain", "small1.txt", 307, 201, 200 );
    // userC:groupC
    createFile( userC, passC, groupC, root + "/dirA700/fileC700", "700", "text/plain", "small1.txt", 307, 403, 0 );
    createFile( userC, passC, groupC, root + "/dirA770/fileC700", "700", "text/plain", "small1.txt", 307, 403, 0 );
//kam:20130219[ chmod seems to be broken at least in Sandbox 1.2
//    createFile( userC, passC, groupC, root + "/dirA707/fileC700", "700", "text/plain", "small1.txt", 307, 201, 200 );
//    createFile( userC, passC, groupC, root + "/dirA777/fileC700", "700", "text/plain", "small1.txt", 307, 201, 200 );
//kam]

    // READ
    // userA
    readFile( userA, passA, root + "/dirA700/fileA700", "text/plain", "small1.txt", HttpStatus.SC_OK );
    readFile( userA, passA, root + "/dirA770/fileA770", "text/plain", "small1.txt", HttpStatus.SC_OK );
    readFile( userA, passA, root + "/dirA707/fileA707", "text/plain", "small1.txt", HttpStatus.SC_OK );
    readFile( userA, passA, root + "/dirA777/fileA777", "text/plain", "small1.txt", HttpStatus.SC_OK );
    // userB:groupB
    readFile( userB, passB, root + "/dirA700/fileA700", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    readFile( userB, passB, root + "/dirA770/fileA770", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    readFile( userB, passB, root + "/dirA707/fileA707", "text/plain", "small1.txt", HttpStatus.SC_OK );
    readFile( userB, passB, root + "/dirA777/fileA777", "text/plain", "small1.txt", HttpStatus.SC_OK );
    // userB:groupAB
    readFile( userB, passB, root + "/dirAB700/fileAB700", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    readFile( userB, passB, root + "/dirAB770/fileAB770", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    readFile( userB, passB, root + "/dirAB707/fileAB707", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    readFile( userB, passB, root + "/dirAB777/fileAB777", "text/plain", "small1.txt", HttpStatus.SC_OK );
    // userC:groupC
    readFile( userC, passC, root + "/dirA700/fileA700", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    readFile( userC, passC, root + "/dirA770/fileA770", "text/plain", "small1.txt", HttpStatus.SC_FORBIDDEN );
    readFile( userC, passC, root + "/dirA707/fileA707", "text/plain", "small1.txt", HttpStatus.SC_OK );
    readFile( userC, passC, root + "/dirA777/fileA777", "text/plain", "small1.txt", HttpStatus.SC_OK );

    //NEGATIVE: Test a bad password.
    if( driver.isUseGateway() ) {
      given()
          //.log().all()
          .auth().preemptive().basic( userA, "invalid-password" )
          .header("X-XSRF-Header", "jksdhfkhdsf")
          .queryParam( "op", "OPEN" )
          .then()
          //.log().all()
          .statusCode( HttpStatus.SC_UNAUTHORIZED )
          .when().get( driver.getUrl("WEBHDFS") + "/v1" + root + "/dirA700/fileA700" );
    }
    driver.assertComplete();

    // UPDATE (Negative First)
    updateFile( userC, passC, root + "/dirA700/fileA700", "text/plain", "small2.txt", 307, 403 );
    updateFile( userB, passB, root + "/dirAB700/fileAB700", "text/plain", "small2.txt", 307, 403 );
    updateFile( userB, passB, root + "/dirAB770/fileAB700", "text/plain", "small2.txt", 307, 403 );
    updateFile( userB, passB, root + "/dirAB770/fileAB770", "text/plain", "small2.txt", 307, 403 );
    updateFile( userA, passA, root + "/dirA700/fileA700", "text/plain", "small2.txt", 307, 201 );

    // DELETE (Negative First)
    deleteFile( userC, passC, root + "/dirA700/fileA700", "false", HttpStatus.SC_FORBIDDEN );
    deleteFile( userB, passB, root + "/dirAB700/fileAB700", "false", HttpStatus.SC_FORBIDDEN );
    deleteFile( userB, passB, root + "/dirAB770/fileAB770", "false", HttpStatus.SC_FORBIDDEN );
    deleteFile( userA, passA, root + "/dirA700/fileA700", "false", HttpStatus.SC_OK );

    // Cleanup anything that might have been leftover because the test failed previously.
    deleteFile( userA, passA, root, "true", HttpStatus.SC_OK );
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testJavaMapReduceViaWebHCat() throws IOException {
    LOG_ENTER();
    String root = "/tmp/GatewayBasicFuncTest/testJavaMapReduceViaWebHCat";
    String user = "mapred";
    String pass = "mapred-password";
//    String user = "hcat";
//    String pass = "hcat-password";
//    String group = "hcat";

    // Cleanup anything that might have been leftover because the test failed previously.
    deleteFile( user, pass, root, "true", HttpStatus.SC_OK );

    /* Put the mapreduce code into HDFS. (hadoop-examples.jar)
    curl -X PUT --data-binary @hadoop-examples.jar 'http://192.168.1.163:8888/org.apache.org.apache.knox.gateway/cluster/webhdfs/v1/user/hdfs/wordcount/hadoop-examples.jar?user.name=hdfs&op=CREATE'
     */
    createFile( user, pass, null, root+"/hadoop-examples.jar", "777", "application/octet-stream", findHadoopExamplesJar(), 307, 201, 200 );

    /* Put the data file into HDFS (changes.txt)
    curl -X PUT --data-binary @changes.txt 'http://192.168.1.163:8888/org.apache.org.apache.knox.gateway/cluster/webhdfs/v1/user/hdfs/wordcount/input/changes.txt?user.name=hdfs&op=CREATE'
     */
    createFile( user, pass, null, root+"/input/changes.txt", "777", "text/plain", "changes.txt", 307, 201, 200 );

    /* Create the output directory
    curl -X PUT 'http://192.168.1.163:8888/org.apache.org.apache.knox.gateway/cluster/webhdfs/v1/user/hdfs/wordcount/output?op=MKDIRS&user.name=hdfs'
    */
    createDir( user, pass, null, root+"/output", "777", 200, 200 );

    /* Submit the job
    curl -d user.name=hdfs -d jar=wordcount/hadoop-examples.jar -d class=org.apache.org.apache.hadoop.examples.WordCount -d arg=wordcount/input -d arg=wordcount/output 'http://localhost:8888/org.apache.org.apache.knox.gateway/cluster/templeton/v1/mapreduce/jar'
    {"id":"job_201210301335_0059"}
    */
    String job = submitJava(
        user, pass,
        root+"/hadoop-examples.jar", "org.apache.org.apache.hadoop.examples.WordCount",
        root+"/input", root+"/output",
        200 );

    /* Get the job status
    curl 'http://vm:50111/templeton/v1/queue/:jobid?user.name=hdfs'
    */
    queryQueue( user, pass, job );

    // Can't really check for the output here because the job won't be done.
    /* Retrieve results
    curl 'http://192.168.1.163:8888/org.apache.org.apache.knox.gateway/cluster/webhdfs/v1/user/hdfs/wordcount/input?op=LISTSTATUS'
    */

    if( CLEANUP_TEST ) {
      // Cleanup anything that might have been leftover because the test failed previously.
      deleteFile( user, pass, root, "true", HttpStatus.SC_OK );
    }
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testPigViaWebHCat() throws IOException {
    LOG_ENTER();
    String root = "/tmp/GatewayWebHCatFuncTest/testPigViaWebHCat";
    String user = "mapred";
    String pass = "mapred-password";
    String group = "mapred";

    // Cleanup if previous run failed.
    deleteFile( user, pass, root, "true", 200, 404 );

    // Post the data to HDFS
    createFile( user, pass, null, root + "/passwd.txt", "777", "text/plain", "passwd.txt", 307, 201, 200 );

    // Post the script to HDFS
    createFile( user, pass, null, root+"/script.pig", "777", "text/plain", "script.pig", 307, 201, 200 );

    // Create the output directory
    createDir( user, pass, null, root + "/output", "777", 200, 200 );

    // Submit the job
    submitPig( user, pass, group, root + "/script.pig", "-v", root + "/output", 200 );

    // Check job status (if possible)
    // Check output (if possible)

    // Cleanup
    deleteFile( user, pass, root, "true", 200 );
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testHiveViaWebHCat() throws IOException {
    LOG_ENTER();
    String user = "hive";
    String pass = "hive-password";
    String group = "hive";
    String root = "/tmp/GatewayWebHCatFuncTest/testHiveViaWebHCat";

    // Cleanup if previous run failed.
    deleteFile( user, pass, root, "true", 200, 404 );

    // Post the data to HDFS

    // Post the script to HDFS
    createFile(user, pass, null, root + "/script.hive", "777", "text/plain", "script.hive", 307, 201, 200);

    // Submit the job
    submitHive(user, pass, group, root + "/script.hive", root + "/output", 200);

    // Check job status (if possible)
    // Check output (if possible)

    // Cleanup
    deleteFile( user, pass, root, "true", 200 );
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testOozieJobSubmission() throws Exception {
    LOG_ENTER();
    String root = "/tmp/GatewayBasicFuncTest/testOozieJobSubmission";
    String user = "hdfs";
    String pass = "hdfs-password";
    String group = "hdfs";

    // Cleanup anything that might have been leftover because the test failed previously.
    deleteFile( user, pass, root, "true", HttpStatus.SC_OK );

    /* Put the workflow definition into HDFS */
    createFile( user, pass, group, root+"/workflow.xml", "666", "application/octet-stream", "oozie-workflow.xml", 307, 201, 200 );

    /* Put the mapreduce code into HDFS. (hadoop-examples.jar)
    curl -X PUT --data-binary @hadoop-examples.jar 'http://192.168.1.163:8888/org.apache.org.apache.knox.gateway/cluster/webhdfs/v1/user/hdfs/wordcount/hadoop-examples.jar?user.name=hdfs&op=CREATE'
     */
    createFile( user, pass, group, root+"/lib/hadoop-examples.jar", "777", "application/octet-stream", findHadoopExamplesJar(), 307, 201, 200 );

    /* Put the data file into HDFS (changes.txt)
    curl -X PUT --data-binary @changes.txt 'http://192.168.1.163:8888/org.apache.org.apache.knox.gateway/cluster/webhdfs/v1/user/hdfs/wordcount/input/changes.txt?user.name=hdfs&op=CREATE'
     */
    createFile( user, pass, group, root+"/input/changes.txt", "666", "text/plain", "changes.txt", 307, 201, 200 );

    VelocityEngine velocity = new VelocityEngine();
    velocity.setProperty( "runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogSystem" );
    velocity.setProperty( RuntimeConstants.RESOURCE_LOADER, "classpath" );
    velocity.setProperty( "classpath.resource.loader.class", ClasspathResourceLoader.class.getName() );
    velocity.init();

    VelocityContext context = new VelocityContext();
    context.put( "userName", user );
    context.put( "nameNode", "hdfs://sandbox:8020" );
    context.put( "jobTracker", "sandbox:50300" );
    //context.put( "appPath", "hdfs://sandbox:8020" + root );
    context.put( "appPath", root );
    context.put( "inputDir", root + "/input" );
    context.put( "outputDir", root + "/output" );

    String name = TestUtils.getResourceName( this.getClass(), "oozie-jobs-submit-request.xml" );
    Template template = velocity.getTemplate( name );
    StringWriter sw = new StringWriter();
    template.merge( context, sw );
    String request = sw.toString();

    /* Submit the job via Oozie. */
    String id = oozieSubmitJob( user, pass, request, 201 );

    String success = "SUCCEEDED";
    String status = "UNKNOWN";
    long delay = 1000; // 1 second.
    long limit = 1000 * 60; // 60 seconds.
    long start = System.currentTimeMillis();
    while( System.currentTimeMillis() <= start+limit ) {
      status = oozieQueryJobStatus( user, pass, id, 200 );
      if( success.equalsIgnoreCase( status ) ) {
        break;
      } else {
        Thread.sleep( delay );
      }
    }
    assertThat( status, is( success ) );

    if( CLEANUP_TEST ) {
      // Cleanup anything that might have been leftover because the test failed previously.
      deleteFile( user, pass, root, "true", HttpStatus.SC_OK );
    }
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testBasicHiveJDBCUseCase() throws IOException {
    LOG_ENTER();
    String username = "hive";
    String password = "hive-password";

    // This use case emulates simple JDBC scenario which consists of following steps:
    // -open connection;
    // -configure Hive using 'execute' statements (this also includes execution of 'close operation' requests internally);
    // -execution of create table command;
    // -execution of select from table command;
    // Data insertion is omitted because it causes a lot of additional command during insertion/querying.
    // All binary data was intercepted during real scenario and stored into files as array of bytes.

    // open session
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/open-session-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/open-session-result.bin" ) )
        .contentType( "application/x-thrift" );
    Response response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/open-session-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/open-session-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/open-session-result.bin" ) ) );

    driver.assertComplete();

    // execute 'set hive.fetch.output.serde=...' (is called internally be JDBC driver)
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/execute-set-fetch-output-serde-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/execute-set-fetch-output-serde-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/execute-set-fetch-output-serde-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/execute-set-fetch-output-serde-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/execute-set-fetch-output-serde-result.bin" ) ) );
    driver.assertComplete();

    // close operation for execute 'set hive.fetch.output.serde=...'
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/close-operation-1-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/close-operation-1-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/close-operation-1-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/close-operation-1-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/close-operation-1-result.bin" ) ) );
    driver.assertComplete();

    // execute 'set hive.server2.http.path=...' (is called internally be JDBC driver)
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/execute-set-server2-http-path-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/execute-set-server2-http-path-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/execute-set-server2-http-path-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/execute-set-server2-http-path-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/execute-set-server2-http-path-result.bin" ) ) );
    driver.assertComplete();

    // close operation for execute 'set hive.server2.http.path=...'
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/close-operation-2-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/close-operation-2-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/close-operation-2-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/close-operation-2-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/close-operation-2-result.bin" ) ) );
    driver.assertComplete();

    // execute 'set hive.server2.servermode=...' (is called internally be JDBC driver)
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/execute-set-server2-servermode-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/execute-set-server2-servermode-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/execute-set-server2-servermode-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/execute-set-server2-servermode-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/execute-set-server2-servermode-result.bin" ) ) );
    driver.assertComplete();

    // close operation for execute 'set hive.server2.servermode=...'
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/close-operation-3-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/close-operation-3-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/close-operation-3-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/close-operation-3-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/close-operation-3-result.bin" ) ) );
    driver.assertComplete();

    // execute 'set hive.security.authorization.enabled=...'
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/execute-set-security-authorization-enabled-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/execute-set-security-authorization-enabled-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/execute-set-security-authorization-enabled-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/execute-set-security-authorization-enabled-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/execute-set-security-authorization-enabled-result.bin" ) ) );
    driver.assertComplete();

    // close operation for execute 'set hive.security.authorization.enabled=...'
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/close-operation-4-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/close-operation-4-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/close-operation-4-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/close-operation-4-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/close-operation-4-result.bin" ) ) );
    driver.assertComplete();

    // execute 'create table...'
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/execute-create-table-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/execute-create-table-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/execute-create-table-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/execute-create-table-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/execute-create-table-result.bin" ) ) );
    driver.assertComplete();

    // close operation for execute 'create table...'
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/close-operation-5-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/close-operation-5-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/close-operation-5-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/close-operation-5-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/close-operation-5-result.bin" ) ) );
    driver.assertComplete();

    // execute 'select * from...'
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/execute-select-from-table-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/execute-select-from-table-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/execute-select-from-table-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/execute-select-from-table-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/execute-select-from-table-result.bin" ) ) );
    driver.assertComplete();

    // execute 'GetResultSetMetadata' (is called internally be JDBC driver)
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/get-result-set-metadata-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/get-result-set-metadata-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/get-result-set-metadata-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/get-result-set-metadata-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/get-result-set-metadata-result.bin" ) ) );
    driver.assertComplete();

    // execute 'FetchResults' (is called internally be JDBC driver)
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/fetch-results-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/fetch-results-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/fetch-results-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/fetch-results-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/fetch-results-result.bin" ) ) );
    driver.assertComplete();

    // close operation for execute 'select * from...'
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/close-operation-6-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/close-operation-6-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/close-operation-6-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/close-operation-6-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/close-operation-6-result.bin" ) ) );
    driver.assertComplete();

    // close session
    driver.getMock( "HIVE" )
        .expect()
        .method( "POST" )
        .content( driver.getResourceBytes( "hive/close-session-request.bin" ) )
        .contentType( "application/x-thrift" )
        .respond()
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "hive/close-session-result.bin" ) )
        .contentType( "application/x-thrift" );
    response = given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body( driver.getResourceBytes( "hive/close-session-request.bin" ) )
        .contentType( "application/x-thrift" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        //.content( is( driver.getResourceBytes( "hive/close-session-result.bin" ) ) )
        .contentType( "application/x-thrift" )
        .when().post( driver.getUrl( "HIVE" ) );
    assertThat( response.body().asByteArray(), is( driver.getResourceBytes( "hive/close-session-result.bin" ) ) );
    driver.assertComplete();
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testHBaseGetTableList() throws IOException {
    LOG_ENTER();
    String username = "hbase";
    String password = "hbase-password";
    String resourceName = "hbase/table-list";

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "GET" )
    .pathInfo( "/" )
    .header( "Accept", ContentType.XML.toString() )
    .respond()
    .status( HttpStatus.SC_OK )
    .content( driver.getResourceBytes( resourceName + ".xml" ) )
    .contentType( ContentType.XML.toString() );

    Response response = given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .header( "Accept", ContentType.XML.toString() )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .contentType( ContentType.XML )
    .when().get( driver.getUrl( "WEBHBASE" ) );

    assertThat(
            the( response.getBody().asString() ),
            isEquivalentTo( the( driver.getResourceString( resourceName + ".xml" ) ) ) );
    driver.assertComplete();

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "GET" )
    .pathInfo( "/" )
    .header( "Accept", ContentType.JSON.toString() )
    .respond()
    .status( HttpStatus.SC_OK )
    .content( driver.getResourceBytes( resourceName + ".json" ) )
    .contentType( ContentType.JSON.toString() );

    response = given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .header( "Accept", ContentType.JSON.toString() )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .contentType( ContentType.JSON )
    .when().get( driver.getUrl( "WEBHBASE" ) );

    assertThat( response.getBody().asString(), sameJSONAs( driver.getResourceString( resourceName + ".json") ) );
    driver.assertComplete();

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "GET" )
    .pathInfo( "/" )
    .header( "Accept", "application/x-protobuf" )
    .respond()
    .status( HttpStatus.SC_OK )
    .content( driver.getResourceString( resourceName + ".protobuf"), StandardCharsets.UTF_8 )
    .contentType( "application/x-protobuf" );

    given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .header( "Accept", "application/x-protobuf" )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .contentType( "application/x-protobuf" )
    .body( is( driver.getResourceString( resourceName + ".protobuf") ) )
    .when().get( driver.getUrl( "WEBHBASE" ) );
    driver.assertComplete();
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testHBaseCreateTableAndVerifySchema() throws IOException {
    LOG_ENTER();
    String username = "hbase";
    String password = "hbase-password";
    String resourceName = "hbase/table-schema";
    String path = "/table/schema";

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "PUT" )
    .pathInfo( path )
    .respond()
    .status( HttpStatus.SC_CREATED )
    .content( driver.getResourceBytes( resourceName + ".xml" ) )
    .contentType( ContentType.XML.toString() )
    .header( "Location", driver.getRealUrl( "WEBHBASE" ) + path  );

    given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .then()
    .statusCode( HttpStatus.SC_CREATED )
    .contentType( ContentType.XML )
    .header( "Location", startsWith( driver.getUrl( "WEBHBASE" ) + path ) )
    .when().put(driver.getUrl("WEBHBASE") + path);
    driver.assertComplete();

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "PUT" )
    .pathInfo( path )
    .respond()
    .status(HttpStatus.SC_CREATED)
    .content(driver.getResourceBytes(resourceName + ".json"))
    .contentType(ContentType.JSON.toString())
    .header("Location", driver.getRealUrl("WEBHBASE") + path);

    given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .then()
    .statusCode( HttpStatus.SC_CREATED )
    .contentType( ContentType.JSON )
    .header( "Location", startsWith( driver.getUrl( "WEBHBASE" ) + path ) )
    .when().put( driver.getUrl( "WEBHBASE" ) + path );
    driver.assertComplete();

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "PUT" )
    .pathInfo( path )
    .respond()
    .status( HttpStatus.SC_CREATED )
    .content( driver.getResourceBytes( resourceName + ".protobuf" ) )
    .contentType( "application/x-protobuf" )
    .header("Location", driver.getRealUrl("WEBHBASE") + path);

    given()
    .auth().preemptive().basic(username, password)
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .then()
    .statusCode(HttpStatus.SC_CREATED)
    .contentType("application/x-protobuf")
    .header("Location", startsWith(driver.getUrl("WEBHBASE") + path))
    .when().put(driver.getUrl("WEBHBASE") + path);
    driver.assertComplete();

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testHBaseGetTableSchema() throws IOException {
    LOG_ENTER();
    String username = "hbase";
    String password = "hbase-password";
    String resourceName = "hbase/table-metadata";
    String path = "/table/schema";

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "GET" )
    .pathInfo( path )
    .header("Accept", ContentType.XML.toString())
    .respond()
    .status(HttpStatus.SC_OK)
    .content(driver.getResourceBytes(resourceName + ".xml"))
    .contentType(ContentType.XML.toString());

    Response response = given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .header( "Accept", ContentType.XML.toString() )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .contentType( ContentType.XML )
    .when().get( driver.getUrl( "WEBHBASE" ) + path );

    assertThat(
            the(response.getBody().asString()),
            isEquivalentTo(the(driver.getResourceString(resourceName + ".xml"))));
    driver.assertComplete();

    driver.getMock("WEBHBASE")
    .expect()
    .method("GET")
    .pathInfo(path)
    .header("Accept", ContentType.JSON.toString())
    .respond()
    .status(HttpStatus.SC_OK)
    .content(driver.getResourceBytes(resourceName + ".json"))
    .contentType(ContentType.JSON.toString());

    response = given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .header( "Accept", ContentType.JSON.toString() )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .contentType( ContentType.JSON )
    .when().get( driver.getUrl( "WEBHBASE" ) + path );

    assertThat(response.getBody().asString(), sameJSONAs(driver.getResourceString(resourceName + ".json")));
    driver.assertComplete();

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "GET" )
    .pathInfo( path )
    .header( "Accept", "application/x-protobuf" )
    .respond()
    .status( HttpStatus.SC_OK )
    .content( driver.getResourceBytes( resourceName + ".protobuf" ) )
    .contentType("application/x-protobuf");

    response = given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .header( "Accept", "application/x-protobuf" )
    .then()
    .statusCode( HttpStatus.SC_OK )
    //.content( is( driver.getResourceBytes( resourceName + ".protobuf" ) ) )
    .contentType( "application/x-protobuf" )
    .when().get( driver.getUrl( "WEBHBASE" ) + path );
    // RestAssured seems to be screwing up the binary comparison so do it explicitly.
    assertThat( driver.getResourceBytes( resourceName + ".protobuf" ), is( response.body().asByteArray() ) );
    driver.assertComplete();
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testEncodedForwardSlash() throws IOException {
    LOG_ENTER();
    String username = "hbase";
    String password = "hbase-password";

    String resourceName = "hbase/table-data";
    String singleRowPath = "/table/%2F%2Ftestrow";

    //PUT request
    driver.getMock( "WEBHBASE" )
        .expect()
        .method( "PUT" )
        .requestURI( singleRowPath ) // Need to use requestURI since pathInfo is url decoded
        .contentType( ContentType.JSON.toString() )
        .respond()
        .status( HttpStatus.SC_OK );

    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
    credentialsProvider.setCredentials(AuthScope.ANY, credentials);
    // Show that normalizing from HttpClient doesn't change the behavior of %2F handling
    // with HttpClient 4.5.8+ - HTTPCLIENT-1968
    RequestConfig requestConfig = RequestConfig.custom().setNormalizeUri(false).build();
    try(CloseableHttpClient httpClient = HttpClients.custom()
        .setDefaultCredentialsProvider(credentialsProvider)
        .setDefaultRequestConfig(requestConfig)
        .build()) {

      HttpPut httpPut = new HttpPut(driver.getUrl("WEBHBASE") + singleRowPath);
      httpPut.setHeader("X-XSRF-Header", "jksdhfkhdsf");
      httpPut.setHeader(HttpHeaders.CONTENT_TYPE, org.apache.http.entity.ContentType.APPLICATION_JSON.getMimeType());
      httpPut.setEntity(new ByteArrayEntity(driver.getResourceBytes(resourceName + ".json")));

      HttpResponse response = httpClient.execute(httpPut);
      assertEquals(200, response.getStatusLine().getStatusCode());
    }
    driver.assertComplete();

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "PUT" )
    .requestURI( singleRowPath ) // Need to use requestURI since pathInfo is url decoded
    .contentType( ContentType.JSON.toString() )
    .respond()
    .status( HttpStatus.SC_OK );

    // There is no way to change the normalization behavior of the
    // HttpClient created by rest-assured since RequestConfig isn't
    // exposed. Instead the above test uses HttpClient directly,
    // this shows that url normalization doesn't matter with 4.5.8+.
    // If this view changes, don't use rest-assured for this type of
    // testing.
    // See: https://github.com/rest-assured/rest-assured/issues/497
    given()
    .urlEncodingEnabled(false) // make sure to avoid rest-assured automatic url encoding
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .body( driver.getResourceBytes( resourceName + ".json" ) )
    .contentType( ContentType.JSON.toString() )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .when().put(driver.getUrl("WEBHBASE") + singleRowPath);
    driver.assertComplete();

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testHBaseInsertDataIntoTable() throws IOException {
    LOG_ENTER();
    String username = "hbase";
    String password = "hbase-password";

    String resourceName = "hbase/table-data";
    String singleRowPath = "/table/testrow";
    String multipleRowPath = "/table/false-row-key";

    //PUT request

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "PUT" )
    .pathInfo( multipleRowPath )
    //.header( "Content-Type", ContentType.XML.toString() )
    .content( driver.getResourceBytes( resourceName + ".xml" ) )
    .contentType( ContentType.XML.toString() )
    .respond()
    .status(HttpStatus.SC_OK);

    given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    //.header( "Content-Type", ContentType.XML.toString() )
    .body( driver.getResourceBytes( resourceName + ".xml" ) )
    .contentType( ContentType.XML.toString() )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .when().put(driver.getUrl("WEBHBASE") + multipleRowPath);
    driver.assertComplete();

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "PUT" )
    .pathInfo( singleRowPath )
    //.header( "Content-Type", ContentType.JSON.toString() )
    .contentType( ContentType.JSON.toString() )
    .respond()
    .status( HttpStatus.SC_OK );

    given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    //.header( "Content-Type", ContentType.JSON.toString() )
    .body( driver.getResourceBytes( resourceName + ".json" ) )
    .contentType( ContentType.JSON.toString() )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .when().put(driver.getUrl("WEBHBASE") + singleRowPath);
    driver.assertComplete();

    driver.getMock("WEBHBASE")
    .expect()
    .method("PUT")
    .pathInfo(multipleRowPath)
    //.header( "Content-Type", "application/x-protobuf" )
    .contentType("application/x-protobuf")
    .content(driver.getResourceBytes(resourceName + ".protobuf"))
    .respond()
    .status(HttpStatus.SC_OK);

    given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    //.header( "Content-Type", "application/x-protobuf" )
    .body( driver.getResourceBytes( resourceName + ".protobuf" ) )
    .contentType( "application/x-protobuf" )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .when().put( driver.getUrl( "WEBHBASE" ) + multipleRowPath );
    driver.assertComplete();

    //POST request

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "POST" )
    .pathInfo( multipleRowPath )
    //.header( "Content-Type", ContentType.XML.toString() )
    .content( driver.getResourceBytes( resourceName + ".xml" ) )
    .contentType( ContentType.XML.toString() )
    .respond()
    .status( HttpStatus.SC_OK );

    given()
      .auth().preemptive().basic( username, password )
      .header("X-XSRF-Header", "jksdhfkhdsf")
      //.header( "Content-Type", ContentType.XML.toString() )
      .body( driver.getResourceBytes( resourceName + ".xml" ) )
      .contentType( ContentType.XML.toString() )
      .then()
      .statusCode( HttpStatus.SC_OK )
      .when().post( driver.getUrl( "WEBHBASE" ) + multipleRowPath );
    driver.assertComplete();

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "POST" )
    .pathInfo( singleRowPath )
    //.header( "Content-Type", ContentType.JSON.toString() )
    .contentType( ContentType.JSON.toString() )
    .respond()
    .status( HttpStatus.SC_OK );

    given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    //.header( "Content-Type", ContentType.JSON.toString() )
    .body( driver.getResourceBytes( resourceName + ".json" ) )
    .contentType( ContentType.JSON.toString() )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .when().post( driver.getUrl( "WEBHBASE" ) + singleRowPath );
    driver.assertComplete();

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "POST" )
    .pathInfo( multipleRowPath )
    //.header( "Content-Type", "application/x-protobuf" )
    .content( driver.getResourceBytes( resourceName + ".protobuf" ) )
    .contentType( "application/x-protobuf" )
    .respond()
    .status( HttpStatus.SC_OK );

    given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    //.header( "Content-Type", "application/x-protobuf" )
    .body( driver.getResourceBytes( resourceName + ".protobuf" ) )
    .contentType( "application/x-protobuf" )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .when().post(driver.getUrl("WEBHBASE") + multipleRowPath);
    driver.assertComplete();
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testHBaseDeleteDataFromTable() {
    LOG_ENTER();
    String username = "hbase";
    String password = "hbase-password";

    String tableId = "table";
    String rowId = "row";
    String familyId = "family";
    String columnId = "column";

    driver.getMock("WEBHBASE")
    .expect()
    .from("testHBaseDeleteDataFromTable-1")
    .method("DELETE")
    .pathInfo("/" + tableId + "/" + rowId)
    .respond()
    .status(HttpStatus.SC_OK);

    given()
    .auth().preemptive().basic(username, password)
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .then()
    .statusCode( HttpStatus.SC_OK )
    .when().delete(driver.getUrl("WEBHBASE") + "/" + tableId + "/" + rowId);
    driver.assertComplete();

    driver.getMock( "WEBHBASE" )
    .expect()
    .from("testHBaseDeleteDataFromTable-2")
    .method("DELETE")
    .pathInfo("/" + tableId + "/" + rowId + "/" + familyId)
    .respond()
    .status( HttpStatus.SC_OK );

    given()
    .auth().preemptive().basic(username, password)
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .then()
    .statusCode( HttpStatus.SC_OK )
    .when().delete(driver.getUrl("WEBHBASE") + "/" + tableId + "/" + rowId + "/" + familyId);
    driver.assertComplete();

    driver.getMock("WEBHBASE")
    .expect()
    .from("testHBaseDeleteDataFromTable-3")
    .method("DELETE")
    .pathInfo("/" + tableId + "/" + rowId + "/" + familyId + ":" + columnId)
    .respond()
    .status(HttpStatus.SC_OK);

    given()
    .auth().preemptive().basic(username, password)
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .then()
    .statusCode( HttpStatus.SC_OK )
    .when().delete(driver.getUrl("WEBHBASE") + "/" + tableId + "/" + rowId + "/" + familyId + ":" + columnId);
    driver.assertComplete();

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testHBaseQueryTableData() throws IOException {
    LOG_ENTER();
    String username = "hbase";
    String password = "hbase-password";

    String resourceName = "hbase/table-data";

    String allRowsPath = "/table/*";
    String rowsStartsWithPath = "/table/row*";
    String rowsWithKeyPath = "/table/row";
    String rowsWithKeyAndColumnPath = "/table/row/family:col";

    driver.getMock("WEBHBASE")
    .expect()
    .method("GET")
    .pathInfo(allRowsPath)
    .header("Accept", ContentType.XML.toString())
    .respond()
    .status(HttpStatus.SC_OK)
    .content(driver.getResourceBytes(resourceName + ".xml"))
    .contentType(ContentType.XML.toString());

    Response response = given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .header( "Accept", ContentType.XML.toString() )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .contentType( ContentType.XML )
    .when().get( driver.getUrl( "WEBHBASE" ) + allRowsPath );

    assertThat(
        the(response.getBody().asString()),
        isEquivalentTo(the(driver.getResourceString(resourceName + ".xml"))));
    driver.assertComplete();

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "GET" )
    .pathInfo( rowsStartsWithPath )
    .header( "Accept", ContentType.XML.toString() )
    .respond()
    .status( HttpStatus.SC_OK )
    .content( driver.getResourceBytes( resourceName + ".xml" ) )
    .contentType(ContentType.XML.toString());

    response = given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .header( "Accept", ContentType.XML.toString() )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .contentType( ContentType.XML )
    .when().get( driver.getUrl( "WEBHBASE" ) + rowsStartsWithPath );

    assertThat(
        the(response.getBody().asString()),
        isEquivalentTo(the(driver.getResourceString(resourceName + ".xml"))));
    driver.assertComplete();

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "GET" )
    .pathInfo( rowsWithKeyPath )
    .header( "Accept", ContentType.JSON.toString() )
    .respond()
    .status( HttpStatus.SC_OK )
    .content( driver.getResourceBytes( resourceName + ".json" ) )
    .contentType( ContentType.JSON.toString() );

    response = given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .header( "Accept", ContentType.JSON.toString() )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .contentType( ContentType.JSON )
    .when().get( driver.getUrl( "WEBHBASE" ) + rowsWithKeyPath );

    assertThat( response.getBody().asString(), sameJSONAs( driver.getResourceString( resourceName + ".json") ) );
    driver.assertComplete();

    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "GET" )
    .pathInfo( rowsWithKeyAndColumnPath )
    .header( "Accept", ContentType.JSON.toString() )
    .respond()
    .status( HttpStatus.SC_OK )
    .content( driver.getResourceBytes( resourceName + ".json" ) )
    .contentType( ContentType.JSON.toString() );

    response = given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .header( "Accept", ContentType.JSON.toString() )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .contentType( ContentType.JSON )
    .when().get( driver.getUrl( "WEBHBASE" ) + rowsWithKeyAndColumnPath );

    assertThat( response.getBody().asString(), sameJSONAs( driver.getResourceString( resourceName + ".json") ) );
    driver.assertComplete();
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testHBaseUseScanner() throws IOException {
    LOG_ENTER();
    String username = "hbase";
    String password = "hbase-password";

    String scannerDefinitionResourceName = "hbase/scanner-definition";
    String tableDataResourceName = "hbase/table-data";
    String scannerPath = "/table/scanner";
    String scannerId = "13705290446328cff5ed";

    //Create scanner for table using PUT and POST requests
    driver.getMock("WEBHBASE")
    .expect()
    .method("PUT")
    .pathInfo(scannerPath)
    .respond()
    .status(HttpStatus.SC_CREATED);

    given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .header( "Content-Type", ContentType.XML.toString() )
    .body( driver.getResourceBytes( scannerDefinitionResourceName + ".xml" ) )
    .then()
    //TODO: Add "Location" header check  when issue with incorrect outbound rewrites will be resolved
    //.header( "Location", startsWith( driver.getUrl( "WEBHBASE" ) + createScannerPath ) )
    .statusCode( HttpStatus.SC_CREATED )
    .when().put( driver.getUrl( "WEBHBASE" ) + scannerPath );
    driver.assertComplete();

    //Get the values of the next cells found by the scanner
    driver.getMock( "WEBHBASE" )
    .expect()
    .method( "GET" )
    .pathInfo( scannerPath + "/" + scannerId )
    .header( "Accept", ContentType.XML.toString() )
    .respond()
    .status( HttpStatus.SC_OK )
    .content( driver.getResourceBytes( tableDataResourceName + ".xml" ) )
    .contentType(ContentType.XML.toString());

    Response response = given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .header( "Accept", ContentType.XML.toString() )
    .then()
    .statusCode( HttpStatus.SC_OK )
    .contentType( ContentType.XML )
    .when().get( driver.getUrl( "WEBHBASE" ) + scannerPath + "/" + scannerId );

    assertThat(
        the(response.getBody().asString()),
        isEquivalentTo(the(driver.getResourceString(tableDataResourceName + ".xml"))));
    driver.assertComplete();

    //Delete scanner
    driver.getMock( "WEBHBASE" )
    .expect()
    .from( "testHBaseUseScanner" )
    .method( "DELETE" )
    .pathInfo( scannerPath + "/" + scannerId )
    .respond()
    .status( HttpStatus.SC_OK );

    given()
    .auth().preemptive().basic( username, password )
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .then()
    .statusCode( HttpStatus.SC_OK )
    .when().delete(driver.getUrl("WEBHBASE") + scannerPath + "/" + scannerId);
    driver.assertComplete();
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testCrossSiteRequestForgeryPreventionPUT() throws IOException {
    LOG_ENTER();
    String root = "/tmp/GatewayWebHdfsFuncTest/testCrossSiteRequestForgeryPrevention";
    String username = "hdfs";
    String password = "hdfs-password";

    given()
//        .log().all()
        .auth().preemptive().basic( username, password )
//        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "MKDIRS" )
        .then()
//            .log().all()
        .statusCode( HttpStatus.SC_BAD_REQUEST )
        .when().put( driver.getUrl( "WEBHDFS" ) + "/v1" + root + "/dir" );
    driver.assertComplete();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testCrossSiteRequestForgeryPreventionGET() throws IOException {
    LOG_ENTER();
    String root = "/tmp/GatewayWebHdfsFuncTest/testCrossSiteRequestForgeryPrevention";
    String username = "hdfs";
    String password = "hdfs-password";

    driver.getMock("WEBHDFS")
        .expect()
        .method("GET")
        .pathInfo("/v1" + root + "/dir")
        .queryParam("op", "LISTSTATUS")
        .queryParam("user.name", username)
        .respond()
        .status(HttpStatus.SC_OK);
    given()
//        .log().all()
        .auth().preemptive().basic( username, password )
//        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "LISTSTATUS" )
        .then()
//            .log().all()
        .statusCode( HttpStatus.SC_OK )
        .when().get( driver.getUrl( "WEBHDFS" ) + "/v1" + root + "/dir" );
//    driver.reset();
    driver.assertComplete();
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testYarnRmGetClusterInfo() throws Exception {
    LOG_ENTER();
    getYarnRmResource( "/v1/cluster/", ContentType.JSON, "yarn/cluster-info" );
    getYarnRmResource( "/v1/cluster/", ContentType.XML, "yarn/cluster-info" );
    getYarnRmResource( "/v1/cluster/info/", ContentType.JSON, "yarn/cluster-info" );
    getYarnRmResource("/v1/cluster/info/", ContentType.XML, "yarn/cluster-info");
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testYarnRmGetClusterMetrics() throws Exception {
    LOG_ENTER();
    getYarnRmResource( "/v1/cluster/metrics/", ContentType.JSON, "yarn/cluster-metrics" );
    getYarnRmResource("/v1/cluster/metrics/", ContentType.XML, "yarn/cluster-metrics");
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testYarnRnGetScheduler() throws Exception {
    LOG_ENTER();
    getYarnRmResource( "/v1/cluster/scheduler/", ContentType.JSON, "yarn/scheduler" );
    getYarnRmResource("/v1/cluster/scheduler/", ContentType.XML, "yarn/scheduler");
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void getYarnRmAppstatistics() throws Exception {
    LOG_ENTER();
    getYarnRmResource( "/v1/cluster/appstatistics/", ContentType.JSON, "yarn/appstatistics" );
    getYarnRmResource("/v1/cluster/appstatistics/", ContentType.XML, "yarn/appstatistics");
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testYarnRmGetApplications() throws Exception {
    LOG_ENTER();
    getYarnRmApps( ContentType.XML, null );
    getYarnRmApps( ContentType.JSON, null );

    Map<String, String> params = new HashMap<>();
    params.put( "states", "FINISHED" );
    params.put( "finalStatus", "SUCCEEDED" );
    params.put( "user", "test" );
    params.put( "queue", "queueName" );
    params.put( "limit", "100" );
    params.put( "startedTimeBegin", "1399903578539" );
    params.put( "startedTimeEnd", "1399903578539" );
    params.put( "finishedTimeBegin", "1399904819572" );
    params.put( "finishedTimeEnd", "1399904819572" );
    params.put( "applicationTypes", "MAPREDUCE" );
    params.put( "applicationTags", "a" );

    getYarnRmApps( ContentType.XML, params );
    getYarnRmApps( ContentType.JSON, params );
    LOG_EXIT();
  }

  private void getYarnRmApps( ContentType contentType, Map<String,String> params ) throws Exception {
    String username = "hdfs";
    String password = "hdfs-password";
    String path = "/v1/cluster/apps/";
    String resource = "/yarn/apps";
    String gatewayPath = driver.getUrl( "RESOURCEMANAGER" ) + path;
    StringBuilder gatewayPathQuery = new StringBuilder();
    if(!driver.isUseGateway()) {
      gatewayPathQuery.append("?user.name=").append(username);
    }
    InetSocketAddress gatewayAddress = driver.gateway.getAddresses()[0];
    String gatewayHostName = gatewayAddress.getHostName();
    String gatewayAddrName = InetAddress.getByName( gatewayHostName ).getHostAddress();

    switch( contentType ) {
    case JSON:
      resource += ".json";
      break;
    case XML:
      resource += ".xml";
      break;
    default:
      break;
    }

    MockRequestMatcher mockRequestMatcher = driver.getMock( "RESOURCEMANAGER" ).expect().method( "GET" )
        .pathInfo( path ).queryParam( "user.name", username );

    if ( params != null ) {
      for (Entry<String, String> param : params.entrySet()) {
        mockRequestMatcher.queryParam( param.getKey(), param.getValue() );
        if (gatewayPathQuery.length() == 0) {
          gatewayPathQuery.append('?');
        } else {
          gatewayPathQuery.append('&');
        }
        gatewayPathQuery.append(param.getKey()).append('=').append(param.getValue());
      }
    }


    mockRequestMatcher.respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( resource ) )
        .contentType(contentType.toString());

    given()
//         .log().all()
        .auth()
        .preemptive()
        .basic( username, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .then()
//         .log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( contentType )
        .body( "apps.app[0].trackingUrl", is(emptyString()) )
        .body( "apps.app[1].trackingUrl",
            anyOf(
                startsWith( "http://" + gatewayHostName + ":" + gatewayAddress.getPort() + "/" ),
                startsWith( "http://" + gatewayAddrName + ":" + gatewayAddress.getPort() + "/" ) ) )
        .body( "apps.app[2].trackingUrl", is(emptyString()))
        .body( "apps.app[0].amContainerLogs", is(emptyString()) )
        .body( "apps.app[1].amContainerLogs", is(emptyString()) )
        .body( "apps.app[0].amHostHttpAddress", is(emptyString()) )
        .body( "apps.app[1].amHostHttpAddress", is(emptyString()) )
        .body( "apps.app[2].id", is( "application_1399541193872_0009" ) )
        .when()
        .get(gatewayPath + gatewayPathQuery);

    driver.assertComplete();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testYarnApplicationLifecycle() throws Exception {
    LOG_ENTER();
    String username = "hdfs";
    String password = "hdfs-password";
    String path = "/v1/cluster/apps/new-application";
    String resource = "yarn/new-application.json";

    driver.getMock("RESOURCEMANAGER")
        .expect()
        .method("POST")
        .respond()
        .status(HttpStatus.SC_OK)
        .content(driver.getResourceBytes(resource))
        .contentType("application/json");
    Response response = given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType("application/json")
        .when().post(driver.getUrl("RESOURCEMANAGER") + path + (driver.isUseGateway() ? "" : "?user.name=" + username));
    assertThat(response.getBody().asString(), Matchers.containsString("application-id"));

    path = "/v1/cluster/apps";
    resource = "yarn/application-submit-request.json";

    driver.getMock("RESOURCEMANAGER")
        .expect()
        .method("POST")
        .content(driver.getResourceBytes(resource))
        .contentType("application/json")
        .respond()
        .status(HttpStatus.SC_OK)
        .contentType("application/json");
    given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body(driver.getResourceBytes(resource))
        .contentType("application/json")
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType("application/json")
        .when().post(driver.getUrl("RESOURCEMANAGER") + path + (driver.isUseGateway() ? "" : "?user.name=" + username));
    driver.assertComplete();

    path = "/v1/cluster/apps/application_1405356982244_0031/state";
    resource = "yarn/application-killing.json";
    driver.getMock("RESOURCEMANAGER")
        .expect()
        .method("PUT")
        .content(driver.getResourceBytes(resource))
        .contentType("application/json")
        .respond()
        .status(HttpStatus.SC_OK)
        .content(driver.getResourceBytes(resource))
        .contentType("application/json");
    response = given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .body(driver.getResourceBytes(resource))
        .contentType("application/json")
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType("application/json")
        .when().put(driver.getUrl("RESOURCEMANAGER") + path + (driver.isUseGateway() ? "" : "?user.name=" + username));
    assertThat(response.getBody().asString(), Matchers.is("{\"state\":\"KILLING\"}"));
    LOG_EXIT();
  }

  @Test
  public void testYarnRmApplication() throws Exception {
    LOG_ENTER();
    getYarnRmApp( ContentType.JSON, true );
    getYarnRmApp( ContentType.XML, true );
    getYarnRmApp( ContentType.JSON, false );
    getYarnRmApp( ContentType.XML, false );
    LOG_EXIT();
  }

  private void getYarnRmApp( ContentType contentType, boolean running ) throws Exception {
    String username = "hdfs";
    String password = "hdfs-password";
    String path = "/v1/cluster/apps/application_1399541193872_0033/";
    String resource;
    if ( running ) {
      resource = "/yarn/app_running";
    } else {
      resource = "/yarn/app_succeeded";
    }

    switch( contentType ) {
    case JSON:
      resource += ".json";
      break;
    case XML:
      resource += ".xml";
      break;
    default:
      break;
    }
    String gatewayPath = driver.getUrl( "RESOURCEMANAGER" ) + path + (driver.isUseGateway() ? "" : "?user.name=" + username);
    InetSocketAddress gatewayAddress = driver.gateway.getAddresses()[0];
    String gatewayHostName = gatewayAddress.getHostName();
    String gatewayAddrName = InetAddress.getByName( gatewayHostName ).getHostAddress();

    VelocityEngine velocity = new VelocityEngine();
    velocity.setProperty( "runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogSystem" );
    velocity.setProperty( RuntimeConstants.RESOURCE_LOADER, "classpath" );
    velocity.setProperty( "classpath.resource.loader.class", ClasspathResourceLoader.class.getName() );
    velocity.init();

    VelocityContext context = new VelocityContext();
    context.put( "proxy_address", driver.getRealUrl( "RESOURCEMANAGER" ) );

    String name = TestUtils.getResourceName( this.getClass(), resource );
    Template template = velocity.getTemplate( name );
    StringWriter sw = new StringWriter();
    template.merge( context, sw );
    String request = sw.toString();

    driver.getMock( "RESOURCEMANAGER" ).expect().method( "GET" )
        .pathInfo( path ).queryParam( "user.name", username ).respond()
        .status( HttpStatus.SC_OK )
        .content( request.getBytes(StandardCharsets.UTF_8) )
        .contentType( contentType.toString() );

    ResponseSpecification response = given()
//         .log().all()
        .auth()
        .preemptive()
        .basic( username, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .then()
//         .log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( contentType );
    if ( running ) {
      response.body(
          "app.trackingUrl",
          anyOf(
              startsWith( "http://" + gatewayHostName + ":" + gatewayAddress.getPort() + "/" ),
              startsWith( "http://" + gatewayAddrName + ":" + gatewayAddress.getPort() + "/" ) ) );
    } else {
      response.body( "app.trackingUrl", is(emptyString()) );
    }

    response.body( "app.amContainerLogs", is(emptyString()) )
        .body( "app.amHostHttpAddress", is(emptyString()) )
        .when()
        .get( gatewayPath );

    driver.assertComplete();
  }

  private void getYarnRmResource( String path, ContentType contentType, String resource )
      throws Exception {
    String username = "hdfs";
    String password = "hdfs-password";

    switch( contentType ) {
    case JSON:
      resource += ".json";
      break;
    case XML:
      resource += ".xml";
      break;
    default:
      break;
    }

    driver.getMock( "RESOURCEMANAGER" ).expect().method( "GET" )
        .pathInfo( path ).queryParam( "user.name", username ).respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( resource ) )
        .contentType( contentType.toString() );

    Response response = given()
//         .log().all()
        .auth()
        .preemptive()
        .basic( username, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .then()
//         .log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( contentType )
        .when()
        .get(
            driver.getUrl( "RESOURCEMANAGER" ) + path
                + (driver.isUseGateway() ? "" : "?user.name=" + username) );

    switch( contentType ) {
    case JSON:
      assertThat( response.getBody().asString(),
          sameJSONAs( driver.getResourceString( resource ) ) );
      break;
    case XML:
      assertThat( the( response.getBody().asString() ),
          isEquivalentTo( the( driver.getResourceString( resource ) ) ) );
      break;
    default:
      break;
    }
    driver.assertComplete();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testYarnRmAppattempts() throws Exception {
    LOG_ENTER();
    getYarnRmAppattempts( ContentType.JSON );
    getYarnRmAppattempts(ContentType.XML);
    LOG_EXIT();
  }

  private void getYarnRmAppattempts( ContentType contentType ) throws Exception {
    String username = "hdfs";
    String password = "hdfs-password";
    String path = "/v1/cluster/apps/application_1399541193872_0018/appattempts/";
    String resource = "/yarn/appattempts";
    String gatewayPath = driver.getUrl( "RESOURCEMANAGER" ) + path + (driver.isUseGateway() ? "" : "?user.name=" + username);

    switch( contentType ) {
    case JSON:
      resource += ".json";
      break;
    case XML:
      resource += ".xml";
      break;
    default:
      break;
    }

    driver.getMock( "RESOURCEMANAGER" ).expect().method( "GET" )
        .pathInfo( path ).queryParam( "user.name", username ).respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( resource ) )
        .contentType( contentType.toString() );

    given()
//         .log().all()
        .auth()
        .preemptive()
        .basic( username, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .then()
//         .log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( contentType )
        .body( "appAttempts.appAttempt[0].nodeHttpAddress", is(emptyString()) )
        .body( "appAttempts.appAttempt[0].nodeId", not( containsString( "localhost:50060" ) ) )
        .body( "appAttempts.appAttempt[0].logsLink", is(emptyString()) )
        .when()
        .get( gatewayPath );

    driver.assertComplete();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testYarnRmNodes() throws Exception {
    LOG_ENTER();
    getYarnRmNodes( ContentType.JSON, null );
    getYarnRmNodes( ContentType.XML, null );

    Map<String, String> params = new HashMap<>();
    params.put( "state", "new,running" );
    params.put( "healthy", "true" );

    getYarnRmNodes( ContentType.JSON, params );
    getYarnRmNodes(ContentType.XML, params);
    LOG_EXIT();
  }

  private void getYarnRmNodes( ContentType contentType, Map<String, String> params ) throws Exception {
    String username = "hdfs";
    String password = "hdfs-password";
    String path = "/v1/cluster/nodes/";
    String nodesResource = "/yarn/nodes";
    String nodeResource = "/yarn/node";
    String nodeId = "localhost:45454";
    String gatewayPath = driver.getUrl( "RESOURCEMANAGER" ) + path;
    StringBuilder gatewayPathQuery = new StringBuilder();
    if(!driver.isUseGateway()) {
      gatewayPathQuery.append("?user.name=").append(username);
    }

    MockRequestMatcher mockRequestMatcher = driver.getMock( "RESOURCEMANAGER" ).expect().method( "GET" )
        .pathInfo( path ).queryParam( "user.name", username );

    if ( params != null ) {
      for (Entry<String, String> param : params.entrySet()) {
        mockRequestMatcher.queryParam( param.getKey(), param.getValue() );
        if (gatewayPathQuery.length() == 0) {
          gatewayPathQuery.append('?');
        } else {
          gatewayPathQuery.append('&');
        }
        gatewayPathQuery.append(param.getKey()).append('=').append(param.getValue());
      }
    }

    mockRequestMatcher.respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( nodesResource + (contentType == ContentType.JSON ? ".json" : ".xml" ) ) )
        .contentType( contentType.toString() );

    String encryptedNodeId = given()
//         .log().all()
        .auth()
        .preemptive()
        .basic( username, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .then()
//         .log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( contentType )
        .body( "nodes.node[0].id", not( containsString( nodeId ) ) )
        .body( "nodes.node[0].nodeHostName", is(emptyString()) )
        .body( "nodes.node[0].nodeHTTPAddress", is(emptyString()) )
        .when()
        .get( gatewayPath + gatewayPathQuery ).getBody().path( "nodes.node[0].id" );

    driver.assertComplete();

    driver.getMock( "RESOURCEMANAGER" ).expect().method( "GET" )
        .pathInfo( path + nodeId ).queryParam( "user.name", username ).respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( nodeResource + (contentType == ContentType.JSON ? ".json" : ".xml" ) ) )
        .contentType( contentType.toString() );

    given()
//         .log().all()
        .auth()
        .preemptive()
        .basic( username, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .then()
//         .log().all()
        .statusCode( HttpStatus.SC_OK )
        .contentType( contentType )
        .body( "node.id", not( containsString( nodeId ) ) )
        .body( "node.nodeHostName", is(emptyString()) )
        .body( "node.nodeHTTPAddress", is(emptyString()) )
        .when()
        .get( gatewayPath + encryptedNodeId );

    driver.assertComplete();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testYarnRmProxy() throws Exception {
    LOG_ENTER();
    String username = "hdfs";
    String password = "hdfs-password";
    String path = "/v1/cluster/apps/application_1399541193872_0033/";
    String gatewayPath = driver.getUrl( "RESOURCEMANAGER" ) + path;

    Map<String, Matcher<?>> matchers = new HashMap<>();

    VelocityEngine velocity = new VelocityEngine();
    velocity.setProperty( "runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogSystem" );
    velocity.setProperty( RuntimeConstants.RESOURCE_LOADER, "classpath" );
    velocity.setProperty( "classpath.resource.loader.class", ClasspathResourceLoader.class.getName() );
    velocity.init();

    VelocityContext context = new VelocityContext();
    context.put( "proxy_address", driver.getRealUrl( "RESOURCEMANAGER" ) );

    String name = TestUtils.getResourceName( this.getClass(), "yarn/app_running.json" );
    Template template = velocity.getTemplate( name );
    StringWriter sw = new StringWriter();
    template.merge( context, sw );
    String request = sw.toString();

    driver.getMock( "RESOURCEMANAGER" ).expect().method( "GET" )
        .pathInfo( path )
        .queryParam( "user.name", username ).respond()
        .status( HttpStatus.SC_OK )
        .content( request.getBytes(StandardCharsets.UTF_8) )
        .contentType( ContentType.JSON.toString() );

    String encryptedTrackingUrl = given()
        // .log().all()
        .auth().preemptive().basic( username, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .then()
        // .log().all()
        .statusCode( HttpStatus.SC_OK ).contentType( ContentType.JSON ).when()
        .get( gatewayPath + ( driver.isUseGateway() ? "" : "?user.name=" + username ) ).getBody()
        .path( "app.trackingUrl" );

    String encryptedQuery = new URI( encryptedTrackingUrl ).getQuery();

    driver.assertComplete();

    // Test that root address of MapReduce Application Master REST API is not accessible through Knox
    // For example, https://{gateway_host}:{gateway_port}/gateway/{cluster}/resourcemanager/proxy/{app_id}/?_={encrypted_application_proxy_location} should return Not Found response
    //  https://{gateway_host}:{gateway_port}/gateway/{cluster}/resourcemanager/proxy/{app_id}/ws/v1/mapreduce/?_={encrypted_application_proxy_location} returns OK
    given()
        // .log().all()
        .auth().preemptive().basic( username, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" ).then()
        // .log().all()
        .statusCode( HttpStatus.SC_NOT_FOUND ).when()
        .get( encryptedTrackingUrl );

    String resource;

    path = "/proxy/application_1399541193872_0033/ws/v1/mapreduce/info";
    resource = "yarn/proxy-mapreduce-info";
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.JSON );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.XML );
    path = "/proxy/application_1399541193872_0033/ws/v1/mapreduce";
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.JSON );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.XML );

    path = "/proxy/application_1399541193872_0033/ws/v1/mapreduce/jobs";
    resource = "yarn/proxy-mapreduce-jobs";
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.JSON );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.XML );

    path = "/proxy/application_1399541193872_0033/ws/v1/mapreduce/jobs/job_1399541193872_0035";
    resource = "yarn/proxy-mapreduce-job";
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.JSON );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.XML );

    path = "/proxy/application_1399541193872_0033/ws/v1/mapreduce/jobs/job_1399541193872_0035/counters";
    resource = "yarn/proxy-mapreduce-job-counters";
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.JSON );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.XML );



//    TODO: Need to understand what we should do with following properties
//    hadoop.proxyuser.HTTP.hosts
//    dfs.namenode.secondary.http-address
//    dfs.namenode.http-address
//    mapreduce.jobhistory.webapp.address
//    mapreduce.jobhistory.webapp.https.address
//    dfs.namenode.https-address
//    mapreduce.job.submithostname
//    yarn.resourcemanager.webapp.address
//    yarn.resourcemanager.hostname
//    mapreduce.jobhistory.address
//    yarn.resourcemanager.webapp.https.address
//    hadoop.proxyuser.oozie.hosts
//    hadoop.proxyuser.hive.hosts
//    dfs.namenode.secondary.https-address
//    hadoop.proxyuser.hcat.hosts
//    hadoop.proxyuser.HTTP.hosts
//    TODO: resolve java.util.regex.PatternSyntaxException: Unmatched closing ')' near index 17   m@\..*EXAMPLE\.COM)s
    path = "/proxy/application_1399541193872_0035/ws/v1/mapreduce/jobs/job_1399541193872_0035/conf";
    resource = "yarn/proxy-mapreduce-job-conf";
//    getYarnRmProxyJobConf( encryptedQuery, path, resource, ContentType.JSON );
//    getYarnRmProxyJobConf( encryptedQuery, path, resource, ContentType.XML );


    path = "/proxy/application_1399541193872_0036/ws/v1/mapreduce/jobs/job_1399541193872_0036/jobattempts";
    resource = "yarn/proxy-mapreduce-job-attempts";
    matchers.clear();
    matchers.put( "jobAttempts.jobAttempt[0].nodeHttpAddress", is(emptyString()) );
    matchers.put( "jobAttempts.jobAttempt[0].nodeId", not( containsString( "host.yarn.com:45454" ) ) );
    matchers.put( "jobAttempts.jobAttempt[0].logsLink", is(emptyString()) );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.JSON, matchers );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.XML, matchers );

    path = "/proxy/application_1399541193872_0036/ws/v1/mapreduce/jobs/job_1399541193872_0036/tasks";
    resource = "yarn/proxy-mapreduce-tasks";
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.JSON );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.XML );

    path = "/proxy/application_1399541193872_0036/ws/v1/mapreduce/jobs/job_1399541193872_0036/tasks/task_1399541193872_0036_r_00";
    resource = "yarn/proxy-mapreduce-task";
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.JSON );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.XML );

    path = "/proxy/application_1399541193872_0036/ws/v1/mapreduce/jobs/job_1399541193872_0036/tasks/task_1399541193872_0036_r_00/counters";
    resource = "yarn/proxy-mapreduce-task-counters";
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.JSON );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.XML );


    path = "/proxy/application_1399541193872_0036/ws/v1/mapreduce/jobs/job_1399541193872_0036/tasks/task_1399541193872_0036_r_00/attempts";
    resource = "yarn/proxy-mapreduce-task-attempts";
    matchers.clear();
    matchers.put( "taskAttempts.taskAttempt[0].nodeHttpAddress", is(emptyString()) );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.JSON, matchers );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.XML, matchers );

    path = "/proxy/application_1399541193872_0036/ws/v1/mapreduce/jobs/job_1399541193872_0036/tasks/task_1399541193872_0036_r_00/attempts/attempt_1399541193872_0036_r_000000_0";
    resource = "yarn/proxy-mapreduce-task-attempt";
    matchers.clear();
    matchers.put( "taskAttempt.nodeHttpAddress", is(emptyString()) );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.JSON, matchers );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.XML, matchers );

    path = "/proxy/application_1399541193872_0036/ws/v1/mapreduce/jobs/job_1399541193872_0036/tasks/task_1399541193872_0036_r_00/attempts/attempt_1399541193872_0036_r_000000_0/counters";
    resource = "yarn/proxy-mapreduce-task-attempt-counters";
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.JSON );
    getYarnRmProxyData( encryptedQuery, path, resource, ContentType.XML );
    LOG_EXIT();
  }

  private void getYarnRmProxyData( String encryptedQuery, String path, String resource, ContentType contentType ) throws Exception {
    getYarnRmProxyData( encryptedQuery, path, resource, contentType, null );
  }

  private void getYarnRmProxyData( String encryptedQuery, String path, String resource, ContentType contentType, Map<String, Matcher<?>> contentMatchers ) throws Exception {

    String username = "hdfs";
    String password = "hdfs-password";
    String gatewayPath = driver.getUrl( "RESOURCEMANAGER" ) + path + "?" + encryptedQuery + ( driver.isUseGateway() ? "" : "&user.name=" + username );

    switch( contentType ) {
    case JSON:
      resource += ".json";
      break;
    case XML:
      resource += ".xml";
      break;
    default:
      break;
    }

    driver.getMock( "RESOURCEMANAGER" ).expect().method( "GET" )
    .pathInfo( path )
    .queryParam( "user.name", username ).respond()
    .status( HttpStatus.SC_OK )
    .content( driver.getResourceBytes( resource ) )
    .contentType( contentType.toString() );

    ResponseSpecification responseSpecification = given()
//     .log().all()
    .auth().preemptive().basic( username, password )
    .header( "X-XSRF-Header", "jksdhfkhdsf" )
    .then()
//     .log().all()
    .statusCode( HttpStatus.SC_OK ).contentType( contentType );

    if ( contentMatchers != null ) {
      for ( Entry<String, Matcher<?>> matcher : contentMatchers.entrySet() ) {
        responseSpecification.body( matcher.getKey(), matcher.getValue() );
      }
    }

    Response response = responseSpecification.when().get( gatewayPath );

    if ( contentMatchers == null || contentMatchers.isEmpty() ) {
      switch( contentType ) {
      case JSON:
        assertThat( response.getBody().asString(),
            sameJSONAs( driver.getResourceString( resource ) ) );
        break;
      case XML:
        assertThat( the( response.getBody().asString() ),
            isEquivalentTo(the(driver.getResourceString(resource))) );
        break;
      default:
        break;
      }
    }

    driver.assertComplete();
  }

  @SuppressWarnings("unused")
  private void getYarnRmProxyJobConf( String encryptedQuery, String path, String resource, ContentType contentType ) throws Exception {

    String username = "hdfs";
    String password = "hdfs-password";
    String gatewayPath = driver.getUrl( "RESOURCEMANAGER" ) + path + "?" + encryptedQuery + ( driver.isUseGateway() ? "" : "&user.name=" + username );

    switch( contentType ) {
    case JSON:
      resource += ".json";
      break;
    case XML:
      resource += ".xml";
      break;
    default:
      break;
    }

    driver.getMock( "RESOURCEMANAGER" ).expect().method( "GET" )
    .pathInfo( path )
    .queryParam( "user.name", username ).respond()
    .status( HttpStatus.SC_OK )
    .content( driver.getResourceBytes( resource ) )
    .contentType( contentType.toString() );

    Response response = given()
//     .log().all()
    .auth().preemptive().basic(username, password)
    .header("X-XSRF-Header", "jksdhfkhdsf")
    .then()
//     .log().all()
    .statusCode(HttpStatus.SC_OK).contentType(contentType).when()
    .get(gatewayPath);

    assertThat( response.body().asString(), not( containsString( "host.yarn.com" ) ) );

    driver.assertComplete();
  }

  private File findFile( File dir, String pattern ) {
    File file = null;
    FileFilter filter = new WildcardFileFilter( pattern );
    File[] files = dir.listFiles(filter);
    if( files != null && files.length > 0 ) {
      file = files[0];
    }
    return file;
  }

  private String findHadoopExamplesJar() throws IOException {
    String pattern = "hadoop-examples-*.jar";
    File dir = new File( System.getProperty( "user.dir" ), "hadoop-examples/target" );
    File file = findFile(dir, pattern);
    if( file == null || !file.exists() ) {
      file = findFile( new File( System.getProperty( "user.dir" ), "../hadoop-examples/target" ), pattern );
    }
    if( file == null ) {
      throw new FileNotFoundException( pattern );
    }
    return file.toURI().toString();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testFalconAdmin() throws Exception {
    LOG_ENTER();
    String resourceName = "falcon/version";
    String path = "/api/admin/version";
    testGetFalconResource(resourceName, path, ContentType.XML);
    testGetFalconResource(resourceName, path, ContentType.JSON);

    resourceName = "falcon/config-runtime";
    path = "/api/admin/config/runtime";
    testGetFalconResource(resourceName, path, ContentType.XML);
    testGetFalconResource(resourceName, path, ContentType.JSON);

    resourceName = "falcon/config-deploy";
    path = "/api/admin/config/deploy";
    testGetFalconResource(resourceName, path, ContentType.XML);
    testGetFalconResource(resourceName, path, ContentType.JSON);

    resourceName = "falcon/config-startup";
    path = "/api/admin/config/startup";
    testGetFalconResource(resourceName, path, ContentType.XML);
    testGetFalconResource(resourceName, path, ContentType.JSON);


    String username = "hdfs";
    String password = "hdfs-password";
    resourceName = "falcon/stack.txt";
    path = "/api/admin/config/stack";
    String gatewayPath = driver.getUrl( "FALCON" ) + path;

    driver.getMock("FALCON")
        .expect()
        .method("GET")
        .pathInfo(path)
        .queryParam("user.name", username)
        .respond()
        .status(HttpStatus.SC_OK)
        .content(driver.getResourceBytes(resourceName))
        .contentType(ContentType.TEXT.toString());

    Response response = given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .then()
        .statusCode(HttpStatus.SC_OK)
        .when().get( gatewayPath );

    Assert.assertEquals(response.getBody().asString(), driver.getResourceString( resourceName ) );
    driver.assertComplete();
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testFalconEntities() throws Exception {
    LOG_ENTER();
    String resourceName = "falcon/entity-status-process";
    String path = "/api/entities/status/process/cleanseEmailProcess";
    testGetFalconResource(resourceName, path, ContentType.XML);

    resourceName = "falcon/oregonCluster";
    path = "/api/entities/definition/cluster/primaryCluster";
    testGetFalconResource(resourceName, path, ContentType.XML);

    resourceName = "falcon/entity-list-cluster";
    path = "/api/entities/list/cluster";
    testGetFalconResource(resourceName, path, ContentType.XML);

//    resourceName = "falcon/entity-summary-feed";
//    path = "/api/entities/summary/feed?cluster=primaryCluster&filterBy=STATUS:RUNNING&fields=status&tags=consumer=consumer@xyz.com&orderBy=name&offset=0&numResults=1&numInstances=2";
//    testGetFalconResource(resourceName, path, ContentType.XML);
//    testGetFalconResource(resourceName, path, ContentType.JSON);

    resourceName = "falcon/entity-dependency-process";
    path = "/api/entities/dependencies/process/cleanseEmailProcess";
    testGetFalconResource(resourceName, path, ContentType.XML);

    String postResource = "falcon/oregonCluster.xml";
    String responseResource = "falcon/entity-validate-cluster.xml";
    path = "/api/entities/validate/cluster";
    testPostFalconResource(postResource, responseResource, path, ContentType.XML);

    postResource = "falcon/rawEmailFeed.xml";
    responseResource = "falcon/entity-submit-feed.json";
    path = "/api/entities/submit/feed";
    testPostFalconResource(postResource, responseResource, path, ContentType.JSON);

    postResource = "falcon/rawEmailFeed.xml";
    responseResource = "falcon/entity-update-feed.xml";
    path = "/api/entities/update/feed";
    testPostFalconResource(postResource, responseResource, path, ContentType.XML);

    postResource = "falcon/cleanseEmailProcess.xml";
    responseResource = "falcon/entity-submit-schedule-process.json";
    path = "/api/entities/submitAndSchedule/process";
    testPostFalconResource(postResource, responseResource, path, ContentType.JSON);

    postResource = null;
    responseResource = "falcon/entity-schedule-feed.xml";
    path = "/api/entities/schedule/feed/rawEmailFeed";
    testPostFalconResource(postResource, responseResource, path, ContentType.XML);

    responseResource = "falcon/entity-resume-feed.xml";
    path = "/api/entities/resume/feed/rawEmailFeed";
    testPostFalconResource(postResource, responseResource, path, ContentType.XML);
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testFalconFeedAndProcess() throws Exception {
    LOG_ENTER();
    String resourceName = "falcon/instance-running-process";
    String path = "/api/instance/running/process/cleanseEmailProcess";
    testGetFalconResource(resourceName, path, ContentType.JSON);

    resourceName = "falcon/instance-params-process";
    path = "/api/instance/params/process/cleanseEmailProcess";
    testGetFalconResource(resourceName, path, ContentType.JSON);

    resourceName = "falcon/instance-status-process";
    path = "/api/instance/status/process/cleanseEmailProcess";
    testGetFalconResource(resourceName, path, ContentType.JSON);
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testFalconMetadataLineage() throws Exception {
    LOG_ENTER();
    String resourceName = "falcon/metadata-lineage-vertices-all";
    String path = "/api/metadata/lineage/vertices/all";
    testGetFalconResource(resourceName, path, ContentType.JSON);

    resourceName = "falcon/metadata-lineage-vertices-id";
    path = "/api/metadata/lineage/vertices/76";
    testGetFalconResource(resourceName, path, ContentType.JSON);

    resourceName = "falcon/metadata-lineage-vertices-direction";
    path = "/api/metadata/lineage/vertices/76/out";
    testGetFalconResource(resourceName, path, ContentType.JSON);

    resourceName = "falcon/metadata-lineage-edges-all";
    path = "/api/metadata/lineage/edges/all";
    testGetFalconResource(resourceName, path, ContentType.JSON);

    resourceName = "falcon/metadata-lineage-edges-id";
    path = "/api/metadata/lineage/edges/Q2v-4-4m";
    testGetFalconResource(resourceName, path, ContentType.JSON);

    String username = "hdfs";
    String password = "hdfs-password";
    resourceName = "falcon/metadata-lineage-vertices-key.json";
    path = "/api/metadata/lineage/vertices";
    String gatewayPath = driver.getUrl( "FALCON" ) + path + "?key=name&value=rawEmailIngestProcess";

    driver.getMock("FALCON")
        .expect()
        .method("GET")
        .pathInfo(path)
        .queryParam("user.name", username)
        .queryParam("key", "name")
        .queryParam("value", "rawEmailIngestProcess")
        .respond()
        .status(HttpStatus.SC_OK)
        .content(driver.getResourceBytes(resourceName))
        .contentType(ContentType.JSON.toString());

    Response response = given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .then()
        .statusCode(HttpStatus.SC_OK)
        .when().get( gatewayPath );

    assertThat(response.getBody().asString(),
        sameJSONAs(driver.getResourceString(resourceName)));
    driver.assertComplete();
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testFalconMetadataDiscovery() throws Exception {
    LOG_ENTER();
    String resourceName = "falcon/metadata-disc-process-entity";
    String path = "/api/metadata/discovery/process_entity/list";
    testGetFalconResource(resourceName, path, ContentType.JSON);

    resourceName = "falcon/metadata-disc-cluster-entity";
    path = "/api/metadata/discovery/cluster_entity/list";
    testGetFalconResource(resourceName, path, ContentType.JSON);

    resourceName = "falcon/metadata-disc-cluster-relations";
    path = "/api/metadata/discovery/cluster_entity/primaryCluster/relations";
    testGetFalconResource(resourceName, path, ContentType.JSON);
    LOG_EXIT();
  }

  private void testGetFalconResource(String resourceName, String path, ContentType contentType) throws IOException {
    String username = "hdfs";
    String password = "hdfs-password";
    String gatewayPath = driver.getUrl( "FALCON" ) + path;

    switch( contentType ) {
      case JSON:
        resourceName += ".json";
        break;
      case XML:
        resourceName += ".xml";
        break;
      default:
        break;
    }

    driver.getMock("FALCON")
        .expect()
        .method("GET")
        .pathInfo(path)
        .queryParam("user.name", username)
        .header("Accept", contentType.toString())
        .respond()
        .status(HttpStatus.SC_OK)
        .content(driver.getResourceBytes(resourceName))
        .contentType(contentType.toString());

    Response response = given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .header("Accept", contentType.toString())
        .then()
//        .log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType( contentType )
        .when().get( gatewayPath );

    switch( contentType ) {
      case JSON:
        assertThat( response.getBody().asString(),
            sameJSONAs( driver.getResourceString( resourceName ) ) );
        break;
      case XML:
        assertThat( the( response.getBody().asString() ),
                isEquivalentTo( the( driver.getResourceString( resourceName ) ) ) );
        break;
      default:
        break;
    }
    driver.assertComplete();
  }

  private void testPostFalconResource(String postResource, String responseResource, String path, ContentType contentType) throws IOException {
    String username = "hdfs";
    String password = "hdfs-password";
    String gatewayPath = driver.getUrl( "FALCON" ) + path;
    Response response;

    if (postResource != null) {
      driver.getMock("FALCON")
          .expect()
          .method("POST")
          .content(driver.getResourceBytes(postResource))
          .header("Accept", contentType.toString())
          .pathInfo(path)
          .queryParam("user.name", username)
          .respond()
          .status(HttpStatus.SC_OK)
          .content(driver.getResourceBytes(responseResource))
          .contentType(contentType.toString());

     response = given()
          .auth().preemptive().basic(username, password)
          .header("X-XSRF-Header", "jksdhfkhdsf")
          .header("Accept", contentType.toString())
          .body(driver.getResourceBytes(postResource))
          .then()
          .statusCode(HttpStatus.SC_OK)
          .contentType(contentType.toString())
          .when().post(gatewayPath);
    } else {
      driver.getMock("FALCON")
          .expect()
          .method("POST")
          .header("Accept", contentType.toString())
          .pathInfo(path)
          .queryParam("user.name", username)
          .respond()
          .status(HttpStatus.SC_OK)
          .content(driver.getResourceBytes(responseResource))
          .contentType(contentType.toString());

      response = given()
          .auth().preemptive().basic(username, password)
          .header("X-XSRF-Header", "jksdhfkhdsf")
          .header("Accept", contentType.toString())
          .then()
          .statusCode(HttpStatus.SC_OK)
          .contentType(contentType.toString())
          .when().post(gatewayPath);
    }

    switch( contentType ) {
      case JSON:
        assertThat( response.getBody().asString(),
            sameJSONAs( driver.getResourceString( responseResource ) ) );
        break;
      case XML:
        assertThat( the( response.getBody().asString() ),
                isEquivalentTo( the( driver.getResourceString( responseResource ) ) ) );
        break;
      default:
        break;
    }
    driver.assertComplete();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testStormUiApi() throws Exception {
    LOG_ENTER();
    String resourceName = "storm/cluster-configuration.json";
    String path = "/api/v1/cluster/configuration";
    testGetStormResource(resourceName, path);

    resourceName = "storm/cluster-summary.json";
    path = "/api/v1/cluster/summary";
    testGetStormResource(resourceName, path);

    resourceName = "storm/supervisor-summary.json";
    path = "/api/v1/supervisor/summary";
    testGetStormResource(resourceName, path);

    resourceName = "storm/topology-summary.json";
    path = "/api/v1/topology/summary";
    testGetStormResource(resourceName, path);

    String username = "hdfs";
    String password = "hdfs-password";

    InetSocketAddress gatewayAddress = driver.gateway.getAddresses()[0];
    String gatewayHostName = gatewayAddress.getHostName();
    String gatewayAddrName = InetAddress.getByName( gatewayHostName ).getHostAddress();

    resourceName = "storm/topology-id.json";
    path = "/api/v1/topology/WordCount-1-1424792039";
    String gatewayPath = driver.getUrl( "STORM" ) + path;
    driver.getMock("STORM")
        .expect()
        .method("GET")
        .pathInfo(path)
        .queryParam("user.name", username)
        .respond()
        .status(HttpStatus.SC_OK)
        .content(driver.getResourceBytes(resourceName))
        .contentType(ContentType.JSON.toString());

    Response response = given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .header("Accept", ContentType.JSON.toString())
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType( ContentType.JSON.toString() )
        .when().get( gatewayPath );

    String link = response.getBody().jsonPath().getString("spouts[0].errorWorkerLogLink");
    assertThat(link, anyOf(
        startsWith("http://" + gatewayHostName + ":" + gatewayAddress.getPort() + "/"),
        startsWith("http://" + gatewayAddrName + ":" + gatewayAddress.getPort() + "/")));
    assertThat( link, containsString("/storm/logviewer") );

    driver.assertComplete();

    resourceName = "storm/topology-component-id.json";
    path = "/api/v1/topology/WordCount-1-1424792039/component/spout";
    gatewayPath = driver.getUrl( "STORM" ) + path;
    driver.getMock("STORM")
        .expect()
        .method("GET")
        .pathInfo(path)
        .queryParam("user.name", username)
        .respond()
        .status(HttpStatus.SC_OK)
        .content(driver.getResourceBytes(resourceName))
        .contentType(ContentType.JSON.toString());

    response = given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .header("Accept", ContentType.JSON.toString())
        .then()
//        .log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType( ContentType.JSON.toString() )
        .when().get( gatewayPath );


    link = response.getBody().jsonPath().getString("executorStats[0].workerLogLink");
    assertThat(link, anyOf(
        startsWith("http://" + gatewayHostName + ":" + gatewayAddress.getPort() + "/"),
        startsWith("http://" + gatewayAddrName + ":" + gatewayAddress.getPort() + "/")));
    assertThat( link, containsString("/storm/logviewer") );

    driver.assertComplete();

    path = "/api/v1/topology/WordCount-1-1424792039/activate";
    testPostStormResource(path);

    path = "/api/v1/topology/WordCount-1-1424792039/deactivate";
    testPostStormResource(path);

    path = "/api/v1/topology/WordCount-1-1424792039/rebalance/20";
    testPostStormResource(path);

    path = "/api/v1/topology/WordCount-1-1424792039/kill/20";
    testPostStormResource(path);

    LOG_EXIT();
  }

  private void testGetStormResource(String resourceName, String path) throws IOException {
    String username = "hdfs";
    String password = "hdfs-password";
    String gatewayPath = driver.getUrl( "STORM" ) + path;

    driver.getMock("STORM")
        .expect()
        .method("GET")
        .pathInfo(path)
        .queryParam("user.name", username)
        .respond()
        .status(HttpStatus.SC_OK)
        .content(driver.getResourceBytes(resourceName))
        .contentType(ContentType.JSON.toString());

    Response response = given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .header("Accept", ContentType.JSON.toString())
        .then()
//        .log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType( ContentType.JSON.toString() )
        .when().get( gatewayPath );

    assertThat(response.getBody().asString(),
        sameJSONAs(driver.getResourceString(resourceName)));
    driver.assertComplete();
  }

  private void testPostStormResource(String path) throws IOException {
    String username = "hdfs";
    String password = "hdfs-password";
    String gatewayPath = driver.getUrl( "STORM" ) + path;

    driver.getMock("STORM")
        .expect()
        .method("POST")
        .pathInfo(path)
        .queryParam("user.name", username)
        .respond()
        .status(HttpStatus.SC_MOVED_TEMPORARILY)
        .contentType(ContentType.JSON.toString());

    given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .header("X-CSRF-Token", "H/8xIWCYQo4ZDWLvV9k0FAkjD0omWI8beVTp2mEPRxCbJmWBTYhRMhIV9LGIY3E51OAj+s6T7eQChpGJ")
        .header("Accept", ContentType.JSON.toString())
        .then()
        .statusCode(HttpStatus.SC_MOVED_TEMPORARILY)
        .contentType( ContentType.JSON.toString() )
        .when().post( gatewayPath );

    driver.assertComplete();
  }


  @Test
  public void testXForwardHeadersPopulate() throws Exception {
    LOG_ENTER();
    String username = "hdfs";
    String password = "hdfs-password";

    String resourceName = "storm/topology-id.json";
    String path = "/api/v1/topology/WordCount-1-1424792039";
    String gatewayPath = driver.getUrl( "STORM" ) + path;
    InetSocketAddress gatewayAddress = driver.gateway.getAddresses()[0];
    int gatewayPort = gatewayAddress.getPort();
    String gatewayHostName = gatewayAddress.getHostName();
    String gatewayAddrName = InetAddress.getByName( gatewayHostName ).getHostAddress();

    driver.getMock("STORM")
        .expect()
        .method("GET")
        .header("X-Forwarded-Host", Matchers.is(oneOf(gatewayHostName + ":" + gatewayPort, gatewayAddrName + ":" + gatewayPort)))
        .header("X-Forwarded-Proto", "http")
        .header("X-Forwarded-Port", Integer.toString(gatewayPort))
        .header("X-Forwarded-Context", "/gateway/cluster")
        .header( "X-Forwarded-Server", Matchers.is(oneOf( gatewayHostName, gatewayAddrName ) ))
        .header( "X-Forwarded-For", Matchers.is(oneOf( gatewayHostName, gatewayAddrName ) ))
        .pathInfo( path )
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( resourceName ) )
        .contentType( ContentType.JSON.toString() );

    Response response = given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .header("Accept", ContentType.JSON.toString())
        .then()
        //.log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType( ContentType.JSON.toString() )
        .when().get( gatewayPath );

    String link = response.getBody().jsonPath().getString("spouts[0].errorWorkerLogLink");
    assertThat(link, anyOf(
        startsWith("http://" + gatewayHostName + ":" + gatewayPort + "/"),
        startsWith("http://" + gatewayAddrName + ":" + gatewayPort + "/")));
    assertThat( link, containsString("/storm/logviewer") );
    driver.assertComplete();
    LOG_EXIT();
  }


  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testXForwardHeadersRewrite() throws Exception {
    LOG_ENTER();
    String username = "hdfs";
    String password = "hdfs-password";
    String host = "whatsinaname";
    String port = "8889";
    String scheme = "https";

    //Test rewriting of body with X-Forwarded headers (using storm)
    String resourceName = "storm/topology-id.json";
    String path = "/api/v1/topology/WordCount-1-1424792039";
    String gatewayPath = driver.getUrl( "STORM" ) + path;
    driver.getMock("STORM")
        .expect()
        .method("GET")
        .header("X-Forwarded-Host", host)
        .header("X-Forwarded-Proto", scheme)
        .header("X-Forwarded-Port", port)
        .header("X-Forwarded-Context", "/gateway/cluster")
        // Since KNOX-2467 enables Jetty's X-Forwarded handling
        // the following is no longer true and while possibly accurate
        // in terms of the most recent proxy hostname, it should
        // represent the host of the X-Forwarded-Host and does not here
        //.header("X-Forwarded-Server", Matchers.is(oneOf( gatewayHostName, gatewayAddrName ) ))
        .header("X-Forwarded-Server", host)
        .header("X-Forwarded-For", Matchers.containsString("what, boo"))
        .pathInfo(path)
        .queryParam("user.name", username)
        .respond()
        .status(HttpStatus.SC_OK)
        .content(driver.getResourceBytes(resourceName))
        .contentType(ContentType.JSON.toString());

    Response response = given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .header("Accept", ContentType.JSON.toString())
        .header("X-Forwarded-Host", host)
        .header("X-Forwarded-Proto", scheme)
        .header("X-Forwarded-Port", port)
        .header("X-Forwarded-Server", "what")
        .header("X-Forwarded-For", "what, boo")
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType(ContentType.JSON.toString())
        .when().get(gatewayPath);

    String link = response.getBody().jsonPath().getString("spouts[0].errorWorkerLogLink");
    assertThat(link, is(
        startsWith(scheme + "://" + host + ":" + port + "/")));
    assertThat( link, containsString("/storm/logviewer") );

    driver.assertComplete();

    resourceName = "storm/topology-component-id.json";
    path = "/api/v1/topology/WordCount-1-1424792039/component/spout";
    gatewayPath = driver.getUrl( "STORM" ) + path;
    driver.getMock("STORM")
        .expect()
        .method("GET")
        .header("X-Forwarded-Host", host)
        .header("X-Forwarded-Proto", scheme)
        .header("X-Forwarded-Port", port)
        .header("X-Forwarded-Context", "/gateway/cluster")
        .header("X-Forwarded-Server", host)
        .header("X-Forwarded-For", Matchers.containsString("what, boo"))
        .pathInfo(path)
        .queryParam("user.name", username)
        .respond()
        .status(HttpStatus.SC_OK)
        .content(driver.getResourceBytes(resourceName))
        .contentType(ContentType.JSON.toString());

    response = given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .header("Accept", ContentType.JSON.toString())
        .header("X-Forwarded-Host", host)
        .header("X-Forwarded-Proto", scheme)
        .header("X-Forwarded-Port", port)
        .header("X-Forwarded-Server", host)
        .header("X-Forwarded-For", "what, boo")
        .then()
//        .log().all()
        .statusCode(HttpStatus.SC_OK)
        .contentType( ContentType.JSON.toString() )
        .when().get( gatewayPath );


    link = response.getBody().jsonPath().getString("executorStats[0].workerLogLink");
    assertThat(link, is(
        startsWith(scheme + "://" + host + ":" + port + "/")));
    assertThat( link, containsString("/storm/logviewer") );
    driver.assertComplete();

    //Test header rewrite using webhdfs
    String root = "/tmp/GatewayBasicFuncTest/testBasicOutboundHeaderUseCase";

    driver.getMock( "WEBHDFS" )
        .expect()
        .method( "PUT" )
        .pathInfo("/v1" + root + "/dir/file")
        .header("Host", driver.getRealAddr("WEBHDFS"))
        .header("X-Forwarded-Host", host)
        .header("X-Forwarded-Proto", scheme)
        .header("X-Forwarded-Port", port)
        .header("X-Forwarded-Context", "/gateway/cluster")
        .header("X-Forwarded-Server", host)
        .header("X-Forwarded-For", Matchers.containsString("what, boo"))
        .queryParam("op", "CREATE")
        .queryParam( "user.name", username )
        .respond()
        .status( HttpStatus.SC_TEMPORARY_REDIRECT )
        .header("Location", driver.getRealUrl("DATANODE") + "/v1" + root + "/dir/file?op=CREATE&user.name=hdfs");
    response = given()
        //.log().all()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .header("X-Forwarded-Host", host)
        .header("X-Forwarded-Proto", scheme)
        .header("X-Forwarded-Port", port)
        .header("X-Forwarded-Server", host)
        .header("X-Forwarded-For", "what, boo")
        .queryParam( "op", "CREATE" )
        .then()
            //.log().ifError()
        .statusCode( HttpStatus.SC_TEMPORARY_REDIRECT )
        .when().put( driver.getUrl("WEBHDFS") + "/v1" + root + "/dir/file" );
    String location = response.getHeader( "Location" );
    log.debug( "Redirect location: " + response.getHeader( "Location" ) );
    if( driver.isUseGateway() ) {
      assertThat( location, is(startsWith(scheme + "://" + host + ":" + port + "/")));
      assertThat( location, containsString( "?_=" ) );
    }
    assertThat(location, not(containsString("host=")));
    assertThat(location, not(containsString("port=")));
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testServiceTestAPI() throws Exception {
    LOG_ENTER();

    String user = "kminder";
    String password = "kminder-password";

    String queryString = "?username=" + user + "&password=" + password;

    String clusterUrl = driver.getClusterUrl();
    String testUrl = clusterUrl + "/service-test";

//    XML Response
    setupResources();
    given()
        .header(new Header("Accept", MediaType.APPLICATION_XML))
        .then()
        .contentType(MediaType.APPLICATION_XML)
        .statusCode(HttpStatus.SC_OK)
        .body(not(containsString("<httpCode>401")))
        .body(not(containsString("<httpCode>404")))
        .body(not(containsString("<httpCode>403")))
        .body(containsString("<httpCode>200"))
        .when()
        .get(testUrl + queryString);
//        .prettyPrint();
      driver.assertComplete();

//    JSON Response
      setupResources();
    given()
        .header(new Header("Accept", MediaType.APPLICATION_JSON))
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType(MediaType.APPLICATION_JSON)
        .body(not(containsString("\"httpCode\" : 401")))
        .body(not(containsString("\"httpCode\" : 404")))
        .body(not(containsString("\"httpCode\" : 403")))
        .body(containsString("\"httpCode\" : 200"))
        .when()
        .get(testUrl + queryString);
//        .prettyPrint();
    driver.assertComplete();

//    Test authorization with a header instead
    setupResources();
    given()
        .header(new Header("Accept", MediaType.APPLICATION_JSON))
        .auth().preemptive().basic("kminder", "kminder-password")
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType(MediaType.APPLICATION_JSON)
        .body(not(containsString("\"httpCode\" : 401")))
        .body(not(containsString("\"httpCode\" : 404")))
        .body(not(containsString("\"httpCode\" : 403")))
        .body(containsString("\"httpCode\" : 200"))
        .when()
        .get(testUrl);
//        .prettyPrint();
    driver.assertComplete();

//    Authorize as a different (invalid) user
    setupResources();
    given()
        .header(new Header("Accept", MediaType.APPLICATION_JSON))
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType(MediaType.APPLICATION_JSON)
        .body(not(containsString("\"httpCode\" : 200")))
        .body(not(containsString("\"httpCode\" : 404")))
        .body(not(containsString("\"httpCode\" : 403")))
        .body(containsString("\"httpCode\" : 401"))
        .when()
        .get(testUrl + "?username=bad-user&password=bad-password");
//        .prettyPrint();
    driver.assertNotComplete("WEBHDFS");
    driver.assertNotComplete("OOZIE");
    driver.assertNotComplete("RESOURCEMANAGER");
    driver.assertNotComplete("WEBHCAT");
    driver.assertNotComplete("STORM");
    driver.assertNotComplete("WEBHBASE");
    driver.assertNotComplete("FALCON");

      //    Authorize as a different (valid) user
    setupResources();
    given()
        .header(new Header("Accept", MediaType.APPLICATION_JSON))
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType(MediaType.APPLICATION_JSON)
        .body(not(containsString("\"httpCode\" : 401")))
        .body(not(containsString("\"httpCode\" : 404")))
        .body(not(containsString("\"httpCode\" : 403")))
        .when()
        .get(testUrl + "?username=mapred&password=mapred-password");
//        .prettyPrint();
    driver.assertComplete();
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.LONG_TIMEOUT )
  public void testCLIServiceTest() throws Exception {
    LOG_ENTER();

    setupResources();

    InetSocketAddress gatewayAddress = driver.gateway.getAddresses()[0];
    String gatewayPort = Integer.toString(gatewayAddress.getPort());

    //    Now let's make sure we can run this same command from the CLI.
    PrintStream out = System.out;
    final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outContent, false, StandardCharsets.UTF_8.name()));

    String[] args = {"service-test", "--master", "knox", "--cluster", driver.clusterName, "--hostname", gatewayAddress.getHostName(),
        "--port", gatewayPort, "--u", "kminder","--p", "kminder-password" };
    KnoxCLI cli = new KnoxCLI();
    cli.run(args);

    Assume.assumeTrue("Gateway port should not contain status code",
        !gatewayPort.contains("404") && !gatewayPort.contains("403"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), not(containsString("\"httpCode\": 401")));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), not(containsString("404")));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), not(containsString("403")));
    outContent.reset();

    setupResources();
    String[] args2 = {"service-test", "--master", "knox", "--cluster", driver.clusterName, "--hostname", gatewayAddress.getHostName(),
        "--port", gatewayPort};

    cli = new KnoxCLI();
    cli.run(args2);
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), (containsString("Username and/or password not supplied. Expect HTTP 401 Unauthorized responses.")));
    outContent.reset();


    String[] args3 = {"service-test", "--master", "knox", "--cluster", driver.clusterName, "--hostname", "bad-host",
        "--port", "0", "--u", "guest", "--p", "guest-password" };

    cli = new KnoxCLI();
    cli.run(args3);
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()).toLowerCase(Locale.ROOT),
        either(containsString("nodename nor servname provided")).or(containsString("name or service not known"))
            .or(containsString("//bad-host:0/")));
    outContent.reset();

    String[] args4 = {"service-test", "--master", "knox", "--cluster", driver.clusterName, "--hostname", gatewayAddress.getHostName(),
        "--port", "543", "--u", "mapred", "--p", "mapred-password" };

    cli = new KnoxCLI();
    cli.run(args4);
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("failed: Connection refused"));
    outContent.reset();


    String[] args5 = {"service-test", "--master", "knox", "--hostname", gatewayAddress.getHostName(),
        "--port", "543", "--u", "mapred", "--p", "mapred-password" };

    cli = new KnoxCLI();
    cli.run(args5);
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("--cluster argument is required"));
    outContent.reset();

//    Reset the out content
    System.setOut(out);
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testSolrRESTAPI() throws Exception {
    LOG_ENTER();
    String resourceName = "solr/query_response.xml";
    String username = "hdfs";
    String password = "hdfs-password";

    String gatewayPath = driver.getUrl( "SOLR" ) + "/gettingstarted/select?q=author_s:William+Shakespeare";
    driver.getMock("SOLR")
        .expect()
        .method("GET")
        .pathInfo("/gettingstarted/select")
        .queryParam("q", "author_s:William+Shakespeare")
        .respond()
        .status(HttpStatus.SC_OK)
        .content(driver.getResourceBytes(resourceName))
        .contentType(ContentType.XML.toString());

    Response response = given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .header("Accept", ContentType.XML.toString())
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType( ContentType.XML.toString() )
        .when().get( gatewayPath );

    assertTrue(response.getBody().asString().contains("The Merchant of Venice"));

    driver.assertComplete();
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testKafka() throws IOException {
    LOG_ENTER();
    String username = "hdfs";
    String password = "hdfs-password";

    driver.getMock( "KAFKA" )
        .expect()
        .method( "GET" )
        .pathInfo( "/topics" )
        .respond()
        .status( HttpStatus.SC_OK );

    given()
        .auth().preemptive().basic( username, password )
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam( "op", "GET" )
        .then()
        .statusCode( HttpStatus.SC_OK )
        .when().get( driver.getUrl( "KAFKA" ) + "/topics" );

    driver.assertComplete();
    LOG_EXIT();
  }

  private void setupResource(String serviceRole, String path){
    driver.getMock(serviceRole)
        .expect().method("GET")
        .pathInfo(path)
        .respond()
        .status(HttpStatus.SC_OK)
        .contentType("application/json")
        .characterEncoding(StandardCharsets.UTF_8.name());
//            .content(driver.getResourceBytes(classLoaderResource + "." + type.toString().toLowerCase(Locale.ROOT)))
//            .contentType(type.toString());
  }

  private void setupResources() {
    driver.setResourceBase(GatewayBasicFuncTest.class);

    try {
      setupResource("WEBHDFS", "/v1/");
      setupResource("WEBHCAT", "/v1/status");
      setupResource("WEBHCAT", "/v1/version");
      setupResource("WEBHCAT", "/v1/version/hive");
      setupResource("WEBHCAT", "/v1/version/hadoop");
      setupResource("OOZIE", "/v1/admin/build-version");
      setupResource("OOZIE", "/v1/admin/status");
      setupResource("OOZIE", "/versions");
      setupResource("WEBHBASE", "/version");
      setupResource("WEBHBASE", "/version/cluster");
      setupResource("WEBHBASE", "/status/cluster");
      setupResource("WEBHBASE", "/");
      setupResource("RESOURCEMANAGER", "/v1/cluster/info/");
      setupResource("RESOURCEMANAGER", "/v1/cluster/metrics/");
      setupResource("RESOURCEMANAGER", "/v1/cluster/apps/");
      setupResource("STORM", "/api/v1/cluster/configuration");
      setupResource("STORM", "/api/v1/cluster/summary");
      setupResource("STORM", "/api/v1/supervisor/summary");
      setupResource("STORM", "/api/v1/topology/summary");
      setupResource("FALCON", "/api/admin/stack");
      setupResource("FALCON", "/api/admin/version");
      setupResource("FALCON", "/api/metadata/lineage/serialize");
      setupResource("FALCON", "/api/metadata/lineage/vertices/all");
      setupResource("FALCON", "/api/metadata/lineage/edges/all");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private String createFileNN( String user, String password, String file, String permsOctal, int status ) throws IOException {
    if( status == HttpStatus.SC_TEMPORARY_REDIRECT ) {
      driver.getMock( "WEBHDFS" )
          .expect()
          .method( "PUT" )
          .pathInfo( "/v1" + file )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .respond()
          .status( status )
          .header( "Location", driver.getRealUrl("DATANODE") + file + "?op=CREATE&user.name="+user );
    } else {
      driver.getMock( "WEBHDFS" )
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
        .then()
        //.log().all()
        .statusCode( status )
        .when().put( driver.getUrl( "WEBHDFS" ) + "/v1" + file + ( driver.isUseGateway() ? "" : "?user.name=" + user ) );
    String location = response.getHeader( "Location" );
    log.trace( "Redirect location: " + response.getHeader( "Location" ) );
    return location;
  }

  private int createFileDN( String user, String password, String path, String location, String contentType, String resource, int status ) throws IOException {
    if( status == HttpStatus.SC_CREATED ) {
      driver.getMock( "DATANODE" )
          .expect()
          .method( "PUT" )
          .pathInfo( path )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .contentType( contentType )
          .content( driver.getResourceBytes( resource ) )
          .respond()
          .status( status )
          .header( "Location", "webhdfs://" + driver.getRealAddr( "DATANODE" ) + "/v1" + path );
    } else {
      driver.getMock( "DATANODE" )
          .expect()
          .method( "PUT" )
          .pathInfo( path )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .contentType( contentType )
          .content( driver.getResourceStream( resource ) )
          .respond()
          .status( status );
    }
    Response response = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .contentType( contentType )
        .body( driver.getResourceBytes( resource ) )
        .then()
        //.log().all()
        .statusCode( status )
        .when().put( location );
    return response.getStatusCode();
  }

  private String createFile(
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
    driver.assertComplete();
    return location;
  }

  private void readFile( String user, String password, String file, String contentType, String resource, int status ) throws IOException {
    driver.getMock( "WEBHDFS" )
        .expect()
        .method( "GET" )
        .pathInfo( "/v1" + file )
        .queryParam( "user.name", user )
        .queryParam( "op", "OPEN" )
        .respond()
        .status( HttpStatus.SC_TEMPORARY_REDIRECT )
        .header( "Location", driver.getRealUrl( "DATANODE" ) + file + "?op=OPEN&user.name="+user );
    if( status == HttpStatus.SC_OK ) {
      driver.getMock( "DATANODE" )
          .expect()
          .method( "GET" )
          .pathInfo( file )
          .queryParam( "user.name", user )
          .queryParam( "op", "OPEN" )
          .respond()
          .status( status )
          .contentType( contentType )
          .content( driver.getResourceBytes( resource ) );
    } else {
      driver.getMock( "DATANODE" )
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
        .then()
        //.log().all()
        .statusCode( status )
        .when().get( driver.getUrl("WEBHDFS") + "/v1" + file + ( driver.isUseGateway() ? "" : "?user.name=" + user ) );
    if( response.getStatusCode() == HttpStatus.SC_OK ) {
      String actualContent = response.asString();
      String thenedContent = driver.getResourceString( resource );
      assertThat( actualContent, Matchers.is(thenedContent) );
    }
    driver.assertComplete();
  }

  private void chownFile( String user, String password, String file, String owner, String group, int status ) {
    driver.getMock( "WEBHDFS" )
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
        .then()
        //.log().all()
        .statusCode( status )
        .when().put( driver.getUrl("WEBHDFS") + "/v1" + file + ( driver.isUseGateway() ? "" : "?user.name=" + user ) );
    driver.assertComplete();
  }

  private void chmodFile( String user, String password, String file, String permsOctal, int status ) {
    driver.getMock( "WEBHDFS" )
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
        .then()
        //.log().all()
        .statusCode( status )
        .when().put( driver.getUrl("WEBHDFS") + "/v1" + file + ( driver.isUseGateway() ? "" : "?user.name=" + user ) );
    driver.assertComplete();
  }

  private String updateFile( String user, String password, String file, String contentType, String resource, int nnStatus, int dnStatus ) throws IOException {
    String location;
    location = updateFileNN( user, password, file, resource, nnStatus );
    if( location != null ) {
      updateFileDN( user, password, file, location, contentType, resource, dnStatus );
    }
    driver.assertComplete();
    return location;
  }

  private String updateFileNN( String user, String password, String file, String resource, int status ) throws IOException {
    if( status == HttpStatus.SC_TEMPORARY_REDIRECT ) {
      driver.getMock( "WEBHDFS" )
          .expect()
          .method( "PUT" )
          .pathInfo( "/v1" + file )
          .queryParam( "op", "CREATE" )
          .queryParam( "user.name", user )
          .queryParam( "overwrite", "true" )
          .respond()
          .status( status )
          .header( "Location", driver.getRealUrl("DATANODE") + file + "?op=CREATE&user.name="+user );
    } else {
      driver.getMock( "WEBHDFS" )
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
        .body( driver.getResourceBytes( resource ) )
        .then()
        //.log().all()
        .statusCode( status )
        .when().put( driver.getUrl("WEBHDFS") + "/v1" + file + ( driver.isUseGateway() ? "" : "?user.name=" + user ) );
    String location = response.getHeader( "Location" );
    log.trace( "Redirect location: " + response.getHeader( "Location" ) );
    return location;
  }

  private void updateFileDN( String user, String password, String path, String location, String contentType, String resource, int status ) throws IOException {
    if( status == HttpStatus.SC_CREATED ) {
      driver.getMock( "DATANODE" )
          .expect()
          .method( "PUT" )
          .pathInfo( path )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .contentType( contentType )
          .content( driver.getResourceBytes( resource ) )
          .respond()
          .status( status )
          .header( "Location", "webhdfs://" + driver.getRealAddr( "DATANODE" ) + "/v1" + path );
    } else {
      driver.getMock( "DATANODE" )
          .expect()
          .method( "PUT" )
          .pathInfo( path )
          .queryParam( "user.name", user )
          .queryParam( "op", "CREATE" )
          .contentType( contentType )
          .content( driver.getResourceBytes( resource ) )
          .respond()
          .status( status );
    }
    given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .queryParam( "op", "CREATE" )
        .queryParam( "overwrite", "true" )
        .contentType( contentType )
        .body( driver.getResourceBytes( resource ) )
        .then()
        //.log().all()
        .statusCode( status )
        .when().put( location );
  }

  private void deleteFile( String user, String password, String file, String recursive, int... status ) {
    driver.getMock( "WEBHDFS" )
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
        .then()
        //.log().all()
        .statusCode( Matchers.is(in(ArrayUtils.toObject(status))) )
        .when()
        .delete( driver.getUrl( "WEBHDFS" ) + "/v1" + file + ( driver.isUseGateway() ? "" : "?user.name=" + user ) );
    driver.assertComplete();
  }

  private String createDir( String user, String password, String dir, String permsOctal, int status ) {
    driver.getMock( "WEBHDFS" )
        .expect()
        .method( "PUT" )
        .pathInfo( "/v1" + dir )
        .queryParam( "op", "MKDIRS" )
        .queryParam( "user.name", user )
        .queryParam( "permission", permsOctal )
        .respond()
        .status( HttpStatus.SC_OK )
        .contentType( "application/json" )
        .content( "{\"boolean\": true}".getBytes(StandardCharsets.UTF_8) );
    Response response = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .queryParam( "op", "MKDIRS" )
        .queryParam( "permission", permsOctal )
        .then()
        //.log().all()
        .statusCode( status )
        .contentType( "application/json" )
        .body( "boolean", CoreMatchers.equalTo(true) )
        .when()
        .put( driver.getUrl("WEBHDFS") + "/v1" + dir + ( driver.isUseGateway() ? "" : "?user.name=" + user ) );
    return response.getHeader( "Location" );
  }

  private String createDir( String user, String password, String group, String dir, String permsOctal, int nnStatus, int chownStatus ) {
    String location = createDir( user, password, dir, permsOctal, nnStatus );
    if( location != null ) {
      chownFile( user, password, dir, user, group, chownStatus );
    }
    return location;
  }

  private String submitJava( String user, String password, String jar, String main, String input, String output, int status ) {
    driver.getMock( "WEBHCAT" )
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
        .content( "{\"id\":\"job_201210301335_0086\"}".getBytes(StandardCharsets.UTF_8) );
    String json = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .formParam( "user.name", user )
        .formParam( "jar", jar )    //"/user/hdfs/test/hadoop-examples.jar" )
        .formParam( "class", main ) //"org.apache.WordCount" )
        .formParam( "arg", input, output ) //.formParam( "arg", "/user/hdfs/test/input", "/user/hdfs/test/output" )
        .then()
        //.log().all()
        .statusCode( status )
        .when().post( driver.getUrl( "WEBHCAT" ) + "/v1/mapreduce/jar" + ( driver.isUseGateway() ? "" : "?user.name=" + user ) ).asString();
    log.trace( "JSON=" + json );
    String job = JsonPath.from(json).getString( "id" );
    log.debug( "JOB=" + job );
    driver.assertComplete();
    return job;
  }

  private String submitPig( String user, String password, String group, String file, String arg, String statusDir, int... status ) {
    driver.getMock( "WEBHCAT" )
        .expect()
        .method( "POST" )
        .pathInfo( "/v1/pig" )
        .respond()
        .status( status[0] )
        .contentType( "application/json" )
        .content( "{\"id\":\"job_201210301335_0086\"}".getBytes(StandardCharsets.UTF_8) );
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
        .then()
        //.log().all();
        .statusCode( Matchers.is(in(ArrayUtils.toObject(status))) )
        .contentType( "application/json" )
        //.content( "boolean", equalTo( true ) )
        .when()
        .post( driver.getUrl( "WEBHCAT" ) + "/v1/pig" + ( driver.isUseGateway() ? "" : "?user.name=" + user ) )
        .asString();
    log.trace( "JSON=" + json );
    String job = JsonPath.from(json).getString( "id" );
    log.debug( "JOB=" + job );
    driver.assertComplete();
    return job;
  }

  private String submitHive( String user, String password, String group, String file, String statusDir, int... status ) {
    driver.getMock( "WEBHCAT" )
        .expect()
        .method( "POST" )
        .pathInfo( "/v1/hive" )
        .respond()
        .status( status[ 0 ] )
        .contentType( "application/json" )
        .content( "{\"id\":\"job_201210301335_0086\"}".getBytes(StandardCharsets.UTF_8) );
    String json = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .formParam( "user.name", user )
        .formParam( "group", group )
        .formParam( "group", group )
        .formParam( "file", file )
        .formParam( "statusdir", statusDir )
        .then()
        //.log().all()
        .statusCode( Matchers.is(in(ArrayUtils.toObject(status))) )
        .contentType( "application/json" )
        //.content( "boolean", equalTo( true ) )
        .when()
        .post( driver.getUrl( "WEBHCAT" ) + "/v1/hive" + ( driver.isUseGateway() ? "" : "?user.name=" + user ) )
        .asString();
    log.trace( "JSON=" + json );
    String job = JsonPath.from(json).getString( "id" );
    log.debug( "JOB=" + job );
    driver.assertComplete();
    return job;
  }

  private void queryQueue( String user, String password, String job ) throws IOException {
    driver.getMock( "WEBHCAT" )
        .expect()
        .method( "GET" )
        .pathInfo( "/v1/jobs/" + job )
        .respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "webhcat-job-status.json" ) )
        .contentType( "application/json" );
    String status = given()
        //.log().all()
        .auth().preemptive().basic( user, password )
        .header( "X-XSRF-Header", "jksdhfkhdsf" )
        .pathParam( "job", job )
        .then()
        //.log().all()
        .body( "status.jobId", CoreMatchers.equalTo(job) )
        .statusCode( HttpStatus.SC_OK )
        .when().get( driver.getUrl( "WEBHCAT" ) + "/v1/jobs/{job}" + ( driver.isUseGateway() ? "" : "?user.name=" + user ) ).asString();
    log.debug( "STATUS=" + status );
    driver.assertComplete();
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
  private String oozieSubmitJob( String user, String password, String request, int status ) throws IOException, URISyntaxException {
    driver.getMock( "OOZIE" )
        .expect()
        .method( "POST" )
        .pathInfo( "/v1/jobs" )
        .respond()
        .status( HttpStatus.SC_CREATED )
        .content( driver.getResourceBytes( "oozie-jobs-submit-response.json" ) )
        .contentType( "application/json" );
    URL url = new URL( driver.getUrl( "OOZIE" ) + "/v1/jobs?action=start" + ( driver.isUseGateway() ? "" : "&user.name=" + user ) );
    HttpHost targetHost = new HttpHost( url.getHost(), url.getPort(), url.getProtocol() );
    HttpClientBuilder builder = HttpClientBuilder.create();
    CloseableHttpClient client = builder.build();

    HttpClientContext context = HttpClientContext.create();
    CredentialsProvider credsProvider = new BasicCredentialsProvider();
    credsProvider.setCredentials(
        new AuthScope( targetHost ),
        new UsernamePasswordCredentials( user, password ) );
    context.setCredentialsProvider( credsProvider );

    // Create AuthCache instance
    AuthCache authCache = new BasicAuthCache();
    // Generate BASIC scheme object and add it to the local auth cache
    BasicScheme basicAuth = new BasicScheme();
    authCache.put( targetHost, basicAuth );
    // Add AuthCache to the execution context
    context.setAuthCache( authCache );

    HttpPost post = new HttpPost( url.toURI() );
//      post.getParams().setParameter( "action", "start" );
    StringEntity entity = new StringEntity( request, org.apache.http.entity.ContentType.create( "application/xml", StandardCharsets.UTF_8.name() ) );
    post.setEntity( entity );
    post.setHeader( "X-XSRF-Header", "ksdjfhdsjkfhds" );
    HttpResponse response = client.execute( targetHost, post, context );
    assertThat( response.getStatusLine().getStatusCode(), Matchers.is(status) );
    String json = EntityUtils.toString( response.getEntity() );

//      String json = given()
//          .log().all()
//          .auth().preemptive().basic( user, password )
//          .queryParam( "action", "start" )
//          .contentType( "application/xml;charset=UTF-8" )
//          .content( request )
//          .then()
//          .log().all()
//          .statusCode( status )
//          .when().post( getUrl( "OOZIE" ) + "/v1/jobs" + ( isUseGateway() ? "" : "?user.name=" + user ) ).asString();
    return JsonPath.from(json).getString( "id" );
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
  private String oozieQueryJobStatus( String user, String password, String id, int status ) throws Exception {
    driver.getMock( "OOZIE" )
        .expect()
        .method( "GET" )
        .pathInfo( "/v1/job/" + id )
        .respond()
        .status( HttpStatus.SC_OK )
        .content( driver.getResourceBytes( "oozie-job-show-info.json" ) )
        .contentType( "application/json" );

    //NOTE:  For some reason REST-assured doesn't like this and ends up failing with Content-Length issues.
    URL url = new URL( driver.getUrl( "OOZIE" ) + "/v1/job/" + id + ( driver.isUseGateway() ? "" : "?user.name=" + user ) );
    HttpHost targetHost = new HttpHost( url.getHost(), url.getPort(), url.getProtocol() );
    HttpClientBuilder builder = HttpClientBuilder.create();
    CloseableHttpClient client = builder.build();

    HttpClientContext context = HttpClientContext.create();
    CredentialsProvider credsProvider = new BasicCredentialsProvider();
    credsProvider.setCredentials(
        new AuthScope( targetHost ),
        new UsernamePasswordCredentials( user, password ) );
    context.setCredentialsProvider( credsProvider );

    // Create AuthCache instance
    AuthCache authCache = new BasicAuthCache();
    // Generate BASIC scheme object and add it to the local auth cache
    BasicScheme basicAuth = new BasicScheme();
    authCache.put( targetHost, basicAuth );
    // Add AuthCache to the execution context
    context.setAuthCache( authCache );

    HttpGet request = new HttpGet( url.toURI() );
    request.setHeader("X-XSRF-Header", "ksdhfjkhdsjkf");
    HttpResponse response = client.execute( targetHost, request, context );
    assertThat( response.getStatusLine().getStatusCode(), Matchers.is(status) );
    String json = EntityUtils.toString( response.getEntity() );
    return JsonPath.from(json).getString( "status" );
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

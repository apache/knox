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

import org.apache.hadoop.gateway.descriptor.ClusterDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterDescriptorFactory;
import org.apache.hadoop.gateway.jetty.JettyGatewayFactory;
import org.apache.hadoop.gateway.security.EmbeddedApacheDirectoryServer;
import org.apache.hadoop.test.category.FunctionalTests;
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

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.ws.rs.HttpMethod;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@Category( { FunctionalTests.class, MediumTests.class } )
public class GatewayLdapFuncTest {

//  @Test
//  public void demoWait() throws IOException {
//    System.out.println( "Press any key to continue. Server at " + getGatewayPath() );
//    System.in.read();
//  }

  private static Logger log = LoggerFactory.getLogger( GatewayLdapFuncTest.class );

  private static boolean MOCK = Boolean.parseBoolean( System.getProperty( "MOCK", "true" ) );
  //private static boolean MOCK = false;
  private static boolean GATEWAY = Boolean.parseBoolean( System.getProperty( "GATEWAY", "true" ) );
  //private static boolean GATEWAY = false;

  private static final int LDAP_PORT = 33389;
  private static final int GATEWAY_PORT = 8888;

  private static String TEST_HOST_NAME = "vm.home";
  private static String NAME_NODE_ADDRESS = TEST_HOST_NAME + ":50070";
  private static String DATA_NODE_ADDRESS = TEST_HOST_NAME + ":50075";

  private static EmbeddedApacheDirectoryServer ldap;
  private static Server gateway;
  private static MockServer namenode;
  private static MockServer datanode;

  public static void startGateway() throws Exception {

    Map<String,String> params = new HashMap<String,String>();
    params.put( "gateway.address", "localhost:" + GATEWAY_PORT );
    if( MOCK ) {
      params.put( "namenode.address", "localhost:" + namenode.getPort() );
      params.put( "datanode.address", "localhost:" + datanode.getPort() );
    } else {
      params.put( "namenode.address", NAME_NODE_ADDRESS );
      params.put( "datanode.address", DATA_NODE_ADDRESS );
    }

    URL configUrl = ClassLoader.getSystemResource( "org/apache/hadoop/gateway/GatewayFuncTest.xml" );
    Reader configReader = new InputStreamReader( configUrl.openStream() );
    ClusterDescriptor config = ClusterDescriptorFactory.load( "xml", configReader );
    configReader.close();

    for( Map.Entry<String,String> param : params.entrySet() ) {
      config.addParam().name( param.getKey() ).value( param.getValue() );
    }
//    URL configUrl = ClassLoader.getSystemResource( "org/apache/hadoop/gateway/GatewayFuncTest.xml" );
//    Config config = ClusterConfigFactory.create( configUrl, params );

    Handler handler = JettyGatewayFactory.create( "/gateway/cluster", config );
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
    startGateway();

    log.info( "LDAP port = " + LDAP_PORT );
    log.info( "NameNode port = " + namenode.getPort() );
    log.info( "DataNode port = " + datanode.getPort() );
    log.info( "Gateway port = " + gateway.getConnectors()[ 0 ].getLocalPort() );
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    gateway.stop();
    gateway.join();
    namenode.stop();
    datanode.stop();
    ldap.stop();
  }

  private String getGatewayPath() {
    Connector conn = gateway.getConnectors()[0];
    return "http://localhost:" + conn.getLocalPort();
  }

  private String getWebHdfsPath() {
    if( GATEWAY ) {
      return getGatewayPath()+ "/gateway/cluster/namenode/api/v1";
    } else if ( MOCK ) {
      return "http://localhost:" + namenode.getPort();
    } else {
      return "http://"+NAME_NODE_ADDRESS+"/webhdfs/v1";
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testDirectJndiLdapAuthenticate() {

    Hashtable env = new Hashtable();
    env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );
    env.put( Context.PROVIDER_URL, "ldap://localhost:" + LDAP_PORT );
    env.put( Context.SECURITY_AUTHENTICATION, "simple" );
    env.put( Context.SECURITY_PRINCIPAL, "uid=allowedUser,ou=people,dc=hadoop,dc=apache,dc=org" );
    env.put( Context.SECURITY_CREDENTIALS, "password" );

    try {
      DirContext ctx = new InitialDirContext( env );
      ctx.close();
    } catch( NamingException e ) {
      e.printStackTrace();
      fail( "Should have been able to find the allowedUser and create initial context." );
    }

    env = new Hashtable();
    env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );
    env.put( Context.PROVIDER_URL, "ldap://localhost:33389" );
    env.put( Context.SECURITY_AUTHENTICATION, "simple" );
    env.put( Context.SECURITY_PRINCIPAL, "uid=allowedUser,ou=people,dc=hadoop,dc=apache,dc=org" );
    env.put( Context.SECURITY_CREDENTIALS, "invalid-password" );

    try {
      DirContext ctx = new InitialDirContext( env );
      fail( "Should have thrown a NamingException to indicate invalid credentials." );
    } catch( NamingException e ) {
      // This exception should be thrown.
    }
  }

  @Test
  public void testLdapProtectedService() throws IOException {
    String namenodePath = getWebHdfsPath();

    log.info( "Making REST API call." );
    // Attempt to delete the test directory in case a previous run failed.
    // Ignore any result.
    if( MOCK ) {
      namenode
          .expect()
          .method( HttpMethod.DELETE )
          .queryParam( "user.name", "hdfs" )
          .queryParam( "op", "DELETE" )
          .queryParam( "recursive", "true" )
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
        .statusCode( HttpStatus.SC_OK )
        .content( "boolean", equalTo( true ) )
        .when()
        .delete( namenodePath + "/test" );
    if( MOCK ) {
      assertThat( namenode.isEmpty(), is( true ) );
    }

    given()
        //.log().all()
        .auth().preemptive().basic( "deniedUser", "invalid-password" )
        .queryParam( "user.name", "hdfs" )
        .queryParam( "op", "DELETE" )
        .queryParam( "recursive", "true" )
        .expect()
        .statusCode( HttpStatus.SC_UNAUTHORIZED )
        //.log().all()
        .when()
        .delete( namenodePath + "/test" );
  }

}

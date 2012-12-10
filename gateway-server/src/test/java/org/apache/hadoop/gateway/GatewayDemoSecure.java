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

import org.apache.hadoop.gateway.descriptor.GatewayDescriptor;
import org.apache.hadoop.gateway.descriptor.GatewayDescriptorFactory;
import org.apache.hadoop.gateway.jetty.JettyGatewayFactory;
import org.apache.hadoop.gateway.mock.MockConsoleFactory;
import org.apache.hadoop.test.category.ManualTests;
import org.apache.hadoop.test.category.SlowTests;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

/**
 *
 */
@Category( { ManualTests.class, SlowTests.class } )
public class GatewayDemoSecure {

  private static Server gateway;

  public static void startGateway() throws Exception {

    URL configUrl = ClassLoader.getSystemResource( "gateway-demo-secure.xml" );
    Reader configReader = new InputStreamReader( configUrl.openStream() );
    GatewayDescriptor gatewayConfig = GatewayDescriptorFactory.load( "xml", configReader );
    configReader.close();
//    Config gatewayConfig;
//    gatewayConfig = ClusterConfigFactory.create( configUrl, null );

    ContextHandlerCollection contexts = new ContextHandlerCollection();
    contexts.addHandler( MockConsoleFactory.create() );

    Map<String,String> params = new HashMap<String,String>();

    try {
      contexts.addHandler( JettyGatewayFactory.create( "/gw/vm", gatewayConfig ) );
    } catch( Exception e ) {
      e.printStackTrace();
      throw e;
    }

    gateway = new Server( 8888 );
    gateway.setHandler( contexts );
    gateway.start();
  }

  @BeforeClass
  public static void setupSuite() throws Exception {

    URL loginUrl = ClassLoader.getSystemResource( "jaas.conf" );
    System.setProperty( "java.security.auth.login.config", loginUrl.getFile() );
    URL krbUrl = ClassLoader.getSystemResource( "krb5.conf" );
    System.setProperty( "java.security.krb5.conf", krbUrl.getFile() );
    System.setProperty( "java.security.krb5.debug", "true" );

    startGateway();
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    gateway.stop();
    gateway.join();
  }

  private static String ensureLeadingSlash( String path ) {
    if( path.startsWith( "/" ) ) {
      return path;
    } else {
      return "/" + path;
    }
  }

  private static String getPath( String protocol, Server server, String path ) {
    Connector connector = server.getConnectors()[ 0 ];
    return protocol + "://localhost:" + connector.getLocalPort() + ( path == null ? "" : ensureLeadingSlash( path ) );
  }

  @org.junit.Test
  public void demoWait() throws IOException {
    System.out.println( "Press any key to continue. Server at " + getPath( "http", gateway, null ) );
    System.in.read();
  }

  @Test
  public void testLive() throws IOException {

    // "http://org.apache.hadoop-secure.home:50070/webhdfs/v1/horton/changes?op=OPEN"
    String url = getPath( "http", gateway, "/gw/vm/webhdfs/v1/horton/changes?op=OPEN" );

//    InputStream input = given()
//        .auth()
//        .basic( "horton", "horton" )
//        .expect()
//        .when().pick( url ).asInputStream();

    DefaultHttpClient client = new DefaultHttpClient();
    client.getCredentialsProvider().setCredentials( AuthScope.ANY, new UsernamePasswordCredentials( "horton", "horton" ) );
    HttpGet request = new HttpGet( url );
    HttpResponse response = client.execute( request );
    InputStream input = response.getEntity().getContent();

    String body = drainToString( input ).trim();

//    System.out.println( body );

    assertThat( body, startsWith( "Hadoop Change Log" ) );
    assertThat( body, endsWith( "1. The first release of Hadoop." ) );

  }

  private String drainToString( InputStream input ) throws IOException {
    StringBuilder builder = new StringBuilder();
    byte[] buffer = new byte[ 1024 ];
    int n, t=0;
    while( ( n = input.read( buffer ) ) >= 0 ) {
      if( n > 0 ) {
        builder.append( new String( buffer, 0, n ) );
      }
    }
    input.close();
    return builder.toString();
  }

}


//    ClusterConfig clusterConfig;
//    ServiceConfig serviceConfig;
//    FilterConfig filterConfig;
//
//    gatewayConfig = new GatewayConfigImpl();
//    gatewayConfig.put( "path", "org.apache.org.apache.hadoop.gateway" );
//
//    clusterConfig = new ClusterConfig();
//    clusterConfig.put( "path", "cluster" );
//
//    serviceConfig = new ServiceConfig();
//    serviceConfig.put( "path", "webhdfs" );
//    serviceConfig.put( "class", HttpClientPivot.class.getName() );
//    serviceConfig.put( "target", "http://org.apache.hadoop-secure.home:50070/" );
//
//    filterConfig = new FilterConfig();
//    filterConfig.put( "class", UrlRewriteFilter.class.getName() );
//    serviceConfig.filters.put( "rewriteUri", filterConfig );
//
//    clusterConfig.proxies.put( "hdfs-console", serviceConfig );
//
//    serviceConfig = new ServiceConfig();
//    serviceConfig.put( "path", "webhdfs/dfshealth.jsp" );
//    serviceConfig.put( "class", HttpClientPivot.class.getName() );
//    serviceConfig.put( "target", "http://org.apache.hadoop-secure.home:50070/dfshealth.jsp" );
//
//    filterConfig = new FilterConfig();
//    filterConfig.put( "class", UrlRewriteFilter.class.getName() );
//    serviceConfig.filters.put( "rewriteUri", filterConfig );
//
//    clusterConfig.proxies.put( "hdfs-health", serviceConfig );
//
//    serviceConfig = new ServiceConfig();
//    serviceConfig.put( "path", "webhdfs/*" );
//    serviceConfig.put( "class", UrlConnectionPivot.class.getName() );
//    serviceConfig.put( "target", "http://org.apache.hadoop-secure.home:50070/webhdfs" );
//
//    filterConfig = new FilterConfig();
//    filterConfig.put( "class", TraceFilter.class.getName() );
//    serviceConfig.filters.put( "trace", filterConfig );
//
//    filterConfig = new FilterConfig();
//    filterConfig.put( "class", SessionFilter.class.getName() );
//    serviceConfig.filters.put( "session", filterConfig );
//
//    filterConfig = new FilterConfig();
//    filterConfig.put( "class", BasicAuthChallengeFilter.class.getName() );
//    filterConfig.put( "realm", "Gateway" );
//    serviceConfig.filters.put( "challenge", filterConfig );
//
//    filterConfig = new FilterConfig();
//    filterConfig.put( "class", JaasLoginFilter.class.getName() );
//    serviceConfig.filters.put( "login", filterConfig );
//
//    clusterConfig.proxies.put( "hdfs-content", serviceConfig );
//
//    gatewayConfig.services.put( "cluster", clusterConfig );


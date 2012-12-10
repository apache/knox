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
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@Category( { ManualTests.class, SlowTests.class } )
public class GatewayDemoInsecure {

  private static Server gateway;

  @Test
  public void demoWait() throws IOException {
    System.out.println( "Press any key to continue. Server at " + getPath( "http", gateway, null ) );
    System.in.read();
  }

  public static void startGateway() throws Exception {

    URL configUrl = ClassLoader.getSystemResource( "gateway-demo-insecure.xml" );
    Reader configReader = new InputStreamReader( configUrl.openStream() );
    GatewayDescriptor gatewayConfig = GatewayDescriptorFactory.load( "xml", configReader );
    configReader.close();
    //Config gatewayConfig;
    //gatewayConfig = ClusterConfigFactory.create( configUrl, null );

    Map<String,String> params = new HashMap<String,String>();

    ContextHandlerCollection contexts = new ContextHandlerCollection();

    try {
      contexts.addHandler( MockConsoleFactory.create() );
      contexts.addHandler( GatewayRootFactory.create( "/org/apache/org.apache.hadoop/gateway" ) );
      contexts.addHandler( JettyGatewayFactory.create( "/org/apache/org.apache.hadoop/gateway/insecure", gatewayConfig ) );
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

}

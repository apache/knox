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

import org.apache.commons.vfs2.VFS;
import org.apache.hadoop.gateway.config.ClusterConfigFactory;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.jetty.JettyGatewayFactory;
import org.apache.hadoop.gateway.config.Config;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.topology.ClusterTopology;
import org.apache.hadoop.gateway.topology.ClusterTopologyEvent;
import org.apache.hadoop.gateway.topology.ClusterTopologyListener;
import org.apache.hadoop.gateway.topology.ClusterTopologyMonitor;
import org.apache.hadoop.gateway.topology.file.FileClusterTopologyProvider;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;

import javax.servlet.Servlet;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;

public class GatewayServer implements ClusterTopologyListener {

  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );
  private static GatewayServer server = new GatewayServer();

  private Server jetty;
  private ClusterTopologyMonitor monitor;

  //TODO: Need to locate bootstrap gateway config that provides information required to locate the Ambari server.
  //TODO: Provide an XML or JSON equivalent that can be specified directly if Ambari proper is not present.
  public static void main( String[] args ) {

    try {
      //CommandLine cmd = GatewayCommandLine.parse( args );
      server = new GatewayServer();
      log.startingGateway();
      server.startGateway();
      log.startedGateway( server.jetty.getConnectors()[ 0 ].getLocalPort() );
    } catch( Exception e ) {
      log.failedToStartGateway( e );
    }
  }

  private void startGateway() throws Exception {

//    Map<String,String> params = new HashMap<String,String>();
//    params.put( GatewayConfig.AMBARI_ADDRESS, gatewayConfig.getAmbariAddress() );
//    params.put( GatewayConfig.NAMENODE_ADDRESS, gatewayConfig.getNameNodeAddress() );
//    params.put( GatewayConfig.TEMPLETON_ADDRESS, gatewayConfig.getTempletonAddress() );
//    params.put( GatewayConfig.SHIRO_CONFIG_FILE, gatewayConfig.getShiroConfigFile() );

    // Create the global context handler.
    ContextHandlerCollection contexts = new ContextHandlerCollection();
    // Load the global gateway config.
    GatewayConfig gatewayConfig = new GatewayConfig();
    // Create a dir/file based cluster topology provider.
    File topologiesDir = new File( gatewayConfig.getGatewayHomeDir(), gatewayConfig.getClusterConfDir() );
    log.loadingTopologiesFromDirecotry( topologiesDir );
    FileClusterTopologyProvider provider = new FileClusterTopologyProvider( topologiesDir );
    // Load the initial cluster topologies.
    Collection<ClusterTopology> topologiesMap = provider.getClusterTopologies();
    // For each cluster topology create cluster config.
    for( ClusterTopology topology : topologiesMap ) {
      Config clusterConfig = ClusterConfigFactory.create( gatewayConfig, topology );
      // Create a Jetty handler for each cluser.
      ServletContextHandler handler = JettyGatewayFactory.create(
          gatewayConfig.getGatewayPath() + "/" + topology.getName(), clusterConfig );
      //TODO: Keep a mapping of cluster name to servlet to allow for dynamic reconfiguration.
      Servlet servlet = handler.getServletHandler().getServlet( topology.getName() ).getServlet();
      contexts.addHandler( handler );
    }

    InetSocketAddress address = gatewayConfig.getGatewayAddress();
    checkAddressAvailability( address );

    jetty = new Server( address );
    jetty.setHandler( contexts );
    jetty.start();

    // Register for changes to any of the topologies.
    log.monitoringTopologyChangesInDirectory( topologiesDir );
    monitor = provider;
    monitor.addTopologyChangeListener( this );
    monitor.startMonitor();
  }

  private void checkAddressAvailability( InetSocketAddress address ) throws IOException {
    ServerSocket socket = new ServerSocket();
    socket.bind( address );
    socket.close();
  }

  private void stopGateway() throws Exception {
    log.stoppingGateway();
    monitor.stopMonitor();
    jetty.stop();
    jetty.join();
    log.stoppedGateway();
  }

  @Override
  public void handleTopologyEvent( List<ClusterTopologyEvent> events ) {
    for( ClusterTopologyEvent event : events ) {
      //TODO: Replace the filter chain for the modified servlet.
      System.out.println( "Config change for cluster " + event.getTopology().getName() + ". Ignored for now." );
    }
  }
}

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

import org.apache.commons.cli.CommandLine;
import org.apache.hadoop.gateway.config.ClusterConfigFactory;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.jetty.JettyGatewayFactory;
import org.apache.hadoop.gateway.config.Config;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.topology.ClusterTopology;
import org.apache.hadoop.gateway.topology.ClusterTopologyEvent;
import org.apache.hadoop.gateway.topology.ClusterTopologyListener;
import org.apache.hadoop.gateway.topology.ClusterTopologyMonitor;
import org.apache.hadoop.gateway.topology.ClusterTopologyProvider;
import org.apache.hadoop.gateway.topology.file.FileClusterTopologyProvider;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;

import javax.servlet.Servlet;
import java.net.InetSocketAddress;
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
      CommandLine cmd = GatewayCommandLine.parse( args );
      log.startingGateway();
      server = new GatewayServer();
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
    // Load the global config.
    GatewayConfig gatewayConfig = new GatewayConfig();
    // Load the topology from files.
    FileClusterTopologyProvider provider = new FileClusterTopologyProvider( "dir" );
    // Load the initial topologies.
    Collection<ClusterTopology> topologies = provider.getClusterTopologies();
    // For each cluster topology create cluster config.
    for( ClusterTopology topology : topologies ) {
      Config clusterConfig = ClusterConfigFactory.create( gatewayConfig, topology );
      ServletContextHandler handler = JettyGatewayFactory.create(
          gatewayConfig.getGatewayPath() + "/" + topology.getName(), clusterConfig );
      //TODO: Keep a mapping of cluster name to servlet to allow for dynamic reconfiguration.
      Servlet servlet = handler.getServletHandler().getServlet( topology.getName() ).getServlet();
      contexts.addHandler( handler );
    }

    jetty = new Server( gatewayConfig.getGatewayAddr() );
    jetty.setHandler( contexts );
    jetty.start();

    // Register for changes to any of the topologies.
    monitor = provider;
    monitor.addTopologyChangeListener( this );
    monitor.startMonitor();
  }

  private void stopGateway() throws Exception {
    monitor.stopMonitor();
    jetty.stop();
    jetty.join();
  }

  @Override
  public void handleTopologyChangeEvent( List<ClusterTopologyEvent> events ) {
    for( ClusterTopologyEvent event : events ) {
      //TODO: Replace the filter chain for the modified servlet.
      System.out.println( "Config change for cluster " + event.getTopology().getName() + ". Ignored for now." );
    }
  }
}

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

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.deploy.ClusterDeploymentFactory;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.topology.ClusterTopology;
import org.apache.hadoop.gateway.topology.ClusterTopologyEvent;
import org.apache.hadoop.gateway.topology.ClusterTopologyListener;
import org.apache.hadoop.gateway.topology.ClusterTopologyMonitor;
import org.apache.hadoop.gateway.topology.file.FileClusterTopologyProvider;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.jboss.shrinkwrap.api.exporter.ExplodedExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.regex.Pattern;

public class GatewayServer implements ClusterTopologyListener {

  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );
  private static GatewayServer server = new GatewayServer();

  private Server jetty;
  private GatewayConfig config;
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
//    params.put( GatewayConfig.AMBARI_ADDRESS, config.getAmbariAddress() );
//    params.put( GatewayConfig.NAMENODE_ADDRESS, config.getNameNodeAddress() );
//    params.put( GatewayConfig.TEMPLETON_ADDRESS, config.getTempletonAddress() );
//    params.put( GatewayConfig.SHIRO_CONFIG_FILE, config.getShiroConfigFile() );

    // Create the global context handler.
    ContextHandlerCollection contexts = new ContextHandlerCollection();
    // Load the global gateway config.
    config = new GatewayConfig();
    // Create a dir/file based cluster topology provider.
    File topologiesDir = calcAbsTopoDir();
    log.loadingTopologiesFromDirecotry( topologiesDir.getAbsolutePath() );
    FileClusterTopologyProvider provider = new FileClusterTopologyProvider( topologiesDir );
//    // Load the initial cluster topologies.
//    Collection<ClusterTopology> topologiesMap = provider.getClusterTopologies();
//    // For each cluster topology create cluster config.
//    for( ClusterTopology topology : topologiesMap ) {
//      Config clusterConfig = ClusterConfigFactory.create( config, topology );
//      // Create a Jetty handler for each cluser.
//      ServletContextHandler handler = JettyGatewayFactory.create(
//          config.getGatewayPath() + "/" + topology.getName(), clusterConfig );
//      //TODO: Keep a mapping of cluster name to servlet to allow for dynamic reconfiguration.
//      //Servlet servlet = handler.getServletHandler().getServlet( topology.getName() ).getServlet();
//      contexts.addHandler( handler );
//    }

    InetSocketAddress address = config.getGatewayAddress();
    checkAddressAvailability( address );

    jetty = new Server( address );
    jetty.setHandler( contexts );
    jetty.start();

    // Register for changes to any of the topologies.
    log.monitoringTopologyChangesInDirectory( topologiesDir.getAbsolutePath() );
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
      ClusterTopology topology = event.getTopology();
      File topoDir = calcAbsTopoDir();
      File warDir = calcWarDir( topology );
      if( event.getType().equals( ClusterTopologyEvent.Type.DELETED ) ) {
        File[] files = topoDir.listFiles( new WarDirFilter( topology.getName() + "\\.[0-9A-Fa-f]+" ) );
        for( File file : files ) {
          log.deletingCluster( file.getAbsolutePath() );
          FileUtils.deleteQuietly( file );
        }
      } else if( !warDir.exists() ) {
        log.deployingCluster( topology.getName(), warDir.getAbsolutePath() );
        WebArchive war = ClusterDeploymentFactory.createClusterDeployment( config, topology );
        File tmp = war.as( ExplodedExporter.class ).exportExploded( topoDir, warDir.getName() + ".tmp" );
        tmp.renameTo( warDir );
        //log.deployedCluster( topology.getName() );
      }
    }
  }

  private File calcAbsTopoDir() {
    File topoDir = new File( config.getGatewayHomeDir(), config.getClusterConfDir() );
    topoDir = topoDir.getAbsoluteFile();
    return topoDir;
  }

  private File calcWarDir( ClusterTopology topology ) {
    File warDir = new File( calcAbsTopoDir(), topology.getName() + "." + Long.toHexString( topology.getTimestamp() ) );
    return warDir;
  }

  private String calcWarName( ClusterTopology topology ) {
    String name = topology.getName() + "." + Long.toHexString( topology.getTimestamp() );
    return name;
  }

  private class WarDirFilter implements FilenameFilter {

    Pattern pattern;

    WarDirFilter( String regex ) {
      pattern = Pattern.compile( regex );
    }

    @Override
    public boolean accept( File dir, String name ) {
      return pattern.matcher( name ).matches();
    }
  }

}

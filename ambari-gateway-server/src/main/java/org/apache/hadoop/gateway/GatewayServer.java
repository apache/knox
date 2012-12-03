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
import org.eclipse.jetty.webapp.WebAppContext;
import org.jboss.shrinkwrap.api.exporter.ExplodedExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class GatewayServer implements ClusterTopologyListener {

  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );
  private static GatewayServer server = new GatewayServer();

  private Server jetty;
  private GatewayConfig config;
  private ContextHandlerCollection contexts;
  private FileClusterTopologyProvider monitor;
  private Map<String, WebAppContext> deployments;

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

    // Load the global gateway config.
    config = new GatewayConfig();

    // Create a dir/file based cluster topology provider.
    File topologiesDir = calculateAbsoluteTopologiesDir();
    monitor = new FileClusterTopologyProvider( topologiesDir );
    monitor.addTopologyChangeListener( this );

    // Create the global context handler.
    contexts = new ContextHandlerCollection();

    // A map to keep track of current deployments by cluster name.
    deployments = new ConcurrentHashMap<String, WebAppContext>();

    // Determine the socket address and check availability.
    InetSocketAddress address = config.getGatewayAddress();
    checkAddressAvailability( address );

    // Start Jetty.
    jetty = new Server( address );
    jetty.setHandler( contexts );
    jetty.start();

    // Loading the topologies.
    log.loadingTopologiesFromDirecotry( topologiesDir.getAbsolutePath() );
    monitor.reloadTopologies();

    // Start the topology monitor.
    log.monitoringTopologyChangesInDirectory( topologiesDir.getAbsolutePath() );
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
      File topoDir = calculateAbsoluteTopologiesDir();
      File warDir = calcWarDir( topology );
      if( event.getType().equals( ClusterTopologyEvent.Type.DELETED ) ) {
        File[] files = topoDir.listFiles( new WarDirFilter( topology.getName() + "\\.war\\.[0-9A-Fa-f]+" ) );
        for( File file : files ) {
          log.deletingCluster( file.getAbsolutePath() );
          undeploy( topology );
          FileUtils.deleteQuietly( file );
        }
      } else {
        if( !warDir.exists() ) {
          log.deployingCluster( topology.getName(), warDir.getAbsolutePath() );
          WebArchive war = ClusterDeploymentFactory.createClusterDeployment( config, topology );
          File tmp = war.as( ExplodedExporter.class ).exportExploded( topoDir, warDir.getName() + ".tmp" );
          tmp.renameTo( warDir );
        }
        deploy( topology, topoDir );
      }
    }
  }

  private File calculateAbsoluteTopologiesDir() {
    File topoDir = new File( config.getGatewayHomeDir(), config.getClusterConfDir() );
    topoDir = topoDir.getAbsoluteFile();
    return topoDir;
  }

  private File calcWarDir( ClusterTopology topology ) {
    File warDir = new File( calculateAbsoluteTopologiesDir(), calcWarName( topology ) );
    return warDir;
  }

  private String calcWarName( ClusterTopology topology ) {
    String name = topology.getName() + ".war." + Long.toHexString( topology.getTimestamp() );
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

  public void undeploy( ClusterTopology topology ) {
    WebAppContext context = deployments.remove( topology.getName() );
    if( context != null ) {
      contexts.removeHandler( context ) ;
      try {
        context.stop();
      } catch( Exception e ) {
        //TODO: I18N message
        e.printStackTrace();
      }
    }
  }

  public void deploy( ClusterTopology topology, File topologyDir ) {
    String name = topology.getName();
    String war = topologyDir.getAbsolutePath();
    WebAppContext context = new WebAppContext();
    context.setDefaultsDescriptor( null );
    context.setContextPath( name );
    context.setWar( war );
    undeploy( topology );
    deployments.put( name, context );
    contexts.addHandler( context );
    try {
      context.start();
    } catch( Exception e ) {
      //TODO: I18N message
      e.printStackTrace();
    }
  }

}

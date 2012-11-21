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

import com.sun.xml.internal.bind.v2.TODO;
import org.apache.hadoop.gateway.config.Config;
import org.apache.hadoop.gateway.config.GatewayConfigFactory;
import org.apache.hadoop.gateway.topology.ClusterTopology;
import org.apache.hadoop.gateway.topology.file.FileClusterTopologyProvider;
import org.junit.Ignore;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

public class GatewayClusterConfigTest {

  private String locateTestConfigDir() {
    return null;
  }

  @Ignore( "TODO" )
  public void testBootstrapConfig() throws IOException, SAXException {
    String configDir = locateTestConfigDir();

    // Load the gateway config.
    GatewayConfig gatewayConfig = new GatewayConfig();

    // Load the cluster topologies.
    FileClusterTopologyProvider topologyProvider = new FileClusterTopologyProvider( gatewayConfig.getHadoopConfDir() );
    Collection<ClusterTopology> topologies = topologyProvider.getClusterTopologies();

    // Check each cluster config.
    Iterator<ClusterTopology> iterator = topologies.iterator();
//TODO    Config clusterConfig = GatewayConfigFactory.create( gatewayConfig, iterator.next() );
  }

//  public void testDynamicReconfig() throws IOException, SAXException {
//    String configDir = locateTestConfigDir();
//
//    // Load the gateway config.
//    GatewayConfig gatewayConfig = new GatewayConfig();
//
//    // Load the topologies.
//    FileClusterTopologyProvider topologyProvider = new FileClusterTopologyProvider( gatewayConfig.getHadoopConfDir() );
//    ClusterTopologyListener topologyListener = new TestClusterTopologyListener();
//    topologyProvider.addTopologyChangeListener( topologyListener );
//    Collection<ClusterTopology> topologies = topologyProvider.getClusterTopologies();
//
//    for( ClusterTopology clusterTopology : topologies ) {
//      // Create the cluster config.
//      Config clusterConfig = GatewayConfigFactory.create( gatewayConfig, clusterTopology );
//    }
//  }
//
//  private class TestClusterTopologyListener implements ClusterTopologyListener {
//    @Override
//    public void handleTopologyChangeEvent( List<ClusterTopologyEvent> events ) {
//    }
//  }

}

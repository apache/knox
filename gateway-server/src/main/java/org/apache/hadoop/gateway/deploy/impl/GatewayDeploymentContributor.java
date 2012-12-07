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
package org.apache.hadoop.gateway.deploy.impl;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.DeploymentContributor;
import org.apache.hadoop.gateway.deploy.DeploymentResourceDescriptorFactory;
import org.apache.hadoop.gateway.descriptor.ClusterDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.topology.ClusterTopology;
import org.apache.hadoop.gateway.topology.ClusterTopologyComponent;

import java.util.List;

public class GatewayDeploymentContributor implements DeploymentContributor {

  @Override
  public void contribute( DeploymentContext context ) {
    ClusterDescriptor clusterDescriptor = context.getClusterDescriptor();
    ClusterTopology clusterTopology = context.getClusterTopology();
    for( ClusterTopologyComponent clusterComponent : clusterTopology.getComponents() ) {
      DeploymentResourceDescriptorFactory factory = context.getClusterResourceDescriptorFactory( clusterComponent );
      if( factory != null ) {
        List<ResourceDescriptor> descriptors = factory.createResourceDescriptors( context, clusterComponent );
        for( ResourceDescriptor descriptor : descriptors ) {
          clusterDescriptor.addResource( descriptor );
        }
      }
    }
  }

}
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
import org.apache.hadoop.gateway.deploy.ResourceDescriptorFactory;
import org.apache.hadoop.gateway.descriptor.ClusterDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.topology.Topology;
import org.apache.hadoop.gateway.topology.Service;

import java.util.List;

public class GatewayDeploymentContributor implements DeploymentContributor {

  @Override
  public void contribute( DeploymentContext context ) {
    ClusterDescriptor clusterDescriptor = context.getClusterDescriptor();
    Topology topology = context.getTopology();
    for( Service service : topology.getServices() ) {
      ResourceDescriptorFactory factory = context.getResourceDescriptorFactory( service );
      if( factory != null ) {
        List<ResourceDescriptor> descriptors = factory.createResourceDescriptors( context, service );
        for( ResourceDescriptor descriptor : descriptors ) {
          clusterDescriptor.addResource( descriptor );
        }
      }
    }
  }

}
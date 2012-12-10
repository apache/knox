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
package org.apache.hadoop.gateway.deploy;

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.deploy.impl.DeploymentContextImpl;
import org.apache.hadoop.gateway.deploy.impl.FinalizeDeploymentContributor;
import org.apache.hadoop.gateway.deploy.impl.GatewayDeploymentContributor;
import org.apache.hadoop.gateway.deploy.impl.InitializeDeploymentContributor;
import org.apache.hadoop.gateway.descriptor.GatewayDescriptor;
import org.apache.hadoop.gateway.descriptor.GatewayDescriptorFactory;
import org.apache.hadoop.gateway.topology.Topology;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;

public abstract class DeploymentFactory {

  // Given global config
  // Given cluster topology
  // Create empty WebArchive
  // Create empty WebAppDescriptor
  // Invoke the Gateway deployment contributor
  // Create empty ClusterConfig
  // Find all ResourceConfigFactory services
  // For each service in cluster topo
  // Find and service's config factory
  //   Add created resources configs to cluster config
  //     Q: How do they know to include as vs ss?
  //   Populate the deployment context with the above.
  // Find all DeploymentContributor services
  // Invoke each contributor
  public static WebArchive createClusterDeployment( GatewayConfig config, Topology topology ) {
    WebArchive webArchive = ShrinkWrap.create( WebArchive.class, topology.getName() );
    WebAppDescriptor webAppDesc = Descriptors.create( WebAppDescriptor.class );
    GatewayDescriptor gateway = GatewayDescriptorFactory.create();

    DeploymentContext context
        = new DeploymentContextImpl( config, topology, gateway, webArchive, webAppDesc );

    DeploymentContributor initializeContributor = new InitializeDeploymentContributor();
    DeploymentContributor gatewayContributor = new GatewayDeploymentContributor();
    DeploymentContributor finalizeContributor = new FinalizeDeploymentContributor();

    initializeContributor.contribute( context );
    gatewayContributor.contribute( context );
    finalizeContributor.contribute( context );

    return webArchive;
  }

}

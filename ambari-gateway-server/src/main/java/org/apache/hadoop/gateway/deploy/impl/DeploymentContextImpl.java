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

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.DeploymentFilterDescriptorFactory;
import org.apache.hadoop.gateway.deploy.DeploymentResourceDescriptorFactory;
import org.apache.hadoop.gateway.descriptor.ClusterDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterDescriptorFactory;
import org.apache.hadoop.gateway.topology.ClusterTopology;
import org.apache.hadoop.gateway.topology.ClusterTopologyComponent;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;

public class DeploymentContextImpl implements DeploymentContext {

  private GatewayConfig gatewayConfig;
  private ClusterTopology clusterTopology;
  private ClusterDescriptor clusterDescriptor;
  private WebArchive webArchive;
  private WebAppDescriptor webAppDescriptor;

  public DeploymentContextImpl(
      GatewayConfig gatewayConfig,
      ClusterTopology clusterTopology,
      ClusterDescriptor clusterDescriptor,
      WebArchive webArchive,
      WebAppDescriptor webAppDescriptor ) {
    this.gatewayConfig = gatewayConfig;
    this.clusterTopology = clusterTopology;
    this.clusterDescriptor = clusterDescriptor;
    this.webArchive = webArchive;
    this.webAppDescriptor = webAppDescriptor;
  }

  @Override
  public DeploymentResourceDescriptorFactory getClusterResourceDescriptorFactory( ClusterTopologyComponent component ) {
    return ClusterDescriptorFactory.getClusterResourceDescriptorFactory( component );
  }

  @Override
  public DeploymentFilterDescriptorFactory getClusterFilterDescriptorFactory( String filterRole ) {
    return ClusterDescriptorFactory.getClusterFilterDescriptorFactory( filterRole );
  }

  @Override
  public GatewayConfig getGatewayConfig() {
    return gatewayConfig;
  }

  @Override
  public ClusterTopology getClusterTopology() {
    return clusterTopology;
  }

  @Override
  public WebArchive getWebArchive() {
    return webArchive;
  }

  @Override
  public WebAppDescriptor getWebAppDescriptor() {
    return webAppDescriptor;
  }

  @Override
  public ClusterDescriptor getClusterDescriptor() {
    return clusterDescriptor;
  }

}

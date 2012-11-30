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

import org.apache.hadoop.gateway.deploy.ClusterDeploymentContext;
import org.apache.hadoop.gateway.deploy.ClusterFilterDescriptorFactory;
import org.apache.hadoop.gateway.descriptor.ClusterFilterDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterFilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterResourceDescriptor;
import org.apache.hadoop.gateway.filter.UrlRewriteFilter;
import org.apache.hadoop.gateway.pivot.HttpClientPivot;
import org.apache.hadoop.gateway.topology.ClusterTopologyComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RewriteClusterFilterDescriptorFactory implements ClusterFilterDescriptorFactory {

  private static final Set<String> ROLES = createSupportedRoles();

  private static Set<String> createSupportedRoles() {
    HashSet<String> roles = new HashSet<String>();
    roles.add( "rewrite" );
    return Collections.unmodifiableSet( roles );
  }

  @Override
  public Set<String> getSupportedFilterRoles() {
    return ROLES;
  }

  @Override
  public List<ClusterFilterDescriptor> createFilterDescriptors( ClusterDeploymentContext clusterDeploymentContext, ClusterTopologyComponent clusterTopologyComponent, ClusterResourceDescriptor clusterResourceDescriptor, String filterRole, List<ClusterFilterParamDescriptor> filterParamDescriptors ) {
    List<ClusterFilterDescriptor> descriptors = new ArrayList<ClusterFilterDescriptor>();
    ClusterFilterDescriptor descriptor
        = clusterResourceDescriptor.createFilter().role( filterRole ).impl( UrlRewriteFilter.class );
    descriptor.addParams( filterParamDescriptors );
    descriptors.add( descriptor );
    return descriptors;
  }

}

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
import org.apache.hadoop.gateway.deploy.ClusterResourceDescriptorFactory;
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

public class HdfsClusterResourceDescriptorFactory implements ClusterResourceDescriptorFactory {

  private static final Set<String> ROLES = createSupportedRoles();

  private static Set<String> createSupportedRoles() {
    HashSet<String> roles = new HashSet<String>();
    roles.add( "NAMENODE" );
    return Collections.unmodifiableSet( roles );
  }

  @Override
  public Set<String> getSupportedResourceRoles() {
    return ROLES;
  }

  @Override
  public List<ClusterResourceDescriptor> createResourceDescriptors( ClusterDeploymentContext context, ClusterTopologyComponent component ) {
    List<ClusterResourceDescriptor> descriptors = new ArrayList<ClusterResourceDescriptor>();

    String extClusterUrl = "{request.scheme}://{request.host}:{request.port}/{gateway.path}/{cluster.path}";
    String extHdfsPath = "/namenode/api/v1";
    String intHdfsUrl = component.getUrl().toExternalForm();

    ClusterResourceDescriptor rootResource = context.getClusterDescriptor().createResource();
    rootResource.source( extHdfsPath + "?{**}" );
    rootResource.target( intHdfsUrl + "?{**}" );
    //TODO: Add authentication filter when we figure out how to configure it.
    rootResource.addFilters(
        context.getClusterFilterDescriptorFactory( "pivot" )
            .createFilterDescriptors( context, component, rootResource, "pivot", null ) );
    descriptors.add( rootResource );

    ClusterResourceDescriptor fileResource = context.getClusterDescriptor().createResource();
    fileResource.source( extHdfsPath + "/{path=**}?{**}" );
    fileResource.target( intHdfsUrl + "/{path=**}?{**}" );
    //TODO: Add authentication filter when we figure out how to configure it.
    List<ClusterFilterParamDescriptor> params = new ArrayList<ClusterFilterParamDescriptor>();
    params.add( fileResource.createParam()
        .name( "rewrite" )
        .value( "webhdfs://*:*/{path=**}" + " " + extClusterUrl + extHdfsPath + "/{path=**}" ) );
    fileResource.addFilters(
        context.getClusterFilterDescriptorFactory( "rewrite" )
            .createFilterDescriptors( context, component, fileResource, "rewrite", params ) );
    fileResource.addFilters(
        context.getClusterFilterDescriptorFactory( "pivot" )
            .createFilterDescriptors( context, component, fileResource, "pivot", null ) );
    descriptors.add( fileResource );

    return descriptors;
  }

}

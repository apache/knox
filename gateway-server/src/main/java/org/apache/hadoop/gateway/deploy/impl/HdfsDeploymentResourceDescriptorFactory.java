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
import org.apache.hadoop.gateway.deploy.DeploymentResourceDescriptorFactory;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.topology.ClusterTopologyComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HdfsDeploymentResourceDescriptorFactory implements
    DeploymentResourceDescriptorFactory {

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
  public List<ResourceDescriptor> createResourceDescriptors(
      DeploymentContext context, ClusterTopologyComponent component ) {
    List<ResourceDescriptor> descriptors = new ArrayList<ResourceDescriptor>();

    String extClusterUrl = "{request.scheme}://{request.host}:{request.port}/{gateway.path}/{cluster.path}";
    String extHdfsPath = "/namenode/api/v1";
    String intHdfsUrl = component.getUrl().toExternalForm();

    ResourceDescriptor rootResource = context.getClusterDescriptor()
        .createResource();
    rootResource.source( extHdfsPath + "?{**}" );
    rootResource.target( intHdfsUrl + "?{**}" );
    addAuthenticationProviderFilter( context, component, rootResource );
    addPivotFilter( context, component, rootResource );
    descriptors.add( rootResource );

    ResourceDescriptor fileResource = context.getClusterDescriptor()
        .createResource();
    fileResource.source( extHdfsPath + "/{path=**}?{**}" );
    fileResource.target( intHdfsUrl + "/{path=**}?{**}" );
    addAuthenticationProviderFilter( context, component, fileResource );
    addRewriteFilter( context, component, extClusterUrl, extHdfsPath, fileResource );
    addPivotFilter( context, component, fileResource );
    descriptors.add( fileResource );

    return descriptors;
  }

  private void addRewriteFilter( DeploymentContext context,
                                 ClusterTopologyComponent component, String extClusterUrl,
                                 String extHdfsPath, ResourceDescriptor fileResource ) {
    List<FilterParamDescriptor> params = new ArrayList<FilterParamDescriptor>();
    params.add( fileResource
        .createFilterParam()
        .name( "rewrite" )
        .value(
            "webhdfs://*:*/{path=**}" + " " + extClusterUrl + extHdfsPath
                + "/{path=**}" ) );
    fileResource.addFilters( context
        .getClusterFilterDescriptorFactory( "rewrite" ).createFilterDescriptors(
            context, component, fileResource, "rewrite", params ) );
  }

  private void addPivotFilter( DeploymentContext context,
                               ClusterTopologyComponent component, ResourceDescriptor rootResource ) {
    rootResource.addFilters( context.getClusterFilterDescriptorFactory( "pivot" )
        .createFilterDescriptors( context, component, rootResource, "pivot",
            null ) );
  }

  private void addAuthenticationProviderFilter( DeploymentContext context,
                                                ClusterTopologyComponent component, ResourceDescriptor resource ) {
    List<FilterParamDescriptor> params = getFilterParams( context, resource );

    resource.addFilters(
        context.getClusterFilterDescriptorFactory( "authentication" ).createFilterDescriptors(
            context, component, resource, "authentication", params ) );
  }

  private List<FilterParamDescriptor> getFilterParams(
      DeploymentContext context, ResourceDescriptor resource ) {
    Map<String, String> filterParams = context.getClusterTopology().getProvider( "authentication" ).getParams();
    List<FilterParamDescriptor> params = new ArrayList<FilterParamDescriptor>();
    Iterator<Map.Entry<String, String>> i = filterParams.entrySet().iterator();
    Map.Entry<String, String> entry;
    while( i.hasNext() ) {
      entry = i.next();
      params.add( resource
          .createFilterParam()
          .name( entry.getKey() )
          .value( entry.getValue() ) );
    }
    return params;
  }
}

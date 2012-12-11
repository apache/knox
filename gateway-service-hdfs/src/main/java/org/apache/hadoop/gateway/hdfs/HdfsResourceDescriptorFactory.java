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
package org.apache.hadoop.gateway.hdfs;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.FilterDescriptorFactory;
import org.apache.hadoop.gateway.deploy.ResourceDescriptorFactory;
import org.apache.hadoop.gateway.descriptor.FilterDescriptor;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HdfsResourceDescriptorFactory implements
    ResourceDescriptorFactory {

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
      DeploymentContext context, Service service ) {
    List<ResourceDescriptor> descriptors = new ArrayList<ResourceDescriptor>();

    String extClusterUrl = "{gateway.url}";
    String extHdfsPath = "/namenode/api/v1";
    String intHdfsUrl = service.getUrl().toExternalForm();

    ResourceDescriptor rootResource = context.getGatewayDescriptor().createResource();
    rootResource.source( extHdfsPath + "?{**}" );
    rootResource.target( intHdfsUrl + "?{**}" );
    addAuthenticationProviderFilter( context, service, rootResource );
    addPivotFilter( context, service, rootResource, "pivot" );
    descriptors.add( rootResource );

    ResourceDescriptor fileResource = context.getGatewayDescriptor().createResource();
    fileResource.source( extHdfsPath + "/{path=**}?{**}" );
    fileResource.target( intHdfsUrl + "/{path=**}?{**}" );
    addAuthenticationProviderFilter( context, service, fileResource );
    addRewriteFilter( context, service, extClusterUrl, extHdfsPath, fileResource );
    addPivotFilter( context, service, fileResource, "pivot/webhdfs" );
    descriptors.add( fileResource );

    return descriptors;
  }

  private void addRewriteFilter( DeploymentContext context,
                                 Service service, String extClusterUrl,
                                 String extHdfsPath, ResourceDescriptor fileResource ) {
    List<FilterParamDescriptor> params = new ArrayList<FilterParamDescriptor>();
    params.add( fileResource
        .createFilterParam()
        .name( "rewrite" )
        .value( "webhdfs://*:*/{path=**}" + " " + extClusterUrl + extHdfsPath + "/{path=**}" ) );
    fileResource.addFilters(
        context.getFilterDescriptorFactory( "rewrite" ).createFilterDescriptors(
            context, service, fileResource, "rewrite", params ) );
  }

  private void addPivotFilter( DeploymentContext context,
                               Service service,
                               ResourceDescriptor rootResource,
                               String filterRole ) {
    rootResource.addFilters( context.getFilterDescriptorFactory( filterRole )
        .createFilterDescriptors( context, service, rootResource, filterRole, null ) );
  }

  private void addAuthenticationProviderFilter( DeploymentContext context,
                                                Service service, ResourceDescriptor resource ) {
    List<FilterParamDescriptor> params = getFilterParams( context, resource );
    FilterDescriptorFactory factory = context.getFilterDescriptorFactory( "authentication" );
    if( factory != null ) {
      List<FilterDescriptor> descriptors = factory.createFilterDescriptors( context, service, resource, "authentication", params );
      if( descriptors != null ) {
        resource.addFilters( descriptors );
      }
    }
  }

  private List<FilterParamDescriptor> getFilterParams(
      DeploymentContext context, ResourceDescriptor resource ) {
    List<FilterParamDescriptor> params = new ArrayList<FilterParamDescriptor>();
    Provider provider = context.getTopology().getProvider( "authentication" );
    if( provider != null ) {
      Map<String, String> filterParams = provider.getParams();
      Iterator<Map.Entry<String, String>> i = filterParams.entrySet().iterator();
      Map.Entry<String, String> entry;
      while( i.hasNext() ) {
        entry = i.next();
        params.add( resource
            .createFilterParam()
            .name( entry.getKey() )
            .value( entry.getValue() ) );
      }
    }
    return params;
  }

}

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
import org.apache.hadoop.gateway.deploy.ServiceDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.topology.Service;

import java.util.ArrayList;
import java.util.List;

public class DataNodeDeploymentContributor extends ServiceDeploymentContributorBase {

  @Override
  public String getRole() {
    return "DATANODE";
  }

  @Override
  public String getName() {
    return "hdfs";
  }

  @Override
  public void contributeService( DeploymentContext context, Service service ) {

    String extGatewayUrl = "{gateway.url}";
    String extHdfsPath = "/datanode/api/v1";
    String intHdfsUrl = service.getUrl().toExternalForm();

//    ResourceDescriptor rootResource = context.getGatewayDescriptor().addResource();
//    rootResource.role( service.getRole() );
//    rootResource.source( extHdfsPath + "?{**}" );
//    rootResource.target( intHdfsUrl + "?{**}" );
//    addAuthenticationFilter( context, service, rootResource );
//    addDispatchFilter( context, service, rootResource, "dispatch", null );

    ResourceDescriptor fileResource = context.getGatewayDescriptor().addResource();
    fileResource.role( service.getRole() );
    fileResource.source( "/datanode/api/v1/{path=**}?{host}&{port}&{**}" );
    fileResource.target( "http://{host}:{port}/webhdfs/v1/{path=**}?{**}" );
    addAuthenticationFilter( context, service, fileResource );
    addRewriteFilter( context, service, fileResource, extGatewayUrl, extHdfsPath );
    addDispatchFilter( context, service, fileResource, "dispatch", null );
  }

  private void addRewriteFilter( DeploymentContext context,
                                 Service service,
                                 ResourceDescriptor resource,
                                 String extGatewayUrl,
                                 String extHdfsPath ) {
        List<FilterParamDescriptor> params = new ArrayList<FilterParamDescriptor>();
    FilterParamDescriptor param = resource.createFilterParam()
        .name( "rewrite" )
        .value( "webhdfs://*:*/{path=**}" + " " + extGatewayUrl + extHdfsPath + "/{path=**}" );
    params.add( param );
    context.contributeFilter( service, resource, "rewrite", null, params );
  }

  private void addDispatchFilter( DeploymentContext context, Service service, ResourceDescriptor resource, String role, String name ) {
    context.contributeFilter( service, resource, role, name, null );
  }

  private void addAuthenticationFilter( DeploymentContext context, Service service, ResourceDescriptor resource ) {
    context.contributeFilter( service, resource, "authentication", null, null );
  }

}

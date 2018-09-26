/*
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
package org.apache.knox.gateway.tgs;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ServiceDeploymentContributorBase;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.topology.Service;

import java.net.URISyntaxException;

public class TGSDeploymentContributor extends ServiceDeploymentContributorBase {

  private static final String TGS_EXTERNAL_PATH = "/tgs/api/v1";

  @Override
  public String getRole() {
    return "TGS";
  }

  @Override
  public String getName() {
    return "tgs";
  }

  @Override
  public void contributeService( DeploymentContext context, Service service ) throws URISyntaxException {
    ResourceDescriptor resource = context.getGatewayDescriptor().addResource();
    resource.role( service.getRole() );
    resource.pattern( TGS_EXTERNAL_PATH + "/accesstoken");
    if (topologyContainsProviderType(context, "authentication")) {
      context.contributeFilter( service, resource, "authentication", null, null );
    }
    if (topologyContainsProviderType(context, "federation")) {
      context.contributeFilter( service, resource, "federation", null, null );
    }
    context.contributeFilter( service, resource, "identity-assertion", null, null );
  }

}
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
package org.apache.knox.gateway.deploy.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;

public class DosFilterDeploymentContributor extends ProviderDeploymentContributorBase {
  private static final String JETTY_DOS_FILTER_CLASSNAME = "org.eclipse.jetty.servlets.DoSFilter";

  @Override
  public String getRole() {
    return "dos";
  }

  @Override
  public String getName() {
    return "JettyDoS";
  }

  @Override
  public void contributeFilter(DeploymentContext context, Provider provider, Service service, ResourceDescriptor resource, List<FilterParamDescriptor> params) {
    if (params == null) {
      params = new ArrayList<>();
    }
    copyAllProviderParams(provider, resource, params, false);
    resource.addFilter().name(getName()).role(getRole()).impl(JETTY_DOS_FILTER_CLASSNAME).params(params);
  }
}

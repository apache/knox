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
package org.apache.knox.gateway.deploy;

import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;

import java.util.List;

public interface ProviderDeploymentContributor {

  // The role this provider supports (e.g. authentication)
  String getRole();

  // In the topology the provider will have an optional name element.  If it is present
  // then the framework will look for the the provider deployment contributor with the correct
  // role and name.
  String getName();

  // All provider initializeContribution methods are called first in arbitrary order.
  void initializeContribution( DeploymentContext context );

  // Called for each provider in the topology based on the role and optionally name.
  void contributeProvider( DeploymentContext context, Provider provider );

  // This will be called indirectly by a ServiceDeploymentContributor when it needs a filter
  // contributed for this providers role.  A ServiceDeploymentContributor may request a specific
  // provider by role and name otherwise the default provider for the role will be used.
  void contributeFilter(
      DeploymentContext context,
      Provider provider,
      Service service,
      ResourceDescriptor resource,
      List<FilterParamDescriptor> params );

  // All provider finalizeContribution methods are called last in arbitrary order.
  void finalizeContribution( DeploymentContext context );

}

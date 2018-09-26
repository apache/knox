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

import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Version;

public interface ServiceDeploymentContributor {

  // The role of this service deployment contributor.  e.g. WEBHDFS
  String getRole();

  // The name of this service deployment contributor.  Not used yet.
  String getName();

  /**
   * Returns the version of the deployment contributor. This helps in providing versioned
   * contributions for service versions.
   * @return the version
   */
  Version getVersion();

  // Called after provider initializeContribution methods and in arbitrary order relative to other service contributors.
  void initializeContribution( DeploymentContext context );

  // Called per service based on the service's role.
  // Returns a list of resources it added to the descriptor.
  void contributeService( DeploymentContext context, Service service ) throws Exception;

  // Called after all contributors and before provider finalizeContribution methods.
  void finalizeContribution( DeploymentContext context );

}

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

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;

import java.util.List;

public interface DeploymentContext {

  GatewayConfig getGatewayConfig();

  Topology getTopology();

  WebArchive getWebArchive();

  WebAppDescriptor getWebAppDescriptor();

  GatewayDescriptor getGatewayDescriptor();

  void contributeFilter(
      Service service,
      ResourceDescriptor resource,
      String role,
      String name,
      List<FilterParamDescriptor> params );

  void addDescriptor( String name, Object descriptor );

  <T> T getDescriptor( String name );
}

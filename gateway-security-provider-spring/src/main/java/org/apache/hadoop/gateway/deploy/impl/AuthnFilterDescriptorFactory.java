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
import org.apache.hadoop.gateway.deploy.DeploymentContributorBase;
import org.apache.hadoop.gateway.deploy.FilterDescriptorFactory;
import org.apache.hadoop.gateway.descriptor.FilterDescriptor;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;
import org.jboss.shrinkwrap.descriptor.api.webcommon30.ServletType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AuthnFilterDescriptorFactory implements FilterDescriptorFactory {

  private static final String DEFAULT_FILTER_CLASSNAME = "org.springframework.web.filter.DelegatingFilterProxy";
  private static final Set<String> ROLES = createSupportedRoles();

  private static Set<String> createSupportedRoles() {
    HashSet<String> roles = new HashSet<String>();
    roles.add( "authentication" );
    return Collections.unmodifiableSet( roles );
  }

  @Override
  public Set<String> getSupportedFilterRoles() {
    return ROLES;
  }

  @Override
  public List<FilterDescriptor> createFilterDescriptors(
      DeploymentContext deploymentContext,
      Service service,
      ResourceDescriptor resourceDescriptor,
      String filterRole,
      List<FilterParamDescriptor> filterParamDescriptors ) {
    List<FilterDescriptor> descriptors = new ArrayList<FilterDescriptor>();
    Provider provider = deploymentContext.getTopology().getProvider( "authentication" );
    if( provider != null && provider.isEnabled() ) {
      String className = DEFAULT_FILTER_CLASSNAME;
      if( className != null ) {
        FilterDescriptor descriptor = resourceDescriptor.createFilter().role( filterRole ).impl( className );
        descriptor.addParams( filterParamDescriptors );
        descriptors.add( descriptor );
      }
    }
    return descriptors;
  }
}

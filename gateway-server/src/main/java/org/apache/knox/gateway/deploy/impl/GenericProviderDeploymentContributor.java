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

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.knox.gateway.descriptor.FilterDescriptor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GenericProviderDeploymentContributor extends
    ProviderDeploymentContributorBase {

  public static final String FILTER_PARAM = "filter";

  @Override
  public String getRole() {
    return "*";
  }

  @Override
  public String getName() {
    return "generic";
  }

  @Override
  public void contributeFilter(
      DeploymentContext context,
      Provider provider,
      Service service,
      ResourceDescriptor resource,
      List<FilterParamDescriptor> params ) {
    String filterClassName = getFilterClassName( provider.getParams() );
    FilterDescriptor filter = resource.addFilter();
    filter.name( getName() );
    filter.role( provider.getRole() );
    filter.impl( filterClassName );
    filter.params( getFilterInitParams( filter, provider.getParams(), params ) );
  }

  private String getFilterClassName( Map<String,String> params ) {
    String filterClassName = null;
    if( params != null ) {
      filterClassName = params.get( FILTER_PARAM );
    }
    if( filterClassName == null ) {
      throw new IllegalArgumentException( FILTER_PARAM + "==null" );
    }
    return filterClassName;
  }

  private List<FilterParamDescriptor> getFilterInitParams(
      FilterDescriptor filter,
      Map<String,String> providerParams,
      List<FilterParamDescriptor> filterParams ) {
    List<FilterParamDescriptor> aggregateParams = new ArrayList<>();
    if( providerParams != null ) {
      for( Map.Entry<String,String> param : providerParams.entrySet() ) {
        String name = param.getKey();
        if( !FILTER_PARAM.equalsIgnoreCase( name ) ) {
          aggregateParams.add( filter.createParam().name( name ).value( param.getValue() ) );
        }
      }
    }
    if( filterParams != null ) {
      aggregateParams.addAll( filterParams );
    }
    return aggregateParams;
  }

}

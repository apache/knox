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
package org.apache.knox.gateway.hadoopauth.deploy;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.hadoopauth.HadoopAuthMessages;
import org.apache.knox.gateway.hadoopauth.filter.HadoopAuthFilter;
import org.apache.knox.gateway.hadoopauth.filter.HadoopAuthPostFilter;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public class HadoopAuthDeploymentContributor extends ProviderDeploymentContributorBase {

  private static HadoopAuthMessages log = MessagesFactory.get( HadoopAuthMessages.class );

  private static final String HADOOPAUTH_FILTER_CLASSNAME = HadoopAuthFilter.class.getCanonicalName();
  private static final String HADOOPAUTH_POSTFILTER_CLASSNAME = HadoopAuthPostFilter.class.getCanonicalName();

  public static final String ROLE = "authentication";
  public static final String NAME = "HadoopAuth";
  private AliasService as;

  @Override
  public String getRole() {
    return ROLE;
  }

  @Override
  public String getName() {
    return NAME;
  }

  public void setAliasService(AliasService as) {
    this.as = as;
  }

  @Override
  public void initializeContribution(DeploymentContext context) {
    super.initializeContribution(context);
  }

  @Override
  public void contributeFilter(DeploymentContext context, Provider provider, Service service,
      ResourceDescriptor resource, List<FilterParamDescriptor> params) {
    String clusterName = context.getTopology().getName();

    List<String> aliases = new ArrayList<>();
    try {
      aliases = this.as.getAliasesForCluster(clusterName);
    } catch (AliasServiceException e) {
      log.aliasServiceException(e);
    }

    // blindly add all the provider params as filter init params
    if (params == null) {
      params = new ArrayList<>();
    }
    Map<String, String> providerParams = provider.getParams();
    for(Entry<String, String> entry : providerParams.entrySet()) {
      String key = entry.getKey().toLowerCase(Locale.ROOT);
      String value = null;
      if(aliases.contains(key)) {
        try {
          value = String.valueOf(this.as.getPasswordFromAliasForCluster(clusterName, key));
        } catch (AliasServiceException e) {
          log.unableToGetPassword(key, e);
        }
      } else {
        value = entry.getValue();
      }

      params.add( resource.createFilterParam().name( key ).value( value ) );
    }

    resource.addFilter().name( getName() ).role( getRole() ).impl(HADOOPAUTH_FILTER_CLASSNAME).params( params );
    resource.addFilter().name( "Post" + getName() ).role( getRole() ).impl(HADOOPAUTH_POSTFILTER_CLASSNAME).params( params );
  }
}

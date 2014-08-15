/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.hdfs;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.hdfs.dispatch.WebHdfsHaHttpClientDispatch;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NameNodeHaDispatchDeploymentContributor extends ProviderDeploymentContributorBase {

   private static final String ROLE = "dispatch";

   private static final String NAME = "ha-http-client";

   @Override
   public String getRole() {
      return ROLE;
   }

   @Override
   public String getName() {
      return NAME;
   }

   @Override
   public void contributeFilter(DeploymentContext context, Provider provider, Service service, ResourceDescriptor resource, List<FilterParamDescriptor> params) {
      if (params == null) {
         params = new ArrayList<FilterParamDescriptor>();
      }
      params.add(resource.createFilterParam().name(WebHdfsHaHttpClientDispatch.RESOURCE_ROLE_ATTRIBUTE).value(resource.role()));
      Map<String, String> providerParams = provider.getParams();
      for (Map.Entry<String, String> entry : providerParams.entrySet()) {
         params.add(resource.createFilterParam().name(entry.getKey().toLowerCase()).value(entry.getValue()));
      }
      resource.addFilter().name(getName()).role(getRole()).impl(WebHdfsHaHttpClientDispatch.class).params(params);
   }
}

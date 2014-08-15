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
package org.apache.hadoop.gateway.ha.deploy;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.ha.provider.HaDescriptor;
import org.apache.hadoop.gateway.ha.provider.HaServiceConfig;
import org.apache.hadoop.gateway.ha.provider.HaServletContextListener;
import org.apache.hadoop.gateway.ha.provider.impl.HaDescriptorFactory;
import org.apache.hadoop.gateway.ha.provider.impl.HaDescriptorManager;
import org.apache.hadoop.gateway.ha.provider.impl.i18n.HaMessages;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;
import org.jboss.shrinkwrap.api.asset.StringAsset;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class HaProviderDeploymentContributor extends ProviderDeploymentContributorBase {

   private static final String PROVIDER_ROLE_NAME = "ha";

   private static final String PROVIDER_IMPL_NAME = "HaProvider";

   private static final String HA_DESCRIPTOR_NAME = "ha.provider.descriptor";

   private static final HaMessages LOG = MessagesFactory.get(HaMessages.class);

   @Override
   public String getRole() {
      return PROVIDER_ROLE_NAME;
   }

   @Override
   public String getName() {
      return PROVIDER_IMPL_NAME;
   }

   @Override
   public void contributeProvider(DeploymentContext context, Provider provider) {
      Map<String, String> params = provider.getParams();
      HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
      for (Entry<String, String> entry : params.entrySet()) {
         HaServiceConfig config = HaDescriptorFactory.createServiceConfig(entry.getKey(), entry.getValue());
         descriptor.addServiceConfig(config);
      }
      StringWriter writer = new StringWriter();
      try {
         HaDescriptorManager.store(descriptor, writer);
      } catch (IOException e) {
         LOG.failedToWriteHaDescriptor(e);
      }
      String asset = writer.toString();
      context.getWebArchive().addAsWebInfResource(
            new StringAsset(asset),
            HaServletContextListener.DESCRIPTOR_DEFAULT_FILE_NAME);
      context.addDescriptor(HA_DESCRIPTOR_NAME, descriptor);
   }

   @Override
   public void finalizeContribution(DeploymentContext context) {
      if (context.getDescriptor(HA_DESCRIPTOR_NAME) != null) {
         // Tell the provider the location of the descriptor.
         // Doing this here instead of in 'contributeProvider' so that this ServletContextListener comes after the gateway services have been set.
         context.getWebAppDescriptor().createListener().listenerClass(HaServletContextListener.class.getName());
         context.getWebAppDescriptor().createContextParam()
               .paramName(HaServletContextListener.DESCRIPTOR_LOCATION_INIT_PARAM_NAME)
               .paramValue(HaServletContextListener.DESCRIPTOR_DEFAULT_LOCATION);
      }
   }

   @Override
   public void contributeFilter(DeploymentContext context, Provider provider, Service service, ResourceDescriptor resource, List<FilterParamDescriptor> params) {
      //no op
   }
}

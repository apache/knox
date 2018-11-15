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
package org.apache.knox.gateway.filter.rewrite.impl;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.knox.gateway.descriptor.FilterDescriptor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletContextListener;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter;
import org.apache.knox.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.jboss.shrinkwrap.api.asset.StringAsset;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

public class UrlRewriteDeploymentContributor extends
    ProviderDeploymentContributorBase {

  private static final String PROVIDER_ROLE_NAME = "rewrite";
  private static final String PROVIDER_IMPL_NAME = "url-rewrite";
  private static final String PARAM_SERVICE_ROLE = "service.role";
  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );

  @Override
  public String getRole() {
    return PROVIDER_ROLE_NAME;
  }

  @Override
  public String getName() {
    return PROVIDER_IMPL_NAME;
  }

  @Override
  public void initializeContribution(DeploymentContext context ) {
    context.addDescriptor( getRole(), UrlRewriteRulesDescriptorFactory.create() );
  }

  @Override
  public void contributeProvider(DeploymentContext context, Provider provider ) {
  }

  @Override
  public void finalizeContribution(DeploymentContext context ) {
    // Write the descriptor into the archive.
    UrlRewriteRulesDescriptor descriptor = context.getDescriptor( getRole() );
    StringWriter writer = new StringWriter();
    try {
      UrlRewriteRulesDescriptorFactory.store( descriptor, "xml", writer );
    } catch( IOException e ) {
      LOG.failedToWriteRulesDescriptor( e );
    }
    String asset = writer.toString();
    context.getWebArchive().addAsWebInfResource(
        new StringAsset( asset ),
        UrlRewriteServletContextListener.DESCRIPTOR_DEFAULT_FILE_NAME );

    // Tell the provider the location of the descriptor.
    context.getWebAppDescriptor().createListener().listenerClass( UrlRewriteServletContextListener.class.getName() );
    context.getWebAppDescriptor().createContextParam()
        .paramName( UrlRewriteServletContextListener.DESCRIPTOR_LOCATION_INIT_PARAM_NAME )
        .paramValue( UrlRewriteServletContextListener.DESCRIPTOR_DEFAULT_LOCATION );
//    ServletType<WebAppDescriptor> servlet = findServlet( context, context.getTopology().getName() );
//    servlet.createInitParam()
//        .paramName( UrlRewriteServletContextListener.DESCRIPTOR_LOCATION_INIT_PARAM_NAME )
//        .paramValue( DESCRIPTOR_FILE_NAME );
  }

  @Override
  public void contributeFilter(
      DeploymentContext context,
      Provider provider,
      Service service,
      ResourceDescriptor resource,
      List<FilterParamDescriptor> params ) {
    FilterDescriptor filterDescriptor = resource.addFilter();
    filterDescriptor.role( getRole() ).name( getName() ).impl( UrlRewriteServletFilter.class ).params( params );
    filterDescriptor.param().name(PARAM_SERVICE_ROLE).value(service.getRole());
  }

}

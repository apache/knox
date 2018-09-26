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
package org.apache.knox.gateway.hostmap.impl;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributor;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.hostmap.api.HostmapFunctionDescriptor;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

public class HostmapDeploymentContributor
    extends ProviderDeploymentContributorBase
    implements ProviderDeploymentContributor {

  public static final String PROVIDER_ROLE_NAME = HostmapFunctionDescriptor.FUNCTION_NAME;
  public static final String PROVIDER_IMPL_NAME = "static";
  private static final String REWRITE_ROLE_NAME = "rewrite";

  @Override
  public String getRole() {
    return PROVIDER_ROLE_NAME;
  }

  @Override
  public String getName() {
    return PROVIDER_IMPL_NAME;
  }

  // Write the provider init params to the hostmap.txt file.
  // Add the function to the rewrite descriptor providing the location of the hostmap.txt file.
  @Override
  public void contributeProvider( DeploymentContext context, Provider provider ) {
    if( provider.isEnabled() ) {
      UrlRewriteRulesDescriptor rules = context.getDescriptor( REWRITE_ROLE_NAME );
      if( rules != null ) {
        HostmapFunctionDescriptor func = rules.addFunction( HostmapFunctionDescriptor.FUNCTION_NAME );
        if( func != null ) {
          Asset asset = createAsset( provider );
          context.getWebArchive().addAsWebInfResource(
              asset, HostmapFunctionProcessor.DESCRIPTOR_DEFAULT_FILE_NAME );
          func.config( HostmapFunctionProcessor.DESCRIPTOR_DEFAULT_LOCATION );
        }
      }
    }
  }

  private Asset createAsset( Provider provider ) {
    StringWriter buffer = new StringWriter();
    PrintWriter writer = new PrintWriter( buffer );
    for( Map.Entry<String, String> entry : provider.getParams().entrySet() ) {
      String externalHosts = entry.getKey();
      String internalHosts = entry.getValue();
      writer.print( externalHosts );
      writer.print( "=" );
      writer.println( internalHosts );
    }
    writer.close();
    String string = buffer.toString();
    Asset asset = new StringAsset( string );
    return asset;
  }

  @Override
  public void contributeFilter(
      DeploymentContext context,
      Provider provider,
      Service service,
      ResourceDescriptor resource,
      List<FilterParamDescriptor> params ) {
    // NoOp.
  }

}

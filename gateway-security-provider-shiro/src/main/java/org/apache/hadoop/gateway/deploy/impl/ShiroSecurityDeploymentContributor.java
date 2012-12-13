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

import java.io.StringWriter;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.DeploymentContributorBase;
import org.apache.hadoop.gateway.topology.Provider;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;
import org.jboss.shrinkwrap.descriptor.api.webcommon30.ServletType;

public class ShiroSecurityDeploymentContributor extends DeploymentContributorBase {

  @Override
  public void contribute(DeploymentContext context) {

    ServletType<WebAppDescriptor> servlet = findServlet( context, context.getTopology().getName() );
    Provider provider = context.getTopology().getProvider("authentication");
    if (provider != null && provider.isEnabled()) {
      StringWriter writer = new StringWriter();
      // write the provider specific config out to the war for cluster specific config
      String config = provider.getParams().get("config");
      if (config != null) {
        context.getWebArchive().addAsWebInfResource(
            new StringAsset( config ),
            "shiro.ini" );
      }
//      Map<String, String> params = provider.getParams();
//      servlet.createInitParam()
//          .paramName( "contextConfigLocation" )
//          .paramValue( params.get("contextConfigLocation") );
      WebAppDescriptor wad = context.getWebAppDescriptor();
      wad.createListener().listenerClass("org.apache.shiro.web.env.EnvironmentLoaderListener");
    }
  }

}

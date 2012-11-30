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

import org.apache.hadoop.gateway.deploy.ClusterDeploymentContext;
import org.apache.hadoop.gateway.deploy.ClusterDeploymentContributor;
import org.apache.hadoop.gateway.deploy.ClusterDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.ClusterDescriptorFactory;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;
import org.jboss.shrinkwrap.descriptor.api.webcommon30.ServletType;

import java.io.IOException;
import java.io.StringWriter;

public class FinalizeClusterDeploymentContributor extends ClusterDeploymentContributorBase implements ClusterDeploymentContributor {

  private static final String GATEWAY_CLUSTER_DESCRIPTOR_LOCATION = "gateway.xml";
  private static final String GATEWAY_CLUSTER_DESCRIPTOR_LOCATION_PARAM = "gatewayClusterDescriptorLocation";

  @Override
  public void contribute( ClusterDeploymentContext context ) {
    try {
      // Write the gateway cluster descriptor (gateway.xml) into the war.
      StringWriter writer = new StringWriter();
      ClusterDescriptorFactory.store( context.getClusterDescriptor(), "xml", writer );
      context.getWebArchive().addAsWebInfResource(
          new StringAsset( writer.toString() ), GATEWAY_CLUSTER_DESCRIPTOR_LOCATION );

      // Set the location of the gateway cluster descriptor as a servlet init param.
      ServletType<WebAppDescriptor> servlet = findServlet( context, context.getClusterTopology().getName() );
      servlet.createInitParam()
          .paramName( GATEWAY_CLUSTER_DESCRIPTOR_LOCATION_PARAM )
          .paramValue( GATEWAY_CLUSTER_DESCRIPTOR_LOCATION );

      // Write the web.xml into the war.
      Asset webXmlAsset = new StringAsset( context.getWebAppDescriptor().exportAsString() );
      context.getWebArchive().setWebXML( webXmlAsset );

    } catch ( IOException e ) {
      throw new RuntimeException( e );
    }
  }

}

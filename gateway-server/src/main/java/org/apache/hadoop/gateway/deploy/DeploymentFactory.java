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
package org.apache.hadoop.gateway.deploy;

import org.apache.hadoop.gateway.GatewayServlet;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.deploy.impl.DeploymentContextImpl;
import org.apache.hadoop.gateway.descriptor.GatewayDescriptor;
import org.apache.hadoop.gateway.descriptor.GatewayDescriptorFactory;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.topology.Service;
import org.apache.hadoop.gateway.topology.Topology;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;
import org.jboss.shrinkwrap.descriptor.api.webcommon30.ServletType;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

public abstract class DeploymentFactory {

  private static Set<DeploymentContributor> CONTRIBUTORS = loadContributors();

  public static WebArchive createDeployment( GatewayConfig config, Topology topology ) {
    WebArchive webArchive = ShrinkWrap.create( WebArchive.class, topology.getName() );
    WebAppDescriptor webAppDesc = Descriptors.create( WebAppDescriptor.class );
    GatewayDescriptor gateway = GatewayDescriptorFactory.create();

    DeploymentContext context
        = new DeploymentContextImpl( config, topology, gateway, webArchive, webAppDesc );

    initialize( context );
    contributePluginDescriptors( context );
    finalize( context );

    return webArchive;
  }
  
  private static void initialize( DeploymentContext context ) {
    WebAppDescriptor wad = context.getWebAppDescriptor();
    String servlet = context.getTopology().getName();
    wad.createServlet().servletName( servlet ).servletClass( GatewayServlet.class.getName() );
    wad.createServletMapping().servletName( servlet ).urlPattern( "/*" );
  }
  
  private static void contributePluginDescriptors( DeploymentContext context ) {
    GatewayDescriptor gatewayDescriptor = context.getGatewayDescriptor();
    Topology topology = context.getTopology();
    for( Service service : topology.getServices() ) {
      ResourceDescriptorFactory factory = context.getResourceDescriptorFactory( service );
      if( factory != null ) {
        List<ResourceDescriptor> descriptors = factory.createResourceDescriptors( context, service );
        for( ResourceDescriptor descriptor : descriptors ) {
          gatewayDescriptor.addResource( descriptor );
        }
      }
    }
  }
  
  private static void finalize( DeploymentContext context ) {
    try {
      // Write the gateway descriptor (gateway.xml) into the war.
      StringWriter writer = new StringWriter();
      GatewayDescriptorFactory.store( context.getGatewayDescriptor(), "xml", writer );
      context.getWebArchive().addAsWebInfResource(
          new StringAsset( writer.toString() ),
          GatewayServlet.GATEWAY_DESCRIPTOR_LOCATION_DEFAULT );

      // Set the location of the gateway descriptor as a servlet init param.
      ServletType<WebAppDescriptor> servlet = findServlet( context, context.getTopology().getName() );
      servlet.createInitParam()
          .paramName( GatewayServlet.GATEWAY_DESCRIPTOR_LOCATION_PARAM )
          .paramValue( GatewayServlet.GATEWAY_DESCRIPTOR_LOCATION_DEFAULT );
      
      invokeLoadedContributors(context);

      // Write the web.xml into the war.
      Asset webXmlAsset = new StringAsset( context.getWebAppDescriptor().exportAsString() );
      context.getWebArchive().setWebXML( webXmlAsset );

    } catch ( IOException e ) {
      throw new RuntimeException( e );
    }
  }

  public static ServletType<WebAppDescriptor> findServlet( DeploymentContext context, String name ) {
    List<ServletType<WebAppDescriptor>> servlets = context.getWebAppDescriptor().getAllServlet();
    for( ServletType<WebAppDescriptor> servlet : servlets ) {
      if( name.equals( servlet.getServletName() ) ) {
        return servlet;
      }
    }
    return null;
  }  
  
  private static void invokeLoadedContributors( DeploymentContext context ) {
    for( DeploymentContributor contributor : CONTRIBUTORS ) {
      try {
        contributor.contribute( context );
      } catch( Exception e ) {
        //TODO: I18N message.
        e.printStackTrace();
      }
    }
  }

  private static Set<DeploymentContributor> loadContributors() {
    Set<DeploymentContributor> set = new HashSet<DeploymentContributor>();
    ServiceLoader<DeploymentContributor> loader = ServiceLoader.load( DeploymentContributor.class );
    Iterator<DeploymentContributor> contributors = loader.iterator();
    while( contributors.hasNext() ) {
      DeploymentContributor contributor = contributors.next();
      set.add( contributor );
    }
    return Collections.unmodifiableSet( set );
  }

}

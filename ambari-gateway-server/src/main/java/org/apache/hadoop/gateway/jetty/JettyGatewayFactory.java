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
package org.apache.hadoop.gateway.jetty;

import org.apache.hadoop.gateway.GatewayFactory;
import org.apache.hadoop.gateway.GatewayFilter;
import org.apache.hadoop.gateway.GatewayServlet;
import org.apache.hadoop.gateway.descriptor.ClusterDescriptor;
import org.apache.hadoop.gateway.util.Urls;
import org.apache.shiro.web.env.EnvironmentLoaderListener;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.net.URISyntaxException;

public class JettyGatewayFactory {

  public static ServletContextHandler create( String gatewayPath, ClusterDescriptor gatewayConfig ) throws URISyntaxException {
    gatewayPath = Urls.ensureLeadingSlash( gatewayPath );
    ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
    context.setContextPath( gatewayPath );

    //context.setInitParameter( "shiroConfigLocations", "shiro.ini" ); //gatewayConfig.get( GatewayConfig.SHIRO_CONFIG_FILE ) );
    context.addEventListener( new EnvironmentLoaderListener() ); // For Shiro bootstrapping.

    GatewayFilter filter = GatewayFactory.create( gatewayConfig );
    GatewayServlet servlet = new GatewayServlet( filter );
    ServletHolder holder = new ServletHolder( servlet );

    context.addServlet( holder, "/*" );
    return context;
  }

  public static ServletContextHandler create( String gatewayPath ) throws URISyntaxException {
    gatewayPath = Urls.ensureLeadingSlash( gatewayPath );
    ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
    context.setContextPath( gatewayPath );
    GatewayServlet servlet = new GatewayServlet();
    ServletHolder holder = new ServletHolder( servlet );
    context.addServlet( holder, "/*" );
    return context;
  }

}

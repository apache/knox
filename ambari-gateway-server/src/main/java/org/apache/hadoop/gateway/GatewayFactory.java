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
package org.apache.hadoop.gateway;

import org.apache.hadoop.gateway.config.Config;

import javax.servlet.Filter;
import java.net.URISyntaxException;

/**
 *
 */
public class GatewayFactory {

//  private static FilterHolder createFilter( ServiceConfig serviceConfig, String filterName ) {
//    FilterConfig filterConfig = serviceConfig.filters.pick( filterName );
//    FilterHolder filterHolder = new FilterHolder( Holder.Source.EMBEDDED );
//    filterHolder.setName( filterName );
//    filterHolder.setClassName( filterConfig.pick( "class" ) );
//    for( Map.Entry<String,String> param : filterConfig.entrySet() ) {
//      filterHolder.setInitParameter( param.getKey(), param.getValue() );
//    }
//    return filterHolder;
//  }
//
//  private static ServletHolder createProxy( String proxyName, ServiceConfig serviceConfig ) {
//    ServletHolder servletHolder = new ServletHolder( Holder.Source.EMBEDDED );
//    servletHolder.setName( proxyName );
//    String servletClass = serviceConfig.pick( "class" );
//    if( servletClass == null ) {
//      servletClass = UrlConnectionPivot.class.getName();
//    }
//    servletHolder.setClassName( servletClass );
//    for( Map.Entry<String,String> param : serviceConfig.entrySet() ) {
//      servletHolder.setInitParameter( param.getKey(), param.getValue() );
//    }
//    return servletHolder;
//  }
//
//  private static Handler createService( GatewayConfig gatewayConfig, ClusterConfig clusterConfig, String proxyName ) {
//    ServiceConfig serviceConfig = clusterConfig.proxies.pick( proxyName );
//    ServletContextHandler proxyContext = new ServletContextHandler( ServletContextHandler.SESSIONS );
//    String gatewayPath = Urls.ensureLeadingSlash( gatewayConfig.pick( "path" ) );
//    String clusterPath = gatewayPath + Urls.ensureLeadingSlash( clusterConfig.pick( "path" ) );
//    proxyContext.setContextPath( clusterPath );
//    String proxyPath = Urls.ensureLeadingSlash( serviceConfig.pick( "path" ) );
//    for( String filterName : serviceConfig.filters.keySet() ) {
//      proxyContext.addFilter(
//          createFilter( serviceConfig, filterName ),
//          proxyPath,
//          EnumSet.of( DispatcherType.REQUEST ) );
//    }
//    System.out.println( "Creating proxy on " + proxyPath );
//    proxyContext.addServlet( createProxy( proxyName, serviceConfig ), proxyPath );
//    return proxyContext;
//  }
//
//  private static Handler createCluster( GatewayConfig gatewayConfig, String clusterName ) {
//    ClusterConfig clusterConfig = gatewayConfig.services.pick( clusterName );
//    ContextHandlerCollection services = new ContextHandlerCollection();
//    for( String proxyName : clusterConfig.proxies.keySet() ) {
//      services.addHandler( createService( gatewayConfig, clusterConfig, proxyName ) );
//    }
//    return services;
//  }

  private static void addFilter( GatewayFilter gateway, Config filterConfig ) throws URISyntaxException {
    String source = filterConfig.get( "source" );
    String name = filterConfig.get( "name" );
    String clazz = filterConfig.get( "class" );
    gateway.addFilter( source, name, clazz, filterConfig );
  }

  private static void addService( GatewayFilter gateway, Config serviceConfig ) throws URISyntaxException {
    for( Config filterConfig : serviceConfig.getChildren().values() ) {
      addFilter( gateway, filterConfig );
    }
  }

  public static GatewayFilter create( Config gatewayConfig ) throws URISyntaxException {
    GatewayFilter gateway = new GatewayFilter();
    for( Config service : gatewayConfig.getChildren().values() ) {
      addService( gateway, service );
    }
    return gateway;
  }

}

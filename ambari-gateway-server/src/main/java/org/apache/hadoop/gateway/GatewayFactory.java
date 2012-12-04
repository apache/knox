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

import org.apache.hadoop.gateway.descriptor.ClusterDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterFilterDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterFilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterResourceDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterResourceParamDescriptor;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class GatewayFactory {

//  public static GatewayFilter create( Config gatewayConfig ) throws URISyntaxException {
//    GatewayFilter gateway = new GatewayFilter();
//    for( Config service : gatewayConfig.getChildren().values() ) {
//      addService( gateway, service );
//    }
//    return gateway;
//  }
//
//  private static void addService( GatewayFilter gateway, Config serviceConfig ) throws URISyntaxException {
//    for( Config filterConfig : serviceConfig.getChildren().values() ) {
//      addFilter( gateway, filterConfig );
//    }
//  }
//
//  private static void addFilter( GatewayFilter gateway, Config filterConfig ) throws URISyntaxException {
//    String source = filterConfig.get( "source" );
//    String name = filterConfig.get( "name" );
//    String clazz = filterConfig.get( "class" );
//    gateway.addFilter( source, name, clazz, filterConfig );
//  }

  public static GatewayFilter create( ClusterDescriptor descriptor ) throws URISyntaxException {
    GatewayFilter filter = new GatewayFilter();
    for( ClusterResourceDescriptor resource : descriptor.resources() ) {
      addResource( filter, resource );
    }
    return filter;
  }

  private static void addResource( GatewayFilter gateway, ClusterResourceDescriptor resource ) throws URISyntaxException {
    for( ClusterFilterDescriptor filter : resource.filters() ) {
      addFilter( gateway, filter );
    }
  }

  private static void addFilter( GatewayFilter gateway, ClusterFilterDescriptor filter ) throws URISyntaxException {
    gateway.addFilter( filter.up().source(), filter.role(), filter.impl(), createParams( filter ) );
  }

  private static Map<String, String> createParams( ClusterFilterDescriptor filter ) {
    Map<String, String> paramMap = new HashMap<String, String>();
    ClusterResourceDescriptor resource = filter.up();
    ClusterDescriptor cluster = resource.up();
    for( ClusterParamDescriptor param : cluster.params() ) {
      paramMap.put( param.name(), param.value() );
    }
    for( ClusterResourceParamDescriptor param : resource.params() ) {
      paramMap.put( param.name(), param.value() );
    }
    paramMap.put( "source", resource.source() );
    paramMap.put( "target", resource.target() );
    List<ClusterFilterParamDescriptor> paramList = filter.params();
    for( ClusterFilterParamDescriptor param : paramList ) {
      paramMap.put( param.name(), param.value() );
    }
    return paramMap;
  }

}

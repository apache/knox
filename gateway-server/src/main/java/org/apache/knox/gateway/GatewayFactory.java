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
package org.apache.knox.gateway;

import org.apache.knox.gateway.descriptor.FilterDescriptor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptor;
import org.apache.knox.gateway.descriptor.GatewayParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.descriptor.ResourceParamDescriptor;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
//    String source = filterConfig.get( "pattern" );
//    String name = filterConfig.get( "name" );
//    String clazz = filterConfig.get( "class" );
//    gateway.addFilter( source, name, clazz, filterConfig );
//  }

  public static GatewayFilter create( GatewayDescriptor descriptor ) throws URISyntaxException {
    GatewayFilter filter = new GatewayFilter();
    for( ResourceDescriptor resource : descriptor.resources() ) {
      addResource( filter, resource );
    }
    return filter;
  }

  private static void addResource( GatewayFilter gateway, ResourceDescriptor resource ) throws URISyntaxException {
    for( FilterDescriptor filter : resource.filters() ) {
      addFilter( gateway, filter );
    }
  }

  private static void addFilter( GatewayFilter gateway, FilterDescriptor filter ) throws URISyntaxException {
    String name = filter.name();
    if( name == null ) {
      name = filter.role();
    }
    gateway.addFilter( filter.up().pattern(), name, filter.impl(), createParams( filter ), filter.up().role() );
  }

  private static Map<String, String> createParams( FilterDescriptor filter ) {
    Map<String, String> paramMap = new HashMap<>();
    ResourceDescriptor resource = filter.up();
    GatewayDescriptor gateway = resource.up();
    for( GatewayParamDescriptor param : gateway.params() ) {
      paramMap.put( param.name(), param.value() );
    }
    for( ResourceParamDescriptor param : resource.params() ) {
      paramMap.put( param.name(), param.value() );
    }
    //TODO: Should all elements of the resource and gateway descriptor somehow be added to the filter params?
    //TODO: Should we use some composite params object instead of copying all these name value pairs?
    paramMap.put( "pattern", resource.pattern() );
    List<FilterParamDescriptor> paramList = filter.params();
    for( FilterParamDescriptor param : paramList ) {
      paramMap.put( param.name(), param.value() );
    }
    return paramMap;
  }

}

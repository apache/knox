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

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.knox.gateway.hostmap.api.HostmapFunctionDescriptor;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.hostmap.FileBasedHostMapper;
import org.apache.knox.gateway.services.hostmap.HostMapper;
import org.apache.knox.gateway.services.hostmap.HostMapperService;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class HostmapFunctionProcessor
    implements UrlRewriteFunctionProcessor<HostmapFunctionDescriptor> {

  public static final String DESCRIPTOR_DEFAULT_FILE_NAME = "hostmap.txt";
  public static final String DESCRIPTOR_DEFAULT_LOCATION = "/WEB-INF/" + DESCRIPTOR_DEFAULT_FILE_NAME;

  private HostMapperService hostMapperService;
  private HostMapper hostMapper = null;
  private String clusterName;

  @Override
  public String name() {
    return HostmapFunctionDescriptor.FUNCTION_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, HostmapFunctionDescriptor descriptor ) throws Exception {
    URL url = environment.getResource( DESCRIPTOR_DEFAULT_LOCATION );
    hostMapper = new FileBasedHostMapper( url );
    clusterName = environment.getAttribute(  GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE );
    GatewayServices services = environment.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE );
    if( clusterName != null && services != null ) {
      hostMapperService = services.getService( GatewayServices.HOST_MAPPING_SERVICE );
      if( hostMapperService != null ) {
        hostMapperService.registerHostMapperForCluster( clusterName, hostMapper );
      }
    }
  }

  @Override
  public void destroy() throws Exception {
    if( hostMapperService != null && clusterName != null ) {
      hostMapperService.removeHostMapperForCluster( clusterName );
    }
  }

  @Override
  public List<String> resolve( UrlRewriteContext context, List<String> parameters ) throws Exception {
    List<String> result = null;
    if( parameters != null ) {
      result = new ArrayList<>( parameters.size() );
      for( String parameter : parameters ) {
        switch( context.getDirection() ) {
          case IN:
            parameter = hostMapper.resolveInboundHostName( parameter );
            break;
          case OUT:
            parameter = hostMapper.resolveOutboundHostName( parameter );
            break;
        }
        result.add( parameter );
      }
//    System.out.println( "HOSTMAP: " + parameter + "->" + value );
    }
    return result;
  }

}


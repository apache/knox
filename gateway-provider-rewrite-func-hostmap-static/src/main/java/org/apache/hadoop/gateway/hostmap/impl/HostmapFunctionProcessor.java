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
package org.apache.hadoop.gateway.hostmap.impl;

import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.hadoop.gateway.hostmap.api.HostmapFunctionDescriptor;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.hostmap.FileBasedHostMapper;
import org.apache.hadoop.gateway.services.hostmap.HostMappingService;
import org.apache.hadoop.gateway.services.security.CryptoService;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HostmapFunctionProcessor
    implements UrlRewriteFunctionProcessor<HostmapFunctionDescriptor> {

  public static final String DESCRIPTOR_DEFAULT_FILE_NAME = "hostmap.txt";
  public static final String DESCRIPTOR_DEFAULT_LOCATION = "/WEB-INF/" + DESCRIPTOR_DEFAULT_FILE_NAME;
  
  private FileBasedHostMapper hostMapper = null;
  private String clusterName;
  private HostMappingService hostMappingService;

  @Override
  public String name() {
    return HostmapFunctionDescriptor.FUNCTION_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, HostmapFunctionDescriptor descriptor ) throws Exception {
    URL url = environment.getResource( DESCRIPTOR_DEFAULT_LOCATION );
    List<String> names = environment.resolve( "cluster.name" );
    if (names != null && names.size() > 0) {
      clusterName = names.get( 0 );
    }
    hostMapper = new FileBasedHostMapper(clusterName, url);

    GatewayServices services = environment.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    if (clusterName != null && services != null) {
      hostMappingService = (HostMappingService) services.getService(GatewayServices.HOST_MAPPING_SERVICE);
      if (hostMappingService != null) {
        hostMappingService.registerHostMapperForCluster(clusterName, hostMapper);
      }
    }
  }

  @Override
  public void destroy() throws Exception {
    // need to remove the host mapper for the cluster on undeploy
    if (clusterName != null && hostMappingService != null) {
      hostMappingService.removeHostMapperForCluster(clusterName);
    }
    
  }

  @Override
  public String resolve( UrlRewriteContext context, String parameter ) throws Exception {
    String value;
    switch( context.getDirection() ) {
      case IN:
        value = hostMapper.resolveInboundHostName(parameter);
        break;
      case OUT:
        value = hostMapper.resolveOutboundHostName(parameter);
        break;
      default:
        value = null;
    }
    if( value == null ) {
      value = parameter;
    }
//    System.out.println( "HOSTMAP: " + parameter + "->" + value );
    return value;
  }

}


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
package org.apache.knox.gateway.svcregfunc.impl;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.hostmap.HostMapper;
import org.apache.knox.gateway.services.hostmap.HostMapperService;
import org.apache.knox.gateway.svcregfunc.api.ServiceMappedHostFunctionDescriptor;
import org.apache.knox.gateway.util.urltemplate.Host;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;

import java.util.ArrayList;
import java.util.List;

public class ServiceMappedHostFunctionProcessor
    extends ServiceRegistryFunctionProcessorBase<ServiceMappedHostFunctionDescriptor>
    implements UrlRewriteFunctionProcessor<ServiceMappedHostFunctionDescriptor> {

  private HostMapper hostmap;

  @Override
  public String name() {
    return ServiceMappedHostFunctionDescriptor.FUNCTION_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, ServiceMappedHostFunctionDescriptor descriptor ) throws Exception {
    super.initialize( environment, descriptor );
    HostMapperService hostmapService = services().getService( ServiceType.HOST_MAPPING_SERVICE );
    if( hostmapService != null ) {
      hostmap = hostmapService.getHostMapper( cluster() );
    }
  }

  @Override
  public List<String> resolve( UrlRewriteContext context, List<String> parameters ) throws Exception {
    List<String> results = null;
    if( parameters != null ) {
      results = new ArrayList<>( parameters.size() );
      for( String parameter : parameters ) {
        String url = lookupServiceUrl( parameter );
        if( url != null ) {
          Template template = Parser.parseLiteral( url );
          Host host = template.getHost();
          if( host != null ) {
            String hostStr = host.getFirstValue().getPattern();
            if( hostmap != null ) {
              switch( context.getDirection() ) {
                case IN:
                  parameter = hostmap.resolveInboundHostName( hostStr );
                  break;
                case OUT:
                  parameter = hostmap.resolveOutboundHostName( hostStr );
                  break;
              }
            } else {
              parameter = hostStr;
            }
          }
        }
        results.add( parameter );
      }
    }
    return results;
  }

}


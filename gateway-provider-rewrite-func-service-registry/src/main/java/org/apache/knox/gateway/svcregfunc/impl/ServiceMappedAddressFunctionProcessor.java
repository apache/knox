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
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.hostmap.HostMapper;
import org.apache.knox.gateway.services.hostmap.HostMapperService;
import org.apache.knox.gateway.svcregfunc.api.ServiceMappedAddressFunctionDescriptor;
import org.apache.knox.gateway.util.urltemplate.Host;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Port;
import org.apache.knox.gateway.util.urltemplate.Template;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriter;

import java.util.ArrayList;
import java.util.List;

public class ServiceMappedAddressFunctionProcessor
    extends ServiceRegistryFunctionProcessorBase<ServiceMappedAddressFunctionDescriptor>
    implements UrlRewriteFunctionProcessor<ServiceMappedAddressFunctionDescriptor> {

  private HostMapper hostmap = null;

  @Override
  public String name() {
    return ServiceMappedAddressFunctionDescriptor.FUNCTION_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, ServiceMappedAddressFunctionDescriptor descriptor ) throws Exception {
    super.initialize( environment, descriptor );
    HostMapperService hostmapService = services().getService( GatewayServices.HOST_MAPPING_SERVICE );
    if( hostmapService != null ) {
      hostmap = hostmapService.getHostMapper( cluster() );
    }
  }

  @Override
  public List<String> resolve( UrlRewriteContext context, List<String> parameters ) throws Exception {
    List<String> results = null;
    if( parameters != null ) {
      UrlRewriter.Direction direction = context.getDirection();
      results = new ArrayList<>( parameters.size() );
      for( String parameter : parameters ) {
        parameter = resolve( direction, parameter );
        results.add( parameter );
      }
    }
    return results;
  }

  public String resolve( UrlRewriter.Direction direction, String parameter ) throws Exception {
    String addr = parameter;
    String url = lookupServiceUrl( parameter );
    if( url != null ) {
      Template template = Parser.parseLiteral( url );
      Host host = template.getHost();
      String hostStr = null;
      if( host != null ) {
        hostStr = host.getFirstValue().getPattern();
        if( hostmap != null ) {
          switch( direction ) {
            case IN:
              hostStr = hostmap.resolveInboundHostName( hostStr );
              break;
            case OUT:
              hostStr = hostmap.resolveOutboundHostName( hostStr );
              break;
          }
        }
      }

      Port port = template.getPort();
      String portStr = null;
      if( port != null ) {
        portStr = port.getFirstValue().getPattern();
      }

      if( hostStr != null && portStr != null ) {
        addr = hostStr + ":" + portStr;
      } else if( host != null && port == null ) {
        addr = hostStr;
      } else if( host == null && port != null ) {
        addr = ":" + portStr;
      }
    }
    return addr;
  }

}


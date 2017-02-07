/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.gateway.filter;

import java.io.IOException;
import java.util.Collection;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.topology.TopologyService;
import org.apache.hadoop.gateway.topology.Topology;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;

public class DefaultTopologyHandler extends HandlerWrapper {

  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);

  private GatewayConfig config;
  private GatewayServices services;
  private String staticRedirectContext = null;

  public DefaultTopologyHandler( GatewayConfig config, GatewayServices services, Handler delegate ) {
    if( config == null ) {
      throw new IllegalArgumentException( "config==null" );
    }
    if( services == null ) {
      throw new IllegalArgumentException( "services==null" );
    }
    this.config = config;
    this.services = services;
    String defaultTopologyName = config.getDefaultTopologyName();
    if( defaultTopologyName != null ) {
      staticRedirectContext = config.getDefaultAppRedirectPath();
      if( staticRedirectContext != null && staticRedirectContext.trim().isEmpty() ) {
        staticRedirectContext = null;
      }
    }
    if( staticRedirectContext != null ) {
      LOG.defaultTopologySetup( defaultTopologyName, staticRedirectContext );
    }
    setHandler( delegate );
  }

  @Override
  public void handle( String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response ) throws IOException, ServletException {
    if( !baseRequest.isHandled() ) {
      String redirectContext = staticRedirectContext;
      if( redirectContext == null ) {
        TopologyService topologies = services.getService( GatewayServices.TOPOLOGY_SERVICE );
        if( topologies != null ) {
          Collection<Topology> candidates = topologies.getTopologies();
          if( candidates != null && candidates.size() == 1 ) {
            Topology topology = candidates.iterator().next();
            redirectContext = "/" + config.getGatewayPath() + "/" + topology.getName();
          }
        }
      }
      if( redirectContext != null ) {
        String newTarget = redirectContext + target;
        ForwardedRequest newRequest = new ForwardedRequest( request, newTarget );
        LOG.defaultTopologyForward( target, newTarget );
        super.handle( newTarget, baseRequest, newRequest, response );
      }
    }
  }

  private static class ForwardedRequest extends HttpServletRequestWrapper {

    private String contextPath;

    public ForwardedRequest( HttpServletRequest request, String contextPath ) {
      super( request );
      this.contextPath = contextPath;
    }

    public String getContextPath() {
      return contextPath;
    }

  }

}

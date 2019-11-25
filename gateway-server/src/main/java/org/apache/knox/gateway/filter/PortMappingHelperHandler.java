/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.filter;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * This is a helper handler that adjusts the "target" patch
 * of the request when Topology Port Mapping feature is
 * enabled. See KNOX-928.
 * <p>
 * This class also handles the Default Topology Feature
 * where, any one of the topologies can be set to "default"
 * and can listen on the standard Knox port (8443) and
 * will not need /gateway/{topology} context.
 * Basically Topology Port Mapping for standard port.
 * Backwards compatible to Default Topology Feature.
 */
public class PortMappingHelperHandler extends HandlerWrapper {
  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);

  private final GatewayConfig config;
  private final String defaultTopologyRedirectContext;

  public PortMappingHelperHandler(final GatewayConfig config) {
    this.config = config;
    this.defaultTopologyRedirectContext = getDefaultTopologyRedirectContext(config);
  }

  /**
   * Set up context for default topology feature.
   * @param config GatewayConfig object to read from
   * @return default topology redirect context as a string
   */
  private String getDefaultTopologyRedirectContext(final GatewayConfig config) {
    final String defaultTopologyName = config.getDefaultTopologyName();

    // default topology feature can also be enabled using port mapping feature
    // config e.g. gateway.port.mapping.{defaultTopologyName}
    String defaultTopologyRedirectContext = null;
    if(defaultTopologyName == null &&
           config.getGatewayPortMappings().containsValue(config.getGatewayPort())) {
      for(final Map.Entry<String, Integer> entry: config.getGatewayPortMappings().entrySet()) {
        if(entry.getValue().equals(config.getGatewayPort())) {
          defaultTopologyRedirectContext = "/" + config.getGatewayPath() + "/" + entry.getKey();
          break;
        }
      }
    }

    if (defaultTopologyName != null) {
      defaultTopologyRedirectContext = config.getDefaultAppRedirectPath();
      if (defaultTopologyRedirectContext != null
          && defaultTopologyRedirectContext.trim().isEmpty()) {
        defaultTopologyRedirectContext = null;
      }
    }
    if (defaultTopologyRedirectContext != null) {
      LOG.defaultTopologySetup(defaultTopologyName, defaultTopologyRedirectContext);
    }
    return defaultTopologyRedirectContext;
  }

  @Override
  public void handle(final String target, final Request baseRequest,
      final HttpServletRequest request, final HttpServletResponse response)
      throws IOException, ServletException {
    final String baseURI = baseRequest.getRequestURI();
    final int port = baseRequest.getLocalPort();

    if (config.isGatewayPortMappingEnabled()
            && config.getGatewayPortMappings().containsValue(port)) {
      // If Port Mapping feature enabled
      handlePortMapping(target, baseRequest, request, response, port);
    } else if (defaultTopologyRedirectContext != null &&
                 !baseURI.startsWith("/" + config.getGatewayPath())) {
      //Backwards compatibility for default topology feature
      handleDefaultTopologyMapping(target, baseRequest, request, response);
    } else {
      // case where topology port mapping is not enabled (or improperly configured)
      // and no default topology is configured
      super.handle(target, baseRequest, request, response);
    }
  }

  private void handlePortMapping(final String target, final Request baseRequest,
                                 final HttpServletRequest request,
                                 final HttpServletResponse response, final int port)
      throws IOException, ServletException {
    final String topologyName = config.getGatewayPortMappings().entrySet()
                                    .stream()
                                    .filter(e -> e.getValue().equals(port))
                                    .map(Map.Entry::getKey)
                                    .findFirst()
                                    .orElse(null);
    final String gatewayTopologyContext = "/" + config.getGatewayPath() + "/" + topologyName;
    String newTarget = target;

    if(!target.contains(gatewayTopologyContext)) {
      newTarget = gatewayTopologyContext + target;
    }

    // if the request does not contain /{gatewayName}/{topologyName}
    if(!baseRequest.getRequestURI().contains(gatewayTopologyContext)) {
      RequestUpdateHandler.ForwardedRequest newRequest = new RequestUpdateHandler.ForwardedRequest(
          request, gatewayTopologyContext);

      baseRequest.setPathInfo(gatewayTopologyContext + baseRequest.getPathInfo());
      baseRequest.setURIPathQuery(gatewayTopologyContext + baseRequest.getRequestURI());

      LOG.topologyPortMappingUpdateRequest(target, newTarget);
      super.handle(newTarget, baseRequest, newRequest, response);
    } else {
      super.handle(newTarget, baseRequest, request, response);
    }
  }

  private void handleDefaultTopologyMapping(final String target, final Request baseRequest,
                                            final HttpServletRequest request,
                                            final HttpServletResponse response)
      throws IOException, ServletException {
    RequestUpdateHandler.ForwardedRequest newRequest = new RequestUpdateHandler.ForwardedRequest(
        request, defaultTopologyRedirectContext);

    final String newTarget = defaultTopologyRedirectContext + target;
    LOG.defaultTopologyForward(target, newTarget);
    super.handle(newTarget, baseRequest, newRequest, response);
  }
}

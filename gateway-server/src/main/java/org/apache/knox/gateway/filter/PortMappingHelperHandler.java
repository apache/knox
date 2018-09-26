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

import org.apache.commons.lang.StringUtils;
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
 * This is a helper handler that adjusts the "target" patch of the request.
 * Used when Topology Port Mapping feature is used.
 * See KNOX-928
 * <p>
 * This class also handles the Default Topology Feature
 * where, any one of the topologies can be set to "default"
 * and can listen on the standard Knox port (8443) and
 * will not need /gateway/{topology} context.
 * Basically Topology Port Mapping for standard port.
 * Backwards compatible to Default Topology Feature.
 *
 */
public class PortMappingHelperHandler extends HandlerWrapper {

  private static final GatewayMessages LOG = MessagesFactory
      .get(GatewayMessages.class);

  final GatewayConfig config;

  private String defaultTopologyRedirectContext = null;

  public PortMappingHelperHandler(final GatewayConfig config) {

    this.config = config;
    //Set up context for default topology feature.
    String defaultTopologyName = config.getDefaultTopologyName();

    // default topology feature can also be enabled using port mapping feature
    // config e.g. gateway.port.mapping.{defaultTopologyName}

    if(defaultTopologyName == null && config.getGatewayPortMappings().values().contains(config.getGatewayPort())) {

      for(final Map.Entry<String, Integer> entry: config.getGatewayPortMappings().entrySet()) {

        if(entry.getValue().intValue() == config.getGatewayPort()) {
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
      LOG.defaultTopologySetup(defaultTopologyName,
          defaultTopologyRedirectContext);
    }

  }

  @Override
  public void handle(final String target, final Request baseRequest,
      final HttpServletRequest request, final HttpServletResponse response)
      throws IOException, ServletException {

    String newTarget = target;
    String baseURI = baseRequest.getRequestURI();

    // If Port Mapping feature enabled
    if (config.isGatewayPortMappingEnabled()) {
      int targetIndex;
      String context = "";

      // extract the gateway specific part i.e. {/gatewayName/}
      String originalContextPath = "";
      targetIndex = StringUtils.ordinalIndexOf(target, "/", 2);

      // Match found e.g. /{string}/
      if (targetIndex > 0) {
        originalContextPath = target.substring(0, targetIndex + 1);
      } else if (targetIndex == -1) {
        targetIndex = StringUtils.ordinalIndexOf(target, "/", 1);
        // For cases "/" and "/hive"
        if(targetIndex == 0) {
          originalContextPath = target;
        }
      }

      // Match "/{gatewayName}/{topologyName/foo" or "/".
      // There could be a case where content is served from the root
      // i.e. https://host:port/

      if (!baseURI.startsWith(originalContextPath)) {
        final int index = StringUtils.ordinalIndexOf(baseURI, "/", 3);
        if (index > 0) {
          context = baseURI.substring(0, index);
        }
      }

      if(!StringUtils.isBlank(context)) {
        LOG.topologyPortMappingAddContext(target, context + target);
      }
      // Move on to the next handler in chain with updated path
      newTarget = context + target;
    }

    //Backwards compatibility for default topology feature
    if (defaultTopologyRedirectContext != null && !baseURI
        .startsWith("/" + config.getGatewayPath())) {
      newTarget = defaultTopologyRedirectContext + target;

      final RequestUpdateHandler.ForwardedRequest newRequest = new RequestUpdateHandler.ForwardedRequest(
          request, defaultTopologyRedirectContext, newTarget);

      LOG.defaultTopologyForward(target, newTarget);
      super.handle(newTarget, baseRequest, newRequest, response);

    } else {

      super.handle(newTarget, baseRequest, request, response);
    }

  }
}

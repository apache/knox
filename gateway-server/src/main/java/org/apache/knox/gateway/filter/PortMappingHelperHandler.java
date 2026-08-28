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

import static org.apache.knox.gateway.filter.AbstractGatewayFilter.DEFAULT_TOPOLOGY_FORWARD_ATTRIBUTE_NAME;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

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
public class PortMappingHelperHandler extends Handler.Wrapper {
  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  private final GatewayConfig config;
  private final String defaultTopologyRedirectContext;

  public PortMappingHelperHandler(final GatewayConfig config) {
    this.config = config;
    this.defaultTopologyRedirectContext = getDefaultTopologyRedirectContext(config);
  }

  /**
   * Set up context for the default topology feature.
   * @param config GatewayConfig object to read from
   * @return default topology redirect context as a string (or {@code null})
   */
  private String getDefaultTopologyRedirectContext(final GatewayConfig config) {
    final String defaultTopologyName = config.getDefaultTopologyName();

    // The default topology feature can also be enabled using port mapping feature
    // config e.g. gateway.port.mapping.{defaultTopologyName}
    String redirectContext = null;
    if (defaultTopologyName == null
    && config.getGatewayPortMappings().containsValue(config.getGatewayPort())) {
      for (final Map.Entry<String, Integer> entry : config.getGatewayPortMappings().entrySet()) {
        if (entry.getValue().equals(config.getGatewayPort())) {
          redirectContext = "/" + config.getGatewayPath() + "/" + entry.getKey();
          break;
        }
      }
    }

    if (defaultTopologyName != null) {
      redirectContext = config.getDefaultAppRedirectPath();
      if (redirectContext != null && redirectContext.trim().isEmpty()) {
        redirectContext = null;
      }
    }
    if (redirectContext != null) {
      LOG.defaultTopologySetup(defaultTopologyName, redirectContext);
    }
    return redirectContext;
  }

  @Override
  public boolean handle(final Request request, final Response response, final Callback callback)
  throws Exception {
    final String requestPath = request.getHttpURI().getPath();
    final int port = Request.getLocalPort(request);

    if (config.isGatewayPortMappingEnabled()
    && config.getGatewayPortMappings().containsValue(port)) {
      // If Port Mapping feature enabled
      return handlePortMapping(request, response, callback, port);
    } else if (defaultTopologyRedirectContext != null
    && !requestPath.startsWith("/" + config.getGatewayPath())) {
      //Backwards compatibility for default topology feature
      return handleDefaultTopologyMapping(request, response, callback);
    } else {
      // case where topology port mapping is not enabled (or improperly configured)
      // and no default topology is configured
      return super.handle(request, response, callback);
    }
  }

  private boolean handlePortMapping(final Request request, final Response response,
                                    final Callback callback, final int port) throws Exception {
    final String topologyName = config.getGatewayPortMappings().entrySet()
                                    .stream()
                                    .filter(e -> e.getValue().equals(port))
                                    .map(Map.Entry::getKey)
                                    .findFirst()
                                    .orElse(null);
    final String gatewayTopologyContext = "/" + config.getGatewayPath() + "/" + topologyName;
    final String requestPath = request.getHttpURI().getPath();

    // If the request URI does not already contain /{gatewayPath}/{topologyName},
    // wrap the request to prepend it.
    if (!requestPath.contains(gatewayTopologyContext)) {
      final String newPath = gatewayTopologyContext + requestPath;
      LOG.topologyPortMappingUpdateRequest(requestPath, newPath);

      Request rewritten = rewritePath(request, newPath);
      return super.handle(rewritten, response, callback);
    }

    return super.handle(request, response, callback);
  }

  private boolean handleDefaultTopologyMapping(final Request request, final Response response,
                                               final Callback callback) throws Exception {
    final String requestPath = request.getHttpURI().getPath();
    final String newPath = defaultTopologyRedirectContext + requestPath;
    LOG.defaultTopologyForward(requestPath, newPath);

    Request rewritten = rewritePath(request, newPath);
    rewritten.setAttribute(DEFAULT_TOPOLOGY_FORWARD_ATTRIBUTE_NAME, "true");
    return super.handle(rewritten, response, callback);
  }

  /**
   * Wraps the given request so that its {@link HttpURI} reports the supplied
   * path instead of the original one. All other URI components (scheme,
   * authority, query, fragment) are preserved.
   */
  private static Request rewritePath(final Request request, final String newPath) {
    final HttpURI original = request.getHttpURI();
    final HttpURI rewritten = HttpURI.build(original).path(newPath).asImmutable();

    return new Request.Wrapper(request) {
      @Override
      public HttpURI getHttpURI() {
        return rewritten;
      }
    };
  }
}
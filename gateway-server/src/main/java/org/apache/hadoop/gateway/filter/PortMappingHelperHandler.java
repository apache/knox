package org.apache.hadoop.gateway.filter;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * This is a helper handler that adjusts the "target" patch of the request.
 * Used when Topology Port Mapping feature is used.
 * See KNOX-928
 *
 */
public class PortMappingHelperHandler extends HandlerWrapper {

  private static final GatewayMessages LOG = MessagesFactory
      .get(GatewayMessages.class);

  final GatewayConfig config;

  public PortMappingHelperHandler(final GatewayConfig config) {

    this.config = config;
  }

  @Override
  public void handle(final String target, final Request baseRequest,
      final HttpServletRequest request, final HttpServletResponse response)
      throws IOException, ServletException {

    // If Port Mapping feature enabled
    if (config.isGatewayPortMappingEnabled()) {
      int targetIndex;
      String context = "";
      String baseURI = baseRequest.getUri().toString();

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
      super.handle(context + target, baseRequest, request, response);
    } else {
      super.handle(target, baseRequest, request, response);
    }

  }
}

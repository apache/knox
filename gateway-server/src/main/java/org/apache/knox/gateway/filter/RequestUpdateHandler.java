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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ScopedHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;

/**
 * This handler will be ONLY registered with a specific connector listening on a
 * port that is configured through a property gateway.port.mapping.{topologyName}
 * in gateway-site.xml
 * <p>
 * The function of this connector is to append the right context path and
 * forward the request to the default port.
 * <p>
 * See KNOX-928
 *
 */
public class RequestUpdateHandler extends ScopedHandler {
  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);

  private String redirectContext;

  public RequestUpdateHandler(final GatewayConfig config,
      final String topologyName, final GatewayServices services) {
    super();

    if (config == null) {
      throw new IllegalArgumentException("config==null");
    }
    if (services == null) {
      throw new IllegalArgumentException("services==null");
    }
    if (topologyName == null) {
      throw new IllegalArgumentException("topologyName==null");
    }

    redirectContext = "/" + config.getGatewayPath() + "/" + topologyName;
  }

  @Override
  public void doScope(final String target, final Request baseRequest,
      final HttpServletRequest request, final HttpServletResponse response)
      throws IOException, ServletException {
    nextScope(target, baseRequest, request, response);
  }

  @Override
  public void doHandle(final String target, final Request baseRequest,
      final HttpServletRequest request, final HttpServletResponse response)
      throws IOException, ServletException {

    RequestUpdateHandler.ForwardedRequest newRequest = new RequestUpdateHandler.ForwardedRequest(
        request, redirectContext);

    // if the request already has the /{gatewaypath}/{topology} part then skip
    if (!StringUtils.startsWithIgnoreCase(target, redirectContext)) {
      baseRequest.setPathInfo(redirectContext + baseRequest.getPathInfo());
      baseRequest.setURIPathQuery(redirectContext + baseRequest.getRequestURI());

      final String newTarget = redirectContext + target;
      LOG.topologyPortMappingUpdateRequest(target, newTarget);
      nextHandle(newTarget, baseRequest, newRequest, response);
    } else {
      nextHandle(target, baseRequest, newRequest, response);
    }
  }

  /**
   * A request wrapper class that wraps a request and adds the context path if
   * needed.
   */
  static class ForwardedRequest extends HttpServletRequestWrapper {
    private final String contextPath;
    private final String requestURL;

    ForwardedRequest(final HttpServletRequest request, final String contextPath) {
      super(request);
      this.contextPath = contextPath;
      this.requestURL = generateRequestURL();
    }

    /**
     * Handle the case where getServerPort returns -1
     * @return requestURL
     */
    private String generateRequestURL() {
      if (getRequest().getServerPort() != -1) {
        return String.format(Locale.ROOT, "%s://%s:%s%s",
            getRequest().getScheme(),
            getRequest().getServerName(),
            getRequest().getServerPort(),
            getRequestURI());
      } else {
        return String.format(Locale.ROOT, "%s://%s%s",
            getRequest().getScheme(),
            getRequest().getServerName(),
            getRequestURI());
      }
    }

    @Override
    public StringBuffer getRequestURL() {
      return new StringBuffer(this.requestURL);
    }

    @Override
    public String getRequestURI() {
      return this.contextPath + super.getRequestURI();
    }

    @Override
    public String getContextPath() {
      return this.contextPath;
    }
  }
}

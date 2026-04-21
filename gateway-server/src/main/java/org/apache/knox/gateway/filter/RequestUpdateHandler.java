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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Locale;

/**
 * Container for the {@link ForwardedRequest} wrapper used by the port-mapping /
 * default-topology request rewriting logic (see KNOX-928).
 * <p>
 * The former Jetty {@code ScopedHandler} implementation was removed as part of
 * the Jetty 12 migration because {@code ScopedHandler} no longer exists and the
 * rewriting logic now lives directly inside {@link PortMappingHelperHandler}.
 * Only the request wrapper is retained here for backwards compatibility with
 * existing call sites and tests.
 */
public final class RequestUpdateHandler {

  private RequestUpdateHandler() {
    // utility holder for the ForwardedRequest wrapper
  }

  /**
   * A request wrapper class that wraps a request and prepends a context path
   * to the request URI.
   */
  public static class ForwardedRequest extends HttpServletRequestWrapper {
    private final String contextPath;
    private final String requestURL;

    public ForwardedRequest(final HttpServletRequest request, final String contextPath) {
      super(request);
      this.contextPath = contextPath;
      this.requestURL = generateRequestURL();
    }

    /**
     * Handle the case where getServerPort returns -1.
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
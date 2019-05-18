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
package org.apache.knox.gateway.filter;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class XForwardedHeaderFilter extends AbstractGatewayFilter {

  private static final String APPEND_SERVICE_NAME_PARAM = "isAppendServiceName";
  /**
   * There could be a case where a service might use double context paths
   * e.g. "/livy/v1" instead of "livy".
   * This parameter can be used to add such context (/livy/v1) to the
   * X-Forward-Context header.
   */
  private static final String SERVICE_CONTEXT = "serviceContext";

  private boolean isAppendServiceName;
  private String serviceContext;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    super.init( filterConfig );
    isAppendServiceName = Boolean.parseBoolean(filterConfig.getInitParameter(APPEND_SERVICE_NAME_PARAM));
    serviceContext = filterConfig.getInitParameter(SERVICE_CONTEXT);
  }

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    chain.doFilter( new XForwardedHeaderRequestWrapper( request ,  isAppendServiceName, serviceContext), response );
  }
}

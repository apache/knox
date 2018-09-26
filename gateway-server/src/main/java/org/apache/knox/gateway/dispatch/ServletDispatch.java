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
package org.apache.knox.gateway.dispatch;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

public class ServletDispatch implements Filter {

  private static final String DISPATCH_SERVLET_PARAM_NAME = "dispatchServletName";

  private ServletContext servletContext;
  private String servletName;

  @Override
  public void init( FilterConfig config ) throws ServletException {
    servletContext = config.getServletContext();
    servletName = config.getInitParameter( DISPATCH_SERVLET_PARAM_NAME );
  }

  @Override
  public void doFilter( ServletRequest request, ServletResponse response, FilterChain chain )
      throws IOException, ServletException {
    servletContext.getNamedDispatcher( servletName ).forward( request, response );
  }

  @Override
  public void destroy() {
    servletContext = null;
    servletName = null;
  }

}

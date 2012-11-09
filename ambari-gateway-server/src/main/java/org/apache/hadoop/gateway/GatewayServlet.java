/**
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
package org.apache.hadoop.gateway;

import javax.servlet.*;
import java.io.IOException;
import java.util.Enumeration;

public class GatewayServlet implements Servlet {

  private ConfigAdapter config;
  private GatewayFilter filter;

  public GatewayServlet( GatewayFilter filter ) {
    this.config = null;
    this.filter = filter;
  }

  public GatewayServlet() {
    this( null );
  }

  public synchronized GatewayFilter getFilter() {
    return filter;
  }

  public synchronized void setFilter( GatewayFilter filter ) throws ServletException {
    Filter prev = filter;
    if( config != null ) {
      filter.init( config );
    }
    this.filter = filter;
    if( prev != null && config != null ) {
      prev.destroy();
    }
  }

  @Override
  public synchronized void init( ServletConfig servletConfig ) throws ServletException {
    config = new ConfigAdapter( servletConfig );
    if( filter != null ) {
      filter.init( config );
    }
  }

  @Override
  public ServletConfig getServletConfig() {
    return config.getServletConfig();
  }

  @Override
  public void service( ServletRequest servletRequest, ServletResponse servletResponse ) throws ServletException, IOException {
    GatewayFilter f = filter;
    if( f != null ) {
      f.doFilter( servletRequest, servletResponse );
    }
  }

  @Override
  public String getServletInfo() {
    return null;
  }

  @Override
  public synchronized void destroy() {
    if( filter != null ) {
      filter.destroy();
    }
    filter = null;
  }

  private class ConfigAdapter implements FilterConfig {

    private ServletConfig config;

    private ConfigAdapter( ServletConfig config ) {
      this.config = config;
    }

    private ServletConfig getServletConfig() {
      return config;
    }

    @Override
    public String getFilterName() {
      return config.getServletName();
    }

    @Override
    public ServletContext getServletContext() {
      return config.getServletContext();
    }

    @Override
    public String getInitParameter( String name ) {
      return config.getInitParameter( name );
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
      return config.getInitParameterNames();
    }
  }

}

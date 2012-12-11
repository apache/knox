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

import org.apache.hadoop.gateway.descriptor.GatewayDescriptor;
import org.apache.hadoop.gateway.descriptor.GatewayDescriptorFactory;
import org.apache.hadoop.gateway.i18n.resources.ResourcesFactory;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.Enumeration;

public class GatewayServlet implements Servlet {

  private static final GatewayResources res = ResourcesFactory.get( GatewayResources.class );

  public static final String GATEWAY_CLUSTER_DESCRIPTOR_LOCATION_DEFAULT = "gateway.xml";
  public static final String GATEWAY_CLUSTER_DESCRIPTOR_LOCATION_PARAM = "gatewayClusterDescriptorLocation";

  private FilterConfigAdapter filterConfig;
  private volatile GatewayFilter filter;

  public GatewayServlet( GatewayFilter filter ) {
    this.filterConfig = null;
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
    if( filterConfig != null ) {
      filter.init( filterConfig );
    }
    this.filter = filter;
    if( prev != null && filterConfig != null ) {
      prev.destroy();
    }
  }

  @Override
  public synchronized void init( ServletConfig servletConfig ) throws ServletException {
    if( filter == null ) {
      filter = createFilter( servletConfig );
    }
    filterConfig = new FilterConfigAdapter( servletConfig );
    if( filter != null ) {
      filter.init( filterConfig );
    }
  }

  @Override
  public ServletConfig getServletConfig() {
    return filterConfig.getServletConfig();
  }

  @Override
  public void service( ServletRequest servletRequest, ServletResponse servletResponse ) throws ServletException, IOException {
    GatewayFilter f = filter;
    if( f != null ) {
      f.doFilter( servletRequest, servletResponse );
    } else {
      ((HttpServletResponse)servletResponse).setStatus( HttpServletResponse.SC_SERVICE_UNAVAILABLE );
    }
  }

  @Override
  public String getServletInfo() {
    return res.gatewayServletInfo();
  }

  @Override
  public synchronized void destroy() {
    if( filter != null ) {
      filter.destroy();
    }
    filter = null;
  }

  private static GatewayFilter createFilter( ServletConfig servletConfig ) throws ServletException {
    GatewayFilter filter = null;
    try {
      InputStream stream = null;
      String location = servletConfig.getInitParameter( GATEWAY_CLUSTER_DESCRIPTOR_LOCATION_PARAM );
      if( location != null ) {
        stream = servletConfig.getServletContext().getResourceAsStream( location );
        if( stream == null ) {
          stream = servletConfig.getServletContext().getResourceAsStream( "/WEB-INF/" + location );
        }
      } else {
        stream = servletConfig.getServletContext().getResourceAsStream( GATEWAY_CLUSTER_DESCRIPTOR_LOCATION_DEFAULT );
      }
      if( stream != null ) {
        try {
          GatewayDescriptor descriptor = GatewayDescriptorFactory.load( "xml", new InputStreamReader( stream ) );
          filter = GatewayFactory.create( descriptor );
        } finally {
          stream.close();
        }
      }
    } catch( IOException e ) {
      throw new ServletException( e );
    } catch( URISyntaxException e ) {
      throw new ServletException( e );
    }
    return filter;
  }

  private class FilterConfigAdapter implements FilterConfig {

    private ServletConfig config;

    private FilterConfigAdapter( ServletConfig config ) {
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

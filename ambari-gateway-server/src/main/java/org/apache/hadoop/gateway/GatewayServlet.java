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

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.descriptor.ClusterDescriptor;
import org.apache.hadoop.gateway.descriptor.ClusterDescriptorFactory;
import org.apache.hadoop.gateway.descriptor.spi.ClusterDescriptorImporter;

import javax.servlet.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;

public class GatewayServlet implements Servlet {

  public static final String GATEWAY_CLUSTER_DESCRIPTOR_LOCATION_DEFAULT = "gateway.xml";
  public static final String GATEWAY_CLUSTER_DESCRIPTOR_LOCATION_PARAM = "gatewayClusterDescriptorLocation";

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
    filter = createFilter( servletConfig );
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
    return "Apache Hadoop Gateway Servlet";
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
      URL locationUrl = null;
      String location = servletConfig.getInitParameter( GATEWAY_CLUSTER_DESCRIPTOR_LOCATION_PARAM );
      if( location != null ) {
        locationUrl = servletConfig.getServletContext().getResource( location );
      } else {
        locationUrl = servletConfig.getServletContext().getResource( GATEWAY_CLUSTER_DESCRIPTOR_LOCATION_DEFAULT );
      }
      if( locationUrl != null ) {
        InputStream stream = locationUrl.openStream();
        ClusterDescriptor descriptor;
        try {
          descriptor = ClusterDescriptorFactory.load( "xml", new InputStreamReader( stream ) );
        } finally {
          stream.close();
        }
        filter = GatewayFactory.create( descriptor );
      }
    } catch( IOException e ) {
      throw new ServletException( e );
    } catch( URISyntaxException e ) {
      throw new ServletException( e );
    }
    return filter;
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

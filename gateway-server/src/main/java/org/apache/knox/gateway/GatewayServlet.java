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
package org.apache.knox.gateway;

import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.CorrelationServiceFactory;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.descriptor.GatewayDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptorFactory;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.metrics.MetricsService;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
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
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public class GatewayServlet implements Servlet, Filter {
  public static final String GATEWAY_DESCRIPTOR_LOCATION_DEFAULT = "gateway.xml";
  public static final String GATEWAY_DESCRIPTOR_LOCATION_PARAM = "gatewayDescriptorLocation";

  private static final GatewayResources res = ResourcesFactory.get( GatewayResources.class );
  private static final GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );

  private static final AuditService auditService = AuditServiceFactory.getAuditService();

  private FilterConfigAdapter filterConfig;
  private GatewayFilter filter;

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
    if( filterConfig != null ) {
      filter.init( filterConfig );
    }
    this.filter = filter;
    if( filter != null && filterConfig != null ) {
      filter.destroy();
    }
  }

  @Override
  public synchronized void init( ServletConfig servletConfig ) throws ServletException {
    try {
      if( filter == null ) {
        filter = createFilter( servletConfig );
      }
      filterConfig = new FilterConfigAdapter( servletConfig );
      if( filter != null ) {
        filter.init( filterConfig );
      }
    } catch( ServletException | RuntimeException e ) {
      LOG.failedToInitializeServletInstace( e );
      throw e;
    }
  }

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    try {
      if( filter == null ) {
        filter = createFilter( filterConfig );
      }
      if( filter != null ) {
        filter.init( filterConfig );
      }
    } catch( ServletException | RuntimeException e ) {
      LOG.failedToInitializeServletInstace( e );
      throw e;
    }
  }

  @Override
  public ServletConfig getServletConfig() {
    return filterConfig.getServletConfig();
  }

  @Override
  public void service( ServletRequest servletRequest, ServletResponse servletResponse ) throws ServletException, IOException {
    try {
      auditService.createContext();
      GatewayFilter f = filter;
      if( f != null ) {
        try {
          f.doFilter( servletRequest, servletResponse, null );
        } catch( IOException | RuntimeException | ServletException e ) {
          LOG.failedToExecuteFilter( e );
          throw e;
        }
      } else {
        ((HttpServletResponse)servletResponse).setStatus( HttpServletResponse.SC_SERVICE_UNAVAILABLE );
      }
    } finally {
      auditService.detachContext();
      // Make sure to destroy the correlationContext to prevent threading issues
      CorrelationServiceFactory.getCorrelationService().detachContext();
    }
  }

  @Override
  public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain ) throws IOException, ServletException {
    try {
      auditService.createContext();
      GatewayFilter f = filter;
      if( f != null ) {
        try {
          f.doFilter( servletRequest, servletResponse );

          /* if response is committed in case of SSO redirect no need to apply further filters */
          if(!servletResponse.isCommitted()) {
            //TODO: This should really happen naturally somehow as part of being a filter.  This way will cause problems eventually.
            chain.doFilter( servletRequest, servletResponse );
          }

        } catch( IOException | RuntimeException | ServletException e ) {
          LOG.failedToExecuteFilter( e );
          throw e;
        }
      } else {
        ((HttpServletResponse)servletResponse).setStatus( HttpServletResponse.SC_SERVICE_UNAVAILABLE );
      }
    } finally {
      auditService.detachContext();
      // Make sure to destroy the correlationContext to prevent threading issues
      CorrelationServiceFactory.getCorrelationService().detachContext();
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

  private static GatewayFilter createFilter( InputStream stream, ServletContext servletContext ) throws ServletException {
    try {
      GatewayFilter filter = null;
      if (stream != null) {
        filter = GatewayFactory.create(createGatewayDescriptor(stream));
      }
      GatewayConfig gatewayConfig = (GatewayConfig) servletContext.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
      if (gatewayConfig.isMetricsEnabled()) {
        GatewayServices gatewayServices = (GatewayServices) servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        MetricsService metricsService = gatewayServices.getService(ServiceType.METRICS_SERVICE);
        if (metricsService != null) {
          GatewayFilter instrumentedFilter = metricsService.getInstrumented(filter);
          if (instrumentedFilter != null) {
            filter = instrumentedFilter;
          }
        }
      }
      return filter;
    } catch( IOException | URISyntaxException e ) {
      throw new ServletException( e );
    }
  }

  private static synchronized GatewayDescriptor createGatewayDescriptor(InputStream stream) throws IOException {
    try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)){
      return GatewayDescriptorFactory.load("xml", reader);
    } finally {
      stream.close();
    }
  }

  private static GatewayFilter createFilter( FilterConfig filterConfig ) throws ServletException {
    GatewayFilter filter;
    InputStream stream;
    String location = filterConfig.getInitParameter( GATEWAY_DESCRIPTOR_LOCATION_PARAM );
    if( location != null ) {
      stream = filterConfig.getServletContext().getResourceAsStream( location );
      if( stream == null ) {
        stream = filterConfig.getServletContext().getResourceAsStream( "/WEB-INF/" + location );
      }
    } else {
      stream = filterConfig.getServletContext().getResourceAsStream( GATEWAY_DESCRIPTOR_LOCATION_DEFAULT );
    }
    filter = createFilter( stream, filterConfig.getServletContext());
    return filter;
  }

  private static GatewayFilter createFilter( ServletConfig servletConfig ) throws ServletException {
    GatewayFilter filter;
    InputStream stream;
    String location = servletConfig.getInitParameter( GATEWAY_DESCRIPTOR_LOCATION_PARAM );
    if( location != null ) {
      stream = servletConfig.getServletContext().getResourceAsStream( location );
      if( stream == null ) {
        stream = servletConfig.getServletContext().getResourceAsStream( "/WEB-INF/" + location );
      }
    } else {
      stream = servletConfig.getServletContext().getResourceAsStream( GATEWAY_DESCRIPTOR_LOCATION_DEFAULT );
    }
    filter = createFilter( stream, servletConfig.getServletContext());
    return filter;
  }

  private static class FilterConfigAdapter implements FilterConfig {

    private ServletConfig config;

    FilterConfigAdapter( ServletConfig config ) {
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

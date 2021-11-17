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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.api.CorrelationService;
import org.apache.knox.gateway.audit.api.CorrelationServiceFactory;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationContext;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.util.ServletRequestUtils;
import org.apache.knox.gateway.util.urltemplate.Matcher;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;

public class GatewayFilter implements Filter {

  private static final FilterChain EMPTY_CHAIN = new FilterChain() {
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse ) throws IOException, ServletException {
    }
  };

  private static final GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );
  private static final GatewayResources RES = ResourcesFactory.get( GatewayResources.class );
  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = auditService.getAuditor(
      AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
      AuditConstants.KNOX_COMPONENT_NAME );

  private Set<Holder> holders;
  private Matcher<Chain> chains;
  private FilterConfig config;

  public GatewayFilter() {
    holders = new HashSet<>();
    chains = new Matcher<>();
  }

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    this.config = filterConfig;
  }

  @Override
  public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain ) throws IOException, ServletException {
    doFilter( servletRequest, servletResponse );
    if( filterChain != null ) {
      filterChain.doFilter( servletRequest, servletResponse );
    }
  }

  @SuppressWarnings("unchecked")
  public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse ) throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest)servletRequest;
    HttpServletResponse httpResponse = (HttpServletResponse)servletResponse;

    //TODO: The resulting pathInfo + query needs to be added to the servlet context somehow so that filters don't need to rebuild it.  This is done in HttpClientDispatch right now for example.
    String path = httpRequest.getPathInfo();
    String requestPath = ServletRequestUtils.getRequestPath(httpRequest);
    String requestPathWithQuery = ServletRequestUtils.getRequestPathWithQuery(httpRequest);

    Template pathWithQueryTemplate;
    try {
      pathWithQueryTemplate = Parser.parseLiteral( requestPathWithQuery );
    } catch( URISyntaxException e ) {
      throw new ServletException( e );
    }
    String contextWithPathAndQuery = ServletRequestUtils.getContextPathWithQuery(httpRequest);
    LOG.receivedRequest( httpRequest.getMethod(), requestPath );

    servletRequest.setAttribute(
        AbstractGatewayFilter.SOURCE_REQUEST_URL_ATTRIBUTE_NAME, pathWithQueryTemplate );
    servletRequest.setAttribute(
        AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME, contextWithPathAndQuery );

    Matcher<Chain>.Match match = chains.match( pathWithQueryTemplate );

    // if there was no match then look for a default service for the topology
    if (match == null) {
      Topology topology = (Topology) servletRequest.getServletContext().getAttribute("org.apache.knox.gateway.topology");
      if (topology != null) {
        String defaultServicePath = topology.getDefaultServicePath();
        if (defaultServicePath != null) {
          try {
            String newPathWithQuery = defaultServicePath + "/" + pathWithQueryTemplate;
            match = chains.match(Parser.parseLiteral(newPathWithQuery));
            String origUrl = ((HttpServletRequest) servletRequest).getRequestURL().toString();
            String url = origUrl;
            if (path == null || "/".equals(path)) {
              url += defaultServicePath;
            } else {
              int index = origUrl.indexOf(path);
              url = origUrl.substring(0, index) + "/" + defaultServicePath + path;
            }
            servletRequest = new ForwardedRequest((HttpServletRequest) servletRequest,
                defaultServicePath,
                url);
          } catch (URISyntaxException e) {
            throw new ServletException( e );
          }
        }
      }
    }

    assignCorrelationRequestId();
    // Populate Audit/correlation parameters
    AuditContext auditContext = auditService.getContext();
    if(auditContext == null) {
      auditContext = auditService.createContext();
    }
    auditContext.setTargetServiceName( match == null ? null : match.getValue().getResourceRole() );
    auditContext.setRemoteIp( getRemoteAddress(servletRequest) );
    auditContext.setRemoteHostname( servletRequest.getRemoteHost() );
    auditService.attachContext(auditContext);
    auditor.audit(
        Action.ACCESS, contextWithPathAndQuery, ResourceType.URI,
        ActionOutcome.UNAVAILABLE, RES.requestMethod(((HttpServletRequest)servletRequest).getMethod()));

    if( match != null ) {
      Chain chain = match.getValue();
      servletRequest.setAttribute( AbstractGatewayFilter.TARGET_SERVICE_ROLE, chain.getResourceRole() );
      try {
        chain.doFilter( servletRequest, servletResponse );
      } catch( IOException | RuntimeException | ThreadDeath | ServletException e ) {
        LOG.failedToExecuteFilter( e );
        auditor.audit( Action.ACCESS, contextWithPathAndQuery, ResourceType.URI, ActionOutcome.FAILURE );
        throw e;
      } catch( Throwable e ) {
        LOG.failedToExecuteFilter( e );
        auditor.audit( Action.ACCESS, contextWithPathAndQuery, ResourceType.URI, ActionOutcome.FAILURE );
        throw new ServletException( e );
      } finally {
        // Make sure to destroy the correlationContext to prevent threading issues
        CorrelationServiceFactory.getCorrelationService().detachContext();
      }
    } else {
      LOG.failedToMatchPath( requestPath );
      httpResponse.setStatus( HttpServletResponse.SC_NOT_FOUND );

      // Make sure to destroy the correlationContext to prevent threading issues
      CorrelationServiceFactory.getCorrelationService().detachContext();
    }

    //KAM[ Don't do this or the Jetty default servlet will overwrite any response setup by the filter.
    // filterChain.doFilter( servletRequest, servletResponse );
    //]
  }

  private String getRemoteAddress(ServletRequest servletRequest) {
    GatewayConfig gatewayConfig =
        (GatewayConfig) servletRequest.getServletContext().
        getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);

    String addrHeaderName = gatewayConfig.getHeaderNameForRemoteAddress();
    String addr = ((HttpServletRequest)servletRequest).getHeader(addrHeaderName);
    if (addr == null || addr.trim().isEmpty()) {
      addr = servletRequest.getRemoteAddr();
    }
    return addr;
  }

  @Override
  public void destroy() {
    for( Holder holder : holders ) {
      holder.destroy();
    }
  }

  private void addHolder( Holder holder ) {
    holders.add( holder );
    Chain chain = chains.get( holder.template );
    if( chain == null ) {
      chain = new Chain();
      chain.setResourceRole( holder.getResourceRole() );
      chains.add( holder.template, chain );
    }
    chain.chainList.add( holder );
  }

  public void addFilter( String path, String name, Filter filter, Map<String,String> params, String resourceRole ) throws URISyntaxException {
    Holder holder = new Holder( path, name, filter, params, resourceRole );
    addHolder( holder );
  }

  public void addFilter( String path, String name, String clazz, Map<String,String> params, String resourceRole ) throws URISyntaxException {
    Holder holder = new Holder( path, name, clazz, params, resourceRole );
    addHolder( holder );
  }

  // Now creating the correlation context only if required since it may be created upstream in the CorrelationHandler.
  private void assignCorrelationRequestId() {
    CorrelationService correlationService = CorrelationServiceFactory.getCorrelationService();
    CorrelationContext correlationContext = correlationService.getContext();
    if( correlationContext == null ) {
      correlationService.attachContext(new Log4jCorrelationContext(UUID.randomUUID().toString(), null, null));
    }
  }

  private class Chain implements FilterChain {
    private List<Holder> chainList;
    private String resourceRole;

    Chain() {
      this.chainList = new ArrayList<>();
    }

    Chain( List<Holder> chainList) {
      this.chainList = chainList;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse ) throws IOException, ServletException {
      if( chainList != null && !chainList.isEmpty() ) {
        final Filter filter = chainList.get( 0 );
        final FilterChain chain = subChain();
        filter.doFilter( servletRequest, servletResponse, chain );
      }
    }

    private FilterChain subChain() {
      if( chainList != null && chainList.size() > 1 ) {
        return new Chain( chainList.subList( 1, chainList.size() ) );
      } else {
        return EMPTY_CHAIN;
      }
    }

    private String getResourceRole() {
      return resourceRole;
    }

    private void setResourceRole( String resourceRole ) {
      this.resourceRole = resourceRole;
    }
  }

  public class Holder implements Filter, FilterConfig {
    private Template template;
    private String name;
    private Map<String,String> params;
    private Filter instance;
    private Class<? extends Filter> clazz;
    private String type;
    private String resourceRole;

    Holder( String path, String name, Filter filter, Map<String,String> params, String resourceRole ) throws URISyntaxException {
      this.template = Parser.parseTemplate( path );
      this.name = name;
      this.params = params;
      this.instance = filter;
      this.clazz = filter.getClass();
      this.type = clazz.getCanonicalName();
      this.resourceRole = resourceRole;
    }

    Holder( String path, String name, String clazz, Map<String,String> params, String resourceRole ) throws URISyntaxException {
      this.template = Parser.parseTemplate( path );
      this.name = name;
      this.params = params;
      this.instance = null;
      this.clazz = null;
      this.type = clazz;
      this.resourceRole = resourceRole;
    }

    @Override
    public String getFilterName() {
      return name;
    }

    @Override
    public ServletContext getServletContext() {
      return GatewayFilter.this.config.getServletContext();
    }

    @Override
    public String getInitParameter( String name ) {
      String value = null;
      if( params != null ) {
        value = params.get( name );
      }
      return value;
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
      Enumeration<String> names = null;
      if( params != null ) {
        names = Collections.enumeration( params.keySet() );
      }
      return names;
    }

    @Override
    public void init( FilterConfig filterConfig ) throws ServletException {
      getInstance().init( filterConfig );
    }

    @Override
    public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain ) throws IOException, ServletException {
      final Filter filter = getInstance();
      filter.doFilter( servletRequest, servletResponse, filterChain );
    }

    @Override
    public void destroy() {
      if( instance != null ) {
        instance.destroy();
        instance = null;
      }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Filter> getClazz() throws ClassNotFoundException {
      if( clazz == null ) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if( loader == null ) {
          loader = this.getClass().getClassLoader();
        }
        clazz = (Class)loader.loadClass( type );
      }
      return clazz;
    }

    private Filter getInstance() throws ServletException {
      if( instance == null ) {
        try {
          if( clazz == null ) {
            clazz = getClazz();
          }
          Filter f = clazz.newInstance();
          f.init(this);
          instance = f;
        } catch( Exception e ) {
          throw new ServletException( e );
        }
      }
      return instance;
    }

    private String getResourceRole() {
      return resourceRole;
    }

  }

  /**
   * A request wrapper class that wraps a request and adds the context path if
   * needed.
   */
  static class ForwardedRequest extends HttpServletRequestWrapper {

    private String newURL;
    private String contextpath;

    ForwardedRequest(final HttpServletRequest request,
        final String contextpath, final String newURL) {
      super(request);
      this.newURL = newURL;
      this.contextpath = contextpath;
    }

    @Override
    public StringBuffer getRequestURL() {
      return new StringBuffer(newURL);
    }

    @Override
    public String getRequestURI() {
      return newURL;
    }

    @Override
    public String getContextPath() {
      return super.getContextPath() + "/" + this.contextpath;
    }

  }
}

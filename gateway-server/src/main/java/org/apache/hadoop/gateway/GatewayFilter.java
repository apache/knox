package org.apache.hadoop.gateway;

import org.apache.hadoop.gateway.filter.AbstractGatewayFilter;
import org.apache.hadoop.gateway.util.urltemplate.Matcher;
import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Template;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class GatewayFilter implements Filter {

  private static final FilterChain EMPTY_CHAIN = new FilterChain() {
    public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse ) throws IOException, ServletException {
    }
  };

  private Set<Holder> holders;
  private Matcher<Chain> chains;
  private FilterConfig config;

  public GatewayFilter() {
    holders = new HashSet<Holder>();
    chains = new Matcher<Chain>();
  }

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    this.config = filterConfig;
  }

  @Override
  public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain ) throws IOException, ServletException {
    doFilter( servletRequest, servletResponse );
  }

  @SuppressWarnings("unckecked")
  public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse ) throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest)servletRequest;
    HttpServletResponse httpResponse = (HttpServletResponse)servletResponse;

    //TODO: The resulting pathInfo + query needs to be added to the servlet context somehow so that filters don't need to rebuild it.  This is done in HttpClientDispatch right now for example.
    String query = httpRequest.getQueryString();
    String path = httpRequest.getPathInfo() + ( query == null ? "" : "?" + query );

    Template pathTemplate;
    try {
      pathTemplate = Parser.parse( path );
    } catch( URISyntaxException e ) {
      throw new ServletException( e );
    }

    servletRequest.setAttribute( AbstractGatewayFilter.SOURCE_REQUEST_URL_ATTRIBUTE_NAME, pathTemplate );

    Matcher<Chain>.Match match = chains.match( pathTemplate );
    if( match != null ) {
      Chain chain = match.getValue();
      try {
        chain.doFilter( servletRequest, servletResponse );
      } catch( IOException e ) {
        e.printStackTrace();
        throw e;
      } catch( ServletException e ) {
        e.printStackTrace();
        throw e;
      } catch( RuntimeException e ) {
        e.printStackTrace();
        throw e;
      } catch( ThreadDeath e ) {
        e.printStackTrace();
        throw e;
      } catch( Throwable e ) {
        e.printStackTrace();
        throw new ServletException( e );
      }
    } else {
      httpResponse.setStatus( HttpServletResponse.SC_NOT_FOUND );
    }
    //KAM[ Don't do this or the Jetty default servlet will overwrite any response setup by the filter.
    // filterChain.doFilter( servletRequest, servletResponse );
    //]
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
      chains.add( holder.template, chain );
    }
    chain.chain.add( holder );
  }

  public void addFilter( String path, String name, Filter filter, Map<String,String> params ) throws URISyntaxException {
    Holder holder = new Holder( path, name, filter, params );
    addHolder( holder );
  }

//  public void addFilter( String path, String name, Class<WarDirFilter> clazz, Map<String,String> params ) throws URISyntaxException {
//    Holder holder = new Holder( path, name, clazz, params );
//    addHolder( holder );
//  }

  public void addFilter( String path, String name, String clazz, Map<String,String> params ) throws URISyntaxException {
    Holder holder = new Holder( path, name, clazz, params );
    addHolder( holder );
  }

  private class Chain implements FilterChain {

    private List<Holder> chain;

    private Chain() {
      this.chain = new ArrayList<Holder>();
    }

    private Chain( List<Holder> chain ) {
      this.chain = chain;
    }

    public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse ) throws IOException, ServletException {
      if( chain != null && !chain.isEmpty() ) {
        chain.get( 0 ).doFilter( servletRequest, servletResponse, subChain() );
      }
    }

    private FilterChain subChain() {
      if( chain != null && chain.size() > 1 ) {
        return new Chain( chain.subList( 1, chain.size() ) );
      } else {
        return EMPTY_CHAIN;
      }
    }

  }

  private class Holder implements Filter, FilterConfig {
//    private String path;
    private Template template;
    private String name;
    private Map<String,String> params;
    private Filter instance;
    private Class<? extends Filter> clazz;
    private String type;

    private Holder( String path, String name, Filter filter, Map<String,String> params ) throws URISyntaxException {
//      this.path = path;
      this.template = Parser.parse( path );
      this.name = name;
      this.params = params;
      this.instance = filter;
      this.clazz = filter.getClass();
      this.type = clazz.getCanonicalName();
    }

//    private Holder( String path, String name, Class<WarDirFilter> clazz, Map<String,String> params ) throws URISyntaxException {
//      this.path = path;
//      this.template = Parser.parse( path );
//      this.name = name;
//      this.params = params;
//      this.instance = null;
//      this.clazz = clazz;
//      this.type = clazz.getCanonicalName();
//    }

    private Holder( String path, String name, String clazz, Map<String,String> params ) throws URISyntaxException {
//      this.path = path;
      this.template = Parser.parse( path );
      this.name = name;
      this.params = params;
      this.instance = null;
      this.clazz = null;
      this.type = clazz;
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
      getInstance().doFilter( servletRequest, servletResponse, filterChain );
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
          instance = clazz.newInstance();
          instance.init( this );
        } catch( Exception e ) {
          throw new ServletException( e );
        }
      }
      return instance;
    }

  }

}

package org.apache.hadoop.gateway;

import org.apache.hadoop.gateway.util.PathMap;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 *
 */
public class GatewayFilter implements Filter {

  private static final FilterChain EMPTY_CHAIN = new FilterChain() {
    @Override
    public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse ) throws IOException, ServletException {
    }
  };

  private Set<Holder> holders;
  private PathMap<Chain> chains;
  private FilterConfig config;

  public GatewayFilter() {
    holders = new HashSet<Holder>();
    chains = new PathMap<Chain>();
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
    String path = httpRequest.getPathInfo();

    Chain chain = chains.pick( path );
    if( chain != null ) {
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
    Chain chain = chains.get( holder.path );
    if( chain == null ) {
      chain = new Chain();
      chains.put( holder.path, chain );
    }
    chain.chain.add( holder );
  }

  public void addFilter( String path, String name, Filter filter, Map<String,String> params ) {
    Holder holder = new Holder( path, name, filter, params );
    addHolder( holder );
  }

  public void addFilter( String path, String name, Class<Filter> clazz, Map<String,String> params ) {
    Holder holder = new Holder( path, name, clazz, params );
    addHolder( holder );
  }

  public void addFilter( String path, String name, String clazz, Map<String,String> params ) {
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

  private class Holder implements Filter, FilterConfig{
    private String path;
    private String name;
    private Map<String,String> params;
    private Filter instance;
    private Class<? extends Filter> clazz;
    private String type;

    private Holder( String path, String name, Filter filter, Map<String,String> params ) {
      this.path = path;
      this.name = name;
      this.params = params;
      this.instance = filter;
      this.clazz = filter.getClass();
      this.type = clazz.getCanonicalName();
    }

    private Holder( String path, String name, Class<Filter> clazz, Map<String,String> params ) {
      this.path = path;
      this.name = name;
      this.params = params;
      this.instance = null;
      this.clazz = clazz;
      this.type = clazz.getCanonicalName();
    }

    private Holder( String path, String name, String clazz, Map<String,String> params ) {
      this.path = path;
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

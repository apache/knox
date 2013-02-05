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
package org.apache.hadoop.gateway.filter.rewrite.impl;

import org.apache.hadoop.gateway.filter.GatewayResponseWrapper;
import org.apache.hadoop.gateway.filter.ResponseStreamer;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteServletContextListener;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteStreamFilterFactory;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.hadoop.gateway.util.Urls;
import org.apache.hadoop.gateway.util.urltemplate.Params;
import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Template;
import org.apache.hadoop.io.IOUtils;

import javax.activation.MimeType;
import javax.servlet.FilterConfig;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class UrlRewriteResponse extends GatewayResponseWrapper implements Params, ResponseStreamer {

  private static final Set<String> IGNORE_HEADER_NAMES = new HashSet<String>();
  static {
    IGNORE_HEADER_NAMES.add( "Content-Length" );
  }

//  private static final Set<String> REWRITE_HEADER_NAMES = new HashSet<String>();
//  static {
//    REWRITE_HEADER_NAMES.add( "Location" );
//  }

  private static final String REQUEST_PARAM_PREFIX = "request.";
  private static final String GATEWAY_PARAM_PREFIX = "gateway.";

  private FilterConfig config;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private ServletOutputStream output;

  public UrlRewriteResponse( FilterConfig config, HttpServletRequest request, HttpServletResponse response )
      throws IOException {
    super( response );
    this.config = config;
    this.request = request;
    this.response = response;
    this.output = null;
  }

//  protected boolean rewriteHeader( String name ) {
//    return REWRITE_HEADER_NAMES.contains( name );
//  }

  protected boolean ignoreHeader( String name ) {
    return IGNORE_HEADER_NAMES.contains( name );
  }

  public String rewriteValue( UrlRewriter rewriter, String value ) {
    try {
      Template input = Parser.parse( value );
      Template output = rewriter.rewrite( input, UrlRewriter.Direction.OUT );
      value = output.toString();
    } catch( URISyntaxException e ) {
      e.printStackTrace();
    }
    return value;
  }

  // Ignore the Content-Length from the dispatch respond since the respond body may be rewritten.
  @Override
  public void setHeader( String name, String value ) {
    if( !ignoreHeader( name) ) {
//      if( rewriteHeader( name ) ) {
        UrlRewriter rewriter = UrlRewriteServletContextListener.getUrlRewriter( config.getServletContext() );
        value = rewriteValue( rewriter, value );
//      }
      super.setHeader( name, value );
    }
  }

  // Ignore the Content-Length from the dispatch respond since the respond body may be rewritten.
  @Override
  public void addHeader( String name, String value ) {
    if( !ignoreHeader( name) ) {
//      if( rewriteHeader( name ) ) {
        UrlRewriter rewriter = UrlRewriteServletContextListener.getUrlRewriter( config.getServletContext() );
        value = rewriteValue( rewriter, value );
//      }
      super.addHeader( name, value );
    }
  }

  @Override
  public OutputStream getRawOutputStream() throws IOException {
    return response.getOutputStream();
  }

  @Override
  public void streamResponse( InputStream input, OutputStream output ) throws IOException {
    MimeType type = getMimeType();
    UrlRewriter rewriter = UrlRewriteServletContextListener.getUrlRewriter( config.getServletContext() );
    InputStream filteredInput = UrlRewriteStreamFilterFactory.create(
        type, null, input, rewriter, UrlRewriter.Direction.OUT );
    IOUtils.copyBytes( filteredInput, output, 4096 );
    output.close();
  }

  //TODO: Need to buffer the output here and when it is closed, rewrite it and then write the result to the stream.
  // This should only happen if the caller isn't using the streaming model.
  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    if( output == null ) {
      output = new UrlRewriteResponseStream( this );
    }
    return output;
  }

  @Override
  public Set<String> getNames() {
    return Collections.emptySet();
  }

  @Override
  @SuppressWarnings( "unchecked" )
  public List<String> resolve( String name ) {
    if( name.startsWith( REQUEST_PARAM_PREFIX ) ) {
      return Arrays.asList( getRequestParam( name.substring( REQUEST_PARAM_PREFIX.length() ) ) );
    } else if ( name.startsWith( GATEWAY_PARAM_PREFIX ) ) {
      return Arrays.asList( getGatewayParam( name.substring( GATEWAY_PARAM_PREFIX.length() ) ) );
    }  else {
      return Arrays.asList( config.getInitParameter( name ) );
    }
  }

  private String getGatewayParam( String name ) {
    if( "url".equals( name ) ) {
      return request.getScheme() + "://" + request.getServerName() + ":" + request.getLocalPort() + request.getContextPath();
    } else if( "address".equals( name ) ) {
      return request.getServerName() + ":" + request.getLocalPort();
    } else if( "path".equals( name ) ) {
      return request.getContextPath();
    } else {
      return null;
    }
  }

  private String getRequestParam( String name ) {
    if( "host".equals( name ) ) {
      return request.getServerName();
    } else if ( "port".equals( name ) ) {
      return Integer.toString( request.getLocalPort() );
    } else if ( "scheme".equals( name ) ) {
      return request.getScheme();
    } else if ( "context-path".equals( name ) ) {
      return Urls.stripLeadingSlash( request.getContextPath() );
    } else {
      return null;
    }
  }

  public String encodeUrl( String url ) {
    return this.encodeURL( url );
  }

  //TODO: Route these through the rewriter.
  public String encodeURL( String url ) {
    throw new UnsupportedOperationException();
  }

  public String encodeRedirectUrl( String url ) {
    return this.encodeRedirectURL( url );
  }

  //TODO: Route these through the rewriter.
  public String encodeRedirectURL( String url ) {
    throw new UnsupportedOperationException();
  }

}

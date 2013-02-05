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

import org.apache.hadoop.gateway.filter.AbstractGatewayFilter;
import org.apache.hadoop.gateway.filter.GatewayRequestWrapper;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteServletContextListener;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteStreamFilterFactory;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Template;

import javax.servlet.FilterConfig;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.Enumeration;

public class UrlRewriteRequest extends GatewayRequestWrapper {

  private UrlRewriter rewriter;

  /**
   * Constructs a request object wrapping the given request.
   *
   * @throws IllegalArgumentException if the request is null
   */
  public UrlRewriteRequest( FilterConfig config, HttpServletRequest request ) throws IOException {
    super( request );
    this.rewriter = UrlRewriteServletContextListener.getUrlRewriter( config.getServletContext() );
  }

  private Template getSourceUrl() {
    Template urlTemplate = null;
    StringBuffer urlString = super.getRequestURL();
    String queryString = super.getQueryString();
    if( queryString != null ) {
      urlString.append( '?' );
      urlString.append( queryString );
    }
    try {
      urlTemplate = Parser.parse( urlString.toString() );
    } catch( URISyntaxException e ) {
      //TODO: Proper I18 stack logging.
      e.printStackTrace();
      // Shouldn't be possible given that the URL is constructed from parts of an existing URL.
      urlTemplate = null;
    }
    return urlTemplate;
  }

  // Note: Source url was added to the request attributes by the GatewayFilter doFilter method.
  private Template getTargetUrl() {
    boolean rewriteRequestUrl = true;
    Template targetUrl;
    if( rewriteRequestUrl ) {
      targetUrl = (Template)getAttribute( AbstractGatewayFilter.TARGET_REQUEST_URL_ATTRIBUTE_NAME );
      if( targetUrl == null ) {
        Template sourceUrl = getSourceUrl();
        targetUrl = rewriter.rewrite( sourceUrl, UrlRewriter.Direction.IN );
        setAttribute( AbstractGatewayFilter.TARGET_REQUEST_URL_ATTRIBUTE_NAME, targetUrl );
      }
    } else {
      targetUrl = (Template)getAttribute( AbstractGatewayFilter.SOURCE_REQUEST_URL_ATTRIBUTE_NAME );
    }
    return targetUrl;
  }

  private String[] splitTargetUrl( Template url ) {
    Template targetUrl = getTargetUrl();
    String s = targetUrl.toString();
    return s.split( "\\?" );
  }

  @Override
  public StringBuffer getRequestURL() {
    return new StringBuffer( getRequestURI() );
  }

  @Override
  public String getRequestURI() {
    String[] split = splitTargetUrl( getTargetUrl() );
    if( split.length > 0 ) {
      return split[0];
    } else {
      return "";
    }
  }

  @Override
  public String getQueryString() {
    String[] split = splitTargetUrl( getTargetUrl() );
    if( split.length > 1 ) {
      return split[1];
    } else {
      return null;
    }
  }

  public String rewriteValue( UrlRewriter rewriter, String value ) {
    try {
      Template input = Parser.parse( value );
      Template output = rewriter.rewrite( input, UrlRewriter.Direction.IN );
      value = output.toString();
    } catch( URISyntaxException e ) {
      e.printStackTrace();
    }
    return value;
  }

  @Override
  public String getHeader( String name ) {
    return rewriteValue( rewriter, super.getHeader( name ) );
  }

  @SuppressWarnings("unchecked")
  public Enumeration getHeaders( String name ) {
    return new EnumerationRewriter( rewriter, (Enumeration<String>)super.getHeaders( name ) );
  }

  private class EnumerationRewriter implements Enumeration<String> {

    private UrlRewriter rewriter;
    private Enumeration<String> delegate;

    private EnumerationRewriter( UrlRewriter rewriter, Enumeration<String> delegate ) {
      this.rewriter = rewriter;
      this.delegate = delegate;
    }

    @Override
    public boolean hasMoreElements() {
      return delegate.hasMoreElements();
    }

    @Override
    public String nextElement() {
      return rewriteValue( rewriter, delegate.nextElement() );
    }
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    InputStream stream = UrlRewriteStreamFilterFactory.create(
        getMimeType(), null, super.getInputStream(), rewriter, UrlRewriter.Direction.IN );
    return new UrlRewriteRequestStream( stream );
  }

  @Override
  public BufferedReader getReader() throws IOException {
    return new BufferedReader( new InputStreamReader( getInputStream(), getCharacterEncoding() ) );
  }

}

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
package org.apache.knox.gateway.filter.rewrite.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.filter.GatewayRequestWrapper;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletContextListener;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteStreamFilterFactory;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.knox.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStreamFilter;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.MimeTypes;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Resolver;
import org.apache.knox.gateway.util.urltemplate.Template;
import org.eclipse.jetty.http.HttpHeader;

import javax.activation.MimeType;
import javax.servlet.FilterConfig;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteUtil.pickFirstRuleWithEqualsIgnoreCasePathMatch;

public class UrlRewriteRequest extends GatewayRequestWrapper implements Resolver {

  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );
  private static final String[] EMPTY_STRING_ARRAY = new String[]{};

  private FilterConfig config;
  private UrlRewriter rewriter;
  private String urlRuleName;
  private String bodyFilterName;
  private String headersFilterName;
  private UrlRewriteFilterContentDescriptor headersFilterConfig;

  /**
   * Constructs a request object wrapping the given request.
   *
   * @param config  FilterConfig
   * @param request request to wrap
   * @throws IllegalArgumentException if the request is null
   */
  public UrlRewriteRequest( FilterConfig config, HttpServletRequest request ) {
    super( request );
    this.config = config;
    this.rewriter = UrlRewriteServletContextListener.getUrlRewriter( config.getServletContext() );
    this.urlRuleName = config.getInitParameter( UrlRewriteServletFilter.REQUEST_URL_RULE_PARAM );
    this.bodyFilterName = config.getInitParameter( UrlRewriteServletFilter.REQUEST_BODY_FILTER_PARAM );
    this.headersFilterName = config.getInitParameter( UrlRewriteServletFilter.REQUEST_HEADERS_FILTER_PARAM );
    this.headersFilterConfig = getRewriteFilterConfig( headersFilterName, UrlRewriteServletFilter.HEADERS_MIME_TYPE );
  }

  Template getSourceUrl() {
    Template urlTemplate;
    //KNOX-439[
    //StringBuffer urlString = super.getRequestURL();
    StringBuilder urlString = new StringBuilder( 128 );
    urlString.append(getScheme()).append("://")
        .append(getServerName()).append(':').append(getServerPort())
        .append(super.getRequestURI());
    //]
    String queryString = super.getQueryString();
    if( queryString != null ) {
      urlString.append( '?' ).append( queryString );
    }
    try {
      urlTemplate = Parser.parseLiteral( urlString.toString() );
    } catch( URISyntaxException e ) {
      LOG.failedToParseValueForUrlRewrite( urlString.toString() );
      // Shouldn't be possible given that the URL is constructed from parts of an existing URL.
      urlTemplate = null;
    }
    return urlTemplate;
  }

  // Note: Source url was added to the request attributes by the GatewayFilter doFilter method.
  Template getTargetUrl() {
    Template targetUrl;
    targetUrl = (Template)getAttribute( AbstractGatewayFilter.TARGET_REQUEST_URL_ATTRIBUTE_NAME );
    if( targetUrl == null ) {
      Template sourceUrl = getSourceUrl();
      targetUrl = rewriter.rewrite( this, sourceUrl, UrlRewriter.Direction.IN, urlRuleName );
      setAttribute( AbstractGatewayFilter.TARGET_REQUEST_URL_ATTRIBUTE_NAME, targetUrl );
    }
    return targetUrl;
  }

  private String[] splitTargetUrl( Template url ) {
    if( url == null ) {
      return EMPTY_STRING_ARRAY;
    } else {
      String s = url.toString();
      return s.split( "\\?" );
    }
  }

  @Override
  public StringBuffer getRequestURL() {
    return new StringBuffer( getRequestURI() );
  }

  //TODO: I think this method is implemented wrong based on the HttpServletRequest.getRequestURI docs.
  // It should not include the scheme or authority parts.
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
      try {
        return URLDecoder.decode(split[1], StandardCharsets.UTF_8.name());
      } catch ( UnsupportedEncodingException e ) {
        LOG.failedToDecodeQueryString(split[1], e);
        return split[1];
      }
    } else {
      return null;
    }
  }

  private String rewriteValue( UrlRewriter rewriter, String value, String rule ) {
    try {
      Template input = Parser.parseLiteral( value );
      Template output = rewriter.rewrite( this, input, UrlRewriter.Direction.IN, rule );
      value = output.getPattern();
    } catch( URISyntaxException e ) {
      LOG.failedToParseValueForUrlRewrite( value );
    }
    return value;
  }

  @Override
  public String getHeader( String name ) {
    String value;
    if ("Host".equalsIgnoreCase(name)) {
      String uri = getRequestURI();
      try {
        URL url = new URL(uri);
        value = url.getHost();
        // by the time the targetUrl is set as a request
        // attribute it has already been rewritten just
        // just return it from here without additional rewrite
        return value;
      } catch (MalformedURLException e) {
        value = null;
      }
    }

    value = super.getHeader( name );

    if( value != null ) {
      value = rewriteValue( rewriter, super.getHeader( name ), pickFirstRuleWithEqualsIgnoreCasePathMatch( headersFilterConfig, name ) );
    }

    return value;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Enumeration getHeaders( String name ) {
    return new EnumerationRewriter( rewriter, super.getHeaders( name ), pickFirstRuleWithEqualsIgnoreCasePathMatch( headersFilterConfig, name ) );
  }

  @Override
  public List<String> resolve( String name ) {
    return Collections.singletonList(config.getInitParameter(name));
  }

  private class EnumerationRewriter implements Enumeration<String> {

    private UrlRewriter rewriter;
    private Enumeration<String> delegate;
    private String rule;

    EnumerationRewriter( UrlRewriter rewriter, Enumeration<String> delegate, String rule ) {
      this.rewriter = rewriter;
      this.delegate = delegate;
      this.rule = rule;
    }

    @Override
    public boolean hasMoreElements() {
      return delegate.hasMoreElements();
    }

    @Override
    public String nextElement() {
      return rewriteValue( rewriter, delegate.nextElement(), rule );
    }
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    ServletInputStream input = super.getInputStream();
    /**
     *  Make sure payload is not empty before adding
     *  content-type specific filters. We cannot rely on request.getContentLength()
     *  since previous filters update it to return -1
     */
    if( getContentLength() != 0 && input.available() != 0 ) {
      MimeType mimeType = getMimeType();

      /* In cases where content type is application/text and content-encoding is gzip */
      final String contentEncoding = getHeader(
          HttpHeader.CONTENT_ENCODING.asString());
      if (!StringUtils.isBlank(contentEncoding) && StringUtils
          .containsAny(contentEncoding, "gzip", "compress", "deflate", "br")) {
        /* This prevents adding filters based on content-type, which in most cases would not be
         * an issue but in cases where request specifies additional encoding this causes issues
         * see KNOX-1412
         */
        mimeType = MimeTypes
            .create("application/" + contentEncoding, getCharacterEncoding());
      }

      UrlRewriteFilterContentDescriptor filterContentConfig = getRewriteFilterConfig( bodyFilterName, mimeType );
      if (filterContentConfig != null) {
        String asType = filterContentConfig.asType();
        if ( asType != null && !asType.trim().isEmpty()) {
          mimeType = MimeTypes.create(asType, getCharacterEncoding());
        }
      }

      final InputStream stream;
      UrlRewriteStreamFilter filter = UrlRewriteStreamFilterFactory.create(mimeType, null);
      if(filter != null) {
        String charset = MimeTypes.getCharset( mimeType, StandardCharsets.ISO_8859_1.name() );
        stream = filter.filter(input, charset, rewriter, this, UrlRewriter.Direction.IN, filterContentConfig );
      } else {
        stream = input;
      }
      input = new UrlRewriteRequestStream( stream );
    }
    return input;
  }

  @Override
  public BufferedReader getReader() throws IOException {
    return new BufferedReader( new InputStreamReader( getInputStream(), getCharacterEncoding() ) );
  }

  @Override
  public int getContentLength() {
    // The rewrite might change the content length so return the default of -1 to indicate the length is unknown.
    int contentLength = super.getContentLength();
    if( contentLength > 0 ) {
      contentLength = -1;
    }
    return contentLength;
  }

  private UrlRewriteFilterContentDescriptor getRewriteFilterConfig( String filterName, MimeType mimeType ) {
    UrlRewriteFilterContentDescriptor filterContentConfig = null;
    UrlRewriteRulesDescriptor rewriteConfig = rewriter.getConfig();
    if( rewriteConfig != null ) {
      UrlRewriteFilterDescriptor filterConfig = rewriteConfig.getFilter( filterName );
      if( filterConfig != null ) {
        filterContentConfig = filterConfig.getContent( mimeType );
      }
    }
    return filterContentConfig;
  }

}

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

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.filter.GatewayResponseWrapper;
import org.apache.knox.gateway.filter.ResponseStreamer;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletContextListener;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteStreamFilterFactory;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.knox.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStreamFilter;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.MimeTypes;
import org.apache.knox.gateway.util.Urls;
import org.apache.knox.gateway.util.urltemplate.Params;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;
import org.apache.commons.io.IOUtils;

import javax.activation.MimeType;
import javax.servlet.FilterConfig;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import static org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteUtil.getRewriteFilterConfig;
import static org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteUtil.pickFirstRuleWithEqualsIgnoreCasePathMatch;

public class UrlRewriteResponse extends GatewayResponseWrapper implements Params,
    ResponseStreamer {

  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );

  // An 8K buffer better matches the underlying buffer sizes.
  // Testing with 16K made no appreciable difference.
  private static final int STREAM_BUFFER_SIZE = 8 * 1024;

  private static final Set<String> IGNORE_HEADER_NAMES = new HashSet<>();
  static {
    IGNORE_HEADER_NAMES.add( "Content-Length" );
  }

  private static final String REQUEST_PARAM_PREFIX = "request.";
  private static final String CLUSTER_PARAM_PREFIX = "cluster.";
  private static final String GATEWAY_PARAM_PREFIX = "gateway.";
  public  static final String INBOUND_QUERY_PARAM_PREFIX   = "query.param.";

  private UrlRewriter rewriter;
  private FilterConfig config;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private ServletOutputStream output;
  private String bodyFilterName;
  private String headersFilterName;
  private UrlRewriteFilterContentDescriptor headersFilterConfig;
  private String xForwardedHostname;
  private String xForwardedPort;
  private String xForwardedScheme;
  private String contentEncoding;

  public UrlRewriteResponse( FilterConfig config, HttpServletRequest request, HttpServletResponse response ) {
    super( response );
    this.rewriter = UrlRewriteServletContextListener.getUrlRewriter( config.getServletContext() );
    this.config = config;
    this.request = request;
    this.response = response;
    this.output = null;
    getXForwardedHeaders();
    this.bodyFilterName = config.getInitParameter( UrlRewriteServletFilter.RESPONSE_BODY_FILTER_PARAM );
    this.headersFilterName = config.getInitParameter( UrlRewriteServletFilter.RESPONSE_HEADERS_FILTER_PARAM );
    this.headersFilterConfig = getRewriteFilterConfig( rewriter.getConfig(), headersFilterName, UrlRewriteServletFilter.HEADERS_MIME_TYPE );
    this.contentEncoding = "";
  }

  protected boolean ignoreHeader( String name ) {
    return IGNORE_HEADER_NAMES.contains( name );
  }

  private String rewriteValue( String value, String rule ) {
    try {
      Template input = Parser.parseLiteral( value );
      Template output = rewriter.rewrite( this, input, UrlRewriter.Direction.OUT, rule );
      if( output != null ) {
        value = output.toString();
      }
    } catch( URISyntaxException e ) {
      LOG.failedToParseValueForUrlRewrite( value );
    }
    return value;
  }

  private void setContentEncoding(String name, String value) {
    if ("Content-Encoding".equalsIgnoreCase(name)) {
      contentEncoding = value;
    }
  }

  // Ignore the Content-Length from the dispatch respond since the respond body may be rewritten.
  @Override
  public void setHeader( String name, String value ) {
    if( !ignoreHeader( name) ) {
      value = rewriteValue( value, pickFirstRuleWithEqualsIgnoreCasePathMatch( headersFilterConfig, name ) );
      setContentEncoding(name, value);
      super.setHeader( name, value );
    }
  }

  // Ignore the Content-Length from the dispatch respond since the respond body may be rewritten.
  @Override
  public void addHeader( String name, String value ) {
    if( !ignoreHeader( name ) ) {
      String rule = pickFirstRuleWithEqualsIgnoreCasePathMatch( headersFilterConfig, name );
      value = rewriteValue( value, rule );
      setContentEncoding(name, value);
      super.addHeader( name, value );
    }
  }

  @Override
  public OutputStream getRawOutputStream() throws IOException {
    return response.getOutputStream();
  }

  @Override
  public void streamResponse( InputStream input, OutputStream output ) throws IOException {
    MimeType mimeType = getMimeType();
    UrlRewriteFilterContentDescriptor filterContentConfig =
        getRewriteFilterConfig(rewriter.getConfig(), bodyFilterName, mimeType);
    if (filterContentConfig != null) {
      String asType = filterContentConfig.asType();
      if ( asType != null && !StringUtils.isBlank(asType)) {
        mimeType = MimeTypes.create(asType, getCharacterEncoding());
      }
    }

    UrlRewriteStreamFilter filter = UrlRewriteStreamFilterFactory.create(mimeType, null);

    final InputStream inStream;
    final OutputStream outStream;
    if( filter != null ) {
      // Use this way to check whether the input stream is gzip compressed, in case
      // the content encoding header is unknown, as it could be unset in inbound response
      boolean isGzip = false;
      final BufferedInputStream inBuffer = new BufferedInputStream(input, STREAM_BUFFER_SIZE);
      inBuffer.mark(2);
      byte [] signature = new byte[2];
      int len = inBuffer.read(signature);
      if( len == 2 && signature[ 0 ] == (byte) 0x1f && signature[ 1 ] == (byte) 0x8b ) {
        isGzip = true;
      }
      inBuffer.reset();

      final InputStream unFilteredStream;
      if(isGzip || "gzip".equalsIgnoreCase(contentEncoding)) {
        unFilteredStream = new GzipCompressorInputStream(inBuffer, true);
        outStream = new GZIPOutputStream(output, STREAM_BUFFER_SIZE);
      } else if ("deflate".equalsIgnoreCase(contentEncoding)) {
        unFilteredStream = new InflaterInputStream(inBuffer);
        outStream = new DeflaterOutputStream(output);
      } else {
        unFilteredStream = inBuffer;
        outStream = output;
      }
      String charset = MimeTypes.getCharset( mimeType, StandardCharsets.UTF_8.name() );
      inStream = filter.filter( unFilteredStream, charset, rewriter, this, UrlRewriter.Direction.OUT, filterContentConfig );
    } else {
      inStream = input;
      outStream = output;
    }

    try {
      IOUtils.copy(inStream, outStream, STREAM_BUFFER_SIZE);
    } finally {
      outStream.close();
    }
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
      return Collections.singletonList(getRequestParam(name.substring(REQUEST_PARAM_PREFIX.length())));
    } else if ( name.startsWith( GATEWAY_PARAM_PREFIX ) ) {
      return Collections.singletonList(getGatewayParam(name.substring(GATEWAY_PARAM_PREFIX.length())));
    } else if ( name.startsWith( CLUSTER_PARAM_PREFIX ) ) {
      return Collections.singletonList(getClusterParam(name.substring(GATEWAY_PARAM_PREFIX.length())));
    } else if ( name.startsWith( INBOUND_QUERY_PARAM_PREFIX ) ) {
      return getInboundQueryParam(name.substring(INBOUND_QUERY_PARAM_PREFIX.length()));
    } else {
      return Collections.singletonList(config.getInitParameter(name));
    }
  }

  // KNOX-464: Doing this because Jetty only returns the string version of the IP address for request.getLocalName().
  // Hopefully the local hostname will be cached so this will not be a significant performance hit.
  // Previously this was an inline request.getServerName() but this ended up mixing the hostname from the Host header
  // and the local port which was making load balancer configuration difficult if not impossible.
  private String getRequestLocalHostName() {
    String hostName = request.getLocalName();
    try {
      hostName = InetAddress.getByName( hostName ).getHostName();
    } catch( UnknownHostException e ) {
      // Ignore it and use the original hostname.
    }
    return hostName;
  }

  private String getGatewayParam( String name ) {
    if( "url".equals( name ) ) {
      if( xForwardedPort == null ) {
        return xForwardedScheme + "://" + xForwardedHostname + request.getContextPath();
      } else {
        return xForwardedScheme + "://" + xForwardedHostname + ":" + xForwardedPort + request.getContextPath();
      }
    } else if( "scheme".equals( name ) ) {
      return xForwardedScheme;
    } else if( "host".equals( name ) ) {
      return xForwardedHostname;
    } else if( "port".equals( name ) ) {
        return xForwardedPort;
    } else if( "addr".equals( name ) || "address".equals( name ) ) {
      if( xForwardedPort == null ) {
        return xForwardedHostname;
      } else {
        return xForwardedHostname + ":" + xForwardedPort;
      }
    } else if( "path".equals( name ) ) {
      return request.getContextPath();
    } else {
      return null;
    }
  }

  private String getClusterParam( String name ) {
    if( "name".equals( name ) ) {
      return config.getServletContext().getServletContextName();
    } else {
      return null;
    }
  }

  private List <String> getInboundQueryParam(String name ){
     List <String> inboundHosts = null;
     if( this.request != null ) {
       inboundHosts = Arrays.asList(this.request.getParameterValues(name));
     }
     return inboundHosts;
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
      config.getServletContext().getServletContextName();
      return null;
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public String encodeUrl( String url ) {
    return this.encodeURL( url );
  }

  //TODO: Route these through the rewriter.
  @Override
  public String encodeURL(String url ) {
    throw new UnsupportedOperationException();
  }

  @Override
  @SuppressWarnings("deprecation")
  public String encodeRedirectUrl( String url ) {
    return this.encodeRedirectURL( url );
  }

  //TODO: Route these through the rewriter.
  @Override
  public String encodeRedirectURL(String url ) {
    throw new UnsupportedOperationException();
  }

  private void getXForwardedHeaders() {
    xForwardedHostname = request.getHeader( "X-Forwarded-Host" );
    xForwardedPort = request.getHeader( "X-Forwarded-Port" );
    xForwardedScheme = request.getHeader( "X-Forwarded-Proto" );
    if ( xForwardedScheme == null ) {
      xForwardedScheme = request.getScheme();
    }
    if ( xForwardedHostname != null ) {
      int separator = xForwardedHostname.indexOf(':');
      if ( separator > 0 ) {
        //a specific port in the forwarded host wins
        xForwardedPort = xForwardedHostname.substring(separator + 1, xForwardedHostname.length());
        xForwardedHostname = xForwardedHostname.substring( 0, separator );
      }
    } else {
      xForwardedHostname = getRequestLocalHostName();
      xForwardedPort = Integer.toString( request.getLocalPort() );
    }
  }
}

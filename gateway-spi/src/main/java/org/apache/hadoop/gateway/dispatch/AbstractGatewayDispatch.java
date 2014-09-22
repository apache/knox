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
package org.apache.hadoop.gateway.dispatch;

import org.apache.hadoop.gateway.filter.AbstractGatewayFilter;
import org.apache.hadoop.gateway.filter.GatewayResponse;
import org.apache.hadoop.io.IOUtils;
import org.apache.http.client.methods.HttpUriRequest;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractGatewayDispatch extends AbstractGatewayFilter implements Dispatch {

  private static Map<String,Adapter> METHOD_ADAPTERS = createMethodAdapters();
  private static int STREAM_COPY_BUFFER_SIZE = 4096;
  private static final List<String> EXCLUDE_HEADERS = Arrays.asList( "Host", "Authorization", "Content-Length", "Transfer-Encoding" );

  private static Map<String,Adapter> createMethodAdapters() {
    Map<String,Adapter> map = new HashMap<String,Adapter>();
    map.put( "GET", new GetAdapter() );
    map.put( "POST", new PostAdapter() );
    map.put( "PUT", new PutAdapter() );
    map.put( "DELETE", new DeleteAdapter() );
    map.put( "OPTIONS", new OptionsAdapter() );
    return Collections.unmodifiableMap( map );
  }

  @Override
  protected void doFilter( HttpServletRequest request, HttpServletResponse response, FilterChain chain )
      throws IOException, ServletException {
    String method = request.getMethod().toUpperCase();
    Adapter adapter = METHOD_ADAPTERS.get( method );
    if( adapter != null ) {
      try {
        adapter.doMethod( this, request, response );
      } catch( URISyntaxException e ) {
        throw new ServletException( e );
      }
    } else {
      response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
    }
  }

  protected static URI getDispatchUrl( HttpServletRequest request ) {
    StringBuffer str = request.getRequestURL();
    String query = request.getQueryString();
    if( query != null ) {
      str.append( '?' );
      str.append( query );
    }
    URI url = URI.create( str.toString() );
    return url;
  }

  protected void writeResponse( HttpServletRequest request, HttpServletResponse response, InputStream stream )
      throws IOException {
//    ResponseStreamer streamer =
//        (ResponseStreamer)request.getAttribute( RESPONSE_STREAMER_ATTRIBUTE_NAME );
//    if( streamer != null ) {
//      streamer.streamResponse( stream, response.getOutputStream() );
//    } else {
      if( response instanceof GatewayResponse ) {
        ((GatewayResponse)response).streamResponse( stream );
      } else {
        OutputStream output = response.getOutputStream();
        IOUtils.copyBytes( stream, output, STREAM_COPY_BUFFER_SIZE );
        output.close();
      }
//    }
  }

  public void doGet( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
  }

  public void doPost( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
  }

  public void doPut( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
  }

  public void doDelete( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
  }

  public void doOptions( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
  }

  private interface Adapter {
    public void doMethod( Dispatch dispatch, HttpServletRequest request, HttpServletResponse response )
        throws IOException, ServletException, URISyntaxException;
  }

  private static class GetAdapter implements Adapter {
    public void doMethod( Dispatch dispatch, HttpServletRequest request, HttpServletResponse response )
        throws IOException, ServletException, URISyntaxException {
      dispatch.doGet( getDispatchUrl( request ), request, response );
    }
  }

  private static class PostAdapter implements Adapter {
    public void doMethod( Dispatch dispatch, HttpServletRequest request, HttpServletResponse response )
        throws IOException, ServletException, URISyntaxException {
      dispatch.doPost( getDispatchUrl( request ), request, response );
    }
  }

  private static class PutAdapter implements Adapter {
    public void doMethod( Dispatch dispatch, HttpServletRequest request, HttpServletResponse response )
        throws IOException, ServletException, URISyntaxException {
      dispatch.doPut( getDispatchUrl( request ), request, response );
    }
  }

  private static class DeleteAdapter implements Adapter {
    public void doMethod( Dispatch dispatch, HttpServletRequest request, HttpServletResponse response )
        throws IOException, ServletException, URISyntaxException {
      dispatch.doDelete( getDispatchUrl( request ), request, response );
    }
  }

  private static class OptionsAdapter implements Adapter {
    public void doMethod( Dispatch dispatch, HttpServletRequest request, HttpServletResponse response )
        throws IOException, ServletException, URISyntaxException {
      dispatch.doOptions( getDispatchUrl( request ), request, response );
    }
  }
  
  public static void copyRequestHeaderFields(HttpUriRequest outboundRequest,
      HttpServletRequest inboundRequest) {
    Enumeration<String> headerNames = inboundRequest.getHeaderNames();
    while( headerNames.hasMoreElements() ) {
      String name = (String) headerNames.nextElement();
      if ( !outboundRequest.containsHeader( name )
          && !EXCLUDE_HEADERS.contains( name ) ) {
        String vaule = inboundRequest.getHeader( name );
        outboundRequest.addHeader( name, vaule );
      }
    }
  }

}

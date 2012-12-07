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
package org.apache.hadoop.gateway.pivot;

import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.util.Streams;
import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Resolver;
import org.apache.hadoop.gateway.util.urltemplate.Rewriter;
import org.apache.hadoop.gateway.util.urltemplate.Template;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 *
 */
//TODO: Common code needs to be factored into helper methods.
public class HttpClientPivot extends AbstractGatewayPivot {

  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );

  //TODO: Should probably the the value that HttpClient will use for its buffer.  See: http://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html
  private static final int BUFFER_SIZE = 8192;

  protected URI resolveRequestUri( HttpServletRequest request ) throws URISyntaxException {
    String sourceQuery = request.getQueryString();
    String sourcePathInfo = request.getPathInfo() + ( sourceQuery == null ? "" : "?" + sourceQuery );
    String sourcePattern = getConfig().getInitParameter( "source" );
    String targetPattern = getConfig().getInitParameter( "target" );

    //TODO: Some of the compilation should be done at servlet init for performance reasons.
    Template sourceTemplate = Parser.parse( sourcePattern );
    Template targetTemplate = Parser.parse( targetPattern );

    Resolver resolver = new ParamResolver( getConfig(), request );
    URI sourceUri = new URI( sourcePathInfo );
    URI targetUri = Rewriter.rewrite( sourceUri, sourceTemplate, targetTemplate, resolver );
    //String targetUrl = UrlRewriter.rewriteUrl( sourcePathInfo, sourcePattern, targetPattern, expect, createResourceConfig() );
//    System.out.println( "Source URI:" + expect.getRequestURI() );
//    System.out.println( "Source URL:" + expect.getRequestURL() );
//    System.out.println( "Source Query: " + expect.getQueryString() );
//    System.out.println( "Source pathInfo: " + sourcePathInfo );
//    System.out.println( "Source pattern: " + sourcePattern );
//    System.out.println( "Target pattern: " + targetPattern );
//    System.out.println( "Resolved target: " + targetUrl );

//    URIBuilder queryBuilder = new URIBuilder( targetUri );
//
//    // Copy the server expect parameters to the client expect parameters.
//    Enumeration<String> paramNames = expect.getParameterNames();
//    while( paramNames.hasMoreElements() ) {
//      String paramName = paramNames.nextElement();
//      String paramValue = expect.getParameter( paramName );
//      queryBuilder.addParameter( paramName, paramValue );
//    }
//
//    URI queryURI = queryBuilder.build();
//    return queryURI;
    return targetUri;
  }

  protected HttpResponse executeRequest( HttpUriRequest clientRequest, HttpServletRequest originalRequest, HttpServletResponse serverResponse ) throws IOException {


//    Set<String> ignored = new HashSet<String>();
//    ignored.add( "Content-Length" );
//    ignored.add( "Host" );

//    Enumeration<String> names = originalRequest.getHeaderNames();
//    while( names.hasMoreElements() ) {
//      String name = names.nextElement();
//      if( !ignored.contains( name ) ) {
//        Enumeration<String> values = originalRequest.getHeaders( name );
//        while( values.hasMoreElements() ) {
//          clientRequest.addHeader( name, values.nextElement() );
//        }
//      }
//    }

    HttpClient client = new DefaultHttpClient();
    HttpResponse clientResponse = client.execute( clientRequest );

    // Copy the client respond header to the server respond.
    serverResponse.setStatus( clientResponse.getStatusLine().getStatusCode() );
    Header[] headers = clientResponse.getAllHeaders();
    for( Header header : headers ) {
      String name = header.getName();
      String value = header.getValue();
      serverResponse.addHeader( name, value );
    }

    HttpEntity entity = clientResponse.getEntity();
    if( entity != null ) {
      Header contentType = entity.getContentType();
      if( contentType != null ) {
        serverResponse.setContentType( contentType.getValue() );
      }
      long contentLength = entity.getContentLength();
      if( contentLength <= Integer.MAX_VALUE ) {
        serverResponse.setContentLength( (int)contentLength );
      }
      InputStream input = entity.getContent();
      OutputStream output = serverResponse.getOutputStream();
      try {
        Streams.drainStream( input, output );
      } finally {
        output.flush();
        input.close();
      }
    }

    return clientResponse;
  }

  @Override
  public void doGet( HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    URI requestUri = resolveRequestUri( request );
    HttpGet clientRequest = new HttpGet( requestUri );
    executeRequest( clientRequest, request, response );
  }

  @Override
  public void doOptions( HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    URI requestUri = resolveRequestUri( request );
    HttpOptions clientRequest = new HttpOptions( requestUri );
    executeRequest( clientRequest, request, response );
  }

  protected HttpEntity createRequestEntity( HttpServletRequest request ) throws IOException {
    InputStream contentStream = request.getInputStream();
    int contentLength = request.getContentLength();
    String contentType = request.getContentType();
    String contentEncoding = request.getCharacterEncoding();
    InputStreamEntity entity = new InputStreamEntity( contentStream, contentLength );
    if( contentType != null ) {
      entity.setContentType( contentType );
    }
    if( contentEncoding != null ) {
      entity.setContentEncoding( contentEncoding );
    }
    return entity;
  }

  @Override
  public void doPut( HttpServletRequest request, HttpServletResponse response ) throws IOException, URISyntaxException {
    HttpEntity entity = createRequestEntity( request );
    URI requestUri = resolveRequestUri( request );
    HttpPut clientRequest = new HttpPut( requestUri );
    clientRequest.setEntity( entity );
    executeRequest( clientRequest, request, response );
  }

  @Override
  public void doPost( HttpServletRequest request, HttpServletResponse response )throws IOException, URISyntaxException {
    HttpEntity entity = createRequestEntity( request );
    URI requestUri = resolveRequestUri( request );
    HttpPost clientRequest = new HttpPost( requestUri );
    clientRequest.setEntity( entity );
    executeRequest( clientRequest, request, response );
  }

  @Override
  public void doDelete( HttpServletRequest request, HttpServletResponse response ) throws IOException, URISyntaxException {
    URI requestUri = resolveRequestUri( request );
    HttpDelete clientRequest = new HttpDelete( requestUri );
    executeRequest( clientRequest, request, response );
  }

}

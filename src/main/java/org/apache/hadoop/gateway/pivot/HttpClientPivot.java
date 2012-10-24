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
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.util.Streams;
import org.apache.hadoop.gateway.util.UrlRewriter;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;

/**
 *
 */
//TODO: Common code needs to be factored into helper methods.
public class HttpClientPivot extends AbstractGatewayPivot {

  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );

  HttpClient httpclient;

  public HttpClientPivot() throws ServletException {
    this.httpclient = new DefaultHttpClient();
  }

  private URI resolveRequestUri( HttpServletRequest request ) throws URISyntaxException {
    String sourcePathInfo = request.getPathInfo();
    String sourcePattern = getConfig().getInitParameter( "source" );
    String targetPattern = getConfig().getInitParameter( "target" );

    //TODO: Some of the regex compilation at servlet init for performance reasons.
    String targetUrl = UrlRewriter.rewriteUrl( sourcePathInfo, sourcePattern, targetPattern, request, getConfig() );
//    System.out.println( "Source URI:" + request.getRequestURI() );
//    System.out.println( "Source URL:" + request.getRequestURL() );
//    System.out.println( "Source Query: " + request.getQueryString() );
//    System.out.println( "Source pathInfo: " + sourcePathInfo );
//    System.out.println( "Source pattern: " + sourcePattern );
//    System.out.println( "Target pattern: " + targetPattern );
//    System.out.println( "Resolved target: " + targetUrl );

    URIBuilder queryBuilder = new URIBuilder( targetUrl );

    // Copy the server request parameters to the client request parameters.
    Enumeration<String> paramNames = request.getParameterNames();
    while( paramNames.hasMoreElements() ) {
      String paramName = paramNames.nextElement();
      String paramValue = request.getParameter( paramName );
      queryBuilder.addParameter( paramName, paramValue );
    }

    URI queryURI = queryBuilder.build();
    return queryURI;
  }

  private void executeRequest( HttpUriRequest clientRequest, HttpServletResponse serverResponse ) throws IOException {
    HttpResponse clientResponse = httpclient.execute( clientRequest );

    // Copy the client response header to the server response.
    serverResponse.setStatus( clientResponse.getStatusLine().getStatusCode() );
    Header[] headers = clientResponse.getAllHeaders();
    for( Header header : headers ) {
      String name = header.getName();
      String value = header.getValue();
      serverResponse.addHeader( name, value );
    }

    HttpEntity entity = clientResponse.getEntity();
    if( entity != null ) {
      serverResponse.setContentType( entity.getContentType().getValue() );
      InputStream input = entity.getContent();
      OutputStream output = serverResponse.getOutputStream();
      try {
        Streams.drainStream( input, output );
      } finally {
        output.flush();
        input.close();
      }
    }

  }

  @Override
  public void doGet( HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    URI requestUri = resolveRequestUri( request );
    HttpGet clientRequest = new HttpGet( requestUri );
    executeRequest( clientRequest, response );
  }

  @Override
  public void doOptions( HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    URI requestUri = resolveRequestUri( request );
    HttpOptions clientRequest = new HttpOptions( requestUri );
    executeRequest( clientRequest, response );
  }

  private HttpEntity createRequestEntity( HttpServletRequest request ) throws IOException {
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
  public void doPut( HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    URI requestUri = resolveRequestUri( request );
    HttpPut clientRequest = new HttpPut( requestUri );
    HttpEntity entity = createRequestEntity( request );
    clientRequest.setEntity( entity );
    executeRequest( clientRequest, response );
  }

  @Override
  public void doPost( HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    URI requestUri = resolveRequestUri( request );
    HttpPost clientRequest = new HttpPost( requestUri );
    HttpEntity entity = createRequestEntity( request );
    clientRequest.setEntity( entity );
    executeRequest( clientRequest, response );
  }

  @Override
  public void doDelete( HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    URI requestUri = resolveRequestUri( request );
    HttpDelete clientRequest = new HttpDelete( requestUri );
    executeRequest( clientRequest, response );
  }

}

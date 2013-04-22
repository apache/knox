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

import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.GatewayResources;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.i18n.resources.ResourcesFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 *
 */
public class HttpClientDispatch extends AbstractGatewayDispatch {

  private static GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );
  private static GatewayResources RES = ResourcesFactory.get( GatewayResources.class );

  protected void executeRequest(
      HttpUriRequest outboundRequest,
      HttpServletRequest inboundRequest,
      HttpServletResponse outboundResponse )
          throws IOException {
    LOG.dispatchRequest( outboundRequest.getMethod(), outboundRequest.getURI() );
    HttpClient client = new DefaultHttpClient();
    HttpResponse inboundResponse;
    try {
      inboundResponse = client.execute( outboundRequest );
    } catch (IOException e) {
      // we do not want to expose back end host. port end points to clients, see JIRA KNOX-58
      LOG.dispatchServiceConnectionException( outboundRequest.getURI(), e );
      throw new IOException( RES.dispatchConnectionError() );
    }

    // Copy the client respond header to the server respond.
    outboundResponse.setStatus( inboundResponse.getStatusLine().getStatusCode() );
    Header[] headers = inboundResponse.getAllHeaders();
    for( Header header : headers ) {
      String name = header.getName();
      String value = header.getValue();
      outboundResponse.addHeader( name, value );
    }

    HttpEntity entity = inboundResponse.getEntity();
    if( entity != null ) {
      Header contentType = entity.getContentType();
      if( contentType != null ) {
        outboundResponse.setContentType( contentType.getValue() );
      }
//KM[ If this is set here it ends up setting the content length to the content returned from the server.
// This length might not match if the the content is rewritten.
//      long contentLength = entity.getContentLength();
//      if( contentLength <= Integer.MAX_VALUE ) {
//        outboundResponse.setContentLength( (int)contentLength );
//      }
//]
      writeResponse( inboundRequest, outboundResponse, entity.getContent() );
    }
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
  public void doGet( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    HttpGet method = new HttpGet( url );
    executeRequest( method, request, response );
  }

  @Override
  public void doOptions( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    HttpOptions method = new HttpOptions( url );
    executeRequest( method, request, response );
  }

  @Override
  public void doPut( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    HttpPut method = new HttpPut( url );
    HttpEntity entity = createRequestEntity( request );
    method.setEntity( entity );
    executeRequest( method, request, response );
  }

  @Override
  public void doPost( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    HttpPost method = new HttpPost( url );
    HttpEntity entity = createRequestEntity( request );
    method.setEntity( entity );
    executeRequest( method, request, response );
  }

  @Override
  public void doDelete( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    HttpDelete method = new HttpDelete( url );
    executeRequest( method, request, response );
  }

}

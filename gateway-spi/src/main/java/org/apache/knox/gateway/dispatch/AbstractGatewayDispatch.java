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
package org.apache.knox.gateway.dispatch;

import org.apache.commons.io.IOUtils;
import org.apache.knox.gateway.filter.GatewayResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractGatewayDispatch implements Dispatch {

  private static final Set<String> REQUEST_EXCLUDE_HEADERS = new HashSet<>();

  static {
      REQUEST_EXCLUDE_HEADERS.add("Host");
      REQUEST_EXCLUDE_HEADERS.add("Authorization");
      REQUEST_EXCLUDE_HEADERS.add("Content-Length");
      REQUEST_EXCLUDE_HEADERS.add("Transfer-Encoding");
  }
  
  protected  HttpClient client;

  @Override
  public void init() {
  }

  protected void writeResponse(HttpServletRequest request, HttpServletResponse response, InputStream stream )
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
        IOUtils.copy(stream, output);
        //KNOX-685: output.flush();
        output.close();
      }
//    }
  }

  @Override
  synchronized public HttpClient getHttpClient() {
    return client;
  }

  @Override
  synchronized public void setHttpClient(HttpClient client) {
    this.client = client;
  }

  @Override
  public URI getDispatchUrl(HttpServletRequest request) {
    StringBuffer str = request.getRequestURL();
    String query = request.getQueryString();
    if ( query != null ) {
      str.append('?');
      str.append(query);
    }
    URI url = URI.create(str.toString());
    return url;
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

  /**
   * @since 0.14.0
   */
  public void doHead( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
  }
  
  public void copyRequestHeaderFields(HttpUriRequest outboundRequest,
      HttpServletRequest inboundRequest) {
    Enumeration<String> headerNames = inboundRequest.getHeaderNames();
    while( headerNames.hasMoreElements() ) {
      String name = headerNames.nextElement();
      if ( !outboundRequest.containsHeader( name )
          && !getOutboundRequestExcludeHeaders().contains( name ) ) {
        String value = inboundRequest.getHeader( name );
        outboundRequest.addHeader( name, value );
      }
    }
  }

  public Set<String> getOutboundRequestExcludeHeaders() {
    return REQUEST_EXCLUDE_HEADERS;
  }

  protected void encodeUnwiseCharacters(StringBuffer str) {
    int pipe = str.indexOf("|");
    while (pipe > -1) {
      str.replace(pipe, pipe+1, "%7C");
      pipe = str.indexOf("|", pipe+1);
    }
    int dq = str.indexOf("\"");
    while (dq > -1) {
      str.replace(dq, dq+1, "%22");
      dq = str.indexOf("\"", dq+1);
    }
  }

}

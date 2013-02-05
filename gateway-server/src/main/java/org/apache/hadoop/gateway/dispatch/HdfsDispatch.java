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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.http.HttpStatus;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class HdfsDispatch extends HttpClientDispatch {

  public HdfsDispatch() throws ServletException {
    super();
  }

  //@Override
  public void doPut( HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    HttpEntity entity = createRequestEntity( request );
    URI requestUri = getDispatchUrl( request );
    if( "CREATE".equals( request.getParameter( "op" ) ) ) {
      HttpPut clientRequest = new HttpPut( requestUri );
      HttpClient client = new DefaultHttpClient();
      HttpResponse clientResponse = client.execute( clientRequest );
      EntityUtils.consume( clientResponse.getEntity() );
      if( clientResponse.getStatusLine().getStatusCode() == HttpStatus.TEMPORARY_REDIRECT_307 ) {
        Header locationHeader = clientResponse.getFirstHeader( "Location" );
        String location = locationHeader.getValue();
        clientRequest = new HttpPut( location );
        clientRequest.setEntity( entity );
        executeRequest( clientRequest, request, response );
      }
    } else {
      HttpPut clientRequest = new HttpPut( requestUri );
      if( entity.getContentLength() > 0 ) {
        clientRequest.setEntity( entity );
      }
      executeRequest( clientRequest, request, response );
    }
  }

}

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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

public class NiFiDispatch extends ConfigurableDispatch {

  @Override
  protected void executeRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse) throws IOException {
    outboundRequest = NiFiRequestUtil.modifyOutboundRequest(outboundRequest, inboundRequest);
    HttpResponse inboundResponse = executeOutboundRequest(outboundRequest);
    writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
  }

  /**
   * Overridden to provide a spot to modify the outbound response before its stream is closed.
   */
  @Override
  protected void writeOutboundResponse(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse, HttpResponse inboundResponse) throws IOException {
    // Copy the client respond header to the server respond.
    outboundResponse.setStatus(inboundResponse.getStatusLine().getStatusCode());
    Header[] headers = inboundResponse.getAllHeaders();
    Set<String> excludeHeaders = getOutboundResponseExcludeHeaders();
    boolean hasExcludeHeaders = false;
    if ((excludeHeaders != null) && !(excludeHeaders.isEmpty())) {
      hasExcludeHeaders = true;
    }
    for ( Header header : headers ) {
      String name = header.getName();
      if (hasExcludeHeaders && excludeHeaders.contains(name.toUpperCase(Locale.ROOT))) {
        continue;
      }
      String value = header.getValue();
      outboundResponse.addHeader(name, value);
    }

    HttpEntity entity = inboundResponse.getEntity();
    if( entity != null ) {
      outboundResponse.setContentType( getInboundResponseContentType( entity ) );
      InputStream stream = entity.getContent();
      try {
        NiFiResponseUtil.modifyOutboundResponse(inboundRequest, outboundResponse, inboundResponse);
        writeResponse( inboundRequest, outboundResponse, stream );
      } finally {
        closeInboundResponse( inboundResponse, stream );
      }
    }
  }
}

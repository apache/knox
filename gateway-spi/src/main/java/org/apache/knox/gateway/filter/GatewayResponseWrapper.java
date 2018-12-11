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
package org.apache.knox.gateway.filter;

import org.apache.commons.io.IOUtils;
import org.apache.knox.gateway.util.MimeTypes;

import javax.activation.MimeType;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class GatewayResponseWrapper extends HttpServletResponseWrapper implements GatewayResponse {

  private static final String DEFAULT_MIME_TYPE = "*/*";

  /**
   * Constructs a response adaptor wrapping the given response.
   *
   * @param response the response object to wrap
   * @throws IllegalArgumentException if the response is null
   */
  public GatewayResponseWrapper( HttpServletResponse response ) {
    super( response );
  }

  @Override
  public MimeType getMimeType() {
    String contentType = getContentType();
    if( contentType == null ) {
      contentType = DEFAULT_MIME_TYPE;
    }
    return MimeTypes.create( contentType, getCharacterEncoding() );
  }

  @Override
  public abstract OutputStream getRawOutputStream() throws IOException;

  @Override
  public void streamResponse( InputStream input ) throws IOException {
    streamResponse( input, getRawOutputStream() );
  }

  @Override
  public void streamResponse( InputStream input, OutputStream output ) throws IOException {
    IOUtils.copy(input, output);
    output.close();
  }
}
